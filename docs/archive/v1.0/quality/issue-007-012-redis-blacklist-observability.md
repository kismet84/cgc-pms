# ISSUE-007-012：Redis 健康与黑名单降级告警回归

## 结论

- 结论：通过
- 阻塞：非阻塞
- 范围结论：已补齐 Redis-backed Token blacklist 的健康口径、prod fail-close 告警日志和 Redis 异常日志脱敏回归。
- 边界结论：未修改生产 Redis 配置；未把生产 Redis 强依赖降级为正常运行；未写入真实密码、连接串或敏感 Redis 地址。

## 本轮修改

- `backend/src/main/java/com/cgcpms/config/TokenBlacklistHealthIndicator.java`
  - 新增 `tokenBlacklist` 健康信号。
  - prod profile 缺少 `TokenBlacklistService` 时返回 `DOWN`，类别为 `BLACKLIST_UNAVAILABLE`。
  - local/dev 等非 prod 缺少服务时返回 `UNKNOWN`，类别为 `BLACKLIST_UNAVAILABLE`，不伪装为 `UP`。
  - Redis 探测失败时返回 `DOWN`，类别为 `TOKEN_BLACKLIST_CHECK_FAILED`。
- `backend/src/main/java/com/cgcpms/auth/service/TokenBlacklistService.java`
  - 新增 `isAvailable()` 轻量探针，供 health indicator 使用。
  - `TOKEN_BLACKLIST_WRITE_FAILED`、`TOKEN_BLACKLIST_CHECK_FAILED` 日志保留固定告警码，但只输出异常类型，不输出异常消息或连接串。
- `backend/src/main/java/com/cgcpms/auth/controller/AuthController.java`
  - 在 prod refresh/logout 的 blacklist 服务缺失与写入失败 fail-close 分支写出 `BLACKLIST_UNAVAILABLE` / `TOKEN_BLACKLIST_WRITE_FAILED` 告警码。
- `backend/src/test/java/com/cgcpms/config/TokenBlacklistHealthIndicatorTest.java`
  - 覆盖 prod 缺失服务 `DOWN`、local 缺失服务 `UNKNOWN`、Redis 可用 `UP`、Redis 探测失败 `DOWN`。
- `backend/src/test/java/com/cgcpms/auth/service/TokenBlacklistServiceTest.java`
  - 补充 Redis 写入/检查异常时日志告警码与脱敏断言。
- `backend/src/test/java/com/cgcpms/auth/controller/AuthControllerTest.java`
  - 补充 refresh prod fail-close 分支告警码与脱敏断言。
- `backend/src/test/java/com/cgcpms/auth/filter/JwtAuthenticationFilterTest.java`
  - 补充 prod blacklist 服务缺失时 `BLACKLIST_UNAVAILABLE` 告警码与脱敏断言。

## TDD 证据

- `cd backend; .\mvnw.cmd "-Dtest=TokenBlacklistServiceTest,TokenBlacklistHealthIndicatorTest" test`
  - 先失败：缺少 `TokenBlacklistHealthIndicator` 和 `TokenBlacklistService.isAvailable()`。
- `cd backend; .\mvnw.cmd "-Dtest=AuthControllerTest,JwtAuthenticationFilterTest" test`
  - 先失败：`AuthController` 的 prod refresh fail-close 分支未输出 `BLACKLIST_UNAVAILABLE` / `TOKEN_BLACKLIST_WRITE_FAILED`。
- 修复后定向回归通过。

## 验证证据

### 1. 定向安全回归

- `cd backend; .\mvnw.cmd "-Dtest=TokenBlacklistServiceTest,TokenBlacklistHealthIndicatorTest,JwtAuthenticationFilterTest,AuthControllerTest" test`
  - 通过，`28` 个用例通过。
  - 覆盖：
    - `BLACKLIST_UNAVAILABLE`
    - `TOKEN_BLACKLIST_WRITE_FAILED`
    - `TOKEN_BLACKLIST_CHECK_FAILED`
    - prod 缺少 blacklist 服务 fail-close
    - prod blacklist 写入失败 fail-close
    - Redis 检查失败 fail-close
    - 日志与 health detail 不包含 `redis://`、`REDIS_PASSWORD`、`secret`、`redis.internal`

### 2. Ready Issue 指定全量验证

- `cd backend; .\mvnw.cmd test`
  - 未通过，失败类未命中本轮 Redis blacklist 相关目标测试，按仓库规则分类为既有真实质量/测试存量问题。
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

## 安全与边界说明

- 本轮未修改 `backend/src/main/resources/application-prod.yml`，未变更 `spring.data.redis.*`、`REDIS_HOST`、`REDIS_PASSWORD` 等生产 Redis 配置口径。
- health indicator 在 prod 缺少 blacklist 服务时返回 `DOWN`，不把生产 Redis 强依赖降级成正常运行。
- 日志只输出固定告警码与异常类型，不输出异常 message、Redis URL、密码或 token。

## 剩余风险

- 本轮只验证本地 MockMvc / unit health indicator，不连接真实 Redis，也不验证外部监控平台采集。
- local profile 仍允许缺少 blacklist 服务时继续请求，但健康组件会暴露 `UNKNOWN + BLACKLIST_UNAVAILABLE`，符合本地降级和可观测性边界。
