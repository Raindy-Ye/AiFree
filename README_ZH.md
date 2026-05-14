**Languages: [English](README.md), [中文](README_ZH.md).**
# AiFree
**`AiFree`** 旨在为OpenClaw提供免费的AI模型。适合轻度使用OpenClaw的个人用户

## 原理
通过代理将OpenClaw发送给AI模型的请求转发到免费的AI模型Web版，如:https://chat.deepseek.com/  
然后将Web回复转换成OpenAI兼容的格式返回给OpenClaw.

## 构建和运行环境
- java25
- Edge 或 Chrome 浏览器。理论其它浏览器也可以，但没测试过。
- gradle

## 使用方法
### 启动服务
- 如果你下载的是原码，可以直接在项目的根目录下执行命令：`./gradlew run --args="-port=8080 -P:model=DeepSeek"`
- 如果你运行的是jar包，可以执行命令: `java -jar AiFree-all.jar -port=8080 -P:model=DeepSeek`
### 参数说明
- `-port`服务端口，默认为`8080`
- `-P:model` AI 模型。目前仅支持`DeepSeek`
- `-P:auto_download_browser`是否自动下载浏览器, 默认为`false`。代理过程需要使用浏览器，默认依次尝试优先使用本地的`Edge`、`Chrome`、`webkit`。
- `-P:browser_path` 明确指定要使用的浏览器路径。默认为空。
- `-P:persistent_context`是否保存代理浏览器的session数据，如 cookies 和 local storage。默认为`true`。多数Web版的AI模型都需要用户登录，如果保存session数据，可以避免每次重启服务后都需要登录。
- `-P:context_data_dir`指定代理浏览器session数据的保存目录。默认为为`~/.AiFree/context_data`
- `-P:headless`代理浏览器是否以headless模式(不可见)，默认为`false`。

### 配置 OpenClaw
#### 使用命令行配置
1. 通过命令`openclaw config get models.providers`找到你要代理的模型提供商
2. 假设是`ollama`，然后通过命令`openclaw config set models.providers.ollama.baseUrl "http://127.0.0.1:8080/aifree"` (注意你代理服务配置的端口号)
#### 直接编辑配置文件
- 可以直接编辑OpenClaw的配置文件，默认在用户目录下： `~/.openclaw/openclaw.json`

### 🔥 重要提示
- 代理只能在提供商层级，既某个提供商被代理后，提供商下配置的所有模型都会走代理。
- 配置的 Web 版 AI 如果需要登录，在代理浏览器打开页面后，你需要手动登录。如果你以允许保存代理浏览器的session数据，重启服务后可能不需要登录。
