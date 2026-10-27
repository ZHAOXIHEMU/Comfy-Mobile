# 翻译链、规则书和 Excel

```mermaid
flowchart LR
 A[手机原文] --> B[ExcelPromptGlossary]
 B -->|PROMPT 与 SYSTEM_PROMPT| C[LlamaCPPEngine]
 B -->|CHECK_DATA| D[ExcelTranslationCheck]
 C -->|RESPONSE| D
 D -->|TEXT| E[可选画师节点或正向编码]
```

ExcelPromptGlossary 在本机读取工作簿：按名称和别名匹配，优先长名称，避免重叠；使用作品关联消歧，保留权重／标签等受保护片段，把需要锁定的实体替换为占位符，产生检查数据及规则书约束。

LlamaCPPModelLoader 读取电脑选定的 GGUF，LlamaCPPOptions 可设置 CPU 翻译（n_gpu_layers=0）。本项目不把 GGUF 放进 APK。ExcelTranslationCheck 校验和还原占位符，再输出给后续编码；不要将原始 RESPONSE 直接绕过检查节点连接到编码器。`Anima 混合` 与 `纯英文翻译` 是节点的两种模式。

节点的 IS_CHANGED 同时检查 Excel 和规则书的路径、修改时间纳秒值及文件大小；文件内容保存后会使对应缓存失效。下一批次也会重新读取已保存的翻译模型和节点设置。

## 电脑工作簿（随本项目提供）

中文名、英文名、日文名三个 Sheet 使用严格 A1:L1 表头：

`名称、别名1、别名2、别名3、别名4、别名5、实体ID、类型、作品ID、Anima名称、来源、说明`

数据从第 2 行开始。三张表的对应行必须有一致的实体ID、类型、作品ID、Anima名称；实体ID 唯一；类型为角色或作品；角色的作品ID 引用作品行。可选“术语”Sheet 的列为：

`中文术语、别名1、别名2、别名3、别名4、别名5、术语ID、英文术语、日文术语、说明、来源`

不使用公式。节点对错位、重复 ID、无效作品引用等给出明确错误。

## 手机设置里的“提示词库”是另一套格式

手机本地导入 `.xlsx/.xls`，只用于输入时联想，不负责电脑翻译。它要求三个 Sheet：中文名／英文名／日文名，无表头，从第 1 行开始，同一行对齐实体，每列一个别名。**不能直接把电脑的带表头增强词典当作手机无表头词库导入**。

设置里的独立“翻译器”页面仍是交互示意，没有接入真实翻译；真实翻译由上述电脑工作流链执行。
