# 可选画师与例图

电脑节点目录：`computer/custom_nodes/comfyui-anima-artist-gallery/`。`artists.json` 保存 ID、中文／外文标签、option、来源与可选 visual_features；`previews/{id}.png` 是对应例图，`web/artist_gallery.js` 是 ComfyUI 面板。

AnimaArtistGallery 接收已校验的译文：无画师时原文直通；选择后前置 `@artist, ` 及该条目的可选视觉特征。相同前缀不重复添加。它位于翻译之后，因此画师名不会再被翻译器错误改写。

接口：`GET /anima_artist_gallery/catalog`、`GET /anima_artist_gallery/preview/{id}`、电脑端面板的 `POST /anima_artist_gallery/pin`。目录给出 preview_version，手机按服务器、画师ID、版本形成缓存键，未变化的 PNG 不重复下载。图片先写临时文件，校验后提交；清除操作使下载代次失效，旧下载不得恢复已清空内容。

手机收藏独立保存在本机，第一次同步以电脑 pinned 标记初始化；收藏自动排最前。清除画师数据同时清例图、目录、手机收藏和当前选择，重开也保持清空，直到电脑重新同步。此操作不删除生成图库。

本包例图是本机 Anima 同条件生成的效果比较，不是画师原作，也不代表画师背书。保留电脑节点原有来源说明。`previews/` 是包中主要体积来源，模型权重未包含。
