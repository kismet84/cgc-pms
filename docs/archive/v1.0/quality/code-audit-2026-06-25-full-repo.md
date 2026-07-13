# CGC-PMS 代码审计报告（2026-06-25）

## 审计范围与验证说明

- 审计对象：`D:\projects-test\cgc-pms` 当前工作区，覆盖后端 Spring Boot、前端 Vue/Vite、CI/CD、部署配置与关键业务代码。
- 审计方式：基于仓库源代码、配置文件、静态搜索和最小构建验证。
- 已执行验证：
  - `frontend-admin`: `pnpm build` 通过。
  - `backend`: `.\mvnw.cmd --% -q -DskipTests test-compile -Djasypt.encryptor.password=dev-jasypt-key` 通过。
  - `scripts\check-sql-safety.ps1` 输出 `SQL injection scan PASS`，但本报告指出该脚本存在覆盖盲区。
- 未执行验证：
  - 未运行后端全量测试、MySQL 集成测试和 E2E。相关结论以静态证据和最小编译验证为基础。
- 限制说明：报告基于 2026-06-25 审计时的工作区状态；仓库存在大量未提交改动，本报告不区分这些改动来自用户还是历史任务。

# 总体结论

当前仓库整体已经比早期审查基线成熟：后端能通过 `test-compile`，前端 `pnpm build` 通过；权限注解、HttpOnly Cookie、租户拦截器、生产 preflight、CI/E2E/SQL 扫描等基础设施都已经具备。

但不建议直接上线：成本台账查询存在 Service 层 SQL 拼接 + `.apply()`，并且现有 SQL 安全扫描没有覆盖到它，这是当前最需要优先处理的生产安全风险。其次，CSP 仍包含 `unsafe-inline`/`unsafe-eval`，前端依赖补丁配置被 pnpm 11 忽略，工程化可复现性存在隐患。

建议结论：修改后合并，暂不建议上线。

## 做得好的地方

- 后端全局启用了认证链路和方法级权限：`SecurityConfig` 要求非白名单请求认证，控制器大量使用 `@PreAuthorize`。
- 登录/刷新 token 已改为 HttpOnly Cookie，响应体主动清空 token，降低 XSS 直接窃取 token 的风险。
- 库存出入库使用事务、乐观锁、并发重试和库存不足业务异常，不是简单读改写。
- 生产 compose 已有密钥、证书、镜像 tag preflight，避免 `latest` 和默认密钥直接进生产。
- CI 已包含后端、MySQL 集成、前端 build/test、E2E、SQL 安全扫描等基础门禁。

# 评分

- 正确性：78/100
- 安全性：68/100
- 性能：72/100
- 可维护性：76/100
- 架构设计：80/100
- 测试与工程化：74/100
- 综合评分：75/100

# 风险统计

- Critical：0
- High：2
- Medium：4
- Low：2
- Info：3

# 关键问题

## 问题 1：成本台账关键字查询拼接 SQL，绕过参数绑定

- 类型：安全 / 数据库
- 严重程度：High
- 位置：`backend/src/main/java/com/cgcpms/cost/service/CostLedgerService.java:148`
- 证据：
  - `escapeLike()` 只做字符串替换。
  - `CostLedgerService.java:181-190` 将 `keyword` 拼入 SQL，并调用 `wrapper.apply("(" + whereClause + ")")`。
- 问题说明：`.apply()` 接收原始 SQL 片段，当前实现不是参数绑定。即使替换了单引号、`%`、`_`，仍容易被数据库转义规则、反斜杠、字符集或后续维护引入绕过。
- 影响：攻击者可能通过成本台账关键字查询触发 SQL 注入、慢查询放大、跨表探测；这是业务核心财务数据路径。
- 修复建议：
  - 不要在 Service 拼接 SQL。
  - 普通字段用 `wrapper.like()` / `eq()`。
  - 跨表搜索改为 XML/注解 SQL 并使用 `#{keyword}` 绑定，或使用 `apply("... LIKE {0}", like)` 参数占位。
- 示例修复代码：

```java
String like = "%" + keyword.trim() + "%";
wrapper.and(w -> {
    if (idMatch != null) {
        w.eq(CostItem::getId, idMatch).or();
    }
    w.like(CostItem::getCostType, keyword)
     .or().like(CostItem::getSourceType, keyword)
     .or().like(CostItem::getCostStatus, keyword)
     .or().like(CostItem::getRemark, keyword)
     .or().apply(
         "EXISTS (SELECT 1 FROM pm_project p " +
         "WHERE p.id = cost_item.project_id AND p.project_name LIKE {0})",
         like
     );
});
```

- 收益 / 成本 / 风险：
  - 收益：高，直接消除注入面。
  - 成本：中等，需要补查询回归测试。
  - 风险：关键字搜索语义可能略有变化，需要业务验收。
- 优先级：P0，必须修复。

## 问题 2：SQL 安全扫描门禁漏扫 Service 层，当前风险被误报为 PASS

- 类型：工程化 / 安全门禁
- 严重程度：High
- 位置：`scripts/check-sql-safety.ps1:54`
- 证据：
  - 脚本只扫描 XML mapper 和 mapper 包/SQL 注解 Java。
  - 本次执行输出 `SQL injection scan PASS`。
  - 但问题 1 的 `.apply()` 位于 Service 层，未被扫描覆盖。
- 问题说明：CI 有安全门禁但覆盖范围不足，会给团队错误安全感。
- 影响：类似 `.apply()`、`.last()`、拼接 SQL 放在 Service 层即可绕过扫描。
- 修复建议：
  - 扫描所有 `backend/src/main/java/**/*.java`。
  - 对 `.apply()` / `.last()` 默认失败。
  - 仅允许同一行带审计豁免注释和原因的白名单用法。
- 示例修复代码：

```powershell
# 不再跳过非 mapper Java
$javaFiles = Get-ChildItem -Path $javaSrcDir -Recurse -Filter *.java
foreach ($f in $javaFiles) {
    $scanTargets += $f.FullName
}
```

- 收益 / 成本 / 风险：
  - 收益：高，能阻断同类问题。
  - 成本：低。
  - 风险：初次扩展扫描范围会暴露既有 false positive，需要一次性分级清理。
- 优先级：P0，必须修复。

## 问题 3：生产 CSP 允许 `unsafe-inline` 和 `unsafe-eval`

- 类型：安全 / 前端部署
- 严重程度：Medium
- 位置：`frontend-admin/nginx.conf:67`
- 证据：`script-src 'self' 'unsafe-inline' 'unsafe-eval'`。
- 问题说明：这会显著削弱 CSP 对 XSS 的防护价值。当前静态搜索未发现 `v-html` 等明显 DOM 注入点，这是正向信号，但 CSP 本应作为最后一道防线。
- 影响：一旦出现 XSS sink 或第三方依赖漏洞，CSP 很难阻止脚本执行。
- 修复建议：
  - 先移除 `unsafe-eval`。
  - 再清点是否有内联脚本，改为外部文件或 hash/nonce。
  - Ant Design Vue / Vite 生产包通常不需要 `unsafe-eval`，但需要浏览器回归确认。
- 示例修复代码：

```nginx
add_header Content-Security-Policy "default-src 'self'; script-src 'self'; style-src 'self' 'unsafe-inline'; frame-ancestors 'none'; form-action 'self';" always;
```

- 收益 / 成本 / 风险：
  - 收益：中高，提升 XSS 防御深度。
  - 成本：中等，需要浏览器回归。
  - 风险：某些 UI 库样式或旧脚本可能需要调整。
- 优先级：P1。

## 问题 4：项目角色审批人解析未显式带 tenant 条件

- 类型：正确性 / 越权防御
- 严重程度：Medium（需要确认运行时 `UserContext` 是否始终存在）
- 位置：`backend/src/main/java/com/cgcpms/workflow/service/ApproverResolver.java:154`
- 证据：`resolveProjectRole()` 只按 `projectId`、`roleCode`、`status` 查询，`ApproverResolver.java:164-167` 没有显式 `.eq(PmProjectMember::getTenantId, tenantId)`。
- 问题说明：当前可能依赖 MyBatis 租户拦截器自动注入 tenant；但方法签名不接收 tenantId，且审批流是核心业务边界，显式条件更稳。
- 影响：如果异步任务、系统调用或拦截器豁免路径下 `UserContext` 缺失，可能解析到错误租户的项目成员。
- 修复建议：改为 `resolveProjectRole(config, tenantId, projectId)`，并显式追加 tenant 条件。
- 示例修复代码：

```java
private List<Long> resolveProjectRole(JsonNode config, Long tenantId, Long projectId) {
    if (!config.has("roleCode")) {
        throw new BusinessException("INVALID_APPROVER_CONFIG", "PROJECT_ROLE类型配置缺少roleCode");
    }
    if (projectId == null) {
        throw new BusinessException("NO_PROJECT", "PROJECT_ROLE类型需要关联项目");
    }
    String roleCode = config.get("roleCode").asText();

    return pmProjectMemberMapper.selectList(
            new LambdaQueryWrapper<PmProjectMember>()
                    .eq(PmProjectMember::getTenantId, tenantId)
                    .eq(PmProjectMember::getProjectId, projectId)
                    .eq(PmProjectMember::getRoleCode, roleCode)
                    .eq(PmProjectMember::getStatus, "ACTIVE"))
            .stream()
            .map(PmProjectMember::getUserId)
            .distinct()
            .toList();
}
```

- 收益 / 成本 / 风险：
  - 收益：中高，降低隐式上下文依赖。
  - 成本：低。
  - 风险：若历史数据 tenantId 缺失会暴露数据问题。
- 优先级：P1。

## 问题 5：pnpm 11 忽略 `package.json` 内 pnpm 配置，补丁依赖可能不生效

- 类型：工程化 / 构建可复现性
- 严重程度：Medium
- 位置：`frontend-admin/package.json:51`
- 证据：
  - `pnpm build` 输出警告：`The "pnpm" field in package.json is no longer read by pnpm`。
  - `patchedDependencies` 和 `onlyBuiltDependencies` 位于 `package.json:51-60`。
- 问题说明：`patchedDependencies` 和 `onlyBuiltDependencies` 放在旧位置，pnpm 11 不读取，实际安装结果可能和团队预期不一致。
- 影响：依赖补丁失效后，UI 或构建行为可能在 CI、本地、生产镜像间漂移。
- 修复建议：迁移到 `pnpm-workspace.yaml` 的新配置位置，并在 CI 中加 `pnpm install --frozen-lockfile` 验证。
- 示例修复代码：

```yaml
# frontend-admin/pnpm-workspace.yaml
onlyBuiltDependencies:
  - core-js
  - esbuild
  - vue-demi
patchedDependencies:
  ant-design-vue@4.2.6: patches/ant-design-vue@4.2.6.patch
```

- 收益 / 成本 / 风险：
  - 收益：中，提升构建可复现性。
  - 成本：低。
  - 风险：迁移后 lockfile 可能需要重新确认。
- 优先级：P1。

## 问题 6：前端核心 vendor chunk 过大，首屏和弱网体验有退化风险

- 类型：性能
- 严重程度：Medium
- 位置：`frontend-admin/vite.config.ts:36`
- 证据：本次 `pnpm build` 通过但警告：`vendor-antd` 约 1.30MB、`vendor-vxe` 约 1.02MB，超过 500KB。
- 问题说明：当前 `manualChunks` 按库粗拆，Ant Design Vue / VxeTable 被打成大块，用户进入任意页面可能提前下载重依赖。
- 影响：低带宽、移动网络或首次访问下加载慢；缓存失效时影响更明显。
- 修复建议：
  - 按路由懒加载重表格/图表页面。
  - 减少全局组件自动导入范围。
  - 对 VxeTable/ECharts 只在使用页面加载。
- 示例修复代码：

```ts
{
  path: '/inventory/stock',
  component: () => import('@/pages/inventory/stock.vue'),
}
```

- 收益 / 成本 / 风险：
  - 收益：中，改善首屏和弱网体验。
  - 成本：中。
  - 风险：拆包后要做路由级冒烟，避免组件样式缺失。
- 优先级：P2。

# 必须修复

- 修复 `backend/src/main/java/com/cgcpms/cost/service/CostLedgerService.java:175` 的 SQL 拼接和 `.apply()` 原始 SQL。
- 扩展 `scripts/check-sql-safety.ps1:54` 的扫描范围，确保 Service 层 SQL 风险不能绕过 CI。
- 修复后补充成本台账关键字查询的恶意字符、跨表搜索、普通搜索回归测试。

# 建议优化

- 收紧 `frontend-admin/nginx.conf:67` 的 CSP，优先移除 `unsafe-eval`。
- 将 `frontend-admin/package.json:51` 中 pnpm 配置迁移到 `pnpm-workspace.yaml`。
- 将审批 `PROJECT_ROLE` 解析显式加 tenant 条件，降低对隐式拦截器上下文的依赖。
- 对超大 vendor chunk 做页面级拆包和首屏性能预算。

# 长期演进建议

- 安全治理：把 SQL 风险从“脚本扫描”升级为“禁止原始 SQL 拼接 + 明确豁免清单 + 单测覆盖”。
- 权限治理：核心业务查询统一采用“方法权限 + tenant 显式条件 + 业务归属校验”三层策略。
- 前端治理：建立 bundle budget，超过阈值时 CI 警告或失败，避免 UI 库逐步膨胀。
- 测试治理：后端覆盖率已有门槛，前端覆盖率仍是基线级，建议优先覆盖审批、成本、库存、付款这些高风险路径。

# 测试建议

- 单元测试：`CostLedgerService` 对 `'`、`\`、`%`、`_`、长 keyword、数字 keyword、跨表匹配做回归。
- 安全测试：新增一个测试或脚本断言 Service 层 `.apply()` / `.last()` 会被 SQL safety scan 捕获。
- 集成测试：成本台账 keyword 查询在 MySQL profile 下验证返回结果和 SQL 无异常。
- 回归测试：审批模板 `PROJECT_ROLE` 在不同租户、同项目角色名场景下不能串租户。
- 前端测试：收紧 CSP 后跑登录、路由切换、表格、图表页面冒烟。

# 验收标准

## 验收 1：成本台账 SQL 拼接风险已消除

- 验收目标：`CostLedgerService` 不再通过字符串拼接构造用户可控 SQL。
- 验收步骤：
  - 检查 `backend/src/main/java/com/cgcpms/cost/service/CostLedgerService.java` 中关键字查询实现。
  - 搜索该文件是否仍存在 `wrapper.apply("(" + whereClause + ")")` 或同等字符串拼接 SQL。
  - 使用包含 `'`、`\`、`%`、`_`、长字符串、纯数字的 keyword 执行成本台账查询测试。
- 通过标准：
  - 用户输入通过 MyBatis/MyBatis-Plus 参数绑定进入 SQL。
  - 成本台账普通字段和跨表字段搜索结果符合原有业务语义。
  - 恶意字符输入不触发 SQL 语法异常，不扩大查询范围，不绕过租户边界。
- 必须通过的命令或测试：
  - `cd backend && .\mvnw.cmd --% -Dtest=CostLedgerServiceTest test -Djasypt.encryptor.password=dev-jasypt-key`
  - 若测试类名称不同，以实际新增的成本台账查询回归测试为准。
- 需要人工确认的事项：跨表 keyword 搜索范围是否与产品预期一致。

## 验收 2：SQL 安全扫描覆盖 Service 层风险

- 验收目标：Service 层 `.apply()`、`.last()`、原始 SQL 拼接不能绕过 CI 安全门禁。
- 验收步骤：
  - 检查 `scripts/check-sql-safety.ps1` 的扫描范围。
  - 确认它扫描 `backend/src/main/java/**/*.java`，而不是只扫描 mapper 包或 SQL 注解。
  - 使用一个临时或测试夹具中的 `.apply()` 风险样例验证脚本会失败。
- 通过标准：
  - 未豁免的 `.apply()` / `.last()` 会被脚本报告。
  - 合理的服务端枚举或固定 SQL 片段必须带明确豁免注释和原因。
  - CI 中 `sql-safety-scan` 能阻断新增风险。
- 必须通过的命令或测试：
  - `powershell -ExecutionPolicy Bypass -File scripts\check-sql-safety.ps1`
- 需要人工确认的事项：现有 false positive 的豁免理由是否足够具体。

## 验收 3：生产 CSP 收紧后核心页面可用

- 验收目标：生产 CSP 至少移除 `unsafe-eval`，并尽量减少 `unsafe-inline`。
- 验收步骤：
  - 检查 `frontend-admin/nginx.conf` 的 `Content-Security-Policy`。
  - 使用生产构建访问登录页、仪表盘、成本台账、库存、审批、表格重页面。
  - 检查浏览器控制台是否出现 CSP 阻断导致的功能异常。
- 通过标准：
  - `script-src` 不再包含 `unsafe-eval`。
  - 核心页面正常加载、路由切换正常、表格和图表可用。
  - 如仍保留 `unsafe-inline`，需记录保留原因和后续收敛计划。
- 必须通过的命令或测试：
  - `cd frontend-admin && pnpm build`
  - 关键页面浏览器冒烟测试。
- 需要人工确认的事项：是否存在必须保留 inline style/script 的第三方组件约束。

## 验收 4：审批项目角色解析显式校验租户

- 验收目标：`PROJECT_ROLE` 审批人解析不依赖隐式租户上下文。
- 验收步骤：
  - 检查 `ApproverResolver.resolveProjectRole` 是否接收并使用 `tenantId`。
  - 构造不同租户下相同 `projectId` 或相同 `roleCode` 的成员测试数据。
  - 提交审批，验证只解析当前租户项目成员。
- 通过标准：
  - 查询条件显式包含 `tenantId`。
  - 非当前租户成员不会成为审批任务接收人。
- 必须通过的命令或测试：
  - `cd backend && .\mvnw.cmd --% -Dtest=ApproverResolverTest test -Djasypt.encryptor.password=dev-jasypt-key`
  - 若测试类名称不同，以实际新增的审批人解析回归测试为准。
- 需要人工确认的事项：历史项目成员数据是否存在缺失 `tenant_id` 的脏数据。

## 验收 5：pnpm 配置迁移后构建可复现

- 验收目标：pnpm 11 能读取补丁依赖和构建依赖配置。
- 验收步骤：
  - 将 `package.json` 中的 pnpm 配置迁移到 `pnpm-workspace.yaml`。
  - 重新安装依赖并构建。
  - 确认不再出现 `The "pnpm" field in package.json is no longer read by pnpm` 警告。
- 通过标准：
  - `pnpm install --frozen-lockfile` 成功。
  - `pnpm build` 成功且不再出现 pnpm 配置忽略警告。
  - `ant-design-vue@4.2.6` 补丁仍被正确应用。
- 必须通过的命令或测试：
  - `cd frontend-admin && pnpm install --frozen-lockfile`
  - `cd frontend-admin && pnpm build`
- 需要人工确认的事项：补丁文件是否仍与当前 lockfile 中的依赖版本匹配。

# 最终建议

暂不建议上线；建议修改后合并。

当前主要阻断点不是整体架构不成熟，而是存在明确代码证据的 Service 层 SQL 拼接风险，并且安全扫描门禁漏扫该风险。修复 P0 问题后，可以进入下一轮 targeted regression：成本台账查询测试、SQL safety scan、后端 `verify`、前端 build/test 和关键 E2E。
