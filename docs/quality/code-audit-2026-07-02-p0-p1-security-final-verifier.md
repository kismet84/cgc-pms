# 总体结论
本次针对 VUL-004 / VUL-006 / VUL-007 / VUL-008 / VUL-011 / VUL-030 的当前工作区独立复核，定向后端测试组合已通过，限流、发票异常消息收口、发票识别 INFO 日志去敏、`externalTxnNo` 空白拒绝与幂等、V76 唯一约束完整性检查均有当前工作区证据支持。

但当前仓库的受版本控制配置中仍存在 `ENC(` 字样：`deploy/.env.example` 第 14 行注释保留了 `ENC(...)` 说明文本。按本次验收口径“受版本控制配置/README/scripts/.github 中不再含 `ENC(`”的字面要求，当前状态不能判定为完全通过。

因此，本次结论为：**不通过 / 阻塞 / 暂不建议进入提交归档**。阻塞点仅 1 个，修复范围很小，删除或改写该注释即可重新复核。

# 评分
- 正确性：92/100
- 安全性：84/100
- 性能：90/100
- 可维护性：88/100
- 架构设计：89/100
- 测试与工程化：91/100
- 综合评分：89/100

# 风险统计
- Critical：0
- High：1
- Medium：1
- Low：1
- Info：2

# 关键问题

## 问题 1：受版本控制配置中仍出现 `ENC(`
- 类型：安全配置残留
- 严重程度：High
- 位置：`deploy/.env.example:14`
- 证据：`git grep -n -I -E "dev-jasypt-key|ENC\\(|cgc123|minioadmin"` 命中 `deploy/.env.example:14:# Jasypt configuration encryption key — used to decrypt ENC(...) values in application-*.yml`
- 问题说明：虽然这里是注释，不是可用凭据，但本轮验收目标明确要求受版本控制配置/README/scripts/.github 中不再含 `ENC(` 字样；当前工作区仍不满足字面验收条件。
- 影响：最终复核无法给出“完全通过”；若该规则用于自动扫描，也会继续报红。
- 修复建议：将注释改写为不包含 `ENC(` 的表述，例如“用于解密 application-*.yml 中的加密配置值”。
- 优先级：P0

## 问题 2：测试源码仍包含固定 JWT 字面量
- 类型：测试资产残留
- 严重程度：Medium
- 位置：`backend/src/test/java/com/cgcpms/auth/config/JwtPropertiesTest.java:13`
- 证据：`git grep -n -I -E "TEST_JWT_SECRET|test-secret-key-minimum-256-bit-length-for-hs256-algorithm|fixed.*jwt|jwt.secret=.*[A-Za-z0-9_-]{16,}" -- README.md .github scripts backend/src/main/resources backend/src/test/resources backend/src/test/java/com/cgcpms/auth/config`
- 问题说明：业务目标主要限制配置/README/scripts/.github 与 `application-dev/test`，该测试类不在主验收范围内，但它仍保留固定 `jwt.secret` 字面量。
- 影响：不直接阻塞本轮目标中的配置脱敏结论，但会让“仓库彻底摆脱固定测试密钥”这一更强口径无法成立。
- 修复建议：改为从测试属性或环境变量注入，避免源码中固定密钥字符串。
- 优先级：P2

# 必须修复
- 删除或改写 `deploy/.env.example:14` 中包含 `ENC(...)` 的注释文本，确保受版本控制配置中不再出现 `ENC(`。

# 建议优化
- 将 `JwtPropertiesTest` 的固定 `jwt.secret` 字面量替换为环境变量或统一测试属性注入。
- 对敏感串扫描规则增加一条仅针对“真实默认凭据/可用密钥”的说明，降低注释和占位符带来的误判空间。

# 长期演进建议
- 在 CI 增加一条轻量级 secrets-literal 扫描，显式覆盖 `README.md`、`scripts/`、`.github/`、`deploy/`、`application-*.yml`。
- 将测试密钥约束统一到单一入口，避免测试类、脚本、CI 各自维护。

# 测试建议
- 保留本次定向测试组合作为安全回归最小集合。
- 在 CI 增加一条针对敏感字串的 `git grep` 审计命令，避免以后再次把说明性 `ENC(` 注释带回版本库。

# 验收标准
- 验收目标：受版本控制配置/README/scripts/.github 中不再包含 `dev-jasypt-key`、`ENC(`、`cgc123`、`minioadmin`、固定 JWT 测试密钥。
- 验收步骤：执行 `git grep -n -I -E "dev-jasypt-key|ENC\\(|cgc123|minioadmin"`，以及针对 `TEST_JWT_SECRET` / 固定 JWT 字面量的 scoped grep。
- 通过标准：上述扫描仅允许出现环境变量名、占位符说明；不得出现 `ENC(` 或固定可用密钥。
- 必须通过的命令或测试：`git grep` 扫描；`mvnw test -Dtest=GlobalWriteRateLimitFilterTest,RateLimitAspectTest,JwtAuthenticationFilterTest,CorsConfigTest,PaymentWritebackTest,InvoiceControllerTest,InvoiceRecognitionTest,InvoiceServiceTest,JwtPropertiesTest`；`mvnw test -Dtest=MigrationIntegrityTest#payRecordExternalTxnNoUniqueConstraintRemainsTenantScopedForMysqlAndH2`
- 需要人工确认的事项：确认 `sample-invoice.pdf` 没有不应提交的二进制改动，`output/` 与 `scripts/__pycache__/` 不进入提交范围。

# 最终建议
- 修改后合并
- 当前不建议进入提交归档
