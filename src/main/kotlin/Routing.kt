import io.klogging.logger
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.route
import io.ktor.server.routing.routing

private val logger = logger("routing")
private val promptRegex = """^\{[^{]*"messages":\[\{[^{]*"content":""".toRegex()
private const val outputPrompt =
    """## output\nIf a tool needs to be called, the usage/call of that tool shall be strictly appended to the output in the format:\n- prefix with '```fcs '\n- then the name of the tool\n- then then arguments, the arguments should be json format\n- then suffix with 'fce'\nexamples:\n-```fcs browser{"action\": "open"}fce"""

fun Application.configureProxyRouting(aiModel: AiModel) {
    routing {
        route("/aifree/{path...}") {
            intercept(ApplicationCallPipeline.Call) {
                val requestBody = call.receiveText()
                val message = promptRegex.replace(requestBody) { matchResult ->
                    "${matchResult.value}$outputPrompt"
                }

                logger.debug {
                    """
                    ----------------------------------------------------------request body----------------------------------------------------------
                    $requestBody
                    --------------------------------------------------------------------------------------------------------------------------------
                    """.trimIndent()
                }
                try {
                    val answer = aiModel.chat(message)
                    logger.debug {
                        """
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