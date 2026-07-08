# ISSUE-004-008 签证变更成本与收入调整回归

日期：2026-07-09
结论：通过
阻塞：非阻塞

## 目标

- 回归签证、合同变更对成本与收入调整链路的影响，确保调整结果与来源单据一致。
- 覆盖重复审批回调下的重复累计或漏记风险。
- 不扩大为收入、成本、合同模块重构，不修改既有 migration。

## 修改范围

- `backend/src/main/java/com/cgcpms/contract/change/handler/CtContractChangeWorkflowHandler.java`
  - 增加合同变更审批回调幂等保护：当变更已 `APPROVED`、已生效且成本已生成时直接返回，避免重复递增合同 `currentAmount`。
- `backend/src/test/java/com/cgcpms/contract/change/handler/CtContractChangeWorkflowHandlerTest.java`
  - 新增合同变更审批重复回调回归：断言合同现行金额只增量一次，`CT_CHANGE` 成本调整只生成一条，来源项目、合同、金额、状态一致。
- `backend/src/test/java/com/cgcpms/variation/handler/VarOrderWorkflowHandlerTest.java`
  - 新增 COST 方向签证审批回归：断言两条签证明细生成两条 `VAR_ORDER` 成本项，金额合计等于来源明细合计，重复回调不重复生成。
- `backend/src/test/java/com/cgcpms/revenue/ContractRevenueServiceTest.java`
  - 新增收入确认重复回调回归：断言 `CT_REVENUE` 收入调整项与收入确认单金额、项目、合同一致，重复回调不重复生成。

## TDD 记录

- RED：`cd backend; .\mvnw.cmd "-Dtest=CtContractChangeWorkflowHandlerTest,VarOrderWorkflowHandlerTest,ContractRevenueServiceTest" test`
  - 新增 `CtContractChangeWorkflowHandlerTest.testOnApproved_ContractChangeAdjustsCostOnce` 失败，合同 `currentAmount` 在重复回调后被二次递增。
  - 同次运行中 `ContractRevenueServiceTest.testSubmitForApproval` 失败，属于既有收入审批提交测试断言与当前工作流错误码不一致，非本 Issue 新增收入幂等断言失败。
- GREEN：增加合同变更已审批/已生效/已生成成本的幂等退出后，目标等价验证通过。

## 验证证据

- `cd backend; .\mvnw.cmd "-Dtest=CtContractChangeWorkflowHandlerTest,VarOrderWorkflowHandlerTest,ContractRevenueServiceTest#testOnApproved_RevenueAdjustmentIsIdempotent" test`
  - 通过，`15` 个用例通过。
  - 覆盖合同变更成本调整、签证成本调整、收入确认调整三条目标链路。
- `cd backend; .\mvnw.cmd test`
  - 未通过；失败类集中在既有 `dashboard`、`invoice validation`、`migration`、`payment`、`purchase`、`revenue submitForApproval`、`workflow` 集成测试。
  - 本轮目标类中新增回归断言已通过；全量红灯按既有无关失败分类，不阻塞本 Issue 收口。
- `git diff --check`
  - 通过。

## 一致性口径

- 合同变更：
  - 来源单据为 `CT_CHANGE`。
  - 审批通过后只对合同 `currentAmount` 做一次 `changeAmount` 增量。
  - 成本调整项使用 `sourceType=CT_CHANGE`、`sourceId=changeId`，金额等于 `changeAmount`，状态为 `CONFIRMED`。
  - 重复审批回调不重复递增合同金额，也不重复生成成本项。
- 签证变更：
  - 来源单据为 `VAR_ORDER`。
  - 仅 `direction=COST` 的签证进入成本生成链路。
  - 每条签证明细生成一条成本项，`sourceItemId` 指向原始明细，金额合计等于来源明细合计。
  - 重复审批回调依赖来源唯一约束与策略幂等，不重复累计。
- 收入确认：
  - 来源单据为 `CT_REVENUE`。
  - 审批通过后生成 `REVENUE_CONFIRMED` 调整项，金额等于 `revenueAmount`。
  - 重复回调通过 `PENDING -> APPROVED` CAS 状态迁移幂等退出，不重复生成收入调整项。

## 边界说明

- 未修改 migration、deploy、生产凭据或外部平台配置。
- 未连接生产数据库。
- 未放宽租户、项目、权限或业务状态边界。
- 未做收入、成本、合同模块重构；只补充合同变更审批回调幂等保护和目标回归测试。

## 剩余风险

- 全量后端测试仍有既有无关红灯，需按对应 Ready Issue 分别治理。
- 本轮未扩展收入确认提交审批的既有失败断言；收入调整链路以 `onApproved` 审批回调幂等为验收口径。
