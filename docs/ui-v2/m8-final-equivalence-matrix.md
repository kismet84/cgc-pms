# M8 最终路由、权限、API 与角色冻结矩阵

> 冻结日期：2026-07-27
> 基线：`master` / `2908dd70b336a522b69640f2883a722ac1e9a5a6` 加当前 M7/M8 未提交工作区
> 状态：`ISSUE-053-043 PASSED`

## 1. 权威来源

| 维度 | 权威来源 | 冻结规则 |
| --- | --- | --- |
| route name、URL、V2 处置 | `docs/ui-v2/route-migration-ledger.json` | 87 个 route name 唯一；`0/87/0`；变更后必须重新生成并通过漂移检查 |
| 导航、permission、adminOnly | `frontend-admin-v2/src/navigation/catalog.ts`、`frontend-admin-v2/src/router.ts` | 导航可见与路由可达分别校验；均不替代后端授权 |
| API 方法、路径、参数、错误 | `frontend-admin-v2/src/services/**`、`packages/frontend-contracts/src/**` | V2 只调用既有后端 API；业务事实、范围、金额、库存和状态由服务端裁决 |
| 角色 | 当前会话 `roles`、`permissions` 和路由元数据 | 普通角色按精确权限；`ADMIN/SUPER_ADMIN`仍需路由角色门；`superAdminOnly`仅允许`SUPER_ADMIN` |
| 验收 | `frontend-admin-v2/tests/unit/**`、`frontend-admin-v2/e2e/**`、阶段正式报告 | 单测固定契约；E2E 固定权限、深链、三视口、axe 和控制台；阶段报告固定业务边界 |

角色不建立静态“角色名称→全部菜单”副本。真实角色权限来自服务端会话；前端只消费 `roles`、`permissions` 和路由元数据，避免第二套授权事实。

## 2. 分域冻结矩阵

| 域 | 路由与权限 | V2 API 锚点 | 角色/范围门 | 当前验收锚点 |
| --- | --- | --- | --- | --- |
| 系统与全局 | 登录、403、404、个人、设置、帮助 | `auth.ts`、`account.ts`、`request.ts`、`health.ts` | 匿名/已登录分离；个人资料仅当前用户；改密撤销全部会话 | M1、M7 unit/E2E；`ISSUE-053-036/037` |
| 工作台 | 驾驶舱、审批工作台、预警、报表目录 | `dashboard.ts`、`workflow.ts`、`alerts.ts`、`reports.ts` | 多角色组合；项目型聚合按服务端可见项目；审批动作精确权限 | M2/M4-1 unit/E2E 和正式报告 |
| 项目履约 | 项目、计划、现场、质量安全、技术、收尾 | `projects.ts`、`delivery.ts`、`quality.ts`、`technical.ts`、`closeout.ts` | 租户、项目、对象存在和动作权限均由后端检查 | M3 unit/E2E 和正式报告 |
| 商务合约 | 合同、签证、投标、成本、预算、产值 | `commercial.ts` | 金额字符串只展示服务端事实；合同/项目范围和写侧状态由服务端裁决 | M4 unit/E2E 和正式报告 |
| 供应链与物资 | 招采、采购、验收、仓库、库存、领退料 | `supply-chain.ts` | 数量、库存、来源、状态均以服务端事实为准；禁止手工通用库存移动 | M5 unit/E2E 和正式报告 |
| 分包与结算 | 分包任务、计量、结算台账与详情 | `subcontract.ts`、`finance.ts` | 项目/合同/合作方范围；金额、来源、审批和并发由服务端裁决 | M6 unit/E2E 和正式报告 |
| 资金财务 | 付款、费用、收入回款、发票、日记账、预测、凭证、月结 | `finance.ts` | 金额、可付余额、归档、冲销、期间、会计状态由服务端裁决 | M6 unit/E2E 和正式报告 |
| 基础资料 | 合作方、组织、材料、成本科目 | `master-data.ts`、`cost-subject.ts` | 租户隔离、引用保护、版本和管理员连续性由后端校验 | M7 unit/E2E；`ISSUE-053-038/039` |
| 系统管理 | 流程、用户、角色、权限、字典、审计、模板、数据维护 | `workflow-process.ts`、`system-management.ts` | 精确权限+ADMIN/SUPER_ADMIN双门；数据维护再加`superAdminOnly`；审计只读 | M7 unit/E2E；`ISSUE-053-040/041/042` |

## 3. 静态门结果

- 路由台账：87 个命名路由，`LEGACY_ONLY=0 / V2_ACCEPTED=87 / V2_SOURCE_AVAILABLE=0`。
- Clean-room：203 个 V2 文件、16 个 contracts 文件扫描通过，Legacy Vue/CSS/store/layout import 为零。
- router/navigation：2 个文件、28 项通过。
- contracts type、V2 type、Lint、生产构建、包体门通过；构建 56 个 JS 资产，均低于 500 KiB。
- Lint 首轮发现 `m1-shell.spec.ts` 6 条纯 Prettier warning；格式化后复验为 0 error / 0 warning。

## 4. 变更控制

- route、permission、adminOnly、API 或角色语义变化必须同步权威来源、目标测试、台账和本矩阵。
- 新增或修改后端 API、权限码、金额、库存、审批、会计口径时停止 M8，另立决策。
- 本矩阵不授权正式入口切换、Legacy 退役、目标环境或生产发布。
