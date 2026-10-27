# Comfy Mobile

## English

> **This project is customized for the author's own ComfyUI workflows.**
> **Its purpose is to make it convenient to run existing image-generation workflows from a phone. The app cannot add, remove, connect, or edit ComfyUI nodes. Edit the nodes and workflow structure in ComfyUI on the computer and save them there.**
> It maps to four specific workflows: text-to-image, single-image editing, dual-image editing, and Anima text-to-image. **To use it in your own environment, ask your own Agent to review and adapt the workflow structure and node ID mappings, model settings, translation and artist nodes, and deployment paths, then build and verify it.** Arbitrary workflows are not detected automatically. Installing the APK and changing the server address alone does not guarantee compatibility.

A personal Android client for generating images remotely with ComfyUI, supporting Android 13–16 with a focus on Android 16. It connects over IPv6 or a local network and supports text-to-image, single- and dual-image editing, and Anima anime generation. The interface runs locally in a WebView; networking, image files, albums, and task recovery are handled by native Java code.

Current version: **1.6.4**. This repository includes the complete source code, workflows, computer-side extensions, and build instructions.

### Contents

- `android/`: APK source, build scripts, bundled workflow mappings, and checks.
- `computer/custom_nodes/`: computer-side batch processing, artist selection, Excel translation glossary nodes, and a source snapshot of the llama.cpp node with its original license preserved.
- `workflows/`: four workflow graphs that can be opened in ComfyUI. Default user prompts and input image filenames have been replaced with release examples; node IDs and graph structure are preserved.
- `data/glossaries/`: the current Excel glossary.
- `docs/`: installation, workflow mappings, translation, artists, background tasks, testing, and build instructions.
- `CHANGELOG.md`: version history.

### Usage

1. Copy the directories under `computer/custom_nodes/` into ComfyUI's `custom_nodes/`. Install each extension's dependencies, prepare the models, and restart ComfyUI.
2. Copy the four JSON files in `workflows/` into `user/default/workflows/`, and place the glossary in `user/default/glossaries/`.
3. Open each workflow on the computer, select your local models, LoRAs, and translation GGUF model, and save it. Replace sample image inputs with your own files.
4. Install the APK and enter the server address, port, and optional reverse-proxy token in Settings. ComfyUI's default port is 8188. IPv6 addresses do not need manually added brackets.

Each **new batch** reads the corresponding saved workflow from the computer. The phone overrides only the controls exposed in its interface; ordinary CFG, sampling steps, LoRAs, and the translation model follow the saved computer settings. Anima base uses 32 steps / CFG 4, while turbo uses 10 steps / CFG 1. A running batch keeps the configuration snapshot captured when it started.

Further details (in Chinese): [Setup](docs/SETUP.md) · [Workflows and mappings](docs/WORKFLOWS.md) · [Translation and glossaries](docs/TRANSLATION.md) · [Artists](docs/ARTISTS.md) · [Background tasks](docs/ARCHITECTURE.md) · [Build and checks](docs/BUILD.md).

Model weights, SDKs, Python runtimes, signing private keys, phone connection settings, and task history are excluded. See `THIRD_PARTY_NOTICES.md` for third-party components and licenses. Original project code is licensed under **AGPL-3.0-only**; see `LICENSE` for the full text. Third-party components retain their original licenses.

---

## 中文

> **这是根据作者本人的工作流定制的项目，不是通用 ComfyUI 手机客户端。**
> **本项目只是为了方便用手机操作已有工作流生图，手机端不具备新增、删除、连接或调整 ComfyUI 节点的能力。节点和流程结构请在电脑端 ComfyUI 中修改并保存。**
> 当前固定对应作者的文生图、单图改图、双图改图、Anima 文生图四个工作流。**如果想用于自己的环境，请使用自己的 Agent 检查并修改工作流结构与节点 ID 映射、模型配置、翻译与画师节点以及部署路径，再自行构建和验证。** 不支持自动识别任意工作流；直接安装 APK 并更换服务器地址，不代表能运行你的工作流。

面向 Android 13～16（重点适配 Android 16）的个人 ComfyUI 远程出图客户端。支持 IPv6／局域网连接、文生图、单图／双图图改图及 Anima 动漫。界面为本地 WebView，网络、图片文件、相册与任务恢复由原生 Java 实现。

当前版本 **1.6.4**。完整源码、工作流、电脑端扩展及构建说明均包含在本项目中。

## 目录

- `android/`：APK 源码、构建脚本、内置映射和检查。
- `computer/custom_nodes/`：电脑端批次扩展、画师节点、Excel 翻译词典节点，以及保留原许可证的 llama.cpp 节点源码快照。
- `workflows/`：四个可在 ComfyUI 打开的图形工作流。默认用户提示词与输入图片文件名已替换成发布示例，节点 ID 和结构保留。
- `data/glossaries/`：当前 Excel 词典。
- `docs/`：安装、工作流映射、翻译、画师、后台任务、测试与构建说明。
- `CHANGELOG.md`：按版本整理的更新记录。

## 使用

1. 将 `computer/custom_nodes/` 下各目录复制到 ComfyUI 的 `custom_nodes/`。安装各插件的依赖，准备模型，重启 ComfyUI。
2. 将 `workflows/` 的四个 JSON 放到 ComfyUI 的 `user/default/workflows/`；将词典放到 `user/default/glossaries/`。
3. 在电脑打开工作流，选择本机模型、LoRA 与翻译 GGUF，保存。输入图示例需要换成本机图片。
4. 安装 APK，在 App 设置中填写地址、端口和可选的反向代理令牌。ComfyUI 默认端口 8188；IPv6 地址不需要手工添加方括号。

每个**新批次**读取电脑上对应的已保存工作流。手机仅覆盖界面控制项；普通 CFG、步数、LoRA、翻译模型等继续使用电脑保存值。Anima base 固定 32 步／CFG4，turbo 固定 10 步／CFG1。正在执行的批次使用开始时的配置快照。

详情：[安装](docs/SETUP.md) · [工作流与映射](docs/WORKFLOWS.md) · [翻译和词典](docs/TRANSLATION.md) · [画师](docs/ARTISTS.md) · [后台任务](docs/ARCHITECTURE.md) · [构建与检查](docs/BUILD.md)。

本包不含模型权重、SDK、Python 运行环境、签名私钥、手机连接配置及任务历史。`THIRD_PARTY_NOTICES.md` 列出第三方组件及其许可；项目自有代码采用 AGPL-3.0-only 开源，完整许可见 LICENSE；第三方组件保留各自原有许可。
