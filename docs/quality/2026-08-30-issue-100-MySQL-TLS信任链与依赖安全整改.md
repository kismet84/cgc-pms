# 第100条主线：MySQL TLS 信任链与依赖安全整改验收报告

> 日期：2026-08-30
> 唯一载体：`ISSUE-100-001`
> 编制基线：`master@5066a5c90bb9048289307a5d60e25c051e399577`
> 复核日期：2026-09-06
> 当前裁决：`G0_G3_PASSED / G4_ENVIRONMENT_BLOCKED / G5_NOT_READY`；功能分支 `c2b81a21` 已推送，同 SHA CI 与 pre-PR 证据核验通过；新组合真实浏览器验收未完成，未创建 PR、未合并

## M6 当前权威记录（2026-09-06）

用户已明确批准 MySQL 8.4 隔离升级、实施与既有受保护 Git 交付，不再等待重复授权。当前采用 Connector/J 26.7.0 与 MySQL 8.4.12，厂商兼容性页与正式发布说明均覆盖该组合。后续编号章节保留 M6 前历史；其中 driver8.4.0、MySQL8.0、等待升级授权、旧浏览器通过等不再代表当前版本或门禁结论。

| 门禁/证据 | 当前结果 |
| --- | --- |
| G0 | 独立 `codex/mainline-100` 工作树；旧 dev 卷及原工作区脏改动保留，无业务 schema/API/权限语义变更 |
| G1 供应链 | Oracle 固定输入，移除 mysql-shell 及 Python、SQLite 官方修复 RPM；独立 Alpine/OpenSSL 预检；精确 runtime ID `sha256:ee7bf662aa692abb5c6384b39796de4796001990b83b47ab020abafde96f2895`、preflight ID `sha256:99f3bf5ead9d68b9a33886d144bcfd90b3f073d25f640c2f0d5be1ebb7229d84`，Trivy Metadata.ImageID 与运行输入相等，高危/严重均0；不是永久无漏洞声明 |
| G2 跨引擎 | 加强对象语义后的本地与 `c2b81a21` CI 均通过：同一8.0备份先恢复8.0再导入8.4.12，230表行数、全部行字节、生成列/CHECK/ENFORCED/外键动作等一致；fresh/V180迁移、租户和并发组通过 |
| G3 静态与测试 | validator22、备份/恢复28、cleanup AST18、部署/工作流契约通过；前端649/649及浏览器contract98项通过。`c2b81a21` 的完整Maven/MySQL、JAR/SPDX与供应链等16个required jobs全绿，pre-PR verifier通过；Windows本地Office错误不冒充本地成功，以该CI平台等价验证 |
| G4 | 本地新8.4.12/26.7、精确JAR已启动，TLS preflight、真实认证API通过；健康503且新MinIO无必需测试桶。创建测试桶命令被执行策略拒绝，不换入口绕过、不禁用健康检查。浏览器仅观察到登录页，尚无有样本链路与最终DOM/console证据；保持阻塞 |
| G5 | 独立只读复核发现已修复，动态与远端收口未完成，唯一Issue保持开放，不创建非Draft PR、不合并、不清理源分支 |

失败按首次事实保留：Docker Desktop代理出现 `192.168.65.7:2376: no route to host`（environment_prerequisite），随后只读version恢复，未重启共享服务；首次完整Maven漏注入CI测试JWT环境（tool_config），2743测试/1728错误/30跳过、BUILD FAILURE，补环境后复验；前端默认并发645/649、4项约5秒超时，原断言/默认超时不变，单worker完整649通过，归environment_prerequisite。镜像绑定与对象快照缺口、cleanup异常及.NET空变量恢复差异归quality_or_security，本轮直接修复并复验，不延期。

具体版本来源、升级前备份哈希、保留资源、风险与恢复边界见 [M6阶段计划](../plans/第100条主线-M6-MySQL8.4升级与ConnectorJ安全版本恢复任务计划书-2026-09-06.md)。新增后续项0、关闭0、净变化0；`ISSUE-100-001`未关闭。没有升级实际dev库，没有生产、Tag、Release或镜像仓库发布。

### 补充失败分类与工具风险边界

- **2026-09-06 03:12 最新证据**：远端功能分支与本地HEAD均为 `c2b81a2108971416209948383e7b4a3f1e3155e8`；[修订版Push CI](https://github.com/kismet84/cgc-pms/actions/runs/33985531989)完整通过，pre-PR verifier绑定16个required jobs为PASS。CI实际运行镜像 `f3c7f3947ee4a3a4cdf187e6348b25ad782c7182ae35aa0fe22532d0ff691993`、预检镜像 `551bb65e46ec6f4b4cbcdd42f057af031bd27432bffd024c5bd0f0be977d2dd9` 的Metadata.ImageID与该SHA一致，高危/严重0；跨引擎批次 `369a53c88e0a` 的同备份8.0恢复、8.4导入和全部对象/行字节通过，CI批次清理完成。首次失败run仍保留，不追溯改写。
- **本地恢复补证**：Docker API再次自行恢复，未重启共享服务。加强版批次 `5e8ee57217f1` 完成230表及全对象/行字节守恒，升级前dump SHA256=`71FA050213BCAEBC0582025B3ED50842DE4EE9905BDABE6BA32ECFB0425C2C21`。在该隔离8.4.12目标内另行执行真实原生socket备份/恢复，230张BASE TABLE计数及二进制/存储过程探针一致，gzip SHA256=`B89733FC765B0C25416D8E7C1C035C851BD376568C0528343F345CC598BAA48A`。未触碰dev库。已停止全部本任务跨引擎服务，保留容器、卷与备份供恢复。
- **G4前置与策略阻塞**：隔离项目 `cgc-pms-m100-g4b` 使用新卷、只读信任材料及本地JAR SHA256=`1CCAE573F17932250BFD17E6FA43A73B685EE6F2AD5C2D5D7117CF210A97AD8F`。Windows bind权限呈现导致preflight拒绝可写key，改为本任务Linux命名卷中UID27、0400文件再只读挂载后通过；本地prod-profile CORS拒绝loopback配置，改为内部origin并使用仓库已有Vite同源代理，未修改validator。新MinIO经认证列桶为空，缺少健康检查必需的`cgc-pms`测试桶，归environment_prerequisite；准备该桶的命令被工具执行策略拒绝，归tool_config/执行权限不可用，停止该步骤，不重试同一动作或改用其他入口。健康503未放行。已停止本项目5个运行服务与本任务Vite31648并关闭临时浏览器，保留7个容器、6个卷、网络及信任材料；未删除数据、未影响现有dev服务。解除执行限制后，仍需补测试桶、健康UP、有样本浏览器及G5，再提交本次状态回写并取得最终SHA Git证据。

- 功能分支 `codex/mainline-100` 已推送，远端HEAD确认为 `bef6aaf9aaf8161ed409d17675873a86ae9671dd`；[首次Push CI](https://github.com/kismet84/cgc-pms/actions/runs/33983978708)未通过，未创建PR、未合并。该次本地pre-push完整门禁通过，浏览器contract98项、0skip/0flaky。CI reliability-contracts暴露Linux隐藏临时SQL的Get-Item未加Force，已用Windows Hidden属性等价复现并修复，28项复验通过；backend-test-mysql四组真实preflight通过但TLS库就绪超时，归unknown，补实际应用账号探测和脱敏错误/容器日志后复验。两项均为本轮直接门禁问题，不记为外部Runner故障；修后不追溯修改首次CI结论。尚不能声明可提PR或G5完成。
- 正确注入测试JWT后的完整Maven：2962项、1 failure、5 errors、30个条件skip，30分钟、BUILD FAILURE。两项成本工作流因规则夹具CURRENT_DATE晚于固定2026-08业务期间而失败；仅将测试规则生效日固定为2020-01-01。付款冲销时间断言在秒为0时比较了不同字符串格式；改为同格式解析后的LocalDateTime相等，并显式覆盖整分钟。不改业务Resolver、金额、状态或时间语义；三项最小复验通过，两完整测试类34/34通过。OfficePreviewClientTest三项为Windows WEPollSelectorImpl创建loopback失败、SocketException `Invalid argument: connect`，归environment_prerequisite，待Linux同SHA CI等价验证，不计本地通过。
- Docker再次代理不可达，WSL配置上限4GB、swap2GB；因果关系尚不能确认。停止4个已完成任务容器的请求均返回API500，不能声称已停止。已实现后续新演练每个MySQL上限512MB，并在目的库验证后停止该服务、保留卷，避免同时保留多组运行实例。未修改WSL配置、未重启共享环境；真实跨引擎最终目标、备份与G4仍受阻。
- Upgrade Checker使用固定Oracle工具镜像digest `7dcc4add9183664de3a214daf85a50c3ba6cccfd7534f700b6561bf5b41885be`，旧扫描Metadata.ImageID与之相等，存在5条High：sqlite-libs `3.34.1-10.el9_8` 的 `CVE-2026-11822/11824`，cryptography `46.0.7` 的 `CVE-2026-69247/69249` 与 `GHSA-537c-gmf6-5ccf`。独立只读复核并由主线程核对实际代码：checker仅调用 `util.check_for_server_upgrade`，只连接内部本批次源MySQL，输入为仓库schema、合成fixture和该容器my.cnf；无端口发布、`--rm`、证据只读挂载。没有恶意SQLite FTS5/MATCH、PKCS7解密服务、攻击者证书链或超大DER输入，因此上述触发条件不进入本次调用。按当前不可达证据关闭本次风险，不制造后续Issue；这不是整个CI镜像零漏洞声明。若增加外部数据库/证书/SQLite/PKCS7输入、服务监听或持久写挂载，裁决立即失效，必须重新扫描复核或使用修复镜像。runtime与preflight的0高危/严重结论不包含此一次性工具镜像。

## 1. 范围与边界

本轮承接 `AUD-20260830-001/002/003`：补齐 MySQL 显式 CA、服务证书、Java PKCS12 truststore 与 Connector/J `VERIFY_IDENTITY`；试验 Connector/J 8.4.0 兼容性；修正 MinIO application credential 注释。8.4.0 安全支持证据不足，F02 未关闭，整个主线未完成。无 migration、业务 API、权限、租户、金额或状态机变化。

只验证本地隔离 Docker、临时证书、当前 JAR 与远端同 SHA CI；项目不存在生产或目标环境，本报告不构成生产发布证据。测试证书、私钥、truststore、密码和临时卷均不得入库。

## 2. 实现结果

| 项目 | 结果 |
| --- | --- |
| MySQL 服务端 | 固定 MySQL 8.0 digest；显式 `--ssl-ca/--ssl-cert/--ssl-key`；`require-secure-transport=ON`；证书只读挂载 |
| backend | 只读挂载独立 PKCS12 truststore；Hikari 固定 `sslMode=VERIFY_IDENTITY`、PKCS12、`fallbackToSystemTrustStore=false`、`allowPublicKeyRetrieval=false` |
| preflight | 校验证书链、CA、serverAuth、30 天阈值、`DNS:mysql`、证书/私钥匹配、Java `trustedCertEntry` 和 CA 指纹一致性 |
| 应用门禁 | `ProductionEnvironmentValidator` 要求完整 Hikari TLS 键并拒绝 legacy/重复参数；错误信息只列键名 |
| TLS smoke | 运行时生成 CA/server cert；正确信任链返回非空 cipher；缺失 truststore、错误密码、错误 CA、hostname 不匹配全部失败 |
| Connector/J | Maven/JAR 固定单一 `mysql-connector-j:8.4.0`，移除 9.7.0/9.7.1 |
| MinIO | Dockerfile 注释与 Compose 已有 root/application 账号隔离合同一致；无运行语义变化 |

运行态验收中额外发现：仅用 `openssl pkcs12 -export -nokeys` 生成的普通证书包可被旧 preflight 读取，却不是 Java `trustedCertEntry`，Connector/J 会报 `trustAnchors parameter must be non-empty`。本轮已将此直接引入风险纳入修复：preflight 要求 `Trusted key usage`，部署手册明确必须使用 `keytool -importcert -storetype PKCS12`。

## 3. G0～G5 证据

### G0 基线与 Code map — PASS

- 使用隔离工作树 `D:\projects-test\cgc-pms-mainline-100`、分支 `codex/mainline-100`；原工作区 Sentry/Codemap 脏改动未读取、覆盖、暂存或清理。
- 代码修改前刷新 Code map。`prod-config-gate` 调用入口为 `CgcPmsApplication.application.addInitializers`，影响 Spring API 启动，覆盖测试为 `ProductionEnvironmentValidatorTest`。
- 本轮改变依赖与部署数据流，最终实现后必须再次生成并执行 `--verify`。

### G1 兼容性与安全契约 — BLOCKED（撤回此前 PASS）

- 8.4.0 的 GA、MySQL 8.0/Java 21 兼容及本地测试证据保留，但不证明当前安全支持。此前用“未命中公告版本范围”将 F02 判为闭合，依据不足，现撤回。
- [Oracle July 2026 CPU](https://www.oracle.com/security-alerts/cpujul2026.html) 表头为受影响的受支持版本；旧版本未列不能据此认定不受影响。
- 2026-09-06 核对：[当前主指南](https://dev.mysql.com/doc/connector-j/en/)（2026-08-31 修订）与 [26.7 简介](https://dev.mysql.com/doc/connector-j/en/connector-j-whats-new.html) 称 MySQL 8.0+；[兼容性页](https://dev.mysql.com/doc/connector-j/en/connector-j-versions.html) 仍称 8.4+。尚不满足本计划的一致厂商支持要求；不得仅用本地回归替代，也不静默升级 MySQL。
- 分类 `quality_or_security`（版本安全闭合证据不足）。需要取得一致支持证据，或由用户明确调整验收约束后验证 26.7；在此之前保持 Issue 开放，不进入 Git 交付。
- 补查发布源：[26.7.0 正式发布说明](https://dev.mysql.com/doc/relnotes/connector-j/en/news-26-7-0.html)也明确要求 MySQL 8.4+，不是只有兼容性单页如此。Maven Central `com/mysql/mysql-connector-j/maven-metadata.xml` 当前 latest/release 均为 26.7.0，9.x 最后一项为 9.7.0，没有可选的 9.7.2；不能借用 MySQL Server 的 9.7.x 版本号作为 JDBC 驱动版本。推荐保留厂商支持验收门，等待厂商澄清；如需立即采用受支持组合，必须另获 MySQL 8.4 升级范围授权并按计划独立评估迁移，不能继续把保持 MySQL 8.0 的 26.7 回归视为等价的厂商支持证明。
- 用户随后授权另立升级计划，已编制 [M6](../plans/第100条主线-M6-MySQL8.4升级与ConnectorJ安全版本恢复任务计划书-2026-09-06.md)。该计划按当前只读运行基线与真实镜像注册表结果编制，不将缺失的 8.4.12 标签或可读取的 8.4.11 manifest 当作已通过安全门；升级实施澄清尚未返回，本次未改运行态/driver 版本、未迁移现有库。

### G2 数据、迁移与真实 MySQL — 既有候选版本证据保留

- 无 migration；所有数据库验证使用固定 MySQL 8.0 digest、隔离网络和临时卷，未复用或重置现有 `cgc_pms` 数据。
- fresh baseline/Flyway `1/1`，付款并发 `10/10`，材料删除并发 `2/2`，通讯并发 `2/2`，投标项目范围 `2/2`，RBAC tenant association `3/3`，code generation 聚焦复验 `5/5`。
- Windows 固定 MySQL 8.0 的 V180→V307 upgrade `1/1`。
- code generation 首轮因测试只允许 loopback、容器使用 `mysql` hostname 而未执行逻辑，分类 `tool_invocation`；改为共享网络命名空间并使用 `127.0.0.1` 后通过。

### G3 服务端、制品与供应链 — 本地证据保留，SPDX/同 SHA CI 未完成

- `ProductionEnvironmentValidatorTest`：`22/22`。
- TLS smoke：`1/1` 正向、4 个负向全部按预期失败；cipher 非空。
- 完整 Maven 测试：Windows 执行 2959 项，唯一 3 个 Office loopback 用例受 Windows WEPoll 限制；同代码 Linux 聚焦 `3/3` 通过。计划内共 2962 项，产品失败 0。Windows `-DskipTests verify`、打包和 JaCoCo 门通过。
- dependency tree 仅 `com.mysql:mysql-connector-j:8.4.0:runtime`；executable JAR 仅 `BOOT-INF/lib/mysql-connector-j-8.4.0.jar`。
- JAR SHA-256 `82ab9daccb659c01c102effa0eb24763565d6cc2e270c5036d2e3d3f067daa3e`；162 个 backend library、166 个 Trivy Java package，实际 vulnerability 0，Critical/High 0。此结果只表示本次本地扫描未发现，不替代 Oracle CPU 范围核对。
- 本地 dependency tree 与 executable JAR 已闭合；SPDX 由既有 `anchore/sbom-action` 在同 SHA CI 生成，当前尚未取得远端工件，不将本地缺失误写成已验证。
- runtime deployment contract、workflow contract、Compose config 均通过；最终提交前需重跑绑定最终 diff。

失败分类：首次 Maven verify 缺 `TEST_JWT_SECRET`、Office Windows loopback、两次直接 surefire 未展开 `@{argLine}` 分别归 `tool_invocation`、`environment_prerequisite`、`tool_invocation`；修正合法前置或 Linux 聚焦复验后均闭合，不掩盖产品失败。

### G4 本地运行与浏览器 — 本地真实链路已补齐，整体仍受 G1 阻塞

- 隔离 production-like Compose 的 preflight、MySQL、Redis、MinIO、ClamAV 已健康；backend 使用本轮 JAR、Java `trustedCertEntry` truststore完成 V1→V307 migration，在数据库名校准后以 79.673 秒记录 `Started CgcPmsApplication`。创建临时 `cgc-pms` bucket 后 `/api/actuator/health` 返回 `UP/200`；前端入口及其代理健康均返回 200。该链证明真实 `VERIFY_IDENTITY`、Flyway 与健康检查成立。
- 运行态发现并修复普通 OpenSSL cert bag 不是 Java trust anchor 的门禁缺口：新版 preflight 要求 `Trusted key usage`；Java `keytool` truststore 正向通过。
- 应用内浏览器确认旧本地镜像 `cgc-pms-frontend-v2:m0` 只是历史静态健康页。当前 SPA 已在宿主执行 `vue-tsc` 与 production Vite build 成功；准备挂入 production nginx 时 Docker Desktop engine 全面返回 API 500，随后现有 dev/G4 容器端口均超时。宿主 JAR fallback 完成 H2 V307 后又被已知 Windows WEPoll `Unable to establish loopback connection` 阻断 Tomcat。两项均分类 `environment_prerequisite`，未判产品回归，也未越权重启影响其他容器的全局 Docker Desktop。
- 2026-09-05/06 Docker 恢复后，以本轮同一 JAR（SHA-256 与 G3 相同）重新取得 backend `http://localhost:18081/api/actuator/health` 的 `UP/200`。当前工作树 Vite 仅绑定 `127.0.0.1:18082` 并代理该 TLS backend，没有使用普通 dev backend 或 mock API。
- 应用内浏览器真实账号、租户 0 登录后落到 `/dashboard`，再进入 `/contract/ledger?contractStatus=DRAFT`；`/api/contracts?pageNo=1&pageSize=10&contractStatus=DRAFT` 返回 HTTP 200、`code=0`、`total=0/records=[]`，与隔离空业务数据基线一致。页面“合同台账”标题唯一、旧“隔离底座已启动”标识为 0，warn/error console 为空。不将空列表描述为有业务行样本。
- 旧 Docker/WEPoll 失败保留为历史 `environment_prerequisite`，本次真实浏览器补证已恢复；此前“由同 SHA contract E2E 替代登录列表”的建议无效，现已纠正。

### G5 收口与 Git — NOT_READY

- G1 重开后停止 Git 交付。远端 `master` 仍为 `5066a5c90bb9048289307a5d60e25c051e399577`，`codex/mainline-100` 远端分支不存在、无 PR；上次 Schannel 上传失败没有成功送达。已重新核对 master 15 个 required contexts，未绕过保护。
- 2026-09-06 独立安全复核的直接 TLS 缺口在本轮修复：preflight 限定唯一 CA/唯一 trusted entry、拒绝 private-key entry；以镜像 mysql 用户确认 server key 可读不可写，手册规定 Linux 精确 UID 与 0400；smoke 仅挂载服务端三文件，不提供 CA 私钥，并在容器私有目录复制设权；hostname 负向先以同地址 VERIFY_CA 成功证明可达，再断言 CertificateException 原因。
- 实际 Compose 函数动态用例 valid、额外 CA、普通 cert bag、不可读私钥均符合预期；真实 MySQL TLS smoke `1/1`、四类负向、cipher 与 hostname 控制通过。临时 Vitest 串行/延长超时配置已撤销，未以降低门禁换取通过。最终全量同 SHA CI、SPDX、PR、合并与清理均未完成。
- 第二次独立只读复核确认上述直接 TLS 缺口已关闭。preflight 的只读检查不等于宿主 POSIX 保密权限检查，Linux 0400/所有权仍须按手册核验；不夸大自动门禁覆盖。
- 已按项目标签核对并移除本任务临时 G4 的 7 个容器、5 个测试卷和 1 个网络，停止临时 Vite；未清理镜像或其他 dev 容器。临时测试卷已删除，不能恢复原卷，只能重新生成。工作树内本任务证书与临时 override 的删除被工具策略拒绝，文件保留且添加本地 Git exclude 防止误入库，后续清理仍由本 Issue 承接，不声称已全部清理。
- Git 交付授权来自用户指令“完成主线计划书100#，并推送”；不包含 Tag、Release、版本发布或生产操作。

## 4. 恢复与剩余风险

- 配置回滚必须原子回退 Compose、Hikari、validator、driver 与合同测试；禁止以 `REQUIRED`、`VERIFY_CA` 或系统 truststore 回退换取启动成功。
- 部署方仍负责生成、轮换和保护真实 CA/server key/truststore；本仓库只提供失败关闭合同，不提供第二套密钥平台。
- 本地与 CI 只能证明代码和标准部署合同；未来如新增非本地环境，需另获授权并独立验收。

## 5. 零悬空

- 三个审计 ID 全部由 `ISSUE-100-001` 唯一承接，无重复载体。
- 当前实施新增正式后续项 0、关闭 0、净变化 0；所有直接缺口仍由 `ISSUE-100-001` 承接，G1/G5 尚未完成，因此 Issue 暂不关闭。
- 计划全周期已新增 1；完成时应关闭 1，净变化回到 0。无价值不明或无验收标准的建议不进入 Backlog。
