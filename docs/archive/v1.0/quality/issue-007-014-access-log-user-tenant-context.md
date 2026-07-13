# ISSUE-007-014：访问日志 userId/tenantId 字段回归

## 结论

- 结论：通过
- 阻塞：非阻塞
- 范围结论：已补齐访问日志 `userId`、`tenantId` 字段口径，并覆盖成功请求、匿名请求、异常请求。
- 边界结论：未修改认证/权限模型，未修改生产日志采集外部平台配置，未记录 Token、Cookie、密码、Authorization 头或完整请求体。

## 本轮修改

- `backend/src/main/java/com/cgcpms/common/filter/TraceIdFilter.java`
  - `HTTP_ACCESS` 日志新增 `userId`、`tenantId` 字段。
  - 身份字段优先从请求属性 `accessLog.userId`、`accessLog.tenantId` 读取，兜底读取 `UserContext`。
  - 无法识别身份时统一输出 `-`，避免输出空值、对象串或脏数据。
  - 访问日志仍只输出固定字段，不读取或记录请求头、Cookie、Authorization、请求体或异常消息。
- `backend/src/main/java/com/cgcpms/auth/filter/JwtAuthenticationFilter.java`
  - JWT 校验通过并写入 `UserContext` 后，将 `userId`、`tenantId` 同步写入请求属性，供最外层访问日志在认证过滤器清理上下文后仍可读取。
- `backend/src/test/java/com/cgcpms/common/filter/TraceIdFilterLoggingTest.java`
  - 补充成功请求、匿名请求、异常请求下的 `userId`/`tenantId` 访问日志断言。
  - 补充 Token、Cookie、密码、请求体敏感内容不进入访问日志断言。
- `backend/src/test/java/com/cgcpms/auth/filter/JwtAuthenticationFilterTest.java`
  - 补充认证成功后请求属性写入 `userId`、`tenantId` 的断言。

## TDD 证据

- `cd backend; .\mvnw.cmd "-Dtest=TraceIdFilterLoggingTest,JwtAuthenticationFilterTest" test`
  - 先失败：`TraceIdFilter` 日志缺少 `userId`/`tenantId`；`JwtAuthenticationFilter` 未写入访问日志身份请求属性。
- 新增最小实现后再次执行同一命令：
  - 通过，`7` 个用例通过。

## 验证证据

### 1. 定向访问日志回归

- `cd backend; .\mvnw.cmd "-Dtest=TraceIdFilterLoggingTest,JwtAuthenticationFilterTest" test`
  - 通过，`7` 个用例通过。
  - 覆盖：
    - 成功请求：`userId=7`、`tenantId=0`。
    - 匿名请求：`userId=-`、`tenantId=-`。
    - 异常请求：`userId=8`、`tenantId=2`，并记录 `exception=RuntimeException`。
    - JWT 认证成功后写入访问日志身份请求属性。
    - 日志不包含 `top-secret-token`、`secret-cookie`、`body-secret`、`super-secret`、`failing-token`、`failing-cookie`。

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
  - 通过；仅有换行符转换提示，无空白错误。

## 访问日志字段口径

- `userId`：
  - 已认证请求：JWT 中的 `userId`，通过请求属性传递给访问日志。
  - 匿名请求或无法识别：`-`。
- `tenantId`：
  - 已认证请求：JWT 中的 `tenantId`，通过请求属性传递给访问日志。
  - 匿名请求或无法识别：`-`。
- 成功、异常请求都会输出同一字段集合：`traceId`、`requestId`、`method`、`path`、`projectId`、`userId`、`tenantId`、`status`、`duration`、`exception`、`clientIp`。

## 安全与边界说明

- 本轮不记录 Authorization 头、Cookie、Token、密码、完整请求体或异常消息。
- 本轮没有修改认证/权限模型，只在认证过滤器已解析出的身份上下文上增加访问日志字段传递。
- 本轮没有修改生产日志采集外部平台配置、生产凭据、部署配置或数据库迁移。

## 剩余风险

- 本轮覆盖的是应用内访问日志格式与本地单元测试，不验证外部日志平台的字段解析规则。
- 异步请求若在认证过滤器之外创建新的请求链路，仍需依赖请求属性或 `UserContext` 正确传递；本轮未扩展异步日志链路。
