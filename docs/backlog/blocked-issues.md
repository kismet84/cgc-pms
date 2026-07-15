# Blocked Issues

## v1.5 当前阻塞任务

当前无已确认的 CI 合并门禁阻塞。

第40条主线 M0 于 2026-07-13 复核并关闭 `ISSUE-037-021-A/B/C`：PR #334 目标提交 11 个 required checks 全部成功，PR 已合并到 `master`；分支保护当前为 `strict=true`、`enforce_admins=true`、`required_conversation_resolution=true`，并禁止 force push/delete。正式证据见 [M0 状态归一化验收报告](../quality/mainline-40-m0-historical-blocker-normalization-acceptance-2026-07-13.md)。

## 需要确认的非阻塞治理观察

- 当前分支保护 API 未返回 required pull-request review 和 push restrictions。旧阻塞的直接管理员绕过路径已由 `enforce_admins=true` 关闭，但是否额外要求审批人数或推送主体白名单需要仓库治理决策；未获明确授权前不修改远端设置。

## 已解除的历史阻塞

| Issue | 原失败分类 | 解除证据 | 当前状态 |
| --- | --- | --- | --- |
| ISSUE-040-006 / V-06 | 外部前置：生产轮换证据缺失 | 用户明确将执行范围收敛到本机；local-dev 完成 MySQL/Redis/MinIO/JWT/Jasypt 真实轮换，74 表保留、依赖健康、旧 JWT 401、新登录 200、注入一致。当前无可识别生产环境，未来目标环境轮换改由上线门禁约束 | `VerifiedResolved`（本地 M1） |
| ISSUE-040-005 / V-04 | `tool_config`：浏览器控制入口未加载 | 2026-07-13 三角色均进入对应驾驶舱；系统管理/流程设计入口隐藏；直达 `/approval/process` 均转 `/403` 并显示无权访问；API 403 与前端 42 项同时通过 | `VerifiedResolved` |
| ISSUE-037-021-A | 真实质量类：`frontend-lint`、`frontend-test` 红灯 | PR #334 head `b1960ec7` 的两项 check 均为 `SUCCESS`，11 个 required checks 全绿 | `VerifiedResolved` |
| ISSUE-037-021-B | 真实质量类：付款申请页 E2E 契约红灯 | 同一目标提交的 `e2e` 为 `SUCCESS`，PR 合并门禁通过 | `VerifiedResolved` |
| ISSUE-037-021-C | 工具配置/治理类：管理员可绕过保护 | `master` 复读为 `enforce_admins=true`、`strict=true`、conversation resolution 已启用、force push/delete 禁止 | `VerifiedResolved` |

历史首次复验及失败分类仍保留在 [ISSUE-037-021 原报告](../quality/ISSUE-037-021-CI-CD与上线门禁v1.5复验报告.md)，不得再作为当前阻塞状态引用。

## 记录格式

后续每条活动阻塞必须包含：Issue、失败分类、阻塞原因、已完成证据、解除条件、未完成验收项和安全恢复方式。
