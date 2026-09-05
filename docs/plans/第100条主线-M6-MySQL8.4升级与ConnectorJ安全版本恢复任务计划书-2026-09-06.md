# 第100条主线 M6：MySQL 8.4 升级与 Connector/J 安全版本恢复

**Goal:** 为主线100建立受厂商支持的 MySQL 8.4 与 Connector/J 26.7 组合，关闭原 G1 安全支持证据阻塞，并规定重新取得 G2～G5、同 SHA CI 和受保护 Git 交付的验收路径。 **Architecture:** 复用现有 Spring Boot 3.5.16、Java 21、Hikari、Flyway、TLS 信任链与 CI；采用隔离新卷、可验证的逻辑备份/恢复和显式版本锁定。保留现有 MySQL 8.0 数据卷，不让 8.4 二进制打开旧活动卷；不建设复制集、第二套数据库平台或生产环境，不升级 Spring Boot。

> 日期：2026-09-06
> 父计划：[第100条主线](第100条主线-MySQL TLS信任链与依赖安全整改任务计划书.md)
> 唯一问题载体：`ISSUE-100-001`；本文件是独立升级阶段计划，不另造重复 Issue
> 状态：`AUTHORIZED / G0_PASSED / G1_IMAGE_SCAN_PASSED / ENGINE_CANDIDATE_VERIFIED / G2_REGRESSION_PENDING`
> 授权：2026-09-06 用户明确“批准实施，你自己根据任务上下文判断，不再需要询问我”。本阶段代码/配置、隔离数据库升级与恢复验证及既有受保护 Git 交付均已授权；同范围阶段切换不再重复询问。现有 dev 库/活动卷、生产、Tag/Release 及其他任务仍不在范围内。
> 边界：仅本地与远端 CI；现有 dev 库、其他任务工作区、Tag、Release 和非本地环境不在操作范围。

## 1. 当前事实与版本决策

1. 隔离工作树 `D:\projects-test\cgc-pms-mainline-100`，分支 `codex/mainline-100`，HEAD `567df5785c2f90f486643307c87b7ad96d3669de`；有主线100自有未提交 TLS 修复、质量记录和 Codemap 更新。原 `D:\projects-test\cgc-pms` 的 master/Sentry 改动不动。
2. 本轮只读查询现有 `cgc-pms-mysql-dev`：实际版本 `8.0.46`，字符集 `utf8mb4`，排序规则 `utf8mb4_0900_ai_ci`，时区 `+08:00`；8 个账号均使用 `caching_sha2_password`。不回显账号密码或读取业务行。活动卷为 `deploy_mysql-dev-data`，必须保留；7 个非系统 schema 的存在说明该容器不是可随意重置的临时库。
3. 原 driver 8.4.0 只保留为已测试的临时候选，安全支持结论已撤回。不能用未列入 Oracle CPU 或 Trivy 0 finding 证明旧版本不受影响。
4. Connector/J 候选为 `26.7.0`：[正式发布说明](https://dev.mysql.com/doc/relnotes/connector-j/en/news-26-7-0.html)与[兼容性页](https://dev.mysql.com/doc/connector-j/en/connector-j-versions.html)均覆盖 MySQL 8.4+，Java 21 落在 JRE 8+ 要求内；因此改为 MySQL 8.4 后不再依赖官网主页的 8.0+ 冲突表述。实施前重新核对最新公告、Maven 可解析性及 transitive dependencies，不宣称零漏洞。
5. 镜像版本尚未冻结：`mysql:8.4.12` manifest 查询返回 `not found`，分类 `tool_config`，不重试同一不存在标签；`mysql:8.4` 当前 OCI 标注为 `8.4.11`，index digest 为 `sha256:b3b90af2a6552ae30c266fdb7d5dd55f3afb72404bb78d37fe8a23eb857fd3fb`，linux/amd64 manifest 为 `sha256:1d6b6a8fcee8ff758ff151d017f5203cd06792a0e698f0a593c9dfcb14609cf0`。这些仅为候选元数据，尚未拉取、运行或扫描，不能当作批准版本。
6. [8.4.12 发布说明](https://dev.mysql.com/doc/relnotes/mysql/8.4/en/news-8-4-12.html)标注为 Docker 镜像安全更新，但不等于 Docker Hub library/mysql 已同步该标签。实施前核对发行来源、可获取的不可变 digest、实际 server/base package 版本及[八月安全公告](https://www.oracle.com/security-alerts/cspuaug2026.html)的具体组件；禁止混淆 Connector/ODBC、NDB Cluster 和 Connector/J/MySQL Server，也禁止凭版本号猜测漏洞覆盖。必要时使用经核实的 Oracle 官方社区镜像；若改变发行镜像，重验 entrypoint、mysql UID、openssl/gosu 和 TLS preflight 能力。

## 2. 范围与非目标

### 2.1 升级实施获授权后的范围

- 固定安全受支持的 MySQL 8.4 镜像与 Connector/J 26.7.0，协调标准 Compose、CI、TLS smoke、备份恢复客户端和静态契约。
- 新建任务专属 8.0 源测试卷与 8.4 目标测试卷；只以本轮合成数据演练逻辑导出/导入、迁移、身份认证、租户、金额、软删除、并发与恢复。
- 保持 `VERIFY_IDENTITY`、单一 CA truststore、禁止系统 truststore 回退和 server-key 只读/运行用户可读要求；不以启用旧认证插件修复测试。
- 更新必要运维手册、计划/Issue/质量报告与 Code map；本轮实际发现的同根安全问题必须本轮修复复验。
- 完成最终 source SHA 的 Push CI、SPDX/JAR/扫描、非 Draft PR 前验证、受保护合并、post-merge 与源分支清理。

### 2.2 明确排除

- 不直接迁移、删除、清空、复制或替换现有 `deploy_mysql-dev-data`，不运行现有数据清理脚本的 `-Execute`，不切换其他任务 backend/frontend。
- 不把标准 Compose 的 image 替换与复用同名旧卷组合成隐式原地升级；新标准版本使用新的显式卷名/升级入口并说明现有安装的迁移步骤。
- 不变更业务 API、权限、租户、金额口径或状态机；不改已应用 Flyway migration。未知兼容 DDL 不在本计划中预先批准：须重新打开 G1/G2，列出精确对象、数据影响、回滚与测试，确认落在用户明确的升级实施范围内后才新增版本化 migration；涉及业务语义或数据重写必须另获授权。
- 不迁移系统账号表或把 `mysql` 系统库 dump 原样导入 8.4；临时测试账号按最小权限重新创建，不复制用户 Secret。
- 不以“同版本 Flyway V180→V307 测试”冒充 MySQL Server 8.0→8.4 引擎升级验证。
- 不引入长期兼容双数据库、全局数据库重置、生产演练、Tag 或 Release。

## 3. 调用与文件影响

当前 Code map 的 `mysql-flyway` 节点从 `application-dev.yml`/数据源配置进入 Flyway 与业务持久层；`prod-config-gate` 由 `CgcPmsApplication.application.addInitializers` 调用，覆盖 `ProductionEnvironmentValidatorTest`。当前源码补充证明：`backend-test-mysql` 调用 TLS smoke、fresh baseline、租户/并发组和 baseline upgrade；备份/恢复脚本独立启动 MySQL 客户端容器。因此不能只更新服务镜像或仅跑 H2。

| 文件/边界 | 必须处理 | 证据 |
| --- | --- | --- |
| `backend/pom.xml` | driver 从临时 8.4.0 改为经确认的 26.7.0；检查 Boot/Flyway 管理版本 | dependency tree、effective POM、JAR、SPDX 单一版本 |
| `deploy/docker-compose.prod.yml` | preflight/mysql 固定镜像，明确升级后新卷，保留 TLS/最小权限 | config、真实 preflight、image UID/工具、健康 |
| `deploy/docker-compose.dev.yml`、`deploy/docker-compose.yml` | 新安装的版本/卷合同一致；现有卷不自动挂入 8.4 | config 与旧卷不引用断言；本轮不运行现有 dev up |
| `.github/workflows/ci.yml` | MySQL required job 切换并增加跨引擎演练，保留原测试 | workflow contract、精确 SHA jobs、测试无意外 skip |
| `scripts/ci/run-mysql-tls-smoke.ps1`、对应 JUnit | 改用最终固定镜像；保留四类失败、同地址可达对照、实际 preflight fixtures | Windows/Linux、cipher、CertificateException |
| `scripts/mysql-backup.ps1`、`scripts/mysql-restore.ps1` | 客户端版本与目标兼容，显式参数/不可变 digest；恢复脚本现有 Secret 进程参数问题在使用前修复 | 原子备份、中文/二进制字节、失败关闭、恢复回读、进程参数不含密码 |
| `scripts/ci/test-windows-backup-contract.ps1`、runtime/workflow contract | 更新版本断言而不削弱安全断言 | 静态与相关动态合同 |
| `scripts/database/clear-business-data.ps1` | 仅核对其 MySQL 客户端版本并按需参数化；不执行数据清理 | 只读/静态兼容检查，不扩大数据操作 |
| `scripts/ci/` 新增跨引擎演练入口（名称实施时冻结） | 真实 8.0→8.4 逻辑恢复与失败回退；只操作唯一前缀资源 | 版本、对象/数据守恒、恢复证据、可靠清理 |
| `docs/standards/10-部署运维手册.md`、本计划、父计划、Backlog、质量、Codemap | 固化升级/不原地降级/授权边界，写回实际结果 | 引用/JSON、地图 generator/self-test/verify |

## 4. 阶段与验收门

| 门 | 动作与完成证据 | 失败处置 |
| --- | --- | --- |
| M6-G0 | 实施授权明确；分支/dirty 归属；固定版本及镜像来源；本地端口、卷和数据库白名单；旧卷保护和恢复边界 | 未确认不改数据库/配置 |
| M6-G1 | 当前版本安全支持闭合；候选镜像实际版本、工具与 UID；字符集/时区/认证；MySQL Upgrade Checker 在隔离 8.0 样本上按最终目标执行且无未处置错误 | 镜像不可得、扫描发现或不支持项必须分类修复，不能仅用元数据通过 |
| M6-G2 | 8.4 fresh baseline；V180→当前 Flyway 迁移；记录升级前 8.0 源生成的逻辑备份 SHA、源版本、客户端版本，先将同一备份恢复至独立 8.0 恢复卷证明可回退，再导入 8.4；账号重建；表/索引/约束/例程、中文/二进制、各表行数和关键金额/状态/租户字段守恒；源卷和备份保留至 G5 | 不用既有 dev 数据做试验；不修改已应用 migration；差异逐项说明并复验 |
| M6-G3 | driver 26.7.0 下 TLS/validator、完整 Maven H2/JaCoCo、真实 MySQL 租户/并发组、升级/恢复；dependency tree/JAR/SPDX/制品扫描全部绑定最终 SHA | 任何弱化或双版本、供应链缺证、错误 Secret 日志均不通过 |
| M6-G4 | 8.4+最终 JAR 的隔离本地栈 health UP；证明实际数据源/版本；真实登录、至少一个有合成样本的列表读取；新 DOM/旧占位标识、控制台及 API 证据；普通 dev/test profile 不误伤 | 旧 8.0/8.4.0 driver 的浏览器结果仅保留历史，不能替代新组合 |
| M6-G5 | 独立只读高风险复核；父计划/Issue/质量/地图回写；最终完整 Push CI、pre-PR verifier、PR checks、受保护合并、post-merge 与安全清理 | 任一缺失保持 ISSUE-100-001 开放，不直接推 master、不绕保护 |

必要既有测试入口：`ProductionEnvironmentValidatorTest`、`MySqlTlsSmokeTest`、`BaselineMySqlSmokeTest`、`FlywayMySqlSmokeTest`、`BaselineMySqlUpgradeTest`、`BidProjectScopeMySqlTest`、`PaymentMySqlConcurrencyTest`、`MdMaterialDeleteMySqlConcurrencyTest`、`CommunicationMySqlConcurrencyTest`、`CodeGenerationMySqlIntegrationTest`、`RbacTenantAssociationMySqlIsolationTest`。必须使用 CI 中对应环境开关及独立 test schema；统计执行/失败/错误/跳过，不能把未启用的用例计为通过。

## 5. 恢复矩阵与金丝雀

采用[厂商列出的 8.0→8.4 逻辑导出/导入路径](https://dev.mysql.com/doc/refman/8.4/en/upgrade-paths.html)，在完整备份与[Upgrade Checker](https://dev.mysql.com/doc/mysql-shell/26.7/en/mysql-shell-utilities-upgrade.html)证明后执行。8.4 默认禁用[mysql_native_password](https://dev.mysql.com/doc/refman/8.4/en/native-pluggable-authentication.html)，本轮复用 caching_sha2_password，不开启旧插件。厂商允许的[跨系列回退](https://dev.mysql.com/doc/refman/8.4/en/downgrading.html)有限制，本计划不采用在 8.4 数据目录上运行 8.0 的原地降级。

| 时点 | 保留与恢复动作 | 回读要求 |
| --- | --- | --- |
| 导出/预检失败 | 8.0 源测试卷保留，目标不切换 | 源版本与合成样本不变，失败退出码可靠 |
| 8.4 导入/迁移失败 | 停止任务自有 8.4 目标，保留失败证据；仅使用记录了 SHA/源版本/客户端版本的同一升级前 8.0 逻辑备份建立独立 8.0 恢复卷，禁止把 8.4 导出当作回退件 | 各表行数、金额/状态/租户及关键字节一致 |
| backend/TLS/浏览器失败 | 不切换任何既有 dev 服务；保留同批应用/配置/driver 回滚点 | 不降低 TLS、不混用新 driver 与旧版本证据 |
| 受保护 Git 未完成 | 保留任务分支、所有提交及真实证据，修复门禁后再走原路径 | source/target SHA、PR 状态和 post-merge 可证明 |
| 验收完成 | 只清理经标签、路径、归属确认的任务临时卷/容器/文件；保留所需证据 | 原 dev 容器/卷未改变；删除失败如实登记，不绕工具策略 |

金丝雀为一个隔离合成租户：建立最小项目/合同/付款及 Unicode/二进制样本，记录升级前快照，8.4 恢复后验证权限过滤、金额、外键/唯一约束、并发与浏览器读取；通过后才扩至完整既有 MySQL 测试组。它不操作真实业务数据，也不构成现有 dev 数据库的正式切换授权。

## 6. 计划裁决与零悬空

- 本计划已获明确实施授权；下列记录覆盖已经执行的镜像和跨引擎演练。完整 G2～G5 尚未取得，不宣称主线100完成。
- 即使本阶段 G5 完成，其结论也只覆盖仓库 schema、合成数据、隔离运行与 CI；`deploy_mysql-dev-data` 未迁移、未切换，不能报告现有 dev 库已升级。
- 原“禁止 MySQL 8.4 升级”的父计划非目标仅在本阶段获得明确实施授权后按上述隔离范围调整；其他非目标保持不变。
- MySQL 镜像补丁来源/可得性、恢复脚本参数脱敏及版本一致性是本升级的直接前置，由 `ISSUE-100-001` 本阶段承接，不另造后续事项。
- 新增正式后续项 0、关闭 0、净变化 0；主线100唯一 Issue 继续开放。原目标仍包括完整 G0～G5 与受保护 Git 交付，不以计划文档代替实施完成。

## 7. M6 实施记录（2026-09-06，优先于第1节候选历史）

- **G0/G1**：沿用任务独立工作树与 `ISSUE-100-001`。用户已明确批准自主实施；不再等待重复授权。Connector/J `26.7.0` 已解析并通过配置门禁22项；正式兼容性页覆盖 MySQL8.4/Java21。旧 driver8.4.0 安全结论不恢复。
- **镜像选择**：弃用 library/mysql8.4.11 候选（38条高危/严重）。Oracle官方8.4 tag固定 digest `7dcc4add9183664de3a214daf85a50c3ba6cccfd7534f700b6561bf5b41885be`，实际server8.4.12、Shell26.7.1、UID:GID27:27。独立复核5条高危后，运行Dockerfile移除不必要Shell及其Python，SQLite以[ELSA-2026-58936](https://linux.oracle.com/errata/ELSA-2026-58936.html)固定RPM和官方SHA256升级至 `3.34.1-11.el9_8`。最初直接移除SQLite被RPM依赖拒绝，分类tool_config，改为保留依赖并安装修复包；没有使用nodeps。
- **可重建而非远端发布**：`deploy/mysql/Dockerfile` 固定Oracle基镜像和RPM校验和；`Preflight.Dockerfile` 固定Alpine基镜像、OpenSSL3.5.8-r0和su-exec0.3-r0。Compose使用build，CI按Dockerfile内容哈希构建并以实际image ID运行，不发布registry、Tag或Release。首次候选扫描高危/严重均0，但构建日志的config digest不能直接当作运行image ID；原记录在此更正。新流程关闭provenance生成，读取Docker实际ID，扫描报告Metadata.ImageID必须与后续TLS/engine消费ID完全一致，否则失败关闭；该加强版证据复验中。标准卷改为 `mysql84-data`/`mysql84-dev-data`，不引用原活动卷。
- **跨引擎恢复已验证**：新入口 `run-mysql-engine-upgrade.ps1` 在新建、标记所有权、无外部监听的internal网络中，以8.0.46 native client载入不可变B215和全部后续SQL，再建立项目/合同/付款、跨租户、软删除、中文和二进制合成样本；不通过不受支持的26.7→8.0连接准备源库。Upgrade Checker以最终8.4.12为目标，0 error。24 warnings全部为已复核的默认系统变量变化；2 notices为临时root账号SET_USER_ID移除，账号不在dump中。复制集参数在本地单实例不适用；InnoDB默认变化由完整MySQL回归再验证；不忽略新增未知提示。
- **同一备份与回退候选证据**：源8.0 dump SHA256 `50DD1EF1B9C854FCBAC8177FE52E636F29BF7326FA4D8E2E02B0653388AD515B`，client8.0.46。先恢复独立8.0.46卷，再导入8.4.12卷，230张表的各表行数、全部行字节及当时采集的对象字段一致；行字节SHA256 `7F02677F5EB9268CE7EB4E32BD3CE91AA0F1DE62ED516BA5E6FC52F2F62DDC5C`，例程/二进制探针通过。独立复核指出原快照未包含生成列表达式、CHECK文本/ENFORCED和外键动作，已补全，完整对象语义须重跑后才可通过。源卷与备份按计划保留至G5。首次合成合同遗漏最新非空party字段，分类quality_or_security测试fixture错误；只补合成合作方与外键后在全新卷复验通过，未修改migration。
- **失败与恢复**：新8.4.12 TLS真实preflight四组通过；JDBC复验期间Docker Desktop代理出现 `192.168.65.7:2376: no route to host`，只读docker ps同样失败，分类environment_prerequisite；随后只读version恢复，未重启共享Docker。首次完整Maven未注入CI测试JWT变量，2743测试/1728错误/30跳过、BUILD FAILURE，根因WeakKeyException，分类tool_config；补与CI相同环境后重跑。前端原并发649项中645通过、4项约5秒超时；降低worker数的同用例57项全部通过，完整串行复验中，未修改业务或放宽测试断言。
- **剩余验证**：精确镜像安全、完整对象语义、异常清理保护、新组合G2 fresh/V180迁移、G3完整Maven/MySQL/JAR/SPDX、安全CI、G4有样本浏览器、G5受保护Git待最终证据。旧组合浏览器证据仅为历史。
- **边界**：现有 `deploy_mysql-dev-data` 未迁移、未切换。早先临时Secret目录删除被策略拒绝，仍忽略保留，不绕策略、不提交私钥；临时批次资源清理须在G5逐一核对所有权。
- **后续复验更新**：实际运行ID `ee7bf662aa692abb5c6384b39796de4796001990b83b47ab020abafde96f2895` 与预检ID `99f3bf5ead9d68b9a33886d144bcfd90b3f073d25f640c2f0d5be1ebb7229d84` 已与扫描Metadata.ImageID精确一致，高危/严重均0。前端649/649、lint、type-check、build通过；备份/恢复28项、实际finally清理AST18项在PS7/5.1通过。完整Maven2962项留下3项既有时间夹具/格式断言缺陷与3项Windows loopback错误；前3项只改测试并复验，后3项仍需等价平台证据。Docker第二次不可达，停止旧任务容器请求也失败；共享WSL4GB上限未改，任务新演练已限每库512MB并顺序停止保留卷。5条Upgrade Checker工具镜像High的不可达依据与失效边界在质量报告单列，不能宣称全部CI镜像零漏洞。当前Git提交 `bef6aaf9` 为阶段快照，正式交付未完成。
