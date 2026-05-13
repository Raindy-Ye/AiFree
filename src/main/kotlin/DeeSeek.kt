import com.microsoft.playwright.BrowserContext
import com.microsoft.playwright.Page
import com.microsoft.playwright.Response
import com.microsoft.playwright.impl.TargetClosedError
import io.klogging.logger
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.suspendCancellableCoroutine
import java.util.concurrent.ConcurrentLinkedDeque
import java.util.function.Consumer
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class DeepSeek(var config: Config) : AiModel {
    val logger = logger("DeepSeek")
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
        """^data:\s*\{"v":\s*\{"response":.*"role":\s*("(?:\\.|.)*?").*"fragments":\s*\[.*"type":\s*"((?:\\.|[^"\\])*)".*$""".toRegex()
    val functionCallRegex = """```(?:\\n|\s)?fcs(?:\\n|\s)?([^{].*)(\{.*?})fce""".toRegex()

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
                val answer = convert(rawResponse)
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

    private fun convert(sseInput: String): String {
        var role: String? = null
        var type: String?
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
                        type = roleMath?.groupValues?.get(2)
                        if (type != "THINK") response = fullRegex.find(line)?.groupValues?.get(1)
                    }
                    if (response != null) {
                        append(response)
                    }
                }
        }


        val result = functionCallRegex.replace(
            """
            data: {"choices":[{"delta":{"content":"$responseText","role":$role}}]}
            """.trimIndent()
        ) { matchResult ->
            val functionName = matchResult.groupValues[1]
            val arguments = matchResult.groupValues[2]
            """
                ","role":$role}}]}
                
                data: {"choices":[{"delta":{"tool_calls":[{"type":"function","function":{"name":"$functionName","arguments":"$arguments","role":$role}}]}}]}
                
                data: {"choices":[{"delta":{"content":"
            """.trimIndent()
        }
        return "$result\n\ndata: [DONE]"
    }
}