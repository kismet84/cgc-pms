# 第100条主线：MySQL TLS 信任链与依赖安全整改

**Goal:** 以 2026-08-30 上午每日全量审计为输入，在当前 `master` 精确基线上关闭 `AUD-20260830-001` MySQL TLS 信任锚缺失、`AUD-20260830-002` Connector/J 受影响版本和 `AUD-20260830-003` MinIO 凭证注释漂移；全部结论只绑定本地代码、配置、制品与 CI 证据。 **Architecture:** 保持现有 Spring Boot 3.5.16、MySQL 8.0、模块化单体、Hikari、Docker Compose、JAR/SBOM/Trivy 和既有门禁；以显式私有 CA、MySQL 服务证书、只读 PKCS12 truststore、`sslMode=VERIFY_IDENTITY` 和禁止系统 truststore 回退形成单一 TLS 信任链，以最窄 Maven 版本覆盖消除受影响 Connector/J。禁止关闭证书校验、提交 Secret、顺带升级 MySQL/Spring Boot、建设第二套密钥平台或把本地验证写成生产发布证据。

> 编制日期：2026-08-30
>
> 审计来源：ChatGPT 会话“每日全量审计报告”2026-08-30 上午审计结果
>
> 审计与当前 Git 基线：`master@5066a5c90bb9048289307a5d60e25c051e399577`，Tree `d3a40a32979b3735bd6d8834be42ec50162f79d3`；`origin/master` 相同
>
> 环境边界：仅本地 dev/test/demo、隔离 Docker 临时卷和远端 CI；项目不存在生产或目标环境，不规划或声称非本地环境测试、验收、发布、凭据或数据操作
>
> 授权边界：用户已明确要求“完成主线计划书100#，并推送”，授权本地实现、验证及受保护 Git 交付；不授权 Tag、Release、版本发布、生产/目标环境或无关改动
>
> 唯一问题载体：`ISSUE-100-001`
>
> 当前状态：`IMPLEMENTED / G0-G3_PASSED / G4_ENVIRONMENT_SPLIT / G5_GIT_IN_PROGRESS`

## 1. 当前事实、审计复核与去重

| ID | 严重级别 | 当前 HEAD 证据 | 本计划裁决 |
| --- | --- | --- | --- |
| `F01 / AUD-20260830-001` | P1 | `deploy/docker-compose.prod.yml` 同时启用 `require-secure-transport=ON` 与 `verifyServerCertificate=true`，但 MySQL 未挂载显式 CA/服务证书，backend 未挂载 truststore，preflight 与 `ProductionEnvironmentValidator` 也未验证信任材料；“Connector/J 默认信任 MySQL 自动生成自签 CA”注释与厂商合同不符 | **确认配置合同缺陷。** 显式配置 MySQL CA/服务证书与 backend truststore；正确 CA 成功，缺失、错误 CA 和身份不匹配全部失败关闭 |
| `F02 / AUD-20260830-002` | P2 | Spring Boot 3.5.16 管理的当前 executable JAR/SBOM 为 `mysql-connector-j 9.7.0`；Oracle 2026-07 CPU 将 9.7.0～9.7.1 列入 Connector/J 多项受影响范围 | **确认直接运行时依赖风险。** 选择不命中受影响范围、与当前 Java/MySQL 获厂商支持的版本，验证 Maven、真实 MySQL、JAR、SBOM 与扫描证据 |
| `F03 / AUD-20260830-003` | P3 | `backend/Dockerfile` 仍称应用 MinIO 凭证来自 root 凭证；当前 Compose、`.env.example` 和 preflight 已明确 root/application 账号隔离并禁止复用 | **确认说明漂移。** 只修正注释并做全仓术语核对，不改变 MinIO 运行语义 |

### 1.1 历史去重

- 第99条主线及 `ISSUE-099-001` 已完成并关闭，处理的是付款并发、制品扫描、模板四态、Tomcat 10.1.59 和另一处 MinIO 注释；本轮三个审计 ID 均未被第99条承接。
- `docs/plans`、`docs/quality`、`docs/backlog` 当前没有 `AUD-20260830-001/002/003`、MySQL truststore 或 Connector/J 9.7.0 整改载体；本计划使用 `ISSUE-100-001`，不重开第99条。
- PR #456 的 Spring Boot 4 聚合升级和 PR #460 的前端大版本升级不是本计划载体；不得把当前安全修复绑定到其失败门禁。

### 1.2 环境口径校准

- 审计报告的“生产发布阻塞”针对仓库中标准 production Compose 合同；项目根规则确认当前仅有本地环境。因此本项仍按 P1 配置缺陷优先处理，但不登记为现存生产环境事故，也不要求不存在的目标环境验收。
- 本地隔离 production-like Compose/TLS smoke、当前 SHA CI 和制品证据属于允许范围；它们不能替代未来另行授权后的真实环境发布证据。
- Spring Boot 3.5 生命周期、真实 QPS/P95、生产监控链和开放批量升级 PR 均为待验证风险，不伪装成本轮确认缺陷或新增 Backlog。

### 1.3 当前工作区约束

- 编制前已有 14 个非本任务脏文件，涉及 Sentry backend/frontend 接入及 Codemap；全部保留。
- `backend/pom.xml` 与本计划 Connector/J 版本覆盖存在同文件重叠。实施前必须确认该脏改动归属并在保留其内容的前提下工作；不得覆盖、回退或顺带提交。
- `docs/codemap/codemap.html/json/lock` 当前也有既存改动。本轮编制阶段不改 Codemap；进入代码实施前必须待归属明确后重新执行新鲜度检查并回答调用、影响、测试三问，依赖或部署数据流变化命中 gate 时由实施批次同步更新。

### 1.4 外部合同与未决兼容性

- [Oracle July 2026 CPU](https://www.oracle.com/security-alerts/cpujul2026.html) 明确列出 Connector/J 9.7.0～9.7.1 受影响。
- [Connector/J 服务器认证文档](https://dev.mysql.com/doc/connector-j/en/connector-j-server-authentication.html) 要求 VERIFY_CA/VERIFY_IDENTITY 使用 Java 默认或显式自定义 truststore；MySQL 自动生成 CA 是 self-signed，不能假定被 JVM 默认信任。
- [Connector/J 26.7 简介](https://dev.mysql.com/doc/connector-j/en/connector-j-whats-new.html) 称 26.7.0 取代 9.7、推荐生产使用且可连接 MySQL 8.0+；但[兼容性页面](https://dev.mysql.com/doc/connector-j/en/connector-j-versions.html) 同时写明支持 MySQL 8.4+。仓库当前服务端固定 MySQL 8.0，二者存在官方文档冲突。
- 因此 `26.7.0` 只能作为首选候选，不在计划阶段伪装成已冻结版本。G1 必须取得一致的厂商支持依据并完成当前 MySQL 8.0 真实回归；若不能消除冲突，保持 P2 开放，不把 MySQL 8.4 升级静默并入本计划。

## 2. 范围与非目标

### 2.1 实施范围

1. MySQL TLS：显式 CA、服务端证书/私钥、backend PKCS12 truststore、只读挂载、`VERIFY_IDENTITY`、禁止系统 truststore 回退、preflight 与应用启动校验。
2. TLS 验证：新增本地/CI 隔离 smoke，证明正确 CA 成功、错误/缺失 CA 失败、服务身份不匹配失败、会话 TLS cipher 非空。
3. Connector/J：以最窄依赖覆盖移出 9.7.0～9.7.1，验证当前 MySQL 8.0、Java 21、Hikari、Flyway、租户与并发链。
4. 供应链：证明 dependency tree、executable JAR、SPDX SBOM、artifact scan 绑定同一代码 SHA，且受影响版本无残留。
5. MinIO：更新 `backend/Dockerfile` 两条说明，使其与独立最小权限应用账号事实一致。
6. 治理：更新计划、问题源、质量报告和必要 Codemap；每个发现项只允许本轮修复、正式承接或有据关闭。

### 2.2 非目标

- 不升级 MySQL 8.0→8.4，不实施 Spring Boot 4、Spring Framework 7 或 PR #456/#460。
- 不改数据库 Schema、已应用 Flyway migration、业务数据、权限、租户、金额、状态机或 API。
- 不提交 CA 私钥、服务端私钥、truststore、truststore 密码或 `.env`；不把示例占位符当真实 Secret。
- prod 有效配置、Compose 和非测试运行入口不得关闭 `verifyServerCertificate`、降级为 `sslMode=REQUIRED/PREFERRED` 或 fallback 到系统 truststore；负向测试夹具允许且必须包含这些弱化值，以证明会被拒绝。
- 不引入 Vault、KMS、第二套密钥平台、第二套供应链扫描器或通用证书生命周期服务。
- 不操作当前 Sentry/Codemap 脏改动；不启动、修改或清理其他工作树、分支、PR 或运行环境。
- 不执行生产/目标环境测试、数据库写入或发布，也不把本地/CI 结果称为“生产可上线”。

## 3. 设计冻结

### D01 单一 TLS 信任链

- 使用一条显式私有 CA 签发 MySQL server certificate；证书 SAN 至少包含 Compose 服务身份 `DNS:mysql`。MySQL 只读挂载 CA、server certificate 和 server key，并通过 `--ssl-ca`、`--ssl-cert`、`--ssl-key` 和 `--require-secure-transport=ON` 使用。
- G1 冻结证书 profile：CA 必须为 CA 证书，server certificate 具备 server authentication 用途；实现验证 CA 链、certificate/key 匹配、SAN、有效期与至少 30 天到期阈值。server key 仅 MySQL 可读、不得 group/world writable，Windows 本地至少证明只读挂载且 backend 不可见。
- 将同一 CA 导入独立 PKCS12 truststore；backend 仅只读挂载 truststore，不挂载 MySQL server key 或整个 MySQL data volume。
- Connector/J 使用 `sslMode=VERIFY_IDENTITY`、`trustCertificateKeyStoreUrl=file:/run/secrets/mysql-truststore.p12`、`trustCertificateKeyStoreType=PKCS12`、独立密码和 `fallbackToSystemTrustStore=false`。
- truststore password 通过现有环境 Secret 注入；优先放入 Hikari `data-source-properties`，禁止拼接进可打印的 JDBC URL。错误消息只输出配置键名，不输出 URL、密码、证书正文或文件内容。
- 移除 legacy `useSSL/requireSSL/verifyServerCertificate` 混合表达，保留 `allowPublicKeyRetrieval=false`；以 `sslMode` 作为唯一 TLS 级别事实源。

### D02 fail-close 配置门

- `deploy/.env.example` 只声明路径、类型和占位密码；真实材料位于已忽略目录或外部只读路径。
- Compose preflight 必须检查 CA、server certificate、server key、truststore 文件存在且非空，truststore password 非空且非占位；任何一项缺失时其他服务不得启动。
- `ProductionEnvironmentValidator` 必须要求 `VERIFY_IDENTITY`、显式 truststore URL/type/password、`fallbackToSystemTrustStore=false`，拒绝不安全或缺失值，并保持脱敏。
- `scripts/ci/test-runtime-deployment-contract.ps1` 固化挂载、路径、TLS 模式、禁止弱化和 secret-ignore 合同。

### D03 真实 TLS smoke

- 测试材料运行时临时生成，不入库；CA 签发 server cert，SAN 覆盖测试地址和 `mysql` 身份。
- 使用当前 Compose 同一 pinned MySQL 8.0 image digest与隔离 Docker network/临时卷；不得复用或重置现有 `cgc_pms` 数据卷。
- 正向：使用正确 truststore 建连，执行最小查询并读取 `Ssl_cipher`，其值必须非空。
- 负向：错误 CA、缺失 truststore、错误密码和 hostname/SAN 不匹配均必须建连失败；失败日志不得泄露 Secret。
- 优先扩展现有 `backend-test-mysql`/`reliability-contracts` required job，不新造绕开既有汇总门禁的独立可选 job。

### D04 Connector/J 版本决策

- 目标版本必须同时满足：不在 Oracle CPU 9.7.0～9.7.1 受影响范围、支持 Java 21、获厂商明确支持连接当前 MySQL 8.0、Maven Central/仓库可解析。
- `26.7.0` 为首选候选，但只有 G1 兼容性依据闭合后才允许写入 `backend/pom.xml`。不得用“本地能连一次”替代厂商支持声明。
- 若唯一安全受支持版本要求 MySQL 8.4，则停止依赖实施并保持 F02 开放；MySQL 服务端大版本升级必须另立计划、风险与迁移验收，不能在本计划中隐式扩域。
- 最终 dependency tree、JAR `BOOT-INF/lib` 与 SBOM 只能包含一个选定 Connector/J 版本，禁止 9.7.0/9.7.1 残留或双版本。

### D05 MinIO 说明一致性

- `MINIO_ACCESS_KEY`/`MINIO_SECRET_KEY` 明确为独立最小权限 application/service account，不来自 root 凭证。
- 只修正 `backend/Dockerfile` 注释；Compose、`.env.example` 和 preflight 当前 root/application 隔离语义保持不变。

## 4. 预计文件边界

| 文件 | 计划动作 | 主要验证 |
| --- | --- | --- |
| `deploy/docker-compose.prod.yml` | MySQL 证书与 backend truststore 只读挂载、TLS 参数、preflight | Compose config、runtime contract、TLS smoke |
| `deploy/.env.example` | 增加证书/truststore 路径与密码占位说明 | 占位符/Secret 静态检查 |
| `.gitignore` | 仅在现有 `deploy/secrets/` 规则不足时补充证书材料忽略 | `git check-ignore` |
| `backend/src/main/resources/application-prod.yml` | Hikari Connector/J TLS data-source properties | 配置绑定与启动测试 |
| `backend/src/main/java/com/cgcpms/config/ProductionEnvironmentValidator.java` | TLS/truststore fail-close 校验 | 单元负向测试、脱敏断言 |
| `backend/src/test/java/com/cgcpms/config/ProductionEnvironmentValidatorTest.java` | 正向与弱化/缺失/占位负向用例 | JUnit |
| `backend/pom.xml` | 最窄 Connector/J 安全版本覆盖；保留既有 Sentry 脏改动 | effective POM、dependency tree、Maven verify |
| `backend/Dockerfile` | 修正 MinIO application credential 注释 | 全仓术语搜索 |
| `scripts/ci/test-runtime-deployment-contract.ps1` | 固化 TLS、挂载、Secret 与 fail-close 合同 | PowerShell contract test |
| `scripts/ci/run-mysql-tls-smoke.ps1`（候选新增） | 生成临时证书、启动隔离 MySQL、执行正负向 smoke、可靠清理 | 本地 Windows/CI |
| `backend/src/test/java/com/cgcpms/integration/MySqlTlsSmokeTest.java`（候选新增） | JDBC TLS cipher、正确/错误 truststore 证据 | 真实 MySQL JUnit |
| `.github/workflows/ci.yml`、`scripts/ci/test-workflow-contract.ps1` | 将 TLS smoke 纳入既有 required gate | workflow contract |
| `docs/codemap/*` | 仅实现触发依赖/数据流变化且归属明确后更新 | generator verify |
| `docs/quality/2026-08-30-issue-100-MySQL-TLS信任链与依赖安全整改.md`（实施后） | G0～G5、差异、测试、风险与零悬空证据 | 引用与静态核对 |

候选新增文件名可在 G1 依现有测试布局微调；不得因此扩大职责或引入新框架。

## 5. G0～G5 门禁

| 门禁 | 必须取得的证据 | 不通过动作 |
| --- | --- | --- |
| G0 基线与归属 | 复核 branch/HEAD/status/worktree；确认 `backend/pom.xml` 与 Codemap 既存改动归属；审计三项逐项对账；记录本地 Docker/MySQL/URL；Codemap 回答调用、影响、测试三问 | 归属不清、Codemap 陈旧或基线漂移时停止代码写入 |
| G1 契约完整 | 冻结 D01～D05；取得固定 Connector/J 版本与 MySQL 8.0 的一致厂商支持依据；形成证书 SAN、Secret、正负向测试、文件清单和安全只读复核 | 版本兼容性仍矛盾、需要 MySQL 8.4、证书来源/权限不清时保持 `NOT_READY` |
| G2 数据与迁移 | 明确无 migration；隔离新卷完成 MySQL fresh baseline、Flyway、租户与并发回归；无现有数据卷重置或复用 | 触及既有数据、需改 migration 或 TLS 下数据库回归失败时停止 |
| G3 服务端闭环 | preflight、应用 validator、正确/错误 truststore、身份校验、cipher、Maven verify、dependency tree/JAR/SBOM/scan 全部闭合且不泄密 | 任何弱化绕过、双版本、Secret 泄露或 9.7.0/9.7.1 残留均失败关闭 |
| G4 本地运行与浏览器 | 本地隔离 TLS 栈 backend health UP；关键 API、登录和一个列表读取成功；浏览器 DOM/console 无新增错误；普通 dev/test 配置不被 prod 强校验误伤 | 只做静态检查、错误 CA 仍成功、健康/API/浏览器链异常时不得进入收口 |
| G5 正式收口 | 相关全量门禁、质量报告、问题源、Codemap（如触发）、独立安全复核、零悬空统计齐全；若另获 Git 授权，再按同 SHA CI/PR/受保护合并/post-merge 执行 | 任一审计项无处置、证据未绑定当前 SHA、把本地/CI 写成生产结论或 Git 权限不足时保持未完成 |

## 6. 实施批次与顺序

1. **M0 归属与兼容性决策：** 先消除 `backend/pom.xml`/Codemap 归属冲突，完成 Connector/J 固定版本支持矩阵；未通过不得写代码。
2. **M1 TLS 负向先行：** 先让“缺 truststore、错误 CA、身份不匹配、系统 truststore 回退”测试失败，再实现 Compose、配置、preflight 和 validator。
3. **M2 Connector/J 窄修复：** 只覆盖 driver 版本；执行 effective POM、dependency tree、Maven verify、真实 MySQL 回归和制品扫描。
4. **M3 文档与合同：** 修正 MinIO 注释，扩展 runtime/workflow contract，核对 `.env.example`、Dockerfile、Compose 与应用配置术语。
5. **M4 本地 G2～G4：** 使用隔离临时卷执行 TLS 正负向 smoke、backend health、关键 API 和最小浏览器回归。
6. **M5 G5 收口：** 生成质量报告、回写 `ISSUE-100-001`、统计零悬空，并按本轮授权执行同 SHA CI、PR、受保护合并、post-merge 验真与源分支安全清理。

## 7. 验收标准

### AC01 TLS 信任链

- 标准 Compose 合同显式配置 CA、server certificate/key 和 backend PKCS12 truststore；server key 不向 backend 暴露。
- Connector/J 使用 `VERIFY_IDENTITY`、显式 truststore、PKCS12 和 `fallbackToSystemTrustStore=false`；prod 有效配置、Compose 和非测试运行入口不存在 `verifyServerCertificate=false`、`sslMode=REQUIRED/PREFERRED` 等弱化路径。负向测试夹具必须保留并断言这些值被拒绝。
- 正确 CA/SAN 建连成功并返回非空 `Ssl_cipher`；错误 CA、缺失 truststore、错误密码和身份不匹配全部稳定失败。
- preflight 与 `ProductionEnvironmentValidator` 在应用业务 bean 初始化前失败关闭，且异常只列配置键名。

### AC02 Connector/J 安全版本

- Oracle CPU 9.7.0～9.7.1 受影响范围不再命中；选定版本有当前 MySQL 8.0 与 Java 21 的明确支持证据。
- `dependency:tree`、executable JAR 与 SPDX SBOM 均只有一个选定版本，无 9.7.0/9.7.1 残留。
- `./mvnw -C verify`、MySQL fresh baseline、Flyway upgrade、tenant、code generation、付款/材料/通讯并发回归全部通过。
- `scripts/ci/test-backend-artifact-scan.sh` 与当前 JAR 扫描通过；扫描器 0 finding 不替代 Oracle CPU 版本范围核对。

### AC03 MinIO 说明

- Dockerfile、Compose、`.env.example` 和 preflight 均明确 root/application 凭证分离。
- 不再出现“application credential 从 root credential 注入”的说明；运行时环境变量和值保持不变。

### AC04 本地与 CI 回归

- runtime deployment contract、workflow contract、Codemap verify（如触发）通过。
- 本地 backend health、登录、一个关键列表 API/页面和浏览器 console 通过；普通 `dev/test` profile 不要求 prod truststore。
- 若后续 Git 交付获授权，G5 前重新实时读取分支保护；截至编制时的 15 个 required contexts 必须绑定同一 HEAD SHA：`pr-push-evidence`、`desktop-launcher`、`backend-test`、`backend-order-sensitive`、`backend-dependency-scan`、`backend-test-mysql`、`frontend-lint`、`type-check`、`frontend-build`、`frontend-test`、`frontend-dependency-audit`、`frontend-v2-gate`、`sql-safety-scan`、`supply-chain-security`、`e2e`。`reliability-contracts` 仍是 workflow/build-summary 内部必过 job，也必须成功，但不冒充分支保护 required context；本地命令不替代远端同 SHA 证据。

## 8. 风险、恢复与回滚

| 风险 | 控制 | 回滚/恢复 |
| --- | --- | --- |
| server cert SAN 与 Compose `mysql` 身份不匹配 | 固定 SAN 合同与负向身份测试 | 更正测试/本地证书后重跑；不得降级到 VERIFY_CA/REQUIRED 绕过 |
| truststore 路径、类型、密码或权限错误导致 backend 启动失败 | preflight、validator、只读挂载和正负向 smoke | 回退本轮配置补丁并保持 F01 开放；禁止恢复“默认信任”错误注释后判通过 |
| Connector/J 26.7 与 MySQL 8.0 厂商支持口径冲突 | G1 支持矩阵、真实回归、独立复核 | 不写或回退 driver 覆盖并保持 F02 开放；另立 MySQL 8.4 计划，禁止静默扩域 |
| Connector/J 变更影响 Flyway/Hikari/时区/并发 | 全量 Maven + MySQL 回归、JAR/SBOM 核对 | 原子回退 driver 版本；F02 继续开放，不跳过测试 |
| Secret、证书正文或 URL 密码进入 Git/日志 | ignore、占位符、脱敏异常、diff/日志扫描 | 删除任务自有泄露工件并轮换本地测试 Secret；若进入 Git 历史需另获历史处理授权 |
| 与既存 Sentry/Codemap 脏改动冲突 | G0 归属确认、逐文件保留、差异复核 | 停止同文件写入；不得 checkout/reset/覆盖其他任务成果 |
| TLS smoke 增加 CI 时间或残留容器/卷 | 唯一前缀、`try/finally`、超时、隔离临时卷 | 清理仅本次 smoke 的精确容器/网络/卷；不得删除现有项目卷 |

本计划不含 migration、业务数据变更或不可逆外部副作用。恢复只允许操作任务自有文件和精确命名的临时 Docker 资源。

## 9. 失败分类

验证失败先归入唯一类别：`tool_config`、`tool_invocation`、`environment_prerequisite`、`ready_issue_config`、`retrieval_gap`、`quality_or_security`、`unknown`。DNS、镜像/漏洞库下载、旧容器、证书工具缺失和测试夹具问题未分类前不得认定为业务回归；`unknown` 必须失败关闭并保留证据。

## 10. 零悬空与实施状态

- `AUD-20260830-001/002/003` 全部由唯一载体 `ISSUE-100-001` 承接；第99条保持关闭，不制造重复计划。
- 实施阶段新增正式后续项 0、关闭 0、后续项净变化 `0`；计划全周期新增 1，待 G5 关闭 1 后净变化归零。无无载体审计遗留项。
- 生命周期、真实环境性能/监控和 PR #456/#460 未达到本轮确认缺陷或独立可验收价值，不新增后续项。
- G0 已在隔离工作树确认任务归属并刷新 Codemap；G1 选择 Connector/J 8.4.0，避免 26.7 对 MySQL 8.0 的支持口径冲突，且未升级 MySQL。
- 当前 Ready 保持 0；本轮来自用户直接授权，不进入 AutoPilot Ready。
- 当前状态：代码、配置、合同、TLS smoke、真实 MySQL、Maven 与制品扫描已完成；G4 的 TLS backend health 已通过，完整浏览器业务流因宿主 Docker Desktop API 500 与 Windows WEPoll 前置故障改由同 SHA CI 补证。Issue 在 G5 Git 与零悬空完成前保持开放。
