import com.google.gson.JsonParser
import com.microsoft.playwright.Page
import com.microsoft.playwright.options.AriaRole
import com.microsoft.playwright.options.FilePayload

class QianWen(config: Config) : AiModel(config) {
    //https://chat2.qianwen.com/api/v2/chat?
    override val chatCompletionPath = "api/v2/chat"
    override val chatPageUrl = "https://www.qianwen.com/"

    init {
        ensureChatPageOpen()
    }

    override fun sentMessage(message: String) {
        val newSessionBtn =  page.locator("button:has(span[data-icon-type='qwpcicon-temporaryDialogue'])").first()
        newSessionBtn.click()
        val fileInputElement = page.locator("""input:not([accept*='png'])[accept*='txt']""")
        // 2. 上传提示词文件
        val filePayload = FilePayload(
            "_prompt_.txt",
            "text/plain",
            message.toByteArray()
        )
        fileInputElement.setInputFiles(filePayload)

        val inputBox = page.getByRole(AriaRole.TEXTBOX)
        val sentBtn = page.getByRole(AriaRole.BUTTON, Page.GetByRoleOptions().setName("发送"))
        inputBox.fill(buildString {
            appendLine("_prompt_.txt是提示词和对话上下文。")
            appendLine("请根据提示词和对话上下文来回复。")
        })
        sentBtn.click()
    }

    override fun parse(rawResponse: String): ChatResponse {
        val responseText = StringBuilder()
        rawResponse.lineSequence().filter { it.startsWith("data:") }
            .map { it.removePrefix("data:") }
            .forEach { line ->
                try {
                    val rootObj = JsonParser.parseString(line).asJsonObject
                    // 安全获取 data -> messages
                    val dataObj = rootObj.getAsJsonObject("data") ?: return@forEach
                    val messagesArray = dataObj.getAsJsonArray("messages") ?: return@forEach
                    messagesArray.map { it.asJsonObject }
                        .filter { it.get("status")?.asString == "complete" }
                        .filter { it.get("mime_type").asString == "multi_load/iframe" }
                        .forEach {
                            val rawContext = it.get("content")?.toString()?.removePrefix("\"")?.removeSuffix("\"")
                            if (rawContext != null) {
                                responseText.append(rawContext).append("\\n")
                            }
                        }
                } catch (_: Exception) {
                    // it may error, just ignore it and process the next line
                }
            }
        // remove the last "\\n"
        return ChatResponse(responseText.toString(), "", "")
    }
}