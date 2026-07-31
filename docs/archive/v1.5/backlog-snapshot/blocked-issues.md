# Blocked Issues

## v1.5 当前阻塞任务

### ISSUE-049-001：历史间接费结果表生产退役确认

- 优先级：P1
- 关联目标：第49条主线 `DBA-P1-001`；仅阻断 `overhead_allocation_record` 的生产物理删除，不阻断 V195—V210 实现验收或现行业务运行。
- 失败分类：`environment_prerequisite`（缺少生产等价数据和外部消费者证据）。
- 阻塞原因：本地 V210 表行数为 0，仓库运行时代码与测试均无直接表引用，现行间接费链已使用 `overhead_allocation_run` 与 `overhead_allocation_run_item`；但仓库外 BI、报表或历史接口依赖无法由本地事实证明。按“不确定内容需要人工确认”规则，不自动生成 DROP migration。
- 已完成证据：`scripts/database/database-remediation-preflight-v194.sql` 和 postflight 均检查该表行数；本地升级后 REVIEW 值为 0；结构已从运行逻辑隔离，继续保留不会影响当前正确性。
- 解除条件：在生产等价脱敏副本确认表为 0 行或完成受控归档，并由应用、BI、报表、ETL 负责人确认无外部读写消费者。
- 未完成验收项：生产等价依赖清单签字；如满足条件，追加独立 migration 物理退役并完成备份恢复、空库/升级库和报表回归。
- 安全恢复方式：未确认前保持表只读保留；退役版本必须独立发布，回滚以恢复同版本结构和已归档数据为主，不复用第49条主线中的其他迁移。

## 需要确认的非阻塞治理观察

- 当前分支保护 API 未返回 required pull-request review 和 push restrictions。旧阻塞的直接管理员绕过路径已由 `enforce_admins=true` 关闭，但是否额外要求审批人数或推送主体白名单需要仓库治理决策；未获明确授权前不修改远端设置。

## 已解除的历史阻塞

| Issue | 原失败分类 | 解除证据 | 当前状态 |
| --- | --- | --- | --- |
| ISSUE-037-021 | `quality_or_security`：测试共享数据污染及旧提交CI失败 | 本地隔离根因修复、顺序回归与全量验证通过；后续 PR #334 head `b1960ec7` 的11项required checks全绿并合并，旧run不再代表当前状态。正式证据见[M0状态归一化验收报告](../quality/mainline-40-m0-historical-blocker-normalization-acceptance-2026-07-13.md) | `VerifiedResolved` |
| ISSUE-053-012 | `quality_or_security`：计划快照权限、陈旧写保护、并发提交 | 用户已授权最小后端/Legacy兼容修复及本地dev/demo准备；快照改为`schedule:progress`，WBS/期间项/日报接通必填并发令牌，计划/期间/纠偏/日报提交加锁并重验状态；后端目标13项、Legacy API目标2项及类型检查通过 | `VerifiedResolved`，已补为Ready继续实施 |
| ISSUE-040-006 / V-06 | 外部前置：生产轮换证据缺失 | 用户明确将执行范围收敛到本机；local-dev 完成 MySQL/Redis/MinIO/JWT/Jasypt 真实轮换，74 表保留、依赖健康、旧 JWT 401、新登录 200、注入一致。当前无可识别生产环境，未来目标环境轮换改由上线门禁约束 | `VerifiedResolved`（本地 M1） |
| ISSUE-040-005 / V-04 | `tool_config`：浏览器控制入口未加载 | 2026-07-13 三角色均进入对应驾驶舱；系统管理/流程设计入口隐藏；直达 `/approval/process` 均转 `/403` 并显示无权访问；API 403 与前端 42 项同时通过 | `VerifiedResolved` |
| ISSUE-037-021-A | 真实质量类：`frontend-lint`、`frontend-test` 红灯 | PR #334 head `b1960ec7` 的两项 check 均为 `SUCCESS`，11 个 required checks 全绿 | `VerifiedResolved` |
| ISSUE-037-021-B | 真实质量类：付款申请页 E2E 契约红灯 | 同一目标提交的 `e2e` 为 `SUCCESS`，PR 合并门禁通过 | `VerifiedResolved` |
| ISSUE-037-021-C | 工具配置/治理类：管理员可绕过保护 | `master` 复读为 `enforce_admins=true`、`strict=true`、conversation resolution 已启用、force push/delete 禁止 | `VerifiedResolved` |

历史首次复验及失败分类仍保留在 [ISSUE-037-021 原报告](../quality/ISSUE-037-021-CI-CD与上线门禁v1.5复验报告.md)，不得再作为当前阻塞状态引用。

## 记录格式

后续每条活动阻塞必须包含：Issue、失败分类、阻塞原因、已完成证据、解除条件、未完成验收项和安全恢复方式。
