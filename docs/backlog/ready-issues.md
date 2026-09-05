# Ready Issues

## v1.6 当前队列

当前 Ready 为 0，用户直接授权执行中为 1；AutoPilot 执行中为 0。

- `ISSUE-100-001` 与 M6 已由用户直接授权实施；不进入 AutoPilot Ready。MySQL8.4.12/ConnectorJ26.7 支持与隔离跨引擎恢复已取得证据；新组合完整服务端/浏览器/同SHA CI与G5尚未完成，无待用户答复的同范围授权。
  当前按已批准的 [M6 MySQL 8.4 升级计划](../plans/第100条主线-M6-MySQL8.4升级与ConnectorJ安全版本恢复任务计划书-2026-09-06.md)补齐精确镜像扫描绑定与完整对象语义复验；不把计划或候选试验通过视为 Ready 或 G5 通过。

- `ISSUE-078-001`已完成G0～G5并关闭，不进入AutoPilot Ready。

- `ISSUE-077-001`已完成 G0～G5 并关闭，不进入 AutoPilot Ready。

- `ISSUE-069-001`、`ISSUE-070-001`、`ISSUE-071-001`均已关闭，不进入AutoPilot Ready。
- v1.5 Ready 与执行历史：[Backlog 快照](../archive/v1.5/backlog-snapshot/ready-issues.md)
- 候选、冻结项与发布门：[`current-issues.json`](current-issues.json)
- `REL-CREDENTIAL-ROTATION`、`REL-FILE-RESCAN`、`REL-TARGET-SHA-REVALIDATION` 当前均为 `FROZEN / NOT_APPLICABLE_LOCAL_ONLY`、`blocking=false`，不是 Ready；仅当用户明确修改根环境规则后重新评估。

新事项必须满足项目 Ready 契约后写入本文件，不得从历史计划或报告直接恢复。
