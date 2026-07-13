# 第32条主线 M2：后端全量测试红灯专项归因裁决报告

## 结论

结论：M2 通过，已形成独立专项入口。

阻塞：非阻塞。本轮未修复业务代码，只把散落在 `ISSUE-008-006/007/008/009` 报告尾注里的后端全量红灯归并为专项 Ready Issue。

裁决：当前红灯不能整体定性为业务代码失败。按 surefire 证据，主要属于测试夹具债、断言漂移、历史集成链路口径漂移；其中 invoice / migration / phase 链路存在真实业务口径需要复核的风险，需后续实现型子任务逐项验证。

## 证据

- 必读规则：`AGENTS.override.md`、`AGENTS.md`。
- 计划书：`docs/plans/第32条主线-既有未解决问题统一治理任务计划书.md`。
- 质量报告：`docs/quality/issue-008-006-规则治理中心.md`、`docs/quality/issue-008-007-通知平台.md`、`docs/quality/issue-008-008-wbs-进度计划与甘特图.md`、`docs/quality/issue-008-009-供应商评分与采购增强.md`。
- Backlog：`docs/backlog/ready-issues.md`、`docs/backlog/blocked-issues.md`、`docs/backlog/current-focus.md`。
- Surefire：`backend/target/surefire-reports`，报告时间集中在 `2026-07-09 15:26`，汇总 `1566` tests、`11` failures、`29` errors、`1` skipped。

本轮未重新执行 `cd backend; .\mvnw.cmd test`。原因：本地已有同日 surefire 全量报告，且四份质量报告已反复记录同一批失败域；复用现有报告足以完成 M2 归因入口，重复跑全量只增加成本。

## 失败域分组

| 失败域 | 失败类 | 失败形态 | 分类 | 后续入口 |
| --- | --- | --- | --- | --- |
| workflow | `WorkflowEngineIntegrationTest`、`WorkflowCoreServiceTest`、`WorkflowConcurrencyTest`、`WorkflowApproverResolverTest`、`WorkflowTemplateManagementTest`、`Phase4IntegrationTest` | `审批业务对象不存在`、`不支持的业务类型`、错误码从 `NO_APPROVER/TEMPLATE_NOT_FOUND` 漂移到 `UNSUPPORTED_BUSINESS_TYPE` | 测试夹具债 / 断言漂移，需复核业务类型注册边界 | `ISSUE-032-001` |
| invoice / migration | `InvoiceValidationTest`、`MigrationSoftDeleteBehaviorTest` | 查询接口期望 `200` 实际 `400`；软删除重建报 `发票缺少项目关系` | 接口契约断言漂移 / migration 兼容性风险，需复核发票项目关系约束 | `ISSUE-032-002` |
| dashboard / purchase / revenue | `DashboardChiefEngineerServiceTest`、`PurchaseRequestServiceTest`、`ContractRevenueServiceTest` | `No value present`、`项目不存在`、审批提交断言失败 | 测试夹具债 / 种子数据前置缺失，可能夹带业务口径漂移 | `ISSUE-032-003` |
| phase 集成链路 | `Phase2FullChainIntegrationTest`、`Phase4IntegrationTest` | 合同可用余额、付款审批状态、审批业务对象链路失败 | 历史集成测试链路口径漂移，需按真实主链路复核 | `ISSUE-032-004` |

## 优先级

1. `ISSUE-032-001`：先收敛 workflow 业务对象 / 业务类型测试夹具。当前 40 个红灯中 workflow 占比最高，并会带出多条链式失败。
2. `ISSUE-032-002`：处理 invoice / migration 的项目关系与接口契约。该域触达发票软删除重建和查询契约，风险高于普通断言漂移。
3. `ISSUE-032-003`：收敛 dashboard / purchase / revenue 的种子数据前置，先证明是否只是夹具债。
4. `ISSUE-032-004`：最后复核 phase 集成链路，避免在底层夹具未收敛前反复修历史链路。

## 验收口径

- 单个专项通过不要求 `.\mvnw.cmd test` 立即全绿，但必须证明对应失败类从 surefire 失败清单中消失，且不得引入新的同域失败。
- 若实现中确认是环境前置、命令调用或 Ready Issue 配置问题，应转入对应分类，不得硬改业务代码。
- 若触及审批状态机、发票项目关系、金额口径或租户隔离，应升为真实质量 / 安全类复核。
