# 第53条主线 UI V2 路由迁移台账

> 自动生成文件。源：`frontend-admin/src/router/index.ts`。修改路由后运行 `pnpm generate:route-ledger`；CI 使用 `pnpm check:route-ledger` 防漂移。

- 命名路由：87
- Legacy 路由视图引用：73
- Legacy 独立页面模块：65
- `LEGACY_ONLY`：86
- `V2_SOURCE_AVAILABLE`：1

| 域 | route name | URL | Legacy 视图 | V2 视图 | permission | adminOnly | 状态 | Stitch / 测试 / 验收 |
|---|---|---|---|---|---|---:|---|---|
| 系统与全局 | Login | /login | @/pages/login/index.vue | — | — | 否 | LEGACY_ONLY | — |
| 工作台 | Dashboard | /dashboard | @/pages/dashboard/index.vue | — | dashboard:view | 否 | V2_SOURCE_AVAILABLE | 用户已选新版经营驾驶舱视觉概念；M2 落地 |
| 系统与全局 | Forbidden | /403 | @/pages/error/403.vue | — | — | 否 | LEGACY_ONLY | — |
| 工作台 | ReportCatalog | /dashboard/reports | @/pages/report/catalog.vue | — | — | 否 | LEGACY_ONLY | — |
| 商务合约 | Contract | /contract | — | — | contract:query | 否 | LEGACY_ONLY | — |
| 商务合约 | ContractLedger | /contract/ledger | @/pages/contract/ContractLedgerPage.vue | — | contract:query | 否 | LEGACY_ONLY | — |
| 商务合约 | ContractCreate | /contract/create | @/pages/contract/ContractFormPage.vue | — | contract:add | 否 | LEGACY_ONLY | — |
| 商务合约 | ContractDetail | /contract/:id | @/pages/contract/ContractDetailPage.vue | — | contract:query | 否 | LEGACY_ONLY | — |
| 商务合约 | ContractEdit | /contract/:id/edit | @/pages/contract/ContractFormPage.vue | — | contract:edit | 否 | LEGACY_ONLY | — |
| 商务合约 | Cost | /cost | — | — | cost:query | 否 | LEGACY_ONLY | — |
| 商务合约 | CostLedger | /cost/ledger | @/pages/cost/ledger.vue | — | cost:ledger:query | 否 | LEGACY_ONLY | — |
| 商务合约 | CostSummary | /cost/summary | @/pages/cost/summary.vue | — | cost:summary:view | 否 | LEGACY_ONLY | — |
| 商务合约 | CostControl | /cost/control | @/pages/cost/control.vue | — | cost:control:query | 否 | LEGACY_ONLY | — |
| 商务合约 | CostSubject | /cost/subject | — | — | cost:query | 否 | LEGACY_ONLY | — |
| 商务合约 | CostSubjectTaxonomy | /cost/subject/taxonomy | @/pages/cost-subject/index.vue | — | cost:query | 否 | LEGACY_ONLY | — |
| 商务合约 | CostSubjectRules | /cost/subject/rules | @/pages/cost-subject/index.vue | — | cost:subject:rule:query | 否 | LEGACY_ONLY | — |
| 商务合约 | CostSubjectScope | /cost/subject/scope | @/pages/cost-subject/index.vue | — | cost:subject:scope:query | 否 | LEGACY_ONLY | — |
| 商务合约 | CostSubjectTrace | /cost/subject/trace | @/pages/cost-subject/index.vue | — | cost:subject:audit:query | 否 | LEGACY_ONLY | — |
| 商务合约 | CostTarget | /cost-target | — | — | cost:target:query | 否 | LEGACY_ONLY | — |
| 商务合约 | CostTargetList | /cost-target/index | @/pages/cost-target/index.vue | — | cost:target:query | 否 | LEGACY_ONLY | — |
| 商务合约 | CostTargetCreate | /cost-target/create | @/pages/cost-target/edit.vue | — | cost:target:add | 否 | LEGACY_ONLY | — |
| 商务合约 | CostTargetEdit | /cost-target/:id/edit | @/pages/cost-target/edit.vue | — | cost:target:edit | 否 | LEGACY_ONLY | — |
| 商务合约 | Variation | /variation | — | — | variation:order:query | 否 | LEGACY_ONLY | — |
| 商务合约 | VariationOrder | /variation/order | @/pages/variation/order.vue | — | variation:order:query | 否 | LEGACY_ONLY | — |
| 分包与结算 | Settlement | /settlement | — | — | settlement:query | 否 | LEGACY_ONLY | — |
| 分包与结算 | SettlementList | /settlement/list | @/pages/settlement/index.vue | — | settlement:query | 否 | LEGACY_ONLY | — |
| 分包与结算 | SettlementDetail | /settlement/:id | @/pages/settlement/detail.vue | — | settlement:query | 否 | LEGACY_ONLY | — |
| 项目履约 | Project | /project | — | — | project:query | 否 | LEGACY_ONLY | — |
| 项目履约 | ProjectList | /project/list | @/pages/project/index.vue | — | project:query | 否 | LEGACY_ONLY | — |
| 项目履约 | ProjectOverview | /project/:projectId/overview | @/pages/project/overview.vue | — | project:query | 否 | LEGACY_ONLY | — |
| 项目履约 | ProjectMembers | /project/:projectId/members | @/pages/project/members.vue | — | project:member:list | 否 | LEGACY_ONLY | — |
| 项目履约 | ProjectEdit | /project/:projectId/edit | @/pages/project/edit.vue | — | project:edit | 否 | LEGACY_ONLY | — |
| 基础资料 | Partner | /partner | @/pages/partner/index.vue | — | partner:query | 否 | LEGACY_ONLY | — |
| 项目履约 | SiteDailyLog | /site/daily-log | @/pages/site/daily-log.vue | — | site:daily:query | 否 | LEGACY_ONLY | — |
| 项目履约 | ProjectSchedule | /project-schedule | @/pages/project-schedule/index.vue | — | schedule:query | 否 | LEGACY_ONLY | — |
| 项目履约 | QualitySafety | /quality-safety | @/pages/quality-safety/index.vue | — | — | 否 | LEGACY_ONLY | — |
| 项目履约 | TechnicalManagement | /technical-management | @/pages/technical-management/index.vue | — | — | 否 | LEGACY_ONLY | — |
| 项目履约 | ProjectCloseout | /project-closeout | @/pages/project-closeout/index.vue | — | — | 否 | LEGACY_ONLY | — |
| 供应链与物资 | SupplierSourcing | /supplier-sourcing | @/pages/supplier-sourcing/index.vue | — | — | 否 | LEGACY_ONLY | — |
| 商务合约 | BidCost | /bid-cost | @/pages/bid-cost/index.vue | — | — | 否 | LEGACY_ONLY | — |
| 基础资料 | Org | /org | @/pages/org/index.vue | — | org:query | 否 | LEGACY_ONLY | — |
| 分包与结算 | Subcontract | /subcontract | — | — | subcontract:task:query | 否 | LEGACY_ONLY | — |
| 分包与结算 | SubcontractTask | /subcontract/task | @/pages/subcontract/task.vue | — | subcontract:task:query | 否 | LEGACY_ONLY | — |
| 分包与结算 | SubcontractMeasure | /subcontract/measure | @/pages/subcontract/measure.vue | — | subcontract:measure:query | 否 | LEGACY_ONLY | — |
| 供应链与物资 | Purchase | /purchase | — | — | purchase:order:query | 否 | LEGACY_ONLY | — |
| 供应链与物资 | PurchaseOrder | /purchase/order | @/pages/purchase/order.vue | — | purchase:order:query | 否 | LEGACY_ONLY | — |
| 供应链与物资 | PurchaseReceipt | /purchase/receipt | @/pages/receipt/index.vue | — | receipt:query | 否 | LEGACY_ONLY | — |
| 资金财务 | Payment | /payment | — | — | payment:app:query | 否 | LEGACY_ONLY | — |
| 资金财务 | PaymentApplication | /payment/application | @/pages/payment/index.vue | — | payment:app:query | 否 | LEGACY_ONLY | — |
| 资金财务 | ExpenseApplication | /payment/expense | @/pages/expense/index.vue | — | — | 否 | LEGACY_ONLY | — |
| 商务合约 | ProjectBudget | /budget | @/pages/budget/index.vue | — | — | 否 | LEGACY_ONLY | — |
| 资金财务 | FinanceOperations | /finance-operations | @/pages/finance-operations/index.vue | — | finance:operations:query | 否 | LEGACY_ONLY | — |
| 资金财务 | RevenueOperations | /revenue | @/pages/revenue/index.vue | — | — | 否 | LEGACY_ONLY | — |
| 商务合约 | ProductionMeasurement | /production-measurement | @/pages/production-measurement/index.vue | — | — | 否 | LEGACY_ONLY | — |
| 资金财务 | CashJournal | /cash-journal | @/pages/cash-journal/index.vue | — | cashbook:journal:query | 否 | LEGACY_ONLY | — |
| 资金财务 | AccountingEntry | /accounting-entry | @/pages/accounting-entry/index.vue | — | — | 否 | LEGACY_ONLY | — |
| 资金财务 | CashForecast | /cash-forecast | @/pages/cash-forecast/index.vue | — | — | 否 | LEGACY_ONLY | — |
| 资金财务 | FinancialClose | /financial-close | @/pages/financial-close/index.vue | — | — | 否 | LEGACY_ONLY | — |
| 供应链与物资 | Inventory | /inventory | — | — | inventory:warehouse:query | 否 | LEGACY_ONLY | — |
| 供应链与物资 | InventoryWarehouse | /inventory/warehouse | @/pages/inventory/warehouse.vue | — | inventory:warehouse:query | 否 | LEGACY_ONLY | — |
| 供应链与物资 | InventoryStock | /inventory/stock | @/pages/inventory/stock.vue | — | inventory:stock:list | 否 | LEGACY_ONLY | — |
| 供应链与物资 | InventoryTransaction | /inventory/transaction | @/pages/inventory/transaction.vue | — | inventory:transaction:list | 否 | LEGACY_ONLY | — |
| 供应链与物资 | InventoryPurchaseRequest | /inventory/purchase-request | @/pages/inventory/purchase-request.vue | — | purchase:request:list | 否 | LEGACY_ONLY | — |
| 供应链与物资 | InventoryMaterialRequisition | /inventory/material-requisition | @/pages/requisition/index.vue | — | requisition:query | 否 | LEGACY_ONLY | — |
| 资金财务 | Invoice | /invoice | @/pages/invoice/index.vue | — | invoice:query | 否 | LEGACY_ONLY | — |
| 基础资料 | Material | /material | — | — | material:query | 否 | LEGACY_ONLY | — |
| 基础资料 | MaterialDictionary | /material/dictionary | @/pages/material/dictionary.vue | — | material:query | 否 | LEGACY_ONLY | — |
| 工作台 | Alert | /alert | @/pages/alert/index.vue | — | alert:view | 否 | LEGACY_ONLY | — |
| 工作台 | Approval | /approval | — | — | workflow:task:query | 否 | LEGACY_ONLY | — |
| 工作台 | ApprovalTodo | /approval/todo | @/pages/approval/todo.vue | — | workflow:task:query | 否 | LEGACY_ONLY | — |
| 工作台 | ApprovalDone | /approval/done | @/pages/approval/todo.vue | — | workflow:task:query | 否 | LEGACY_ONLY | — |
| 工作台 | ApprovalCc | /approval/cc | @/pages/approval/todo.vue | — | workflow:cc:query | 否 | LEGACY_ONLY | — |
| 工作台 | ApprovalMine | /approval/mine | @/pages/approval/todo.vue | — | workflow:instance:query | 否 | LEGACY_ONLY | — |
| 工作台 | ApprovalProcess | /approval/process | @/pages/approval/process.vue | — | workflow:process:query | 是 | LEGACY_ONLY | — |
| 工作台 | ApprovalDetail | /approval/:instanceId | @/pages/approval/detail.vue | — | workflow:instance:query | 否 | LEGACY_ONLY | — |
| 系统与全局 | System | /system | — | — | system:dict:query | 是 | LEGACY_ONLY | — |
| 系统与全局 | SystemDict | /system/dict | @/pages/system/dict/index.vue | — | system:dict:query | 是 | LEGACY_ONLY | — |
| 系统与全局 | SystemUsers | /system/users | @/pages/system/users/index.vue | — | system:user:query | 是 | LEGACY_ONLY | — |
| 系统与全局 | SystemData | /system/data | @/pages/system/data/index.vue | — | system:data:query | 是 | LEGACY_ONLY | — |
| 系统与全局 | RoleManagement | /system/roles | @/pages/system/roles/index.vue | — | system:role:query | 是 | LEGACY_ONLY | — |
| 系统与全局 | SystemPermissions | /system/permissions | @/pages/system/permissions/index.vue | — | system:permission:query | 是 | LEGACY_ONLY | — |
| 系统与全局 | SystemAudit | /system/audit | @/pages/system/audit/index.vue | — | audit:query | 否 | LEGACY_ONLY | — |
| 系统与全局 | DocumentTemplateManagement | /system/document-templates | @/pages/system/document-templates/index.vue | — | document:template:query | 是 | LEGACY_ONLY | — |
| 系统与全局 | Profile | /profile | @/pages/profile/index.vue | — | profile:query | 否 | LEGACY_ONLY | — |
| 系统与全局 | Settings | /settings | @/pages/settings/index.vue | — | settings:query | 否 | LEGACY_ONLY | — |
| 系统与全局 | Help | /help | @/pages/help/index.vue | — | help:query | 否 | LEGACY_ONLY | — |
| 系统与全局 | NotFound | /:pathMatch(.*)* | @/pages/error/404.vue | — | — | 否 | LEGACY_ONLY | — |
