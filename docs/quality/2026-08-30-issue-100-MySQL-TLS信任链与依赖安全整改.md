# 第100条主线：MySQL TLS 信任链与依赖安全整改验收报告

> 日期：2026-08-30
> 唯一载体：`ISSUE-100-001`
> 编制基线：`master@5066a5c90bb9048289307a5d60e25c051e399577`
> 当前裁决：`G0-G3 PASSED / G4 ENVIRONMENT_SPLIT / G5 GIT_IN_PROGRESS`

## 1. 范围与边界

本轮关闭 `AUD-20260830-001/002/003`：补齐 MySQL 显式 CA、服务证书、Java PKCS12 truststore 与 Connector/J `VERIFY_IDENTITY`；将 Connector/J 从 9.7.0 窄覆盖到 8.4.0；修正 MinIO application credential 注释。无 migration、业务 API、权限、租户、金额或状态机变化。

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

### G1 兼容性与安全契约 — PASS

- 独立只读复核确认 Connector/J 8.4.0 为 GA，支持 MySQL 8.0+ 与 Java 21；Maven 可解析。
- Oracle CPU 范围核对截至 2026-08-30 未命中选定 8.4.0。结论仅为“未命中已核对 CPU 受影响范围”，不宣称最新版本或零漏洞。
- 26.7 文档对 MySQL 8.0/8.4 支持口径冲突，未采用；没有静默升级 MySQL。

### G2 数据、迁移与真实 MySQL — PASS

- 无 migration；所有数据库验证使用固定 MySQL 8.0 digest、隔离网络和临时卷，未复用或重置现有 `cgc_pms` 数据。
- fresh baseline/Flyway `1/1`，付款并发 `10/10`，材料删除并发 `2/2`，通讯并发 `2/2`，投标项目范围 `2/2`，RBAC tenant association `3/3`，code generation 聚焦复验 `5/5`。
- Windows 固定 MySQL 8.0 的 V180→V307 upgrade `1/1`。
- code generation 首轮因测试只允许 loopback、容器使用 `mysql` hostname 而未执行逻辑，分类 `tool_invocation`；改为共享网络命名空间并使用 `127.0.0.1` 后通过。

### G3 服务端、制品与供应链 — PASS

- `ProductionEnvironmentValidatorTest`：`22/22`。
- TLS smoke：`1/1` 正向、4 个负向全部按预期失败；cipher 非空。
- 完整 Maven 测试：Windows 执行 2959 项，唯一 3 个 Office loopback 用例受 Windows WEPoll 限制；同代码 Linux 聚焦 `3/3` 通过。计划内共 2962 项，产品失败 0。Windows `-DskipTests verify`、打包和 JaCoCo 门通过。
- dependency tree 仅 `com.mysql:mysql-connector-j:8.4.0:runtime`；executable JAR 仅 `BOOT-INF/lib/mysql-connector-j-8.4.0.jar`。
- JAR SHA-256 `82ab9daccb659c01c102effa0eb24763565d6cc2e270c5036d2e3d3f067daa3e`；162 个 backend library、166 个 Trivy Java package，实际 vulnerability 0，Critical/High 0。此结果只表示本次本地扫描未发现，不替代 Oracle CPU 范围核对。
- 本地 dependency tree 与 executable JAR 已闭合；SPDX 由既有 `anchore/sbom-action` 在同 SHA CI 生成，当前尚未取得远端工件，不将本地缺失误写成已验证。
- runtime deployment contract、workflow contract、Compose config 均通过；最终提交前需重跑绑定最终 diff。

失败分类：首次 Maven verify 缺 `TEST_JWT_SECRET`、Office Windows loopback、两次直接 surefire 未展开 `@{argLine}` 分别归 `tool_invocation`、`environment_prerequisite`、`tool_invocation`；修正合法前置或 Linux 聚焦复验后均闭合，不掩盖产品失败。

### G4 本地运行与浏览器 — ENVIRONMENT SPLIT

- 隔离 production-like Compose 的 preflight、MySQL、Redis、MinIO、ClamAV 已健康；backend 使用本轮 JAR、Java `trustedCertEntry` truststore完成 V1→V307 migration，在数据库名校准后以 79.673 秒记录 `Started CgcPmsApplication`。创建临时 `cgc-pms` bucket 后 `/api/actuator/health` 返回 `UP/200`；前端入口及其代理健康均返回 200。该链证明真实 `VERIFY_IDENTITY`、Flyway 与健康检查成立。
- 运行态发现并修复普通 OpenSSL cert bag 不是 Java trust anchor 的门禁缺口：新版 preflight 要求 `Trusted key usage`；Java `keytool` truststore 正向通过。
- 应用内浏览器确认旧本地镜像 `cgc-pms-frontend-v2:m0` 只是历史静态健康页。当前 SPA 已在宿主执行 `vue-tsc` 与 production Vite build 成功；准备挂入 production nginx 时 Docker Desktop engine 全面返回 API 500，随后现有 dev/G4 容器端口均超时。宿主 JAR fallback 完成 H2 V307 后又被已知 Windows WEPoll `Unable to establish loopback connection` 阻断 Tomcat。两项均分类 `environment_prerequisite`，未判产品回归，也未越权重启影响其他容器的全局 Docker Desktop。
- 本地已取得 TLS backend health；完整登录、列表 DOM 与 console 仍须由同 SHA CI/e2e 补证。取得前不将 G4 写成无条件通过。

### G5 收口与 Git — IN PROGRESS

- 最终 Codemap、问题源与零悬空已进入回写；待最终本地合同门通过后执行同 source SHA Push CI、PR checks、受保护合并、post-merge 验真和源分支清理。
- Git 交付授权来自用户指令“完成主线计划书100#，并推送”；不包含 Tag、Release、版本发布或生产操作。

## 4. 恢复与剩余风险

- 配置回滚必须原子回退 Compose、Hikari、validator、driver 与合同测试；禁止以 `REQUIRED`、`VERIFY_CA` 或系统 truststore 回退换取启动成功。
- 部署方仍负责生成、轮换和保护真实 CA/server key/truststore；本仓库只提供失败关闭合同，不提供第二套密钥平台。
- 本地与 CI 只能证明代码和标准部署合同；未来如新增非本地环境，需另获授权并独立验收。

## 5. 零悬空

- 三个审计 ID 全部由 `ISSUE-100-001` 唯一承接，无重复载体。
- 当前实施新增正式后续项 0、关闭 0、净变化 0；G4 补证/G5 尚未完成，因此 Issue 暂不关闭。
- 计划全周期已新增 1；完成时应关闭 1，净变化回到 0。无价值不明或无验收标准的建议不进入 Backlog。
