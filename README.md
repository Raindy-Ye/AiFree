**Languages: [English](README.md), [中文](README_ZH.md).**
# AiFree
**`AiFree`** is designed to provide free AI models for OpenClaw. It is suitable for individual users who use OpenClaw lightly.

## How it works
The tool works by proxying requests sent from OpenClaw to AI models and forwarding them to free web-based AI models, such as: https://chat.deepseek.com/.
It then converts the web response into an OpenAI-compatible format and sent it to OpenClaw.

## Build and Runtime Requirements
- java25
- Edge or Chrome browser. Other browsers might work in theory, but they haven't been tested.
- gradle

## Usage
### Starting the Service
- If you have downloaded the source code, navigate to the project's root directory and execute the command:`./gradlew run --args="-port=8080 -P:model=DeepSeek"`
- If you are running the jar package, execute the command: `java -jar AiFree-all.jar -port=8080 -P:model=DeepSeek`
### Parameters
- `-port`The service port. Default is`8080`
- `-P:model`The AI model to use. Currently, only DeepSeek is supported.`DeepSeek`
- `-P:auto_download_browser`Whether to automatically download a browser. Default is `false`。The proxy process requires a browser and will by default try to use the local `Edge`、`Chrome`、`webkit`。
- `-P:browser_path` Explicitly specify the path to the browser you want to use.
- `-P:persistent_context` Whether to save session data (such as cookies and local storage) for the proxy browser. Default is`true`。Most web-based AI models require user login, so saving session data can prevent the need to log in again after each service restart.
- `-P:context_data_dir`Specify the directory where the proxy browser's session data will be saved. Default is`~/.AiFree/context_data`
- `-P:headless` Whether the proxy browser should run in headless mode (invisible). Default is`false`.

### Configuring OpenClaw
#### Configure via Command Line
1. Use the command to find the model provider you wish to proxy: `openclaw config get models.providers`
2. For example, to proxy the provider `ollama`, you can run the command to update the configuration: `openclaw config set models.providers.ollama.baseUrl "http://127.0.0.1:8080/aifree"`
#### Or edit the Configuration File
- You can directly edit the OpenClaw configuration file, which is located by default in the user's home directory:`~/.openclaw/openclaw.json`

### 🔥 Important Notes
- proxy only works at the provider level. This means that once a specific provider is proxied, all models configured under that provider will use the proxy.
- If the configured web-based AI requires login, you will need to manually log in after the proxy browser opens the page. If you have allowed saving the proxy browser's session data, you likely won't need to log in again after restarting the service.
