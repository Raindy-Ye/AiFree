import com.microsoft.playwright.options.AriaRole

enum class EventType { NONE, MESSAGE, THOUGHT, MAJOR }

class WenXinYiYan(config: Config) : AiModel(config) {

    override val chatCompletionPath = "eb/chat/conversation/v2"
    override val chatPageUrl = "https://yiyan.baidu.com/"
    val roleRegex =
        """^data:\s*\{(?:\\.|[^{\\])*"data":\{(?:\\.|[^{\\])*"createChatResponseVoCommonResult":\{(?:\\.|[^{\\])*"data":\{(?:\\.|[^{\\])*"chat":\{(?:\\.|[^{\\])*"role":\s*"((?:\\.|[^"\\])*)".*$""".toRegex()

    // 提取 fragments 中的 content（兼容转义字符）
    val msgDataRegex =
        """^data:\s*\{(?:\\.|[^{\\])*"data":\{(?:\\.|[^{\\])*"content":\s*"((?:\\.|[^"\\])*)".*$""".toRegex()
    val thoughtDataRegex = """^data:\s*\{(?:\\.|[^{\\])*"thoughts":\s*"((?:\\.|[^"\\])*)".*$""".toRegex()

    init {
        ensureChatPageOpen()
    }

    override fun sentMessage(message: String) {
        page.keyboard().press("Control+K")// New conversation
        page.getByRole(AriaRole.TEXTBOX).fill(message)
        page.keyboard().press("Enter")
    }

    override fun parse(rawResponse: String): ChatResponse {
        var role: String? = null
        val responseText = StringBuilder()
        val reasoningBuilder = StringBuilder()

        var currentEvent = EventType.NONE
        rawResponse.lineSequence().forEach { line ->
            when {
                // 1. 识别事件头，更新当前状态
                line == "event:message" -> currentEvent = EventType.MESSAGE
                line == "event:thought" -> currentEvent = EventType.THOUGHT
                line == "event:major" -> currentEvent = EventType.MAJOR
                // 2. 根据当前状态处理 data 行
                line.startsWith("data:") && currentEvent != EventType.NONE -> {
                    when (currentEvent) {
                        EventType.MESSAGE -> responseText.append(msgDataRegex.find(line)?.groupValues?.get(1))
                        EventType.THOUGHT -> reasoningBuilder.append(thoughtDataRegex.find(line)?.groupValues?.get(1))
                        EventType.MAJOR -> role = roleRegex.find(line)?.groupValues?.get(1)
                    }
                }

                else -> currentEvent = EventType.NONE
            }
        }
        return ChatResponse(responseText.toString(), reasoningBuilder.toString(), role)
    }
}