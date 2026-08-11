# 灵感与想法暂存

> 本文仅记录个人灵感草稿，不属于正式需求、Backlog、实施计划或实施授权。

## 2026-08-11：付款审批完成后自动发送附件到飞书群

- 状态：暂存，待评估
- 想法：付款审批终结且业务事务提交成功后，由后台任务驱动脚本，将最终审批附件发送到指定飞书群。
- 最小流程：`审批完成 -> 待发送任务 -> 后台脚本 -> 上传附件取得 file_key -> 按 chat_id 发送群消息 -> 记录发送结果`
- 技术倾向：固定规则使用脚本或后台任务，不需要 Agent；附件上传与发送直接调用飞书 OpenAPI。
- 已知限制：飞书 CLI 的 OpenAPI MCP 当前支持发送消息，但暂不支持图片或文件上传下载，因此本地附件不能只依赖 CLI 完成。
- 可靠性边界：发送动作不得阻塞或回滚付款审批；需要防重复、失败重试、审计记录和人工补发入口。
- 安全边界：仅允许发送审批终结版本附件；应用密钥不得写入参数、代码或日志；机器人必须已加入目标群并具备发言权限。

### 待确认

- 目标群是全公司固定群、按项目配置，还是按付款类型配置？
- 发送全部附件，还是仅发送指定类型或最终版本附件？
- 消息是否需要包含付款编号、项目、收款方、金额和审批人摘要？
- 失败重试次数、人工补发方式和发送结果查看入口如何设置？

### 官方能力参考

- [上传文件](https://open.feishu.cn/document/uAjLw4CM/ukTMukTMukTM/reference/im-v1/file/create)
- [发送消息](https://open.feishu.cn/document/server-docs/im-v1/message/create?lang=zh-CN)
- [飞书 CLI OpenAPI MCP 概述](https://open.feishu.cn/document/mcp_open_tools/mcp-overview?lang=zh-CN)
