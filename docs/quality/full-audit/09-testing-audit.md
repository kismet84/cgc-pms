# 阶段 10：测试体系审计

## 规模与本轮结果

- 后端测试资源/源码文件：424；前端单测文件：89；E2E 文件：40。
- 后端 `mvnw verify`：见命令日志；覆盖单元、Spring 集成、数据库、并发、契约与 JaCoCo 门禁。
- 前端：89 个 Vitest 文件、644 个测试通过；类型、契约类型、构建、路由账本和 bundle 通过。
- CI：包含 H2/MySQL、顺序敏感、依赖扫描、前端单测、浏览器契约、SQL 安全、Flyway 不可变与供应链制品证明。

## TEST-001（并入 SEC-001，已关闭）

- 等级/状态：原 P2，2026-08-20 已补齐。
- 缺口：没有覆盖服务端模板事件属性净化与两个无 sandbox 预览面的自动化安全回归；现有测试未阻止 `onerror` payload。
- 影响：构建、单测和普通模板校验均可通过，同时保留 `SEC-001`。
- 验收：新增服务端允许列表参数化测试、前端组件 sandbox 断言和浏览器 payload 不执行测试，并纳入默认 CI。

结果：后端参数化测试覆盖事件属性变体；前端单测断言两个 iframe 的最小 sandbox；真实浏览器检查 sandbox DOM、交互与 console。该项继续并入 `SEC-001`，不重复计数。

## DEP-001（已补本地证据）

- 等级/状态：原 P2，本地扫描通过。
- 事实：前端 `pnpm audit --audit-level high` 为 0 漏洞；固定摘要 Trivy 0.65.0 使用当前数据库执行 CI `backend-dependency-scan` 等价 manifest 扫描，HIGH/CRITICAL 为 0。POM 中由 BOM 管理的空版本依赖由 Trivy 明确提示不能在该阶段展开。
- 边界：本地 JAR 二次扫描需要首次下载 908 MiB Java DB；当前镜像链估算 7–14 小时，按 `environment_prerequisite` 停止。受保护 CI 的 `supply-chain-security` 必须对准确 JAR 重跑，本地 manifest 结果不替代该门禁。

详细场景见 [测试缺口矩阵](09a-test-gap-matrix.md)。
