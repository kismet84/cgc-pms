# Blocked Issues

## v1.6 当前本地环境状态

[`current-issues.json`](current-issues.json) 是状态唯一事实源。项目当前仅有本地开发环境，以下非本地事项均冻结、不阻塞当前本地工作：

| Issue | 状态 | 当前口径 |
| --- | --- | --- |
| `REL-CREDENTIAL-ROTATION` | `FROZEN / NOT_APPLICABLE_LOCAL_ONLY` | 不得执行；仅当用户明确修改根环境规则并提供非本地环境后重新评估 |
| `REL-FILE-RESCAN` | `FROZEN / NOT_APPLICABLE_LOCAL_ONLY` | 不得执行；仅当用户明确修改根环境规则并提供非本地环境后重新评估 |
| `REL-TARGET-SHA-REVALIDATION` | `FROZEN / NOT_APPLICABLE_LOCAL_ONLY` | 同 SHA CI 由具体 Git 交付任务处理；不得规划目标环境复验 |

## 其他冻结事项

### `AUDIT-PROMETHEUS-SCRAPE-AUTH`

- 状态：`FROZEN / OPERATIONAL_RISK`，`blocking=false`。
- 非本地监控网络、Secret 和真实运行验收当前不适用；除非用户明确修改根环境规则，否则不得重开。

v1.5 已解除阻塞及完整历史见 [Backlog 快照](../archive/v1.5/backlog-snapshot/blocked-issues.md)。
