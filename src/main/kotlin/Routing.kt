import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import io.klogging.logger
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.route
import io.ktor.server.routing.routing

private val logger = logger("routing")
private const val outputPrompt = """
##回复规则
如果需要调用工具，要将调用工具的方式严格按照下面的格式附加到回复中：fcs <name><jsonArgs>fce
示例: fcs browser{\"action\": \"open\"}fce
"""

data class ChatRequest(
    @SerializedName("messages") val messages: List<Message>
) {
    data class Message(
        @SerializedName("role") val role: String,
        @SerializedName("content") val content: String
    )
}
private val gson = Gson()
fun Application.configureProxyRouting(aiModel: AiModel) {
    routing {
        route("/aifree/{path...}") {
            intercept(ApplicationCallPipeline.Call) {
                val requestBody = call.receiveText()
            //    val chatRequest = gson.fromJson(requestBody, ChatRequest::class.java)
                logger.debug {"""
                    ----------------------------------------------------------request body----------------------------------------------------------
                    $requestBody
                    --------------------------------------------------------------------------------------------------------------------------------
                    """.trimIndent()
                }
                try {
                    val answer = aiModel.chat("$requestBody\n$outputPrompt")
                    logger.debug {"""
                        ----------------------------------------------------------response body----------------------------------------------------------
                        $answer
                        ---------------------------------------------------------------------------------------------------------------------------------
                        """.trimIndent()
                    }
                    call.respondText(answer)
                } catch (e: Exception) {
                    e.printStackTrace()
                    call.respond(HttpStatusCode.BadRequest, "${e.message}")
                }
            }
        }
    }
}