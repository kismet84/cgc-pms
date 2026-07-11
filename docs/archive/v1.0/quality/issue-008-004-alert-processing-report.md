# ISSUE-008-004 预警处理报表口径回归

完成日期：2026-07-09

## 目标

- 回归预警数量、严重度、处理状态和处理结果的报表口径。
- 不扩大为规则治理中心 M2，不新增预警规则表。

## 修改范围

- `backend/src/main/java/com/cgcpms/alert/dto/AlertProcessingReportVO.java`
- `backend/src/main/java/com/cgcpms/alert/controller/AlertController.java`
- `backend/src/main/java/com/cgcpms/alert/service/AlertEvaluationService.java`
- `backend/src/test/java/com/cgcpms/alert/AlertEvaluationServiceTest.java`
- `backend/src/test/java/com/cgcpms/alert/AlertControllerTest.java`
- `docs/backlog/ready-issues.md`
- `docs/backlog/done-issues.md`
- `docs/iterations/iteration-2026-07-09-report.md`

## 回归口径

- 新增 `GET /alerts/processing-report`，复用现有 `AlertEvaluationService` 的租户、项目、规则类型、预警域、严重度、已读状态、处理状态和触发时间筛选口径。
- 报表统计 `totalCount`、`readCount`、`unreadCount`、`severityCounts` 和 `processStatusCounts`。
- 服务层报表与同条件列表查询使用同一查询构造，避免列表和报表口径分叉。

## 权限、租户与数据边界

- 接口沿用 `alert:view` 或 `ADMIN/SUPER_ADMIN` 鉴权。
- 非管理员仍沿用现有 `AlertAccessScopeResolver` 的项目和预警域访问边界。
- 本轮未修改预警规则引擎、规则配置表、schema、生产配置或外部平台连接。
- 未修改 `backend/src/main/resources/db/migration/**`、`deploy/**`、生产凭据。

## 验证证据

- `http://localhost:8080/api/actuator/health`：通过，HTTP 200。
- `http://localhost:5173/`：通过，HTTP 200。
- `http://localhost:5173/api/auth/dev-login?redirect=/dashboard`：通过，HTTP 302，Location `/dashboard`。
- `cd backend; .\mvnw.cmd "-Dtest=AlertEvaluationServiceTest#testProcessingReportAggregatesListFilters,AlertControllerTest#testProcessingReport" test`：通过，`2` 个用例通过。
- `cd frontend-admin; pnpm type-check`：通过。
- `cd backend; .\mvnw.cmd test`：未通过；失败类仍集中在既有 dashboard、invoice、workflow、purchase、payment、revenue 测试夹具/断言问题；本轮目标类已通过，未见本轮预警处理报表改动引入的失败。
- `git diff --check`：通过。

## 自审结论

PASS。

依据：
- 稳定断言覆盖预警总数、严重度分布、已读/未读数量和处理状态分布。
- 报表总数与同筛选条件列表总数一致。
- 本轮未放宽预警域、角色、租户和项目边界。

## 结论

通过 / 非阻塞。

剩余风险：
- 本轮只新增预警处理报表的只读最小接口，未新增前端报表页面、导出能力或规则治理中心。
- 后端全量测试仍存在既有无关红灯，需要后续 Ready Issue 分别治理。
