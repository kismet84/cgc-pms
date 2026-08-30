# Ready Issues

## v1.6 当前队列

当前 Ready 为 0，用户直接授权执行中为 1；AutoPilot 执行中为 0。

- `ISSUE-100-001` 已由用户直接授权实施并进入 G5 Git 交付；不进入 AutoPilot Ready。G0-G3 已通过，G4 TLS/health 已取得，完整浏览器流待同 SHA CI 补证后关闭。

- `ISSUE-078-001`已完成G0～G5并关闭，不进入AutoPilot Ready。

- `ISSUE-077-001`已完成 G0～G5 并关闭，不进入 AutoPilot Ready。

- `ISSUE-069-001`、`ISSUE-070-001`、`ISSUE-071-001`均已关闭，不进入AutoPilot Ready。
- v1.5 Ready 与执行历史：[Backlog 快照](../archive/v1.5/backlog-snapshot/ready-issues.md)
- 候选、冻结项与发布门：[`current-issues.json`](current-issues.json)
- `REL-CREDENTIAL-ROTATION`、`REL-FILE-RESCAN`、`REL-TARGET-SHA-REVALIDATION` 当前均为 `FROZEN / NOT_APPLICABLE_LOCAL_ONLY`、`blocking=false`，不是 Ready；仅当用户明确修改根环境规则后重新评估。

新事项必须满足项目 Ready 契约后写入本文件，不得从历史计划或报告直接恢复。
