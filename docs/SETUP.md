# 安装电脑端和 App

> **这是根据作者本人的工作流定制的项目，不是通用 ComfyUI 手机客户端。**
> 当前固定对应作者的文生图、单图改图、双图改图、Anima 文生图四个工作流。**如果想用于自己的环境，请使用自己的 Agent 检查并修改工作流结构与节点 ID 映射、模型配置、翻译与画师节点以及部署路径，再自行构建和验证。** 不支持自动识别任意工作流；直接安装 APK 并更换服务器地址，不代表能运行你的工作流。

要求：当前 ComfyUI 新节点接口、Android API 33+、电脑和手机网络互通。Anima／Krea2 模型节点需要对应版本的 ComfyUI；不是任意老版本都能打开。

## 电脑文件位置（相对 ComfyUI 根目录）

| 内容 | 位置 |
|---|---|
| 四个图形工作流 | `user/default/workflows/` |
| `Anima角色作品词典.xlsx` | `user/default/glossaries/` |
| 规则书 | `custom_nodes/comfyui-excel-prompt-glossary/Anima_translation_rules.txt` |
| 画师目录和例图 | `custom_nodes/comfyui-anima-artist-gallery/artists.json`、`previews/` |
| GGUF | `models/llm/` 或 llama.cpp 插件的 `config.json` 指定目录 |

工作流使用相对路径，ComfyUI 应以其根目录作为工作目录启动。也可在节点中选择本机绝对路径后保存。词典内容／规则书内容更新不要求更新 APK；更换文件路径时也在电脑节点中保存。

安装各节点 `requirements.txt` 的依赖。llama-cpp-python 根据本机 Python／CPU／CUDA 环境安装，不随项目分发二进制轮子；CPU 翻译设置 `n_gpu_layers=0`。其 `config.example.json` 是示例，复制为 `config.json` 并填写自己的模型目录。

其它节点依赖：rgthree 的 LoRA Loader Stack、ComfyUI-KJNodes 的 GetImageSizeAndCount，以及工作流使用的 Krea2、ComfyMathExpression 节点。完整类型和连线以 JSON 为准；模型文件名仅是当前选择，需自行准备或在电脑替换。

批次扩展加载后，`GET /comfy-mobile/capabilities` 应包含 `batch_api: 2`、`desktop_workflows: true`。扩展源码更新需要重启；仅更改保存的工作流参数、词典或规则书不需要重装 App。

## 连接

电脑启动参数的 `--listen` 使服务器监听外部接口；手机设置可填 IPv6／IPv4／域名。公网使用时在自己的路由／反向代理中配置访问控制和 HTTPS；App 的令牌字段对应反向代理 Bearer 验证，本项目没有替 ComfyUI 核心增加账户系统。

## 常见故障

- `VBAR allocation failed`：动态显存分配层错误。可在启动器自定义参数中加入 `--disable-dynamic-vram`，重启后验证；不等于用户负向词写错，也不能笼统按分辨率过大处理。
- CUDA OOM：降低图改图比例或文生图宽高，再提交新批次。
- 少相册：确认照片权限为全部照片；手动刷新或切到后台后返回。App 使用公开 MediaStore 目录分类，厂商私有数据库中的纯虚拟分类不保证可见。
