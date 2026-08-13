import AppShell from '../layouts/AppShell.vue'
import LoginPage from '../pages/auth/LoginPage.vue'
import HealthPage from '../pages/HealthPage.vue'

export { AppShell, HealthPage, LoginPage }

export const ForbiddenPage = () => import('../pages/errors/ForbiddenPage.vue')
export const NotFoundPage = () => import('../pages/errors/NotFoundPage.vue')
export const DashboardPage = () => import('../pages/dashboard/DashboardPage.vue')
export const WorkflowWorkbenchPage = () => import('../pages/workbench/WorkflowWorkbenchPage.vue')
export const ReportCatalogPage = () => import('../pages/workbench/ReportCatalogPage.vue')
export const ProjectListPage = () => import('../pages/projects/project-routes/ProjectListPage.vue')
export const ProjectOverviewPage = () =>
  import('../pages/projects/project-routes/ProjectOverviewPage.vue')
export const ProjectMembersPage = () =>
  import('../pages/projects/project-routes/ProjectMembersPage.vue')
export const ProjectEditPage = () => import('../pages/projects/project-routes/ProjectEditPage.vue')
export const ProjectFileCenterPage = () => import('../pages/project/ProjectFileCenterPage.vue')
export const CommunicationPage = () => import('../pages/communication/CommunicationPage.vue')
export const ContractLedgerPage = () =>
  import('../pages/commercial/contract/ContractLedgerPage.vue')
export const ContractCreatePage = () =>
  import('../pages/commercial/contract/ContractCreatePage.vue')
export const ContractDetailPage = () =>
  import('../pages/commercial/contract/ContractDetailPage.vue')
export const ContractEditPage = () => import('../pages/commercial/contract/ContractEditPage.vue')
export const VariationPage = () => import('../pages/commercial/VariationPage.vue')
export const VariationWorkspacePage = () =>
  import('../pages/commercial/variation/VariationWorkspacePage.vue')
export const BidCostPage = () => import('../pages/commercial/BidCostPage.vue')
export const BidTenderDetailPage = () => import('../pages/commercial/BidTenderDetailPage.vue')
export const BidTenderCostPage = () => import('../pages/commercial/BidTenderCostPage.vue')
export const CostTargetPage = () => import('../pages/commercial/CostTargetPage.vue')
export const CostLedgerPage = () => import('../pages/commercial/CostLedgerPage.vue')
export const CostSummaryPage = () => import('../pages/commercial/CostSummaryPage.vue')
export const CostControlPage = () => import('../pages/commercial/CostControlPage.vue')
export const ProductionMeasurementPage = () =>
  import('../pages/commercial/ProductionMeasurementPage.vue')
export const SchedulePage = () => import('../pages/delivery/SchedulePage.vue')
export const DailyLogPage = () => import('../pages/delivery/DailyLogPage.vue')
export const QualitySafetyPage = () => import('../pages/delivery/QualitySafetyPage.vue')
export const TechnicalManagementPage = () => import('../pages/delivery/TechnicalManagementPage.vue')
export const ProjectCloseoutPage = () => import('../pages/delivery/ProjectCloseoutPage.vue')
export const SupplierSourcingPage = () => import('../pages/supply-chain/SupplierSourcingPage.vue')
export const PurchaseRequestWorkspace = () =>
  import('../pages/supply-chain/purchase-execution/PurchaseRequestWorkspace.vue')
export const PurchaseOrderWorkspace = () =>
  import('../pages/supply-chain/purchase-execution/PurchaseOrderWorkspace.vue')
export const MaterialReceiptWorkspace = () =>
  import('../pages/supply-chain/purchase-execution/MaterialReceiptWorkspace.vue')
export const InventoryWorkspacePage = () =>
  import('../pages/supply-chain/InventoryWorkspacePage.vue')
export const RequisitionWorkspacePage = () =>
  import('../pages/supply-chain/RequisitionWorkspacePage.vue')
export const SubcontractWorkspacePage = () =>
  import('../pages/subcontract/SubcontractWorkspacePage.vue')
export const SettlementWorkspacePage = () =>
  import('../pages/settlement/SettlementWorkspacePage.vue')
export const PaymentApplicationPage = () =>
  import('../pages/finance/receivables-workspace/PaymentApplicationPage.vue')
export const ExpenseApplicationPage = () =>
  import('../pages/finance/receivables-workspace/ExpenseApplicationPage.vue')
export const RevenueOperationsPage = () =>
  import('../pages/finance/receivables-workspace/RevenueOperationsPage.vue')
export const InvoiceManagementPage = () =>
  import('../pages/finance/receivables-workspace/InvoiceManagementPage.vue')
export const FinanceOperationsPage = () =>
  import('../pages/finance/finance-control-workspace/FinanceOperationsPage.vue')
export const CashJournalPage = () =>
  import('../pages/finance/finance-control-workspace/CashJournalPage.vue')
export const FundAccountsPage = () =>
  import('../pages/finance/finance-control-workspace/FundAccountsPage.vue')
export const CashForecastPage = () =>
  import('../pages/finance/finance-control-workspace/CashForecastPage.vue')
export const AccountingEntryPage = () =>
  import('../pages/finance/finance-control-workspace/AccountingEntryPage.vue')
export const FinancialClosePage = () =>
  import('../pages/finance/finance-control-workspace/FinancialClosePage.vue')
export const AccountPage = () => import('../pages/account/AccountPage.vue')
export const PartnerPage = () => import('../pages/master-data/PartnerPage.vue')
export const PartnerDetailPage = () => import('../pages/master-data/PartnerDetailPage.vue')
export const OrganizationPage = () => import('../pages/master-data/OrganizationPage.vue')
export const MaterialDictionaryPage = () =>
  import('../pages/master-data/MaterialDictionaryPage.vue')
export const CostSubjectTaxonomyPage = () =>
  import('../pages/master-data/cost-subject/CostSubjectTaxonomyPage.vue')
export const CostSubjectRulesPage = () =>
  import('../pages/master-data/cost-subject/CostSubjectRulesPage.vue')
export const CostSubjectScopePage = () =>
  import('../pages/master-data/cost-subject/CostSubjectScopePage.vue')
export const CostSubjectTracePage = () =>
  import('../pages/master-data/cost-subject/CostSubjectTracePage.vue')
export const WorkflowProcessPage = () => import('../pages/system/WorkflowProcessPage.vue')
export const UserManagementPage = () =>
  import('../pages/system/access-control/UserManagementPage.vue')
export const RoleManagementPage = () =>
  import('../pages/system/access-control/RoleManagementPage.vue')
export const PermissionListPage = () =>
  import('../pages/system/access-control/PermissionListPage.vue')
export const DictionaryPage = () => import('../pages/system/DictionaryPage.vue')
export const AuditPage = () => import('../pages/system/AuditPage.vue')
export const DocumentTemplatePage = () => import('../pages/system/DocumentTemplatePage.vue')
export const DataMaintenancePage = () => import('../pages/system/DataMaintenancePage.vue')

export const navigationComponents = {
  '/dashboard': DashboardPage,
  '/dashboard/reports': ReportCatalogPage,
  '/approval/todo': WorkflowWorkbenchPage,
  '/approval/done': WorkflowWorkbenchPage,
  '/approval/cc': WorkflowWorkbenchPage,
  '/approval/mine': WorkflowWorkbenchPage,
  '/project/list': ProjectListPage,
  '/project/files': ProjectFileCenterPage,
  '/communication': CommunicationPage,
  '/contract/ledger': ContractLedgerPage,
  '/variation/order': VariationWorkspacePage,
  '/engineering-tender/records': BidCostPage,
  '/engineering-tender/costs': BidTenderCostPage,
  '/cost-budget': CostTargetPage,
  '/cost/ledger': CostLedgerPage,
  '/cost/summary': CostSummaryPage,
  '/cost/control': CostControlPage,
  '/production-measurement': ProductionMeasurementPage,
  '/project-schedule': SchedulePage,
  '/site/daily-log': DailyLogPage,
  '/quality-safety': QualitySafetyPage,
  '/technical-management': TechnicalManagementPage,
  '/project-closeout': ProjectCloseoutPage,
  '/inventory/purchase-request': PurchaseRequestWorkspace,
  '/purchase/order': PurchaseOrderWorkspace,
  '/purchase/receipt': MaterialReceiptWorkspace,
  '/inventory/warehouse': InventoryWorkspacePage,
  '/inventory/stock': InventoryWorkspacePage,
  '/inventory/material-requisition': RequisitionWorkspacePage,
  '/subcontract/task': SubcontractWorkspacePage,
  '/subcontract/measure': SubcontractWorkspacePage,
  '/settlement/list': SettlementWorkspacePage,
  '/payment/application': PaymentApplicationPage,
  '/payment/expense': ExpenseApplicationPage,
  '/revenue': RevenueOperationsPage,
  '/invoice': InvoiceManagementPage,
  '/finance-operations': FinanceOperationsPage,
  '/cash-journal': CashJournalPage,
  '/fund-accounts': FundAccountsPage,
  '/cash-forecast': CashForecastPage,
  '/accounting-entry': AccountingEntryPage,
  '/financial-close': FinancialClosePage,
  '/partner': PartnerPage,
  '/org': OrganizationPage,
  '/material/dictionary': MaterialDictionaryPage,
  '/cost/subject/taxonomy': CostSubjectTaxonomyPage,
  '/cost/subject/rules': CostSubjectRulesPage,
  '/cost/subject/scope': CostSubjectScopePage,
  '/cost/subject/trace': CostSubjectTracePage,
  '/approval/process': WorkflowProcessPage,
  '/system/users': UserManagementPage,
  '/system/roles': RoleManagementPage,
  '/system/permissions': PermissionListPage,
  '/system/dict': DictionaryPage,
  '/system/audit': AuditPage,
  '/system/document-templates': DocumentTemplatePage,
  '/system/data': DataMaintenancePage,
} as const
