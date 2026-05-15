interface AiModel {
    suspend fun chat(message: String): String

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
}
