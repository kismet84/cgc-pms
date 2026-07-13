# 全量审计根因闭环修复设计

## 目标

修复 `docs/quality/code-audit-2026-07-12-full-develop-1.5.md` 中全部阻塞项和明确测试缺口，使当前分支在同口径后端、前端、SQL 安全、数据库迁移、供应链、E2E 与治理门禁下具备可复验的通过证据。

## 约束

- 不发布生产、不连接生产数据库、不自动 push。
- 不修改已应用的 Flyway migration。
- 不通过关闭生产安全能力、放宽扫描规则或删除有效断言换取绿灯。
- 仅重建仓库内已损坏且可再生成的前端依赖目录；操作前核对绝对路径和清理预览。
- GitHub 外部变更仅限已确认的 master 分支保护加固，并保留原配置作为回滚依据。

## 设计决策

### 1. 后端测试态限流隔离

生产和本地运行态继续启用全局写限流。测试 classpath 的默认配置关闭全局写限流，避免不同 Spring 集成测试共享 `FallbackRateLimitCounterStore` 造成 429 污染；`GlobalWriteRateLimitFilterTest` 显式重新启用并继续验证阈值、身份维度、白名单和响应契约。

验收要求：原 5 个失败套件与完整 Maven `verify` 均不再出现跨测试 429，限流专项测试仍能稳定触发 429。

### 2. 权限测试契约

删除 `FundAccountMapperTest` 对菜单数字 ID 956/957/958 的陈旧假设，改为只按权限码和角色权限关系验证。菜单 ID 已被后续 migration 合法复用，不再作为文件权限边界证据。

验收要求：财务角色仍不具备 `file:upload`、`file:query`、`file:delete`，现金日记账权限断言保持有效。

### 3. SQL 安全门禁

两处 `.apply("1 = 0")` 均为固定服务端 SQL 片段，用于在可见 ID 集为空时强制返回空结果。在同一行添加扫描器支持的 `SQL-SAFETY: fixed-sql-fragment` 说明，不修改查询语义，也不增加泛化豁免。

验收要求：SQL 安全脚本通过，且租户/项目可见性测试继续证明空权限集不会返回数据。

### 4. 前端 lint 与契约测试

- 从库存页面的 composable 解构中移除未使用的 `fetchKpi`、`fetchWarehouses`；composable 内部调用保持不变。
- 菜单测试纳入已上线的“现场日报”。
- 合同台账测试按公共 `buildDateColumn('signedDate', '签订日期', ...)` 语义验证，而非要求内联对象字面量。
- 采购驾驶舱测试按已拆分的 `lateCompletedCount` 与 `overdueIncompleteCount` 展示契约验证；后端保留 `overdueOrderCount` 汇总字段用于兼容，不强迫组件展示重复列。

验收要求：前端 lint 0 error，相关单测和完整 Vitest 全绿，类型检查与生产构建通过。

### 5. E2E 与本地依赖环境

安全删除并重建 ACL 损坏的 `frontend-admin/node_modules`，使用锁文件指定的 pnpm 版本安装依赖，再安装匹配版本的 Playwright Chromium。付款申请 E2E 验证页面容器、头部经营摘要，以及数据表格或合法空状态二者之一，不再把空数据状态误判为空白页。

验收要求：主机可直接读取 ESLint 入口并执行 lint；Playwright 完整 UI smoke 进入并完成全部用例，付款页无论有无数据均有明确可见结果。

### 6. 金额结算直接覆盖

为 `StlSettlementWriteService` 增加写服务回归测试，通过公开的创建或更新入口验证合同额、变更额、计量额、已付额、扣款额、最终金额、5% 质保金和未付额的组合计算，同时验证 tenantId/contractId 被正确传给查询汇总服务。

验收要求：测试先在缺少覆盖时建立预期，再在当前实现上证明金额快照公式；后续任何公式回退都会导致测试失败。

### 7. 供应链扫描覆盖

保留源码目录 Trivy 扫描，并在 `supply-chain-security` 使用构建后的 Spring Boot fat JAR 增加 HIGH/CRITICAL library vulnerability 扫描，避免仅依赖 Maven 声明解析。SBOM 与 provenance attestation 流程保持不变。

验收要求：工作流语法有效；本地对构建 JAR 的等价扫描通过；现有 SBOM/attestation job 依赖不被破坏。

### 8. GitHub 分支保护

对 master 启用 `enforce_admins` 与 `required_conversation_resolution`；保留 strict required checks、禁止 force push 和禁止删除。仓库为个人仓库且仅有所有者一个管理员，不配置组织型 push restrictions。变更前保存当前 API 响应，变更后重新读取并逐项核验。

验收要求：`enforce_admins.enabled=true`、`required_conversation_resolution.enabled=true`，11 个 required checks 未丢失。

### 9. 审计收口

重新执行原报告同口径验证并更新原审计报告，将每项阻塞标记为已解除或保留客观阻塞。只有全部必需门禁通过、E2E 有直接证据、分支保护已核验且 `git diff --check` 通过，才可裁决为通过和可上线。

## 回滚

- 代码、测试和 workflow 改动可按文件回退，不涉及数据库结构迁移。
- 前端依赖目录由锁文件重新生成，不作为版本化交付物。
- GitHub 分支保护回滚为变更前读取的原值：`enforce_admins=false`、`required_conversation_resolution=false`；required checks 列表始终保持不变。

## 非目标

- 不重构生产限流存储架构。
- 不改变结算金额业务公式。
- 不扩大付款、合同、菜单或驾驶舱产品功能。
- 不创建发布版本、不合并 master、不推送分支。
