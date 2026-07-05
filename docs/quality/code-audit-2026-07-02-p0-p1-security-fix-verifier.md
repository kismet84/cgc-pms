# 总体结论

本次 P0/P1 安全修复批次当前结论为：**不通过**，且属于**阻塞**状态，不建议直接继续下一批。  
VUL-006、VUL-007、VUL-011、VUL-008 的主线修复大体成立，最小验证组合里限流、固定错误消息、敏感日志收敛、`externalTxnNo` 必填/空白拒绝、迁移唯一约束检查都已有实测支撑。  
但 VUL-004 / VUL-030 仍存在阻塞问题：`application-dev.yml` 把原先的加密/密钥共存问题替换成了新的**明文默认凭据/默认密钥硬编码**，这不构成真正的“配置密钥硬编码修复”。  
另外，VUL-008 的服务层收紧引入了回归：`InvoiceControllerTest` 初始化数据仍通过 `PayRecordService.writeback()` 写入未带 `externalTxnNo` 的支付记录，导致指定验证组合直接失败，说明相关测试/调用方尚未同步收口。  
`backend/src/test/resources/sample-invoice.pdf` 的二进制改动也不建议保留，证据显示它是测试生成副产物而非必要业务样本更新。

# 评分

- 正确性：72/100
- 安全性：58/100
- 性能：84/100
- 可维护性：70/100
- 架构设计：78/100
- 测试与工程化：68/100
- 综合评分：70/100

# 风险统计

- Critical：0
- High：2
- Medium：3
- Low：2
- Info：3

# 关键问题

## 问题 1：VUL-004 / VUL-030 未真正消除明文默认凭据

- 类型：安全配置
- 严重程度：High
- 位置：
  - `backend/src/main/resources/application-dev.yml:6`
  - `backend/src/main/resources/application-dev.yml:42`
  - `backend/src/main/resources/application-dev.yml:43`
  - `backend/src/main/resources/application-dev.yml:48`
  - `README.md:89`
- 证据：
  - `DB_PASSWORD` 默认值为 `cgc123`
  - MinIO access/secret 默认值为 `minioadmin`
  - JWT 默认值为 `test-only-change-me-cgc-pms-jwt-secret-key-256bit-minimum-length`
  - `git grep -n "cgc123|minioadmin|test-only-change-me-cgc-pms-jwt-secret-key-256bit-minimum-length|local-dev-only-cgc-pms-jwt-secret-key-256bit-minimum-length" -- backend/src/main/resources README.md scripts .github/workflows`
- 问题说明：当前 diff 的确移除了 `dev-jasypt-key` 与 `ENC(...)`，但同时引入了新的明文默认凭据，仍然属于“配置密钥硬编码”。
- 影响：开发配置泄漏风险继续存在；后续被复制到其他环境或文档时会扩散；安全审计口径上不能视为完成修复。
- 修复建议：移除 `application-dev.yml` 与 README 中的真实/准真实默认凭据，改为仅引用环境变量，必要时在 README 中要求“本地自行设置 32+ 字节随机值”。
- 示例修复代码：不在本线程实施。
- 优先级：P0

## 问题 2：VUL-008 服务收紧后，现有调用路径/测试未同步，最小验证组合失败

- 类型：正确性 / 回归
- 严重程度：High
- 位置：
  - `backend/src/main/java/com/cgcpms/payment/service/PayRecordService.java:68`
  - `backend/src/test/java/com/cgcpms/invoice/InvoiceControllerTest.java:99`
  - `backend/src/test/java/com/cgcpms/invoice/InvoiceControllerTest.java:104`
- 证据：
  - `PayRecordService.writeback()` 现在对 `externalTxnNo` 为 `null/blank` 直接抛 `EXTERNAL_TXN_NO_REQUIRED`
  - `InvoiceControllerTest.initData()` 仍然构造未设置 `externalTxnNo` 的 `PayRecord` 并调用 `payRecordService.writeback(record)`
  - 实测命令失败：`.\mvnw.cmd "-Dtest=InvoiceControllerTest#testCreate_MalformedJson_UsesFixedMessage,InvoiceRecognitionTest#shouldNotLogSensitiveRecognitionDetailsAtInfo,InvoiceRecognitionTest#shouldReturnFixedMessageWhenPdfRecognitionFails,InvoiceServiceTest#shouldNotLogSensitiveIdentifiersAtInfoOnCreate" test`
- 问题说明：安全修复本身方向正确，但依赖 `writeback()` 的调用方/测试基座未补齐必填字段，导致回归。
- 影响：当前批次验证不完整；类似未补齐 `externalTxnNo` 的生产调用路径也需要再次盘点。
- 修复建议：系统性搜索所有 `payRecordService.writeback(...)` 调用点和相关 controller / test seed，补充稳定的 `externalTxnNo`。
- 示例修复代码：不在本线程实施。
- 优先级：P0

## 问题 3：`sample-invoice.pdf` 二进制改动是测试副产物，当前没有保留必要性

- 类型：测试资产管理
- 严重程度：Medium
- 位置：
  - `backend/src/test/resources/sample-invoice.pdf`
  - `backend/src/test/java/com/cgcpms/invoice/InvoiceRecognitionTest.java:51`
  - `backend/src/test/java/com/cgcpms/invoice/InvoiceRecognitionTest.java:88`
- 证据：
  - 实测当前文件与 `HEAD` 版本 `SHA-256` 不同，但大小相同，逐字节差异数为 62，且差异集中在 PDF trailer `/ID` 字段
  - `InvoiceRecognitionTest` 在启动时执行 `Files.write(SAMPLE_PDF_DISK, sampleInvoiceBytes);`
  - 控制台输出：`Sample invoice PDF written to: ...sample-invoice.pdf`
- 问题说明：这类改动更像测试运行时重写样本文件产生的元数据漂移，而不是有意更新测试语义。
- 影响：污染工作区 diff，增加审计噪音；若提交，会让后续 verifier 难以区分真实样本变更与测试副产物。
- 修复建议：当前文件建议恢复；若测试确需动态生成样本，应写到临时目录而不是覆写版本库资源。
- 示例修复代码：不在本线程实施。
- 优先级：P1

## 问题 4：`scripts/rebuild.py` 的测试前置校验存在过度收紧

- 类型：工程化
- 严重程度：Medium
- 位置：
  - `scripts/rebuild.py:134`
  - `scripts/rebuild.py:135`
- 证据：
  - 脚本在 `TEST_JWT_SECRET` 缺失时直接返回失败
  - 但实测 `Remove-Item Env:TEST_JWT_SECRET; .\mvnw.cmd "-Dtest=CorsConfigTest" test` 仍能通过
- 问题说明：移除 Jasypt 依赖本身是必要的，但脚本把“某些 local profile 测试需要 secret”扩大成了“所有 rebuild test 都必须先设 `TEST_JWT_SECRET`”。
- 影响：增加本地使用门槛，且与脚本实际执行的 `mvnw test` 命令不完全一致。
- 修复建议：脚本校验应与实际触发的测试 profile 对齐，或者给出更精确提示，而不是一刀切阻断。
- 示例修复代码：不在本线程实施。
- 优先级：P1

## 问题 5：全局写限流白名单路径的自动化覆盖还不完整

- 类型：测试覆盖
- 严重程度：Medium
- 位置：
  - `backend/src/main/java/com/cgcpms/auth/config/SecurityConfig.java:26`
  - `backend/src/main/java/com/cgcpms/auth/config/SecurityConfig.java:38`
  - `backend/src/main/java/com/cgcpms/common/filter/GlobalWriteRateLimitFilter.java:51`
  - `backend/src/test/java/com/cgcpms/common/filter/GlobalWriteRateLimitFilterTest.java:72`
  - `backend/src/test/java/com/cgcpms/common/filter/GlobalWriteRateLimitFilterTest.java:92`
- 证据：
  - 代码层面：`AUTH_WHITELIST_PATHS` / `HEALTH_WHITELIST_PATHS` 被纳入 `SKIP_PATHS`
  - 自动化已覆盖：`GET`、`OPTIONS`、`@RateLimit` 独立生效
  - 未见本批次对 `/auth/login`、`/auth/refresh`、`/actuator/health` 的直接行为回归测试
- 问题说明：逻辑实现基本正确，但白名单关键路径缺少直接回归样例。
- 影响：当前只能基于代码路径推断 `/auth/login` 与 health 不会被误伤，缺少显式防回归证据。
- 修复建议：补 2 到 3 个最小 MockMvc 用例，直接覆盖 `/auth/login`、`/auth/refresh`、`/actuator/health`。
- 示例修复代码：不在本线程实施。
- 优先级：P1

# 必须修复

1. 移除 `application-dev.yml` 与 README 中的明文默认凭据/默认 JWT secret，不能以“测试用默认值”代替安全修复。
2. 修复所有依赖 `PayRecordService.writeback()` 的测试/调用路径，补齐 `externalTxnNo`，至少先让指定最小验证组合全部通过。
3. 恢复 `backend/src/test/resources/sample-invoice.pdf`，或改造测试使其不覆写版本库样本。

# 建议优化

1. `scripts/rebuild.py` 只在确实需要 `TEST_JWT_SECRET` 的场景下提示或注入，不要对所有 `mvnw test` 一刀切失败。
2. README 不要给出固定测试 secret 字符串，改成用户自定义随机值示例。
3. 给全局写限流补齐 `/auth/login`、`/auth/refresh`、`/actuator/health` 的直接回归测试。

# 长期演进建议

1. 将“测试配置需要的 secret”与“开发 profile 默认配置”彻底分离，避免安全修复时反复在 `application-dev.yml` 和 `application-local.yml` 间摆动。
2. 为测试样本资源建立规则：版本库内资源只读，运行时生成文件一律写临时目录。
3. 对 `writeback()` 这类权威入口建立调用方盘点清单，服务层收紧后统一回归，避免一处加校验、到处断初始化。

# 测试建议

1. 增加 `/auth/login`、`/auth/refresh`、`/actuator/health` 不受全局写限流影响的直接 MockMvc 测试。
2. 对所有通过 `PayRecordService.writeback()` 建立测试数据的测试类做一次全文检索回归。
3. 增加一个“无环境变量时的最小本地测试命令”验证用例，校验 README / `scripts/rebuild.py` / CI 行为一致性。

# 验收标准

## 验收项 1：配置硬编码真正移除

- 验收目标：仓库中不再保留 `dev-jasypt-key`、`ENC(...)`、明文默认凭据或固定测试 secret。
- 验收步骤：执行 `git grep` / `rg` 检查关键字符串。
- 通过标准：无匹配；文档仅保留“如何设置环境变量”的说明。
- 必须通过的命令或测试：
  - `git grep -n "dev-jasypt-key" -- .`
  - `git grep -n "ENC(" -- backend/src/main/resources README.md scripts .github/workflows`
  - `git grep -n "cgc123|minioadmin|test-only-change-me-cgc-pms-jwt-secret-key-256bit-minimum-length" -- backend/src/main/resources README.md`
- 需要人工确认的事项：示例文档是否仍泄露固定凭据。

## 验收项 2：支付回写必填/幂等收口完成

- 验收目标：`externalTxnNo` 为 null/blank 被拒绝；重复 `externalTxnNo` 保持幂等；所有相关调用方已补齐字段。
- 验收步骤：跑支付写回测试与指定回归测试。
- 通过标准：所有命令成功，且 `InvoiceControllerTest` 不再因初始化数据缺字段失败。
- 必须通过的命令或测试：
  - `.\mvnw.cmd "-Dtest=PaymentWritebackTest" test`
  - `.\mvnw.cmd "-Dtest=MigrationIntegrityTest#payRecordExternalTxnNoUniqueConstraintRemainsTenantScopedForMysqlAndH2" test`
  - `.\mvnw.cmd "-Dtest=InvoiceControllerTest#testCreate_MalformedJson_UsesFixedMessage,InvoiceRecognitionTest#shouldNotLogSensitiveRecognitionDetailsAtInfo,InvoiceRecognitionTest#shouldReturnFixedMessageWhenPdfRecognitionFails,InvoiceServiceTest#shouldNotLogSensitiveIdentifiersAtInfoOnCreate" test`
- 需要人工确认的事项：是否还有其他业务入口直接/间接调用 `writeback()` 且未补 `externalTxnNo`。

## 验收项 3：全局写限流白名单路径无误伤

- 验收目标：`/auth/login`、`OPTIONS`、`GET`、`/actuator/health` 不被全局写限流误伤，`@RateLimit` 继续独立生效。
- 验收步骤：执行现有测试并补充白名单路径直接用例。
- 通过标准：白名单路径稳定通过；受控路径超过阈值返回 429；`@RateLimit` 仍按自身阈值触发。
- 必须通过的命令或测试：
  - `.\mvnw.cmd "-Dtest=GlobalWriteRateLimitFilterTest,RateLimitAspectTest,JwtAuthenticationFilterTest,CorsConfigTest" test`
- 需要人工确认的事项：新增用例是否覆盖 `/auth/login`、`/auth/refresh`、`/actuator/health`。

# 最终建议

- 可以合并：否
- 修改后合并：否
- 暂不建议合并：是
- 不建议上线：是
