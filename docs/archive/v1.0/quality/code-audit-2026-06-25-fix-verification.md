# 总体结论

本次验证针对 `docs/quality/code-audit-2026-06-25-pre-release.md` 中列出的 5 个 High 级阻断项进行复核。基于源码证据和本地门禁命令，5 个阻断项当前均可判定为已修复：生产 Nginx 代理配置已模板化并注入 `BACKEND_URL`，后端 `mvn verify` 通过，前端构建与覆盖率测试通过，生产 profile 下清库接口不注册，前端官方 high 级依赖审计无已知漏洞。

需要说明的是，子代理在并发执行 `pnpm test:coverage` 时曾遇到 `coverage/.tmp/coverage-1.json` 缺失；主流程清理覆盖率目录后独占重跑通过，因此该现象更符合多个覆盖率进程同时写同一目录导致的工程化竞争，而非代码功能仍未修复。当前不再保留“暂不建议合并/上线”的阻断结论，但上线前仍需在真实生产环境完成镜像启动、SSL 文件、生产环境变量和前端反向代理链路验收。

做得好的地方：本轮修复没有只绕过门禁，而是补齐了 Dockerfile 模板路径、Compose 环境变量、后端测试数据清理、生产 profile 保护和依赖锁定；对应的构建、测试、审计命令都可以复现。

# 评分

- 正确性：90/100
- 安全性：88/100
- 性能：85/100
- 可维护性：84/100
- 架构设计：82/100
- 测试与工程化：86/100
- 综合评分：86/100

# 风险统计

- Critical：0
- High：0
- Medium：2
- Low：2
- Info：2

# 关键问题

## 问题 1：并发执行前端覆盖率会竞争同一输出目录

- 类型：工程化 / 测试稳定性
- 严重程度：Medium
- 位置：`frontend-admin/coverage`
- 证据：
  - 子代理并发验证时 `pnpm test:coverage` 失败，错误为 `ENOENT: no such file or directory, open 'D:\projects-test\cgc-pms\frontend-admin\coverage\.tmp\coverage-1.json'`。
  - 主流程清理覆盖率目录后独占执行 `pnpm test:coverage` 通过：`38 passed (38)`、`179 passed (179)`，退出码 0。
- 问题说明：覆盖率输出目录是共享状态，同一工作区内多个 coverage 进程并发写入可能互相清理或移动临时文件。
- 影响：本地或 CI 中如果把同一前端工作区的 coverage 任务并发化，可能出现误报失败。
- 修复建议：CI 中禁止同一 workspace 并发执行 `pnpm test:coverage`；如确需并行，为每个任务配置独立 coverage 目录或独立 checkout。
- 示例修复代码：

```ts
// vitest.config.ts 示例：通过环境变量隔离覆盖率目录
coverage: {
  reportsDirectory: process.env.COVERAGE_DIR ?? 'coverage'
}
```

- 优先级：P1
- 收益：降低门禁偶发失败，避免误判修复状态。
- 成本：低，仅需 CI 串行化或增加环境变量。
- 风险：低，主要影响报告输出路径。

## 问题 2：前端覆盖率整体比例仍偏低，缺少质量阈值

- 类型：测试与工程化
- 严重程度：Medium
- 位置：`frontend-admin` 覆盖率报告
- 证据：
  - `pnpm test:coverage` 通过，但覆盖率汇总为 Statements 9.61%、Branches 10%、Functions 7.63%、Lines 9.68%。
- 问题说明：当前覆盖率命令能验证测试可运行，但没有形成有效的质量下限；大量页面、store、API 模块仍为 0 覆盖。
- 影响：后续改动可能在门禁通过的情况下破坏关键业务流程。
- 修复建议：不要一次性追求全量高覆盖；先给核心链路设置分阶段阈值，例如登录、权限、合同、发票、库存、请求拦截器模块优先补测试，并按季度提升阈值。
- 示例修复代码：

```ts
// vitest.config.ts 示例：先设置温和阈值，逐步抬高
coverage: {
  thresholds: {
    statements: 10,
    branches: 10,
    functions: 8,
    lines: 10
  }
}
```

- 优先级：P2
- 收益：把“能跑测试”推进到“测试能防回归”。
- 成本：中，需要补关键路径测试。
- 风险：中，阈值过高会阻塞交付，建议渐进提升。

## 问题 3：生产清库接口已禁用，但非生产环境仍保留高危能力

- 类型：安全 / 运维控制
- 严重程度：Low
- 位置：`backend/src/main/java/com/cgcpms/system/controller/SystemController.java`
- 证据：
  - `SystemController.java:18` 使用 `@Profile("!prod")`。
  - `SystemController.java:42` 仍保留 `DELETE /clear-database`，用于非生产环境。
  - `SystemControllerProfileTest.java:25` 验证 prod profile 下不注册 `systemController`，`SystemControllerProfileTest.java:40` 验证 local profile 下注册。
- 问题说明：生产暴露风险已解除，但非生产环境如果连接到真实数据源或共享演示库，仍可能误删。
- 影响：测试、演示、预发环境存在误操作风险。
- 修复建议：继续保留 `@Profile("!prod")`，并为非生产接口增加显式确认参数、审计日志和环境 banner 校验。
- 示例修复代码：

```java
@DeleteMapping("/clear-database")
public Result<Void> clearDatabase(@RequestParam String confirm) {
    if (!"CLEAR_NON_PROD_DATABASE".equals(confirm)) {
        throw new BusinessException("CONFIRM_REQUIRED", "需要显式确认");
    }
    systemService.clearDatabase();
    return Result.success();
}
```

- 优先级：P2
- 收益：减少非生产误删。
- 成本：低。
- 风险：低，需要同步前端调用参数。

# 已验证修复项

## 修复项 1：生产 Nginx `${BACKEND_URL}` 模板化

- 原严重程度：High
- 当前状态：已修复
- 证据：
  - `frontend-admin/Dockerfile:31`：`COPY nginx.conf /etc/nginx/templates/default.conf.template`
  - `deploy/docker-compose.prod.yml:257`：`BACKEND_URL: backend:8080`
  - `docker compose -f deploy/docker-compose.prod.yml config` 退出码 0，展开配置中 `frontend.environment.BACKEND_URL` 为 `backend:8080`。
- 验收结论：源码和 Compose 静态配置通过。真实容器内 `nginx -T` 与 `/api/actuator/health` 代理访问仍需在生产镜像启动后确认。

## 修复项 2：后端 `mvn verify` 发布门禁

- 原严重程度：High
- 当前状态：已修复
- 证据：
  - `backend/src/test/java/com/cgcpms/invoice/InvoiceValidationTest.java:64` 清理 `pay_invoice` 中固定 `pay_record_id` 数据。
  - `backend/src/test/java/com/cgcpms/invoice/InvoiceValidationTest.java:71` 清理 `pay_record` 固定种子数据。
  - `backend && .\mvnw.cmd verify "-Djasypt.encryptor.password=dev-jasypt-key"` 退出码 0。
  - `backend/target/surefire-reports` 汇总：132 个 XML 报告，1276 tests，0 failures，0 errors，0 skipped。
- 验收结论：已通过。

## 修复项 3：前端测试和覆盖率门禁

- 原严重程度：High
- 当前状态：已修复
- 证据：
  - `frontend-admin && pnpm test:coverage` 独占重跑退出码 0。
  - 结果：`38 passed (38)`、`179 passed (179)`。
  - 覆盖率汇总：Statements 9.61%、Branches 10%、Functions 7.63%、Lines 9.68%。
- 验收结论：命令门禁已恢复。覆盖率比例偏低属于后续质量改进，不阻断本轮修复验收。

## 修复项 4：生产环境清库接口禁用

- 原严重程度：High
- 当前状态：已修复
- 证据：
  - `backend/src/main/java/com/cgcpms/system/controller/SystemController.java:18`：`@Profile("!prod")`
  - `backend/src/test/java/com/cgcpms/system/controller/SystemControllerProfileTest.java:25`：断言 prod 下不包含 `systemController`。
  - `backend/src/test/java/com/cgcpms/system/controller/SystemControllerProfileTest.java:40`：断言 local 下包含 `systemController`。
  - 后端 `mvn verify` 已通过。
- 验收结论：已通过。

## 修复项 5：前端 high 级依赖审计

- 原严重程度：High
- 当前状态：已修复
- 证据：
  - `frontend-admin/pnpm-lock.yaml:8`：`form-data: ^4.0.6` override。
  - `frontend-admin/pnpm-lock.yaml:2857`：`axios@1.17.0` 依赖 `form-data: 4.0.6`。
  - `frontend-admin && pnpm audit --audit-level high --registry=https://registry.npmjs.org` 输出 `No known vulnerabilities found`，退出码 0。
- 验收结论：已通过。

# 必须修复

本轮原 5 个 High 级上线阻断项均已修复并通过当前验证。当前没有必须修复后才能合并的代码级阻断项。

# 建议优化

- 将 `pnpm test:coverage` 设置为同一 workspace 内串行执行，收益是消除 coverage 输出竞争；成本低；风险低。
- 为 `frontend-admin` 增加覆盖率阈值并分阶段提高，收益是提升回归防护；成本中；风险是短期可能增加补测工作量。
- 为非生产清库接口增加二次确认、审计日志和环境校验，收益是降低误删；成本低；风险低。
- 把 `pnpm audit --audit-level high --registry=https://registry.npmjs.org` 固化进 CI，收益是避免镜像源差异导致安全扫描失真；成本低；风险低。

# 长期演进建议

- 建立“发布门禁矩阵”：后端 `mvn verify`、前端 `type-check/build/test:coverage/audit/check:bundle-size`、Compose config、SQL 安全扫描全部作为合并前可复现门禁。
- 将生产环境验收拆成源码门禁和运行时门禁：源码门禁在 CI 执行，运行时门禁验证镜像、证书、环境变量、健康检查、Nginx 最终配置和关键 API。
- 对高危运维能力采用“环境隔离 + 双确认 + 审计日志 + 最小权限”的一致治理方式，不依赖单一注解。
- 逐步补齐核心业务链路测试，优先覆盖登录鉴权、合同、发票、库存、成本、审批和请求拦截器。

# 测试建议

- 单元测试：继续补充 `request.ts`、权限路由、登录重定向、状态标签和关键表单校验。
- 集成测试：保留并扩展 `InvoiceValidationTest` 的数据隔离用例，避免固定 ID 与种子数据冲突。
- 回归测试：每次发布前执行 `pnpm test:coverage`、`pnpm build`、`mvnw verify`。
- 安全测试：固定执行 `pnpm audit --audit-level high --registry=https://registry.npmjs.org`，并补充 prod profile 下敏感 Controller 不注册的测试。
- 部署测试：使用真实生产镜像执行 `docker compose config`、容器启动、`nginx -T`、前端 `/api/actuator/health` 反向代理访问。

# 验收标准

- `backend && .\mvnw.cmd verify "-Djasypt.encryptor.password=dev-jasypt-key"` 退出码 0。
- `frontend-admin && pnpm build` 退出码 0，且无 Vite 大 chunk 警告。
- `frontend-admin && pnpm test:coverage` 独占执行退出码 0。
- `frontend-admin && pnpm audit --audit-level high --registry=https://registry.npmjs.org` 输出 `No known vulnerabilities found`。
- `docker compose -f deploy/docker-compose.prod.yml config` 在必要环境变量存在时退出码 0，并展开 `frontend.environment.BACKEND_URL=backend:8080`。
- prod profile 下 `SystemController` 不注册，相关测试通过。
- 生产上线前人工确认：生产域名、SSL 证书、真实密钥、镜像 tag、数据库备份、回滚方案、Nginx 最终配置和 `/api/actuator/health` 代理链路。

# 最终建议

修改后合并。  

从当前本地源码和门禁结果看，上一轮 5 个 High 级阻断项已经全部修复；可以进入合并或预发布流程。不建议跳过真实生产镜像启动验收，尤其是 SSL 文件、生产环境变量和 Nginx 模板替换后的最终配置。
