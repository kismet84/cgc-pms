# ISSUE-007-013：慢 SQL 监控口径回归

## 结论

- 结论：通过
- 阻塞：非阻塞
- 范围结论：已补齐项目内 mapper 调用级慢 SQL 监控口径，明确默认阈值、日志告警码、Micrometer 指标和本地测试覆盖。
- 边界结论：未引入外部 APM；未修改生产数据库配置；未输出完整 SQL、绑定参数、连接串、密码或 token。

## 本轮修改

- `backend/src/main/java/com/cgcpms/common/aspect/SlowSqlObservationAspect.java`
  - 新增 mapper 调用观测切面，切点限定为 `com.cgcpms..mapper..*`。
  - 默认慢 SQL 阈值为 `observability.slow-sql.threshold-ms:500`，通过属性覆盖，不写入生产数据库配置。
  - 所有 mapper 调用写入 `db.sql.duration` timer，tag 为 `operation`。
  - 超过阈值时写入 `db.sql.slow.count` counter，并输出固定告警码 `SLOW_SQL_DETECTED`。
  - 日志仅包含 `operation`、`durationMs`、`thresholdMs`，不记录 SQL 文本、方法参数、连接串或异常消息。
- `backend/src/test/java/com/cgcpms/common/aspect/SlowSqlObservationAspectTest.java`
  - 覆盖超过阈值时日志与指标输出。
  - 覆盖低于阈值时不产生慢 SQL 告警。
  - 覆盖参数中存在 JDBC URL、密码、token、金额等敏感值时，日志不泄露这些内容。

## TDD 证据

- `cd backend; .\mvnw.cmd "-Dtest=SlowSqlObservationAspectTest" test`
  - 先失败：`SlowSqlObservationAspect` 不存在，测试编译失败。
- 新增实现后再次执行同一命令：
  - 通过，`3` 个用例通过。
- `cd backend; .\mvnw.cmd test`
  - 首次全量验证失败：Spring 未能为 `SlowSqlObservationAspect` 选择注入构造器，应用上下文启动失败。
  - 修复：为公开生产构造器补充 `@Autowired`，保留包内测试构造器。
- `cd backend; .\mvnw.cmd "-Dtest=AccountingEntryControllerTest" test`
  - 修复后通过，`7` 个用例通过，确认 Spring 上下文可加载慢 SQL 切面。

## 验证证据

### 1. 定向慢 SQL 回归

- `cd backend; .\mvnw.cmd "-Dtest=SlowSqlObservationAspectTest" test`
  - 通过，`3` 个用例通过。
  - 覆盖：
    - 默认阈值口径：测试使用 500ms 阈值模拟 650ms mapper 调用。
    - 指标输出：`db.sql.duration`、`db.sql.slow.count`。
    - 日志输出：`SLOW_SQL_DETECTED operation=... durationMs=... thresholdMs=...`。
    - 非慢调用不误报。
    - 不输出 `jdbc:mysql://`、数据库 host、`secret`、明文 token、业务参数值。

### 2. Ready Issue 指定全量验证

- `cd backend; .\mvnw.cmd test`
  - 未通过，失败类未命中本轮慢 SQL 相关目标测试，按仓库规则分类为既有真实质量/测试存量问题。
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

- 本轮没有修改 `backend/src/main/resources/application-prod.yml`、`spring.datasource.*` 或任何生产数据库连接配置。
- 慢 SQL 监控在应用内完成，不接入外部 APM，不改变数据库 schema，不重构业务查询逻辑。
- 日志采用固定告警码和 mapper operation，避免输出完整 SQL、绑定参数、连接串、密码、token 或真实业务参数值。
- 指标只使用既有 Micrometer 体系，不新增外部监控平台依赖。

## 剩余风险

- 本轮观测粒度是 mapper 方法调用耗时，不解析具体 SQL 文本；因此可以定位到 mapper operation，但不能在日志中直接看到完整 SQL。
- `operation` tag 基于 mapper 方法名，后续若 mapper 数量继续扩大，应关注指标基数。
