import com.microsoft.playwright.BrowserContext
import com.microsoft.playwright.BrowserType
import com.microsoft.playwright.BrowserType.LaunchPersistentContextOptions
import com.microsoft.playwright.Playwright
import com.microsoft.playwright.Playwright.CreateOptions
import com.microsoft.playwright.PlaywrightException
import java.nio.file.Path
import java.nio.file.Paths

fun launchBrowser(config: Config): BrowserContext {

    val contextPath = when {
        config.persistentContext -> Paths.get(config.contextDataDir)
        else -> Paths.get("")
    }
    val createOptions = CreateOptions().apply {
        env = mapOf("PLAYWRIGHT_SKIP_BROWSER_DOWNLOAD" to if (config.autoDownloadBrowser) "0" else "1")
    }
    val launchOptions = LaunchPersistentContextOptions().apply {
        headless = config.headlessBrowser
        config.browserPath?.let { executablePath = Paths.get(it) }
    }
    val playwright = Playwright.create(createOptions)
    // 如果指定了浏览器路径，直接尝试启动 Chromium，失败则不再兜底
    if (config.browserPath != null) {
        return playwright.chromium().launchPersistentContext(contextPath, launchOptions)
    }

    // 可能的浏览器
    val browserFactories = listOf(
        playwright.edge(),
        playwright.chromium(),
        playwright.webkit(),
        playwright.firefox()
    )

    // 依次尝试启动
    for (browserType in browserFactories) {
        try {
            return browserType.launchPersistentContext(contextPath, launchOptions)
        } catch (e: PlaywrightException) {
            if (e.message?.contains("Executable doesn't exist") != true) {
                throw e
            }
        }

    }
    // 如果所有浏览器都因为“不存在”而启动失败，抛出最后一个异常
    throw PlaywrightException("无法启动任何浏览器 (Edge, Chromium, Firefox, WebKit)，请检查环境或指定 browserPath。")
}

fun Playwright.edge(): BrowserType {
    val actualBrowserType = chromium()
    return object : BrowserType by actualBrowserType {
        override fun launchPersistentContext(
            userDataDir: Path?,
            options: LaunchPersistentContextOptions?
        ): BrowserContext? {
            return actualBrowserType.launchPersistentContext(userDataDir, options?.apply { channel = "msedge" })
        }
    }
}