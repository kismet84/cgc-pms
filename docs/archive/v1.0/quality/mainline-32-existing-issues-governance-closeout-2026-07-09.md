# 第32条主线既有未解决问题统一治理最终收口裁决报告

报告日期：2026-07-09
报告类型：最终验收 / 收口裁决
报告边界：仅核对第32条主线 M1-M6 正式交付物、backlog 状态与质量报告；本报告不修改代码、配置、脚本、运行环境或 Git 状态。

## 1. 最终裁决

结论：不通过完全验收。
阻塞：阻塞，阻塞项为 `ISSUE-032-005：M3 财务真实角色缺失导致真实角色抽样无法完成`。
是否可收口：可带阻塞归档收口，但不得宣告第32主线完全通过，也不得把 M3 说成非阻塞。
是否可上线：需要确认。本报告只裁决治理主线状态，不替代生产发布裁决；M3 财务真实角色未验收前，涉及财务角色体验、财务驾驶舱和相关入口的上线结论不得放行。

裁决依据：

1. 第32计划书最终验收口径 1、2、4、5、6 已有正式证据满足。
2. 第32计划书最终验收口径 3 未满足：采购、生产真实角色已完成抽样，财务真实角色因缺少非超管 `FINANCE` 账号未完成，且已在 `docs/backlog/blocked-issues.md` 记录为前置阻塞。
3. M3 报告明确裁决为 `不通过 / 前置阻塞`，不能用超管 `dev-login` 或非财务账号替代财务角色验收。

## 2. M1-M6 验收核对

| 阶段 | 计划书最终口径 | 核对结果 | 结论 |
| --- | --- | --- | --- |
| M1 | `ISSUE-008-008`、`ISSUE-008-009` 的 backlog 状态完成正式同步 | `docs/backlog/ready-issues.md` 中两项均已在历史区标为 `Done`，并保留对应质量报告入口 | 通过 |
| M2 | 后端全量测试红灯被拆成独立专项，不再只是分散备注 | `mainline-32-m2-backend-full-test-red-triage-2026-07-09.md` 已按 workflow、invoice/migration、dashboard/purchase/revenue、phase 集成链路分组，并形成 `ISSUE-032-001~004` | 通过 |
| M3 | 真实采购、生产、财务角色完成至少一轮正式抽样复核，或明确记录前置阻塞 | 采购、生产通过；财务无真实非超管账号，`mainline-32-m3-real-role-sampling-2026-07-09.md` 与 `blocked-issues.md` 均记录前置阻塞 | 不通过 / 前置阻塞 |
| M4 | Mockito 动态 agent 与 generated password 提示进入独立治理项 | `mainline-32-m4-build-compatibility-debt-2026-07-09.md` 已分类为非阻塞技术债，并形成 `ISSUE-032-006` | 通过 |
| M5 | 已做最小回归的平台项与长期储备项完成分层冻结 | `mainline-32-m5-product-capability-layering-2026-07-09.md` 已冻结 A/B 类口径，`current-focus.md` 与长期计划已有对应说明 | 通过 |
| M6 | UI 一致性、可访问性、登录页品牌、`440px` 布局、覆盖率、E2E CI 形成统一复验与治理入口 | `mainline-32-m6-ui-quality-regression-entry-2026-07-09.md` 已拆成 UI/体验基线与自动化/门禁基线，并形成 `ISSUE-032-007~008` | 通过入口建立，未代表实测通过 |

## 3. 正式交付物核对

| 交付物 | 核对结论 |
| --- | --- |
| `docs/plans/第32条主线-既有未解决问题统一治理任务计划书.md` | 已定义 Goal、Architecture、M1-M6 和最终验收口径 |
| `docs/backlog/ready-issues.md` | 已承接 M2、M4、M6 后续治理入口；`ISSUE-008-008/009` 已不再作为 Ready 假阻塞 |
| `docs/backlog/blocked-issues.md` | 已记录 `ISSUE-032-005` 财务真实角色账号缺失阻塞，分类为环境前置类 |
| `docs/backlog/current-focus.md` | 已记录 M5 分层冻结与 M6 复验顺序 |
| `docs/backlog/cgc-pms-production-enhancement-plan.md` | 已补充中期/长期能力冻结口径 |
| `docs/quality/mainline-32-m2-backend-full-test-red-triage-2026-07-09.md` | M2 专项入口通过 |
| `docs/quality/mainline-32-m3-real-role-sampling-2026-07-09.md` | M3 不通过 / 前置阻塞 |
| `docs/quality/mainline-32-m4-build-compatibility-debt-2026-07-09.md` | M4 非阻塞技术债入口通过 |
| `docs/quality/mainline-32-m5-product-capability-layering-2026-07-09.md` | M5 分层冻结通过 |
| `docs/quality/mainline-32-m6-ui-quality-regression-entry-2026-07-09.md` | M6 统一复验入口通过 |

## 4. 阻塞与剩余风险

阻塞项：

1. `ISSUE-032-005`：缺少绑定 `FINANCE` 的非超管真实账号，导致财务角色抽样无法完成。

解除条件：

1. 在 dev/test 运行态新增或确认一个真实 `FINANCE` 非超管账号。
2. 使用该账号完成 `/dashboard` 财务标签、`/api/dashboard/finance`、付款/发票/结算相关入口抽样。
3. 更新 M3 正式质量报告，并按复验结论同步 backlog 状态。

非阻塞剩余风险：

1. M2 后端全量红灯只是完成专项拆解，具体红灯仍需 `ISSUE-032-001~004` 实施型任务逐项收敛。
2. M4 只是把构建兼容性提示纳入治理入口，Mockito 动态 agent 与 generated password 提示尚未完成实现治理。
3. M6 只是建立复验入口，UI/可访问性/移动端/覆盖率/E2E CI 尚未完成当前实测基线。
4. M5 长期储备项仍冻结；未来若解除冻结，需要重新补齐业务前置、数据前置、验收口径和风险边界。

## 5. 收口结论

正式交付物：第32计划书、M2-M6 质量报告、backlog/current-focus/长期计划状态更新、本文最终收口裁决报告。
验收证据：M1-M6 逐项核对；M3 财务账号缺失已进入 blocked；M2/M4/M6 已形成 Ready 入口；M5 已完成分层冻结。
临时产物：无新增临时日志、截图、缓存或测试产物。
结论：不通过完全验收。
阻塞：阻塞。
是否可收口：可带阻塞归档收口；不可宣告完全通过。
剩余风险：财务真实角色未补验前，财务角色体验和财务驾驶舱相关结论不可放行；后续治理任务仍需按 `ISSUE-032-001~004`、`ISSUE-032-006~008` 分别执行与验收。
