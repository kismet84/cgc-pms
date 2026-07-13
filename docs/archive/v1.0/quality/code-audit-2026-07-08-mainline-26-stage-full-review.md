# 第26条主线 M2/M3 阶段性全量代码审计报告

审计日期：2026-07-08  
审计范围：`backend`、`frontend-admin`、`.github/workflows`、`deploy`、数据库迁移、项目规范与计划书中 M2/M3/M4 除浏览器自动化之外的审查项。  
审计边界：本次仅执行读取、CodeGraph 理解、只读/构建测试验证与报告归档；未修改业务代码、配置、测试源码、运行环境或 Git 状态；未清理文件；未提交 Git。  
参考输入：`AGENTS.override.md`、`AGENTS.md`、`docs/plans/第26条主线-阶段性全量代码审查任务计划书.md`、`docs/04-后端开发规范.md`、`docs/05-前端开发规范.md`、`docs/07-数据库与迁移规范.md`、`docs/09-测试规范.md`、`docs/11-安全规范.md`、`.github/workflows/ci.yml`、`deploy/docker-compose.prod.yml`、`deploy/.env.example`。  
需要确认：`docs/prompt/code-audit-agent.md` 不存在，本报告未将其缺失作为阻塞项。

## 总体结论

结论：不通过。  
上线裁决：阻塞。  
综合评分：72/100。

本轮代码库具备较完整的基础安全与交付框架：生产配置默认关闭开发登录，Cookie 默认 `HttpOnly`、`SameSite=Strict`，生产 Cookie 开启 `Secure`；前端请求封装已有 CSRF、刷新队列和跳转净化；CI 覆盖后端测试、前端类型检查、构建、前端测试、依赖审计、SBOM/attestation、E2E 和 SQL 安全扫描；后端全量测试、前端构建、前端测试均已通过。

但第26条主线阶段性收口仍存在阻塞风险：成本摘要接口缺少项目级访问控制，版本库仍跟踪测试/浏览器产物并包含 `admin-state.json`，Redis/token blacklist 降级在本地全量后端测试中持续出现且请求链路允许继续放行。以上问题分别影响权限隔离、发布包洁净度与会话失效语义，不能作为正式上线收口。

## 评分

| 维度 | 分数 | 结论 |
| --- | ---: | --- |
| 代码规范与结构可维护性 | 74 | 非阻塞，但多个前端页面超过 800 行，后续维护风险偏高 |
| 安全权限与租户/项目隔离 | 65 | 阻塞，发现项目级成本摘要访问控制缺口 |
| 数据库与 Flyway | 70 | 非阻塞但风险较高，迁移不可变门禁不可靠且 H2/MySQL 不对称 |
| 构建与 CI | 78 | 主流程通过，但覆盖率和迁移门禁存在假绿风险 |
| 发布包洁净度 | 60 | 阻塞，版本库跟踪测试产物和浏览器状态文件 |
| 日志可观测与回滚 | 75 | 基础可观测存在，Redis/token blacklist 降级需要更强门禁与告警 |
| 测试有效性 | 68 | 测试命令通过，但前端覆盖率低且关键页面缺覆盖 |

## 风险统计

| 级别 | 数量 | 阻塞数量 | 说明 |
| --- | ---: | ---: | --- |
| P0 | 0 | 0 | 未发现立即导致数据破坏、凭据泄露或系统不可用的确定问题 |
| P1 | 3 | 3 | 权限隔离、发布洁净度、token blacklist 降级语义 |
| P2 | 8 | 0 | 数据库门禁、测试有效性、依赖安全、最小暴露和结构风险 |
| P3 | 2 | 0 | 文档/兼容性清理项 |

## 关键问题

### P1-1 成本摘要接口缺少项目级访问控制

类型：安全权限 / 项目级隔离  
严重级别：P1  
位置：`backend/src/main/java/com/cgcpms/cost/controller/CostSummaryController.java`、`backend/src/main/java/com/cgcpms/cost/service/CostSummaryService.java`、`backend/src/main/java/com/cgcpms/security/ProjectAccessChecker.java`  
证据：`CostSummaryController` 仅通过 `@PreAuthorize("hasAuthority('cost:summary:view') or hasRole('ADMIN')")` 控制入口；`CostSummaryService.getProjectSummary` 和 `refreshSummary` 只调用 `assembler.requireProjectInTenant(tenantId, projectId)` 确认项目属于当前租户，未调用 `ProjectAccessChecker.checkAccess`。项目访问控制器已实现 ADMIN/SUPER_ADMIN、ALL、SELF、DEPT、CUSTOM 数据范围判断，但该成本摘要链路未接入。  
影响：具备 `cost:summary:view` 的同租户用户可能读取或刷新其无项目授权的项目成本摘要，影响金额口径、项目隔离和管理驾驶舱数据保密性。  
修复建议：在成本摘要读取和刷新入口统一接入 `ProjectAccessChecker.checkAccess(projectId)` 或同等项目级授权服务；补充 ADMIN、项目负责人、无权限普通用户、跨项目同租户用户的单元/MockMvc 测试；将无权限访问明确返回 403。  
优先级：立即修复。  
阻塞/非阻塞：阻塞。

### P1-2 版本库跟踪测试/浏览器产物，发布包洁净度不达标

类型：发布包洁净度 / 敏感测试状态  
严重级别：P1  
位置：`output/playwright/**`、`frontend-admin/coverage-result.json`、`.gitignore`  
证据：`git ls-files backend/target frontend-admin/dist frontend-admin/coverage test-results output .agent-runtime frontend-admin/playwright-report frontend-admin/coverage-result.json` 显示 36 个 `output/playwright/**` 文件和 1 个 `frontend-admin/coverage-result.json` 已被版本管理跟踪，其中包含 `output/playwright/.auth/admin-state.json`、截图和浏览器验证产物。`.gitignore` 已覆盖大量构建和运行态目录，但未覆盖顶层 `output/`。  
影响：测试状态、截图和覆盖率中间产物进入交付面，增加误发布、凭据/会话状态泄露、审计噪声和代码评审成本。  
修复建议：由执行子任务先确认这些文件是否仍有产品/文档价值；若无价值，使用非破坏性流程从版本库移除跟踪并补充 `.gitignore`；若其中有验收证据需要保留，应迁移为脱敏后的正式报告附件或文档，不应保留浏览器状态文件。  
优先级：立即修复。  
阻塞/非阻塞：阻塞。

### P1-3 Token blacklist/Redis 降级会导致登出和吊销语义不稳

类型：安全会话 / 可观测与回滚  
严重级别：P1  
位置：`backend/src/main/java/com/cgcpms/security/JwtAuthenticationFilter.java`、`docs/10-部署运维手册.md`、`deploy/docker-compose.prod.yml`  
证据：`JwtAuthenticationFilter` 在 `TokenBlacklistService` 不可用时记录 `BLACKLIST_UNAVAILABLE` 后继续执行认证链路；后端全量测试通过过程中出现大量 `BLACKLIST_UNAVAILABLE` 与 `FallbackRateLimitCounterStore` 日志。部署编排中 Redis 是必需健康依赖，但代码层仍允许 blacklist 服务不可用时放行。运维文档已说明 Redis 异常会导致登出保护“完全失效”。  
影响：生产若出现 Redis/blacklist 组件异常，已登出或已吊销 token 可能继续可用，安全语义与审计预期不一致；本地测试对该降级状态未失败，容易形成假绿。  
修复建议：生产 profile 下将 blacklist 不可用设为 fail-closed 或至少对敏感接口 fail-closed；补充 Redis/blacklist 健康检查与告警；增加测试断言，区分测试环境可降级与生产环境不可降级。  
优先级：立即修复。  
阻塞/非阻塞：阻塞。

### P2-1 Flyway 不可变检查不是有效门禁且 Windows 脚本存在假绿

类型：数据库 / Flyway / CI 门禁  
严重级别：P2  
位置：`scripts/check-flyway-immutability.bat`、`scripts/check-flyway-immutability.sh`、`.github/workflows/ci.yml`、`docs/07-数据库与迁移规范.md`  
证据：两个脚本注释均说明仅 warning、退出码为 0；脚本只检查 staged 的 MySQL migration 修改，不覆盖 H2 migration，不覆盖已提交历史变更。实际执行 `scripts\check-flyway-immutability.bat` 和 `cmd /c scripts\check-flyway-immutability.bat` 出现多条命令识别错误但退出码仍为 0。CI 当前运行 SQL 安全扫描和 Flyway smoke，但未将不可变检查作为失败门禁。  
影响：历史迁移被修改、H2/MySQL 迁移不同步或脚本异常时，CI 仍可能绿色通过，削弱生产数据库升级可预测性。  
修复建议：重写不可变检查为跨平台脚本或 CI 原生命令；覆盖 MySQL 与 H2 migration；异常时非 0 退出；将检查纳入 CI；保留一次性白名单处理历史已确认变更。  
优先级：高。  
阻塞/非阻塞：非阻塞，但上线前应作为质量加固项完成。

### P2-2 H2 与 MySQL 迁移数量不一致，测试数据库与生产数据库存在漂移

类型：数据库 / 测试有效性  
严重级别：P2  
位置：`backend/src/main/resources/db/migration`、`backend/src/main/resources/db/migration-h2`、`docs/07-数据库与迁移规范.md`  
证据：当前 MySQL SQL migration 数量为 130，H2 SQL migration 数量为 125。数据库规范文档已记录 H2/MySQL 迁移不对称是遗留风险。  
影响：后端 H2 测试通过不能完全证明 MySQL 行为一致，尤其是字段类型、索引、约束、默认值和 SQL 方言相关逻辑。  
修复建议：建立 migration parity 清单；对必须分叉的 migration 写明原因；对金额、审批、删除标记、权限相关 migration 优先补齐 H2 或改用 Testcontainers MySQL 覆盖关键链路。  
优先级：高。  
阻塞/非阻塞：非阻塞。

### P2-3 前端依赖存在 ECharts XSS 中危漏洞

类型：依赖安全 / 前端 XSS  
严重级别：P2  
位置：`frontend-admin/pnpm-lock.yaml`、`frontend-admin/package.json`  
证据：`pnpm audit --json --registry=https://registry.npmjs.org` 返回 `echarts@5.6.0` 中危 XSS 漏洞，GHSA `GHSA-fgmj-fm8m-jvvx`，CWE-79，受影响版本 `<6.1.0`，修复版本 `>=6.1.0`。`pnpm audit --audit-level high` 通过，因为该漏洞为 moderate。  
影响：项目存在图表场景，若图表配置、tooltip、label、dataset 或富文本内容混入不可信输入，可能触发 XSS 风险。  
修复建议：评估升级 `echarts` / `vue-echarts` 到兼容修复版本；升级前检查驾驶舱、图表渲染和主题配置；在升级完成前避免把未经净化的用户输入传入图表富文本/HTML 渲染。  
优先级：高。  
阻塞/非阻塞：非阻塞。

### P2-4 文件接口响应暴露内部存储路径与 bucket 名称

类型：信息暴露 / 接口最小化  
严重级别：P2  
位置：`backend/src/main/java/com/cgcpms/file/vo/SysFileVO.java`、`frontend-admin/src/types/file.ts`  
证据：`SysFileVO` 包含 `storagePath` 和 `bucketName` 字段，前端 `FileVO` 类型也保留对应字段。文件下载链路本身已做租户和业务对象读权限校验，但列表/详情响应仍暴露存储实现细节。  
影响：增加对象存储结构、bucket 命名和内部路径暴露面，不符合接口最小化原则；一旦日志、截图或第三方集成泄露响应内容，会扩大攻击面。  
修复建议：默认响应只返回 `id/name/size/contentType/url` 等业务必要字段；将 `storagePath/bucketName` 限制在后端内部模型或管理员诊断接口；补充序列化测试，确保普通文件响应不暴露内部存储字段。  
优先级：中。  
阻塞/非阻塞：非阻塞。

### P2-5 附件业务对象授权粒度不一致

类型：对象级权限 / 文件安全  
严重级别：P2  
位置：`backend/src/main/java/com/cgcpms/file/security/BusinessObjectAuthorizer.java`、`backend/src/main/java/com/cgcpms/file/service/FileService.java`  
证据：`FileService.upload`、下载 URL 获取和删除均调用业务对象授权器；授权器对未知业务类型 fail-close，对 `PROJECT` 类型使用 `ProjectAccessChecker`，但对 `CONTRACT`、`INVOICE`、`RECEIPT`、`PAYMENT`、`PURCHASE_*`、`SETTLEMENT` 等类型主要校验租户归属，未统一项目成员或业务对象权限。  
影响：同租户内拥有文件上传/读取权限的用户，可能在部分业务类型上缺少更细粒度的对象级约束。  
修复建议：为关键业务对象建立统一的对象权限策略，优先覆盖合同、发票、付款、结算、采购和库存；若短期无法完整接入项目权限，应在审计日志中记录业务类型、业务 ID、用户、授权依据，并补充负向测试。  
优先级：中。  
阻塞/非阻塞：非阻塞。

### P2-6 前端关键页面过长，结构维护风险较高

类型：代码结构 / 可维护性  
严重级别：P2  
位置：`frontend-admin/src/pages/**`  
证据：多页超过 800 行，例如 `cost-target/edit.vue` 998 行、`settlement/index.vue` 984 行、`purchase/order.vue` 972 行、`login/index.vue` 969 行、`dashboard/index.vue` 921 行、`subcontract/measure.vue` 891 行、`cost-target/index.vue` 890 行、`alert/index.vue` 888 行。  
影响：页面逻辑、表单状态、接口调用和展示结构耦合，增加回归成本；后续 M2/M3 修复安全或权限问题时更容易引入前端行为回归。  
修复建议：按已有组合式函数和组件约定逐页拆分，不做一次性大重构；优先拆出表格列、查询条件、表单 schema、API 状态和权限显示逻辑；每拆一页补最小单元测试。  
优先级：中。  
阻塞/非阻塞：非阻塞。

### P2-7 前端测试覆盖率低，CI 通过不能代表关键页面行为可靠

类型：测试有效性 / CI 假绿  
严重级别：P2  
位置：`frontend-admin` 测试覆盖率输出  
证据：`pnpm test:coverage` 通过，74 个测试文件、392 个测试通过；覆盖率为 Statements 16.55%、Branches 16.89%、Functions 13.41%、Lines 16.85%。多个核心页面或模块覆盖率为 0%。  
影响：构建和测试通过无法充分证明合同、采购、库存、结算、审批、驾驶舱等核心页面的交互可靠性。  
修复建议：先不追求全局高覆盖率，给高风险页面建立最小覆盖阈值和关键路径用例；把权限显示、金额展示、表单提交、错误处理、刷新重试作为优先测试点。  
优先级：中。  
阻塞/非阻塞：非阻塞。

### P2-8 生产对象存储应用凭据沿用 MinIO root 用户

类型：部署安全 / 最小权限  
严重级别：P2  
位置：`deploy/docker-compose.prod.yml`、`deploy/.env.example`、`docs/10-部署运维手册.md`  
证据：生产 compose 中 backend 的 `MINIO_ACCESS_KEY` 和 `MINIO_SECRET_KEY` 取自 `MINIO_ROOT_USER` / `MINIO_ROOT_PASSWORD`；运维文档建议生产为应用创建独立 MinIO service account。  
影响：应用运行凭据权限过大，一旦后端或配置泄露，影响面扩大到对象存储管理级别。  
修复建议：生产环境拆分 MinIO root 管理账号与应用 service account；backend 仅使用最小 bucket 权限；上线前补充凭据轮换步骤和回滚说明。  
优先级：中。  
阻塞/非阻塞：非阻塞。

### P3-1 刷新接口仍保留 `X-Refresh-Token` 兼容路径

类型：认证兼容性 / 攻击面收敛  
严重级别：P3  
位置：`backend/src/main/java/com/cgcpms/auth/controller/AuthController.java`、`backend/src/main/java/com/cgcpms/config/CorsConfig.java`、`frontend-admin/src/api/request.ts`  
证据：刷新接口优先使用 HttpOnly Cookie，但仍兼容 `X-Refresh-Token` header；CORS allowed headers 包含该 header；当前前端请求封装主要使用 cookie 刷新。  
影响：兼容路径增加 token 传递面和审计复杂度，虽然当前未发现直接可利用链路。  
修复建议：确认是否仍有旧客户端依赖；若无依赖，分阶段移除 header 兼容；若需保留，增加调用来源审计和弃用说明。  
优先级：低。  
阻塞/非阻塞：非阻塞。

### P3-2 审计提示文件缺失，需要确认是否仍为正式流程依赖

类型：流程文档 / 审计输入  
严重级别：P3  
位置：`docs/prompt/code-audit-agent.md`  
证据：按任务要求检查该文件，当前不存在。用户已声明若不存在则标注“需要确认”，不作为阻塞。  
影响：若后续全量审计依赖统一 prompt，则不同审计人可能在问题分级和报告结构上出现差异。  
修复建议：确认该文件是否应创建、迁移或废弃；若继续使用，应作为审计/归档型子任务补齐，不在本轮直接修复。  
优先级：低。  
阻塞/非阻塞：非阻塞。

## 必须修复

1. 修复成本摘要接口项目级访问控制缺口，并补充正反向权限测试。
2. 从版本管理中移除测试/浏览器产物和 `admin-state.json` 等运行状态文件，补齐忽略规则或转为脱敏正式证据。
3. 明确 Redis/token blacklist 在生产 profile 下的 fail-closed 或强告警策略，避免测试假绿掩盖登出/吊销失效。

## 建议优化

1. 将 Flyway 不可变检查改成 CI 失败门禁，并覆盖 MySQL 与 H2 migration。
2. 建立 H2/MySQL migration parity 清单，对必须分叉的 migration 写明原因和测试策略。
3. 升级 `echarts` 到修复 XSS 的兼容版本，并回归驾驶舱图表。
4. 收敛文件响应字段，避免普通 API 暴露对象存储路径和 bucket。
5. 分阶段拆分超长前端页面，优先拆高风险业务页面。
6. 为核心页面设置最小覆盖目标，避免仅以测试通过作为有效性证明。
7. 生产 MinIO 凭据拆分 root 管理账号和应用最小权限账号。

## 长期演进建议

1. 建立项目级授权统一入口：成本、合同、付款、结算、采购、库存、文件附件等业务对象应复用同一项目/对象权限模型，减少每个模块自行判断。
2. 将“测试证据”和“运行产物”分离：正式证据进入 `docs/quality` 的脱敏报告，运行态输出进入忽略目录并禁止跟踪。
3. 将数据库迁移治理前移到 PR：migration 新增、修改、H2/MySQL parity、危险 SQL、回滚说明由 CI 和审查模板共同约束。
4. 建立生产降级矩阵：Redis、MinIO、MySQL、JWT/Jasypt、前端代理等关键依赖应明确 fail-open/fail-closed、告警、回滚和验收命令。
5. 对前端大页面采用“业务域 + 组合式函数 + 组件拆分 + 最小测试”的渐进式治理，避免一次性大重构。

## 测试建议

1. 后端：新增成本摘要权限测试，至少覆盖管理员、项目负责人、同租户无项目权限用户、跨租户用户。
2. 后端：新增 token blacklist 不可用时生产 profile 的行为测试，明确是否 401/503/fail-closed。
3. 后端：为文件附件的 `CONTRACT`、`INVOICE`、`PAYMENT`、`SETTLEMENT` 等业务类型补充对象级授权负向测试。
4. 数据库：使用 MySQL Testcontainers 或 CI MySQL job 覆盖金额、审批、删除标记、权限相关迁移后的关键查询。
5. 前端：围绕登录刷新、权限菜单、成本摘要、文件上传、合同/采购/结算核心表单建立最小组件测试和 API mock 测试。
6. 依赖：升级 ECharts 后对 dashboard 和所有图表页面执行截图或 DOM 断言回归。

## 验证命令与结果

| 命令 | 结果 |
| --- | --- |
| `git status --short -- . ':!.omc' ':!.omo' ':!.opencode' ':!.claude' ':!.mimocode' ':!graphify-out' ':!.sisyphus' ':!.archive'` | 只读检查；审计开始时无相关输出，后续发现既有未跟踪 `docs/quality/browser-automation-test-report-2026-07-08-mainline-26.md`，本任务未触碰 |
| `git check-ignore -v deploy/.env deploy/ssl/server.key deploy/ssl/server.crt AGENTS.md docs/README.md skills-lock.json` | `deploy/.env` 与 `deploy/ssl/**` 被忽略；`AGENTS.md`、`docs/README.md`、`skills-lock.json` 未被忽略 |
| `git ls-files backend/target frontend-admin/dist frontend-admin/coverage test-results output .agent-runtime frontend-admin/playwright-report frontend-admin/coverage-result.json` | 发现 36 个 `output/playwright/**` 和 1 个 `frontend-admin/coverage-result.json` 已跟踪 |
| `powershell -ExecutionPolicy Bypass -File scripts\check-sql-safety.ps1` | 退出码 0，`SQL injection scan PASS` |
| `scripts\check-flyway-immutability.bat` | 退出码 0，但输出多条命令识别错误，不能作为有效通过证据 |
| `cmd /c scripts\check-flyway-immutability.bat` | 退出码 0，但同样输出多条命令识别错误，不能作为有效通过证据 |
| `.\mvnw.cmd -q test "-Djasypt.encryptor.password=dev-jasypt-key"`（`backend`） | 退出码 0，后端全量测试通过；日志包含大量 `BLACKLIST_UNAVAILABLE` 与 fallback rate limit 提示 |
| `pnpm build`（`frontend-admin`） | 退出码 0，`vue-tsc --noEmit && vite build` 通过 |
| `pnpm test:coverage`（`frontend-admin`） | 退出码 0，74 个测试文件、392 个测试通过；覆盖率 Statements 16.55%、Branches 16.89%、Functions 13.41%、Lines 16.85% |
| `pnpm audit --audit-level high --registry=https://registry.npmjs.org`（`frontend-admin`） | 退出码 0；无 high 及以上漏洞导致失败 |
| `pnpm audit --json --registry=https://registry.npmjs.org`（`frontend-admin`） | 退出码 1；发现 `echarts` 中危 XSS 漏洞 |

## 未验证项

1. 未执行浏览器自动化测试；任务要求覆盖 M2/M3/M4 除浏览器自动化之外的审查项。
2. 未启动或重启任何后端、前端、Docker、数据库、Redis、MinIO 服务，避免改变运行环境。
3. 未执行会安装依赖或改变锁文件的命令。
4. 未执行 Git 清理、移除跟踪、提交或变更分支。
5. 未读取或扫描 `.omc/`、`.omo/`、`.opencode/`、`.claude/`、`.mimocode/`、`graphify-out/`、`.sisyphus/`、`.archive/`。

## 最终建议

本阶段不建议进入正式上线收口。建议先以最小修复闭环处理 3 个 P1 阻塞项：成本摘要项目级权限、发布包洁净度、Redis/token blacklist 降级语义。完成后再补跑后端全量测试、前端构建、前端覆盖率测试、依赖审计、SQL 安全扫描，并追加一次针对修复范围的权限与发布洁净度复核。P2/P3 可进入第26条主线后续加固清单，但其中 Flyway 门禁、H2/MySQL parity 和 ECharts 漏洞建议在正式生产发布前完成。
