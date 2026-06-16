# msg-011-main-agent-deserialization-error-diag

## 诊断：主 Agent 线程历史 JSON 反序列化失败

### 错误信息

```
Failed to deserialize the JSON body into the target type:
messages[46]: unknown variant `image_url`, expected `text` at line 1 column 1669019
```

### 根因分析

主 Agent 线程（019ebc4b-a8d8-7a61-9fd3-b6b5e2a172db）的消息历史中，第 46 条消息（从 0 开始计数）的内容项（items）包含了 `"type": "image_url"` 的条目，但该线程的通信协议目前只接受 `"type": "text"`。

这通常由以下原因之一导致：

1. 某个 agent 或用户在回复中嵌入了图片（如截图），该图片被序列化为 `image_url` 类型的内容项。
2. 消息构造时误用了非文本类型的输入项（如 `local_image`、`image`），而目标线程的 schema 未声明支持。

### 建议处理步骤

1. 定位来源：检查第 46 条消息是由哪个 agent 或用户在什么时间发送的，确认图片内容的来源和意图。
2. 修复方式（二选一）：
   - 如果能定位到原始发送者，让发送者将图片内容改为文件路径链接 + 纯文本描述后重发。
   - 如果无法追溯发送者，可以考虑 fork 主 Agent 线程到第 45 条消息之前的状态，从干净断点继续工作。
3. 预防措施：
   - 在协同规则中明确：agent 间通信只允许 `text` 类型内容项；截图、图片等应写入文件，只传递文件路径。
   - 检查 `docs/agents/message-protocol.md` 是否已约束消息内容类型，如未约束则补充。

### 影响范围

- 主 Agent 线程当前不可用，无法接收和处理新的消息。
- 子 Agent（development / testing）等待中的任务不会自动恢复，需要主 Agent 恢复后重新派发。

### task-014 状态

task-014（项目管理与目标管理 UI 重设计）已由外部用户验证提交并闭环，implementation report 在：

`D:\projects-test\cgc-pms\.agent-runtime\reports\task-014-project-target-ui-redesign-implementation-result.md`

主 Agent 恢复后无需为该任务重新派发或 rework。
