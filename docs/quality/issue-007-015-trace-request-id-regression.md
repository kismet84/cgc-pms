# ISSUE-007-015：访问日志 traceId/requestId 透传与响应头回归

## 结论

- 结论：通过
- 阻塞：非阻塞
- 范围结论：已补齐访问日志 `traceId`、`requestId` 字段透传、生成和响应头回写的回归断言，覆盖成功请求、匿名请求和异常请求。
- 边界结论：本轮未修改生产代码、鉴权边界、外部日志平台、生产部署配置、数据库迁移或生产凭据。

## 本轮修改

- `backend/src/test/java/com/cgcpms/common/filter/TraceIdFilterLoggingTest.java`
  - 成功请求：断言客户端传入的 `X-Trace-Id`、`X-Request-Id` 被原样写入响应头，并进入 `HTTP_ACCESS` 日志。
  - 匿名请求：断言缺失标识时系统生成 32 位十六进制 `traceId/requestId`，响应头与访问日志一致，`userId/tenantId` 兜底为 `-`。
  - 异常请求：断言异常路径仍回写响应头并记录同一组 `traceId/requestId`，同时保留 `status=500` 与 `exception=RuntimeException`。
  - 抽出敏感信息防泄漏断言，覆盖 Authorization、Cookie、password、token、请求体敏感值和异常消息敏感值不进入访问日志。

## TDD 证据

- 先补充访问日志回归断言后执行：
  - `cd backend; .\mvnw.cmd "-Dtest=TraceIdFilterLoggingTest" test`
  - 结果：通过，`3` 个用例通过。
- 说明：当前 `TraceIdFilter` 既有实现已具备 `traceId/requestId` 透传、生成、响应头回写和日志字段输出能力，因此新增回归测试未出现生产缺口；本轮按最小闭环只补测试门禁，不做生产代码变更。

## 验证证据

### 1. 定向访问日志回归

- `cd backend; .\mvnw.cmd "-Dtest=TraceIdFilterLoggingTest" test`
  - 通过，`3` 个用例通过。
  - 覆盖：
    - 成功请求：`X-Trace-Id=trace-from-client`、`X-Request-Id=request-from-client` 在响应头与访问日志中一致。
    - 匿名请求：系统生成 `traceId/requestId`，响应头与访问日志中同值可断言。
    - 异常请求：`X-Trace-Id=trace-failure`、`X-Request-Id=request-failure` 在异常路径响应头与访问日志中一致。
    - 日志不包含 Authorization、Cookie、password、token、完整请求体敏感值或异常消息敏感值。

### 2. Ready Issue 指定全量验证

- `cd backend; .\mvnw.cmd test`
  - 未通过，失败类未命中本轮访问日志相关目标测试，按仓库规则分类为既有真实质量/测试存量问题。
  - 本轮扫描到的失败类包括：
    - `com.cgcpms.dashboard.service.DashboardChiefEngineerServiceTest`
    - `com.cgcpms.invoice.InvoiceValidationTest`
    - `com.cgcpms.MigrationSoftDeleteBehaviorTest`
    - `com.cgcpms.payment.PayRecordControllerTest`
    - `com.cgcpms.Phase2FullChainIntegrationTest`
    - `com.cgcpms.Phase4IntegrationTest`
    - `com.cgcpms.purchase.PurchaseRequestServiceTest`
    - `com.cgcpms.revenue.ContractRevenueServiceTest`
    - `com.cgcpms.workflow.WorkflowApproverResolverTest`
    - `com.cgcpms.workflow.WorkflowConcurrencyTest`
    - `com.cgcpms.workflow.WorkflowCoreServiceTest`
    - `com.cgcpms.workflow.WorkflowEngineIntegrationTest`
    - `com.cgcpms.workflow.WorkflowTemplateManagementTest`

### 3. diff 门禁

- `git diff --check`
  - 通过。

## traceId/requestId 口径

- `traceId`
  - 请求携带 `X-Trace-Id`：去除首尾空白后原样透传到响应头、MDC、`TraceIdContext` 和访问日志。
  - 请求未携带或为空：生成 32 位十六进制 UUID 去横线值，并写入响应头和访问日志。
- `requestId`
  - 请求携带 `X-Request-Id`：去除首尾空白后原样透传到响应头、MDC 和访问日志。
  - 请求未携带或为空：生成 32 位十六进制 UUID 去横线值，并写入响应头和访问日志。
- 成功、匿名、异常请求都会输出同一关联字段集合，便于用响应头值反查访问日志。

## 安全与边界说明

- 本轮访问日志断言只允许固定字段输出，不记录 Authorization 头、Cookie、Token、密码、完整请求体或异常消息中的敏感内容。
- 本轮没有修改认证/权限模型，不放宽 actuator 或业务接口鉴权边界。
- 本轮没有修改外部日志平台、生产部署配置、生产凭据、部署目录或数据库迁移。

## 剩余风险

- 本轮覆盖应用内访问日志与响应头行为，不验证外部日志平台对字段的解析、索引或链路追踪展示。
- `TraceIdContext` 当前只保存 `traceId`，`requestId` 通过 MDC 与访问日志覆盖；如未来业务代码需要直接读取 `requestId` 上下文，需另立任务扩展上下文对象。
