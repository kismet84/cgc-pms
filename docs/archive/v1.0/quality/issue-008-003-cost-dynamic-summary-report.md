# ISSUE-008-003 成本动态汇总报表口径回归

完成日期：2026-07-09

## 目标

- 回归目标成本、实际成本、动态成本和偏差金额的汇总口径。
- 不新增成本快照表，不扩大为完整成本报表中心。

## 修改范围

- `backend/src/test/java/com/cgcpms/cost/CostSummaryServiceTest.java`
- `docs/backlog/ready-issues.md`
- `docs/backlog/done-issues.md`
- `docs/iterations/iteration-2026-07-09-report.md`

## 回归口径

- 目标成本取项目 `PmProject.targetCost`。
- 实际成本只累计既有实际成本来源，本轮固定种子覆盖 `MAT_RECEIPT` 与 `CT_CHANGE`。
- 动态成本按 `actualCost + estimatedRemainingCost` 计算；本轮独立项目无合同剩余成本，稳定断言为 `200000.00`。
- 偏差金额按 `dynamicCost - targetCost` 计算，稳定断言为 `-3800000.00`。

## 权限、租户与数据边界

- 测试继续通过 `CostSummaryService.refreshSummary(tenantId, projectId)` 的既有租户与项目边界。
- 本轮未修改 controller 鉴权、角色边界、成本核算模型、schema、生产配置或外部平台连接。
- 未修改 `backend/src/main/resources/db/migration/**`、`deploy/**`、生产凭据。

## 验证证据

- `http://localhost:8080/api/actuator/health`：通过，HTTP 200。
- `http://localhost:5173/`：通过，HTTP 200。
- `http://localhost:5173/api/auth/dev-login?redirect=/dashboard`：通过，HTTP 302，Location `/dashboard`。
- `cd backend; .\mvnw.cmd "-Dtest=CostSummaryServiceTest#testRefreshSummaryDynamicCostReportAmounts" test`：通过。
- `cd backend; .\mvnw.cmd test`：未通过；失败类仍集中在既有 dashboard、invoice、workflow、purchase、payment、revenue 测试夹具/断言问题；本轮目标类已通过，未见本轮成本动态汇总报表改动引入的失败。
- `git diff --check`：通过。

## 自审结论

PASS。

依据：
- 固定种子断言覆盖目标成本、实际成本、动态成本和偏差金额。
- 实际成本来源按既有 `CostSummaryAssembler#isActualCostSource` 口径累计，不重复累计、不漏计本轮覆盖来源。
- 本轮仅补强测试和归档证据，未放宽租户、项目和角色边界。

## 结论

通过 / 非阻塞。

剩余风险：
- 本轮只覆盖成本动态汇总报表的最小口径回归，未新增前端页面、导出能力或通用报表中心。
- 后端全量测试仍存在既有无关红灯，需要后续 Ready Issue 分别治理。
