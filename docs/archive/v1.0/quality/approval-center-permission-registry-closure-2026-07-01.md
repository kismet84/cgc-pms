# 第 9 条主线质量归档：权限与入口只读治理及审批入口注册收口

## 1. 最终结论

- 结论：通过
- 阻塞/非阻塞：非阻塞
- 是否建议关闭第 9 条主线：建议关闭
- 归档日期：2026-07-01

第 9 条主线已按正式版候选计划完成最小生产化收口。本线实际完成范围限定为“只读权限治理 + 审批中心三类业务入口 registry/helper 收口”，未扩展为可编辑权限平台、全站入口注册体系或新增业务类型接入。

本线不新增权限配置表、不新增入口注册表、不新增 workflow 授权聚合接口、不新增目标业务权限预判接口，也不让 workflow 详情聚合目标业务详情。`sys_menu.perms` 仅作为当前运行态鉴权事实来源继续使用，不宣称为完整权限字典。

## 2. 范围核对

### 2.1 已完成范围

1. 权限治理侧：
   - 新增系统管理下只读 `权限清单` 页面。
   - 清单页展示 `权限码 / 菜单名称 / 路径 / 来源/备注 / 角色绑定`。
   - 权限清单口径基于现有菜单权限数据，不开放在线编辑。
2. 审批入口侧：
   - 审批中心三类冻结业务入口已统一收口到 registry/helper。
   - 覆盖业务类型：合同审批、采购申请、分包计量。
   - 统一入口展示、目标路由、权限码与无权限禁用口径。
3. 回归侧：
   - 第 8 条账号矩阵核心边界已回归：admin、workflow-only、cc-readonly、non-participant。

### 2.2 明确未纳入范围

1. 不接付款申请或更多 workflow 业务类型。
2. 不做可编辑权限配置平台。
3. 不做全站入口注册体系。
4. 不新增权限配置中心表。
5. 不新增入口注册表。
6. 不新增 workflow 授权聚合或目标业务授权预判。
7. 不做第二租户、复杂 `data_scope`、项目级强隔离专项。
8. 不做后端 authority 与 `sys_menu.perms` 全量一次性对齐专项。

## 3. 前置结论归档

| 小包 | 结论 | 依据 |
| --- | --- | --- |
| 前端小包 2/3 | 通过 | 审批入口 registry/helper 已统一收口；系统管理下新增只读 `权限清单` 页面；前端定向 Vitest 19/19 通过；`pnpm build` 通过 |
| 后端复核 | 通过 | 当前零后端业务代码改动成立；仅当前端确认现有菜单接口不足时，才允许最小只读接口兜底 |
| 数据库复核 | 通过 | 当前零 migration 成立；若后续必须修复，仅限 `sys_menu/sys_role_menu` 单点修复 |
| 安全复核 | 通过 | 未引入授权聚合、可编辑权限平台、目标业务权限预判或 workflow 目标业务详情聚合 |
| 运维刷新 | 通过 | 已仅重启 `cgc-pms-frontend-dev`，等待 190 秒后 `5173` 命中新版 `workflowBusinessEntryRegistry`、`权限清单`、`system/permissions` |
| 测试验收 | 通过 | 浏览器证据目录 `D:/projects-test/cgc-pms/output/playwright/permission-registry-acceptance-20260701/` |

## 4. 测试与证据

证据目录：

`D:/projects-test/cgc-pms/output/playwright/permission-registry-acceptance-20260701/`

关键证据文件：

1. `01-admin-permissions-page.png`
2. `02-admin-contract-entry.png`
3. `03-admin-purchase-entry.png`
4. `04-admin-submeasure-entry.png`
5. `05-workflow-only-contract.png`
6. `06-workflow-only-purchase.png`
7. `07-workflow-only-submeasure.png`
8. `08-cc-readonly-list-detail.png`
9. `09-non-participant-todo.png`
10. `evidence.json`

`evidence.json` 只读核对结果：

| 验收项 | 结果 |
| --- | --- |
| 权限清单页 | `http://localhost:5173/system/permissions` 正常加载；表头包含 `权限码 / 菜单名称 / 路径 / 来源/备注 / 角色绑定` |
| admin 合同入口 | 入口可点击，`okUrl=true`、`okApi=true`、`okText=true` |
| admin 采购申请入口 | 入口可点击，`okUrl=true`、`okApi=true`、`okText=true` |
| admin 分包计量入口 | 入口可点击，`okUrl=true`、`okApi=true`、`okText=true` |
| workflow-only 合同负向 | 入口 disabled，未命中目标业务接口，无目标业务字段泄露 |
| workflow-only 采购申请负向 | 入口 disabled，未命中目标业务接口，无目标业务字段泄露 |
| workflow-only 分包计量负向 | 入口 disabled，未命中目标业务接口，无目标业务字段泄露 |
| cc-readonly | `/approval/cc` 列表与详情可见，未出现 `同意/驳回/撤回/重提` |
| non-participant | 无关实例不可见；详情探针 `HTTP 200 + code=0 + data=null` |

## 5. 关闭条件核对

| 关闭条件 | 结论 |
| --- | --- |
| 只读权限清单口径自洽 | 通过 |
| 三类业务入口 registry/helper 统一 | 通过 |
| admin 任一入口不回退 | 通过 |
| workflow-only 三类负向不泄露目标业务字段 | 通过 |
| cc-readonly 只读不回退 | 通过 |
| non-participant 不可见或 `success + null` 不回退 | 通过 |
| 不新增权限配置表、入口注册表、workflow 授权聚合 | 通过 |
| 不接付款申请或更多业务类型 | 通过 |

## 6. 代码质量结论

本线采取的是符合当前规模的最小实现路径：复用现有菜单权限事实来源与前端审批入口 helper，避免新增权限配置中心表、入口注册表、插件式注册框架和 workflow 聚合授权接口。该选择降低了迁移风险、误授权风险、回滚成本和验收矩阵膨胀风险。

当前只读权限清单承担治理视图职责，不承担授权边界职责。审批入口 registry/helper 仅用于展示与导航，不替代目标业务模块接口鉴权。目标业务详情权限仍由合同、采购申请、分包计量等业务模块自身接口兜底。

## 7. 剩余风险与不回灌项

以下事项不作为第 9 条主线阻塞项，不回灌本线：

1. `sys_menu.perms` 不是完整权限字典，仅作为运行态鉴权事实来源。
2. `84` 个 authority 缺口、`11` 个历史残留码、`6` 条未绑定 submit 权限继续列为治理观察项，不在本线一次性修复。
3. 付款申请或更多业务类型接入属于后续新主线或增强包。
4. 第二租户、复杂 `data_scope`、项目级强隔离属于后续安全/权限专项。
5. 可编辑权限平台需要单独设计强鉴权、审计日志、回滚和高危权限保护，不能复用本线 MVP 通过结论。
6. 全站入口注册体系、插件注册表或跨模块入口平台不属于本线成果。

## 8. 最终建议

建议按通过 / 非阻塞关闭第 9 条主线。后续若推进权限字典治理、历史权限码清理、付款申请接入、复杂数据权限或可编辑权限平台，应另开独立主线，并重新定义安全边界、迁移策略和验收矩阵。

本次归档仅新增质量文档，未修改业务代码，未运行测试，未重启服务，未提交 git。
