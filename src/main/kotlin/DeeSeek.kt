class DeepSeek(config: Config) : AiModel(config) {
    override val chatCompletionPath = "api/v0/chat/completion"
    override val chatPageUrl = "https://chat.deepseek.com/"

    // 提取 v 的直接值（兼容转义字符）
    val simplifiedRegex = """^data:\s*\{.*"v":\s*"((?:\\.|[^"\\])*)".*$""".toRegex()

    // 提取 fragments 中的 content（兼容转义字符）
    val fullRegex =
        """^data:\s*\{"v":\s*\{"response":.*"fragments":\s*\[.*"content":\s*"((?:\\.|[^"\\])*)".*$""".toRegex()
    val roleRegex =
        """^data:\s*\{"v":\s*\{"response":.*"role":\s*"((?:\\.|.)*?)".*"fragments":\s*\[.*"type":\s*"((?:\\.|[^"\\])*)".*$""".toRegex()

    init {
        ensureChatPageOpen()
    }

    override fun sentMessage(message: String) {
        page.fill("textarea", message)
        page.keyboard().press("Enter")
        // Wait for the response
    }

    override fun parse(rawResponse: String): ChatResponse {
        var role: String? = null
        val reasoningBuilder = StringBuilder()
        val responseText = buildString {
            rawResponse.lineSequence()
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
        return ChatResponse(responseText, reasoningBuilder.toString(), role)
    }
}