# 第53条主线 UI V2 路由迁移台账

> 自动生成文件。源：`frontend-admin/src/router/index.ts`。修改路由后运行 `pnpm generate:route-ledger`；CI 使用 `pnpm check:route-ledger` 防漂移。

- 命名路由：87
- Legacy 路由视图引用：73
- Legacy 独立页面模块：65
- `LEGACY_ONLY`：24
- `V2_SOURCE_AVAILABLE`：0
- `V2_ACCEPTED`：63

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
| 分包与结算 | Settlement | /settlement | — | @/router.ts#V2SettlementRedirect | settlement:query | 否 | V2_ACCEPTED | frontend-admin-v2/tests/unit；frontend-admin-v2/e2e；docs/quality/ISSUE-053-032-M6结算台账详情与追溯V2验收报告.md |
| 分包与结算 | SettlementList | /settlement/list | @/pages/settlement/index.vue | @/pages/settlement/SettlementWorkspacePage.vue | settlement:query | 否 | V2_ACCEPTED | frontend-admin-v2/tests/unit；frontend-admin-v2/e2e；docs/quality/ISSUE-053-032-M6结算台账详情与追溯V2验收报告.md |
| 分包与结算 | SettlementDetail | /settlement/:id | @/pages/settlement/detail.vue | @/pages/settlement/SettlementWorkspacePage.vue | settlement:query | 否 | V2_ACCEPTED | frontend-admin-v2/tests/unit；frontend-admin-v2/e2e；docs/quality/ISSUE-053-032-M6结算台账详情与追溯V2验收报告.md |
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
| 供应链与物资 | SupplierSourcing | /supplier-sourcing | @/pages/supplier-sourcing/index.vue | @/pages/supply-chain/SupplierSourcingPage.vue | — | 否 | V2_ACCEPTED | frontend-admin-v2/tests/unit；frontend-admin-v2/e2e；docs/quality/ISSUE-053-025-M5供应商招采与履约V2验收报告.md |
| 商务合约 | BidCost | /bid-cost | @/pages/bid-cost/index.vue | @/pages/commercial/BidCostPage.vue | — | 否 | V2_ACCEPTED | frontend-admin-v2/tests/unit；frontend-admin-v2/e2e；docs/quality/ISSUE-053-019-M4变更签证与投标成本V2验收报告.md |
| 基础资料 | Org | /org | @/pages/org/index.vue | — | org:query | 否 | LEGACY_ONLY | — |
| 分包与结算 | Subcontract | /subcontract | — | @/router.ts#V2SubcontractRedirect | subtask:query | 否 | V2_ACCEPTED | frontend-admin-v2/tests/unit；frontend-admin-v2/e2e；docs/quality/ISSUE-053-031-M6分包任务与计量V2验收报告.md |
| 分包与结算 | SubcontractTask | /subcontract/task | @/pages/subcontract/task.vue | @/pages/subcontract/SubcontractWorkspacePage.vue | subtask:query | 否 | V2_ACCEPTED | frontend-admin-v2/tests/unit；frontend-admin-v2/e2e；docs/quality/ISSUE-053-031-M6分包任务与计量V2验收报告.md |
| 分包与结算 | SubcontractMeasure | /subcontract/measure | @/pages/subcontract/measure.vue | @/pages/subcontract/SubcontractWorkspacePage.vue | subcontract:measure:query | 否 | V2_ACCEPTED | frontend-admin-v2/tests/unit；frontend-admin-v2/e2e；docs/quality/ISSUE-053-031-M6分包任务与计量V2验收报告.md |
| 供应链与物资 | Purchase | /purchase | — | @/router.ts#V2PurchaseRedirect | purchase:order:query | 否 | V2_ACCEPTED | frontend-admin-v2/tests/unit；frontend-admin-v2/e2e；docs/quality/ISSUE-053-026-M5采购申请订单与验收V2验收报告.md |
| 供应链与物资 | PurchaseOrder | /purchase/order | @/pages/purchase/order.vue | @/pages/supply-chain/PurchaseExecutionPage.vue | purchase:order:query | 否 | V2_ACCEPTED | frontend-admin-v2/tests/unit；frontend-admin-v2/e2e；docs/quality/ISSUE-053-026-M5采购申请订单与验收V2验收报告.md |
| 供应链与物资 | PurchaseReceipt | /purchase/receipt | @/pages/receipt/index.vue | @/pages/supply-chain/PurchaseExecutionPage.vue | receipt:query | 否 | V2_ACCEPTED | frontend-admin-v2/tests/unit；frontend-admin-v2/e2e；docs/quality/ISSUE-053-026-M5采购申请订单与验收V2验收报告.md |
| 资金财务 | Payment | /payment | — | @/router.ts#V2PaymentRedirect | payment:app:query | 否 | V2_ACCEPTED | frontend-admin-v2/tests/unit；frontend-admin-v2/e2e；docs/quality/ISSUE-053-033-M6付款费用收入回款与发票V2验收报告.md |
| 资金财务 | PaymentApplication | /payment/application | @/pages/payment/index.vue | @/pages/finance/ReceivablesWorkspacePage.vue | payment:app:query | 否 | V2_ACCEPTED | frontend-admin-v2/tests/unit；frontend-admin-v2/e2e；docs/quality/ISSUE-053-033-M6付款费用收入回款与发票V2验收报告.md |
| 资金财务 | ExpenseApplication | /payment/expense | @/pages/expense/index.vue | @/pages/finance/ReceivablesWorkspacePage.vue | expense:query | 否 | V2_ACCEPTED | frontend-admin-v2/tests/unit；frontend-admin-v2/e2e；docs/quality/ISSUE-053-033-M6付款费用收入回款与发票V2验收报告.md |
| 商务合约 | ProjectBudget | /budget | @/pages/budget/index.vue | @/pages/commercial/BudgetPage.vue | — | 否 | V2_ACCEPTED | frontend-admin-v2/tests/unit；frontend-admin-v2/e2e；docs/quality/ISSUE-053-022-M4项目预算与产值计量V2验收报告.md |
| 资金财务 | FinanceOperations | /finance-operations | @/pages/finance-operations/index.vue | @/pages/finance/FinanceControlWorkspacePage.vue | finance:operations:query | 否 | V2_ACCEPTED | frontend-admin-v2/tests/unit；frontend-admin-v2/e2e；docs/quality/ISSUE-053-034-M6资金运营日记账预测凭证与月结V2验收报告.md |
| 资金财务 | RevenueOperations | /revenue | @/pages/revenue/index.vue | @/pages/finance/ReceivablesWorkspacePage.vue | revenue:operations:query | 否 | V2_ACCEPTED | frontend-admin-v2/tests/unit；frontend-admin-v2/e2e；docs/quality/ISSUE-053-033-M6付款费用收入回款与发票V2验收报告.md |
| 商务合约 | ProductionMeasurement | /production-measurement | @/pages/production-measurement/index.vue | @/pages/commercial/ProductionMeasurementPage.vue | — | 否 | V2_ACCEPTED | frontend-admin-v2/tests/unit；frontend-admin-v2/e2e；docs/quality/ISSUE-053-022-M4项目预算与产值计量V2验收报告.md |
| 资金财务 | CashJournal | /cash-journal | @/pages/cash-journal/index.vue | @/pages/finance/FinanceControlWorkspacePage.vue | cashbook:journal:query | 否 | V2_ACCEPTED | frontend-admin-v2/tests/unit；frontend-admin-v2/e2e；docs/quality/ISSUE-053-034-M6资金运营日记账预测凭证与月结V2验收报告.md |
| 资金财务 | AccountingEntry | /accounting-entry | @/pages/accounting-entry/index.vue | @/pages/finance/FinanceControlWorkspacePage.vue | accounting:query | 否 | V2_ACCEPTED | frontend-admin-v2/tests/unit；frontend-admin-v2/e2e；docs/quality/ISSUE-053-034-M6资金运营日记账预测凭证与月结V2验收报告.md |
| 资金财务 | CashForecast | /cash-forecast | @/pages/cash-forecast/index.vue | @/pages/finance/FinanceControlWorkspacePage.vue | finance:forecast:query | 否 | V2_ACCEPTED | frontend-admin-v2/tests/unit；frontend-admin-v2/e2e；docs/quality/ISSUE-053-034-M6资金运营日记账预测凭证与月结V2验收报告.md |
| 资金财务 | FinancialClose | /financial-close | @/pages/financial-close/index.vue | @/pages/finance/FinanceControlWorkspacePage.vue | finance:close:query | 否 | V2_ACCEPTED | frontend-admin-v2/tests/unit；frontend-admin-v2/e2e；docs/quality/ISSUE-053-034-M6资金运营日记账预测凭证与月结V2验收报告.md |
| 供应链与物资 | Inventory | /inventory | — | @/router.ts#V2InventoryRedirect | inventory:warehouse:list | 否 | V2_ACCEPTED | frontend-admin-v2/tests/unit；frontend-admin-v2/e2e；docs/quality/ISSUE-053-027-M5仓库库存与来源流水V2验收报告.md |
| 供应链与物资 | InventoryWarehouse | /inventory/warehouse | @/pages/inventory/warehouse.vue | @/pages/supply-chain/InventoryWorkspacePage.vue | inventory:warehouse:list | 否 | V2_ACCEPTED | frontend-admin-v2/tests/unit；frontend-admin-v2/e2e；docs/quality/ISSUE-053-027-M5仓库库存与来源流水V2验收报告.md |
| 供应链与物资 | InventoryStock | /inventory/stock | @/pages/inventory/stock.vue | @/pages/supply-chain/InventoryWorkspacePage.vue | inventory:stock:list | 否 | V2_ACCEPTED | frontend-admin-v2/tests/unit；frontend-admin-v2/e2e；docs/quality/ISSUE-053-027-M5仓库库存与来源流水V2验收报告.md |
| 供应链与物资 | InventoryTransaction | /inventory/transaction | @/pages/inventory/transaction.vue | @/router.ts#V2InventoryTransactionRedirect | inventory:transaction:list | 否 | V2_ACCEPTED | frontend-admin-v2/tests/unit；frontend-admin-v2/e2e；docs/quality/ISSUE-053-027-M5仓库库存与来源流水V2验收报告.md |
| 供应链与物资 | InventoryPurchaseRequest | /inventory/purchase-request | @/pages/inventory/purchase-request.vue | @/pages/supply-chain/PurchaseExecutionPage.vue | purchase:request:list | 否 | V2_ACCEPTED | frontend-admin-v2/tests/unit；frontend-admin-v2/e2e；docs/quality/ISSUE-053-026-M5采购申请订单与验收V2验收报告.md |
| 供应链与物资 | InventoryMaterialRequisition | /inventory/material-requisition | @/pages/requisition/index.vue | @/pages/supply-chain/RequisitionWorkspacePage.vue | requisition:query | 否 | V2_ACCEPTED | frontend-admin-v2/tests/unit；frontend-admin-v2/e2e；docs/quality/ISSUE-053-028-M5领料出库与退料V2验收报告.md |
| 资金财务 | Invoice | /invoice | @/pages/invoice/index.vue | @/pages/finance/ReceivablesWorkspacePage.vue | invoice:query | 否 | V2_ACCEPTED | frontend-admin-v2/tests/unit；frontend-admin-v2/e2e；docs/quality/ISSUE-053-033-M6付款费用收入回款与发票V2验收报告.md |
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
