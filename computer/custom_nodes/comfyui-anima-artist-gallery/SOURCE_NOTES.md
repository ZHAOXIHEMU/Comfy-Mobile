# 早期例图生成与来源记录

以下保留原有说明，数量与当时版本对应，当前数量以 artists.json 为准。

# Anima 本地画师图库

40位画师，40张本机生成例图，加一张不加画师的对照图。选择器提供中文/外文名称搜索、可点击例图、☆置顶与★取消、只看置顶、不添加画师选项。置顶存储在插件目录 pinned.json，同一后端的电脑/手机共用，重启后保留。

已安装到 custom_nodes/comfyui-anima-artist-gallery。完全重启ComfyUI并刷新网页后，打开《手机Anima文生图_画师图库》。新工作流来自你最近一次成功运行的含翻译版本；原工作流未覆盖，示例文字已换成你指定的明日香黄裙场景。

连接：Excel → Qwen → 翻译结果检查TEXT → 画师选择器prompt → TEXT → 正面文本编码。节点会输出 @yoneyama mai, 后接原译文，不会把画师名和正文粘在一起；相同画师已经在最前面时不重复添加。不选择画师时原文直通。已有其他画师前缀不会擅自删除，因此可能形成多画师组合。

只想复制一个节点可打开《画师选择器_单节点可复制.json》，选中复制到已有画布，再接上上述两条线。不要直接用Qwen的原始RESPONSE绕过翻译结果检查，否则可能出现未还原的占位符。

例图：使用已有 anima-base-v1.0.safetensors，832×1216、30步、CFG4、er_sde/simple、固定种子2026102401；仅改变画师前缀。服装、酒杯、镜面水晶鞋和玻璃温室提示词相同，但不同画师下动作和细节会有偏差。没有挑不同种子来美化某位画师。高脚杯中指定的是橙汁。

来源：[Anima官方模型卡](https://huggingface.co/circlestone-labs/Anima)要求艺术家标签加@，标签用小写和空格；[社区画师数据](https://github.com/nregret/Comfyui-Anima-Tools/blob/main/js/data.js)用于核对准确标签；[Anima Style Explorer](https://anima.mindspark.fyi/)用于参考同条件风格比较方法。Pixiv插画日榜2026-10-23快照也作了参考（[日榜入口](https://www.pixiv.net/ranking.php?mode=daily&content=illust)）：Sa-fu、SWAV、七原しえ、mignon分别出现在第4、13、17、19位。这里不是Pixiv热度榜排名；日榜热度也不是Anima风格还原强度。各条目保留社区来源与榜单参考信息。

例图全部由本地模型生成，不是从Pixiv下载的画师原作。画师名对应的是模型学到的风格响应，不保证身份、服装或动作全部精确，画师本人没有为这些例图背书。


节点内预览与画师选择面板均加载832×1216原始PNG文件，完整保留原图像素和文件内容；仅通过界面缩放显示，不再加载缩略图。修改后需重启ComfyUI并刷新网页。
