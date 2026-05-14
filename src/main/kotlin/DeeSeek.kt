import com.microsoft.playwright.BrowserContext
import com.microsoft.playwright.Page
import com.microsoft.playwright.Response
import com.microsoft.playwright.impl.TargetClosedError
import io.klogging.noCoLogger
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.suspendCancellableCoroutine
import java.util.concurrent.ConcurrentLinkedDeque
import java.util.function.Consumer
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class DeepSeek(var config: Config) : AiModel {
    val logger = noCoLogger("DeepSeek")
    var retries = 0
    val maxRetries = 3
    lateinit var browserContext: BrowserContext
    lateinit var page: Page
    var chatCompletionListeners = ConcurrentLinkedDeque<Consumer<Response>>()

    val chatCompletionPath = "api/v0/chat/completion"
    val chatPageUrl = "https://chat.deepseek.com/"

    // 提取 v 的直接值（兼容转义字符）
    val simplifiedRegex = """^data:\s*\{.*"v":\s*"((?:\\.|[^"\\])*)".*$""".toRegex()

    // 提取 fragments 中的 content（兼容转义字符）
    val fullRegex =
        """^data:\s*\{"v":\s*\{"response":.*"fragments":\s*\[.*"content":\s*"((?:\\.|[^"\\])*)".*$""".toRegex()
    val roleRegex =
        """^data:\s*\{"v":\s*\{"response":.*"role":\s*"((?:\\.|.)*?)".*"fragments":\s*\[.*"type":\s*"((?:\\.|[^"\\])*)".*$""".toRegex()
    val functionCallRegex = """(?:\b|\\n)fcs(?:\\n|\s)?([^{].*)(\{.*?})fce\b""".toRegex()

    init {
        ensureChatPageOpen()
    }

    fun ensureChatPageOpen() {
        if (!::browserContext.isInitialized || browserContext.isClosed) {
            browserContext = launchBrowser(config)
        }
        if (!::page.isInitialized || page.isClosed) {
            chatCompletionListeners.clear()
            page = browserContext.pages().first { it.url() == "about:blank" } ?: browserContext.newPage()
            page.navigate(chatPageUrl)
            page.onResponse { response ->
                if (response.url().contains(chatCompletionPath)) {
                    chatCompletionListeners.pollFirst()?.accept(response)
                }
            }
        }
    }

    override suspend fun chat(message: String): String = suspendCancellableCoroutine { continuation ->
        while (retries++ < maxRetries) {
            registerCompletionHandler(continuation)
            sentMessage(message, continuation)
            if (continuation.isCompleted) break
        }
    }

    private fun registerCompletionHandler(continuation: CancellableContinuation<String>) {
        chatCompletionListeners.offerLast { response ->
            try {
                val rawResponse = response.text()
                logger.debug { "The row response: $rawResponse" }
                val answer = transformToOpenAiFormat(rawResponse)
                continuation.resume(answer)
            } catch (e: Exception) {
                continuation.resumeWithException(e)
            }
        }
    }

    private fun sentMessage(message: String, continuation: CancellableContinuation<String>) {
        try {
            page.fill("textarea", message)
            page.keyboard().press("Enter")
            // Wait for the response
            page.waitForResponse(
                { resp -> resp.url().contains(chatCompletionPath) },
                { Page.WaitForResponseOptions().setTimeout(30000.0) }
            )
            retries = 0
        } catch (_: TargetClosedError) {
            ensureChatPageOpen()
        } catch (e: Exception) {
            continuation.resumeWithException(e)
        }
    }

    private fun transformToOpenAiFormat(rawResponse: String): String = buildString {
        val (responseText, role, reasoningText) = extractContent(rawResponse)
        if (reasoningText.isNotBlank()) {
            appendLine(buildReasoningContent(reasoningText, role))
        }
        var lastEndIndex = 0
        // 查找所有匹配的函数调用
        val matches = functionCallRegex.findAll(responseText)
        for (match in matches) {
            val startIdx = match.range.first
            val endIdx = match.range.last + 1 // range.last 是包含的，所以 +1 作为 substring 的 endIndex
            val functionName = match.groupValues[1]
            val rawArguments = match.groupValues[2]
            // 1. 处理函数调用前的普通文本
            if (startIdx > lastEndIndex) {
                val textContent = responseText.substring(lastEndIndex, startIdx)
                if (textContent.isNotEmpty()) appendLine(buildResponseContent(textContent, role))
            }
            // 2. 处理函数调用本身
            appendLine(buildFunctionCall(functionName, rawArguments, role))
            // 更新上次处理结束位置
            lastEndIndex = endIdx
        }
        // 3. 处理剩余的普通文本 (最后一个函数调用之后，或没有函数调用时的全部文本)
        if (lastEndIndex < responseText.length) {
            val remainingText = responseText.substring(lastEndIndex)
            if (remainingText.isNotEmpty()) appendLine(buildResponseContent(remainingText, role))
        }
        append("data: [DONE]")
    }

    private fun extractContent(sseInput: String): Triple<String, String?, String> {
        var role: String? = null
        val reasoningBuilder = StringBuilder()
        val responseText = buildString {
            sseInput.lineSequence()
                .filter { it.startsWith("data:") }
                .filter { !it.contains(""""o":"BATCH"""") }
                .filter { !it.contains(""""o":"SET"""") }
                .forEach { line ->
                    var response: String? = null
                    val simplifiedMatch = simplifiedRegex.find(line)
                    if (simplifiedMatch != null) {
                        response = simplifiedMatch.groupValues[1]
                    } else {
                        val roleMath = roleRegex.find(line)
                        role = roleMath?.groupValues?.get(1)
                        val type = roleMath?.groupValues?.get(2)
                        val text = fullRegex.find(line)?.groupValues?.get(1)
                        when(type) {
                            "THINK" -> reasoningBuilder.append(text)
                            else -> response = text
                        }
                    }
                    if (response != null) {
                        append(response)
                    }
                }
        }
        return Triple(responseText, role, reasoningBuilder.toString())
    }
}