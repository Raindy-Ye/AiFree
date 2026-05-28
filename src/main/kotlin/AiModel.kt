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

private const val outputPrompt = """
##回复规则
如果需要调用工具，要将调用工具的方式严格按照下面的格式附加到回复中：fcs <name><jsonArgs>fce
示例: fcs browser{\"action\": \"open\"}fce
"""
abstract class AiModel(var config: Config) {
    var retries = 0
    val maxRetries = 3
    abstract val chatPageUrl: String
    abstract val chatCompletionPath: String

    lateinit var browserContext: BrowserContext
    lateinit var page: Page
    var chatCompletionListeners = ConcurrentLinkedDeque<Consumer<Response>>()

    val functionCallRegex = """(?:\b|\\n)fcs(?:\\n|\s)?([^{].*)(\{.*?})fce\b""".toRegex()
    // 使用 by lazy 延迟初始化，确保获取的是运行时实际子类的 Class
    protected val logger by lazy {
        noCoLogger(this.javaClass.name)
    }

    abstract fun sentMessage(message: String)
  //  abstract fun transformToOpenAiFormat(rawResponse: String): String
    abstract fun parse(rawResponse: String): ChatResponse

    suspend fun chat(message: String): String = suspendCancellableCoroutine { continuation ->
        while (retries++ < maxRetries) {
            registerCompletionHandler(continuation)
            sentMessage("$message\n$outputPrompt", continuation)
            if (continuation.isCompleted) break
        }
    }

    private fun sentMessage(message: String, continuation: CancellableContinuation<String>) {
        try {
            sentMessage(message)
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
    private fun transformToOpenAiFormat(rawResponse: String) = buildString {
        logger.debug { "The row response: $rawResponse" }
        val (responseText, reasoningText, role) = parse(rawResponse)
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

    fun buildResponseContent(text: String, role: String?): String = buildString {
        append("""data: {"choices":[{"delta":{"content":"$text"""")
        if (role != null) append(""","role":"$role"""")
        append("}}]}\n")
    }
    fun buildReasoningContent(text: String, role: String?): String = buildString {
        append("""data: {"choices":[{"delta":{"reasoning_content":"$text"""")
        if (role != null) append(""","role":"$role"""")
        append("}}]}\n")
    }
    fun buildFunctionCall(functionName: String, args:String, role: String?): String = buildString {
        append("""data: {"choices":[{"delta":{"tool_calls":[{"type":"function","function":{"name":"$functionName","arguments":"$args"""")
        if (role != null) append(""","role":"$role"""")
        append("}}]}}]}\n")
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
    data class ChatResponse(val text: String, val reasoning: String, val role: String?)
}
