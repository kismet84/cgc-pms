# 后端接口前端页面覆盖审查报告

> 日期：2026-07-03  
> 范围：`backend/src/main/java/**/*Controller.java` 与 `frontend-admin/src/router`、`frontend-admin/src/pages`、`frontend-admin/src/api/modules` 静态对齐审查  
> 口径：以后端 Controller 暴露的 REST endpoint 为基线，检查前端是否存在页面、组件入口或 API 调用覆盖。

## 1. 审查结论

后端当前共有：

| 指标 | 数量 |
|---|---:|
| Controller 数量 | 48 |
| Endpoint 数量 | 289 |
| 一级业务域数量 | 35 |
| 明确缺前端页面/功能入口的 Controller | 5 |
| 明确缺前端页面/功能入口的 Endpoint | 25 |

按“是否存在前端页面或功能入口”口径，当前有 **25 个后端接口没有前端页面/功能入口覆盖**，涉及 **5 个 Controller**。

如果按更严格的“必须有独立路由页面才算覆盖”口径，则另有 15 个嵌入式/支撑型接口无独立页面，严格口径下共 **40 个 endpoint 没有独立前端页面**。

## 2. 统计与匹配口径

### 2.1 后端接口统计规则

1. 统计范围：`backend/src/main/java/**/*Controller.java`。
2. 全局前缀：`backend/src/main/resources/application.yml` 中配置统一 servlet context path 为 `/api`。
3. 完整路径规则：`/api` + Controller 类级 `@RequestMapping` + 方法级映射注解。
4. endpoint 规则：一个 `HTTP method + full path` 计为一个 endpoint。
5. 纳入的方法级注解：
   - `@GetMapping`
   - `@PostMapping`
   - `@PutMapping`
   - `@DeleteMapping`
   - `@PatchMapping`
6. 源码声明口径未扣除运行期条件：
   - `SystemController` 受 `@Profile("!prod")` 限制。
   - `FileController` 受 `@ConditionalOnProperty(minio.enabled=true)` 限制。

### 2.2 前端覆盖判断规则

前端覆盖基于以下位置做静态对齐：

- 路由：`frontend-admin/src/router/index.ts`
- 页面：`frontend-admin/src/pages/**`
- API 模块：`frontend-admin/src/api/modules/**`
- 组件/Store/Composable 中的静态调用：`frontend-admin/src/**/*.{ts,vue}`

覆盖分为三类：

| 覆盖类型 | 说明 | 是否视为“已有前端覆盖” |
|---|---|---|
| 独立页面覆盖 | 有明确路由页面，如 `/project/list`、`/cost/summary` | 是 |
| 嵌入式功能覆盖 | 无独立路由，但嵌入其他页面或全局组件使用 | 是，但无独立页面 |
| API 模块存在但未被页面/组件引用 | 仅有 API 封装，未发现功能入口 | 否，视为缺页面/入口 |

## 3. 明确缺前端页面/功能入口的接口

以下 Controller 未发现对应前端页面、组件入口或有效调用入口，应优先核查是否为历史遗留、后台专用，还是前端漏接。

| 后端 Controller | Base path | Endpoint 数 | 前端现状 | 判断 |
|---|---|---:|---|---|
| `AccountingEntryController` | `/api/accounting-entry` | 5 | 未发现页面、API 模块、组件引用 | 缺前端 |
| `OperationAuditController` | `/api/audit-logs` | 1 | 未发现页面、API 模块、组件引用 | 缺前端 |
| `BidCostController` | `/api/bid-cost` | 7 | 未发现页面、API 模块、组件引用 | 缺前端 |
| `OverheadAllocationController` | `/api/overhead-allocation` | 5 | 未发现页面、API 模块、组件引用 | 缺前端 |
| `ContractRevenueController` | `/api/contract-revenue` | 7 | 有 `contractRevenue.ts` API 模块，但未发现页面/组件引用 | 缺页面/入口 |

合计：

```text
5 + 1 + 7 + 5 + 7 = 25 个 endpoint
```

## 4. 严格口径下无独立路由页面的接口

以下接口虽然没有独立前端路由页面，但已有嵌入式功能、全局组件或支撑型调用入口，因此不建议直接归为“完全缺前端”。

| 后端接口域 | Endpoint 数 | 前端现状 | 建议判断 |
|---|---:|---|---|
| `/api/contract-changes` | 6 | 通过 `ContractChangeList.vue` 嵌入合同详情页 | 有功能入口，无独立页面 |
| `/api/notifications` | 5 | 顶部通知铃铛组件使用 | 有全局入口，无独立页面 |
| `/api/files` | 4 | 发票上传、附件等跨域能力使用 | 支撑型接口，无需独立页面 |

严格口径下：

```text
明确缺入口 25 个 + 嵌入/支撑型无独立页面 15 个 = 40 个 endpoint
```

## 5. 容易误判但已有覆盖的接口

| 后端接口域 | Endpoint 数 | 前端覆盖情况 | 结论 |
|---|---:|---|---|
| `/api/cost-summary` | 3 | 已有 `/cost/summary` 页面和 `cost.ts` API 调用 | 已覆盖 |
| `/api/profile/preferences` | 2 | 设置页 `/settings` 直接调用 `/profile/preferences` | 已覆盖，但未封装 API 模块 |
| `/api/system/clear-database` | 1 | 系统数据管理页 `/system/data` 直接调用 | 已覆盖，但未封装 API 模块 |
| `/api/alerts` | 3 | `/alert` 页面通过 `stores/alert.ts` 间接调用 | 已覆盖 |
| `/api/dashboard` | 9 | `/dashboard` 页面通过 composable 间接调用 | 已覆盖 |

## 6. 后端接口总览

### 6.1 HTTP Method 分布

| Method | Endpoint 数 |
|---|---:|
| GET | 123 |
| POST | 78 |
| PUT | 53 |
| DELETE | 34 |
| PATCH | 1 |

### 6.2 按一级业务域分组的 endpoint 数量

| 一级业务域 | Endpoint 数 |
|---|---:|
| system | 31 |
| contracts | 20 |
| workflow | 19 |
| org | 16 |
| settlements | 16 |
| projects | 12 |
| inventory | 10 |
| cost-targets | 9 |
| dashboard | 9 |
| receipts | 9 |
| invoices | 8 |
| pay-applications | 8 |
| purchase-orders | 8 |
| purchase-requests | 8 |
| requisitions | 8 |
| sub-measures | 8 |
| var-orders | 8 |
| bid-cost | 7 |
| contract-revenue | 7 |
| cost-subjects | 7 |
| contract-changes | 6 |
| accounting-entry | 5 |
| materials | 5 |
| notifications | 5 |
| overhead-allocation | 5 |
| partners | 5 |
| sub-tasks | 5 |
| auth | 4 |
| files | 4 |
| profile | 4 |
| alerts | 3 |
| cost-ledger | 3 |
| cost-summary | 3 |
| pay-records | 3 |
| audit-logs | 1 |

## 7. 每个 Controller 的前端静态对齐情况

| Controller | Base path | Endpoint 数 | 前端静态对齐 |
|---|---|---:|---|
| `AccountingEntryController` | `/api/accounting-entry` | 5 | 未见 |
| `AlertController` | `/api/alerts` | 3 | 已见 |
| `OperationAuditController` | `/api/audit-logs` | 1 | 未见 |
| `AuthController` | `/api/auth` | 4 | 已见 |
| `BidCostController` | `/api/bid-cost` | 7 | 未见 |
| `CtContractChangeController` | `/api/contract-changes` | 6 | 已见 |
| `CtContractController` | `/api/contracts` | 10 | 已见 |
| `CtContractItemController` | `/api/contracts/{contractId}/items` | 5 | 已见 |
| `CtContractPaymentTermController` | `/api/contracts/{contractId}/payment-terms` | 5 | 已见 |
| `CostLedgerController` | `/api/cost-ledger` | 3 | 已见 |
| `CostSubjectController` | `/api/cost-subjects` | 7 | 已见 |
| `CostSummaryController` | `/api/cost-summary` | 3 | 已见 |
| `CostTargetController` | `/api/cost-targets` | 9 | 已见 |
| `DashboardController` | `/api/dashboard` | 9 | 已见 |
| `FileController` | `/api/files` | 4 | 已见 |
| `MatStockController` | `/api/inventory/stock` | 4 | 已见 |
| `MatWarehouseController` | `/api/inventory/warehouses` | 6 | 已见 |
| `InvoiceController` | `/api/invoices` | 8 | 已见 |
| `MdMaterialController` | `/api/materials` | 5 | 已见 |
| `NotificationController` | `/api/notifications` | 5 | 已见 |
| `OrgCompanyController` | `/api/org/companies` | 5 | 已见 |
| `OrgDepartmentController` | `/api/org/departments` | 6 | 已见 |
| `OrgPositionController` | `/api/org/positions` | 5 | 已见 |
| `OverheadAllocationController` | `/api/overhead-allocation` | 5 | 未见 |
| `MdPartnerController` | `/api/partners` | 5 | 已见 |
| `PayApplicationController` | `/api/pay-applications` | 8 | 已见 |
| `PayRecordController` | `/api/pay-records` | 3 | 已见 |
| `PmProjectController` | `/api/projects` | 7 | 已见 |
| `PmProjectMemberController` | `/api/projects/{projectId}/members` | 5 | 已见 |
| `MatPurchaseOrderController` | `/api/purchase-orders` | 8 | 已见 |
| `MatPurchaseRequestController` | `/api/purchase-requests` | 8 | 已见 |
| `MatReceiptController` | `/api/receipts` | 9 | 已见 |
| `MatRequisitionController` | `/api/requisitions` | 8 | 已见 |
| `ContractRevenueController` | `/api/contract-revenue` | 7 | API 模块存在，但未见页面/组件入口 |
| `StlSettlementController` | `/api/settlements` | 16 | 已见 |
| `SubMeasureController` | `/api/sub-measures` | 8 | 已见 |
| `SubTaskController` | `/api/sub-tasks` | 5 | 已见 |
| `PreferenceController` | `/api/profile/preferences` | 2 | 已见 |
| `ProfileController` | `/api/profile` | 2 | 已见 |
| `SysMenuController` | `/api/system/menus` | 6 | 已见 |
| `SysRoleController` | `/api/system/roles` | 6 | 已见 |
| `SystemController` | `/api/system` | 1 | 已见 |
| `SysUserController` | `/api/system/users` | 7 | 已见 |
| `SysDictDataController` | `/api/system/dict/data` | 6 | 已见 |
| `SysDictTypeController` | `/api/system/dict/types` | 5 | 已见 |
| `VarOrderController` | `/api/var-orders` | 8 | 已见 |
| `WorkflowController` | `/api/workflow` | 12 | 已见 |
| `WorkflowTemplateController` | `/api/workflow/templates` | 7 | 已见 |

## 8. 前端覆盖域摘要

前端现有业务页面或功能入口已经覆盖以下主要域：

- `dashboard`
- `contract`
- `contract-change`（嵌入式）
- `cost`
- `cost-subject`
- `cost-target`
- `variation`
- `settlement`
- `project`
- `partner`
- `org`
- `subcontract`
- `purchase`
- `receipt`
- `payment`
- `inventory`
- `requisition`
- `invoice`
- `material`
- `alert`
- `notification`（全局组件）
- `workflow/approval`
- `system-dict`
- `system-user-role-menu`
- `system-data`
- `auth/profile`

## 9. 建议后续处理

### 9.1 优先确认 5 个缺口 Controller 的业务定位

建议逐一确认以下 Controller 是否仍应对用户开放：

1. `AccountingEntryController`
2. `OperationAuditController`
3. `BidCostController`
4. `OverheadAllocationController`
5. `ContractRevenueController`

对每个 Controller 建议判断：

| 判断问题 | 处理建议 |
|---|---|
| 是否为当前业务必需？ | 若必需，补前端页面/菜单/API 引用 |
| 是否仅后台内部调用？ | 若是，补接口说明与权限约束，不一定补页面 |
| 是否历史遗留？ | 若无用，进入废弃/删除评估 |
| 是否权限敏感？ | 补权限点、菜单可见性和审计说明 |

### 9.2 优先级建议

| 优先级 | 模块 | 原因 |
|---|---|---|
| P1 | `ContractRevenueController` | 已有 API 模块但无页面入口，最像“前端漏接” |
| P1 | `OperationAuditController` | 审计日志通常应有系统管理查看入口 |
| P2 | `AccountingEntryController` | 可能属于财务核算闭环，需确认是否缺菜单 |
| P2 | `BidCostController` | 投标成本域有 7 个接口，缺页面可能影响完整业务链 |
| P2 | `OverheadAllocationController` | 间接费分摊域有 5 个接口，需确认是否并入成本页或单独建页 |

### 9.3 API 封装一致性建议

以下页面已有功能，但直接调用 request/service，建议后续统一封装到 `frontend-admin/src/api/modules`：

| 页面 | 当前直接调用 | 建议 API 模块 |
|---|---|---|
| `/settings` | `/profile/preferences` | `profile.ts` 或 `preferences.ts` |
| `/system/data` | `/system/clear-database` | `system.ts` |

## 10. 最终结论

当前更合理的业务可用性口径是：

> **真正需要补前端页面/功能入口的是 25 个 endpoint，涉及 5 个 Controller。**

如采用“必须有独立路由页面”的严格口径，则：

> **40 个 endpoint 没有独立前端页面。**

其中 15 个属于嵌入式或支撑型接口，不建议简单视为缺陷；应按业务入口和权限可见性进一步判断。
