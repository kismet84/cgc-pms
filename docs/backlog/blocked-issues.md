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

### ISSUE-037-021（2026-07-16 复发）：本地测试隔离已修复，master CI 待新提交复验

- 优先级：P0
- 失败分类：`quality_or_security`（真实质量/测试隔离）；远端失败已精确定位为测试共享数据污染导致的顺序依赖。整份日志下载异常仍单独归类为 `environment_prerequisite`，不再影响业务根因裁决。
- 阻塞原因：本地根因与回归已关闭，但 `master` 当前仍是提交 `ed47aabb39198f343742009d52c68080530b79a9`；其 [CI run #186](https://github.com/kismet84/cgc-pms/actions/runs/29428204584) 为 failure，required job `backend-test` 失败，依赖它的 `supply-chain-security` 被跳过。用户明确要求暂不提交、不推送，因此当前没有包含修复的新提交可供 11 个 required checks 复验，仍不具备上线门禁通过证据。
- 已完成证据：远端唯一失败已定位为 `DashboardMaterialRoleServiceTest.testDefaultDemoProject_DashboardRealisticDemoDistribution` 第 349 行，Maven 汇总 1814 项、失败 1、错误 0、跳过 3；修复前按 `StlSettlementServiceTest` 后接 Dashboard 的顺序可稳定复现。2026-07-16 已在当前功能分支完成测试隔离修复：`StlSettlementServiceTest` 不再改写共享 `sub_measure.status`，只临时设置结算前置校验实际需要的 `approval_status`，并在每个测试结束后按原值恢复。修复后目标类 13 项全绿；相同显式顺序回归 26 项全绿；`backend\\mvnw.cmd -C verify` 为 1814 项、失败 0、错误 0、跳过 1；`git diff --check` 通过。
- 根因：`StlSettlementServiceTest#setUp` 原先在每个测试前把租户 0、合同 30001 的全部 `sub_measure` 同时改为 `approval_status='APPROVED'`、`status='CONFIRMED'`，且不恢复；V105 Dashboard 演示数据使用同一合同，原有 `APPROVING/CONFIRMED/DRAFT` 三态被压成单态。修复采用原值快照/逐项恢复，不使用无法覆盖并发子线程提交的类级测试事务。
- 当前未完成原因：用户授权了本地修复与复验，但明确要求暂不提交、不推送；不得自行创建远端验证提交、触发等价 workflow 或改变 `master`。
- 解除条件：获得提交与推送授权后，将已验证修复纳入等价新提交，并让 11 个 required checks 全部成功且 `supply-chain-security` 实际执行；若远端仍失败，必须基于新提交绑定日志重新分类，不复用旧 run 结果。
- 未完成验收项：修复提交的远端 required checks 全绿、`supply-chain-security` 实际执行结果。
- 安全恢复方式：根因日志已取得，不再重复下载整份日志或 artifact。若未来同类入口稳定超时，使用已登录 job 页面定位失败 step 临时日志入口，确认 Range 支持后先取末段 `256 KB`，摘要不足时只扩大一次；GitHub 仓库 SSH URL 只服务 Git 传输，切换 `origin` 不能修复 Actions 日志入口。临时签名 URL 不写入长期载体。
- 恢复复核：2026-07-16 本地目标、显式失败顺序和全量验证均已通过；当前剩余阻塞是发布级远端证据缺失，不再是本地实现或测试隔离未完成。旧 run 只能证明旧提交失败，不能替代修复提交的 CI 裁决。

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
