# Blocked Issues

## v1.6 当前生产发布门

以下三项由 [`current-issues.json`](current-issues.json) 的稳定键承接，继续阻塞生产：

| Issue | 状态 | 解除条件摘要 |
| --- | --- | --- |
| `REL-CREDENTIAL-ROTATION` | `RELEASE_GATE` | 目标环境凭据轮换、旧值失效和双人复核证据 |
| `REL-FILE-RESCAN` | `RELEASE_GATE` | 生产存量文件逐对象真实病毒复扫与处置证据 |
| `REL-TARGET-SHA-REVALIDATION` | `RELEASE_GATE` | 同一待发布 SHA 的 CI、目标环境迁移、真实角色、备份恢复和回滚证据 |

## 其他需要确认事项

### ISSUE-049-001：历史间接费结果表生产退役确认

- 状态：`environment_prerequisite`。
- 本地已证明现行链路不依赖 `overhead_allocation_record`，但仓库外 BI、报表或 ETL 消费者需要目标环境签认。
- 未确认前保留该表；不得自动生成 DROP migration。

### `AUDIT-PROMETHEUS-SCRAPE-AUTH`

- 状态：`NEEDS_CONFIRMATION`。
- 需要目标环境明确 Prometheus 抓取身份、Secret 责任和网络边界后再决策。

### 分支保护治理观察

- 当前管理员保护已启用；是否追加审批人数或推送主体白名单仍需仓库治理决策。

v1.5 已解除阻塞及完整历史见 [Backlog 快照](../archive/v1.5/backlog-snapshot/blocked-issues.md)。
