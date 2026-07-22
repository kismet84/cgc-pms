# 第53条主线 UI V2 路由迁移台账

> 自动生成文件。源：`frontend-admin/src/router/index.ts`。修改路由后运行 `pnpm generate:route-ledger`；CI 使用 `pnpm check:route-ledger` 防漂移。

- 命名路由：87
- Legacy 路由视图引用：73
- Legacy 独立页面模块：65
- `LEGACY_ONLY`：50
- `V2_SOURCE_AVAILABLE`：0
- `V2_ACCEPTED`：37

| 域 | route name | URL | Legacy 视图 | V2 视图 | permission | adminOnly | 状态 | Stitch / 测试 / 验收 |
|---|---|---|---|---|---|---:|---|---|
| 系统与全局 | Login | /login | @/pages/login/index.vue | — | — | 否 | LEGACY_ONLY | — |
| 工作台 | Dashboard | /dashboard | @/pages/dashboard/index.vue | @/pages/dashboard/DashboardPage.vue | dashboard:view | 否 | V2_ACCEPTED | 用户已选新版经营驾驶舱视觉概念；M2 已验收；frontend-admin-v2/tests/unit；frontend-admin-v2/e2e；docs/quality/第53条主线-M2-工作台与新版驾驶舱验收报告.md |
| 系统与全局 | Forbidden | /403 | @/pages/error/403.vue | — | — | 否 | LEGACY_ONLY | — |
| 工作台 | ReportCatalog | /dashboard/reports | @/pages/report/catalog.vue | @/pages/workbench/ReportCatalogPage.vue | — | 否 | V2_ACCEPTED | frontend-admin-v2/tests/unit；frontend-admin-v2/e2e；docs/quality/第53条主线-M2-工作台与新版驾驶舱验收报告.md |
| 商务合约 | Contract | /contract | — | @/router.ts#V2ContractRootRedirect | contract:query | 否 | V2_ACCEPTED | frontend-admin-v2/tests/unit；frontend-admin-v2/e2e；docs/quality/ISSUE-053-018-M4合同台账与全生命周期V2验收报告.md |
| 商务合约 | ContractLedger | /contract/ledger | @/pages/contract/ContractLedgerPage.vue | @/pages/commercial/ContractPage.vue | contract:query | 否 | V2_ACCEPTED | frontend-admin-v2/tests/unit；frontend-admin-v2/e2e；docs/quality/ISSUE-053-018-M4合同台账与全生命周期V2验收报告.md |
| 商务合约 | ContractCreate | /contract/create | @/pages/contract/ContractFormPage.vue | @/pages/commercial/ContractPage.vue | contract:add | 否 | V2_ACCEPTED | frontend-admin-v2/tests/unit；frontend-admin-v2/e2e；docs/quality/ISSUE-053-018-M4合同台账与全生命周期V2验收报告.md |
| 商务合约 | ContractDetail | /contract/:id | @/pages/contract/ContractDetailPage.vue | @/pages/commercial/ContractPage.vue | contract:query | 否 | V2_ACCEPTED | frontend-admin-v2/tests/unit；frontend-admin-v2/e2e；docs/quality/ISSUE-053-018-M4合同台账与全生命周期V2验收报告.md |
| 商务合约 | ContractEdit | /contract/:id/edit | @/pages/contract/ContractFormPage.vue | @/pages/commercial/ContractPage.vue | contract:edit | 否 | V2_ACCEPTED | frontend-admin-v2/tests/unit；frontend-admin-v2/e2e；docs/quality/ISSUE-053-018-M4合同台账与全生命周期V2验收报告.md |
| 商务合约 | Cost | /cost | — | @/router.ts#V2CostRootRedirect | cost:query | 否 | V2_ACCEPTED | frontend-admin-v2/tests/unit；frontend-admin-v2/e2e；docs/quality/ISSUE-053-021-M4成本台账核对与动态利润V2验收报告.md |
| 商务合约 | CostLedger | /cost/ledger | @/pages/cost/ledger.vue | @/pages/commercial/CostLedgerPage.vue | cost:ledger:query | 否 | V2_ACCEPTED | frontend-admin-v2/tests/unit；frontend-admin-v2/e2e；docs/quality/ISSUE-053-021-M4成本台账核对与动态利润V2验收报告.md |
| 商务合约 | CostSummary | /cost/summary | @/pages/cost/summary.vue | @/pages/commercial/CostSummaryPage.vue | cost:summary:view | 否 | V2_ACCEPTED | frontend-admin-v2/tests/unit；frontend-admin-v2/e2e；docs/quality/ISSUE-053-021-M4成本台账核对与动态利润V2验收报告.md |
| 商务合约 | CostControl | /cost/control | @/pages/cost/control.vue | @/pages/commercial/CostControlPage.vue | cost:control:query | 否 | V2_ACCEPTED | frontend-admin-v2/tests/unit；frontend-admin-v2/e2e；docs/quality/ISSUE-053-021-M4成本台账核对与动态利润V2验收报告.md |
| 商务合约 | CostSubject | /cost/subject | — | — | cost:query | 否 | LEGACY_ONLY | — |
| 商务合约 | CostSubjectTaxonomy | /cost/subject/taxonomy | @/pages/cost-subject/index.vue | — | cost:query | 否 | LEGACY_ONLY | — |
| 商务合约 | CostSubjectRules | /cost/subject/rules | @/pages/cost-subject/index.vue | — | cost:subject:rule:query | 否 | LEGACY_ONLY | — |
| 商务合约 | CostSubjectScope | /cost/subject/scope | @/pages/cost-subject/index.vue | — | cost:subject:scope:query | 否 | LEGACY_ONLY | — |
| 商务合约 | CostSubjectTrace | /cost/subject/trace | @/pages/cost-subject/index.vue | — | cost:subject:audit:query | 否 | LEGACY_ONLY | — |
| 商务合约 | CostTarget | /cost-target | — | @/router.ts#V2CostTargetRootRedirect | cost:target:query | 否 | V2_ACCEPTED | frontend-admin-v2/tests/unit；frontend-admin-v2/e2e；docs/quality/ISSUE-053-020-M4目标成本版本V2验收报告.md |
| 商务合约 | CostTargetList | /cost-target/index | @/pages/cost-target/index.vue | @/pages/commercial/CostTargetPage.vue | cost:target:query | 否 | V2_ACCEPTED | frontend-admin-v2/tests/unit；frontend-admin-v2/e2e；docs/quality/ISSUE-053-020-M4目标成本版本V2验收报告.md |
| 商务合约 | CostTargetCreate | /cost-target/create | @/pages/cost-target/edit.vue | @/pages/commercial/CostTargetPage.vue | cost:target:add | 否 | V2_ACCEPTED | frontend-admin-v2/tests/unit；frontend-admin-v2/e2e；docs/quality/ISSUE-053-020-M4目标成本版本V2验收报告.md |
| 商务合约 | CostTargetEdit | /cost-target/:id/edit | @/pages/cost-target/edit.vue | @/pages/commercial/CostTargetPage.vue | cost:target:edit | 否 | V2_ACCEPTED | frontend-admin-v2/tests/unit；frontend-admin-v2/e2e；docs/quality/ISSUE-053-020-M4目标成本版本V2验收报告.md |
| 商务合约 | Variation | /variation | — | @/router.ts#V2VariationRootRedirect | variation:order:query | 否 | V2_ACCEPTED | frontend-admin-v2/tests/unit；frontend-admin-v2/e2e；docs/quality/ISSUE-053-019-M4变更签证与投标成本V2验收报告.md |
| 商务合约 | VariationOrder | /variation/order | @/pages/variation/order.vue | @/pages/commercial/VariationPage.vue | variation:order:query | 否 | V2_ACCEPTED | frontend-admin-v2/tests/unit；frontend-admin-v2/e2e；docs/quality/ISSUE-053-019-M4变更签证与投标成本V2验收报告.md |
| 分包与结算 | Settlement | /settlement | — | — | settlement:query | 否 | LEGACY_ONLY | — |
| 分包与结算 | SettlementList | /settlement/list | @/pages/settlement/index.vue | — | settlement:query | 否 | LEGACY_ONLY | — |
| 分包与结算 | SettlementDetail | /settlement/:id | @/pages/settlement/detail.vue | — | settlement:query | 否 | LEGACY_ONLY | — |
| 项目履约 | Project | /project | — | @/router.ts#V2ProjectRedirect | project:query | 否 | V2_ACCEPTED | frontend-admin-v2/tests/unit；frontend-admin-v2/e2e；docs/quality/ISSUE-053-011-M3项目对象工作区验收报告.md |
| 项目履约 | ProjectList | /project/list | @/pages/project/index.vue | @/pages/projects/ProjectPage.vue | project:query | 否 | V2_ACCEPTED | frontend-admin-v2/tests/unit；frontend-admin-v2/e2e；docs/quality/ISSUE-053-011-M3项目对象工作区验收报告.md |
| 项目履约 | ProjectOverview | /project/:projectId/overview | @/pages/project/overview.vue | @/pages/projects/ProjectPage.vue | project:query | 否 | V2_ACCEPTED | frontend-admin-v2/tests/unit；frontend-admin-v2/e2e；docs/quality/ISSUE-053-011-M3项目对象工作区验收报告.md |
| 项目履约 | ProjectMembers | /project/:projectId/members | @/pages/project/members.vue | @/pages/projects/ProjectPage.vue | project:member:list | 否 | V2_ACCEPTED | frontend-admin-v2/tests/unit；frontend-admin-v2/e2e；docs/quality/ISSUE-053-011-M3项目对象工作区验收报告.md |
| 项目履约 | ProjectEdit | /project/:projectId/edit | @/pages/project/edit.vue | @/pages/projects/ProjectPage.vue | project:edit | 否 | V2_ACCEPTED | frontend-admin-v2/tests/unit；frontend-admin-v2/e2e；docs/quality/ISSUE-053-011-M3项目对象工作区验收报告.md |
| 基础资料 | Partner | /partner | @/pages/partner/index.vue | — | partner:query | 否 | LEGACY_ONLY | — |
| 项目履约 | SiteDailyLog | /site/daily-log | @/pages/site/daily-log.vue | @/pages/delivery/DailyLogPage.vue | site:daily:query | 否 | V2_ACCEPTED | frontend-admin-v2/tests/unit；frontend-admin-v2/e2e；docs/quality/ISSUE-053-012-M3项目计划与现场日报验收报告.md |
| 项目履约 | ProjectSchedule | /project-schedule | @/pages/project-schedule/index.vue | @/pages/delivery/SchedulePage.vue | schedule:query | 否 | V2_ACCEPTED | frontend-admin-v2/tests/unit；frontend-admin-v2/e2e；docs/quality/ISSUE-053-012-M3项目计划与现场日报验收报告.md |
| 项目履约 | QualitySafety | /quality-safety | @/pages/quality-safety/index.vue | @/pages/delivery/QualitySafetyPage.vue | — | 否 | V2_ACCEPTED | frontend-admin-v2/tests/unit；frontend-admin-v2/e2e；docs/quality/ISSUE-053-013-M3质量安全整改闭环验收报告.md |
| 项目履约 | TechnicalManagement | /technical-management | @/pages/technical-management/index.vue | @/pages/delivery/TechnicalManagementPage.vue | — | 否 | V2_ACCEPTED | frontend-admin-v2/tests/unit；frontend-admin-v2/e2e；docs/quality/ISSUE-053-014-M3技术管理图纸与RFI闭环验收报告.md |
| 项目履约 | ProjectCloseout | /project-closeout | @/pages/project-closeout/index.vue | @/pages/delivery/ProjectCloseoutPage.vue | — | 否 | V2_ACCEPTED | frontend-admin-v2/tests/unit；frontend-admin-v2/e2e；docs/quality/ISSUE-053-015-M3竣工收尾闭环验收报告.md |
| 供应链与物资 | SupplierSourcing | /supplier-sourcing | @/pages/supplier-sourcing/index.vue | — | — | 否 | LEGACY_ONLY | — |
| 商务合约 | BidCost | /bid-cost | @/pages/bid-cost/index.vue | @/pages/commercial/BidCostPage.vue | — | 否 | V2_ACCEPTED | frontend-admin-v2/tests/unit；frontend-admin-v2/e2e；docs/quality/ISSUE-053-019-M4变更签证与投标成本V2验收报告.md |
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
| 商务合约 | ProjectBudget | /budget | @/pages/budget/index.vue | @/pages/commercial/BudgetPage.vue | — | 否 | V2_ACCEPTED | frontend-admin-v2/tests/unit；frontend-admin-v2/e2e；docs/quality/ISSUE-053-022-M4项目预算与产值计量V2验收报告.md |
| 资金财务 | FinanceOperations | /finance-operations | @/pages/finance-operations/index.vue | — | finance:operations:query | 否 | LEGACY_ONLY | — |
| 资金财务 | RevenueOperations | /revenue | @/pages/revenue/index.vue | — | — | 否 | LEGACY_ONLY | — |
| 商务合约 | ProductionMeasurement | /production-measurement | @/pages/production-measurement/index.vue | @/pages/commercial/ProductionMeasurementPage.vue | — | 否 | V2_ACCEPTED | frontend-admin-v2/tests/unit；frontend-admin-v2/e2e；docs/quality/ISSUE-053-022-M4项目预算与产值计量V2验收报告.md |
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
| 工作台 | Alert | /alert | @/pages/alert/index.vue | @/router.ts#V2LegacyAlertRedirect | alert:view | 否 | V2_ACCEPTED | frontend-admin-v2/tests/unit；frontend-admin-v2/e2e；docs/quality/第53条主线-M2-工作台与新版驾驶舱验收报告.md |
| 工作台 | Approval | /approval | — | @/router.ts#V2LegacyApprovalRedirect | workflow:task:query | 否 | V2_ACCEPTED | frontend-admin-v2/tests/unit；frontend-admin-v2/e2e；docs/quality/第53条主线-M2-工作台与新版驾驶舱验收报告.md |
| 工作台 | ApprovalTodo | /approval/todo | @/pages/approval/todo.vue | @/pages/workbench/WorkflowWorkbenchPage.vue | workflow:task:query | 否 | V2_ACCEPTED | frontend-admin-v2/tests/unit；frontend-admin-v2/e2e；docs/quality/第53条主线-M2-工作台与新版驾驶舱验收报告.md |
| 工作台 | ApprovalDone | /approval/done | @/pages/approval/todo.vue | @/pages/workbench/WorkflowWorkbenchPage.vue | workflow:task:query | 否 | V2_ACCEPTED | frontend-admin-v2/tests/unit；frontend-admin-v2/e2e；docs/quality/第53条主线-M2-工作台与新版驾驶舱验收报告.md |
| 工作台 | ApprovalCc | /approval/cc | @/pages/approval/todo.vue | @/pages/workbench/WorkflowWorkbenchPage.vue | workflow:cc:query | 否 | V2_ACCEPTED | frontend-admin-v2/tests/unit；frontend-admin-v2/e2e；docs/quality/第53条主线-M2-工作台与新版驾驶舱验收报告.md |
| 工作台 | ApprovalMine | /approval/mine | @/pages/approval/todo.vue | @/pages/workbench/WorkflowWorkbenchPage.vue | workflow:instance:query | 否 | V2_ACCEPTED | frontend-admin-v2/tests/unit；frontend-admin-v2/e2e；docs/quality/第53条主线-M2-工作台与新版驾驶舱验收报告.md |
| 工作台 | ApprovalProcess | /approval/process | @/pages/approval/process.vue | — | workflow:process:query | 是 | LEGACY_ONLY | — |
| 工作台 | ApprovalDetail | /approval/:instanceId | @/pages/approval/detail.vue | @/router.ts#V2LegacyApprovalDetailRedirect | workflow:instance:query | 否 | V2_ACCEPTED | frontend-admin-v2/tests/unit；frontend-admin-v2/e2e；docs/quality/第53条主线-M2-工作台与新版驾驶舱验收报告.md |
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
