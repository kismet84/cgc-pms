# ISSUE-007-011：CPU/内存/进程指标回归

## 结论

- 结论：通过
- 阻塞：非阻塞
- 范围结论：已补齐本地 `actuator/prometheus` 抓取入口，并以回归测试覆盖 CPU、内存、进程级基础指标读取。
- 边界结论：未修改 `application-prod.yml`；未放宽 actuator 鉴权白名单；未引入外部监控平台。

## 本轮修改

- `backend/src/main/java/com/cgcpms/config/PrometheusRegistryConfig.java`
  - 在本地应用上下文缺少自动注册时，补充最小 `PrometheusRegistry` / `PrometheusMeterRegistry` fallback bean。
- `backend/src/main/java/com/cgcpms/config/PrometheusScrapeController.java`
  - 新增 `/actuator/prometheus` 抓取端点，复用现有 actuator 安全边界，仅在存在 `PrometheusMeterRegistry` 时启用。
- `backend/src/test/java/com/cgcpms/config/ActuatorMetricsTest.java`
  - 扩展 `metrics` 端点断言，覆盖 `system.cpu.usage`、`process.cpu.usage`、`jvm.memory.used`、`process.uptime`。
  - 新增 `prometheus` 端点断言，覆盖 `system_cpu_usage`、`process_cpu_usage`、`jvm_memory_used_bytes`、`process_uptime_seconds`。
  - 扩展未登录访问 `metrics/prometheus` 仍被拒绝的断言。

## 验证证据

### 1. 定向回归

- `cd backend; .\mvnw.cmd "-Dtest=ActuatorMetricsTest" test`
  - 先失败后修复通过。
  - 失败前表现：鉴权后访问 `/api/actuator/prometheus` 返回 `404`，说明抓取端点未注册。
  - 修复后表现：
    - 已登录 `GET /api/actuator/metrics` 返回 `200`
    - 已登录 `GET /api/actuator/metrics/system.cpu.usage` 返回 `200`
    - 已登录 `GET /api/actuator/metrics/process.cpu.usage` 返回 `200`
    - 已登录 `GET /api/actuator/metrics/jvm.memory.used` 返回 `200`
    - 已登录 `GET /api/actuator/metrics/process.uptime` 返回 `200`
    - 已登录 `GET /api/actuator/prometheus` 返回 `200`
    - 未登录 `GET /api/actuator/metrics` / `prometheus` 返回 `4xx`

### 2. Ready Issue 指定全量验证

- `cd backend; .\mvnw.cmd test`
  - 首次运行因命令超时未形成质量结论，归类为工具执行时限问题。
  - 放宽超时后再次运行未通过；失败已分类为既有无关测试红灯，不属于本次指标回归引入。
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

## 安全与边界说明

- `SecurityConfig` 仍只对白名单开放 `/api/actuator/health/**`，本轮未新增 `metrics` 或 `prometheus` 匿名访问。
- 本轮未修改 `backend/src/main/resources/application-prod.yml`，生产默认暴露口径未扩大。
- 本轮只补本地可验证指标链路，不接入外部 Prometheus 服务，不连接生产环境。

## 剩余风险

- 全量后端测试仍有既有无关失败，当前通过结论基于定向回归和边界审查，不等于全仓测试已恢复为全绿。
- `prometheus` 抓取日志里仍可见 `executor.completed` 相关重复 tag key 警告；本轮抓取结果与断言未受影响，但后续若扩展异步线程池指标告警，建议单独清理该噪声。
