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
                    val answer = aiModel.chat(requestBody)
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