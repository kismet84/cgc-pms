# ISSUE-006-011 发票识别记录与人工确认审计回归

日期：2026-07-09

结论：通过

## 目标

- 回归发票识别与人工确认链路的审计记录，确保识别成功、识别失败、人工确认三类关键动作均可追踪。
- 审计记录不得泄露票据图片直链、凭据、token 或完整敏感载荷。
- 合法识别与人工确认流程不回退，前端提示与后端结果一致。
- 不修改发票识别供应商配置，不引入外部依赖，不修改 Flyway migration。

## 实现摘要

- `backend/src/main/java/com/cgcpms/file/audit/InvoiceRecognitionAuditAspect.java`
  - 新增发票识别与人工确认专用审计切面。
  - `InvoiceController.recognize` 发布 `INVOICE_RECOGNITION` 审计事件。
  - `InvoiceController.create` 与 `InvoiceController.register` 发布 `INVOICE_MANUAL_CONFIRM` 审计事件。
  - 识别结果按业务返回体中的 `success` 字段判定审计成功/失败，避免识别失败但 HTTP 成功时被误记为成功。
  - 审计字段仅保留租户、用户、操作类型、业务类型、业务 ID、HTTP 方法、请求路径、成功标记、错误码、来源 IP、耗时和创建时间。
- `backend/src/test/java/com/cgcpms/file/InvoiceRecognitionAuditAspectTest.java`
  - 补充识别成功审计断言。
  - 补充识别失败审计断言，并固定 `PDF_RECOGNIZE_FAILED` 错误码。
  - 补充人工确认创建发票审计断言，并验证只记录生成后的发票 ID。
  - 补充票据图片直链、签名参数、token、发票号、税号和完整载荷不进入审计事件的断言。

## 审计记录口径

- 识别成功：
  - `operationType=INVOICE_RECOGNITION`
  - `businessType=INVOICE`
  - `successFlag=true`
  - `errorCode=null`
  - `businessId=null`
- 识别失败：
  - `operationType=INVOICE_RECOGNITION`
  - `businessType=INVOICE`
  - `successFlag=false`
  - `errorCode` 优先使用识别结果返回的稳定错误码，缺失时兜底为 `UNKNOWN`
  - `businessId=null`
- 人工确认：
  - `operationType=INVOICE_MANUAL_CONFIRM`
  - `businessType=INVOICE`
  - `successFlag=true`
  - `businessId` 使用后端创建或登记返回的发票 ID
  - `errorCode=null`
- 三类事件均记录 `tenantId/userId/httpMethod/requestPath/sourceIp/durationMs/createdAt`，不记录请求体、响应体、文件名、票据图片 URL、PDF 文本、发票号或税号。

## 前端提示口径

- 本轮未修改前端代码。
- 识别接口与人工确认接口仍沿用既有 `ApiResponse` 与请求层提示口径；后端识别成功、识别失败和人工确认返回结构未改变。
- 审计补强不改变用户可见流程，不引入仅前端校验。

## 安全边界

- 未修改 `backend/src/main/resources/db/migration/**`。
- 未修改 `deploy/**`、生产凭据、外部平台配置或发票识别供应商配置。
- 未引入外部依赖。
- 未记录票据图片直链、`X-Amz-Signature`、token、发票号、买方税号、卖方税号或完整 JSON 载荷。
- 发票识别业务口径未重构；本轮只补充审计可追踪性。

## 验证记录

- `cd backend; .\mvnw.cmd "-Dtest=InvoiceRecognitionAuditAspectTest" test`
  - RED：先失败；失败原因为缺少 `INVOICE_RECOGNITION` 与 `INVOICE_MANUAL_CONFIRM` 专用审计事件。
  - GREEN：修复后通过，`3` 个用例通过。
- `cd backend; .\mvnw.cmd "-Dtest=InvoiceRecognitionAuditAspectTest,InvoiceRecognitionTest,InvoiceServiceTest,OperationAuditAspectTest,OperationAuditServiceTest" test`
  - 通过，`44` 个用例通过。
- `cd frontend-admin; pnpm type-check`
  - 通过。
- `cd backend; .\mvnw.cmd test`
  - 命令在工具层 120 秒超时后仍继续运行；后续 Surefire 报告已生成。
  - Surefire 汇总：`156` 个报告，`1540` 个测试，`10` 个 failures，`30` 个 errors，`1` 个 skipped。
  - 失败类未命中本轮新增 `InvoiceRecognitionAuditAspectTest`，按既有无关后端测试红灯分类。
  - 失败类包括 `DashboardChiefEngineerServiceTest`、`InvoiceValidationTest`、`MigrationSoftDeleteBehaviorTest`、`PayRecordControllerTest`、`Phase2FullChainIntegrationTest`、`Phase4IntegrationTest`、`PurchaseRequestServiceTest`、`ContractRevenueServiceTest`、`WorkflowApproverResolverTest`、`WorkflowConcurrencyTest`、`WorkflowCoreServiceTest`、`WorkflowEngineIntegrationTest`、`WorkflowTemplateManagementTest`。
- `git diff --check`
  - 通过。

## 失败分类或非失败分类

真实代码质量问题已修复；全量测试存在既有无关失败；全量 Maven 命令首次存在工具执行时限问题。

## 剩余风险

- 本轮以 Spring 事件与 MockMvc 覆盖审计发布口径，未验证外部日志平台或审计报表展示。
- 当前为专用补充审计事件，仍会保留既有通用 `@AuditedOperation` 审计事件；如后续需要统一审计事件去重或展示聚合，应另立任务处理。
