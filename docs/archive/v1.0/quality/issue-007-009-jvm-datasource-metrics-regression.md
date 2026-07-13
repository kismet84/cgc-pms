# ISSUE-007-009：JVM 与数据库连接池指标回归

## 结论

- 结论：通过
- 阻塞：非阻塞
- 范围结论：已在 `dev/local/test` 范围内恢复 actuator `metrics` 暴露，并新增 JVM、HikariCP 指标与鉴权边界回归测试。
- 边界结论：未放宽 `SecurityConfig` 鉴权白名单；未修改生产部署配置默认值。

## 本轮修改

- `backend/src/main/resources/application-dev.yml`
  - 默认 `MANAGEMENT_ENDPOINTS_INCLUDE` 从 `health,info` 调整为 `health,info,metrics`，用于本地开发监控入口。
- `backend/src/main/resources/application-local.yml`
  - 新增 `management.endpoints.web.exposure.include=${MANAGEMENT_ENDPOINTS_INCLUDE:health,info,metrics}`，用于本地测试与 H2 profile。
- `backend/src/test/java/com/cgcpms/config/ActuatorMetricsTest.java`
  - 补充 `/api/actuator/metrics` 与具体 meter 端点回归。
  - 补充未登录访问 `metrics` 仍被拒绝的断言。

## 越界风险纠正

- 中间过程曾误把 `backend/src/main/resources/application-prod.yml` 默认暴露改为 `health,info,metrics`。
- 该变更已在提交前撤回，生产配置默认值保持 `health,info` 原状。
- 最终交付中不包含生产默认暴露范围扩大。

## 验证证据

### 1. 定向回归

- `cd backend; .\mvnw.cmd "-Dtest=ActuatorMetricsTest" test`
  - 先失败后修复通过。
  - 失败前表现：鉴权后访问 `/api/actuator/metrics` 返回 `404`，说明端点未暴露。
  - 修复后表现：
    - 未登录 `GET /api/actuator/metrics` 返回 `401`
    - 已登录 `GET /api/actuator/metrics` 返回 `200`
    - 已登录 `GET /api/actuator/metrics/jvm.threads.live` 返回 `200`
    - 已登录 `GET /api/actuator/metrics/hikaricp.connections.max` 返回 `200`

### 2. 鉴权边界未回退

- `cd backend; .\mvnw.cmd "-Dtest=ActuatorMetricsTest,GlobalWriteRateLimitFilterTest" test`
  - 通过，`9` 个用例通过。
  - 其中 `GlobalWriteRateLimitFilterTest` 继续验证 `/api/actuator/health` 白名单行为，不存在把 `metrics` 放进匿名白名单的回退。

### 3. Ready Issue 指定全量验证

- `cd backend; .\mvnw.cmd test`
  - 未通过，但失败已分类为既有无关测试红灯，不属于本次指标回归引入。
  - 本轮扫描到的失败类包括：
    - `com.cgcpms.dashboard.service.DashboardChiefEngineerServiceTest`
    - `com.cgcpms.invoice.InvoiceValidationTest`
    - `com.cgcpms.workflow.WorkflowEngineIntegrationTest`
    - `com.cgcpms.workflow.WorkflowCoreServiceTest`
    - `com.cgcpms.workflow.WorkflowConcurrencyTest`
    - `com.cgcpms.workflow.WorkflowApproverResolverTest`
    - `com.cgcpms.workflow.WorkflowTemplateManagementTest`
    - `com.cgcpms.payment.PayRecordControllerTest`
    - `com.cgcpms.Phase2FullChainIntegrationTest`
    - `com.cgcpms.Phase4IntegrationTest`
    - `com.cgcpms.revenue.ContractRevenueServiceTest`
- 分类：既有真实质量/测试存量问题，非本次 `metrics` 暴露修复所致。

### 4. diff 门禁

- `git diff --check`
  - 通过。

## 风险说明

- 当前 `metrics` 暴露仅在 `dev/local/test` 默认开启；生产默认值仍为 `health,info`，如线上需要采集指标，应通过环境变量显式配置。
- 测试仍依赖本地 profile 与 H2/MockMvc，上线前若要验证真实运行态，还需要单独做环境级 health/metrics 探测。
