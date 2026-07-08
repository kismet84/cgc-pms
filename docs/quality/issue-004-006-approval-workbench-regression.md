# ISSUE-004-006 审批中心待办/已办/我发起统一筛选回归报告

日期：2026-07-08

## 目标

验证审批中心待办、已办、抄送、我发起四类工作台查询口径：

- 分页 `total` 与记录来源一致。
- 统一筛选条件不突破租户、项目、审批参与人边界。
- 前端审批工作台筛选、详情展示、确认动作入口保持冻结口径。
- 后端查询回归与前端工作台回归分开记录。

## 失败分类与修正

失败分类：Ready Issue 测试配置问题已更正。

首轮后端验证失败集中在 `WorkflowQueryServiceTest#setUp -> seedTemplateAndSubmit`：测试仍使用虚构业务类型 `WQ_TEST_APPROVAL` 调用真实 `workflowEngine.submit`，被当前 `WorkflowBusinessAccessValidator` 拒绝为 `UNSUPPORTED_BUSINESS_TYPE`。

最小修正：

- `WorkflowQueryServiceTest` 改用真实 `WorkflowBusinessTypes.CONTRACT_APPROVAL`。
- 为测试 submit 的 businessId 插入最小合同测试数据，满足真实业务对象校验。
- 本测试类运行期间临时禁用合同审批种子模板 `50001`，确保查询服务测试继续使用一节点测试模板；清理时恢复。
- cleanup 只删除 `33333001..33333025` 测试 businessId 范围，避免误删 Flyway 的 V107 演示审批待办。

## 回归证据

| 范围 | 断言位置 | 断言摘要 |
| --- | --- | --- |
| 待办 | `WorkflowQueryServiceTest#getMyTodosSupportsUnifiedFilters` | keyword、businessType、instanceStatus、时间范围与当前审批人边界同时生效 |
| 已办 | `WorkflowQueryServiceTest#getMyDoneSupportsUnifiedFilters` | 已办只返回当前操作人记录，状态筛选不混入运行中实例 |
| 我发起 | `WorkflowQueryServiceTest#getMyStartedSupportsUnifiedFilters` | 只返回当前发起人，分页与状态筛选保持一致 |
| 抄送 | `WorkflowQueryServiceTest#getMyCcSupportsUnifiedFilters` | 只返回当前抄送人记录，状态筛选与 businessType 同时生效 |
| 详情权限 | `WorkflowQueryServiceTest#getInstanceDetailParticipantWithoutProjectAccessDenied`、`getInstanceDetailParticipantWithProjectAccessCanView` | 参与人仍受项目访问权限控制 |
| 前端工作台 | `ApprovalWorkList.test.ts`、`ApprovalConfirm.test.ts`、`workflowDisplay.test.ts` | tab、筛选参数、详情显示、确认动作和业务类型展示保持冻结口径 |

## 修改摘要

- `backend/src/test/java/com/cgcpms/workflow/WorkflowQueryServiceTest.java`：修正测试业务类型、合同测试数据和模板隔离，避免测试配置与真实业务访问校验冲突。
- `docs/quality/issue-004-006-approval-workbench-regression.md`：新增正式质量报告。
- `docs/backlog/ready-issues.md`、`docs/backlog/done-issues.md`、`docs/iterations/iteration-2026-07-08-report.md`：更新 Issue 状态和 iteration 记录。
- 未修改后端生产代码或前端页面代码。

## 验证命令

- `cd backend; .\mvnw.cmd "-Dtest=WorkflowQueryServiceTest,WorkflowTaskServiceTest,WorkflowSubmitServiceTest" test`：首轮失败，分类为 Ready Issue 测试配置问题；修正后通过，`Tests run: 60, Failures: 0, Errors: 0, Skipped: 0`，`BUILD SUCCESS`。
- `cd frontend-admin; pnpm exec vitest run src/pages/approval/__tests__/ApprovalWorkList.test.ts src/pages/approval/__tests__/ApprovalConfirm.test.ts src/pages/approval/__tests__/workflowDisplay.test.ts`：通过，`3` 个文件、`22` 个用例全部通过。
- `git diff --check`：通过。

## 结论

通过。审批中心待办、已办、抄送、我发起四类查询与前端工作台冻结口径在本轮指定范围内通过。

## 剩余风险

- 本轮未跑全量后端/前端测试，结论限于 ISSUE-004-006 指定范围。
- 本轮未做真实浏览器验收，前端结论来自 Vitest 组件/逻辑测试。
