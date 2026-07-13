# cgc-pms 全量审计与根因闭环报告（2026-07-12～2026-07-13）

## 最终裁决

- **整改结论：通过（本地与已授权远端治理范围）**
- **原报告问题：7 项均已完成根因闭环**
- **本地质量门禁：通过**
- **是否可直接上线：否**
- **上线阻塞：是，仅剩同一待合并提交的远端 required checks 尚未产生**
- **审计对象：** `develop/1.5`，整改基线 HEAD `3f654dd20894cca754b29492a179671a84e2d806`
- **执行边界：** 未连接生产环境或生产数据库，未发布；本地整改完成后用户已于 2026-07-13 授权提交并推送 `develop/1.5`，进入同一待合并 SHA 的远端 CI 取证阶段。

本轮已经关闭原审计中的后端测试污染、陈旧菜单 ID 契约、前端 lint、前端测试契约、SQL 安全标记、E2E/浏览器环境和分支保护缺口。当前本地证据支持提交与远端 CI；提交/push 已获授权，但在目标 SHA 的 11 个 required checks 实际完成前，不能提前给出上线通过结论。

## 原阻塞项闭环

| 编号 | 根因 | 实施 | 复验证据 | 状态 |
| --- | --- | --- | --- | --- |
| B-01 | 全量测试共享 Spring 缓存上下文和内存限流计数器，非限流测试被 429 污染 | 测试 classpath 默认关闭全局写限流；限流专项测试显式开启；新增默认值回归测试 | 限流/控制器定向 40 tests 通过；最终 Maven `verify` 1723 tests、0 failures、0 errors | 已关闭 |
| B-02 | 测试把菜单数字 ID 956/957/958 当作永久契约，但迁移已合法复用 | 删除陈旧 ID 不存在断言，保留权限码与角色授权断言 | `FundAccountMapperTest` 与租户边界组合 17 tests 通过；财务角色仍无 `file:*` | 已关闭 |
| B-03 | 库存页解构了未使用的 `fetchKpi`、`fetchWarehouses`，并存在 413 条纯 Prettier 告警 | 删除两个未使用绑定；对 48 个命中文件执行确定性 ESLint/Prettier 自动修复；将 5 个格式敏感源码契约改为分项语义断言 | `pnpm lint:check` 0 error、0 warning；格式化后全量 505 tests、构建与 E2E 通过 | 已关闭 |
| B-04 | 3 个前端静态契约落后于现实现：新增菜单、日期列工厂、采购逾期指标拆分 | 更新断言为当前稳定语义 | 目标 3 files / 23 tests 通过；全量 91 files / 505 tests 通过 | 已关闭 |
| B-05 | 两处固定 SQL 字面量安全，但缺少扫描器支持的同行说明 | 为 `.apply("1 = 0")` 添加 `SQL-SAFETY: fixed-sql-fragment` | `scripts/check-sql-safety.ps1` 通过 | 已关闭 |
| B-06 | 主机 Playwright 浏览器缺失、旧 `node_modules` ACL 损坏，且两个 E2E 只接受“有表格”而拒绝合法空态 | 重建主机依赖；支持 `PLAYWRIGHT_EXECUTABLE_PATH` / 可选 channel；修正付款与审批空态契约；使用用户提供的 Chrome 149 | UI smoke 7/7；内置浏览器到达 `/dashboard`，标题、驾驶舱、导航、卡片和列表内容可读 | 已关闭 |
| B-07 | `master` 未约束管理员且未要求对话解决 | 启用 `enforce_admins` 与 `required_conversation_resolution` | 远端复读：两者均为 `true`；11 checks、`strict=true`、禁止 force push/delete 保持不变 | 已关闭 |

## 补充发现与闭环

### 后端日期型测试夹具漂移

首次整改后全量 `verify` 稳定暴露 `DashboardProjectBusinessServiceTest` 2 项失败。夹具把合同到期日固定为“本月 12 日”，从每月 13 日起不再属于未来 30 天，导致测试随日期变化。现将到期日限定为“今天至当月末之间、最多未来 10 天”，目标 14 tests 与最终全量均通过。

### 结算金额直接覆盖

新增 `StlSettlementWriteServiceAmountTest`，通过公开 `create` 入口验证：

- 合同额 `1000.00`
- 变更额 `100.00`
- 计量额 `200.00`
- 已付额 `300.00`
- 扣款额 `50.00`
- 最终金额 `1250.00`
- 5% 质保金 `62.50`
- 未付金额 `887.50`
- 汇总查询使用正确的 `tenantId` 与 `contractId`

该测试与结算 Service/Controller 组合 19 tests 通过，金额快照缺口已关闭。

### 构建后 JAR 供应链扫描

原 `trivy fs backend` 只能解析预构建 `pom.xml`。本轮实测发现，即使把 fat JAR 直接交给 `trivy fs`，也会返回 `Number of language-specific files=0`；退出码 0 并不代表 JAR 已被扫描。

根据 Trivy 的目标覆盖语义，JAR/WAR 属于 post-build 目标，应使用 `rootfs`/image 扫描。CI 已在 `supply-chain-security` 下载构建产物后增加：

```text
trivy rootfs --scanners vuln --pkg-types library --severity HIGH,CRITICAL --exit-code 1 /workspace
```

本地等价验证识别 `cgc-pms-backend.jar`，`Number of language-specific files=1`，HIGH/CRITICAL 为 0。工作流 YAML 已由项目现有 Prettier YAML 解析器成功解析，并通过缓存/扫描步骤结构与顺序断言；SBOM 与 provenance attestation 顺序未破坏。

为降低 Trivy Java DB 在受限网络下重复下载的影响，`supply-chain-security` 现在按 UTC 日期缓存 `.trivy-cache`，并使用历史日期前缀回退。冷缓存仍会从官方数据库下载一次，但同日重复运行和后续恢复可复用数据库，不引入第三方镜像信任边界。

## 最终验收证据

| 验收域 | 命令/方式 | 结果 |
| --- | --- | --- |
| 后端全量 | `backend/mvnw.cmd -q verify` | 184 suites；1723 tests；0 failures；0 errors；1 skipped；通过 |
| MySQL 8 migration | 独立 `mysql:8.0`，`127.0.0.1:33307`，`FlywayMySqlSmokeTest` | 通过；容器已移除 |
| 前端 lint | `pnpm lint:check` | 0 error、0 warning；通过 |
| 前端类型 | `pnpm type-check` | 通过 |
| 前端构建 | `pnpm build` | 通过 |
| bundle | `pnpm check:bundle-size` | 最大 JS 457.14 KiB；通过 |
| 前端测试 | `pnpm test:coverage` | 91 files、505 tests 全过 |
| 依赖审计 | `pnpm audit --audit-level moderate --registry=https://registry.npmjs.org` | ECharts 升级后 0 vulnerabilities；通过 |
| SQL 安全 | `scripts/check-sql-safety.ps1` | 通过 |
| 源码依赖扫描 | Trivy `fs` | `pom.xml` HIGH/CRITICAL 0 |
| 构建产物扫描 | Trivy `rootfs` | JAR 被识别；HIGH/CRITICAL 0 |
| E2E | 用户 Chrome 149 + `PLAYWRIGHT_EXECUTABLE_PATH` | 7/7 通过 |
| 运行态 | 8080 health、5173、dev-login | 200/UP、200、最终 `/dashboard` |
| 内置浏览器 | `/api/auth/dev-login?redirect=/dashboard` | 到达 dashboard；页面完整渲染 |
| CI 配置 | Prettier YAML 解析与缓存/扫描步骤结构断言 | 通过；项目未安装可直接调用的 `js-yaml`，首次验证缺少该可选模块按 `tool_config` 分类 |
| 分支保护 | GitHub API 变更后复读 | 管理员强制、对话解决已启用；原 11 checks 保持 |
| Git 差异 | `git diff --check` | 通过 |

## 架构与安全复核

- CodeGraph 用于定位限流、Dashboard 日期逻辑和相关测试影响；当 Dashboard 测试文件召回不足时，按规则使用 `rg` 对明确类名/方法补查，归类为工具召回不足，不等同于代码不存在。
- `codebase-memory-mcp` 只读交叉核验目的为确认限流过滤器接入安全链、结算写服务的调用/测试覆盖和 Dashboard 跨层影响；命中 `SecurityConfig -> GlobalWriteRateLimitFilter`、结算 Controller/Service/既有集成测试及新增金额测试，未执行索引写入或规则改写。
- SQL 两处变化只增加固定片段说明，不放宽规则、不引入用户输入拼接。
- 测试态关闭全局写限流仅位于 `src/test/resources`；生产默认与生产配置未被关闭。
- 分支保护完整更新保留了 11 个 app 绑定 checks，没有因启用对话解决而丢失 required check。

## 环境与临时产物

- 用户提供 `D:\用户\下载\chrome-win64.zip`，SHA-256：`EBC0C2B75E2EA98151A7F18FF47037BFCBAB44A8660E79B9FFA6520F9B7607AB`；识别版本 149.0.7827.55。
- 浏览器解压在 `.codex-autopilot/runs/chrome-win64-user-149`，Trivy 数据库缓存位于 `.codex-autopilot/runs/trivy-cache`，均为被忽略的本地临时产物，不进入正式交付。
- 损坏 ACL 的旧前端依赖已隔离到 `.codex-autopilot/runs/frontend-node_modules-acl-broken-20260713`；当前 `frontend-admin/node_modules` 已重建并可用。已安全校验路径并尝试清理，可访问文件已移除，残留仍因原 ACL 拒绝删除；该忽略目录不参与运行、构建或版本管理。
- E2E 自动刷新的跟踪截图已恢复，未把本轮动态页面数据写入正式 diff。

## 远端 CI 与上线边界

- `master` 的 11 个 required checks 仍为：`backend-test`、`backend-test-mysql`、`backend-dependency-scan`、`frontend-lint`、`type-check`、`frontend-build`、`frontend-test`、`frontend-dependency-audit`、`sql-safety-scan`、`e2e`、`supply-chain-security`。
- PR #334 首轮 `backend-test` 在 `Phase3IntegrationTest.test03_dynamicCostFormula` 暴露公式漂移：科目行使用 `confirmedRevenue-dynamicCost`，而 V27 回填、项目级汇总、批量汇总和既有契约均使用 `contractIncome-dynamicCost`。该失败分类为真实质量/数据一致性问题，不是工具或环境波动。
- 已恢复科目行公式契约，并在 `CostSummaryServiceTest.testRefreshSummaryDynamicCostReportAmounts` 固定“合同收入与确认收入可区分”的回归数据。修复前该断言稳定失败，修复后目标测试与 `Phase3IntegrationTest` 共 8 项通过，后端全量 `verify` 再次通过。
- 公式修复提交 `27410fa2` 的 CI 全量运行 11 个 required checks 全绿，`build-summary` 同步成功；GitHub 复读结果为 `mergeable=MERGEABLE`、`mergeStateStatus=CLEAN`。
- Node 24 action 治理首轮验证发现 `pnpm/action-setup@v6` 的 `version: 11` 会范围漂移并在自安装更新时失败，分类为工具配置兼容性问题；改为读取 `frontend-admin/package.json` 中带校验哈希的精确 `pnpm@11.0.9` 后根因解除。
- 配置提交 `b455ca53` 的 11 个 required checks 全绿，E2E、artifact 上传下载、Trivy 缓存与构建后供应链扫描均通过；逐 job 复读 12 条 annotation，Node 20 命中为 0，GitHub 状态恢复 `MERGEABLE/CLEAN`。
- 因此：**全量审计整改通过，远端合并门禁通过；PR 可进入人工审阅/合并，但本次授权不包含执行合并。**

## 依赖与格式治理补充闭环

- `echarts 5.6.0` 命中 GHSA-fgmj-fm8m-jvvx（moderate XSS）。已同步升级为 `echarts 6.1.0` 与兼容的 `vue-echarts 8.0.1`，避免只升级 peer 不兼容的单一包。
- 升级后 `pnpm audit --audit-level moderate` 返回 0 vulnerabilities；图表相关 12 tests、全量 505 tests、类型检查、生产构建、bundle 与 Chrome E2E 均通过。
- 413 条格式告警全部来自 `prettier/prettier`，分布于 48 个文件；自动修复后 lint 为 0 error / 0 warning。格式化暴露的 5 个单行源码字符串断言已改为格式无关的关键语义断言，目标 41 tests 与全量测试均通过。

## 剩余风险

1. Trivy Java DB 冷缓存首次下载约 891.5 MiB，国内网络较慢；CI 已加入按日缓存和历史缓存回退，因此该风险只剩首次冷启动时延，不影响扫描正确性或已完成的 0 命中结论。
