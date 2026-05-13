import io.ktor.server.application.*
import io.ktor.server.netty.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File

data class Config(
    val aiModel: String,
    val autoDownloadBrowser: Boolean = false,
    val browserPath: String? = null,
    val contextDataDir: String,
    val persistentContext: Boolean = true,
    val headlessBrowser: Boolean = false
)

// this module will be loaded based on the configuration under application.conf
fun Application.module() {
    val config = environment.config
    val aiModelName = config.property("model").getString()
    val autoDownloadBrowser = config.propertyOrNull("auto_download_browser")?.getString().toBoolean()
    val browserPath = config.propertyOrNull("browser_path")?.getString()
    val headlessBrowser = config.propertyOrNull("headless")?.getString().toBoolean()
    val persistentContext = config.propertyOrNull("persistent_context")?.getString().toBoolean()
    val contextDataDir = config.propertyOrNull("context_data_dir")?.getString() ?: File(
        System.getProperty("user.home"),
        ".AiFree/context_data"
    ).apply { mkdirs() }.absolutePath

    val cfg = Config(
        aiModel = aiModelName,
        autoDownloadBrowser = autoDownloadBrowser,
        browserPath = browserPath,
        contextDataDir = contextDataDir,
        persistentContext = persistentContext,
        headlessBrowser = headlessBrowser
    )
    val aiModel = when (aiModelName) {
        "DeepSeek" -> DeepSeek(cfg)
        else -> throw NotImplementedError("The AI Model [$aiModelName] is not supported! Currently only DeepSeek is supported")
    }
    configureProxyRouting(aiModel)

    launch(Dispatchers.IO) {
        engine.resolvedConnectors().forEach { connector ->
            println("服务器启动成功，当前监听端口为: ${connector.port}")
        }
    }
}

fun main(args: Array<String>) {
    // start EngineMain，it will read resources/application.conf automatically
    EngineMain.main(args)
}