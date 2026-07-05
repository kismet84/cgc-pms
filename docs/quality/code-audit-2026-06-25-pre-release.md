# 上线前代码审计报告

审计时间：2026-06-25  
审计范围：`backend`、`frontend-admin`、`deploy`、`.github/workflows`、质量脚本与生产配置  
审计目标：判断当前仓库是否达到上线条件，优先识别生产事故、安全事故、数据错误、性能雪崩与交付门禁失败风险。  
审计限制：未连接真实生产环境，SSL 证书、生产 `.env`、镜像仓库、运行时域名、真实 MySQL/Redis/MinIO 可用性需要上线前人工确认。

# 总体结论

当前仓库不建议上线。代码层面已经具备不少生产基础设施：HttpOnly Cookie、租户拦截器、SQL 安全扫描、Flyway 校验、生产 preflight、前端构建与 bundle size 门禁都已存在；`pnpm build`、`pnpm type-check`、`pnpm check:bundle-size`、SQL safety scan 和后端 `test-compile` 均通过。  
但当前仍有明确阻断项：生产 Nginx 配置中的 `${BACKEND_URL}` 未模板化，可能导致前端容器启动或 API 代理失败；后端 `mvn verify` 失败；前端 `pnpm test:coverage` 失败；生产环境暴露超级管理员清库接口；前端官方依赖审计存在 High 级生产依赖链风险。  
这些问题并非风格问题，而是会影响发布可用性、CI 可信度、数据安全和供应链安全。建议先修复阻断项并重新跑完整门禁，再进入上线审批。

# 评分

- 正确性：72/100
- 安全性：68/100
- 性能：84/100
- 可维护性：78/100
- 架构设计：76/100
- 测试与工程化：62/100
- 综合评分：70/100

# 风险统计

- Critical：0
- High：5
- Medium：3
- Low：2
- Info：4

# 关键问题

## 问题 1：生产 Nginx 配置使用未模板化的 `${BACKEND_URL}`，前端容器可能无法代理 API

- 类型：工程化与交付 / 生产配置
- 严重程度：High
- 位置：
  - `frontend-admin/nginx.conf:97`
  - `frontend-admin/nginx.conf:120`
  - `frontend-admin/Dockerfile:30`
  - `deploy/docker-compose.prod.yml:244`
- 证据：
  - `frontend-admin/nginx.conf:97`：`proxy_pass http://${BACKEND_URL}/api/;`
  - `frontend-admin/nginx.conf:120`：`proxy_pass http://${BACKEND_URL}/api/notifications/stream;`
  - `frontend-admin/Dockerfile:30`：`COPY nginx.conf /etc/nginx/conf.d/default.conf`
  - `deploy/docker-compose.prod.yml:244-260` 的 `frontend` 服务未设置 `BACKEND_URL` 环境变量，也没有模板文件挂载。
- 问题说明：官方 `nginx` 镜像只会自动处理 `/etc/nginx/templates/*.template`，不会对 `/etc/nginx/conf.d/default.conf` 自动执行 `envsubst`。当前配置把 shell 风格变量直接写入 Nginx 配置，但 Dockerfile 只是复制为静态 conf，生产容器启动时可能因 Nginx 无法解析变量而失败，或 API/SSE 代理不可用。
- 影响：上线后前端静态资源可能可访问，但 `/api/` 和 `/api/notifications/stream` 代理失败；更严重时 Nginx 进程直接启动失败，导致前端服务不可用。
- 修复建议：优先采用官方模板方式，将配置复制到 `/etc/nginx/templates/default.conf.template`，并在 compose 中设置 `BACKEND_URL=backend:8080`。如果短期只面向 docker compose 单一部署，也可以直接写死 `backend:8080`，但模板方式更利于后续环境化。
- 示例修复代码：

```dockerfile
# frontend-admin/Dockerfile
COPY nginx.conf /etc/nginx/templates/default.conf.template
```

```yaml
# deploy/docker-compose.prod.yml
frontend:
  environment:
    BACKEND_URL: backend:8080
```

- 优先级：P0

收益、成本和风险：
- 收益：消除生产容器启动和 API 代理不可用风险。
- 成本：低，Dockerfile 与 compose 小改即可。
- 风险：需要验证模板替换后的最终 Nginx 配置，避免 `${BACKEND_URL}` 与 Nginx 原生变量混用导致替换误伤。

## 问题 2：后端 `verify` 发布门禁失败，发票集成测试数据隔离不可靠

- 类型：测试与工程化 / 数据一致性
- 严重程度：High
- 位置：
  - `backend/src/test/java/com/cgcpms/invoice/InvoiceValidationTest.java:48`
  - `backend/src/test/java/com/cgcpms/invoice/InvoiceValidationTest.java:58`
  - `backend/src/test/java/com/cgcpms/invoice/InvoiceValidationTest.java:61`
  - `backend/src/test/java/com/cgcpms/invoice/InvoiceValidationTest.java:69`
  - `backend/target/surefire-reports/com.cgcpms.invoice.InvoiceValidationTest.txt:4`
- 证据：
  - 执行 `.\mvnw.cmd verify "-Djasypt.encryptor.password=dev-jasypt-key"` 失败。
  - Surefire 报告显示 `InvoiceValidationTest`：`Tests run: 7, Failures: 0, Errors: 7`。
  - `InvoiceValidationTest.java:48` 固定 `SEED_PAY_RECORD_ID = 90001L`。
  - `InvoiceValidationTest.java:58-69` 每个 `@BeforeEach` 都插入同一个 `pay_record.id=90001`。
  - 报告错误为 H2 主键冲突：`PRIMARY KEY ON public.pay_record(id) ... key:90001`。
- 问题说明：测试清理逻辑不可靠。`payRecordMapper.delete(...gt(id, 0))` 在当前 MyBatis-Plus 租户/逻辑删除拦截下没有真正避免固定主键复用冲突，导致所有发票校验用例在 `setUp()` 阶段失败。即使这是测试代码问题，也会让后端发布门禁失去通过条件。
- 影响：CI 的 `backend-test` job 无法稳定通过；更严重的是发票创建、租户防篡改、字段校验等关键路径无法被当前测试有效验证。
- 修复建议：修复测试数据隔离。最小成本方案是在插入前按固定 ID 精确物理清理或使用每个测试唯一 ID；更稳妥方案是使用事务回滚、`@Sql` 清理脚本或测试数据工厂统一生成唯一 ID。
- 示例修复代码：

```java
@BeforeEach
void setUp() {
    payRecordMapper.deleteById(SEED_PAY_RECORD_ID);
    PayRecord seed = new PayRecord();
    seed.setId(SEED_PAY_RECORD_ID);
    seed.setTenantId(0L);
    seed.setPayApplicationId(SEED_PAY_RECORD_ID);
    seed.setPayAmount(new BigDecimal("100000.00"));
    seed.setPayDate(LocalDate.of(2026, 6, 1));
    seed.setPayStatus("PAID");
    payRecordMapper.insert(seed);
}
```

- 优先级：P0

收益、成本和风险：
- 收益：恢复后端发布门禁可信度，重新覆盖发票关键安全与校验路径。
- 成本：低到中，主要是测试隔离修复。
- 风险：如果直接绕过拦截器清理，要确认不会影响其他并行测试数据；更建议测试级唯一 ID 或事务回滚。

## 问题 3：前端覆盖率测试失败，CI 的 `frontend-test` 当前不可通过

- 类型：测试与工程化 / 前端回归
- 严重程度：High
- 位置：
  - `frontend-admin/src/layouts/BasicLayoutAsync.vue:6`
  - `frontend-admin/src/layouts/BasicLayoutAsync.vue:57`
  - `frontend-admin/src/layouts/__tests__/BasicLayout.test.ts`
  - `frontend-admin/src/layouts/__tests__/BasicLayout.a11y.test.ts`
  - `frontend-admin/src/pages/help/__tests__/index.test.ts:105`
  - `frontend-admin/src/pages/help/__tests__/index.test.ts:172`
  - `frontend-admin/src/pages/help/index.vue:61`
  - `frontend-admin/src/pages/help/index.vue:64`
- 证据：
  - 执行 `pnpm test:coverage` 失败：`3 failed | 35 passed (38)`，`10 failed | 164 passed (174)`。
  - 失败日志：`No "ProjectOutlined" export is defined on the "@ant-design/icons-vue" mock`。
  - `BasicLayoutAsync.vue:6` 引入 `ProjectOutlined`，`BasicLayoutAsync.vue:57` 使用该图标。
  - HelpPage 测试期望 `.stub-card-title` 有 3 个，但当前页面 `help/index.vue:61-101` 使用 `lg-section` 结构，不再使用测试 stub 的 card title。
  - HelpPage 测试期望首个 `h2` 是 `帮助中心`，但当前页面首个 `h2` 是 `快捷键`。
- 问题说明：前端测试与当前组件实现不同步。布局测试 mock 缺少新图标导出，Help 页面测试仍按旧 card 结构断言。当前不是运行时构建失败，但 CI 的 `frontend-test` 会失败，阻断可发布性。
- 影响：前端回归门禁不可用；可访问性和布局交互测试无法提供上线信号。
- 修复建议：补齐 `@ant-design/icons-vue` mock 中的 `ProjectOutlined`；同步 HelpPage 测试到当前 `lg-section` DOM 结构，或者给页面标题恢复明确语义并调整断言。
- 示例修复代码：

```ts
vi.mock('@ant-design/icons-vue', () => ({
  MenuFoldOutlined: iconStub('MenuFoldOutlined'),
  ProjectOutlined: iconStub('ProjectOutlined'),
  QuestionCircleOutlined: iconStub('QuestionCircleOutlined'),
}))
```

- 优先级：P0

收益、成本和风险：
- 收益：恢复前端测试门禁，避免 UI 回归被掩盖。
- 成本：低，主要是测试 mock 和断言更新。
- 风险：如果只修测试不复核页面语义，可能错过 Help 页面标题层级变化对可访问性的影响。

## 问题 4：生产环境暴露超级管理员清库接口，缺少 profile 限制和二次确认

- 类型：安全性 / 数据安全 / 运维风险
- 严重程度：High
- 位置：
  - `backend/src/main/java/com/cgcpms/system/controller/SystemController.java:40`
  - `backend/src/main/java/com/cgcpms/system/controller/SystemController.java:41`
  - `backend/src/main/java/com/cgcpms/system/controller/SystemController.java:46`
  - `backend/src/main/java/com/cgcpms/system/controller/SystemController.java:58`
- 证据：
  - `SystemController.java:40` 暴露 `DELETE /system/clear-database`。
  - `SystemController.java:41` 只限制 `hasRole('SUPER_ADMIN')`。
  - `SystemController.java:46` 执行 `SET FOREIGN_KEY_CHECKS = 0`。
  - `SystemController.java:58` 循环 `TRUNCATE TABLE` 清空非保护表。
- 问题说明：虽然该接口有 `SUPER_ADMIN` 权限，但它在 Web API 层暴露全库业务数据清除能力，且当前代码未按 `prod` profile 禁用、未要求二次确认、未做备份前置校验、未绑定审批或 break-glass 流程。上线前这类接口的误触、越权账号滥用或凭证泄露后果都非常高。
- 影响：一旦被超级管理员误操作或账号被盗用，会造成生产业务数据大面积删除。`TRUNCATE` 类操作通常难以从应用层审计和回滚。
- 修复建议：生产环境禁用该接口，或移出 Web API 改为受控运维脚本。若业务确需保留，至少增加 `@Profile("!prod")`、显式确认码、操作审计、备份快照检查、IP 白名单和双人审批。
- 示例修复代码：

```java
@Profile("!prod")
@RestController
@RequestMapping("/system")
public class SystemController {
    // clear-database only available outside production
}
```

- 优先级：P0

收益、成本和风险：
- 收益：显著降低生产数据误删除和高权限滥用风险。
- 成本：低；如改为运维脚本，成本中等。
- 风险：如果当前团队依赖该接口做演示环境重置，需要提供替代脚本或非生产 profile 才不会影响运维效率。

## 问题 5：前端官方依赖审计存在 High 级生产依赖链风险

- 类型：安全性 / 供应链
- 严重程度：High
- 位置：
  - `frontend-admin/package.json:21`
  - `frontend-admin/pnpm-lock.yaml:919`
  - `frontend-admin/pnpm-lock.yaml:1275`
- 证据：
  - `frontend-admin/package.json:21` 使用 `axios`。
  - `frontend-admin/pnpm-lock.yaml:919` 锁定 `axios@1.17.0`。
  - `frontend-admin/pnpm-lock.yaml:1275` 锁定 `form-data@4.0.5`。
  - 执行 `pnpm audit --audit-level high --registry=https://registry.npmjs.org` 返回 10 个漏洞，其中 `axios > form-data` 链路包含 High：`form-data >=4.0.0 <4.0.6` 存在 multipart field/filename CRLF injection，patched `>=4.0.6`。
- 问题说明：这不是镜像源误报。使用官方 npm registry 后能够复现 High 级审计结果。`axios` 是运行时依赖，虽然浏览器端是否实际触发 `form-data` 取决于打包和使用路径，但上线前不能忽略生产依赖链的 High 级供应链风险。
- 影响：若在 Node/SSR/构建或浏览器兼容路径中触发受影响的 multipart 处理，可能导致请求头注入类风险。即使业务当前主要是浏览器运行，也会让供应链安全门禁不达标。
- 修复建议：升级或覆盖 `form-data >=4.0.6`，同步锁文件后重新执行官方 registry audit。测试工具链中的 `playwright`、`jsdom/undici`、`@vue/test-utils/js-beautify/ini` 也建议一并升级，但运行时依赖链优先级最高。
- 示例修复代码：

```json
{
  "pnpm": {
    "overrides": {
      "form-data": "^4.0.6"
    }
  }
}
```

- 优先级：P0

收益、成本和风险：
- 收益：清除生产依赖链 High 风险，提升供应链审计可信度。
- 成本：低到中，取决于锁文件解析和兼容性回归。
- 风险：依赖覆盖可能影响 axios 上传行为，需要回归文件上传、发票/附件等 multipart 场景。

## 问题 6：登录重定向直接使用 `route.query.redirect`，缺少站内路径约束

- 类型：安全性 / 前端路由
- 严重程度：Medium
- 位置：
  - `frontend-admin/src/pages/login/index.vue:34`
  - `frontend-admin/src/pages/login/index.vue:35`
  - `frontend-admin/src/router/index.ts:412`
  - `frontend-admin/src/router/index.ts:413`
- 证据：
  - `login/index.vue:34-35`：`const redirect = (route.query.redirect as string) || '/'` 后直接 `router.push(redirect)`。
  - `router/index.ts:412-413` 未登录时将 `to.fullPath` 写入 `redirect`。
- 问题说明：当前来源主要来自路由守卫写入的站内 fullPath，但登录页本身接受任意 query。Vue Router 对完整外部 URL 的处理通常不会直接跳出站点，但仍建议显式限制为单斜杠开头的站内相对路径，拒绝 `//evil.com`、协议 URL 或异常对象数组，避免未来改为 `window.location` 或 SSO 回调时升级为开放重定向。
- 影响：当前为中等偏低风险；长期看属于安全边界不清晰。
- 修复建议：封装 `normalizeRedirect()`，只允许以 `/` 开头且不以 `//` 开头的字符串，不合法则回到 `/dashboard` 或 `/`。
- 示例修复代码：

```ts
function normalizeRedirect(value: unknown) {
  if (typeof value !== 'string') return '/'
  if (!value.startsWith('/') || value.startsWith('//')) return '/'
  return value
}

router.push(normalizeRedirect(route.query.redirect))
```

- 优先级：P1

收益、成本和风险：
- 收益：收紧认证后跳转边界，避免未来演进引入开放重定向。
- 成本：低。
- 风险：如果业务需要登录后跳外部 SSO/门户，需要改为显式 allowlist。

## 问题 7：`pnpm audit` 默认镜像源不可用，依赖安全扫描容易被误判为无法执行

- 类型：工程化 / 安全门禁
- 严重程度：Medium
- 位置：
  - 当前 npm registry 配置需要确认
  - `frontend-admin/package.json`
  - `.github/workflows/ci.yml`
- 证据：
  - 执行 `pnpm audit --audit-level high` 返回：`registry.npmmirror.com/-/npm/v1/security/advisories/bulk doesn't exist`。
  - 改用官方 registry：`pnpm audit --audit-level high --registry=https://registry.npmjs.org` 可以正常返回漏洞结果。
- 问题说明：如果团队在本地或 CI 使用 `npmmirror`，直接跑 `pnpm audit` 会因为镜像源缺少安全审计 endpoint 而失败或被跳过。当前 CI 文件未看到专门的依赖漏洞扫描 job。
- 影响：供应链漏洞可能不会被稳定发现，或者被误判为网络/镜像问题而长期跳过。
- 修复建议：CI 中固定 audit 使用官方 registry，或接入 GitHub Dependabot / pnpm audit 的可用 registry。若内网必须使用私有源，需要私有源支持 npm audit endpoint 或接入独立 SCA 工具。
- 示例修复代码：

```yaml
- name: Frontend dependency audit
  run: cd frontend-admin && pnpm audit --audit-level high --registry=https://registry.npmjs.org
```

- 优先级：P1

收益、成本和风险：
- 收益：让供应链门禁可重复、可解释。
- 成本：低。
- 风险：官方 registry 访问受网络影响时可能导致 CI 不稳定，需要设置合理重试或企业 SCA 替代方案。

## 问题 8：H2 测试迁移与 MySQL 生产迁移存在版本偏移，需要防止迁移验证盲区

- 类型：数据与数据库 / 测试一致性
- 严重程度：Medium
- 位置：
  - `backend/src/main/resources/db/migration/V90__create_operation_audit_log.sql`
  - `backend/src/main/resources/db/migration-h2/V90__h2_integration_test_seed_data.sql`
  - `backend/src/main/resources/db/migration-h2/V91__create_operation_audit_log.sql`
- 证据：
  - 生产 MySQL 迁移使用 `V90__create_operation_audit_log.sql`。
  - H2 迁移中 `V90` 是集成测试种子数据，`operation_audit_log` 对应为 `V91`。
- 问题说明：H2 和 MySQL 的迁移版本不完全对齐。当前不一定导致生产失败，但会降低测试环境对生产迁移顺序的模拟能力，尤其是涉及 Flyway 历史、数据回填、约束变更时。
- 影响：某些迁移顺序问题可能在 H2 测试中不可见，只在 MySQL 集成或生产迁移时暴露。
- 修复建议：保留 H2 专用兼容脚本可以接受，但应明确维护映射表，并优先用 MySQL 集成测试覆盖迁移链路。上线前必须跑 `.github/workflows/ci.yml:52-53` 对应的 MySQL 集成测试。
- 示例修复代码：不建议机械重命名历史迁移。建议增加迁移一致性检查文档或脚本。
- 优先级：P1

收益、成本和风险：
- 收益：降低数据库迁移环境差异导致的上线事故。
- 成本：中等，需要梳理 Flyway 历史。
- 风险：已执行过的迁移不要随意改名或改内容，否则会破坏 Flyway checksum。

# 必须修复

- 修复 `frontend-admin/nginx.conf` 的 `${BACKEND_URL}` 模板化或固定代理目标问题，并验证生产容器可以启动、`/api/actuator/health` 可经前端代理访问。
- 修复 `InvoiceValidationTest` 数据隔离问题，确保 `cd backend && .\mvnw.cmd verify "-Djasypt.encryptor.password=dev-jasypt-key"` 通过。
- 修复前端失败用例，确保 `cd frontend-admin && pnpm test:coverage` 通过。
- 生产环境禁用或强约束 `DELETE /system/clear-database`。
- 修复 `axios > form-data@4.0.5` High 级生产依赖风险，并重新执行官方 registry 的 `pnpm audit --audit-level high`。

# 建议优化

- 将 `pnpm audit --registry=https://registry.npmjs.org` 或企业 SCA 工具加入 CI。收益是稳定发现供应链漏洞；成本低；风险是外网访问可能影响 CI 稳定。
- 收紧登录 redirect，只允许站内相对路径。收益是消除未来开放重定向演进风险；成本低；风险是需要确认是否存在外部跳转业务。
- 为 H2/MySQL Flyway 迁移差异建立映射说明，并保证 MySQL 集成测试始终是上线门禁。收益是减少迁移盲区；成本中等；风险是历史迁移不能随意改 checksum。
- 对生产 `.env` 建立上线检查清单：`JWT_SECRET` 长度、`CORS_ALLOWED_ORIGINS`、镜像 tag、SSL 证书、Redis 密码、MinIO 凭据。收益是避免配置事故；成本低；风险是需要运维配合。

# 长期演进建议

- 建立“发布候选版本”流水线：后端 verify、MySQL 集成、前端 coverage、E2E、SQL safety、bundle size、依赖 audit、镜像构建、compose config 校验全部通过后才允许打 tag。
- 对高危运维能力采用 break-glass 模式：从 Web API 中移除清库、重置、批量删除类能力，改为有审批、备份、审计和最小权限的运维通道。
- 将前端测试 mock 组件沉淀为共享 test helper，避免每次布局图标或 Ant Design 组件变更都导致多个测试文件重复腐化。
- 对关键业务域保留跨租户隔离回归测试。当前租户拦截器和部分测试已覆盖租户边界，这是做得好的方向，应继续扩展到发票、付款、附件、审批等跨业务链路。
- 对依赖升级建立小步节奏：优先修生产依赖 High，再升级测试工具链 High，升级后固定跑上传、登录、审批、E2E smoke。

# 测试建议

- 后端：
  - `cd backend && .\mvnw.cmd test-compile "-Djasypt.encryptor.password=dev-jasypt-key"`
  - `cd backend && .\mvnw.cmd verify "-Djasypt.encryptor.password=dev-jasypt-key"`
  - MySQL 集成测试：按 `.github/workflows/ci.yml:52-58` 使用真实 MySQL/Redis 服务运行。
- 前端：
  - `cd frontend-admin && pnpm type-check`
  - `cd frontend-admin && pnpm build`
  - `cd frontend-admin && pnpm check:bundle-size`
  - `cd frontend-admin && pnpm test:coverage`
  - 修复布局/Help 测试后补跑 `pnpm exec vitest run src/layouts/__tests__/BasicLayout.test.ts src/layouts/__tests__/BasicLayout.a11y.test.ts src/pages/help/__tests__/index.test.ts`
- 安全：
  - `powershell -ExecutionPolicy Bypass -File scripts\check-sql-safety.ps1`
  - `cd frontend-admin && pnpm audit --audit-level high --registry=https://registry.npmjs.org`
  - 对 `DELETE /system/clear-database` 增加生产 profile 验证测试。
- 部署：
  - `docker compose -f deploy/docker-compose.prod.yml config`
  - 构建前端镜像后在容器内执行 `nginx -t`，确认模板替换后的配置合法。
  - 启动生产 compose 试运行环境，验证 `/`、`/api/actuator/health`、SSE 通知代理、登录刷新 cookie。

# 验收标准

- 验收目标：生产前端容器可启动且 API 代理可用。
  - 验收步骤：构建前端镜像，使用生产 compose 或等价环境启动；进入容器执行 `nginx -t`；访问 `/api/actuator/health`。
  - 通过标准：Nginx 配置测试通过，前端容器 healthy，API 和 SSE 代理指向后端服务。
  - 必须通过的命令或测试：`docker compose -f deploy/docker-compose.prod.yml config`、容器内 `nginx -t`。
  - 需要人工确认的事项：生产域名、SSL 证书、`BACKEND_URL` 或固定服务名符合实际部署。

- 验收目标：后端发布门禁恢复。
  - 验收步骤：修复 `InvoiceValidationTest` 数据隔离，重新运行 Maven verify。
  - 通过标准：`InvoiceValidationTest` 7 个用例全部通过，后端 `verify` 总体通过。
  - 必须通过的命令或测试：`cd backend && .\mvnw.cmd verify "-Djasypt.encryptor.password=dev-jasypt-key"`。
  - 需要人工确认的事项：失败不应通过 skip test、删测试或降低断言绕过。

- 验收目标：前端测试门禁恢复。
  - 验收步骤：补齐 icon mock，同步 HelpPage 测试断言，重新运行 coverage。
  - 通过标准：`38` 个测试文件、`174` 个测试全部通过，覆盖率阈值不下降。
  - 必须通过的命令或测试：`cd frontend-admin && pnpm test:coverage`。
  - 需要人工确认的事项：Help 页面标题和可访问性结构符合当前 UI 设计。

- 验收目标：清库能力不再对生产 Web API 暴露。
  - 验收步骤：在 `prod` profile 启动后请求 `DELETE /api/system/clear-database`。
  - 通过标准：生产环境返回 404/403，且不会执行任何 `TRUNCATE`。
  - 必须通过的命令或测试：新增或更新 Spring profile 测试。
  - 需要人工确认的事项：如果业务坚持保留，必须提供审批、备份和 break-glass 文档。

- 验收目标：供应链 High 风险清零或有明确豁免。
  - 验收步骤：升级/override 受影响依赖，重新执行官方 registry audit。
  - 通过标准：生产依赖链无 High/Critical；测试工具链 High 如暂缓，需记录不影响运行时的风险接受。
  - 必须通过的命令或测试：`cd frontend-admin && pnpm audit --audit-level high --registry=https://registry.npmjs.org`。
  - 需要人工确认的事项：依赖升级后文件上传、附件下载、Playwright E2E 仍可用。

# 最终建议

不建议上线。  
当前已经具备较好的上线基础，但仍有 5 个 High 级阻断项，其中 Nginx 代理配置和清库接口是生产事故风险，后端/前端测试失败是发布门禁风险，`axios > form-data` 是供应链风险。修复上述问题并通过验收标准后，再进行下一轮上线前复审。
