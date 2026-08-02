import { mkdir, readFile, writeFile } from 'node:fs/promises'
import { dirname, resolve } from 'node:path'
import { fileURLToPath } from 'node:url'
import ts from 'typescript'

const scriptRoot = resolve(fileURLToPath(new URL('.', import.meta.url)))
const repositoryRoot = resolve(scriptRoot, '../..')
const routerPath = resolve(repositoryRoot, 'archive/v1.6/frontend-admin-legacy/src/router/index.ts')
const v2RouterPath = resolve(repositoryRoot, 'frontend-admin-v2/src/router.ts')
const navigationCatalogPath = resolve(repositoryRoot, 'frontend-admin-v2/src/navigation/catalog.ts')
const jsonPath = resolve(repositoryRoot, 'docs/ui-v2/route-migration-ledger.json')
const markdownPath = resolve(repositoryRoot, 'docs/ui-v2/route-migration-ledger.md')

const acceptedRoutes = {
  Login: '@/pages/auth/LoginPage.vue',
  Forbidden: '@/router.ts#V2LegacyForbiddenRedirect',
  NotFound: '@/pages/errors/NotFoundPage.vue',
  Profile: '@/pages/account/AccountPage.vue',
  Settings: '@/pages/account/AccountPage.vue',
  Help: '@/pages/account/AccountPage.vue',
  Partner: '@/pages/master-data/PartnerPage.vue',
  Org: '@/pages/master-data/OrganizationPage.vue',
  Material: '@/router.ts#V2MaterialRedirect',
  MaterialDictionary: '@/pages/master-data/MaterialDictionaryPage.vue',
  CostSubject: '@/router.ts#V2CostSubjectRootRedirect',
  CostSubjectTaxonomy: '@/pages/master-data/CostSubjectPage.vue',
  CostSubjectRules: '@/pages/master-data/CostSubjectPage.vue',
  CostSubjectScope: '@/pages/master-data/CostSubjectPage.vue',
  CostSubjectTrace: '@/pages/master-data/CostSubjectPage.vue',
  Dashboard: '@/pages/dashboard/DashboardPage.vue',
  ReportCatalog: '@/pages/workbench/ReportCatalogPage.vue',
  Alert: '@/router.ts#V2LegacyAlertRedirect',
  Approval: '@/router.ts#V2LegacyApprovalRedirect',
  ApprovalTodo: '@/pages/workbench/WorkflowWorkbenchPage.vue',
  ApprovalDone: '@/pages/workbench/WorkflowWorkbenchPage.vue',
  ApprovalCc: '@/pages/workbench/WorkflowWorkbenchPage.vue',
  ApprovalMine: '@/pages/workbench/WorkflowWorkbenchPage.vue',
  ApprovalProcess: '@/pages/system/WorkflowProcessPage.vue',
  System: '@/router.ts#V2SystemRedirect',
  SystemDict: '@/pages/system/DictionaryPage.vue',
  SystemUsers: '@/pages/system/AccessControlPage.vue',
  SystemData: '@/pages/system/DataMaintenancePage.vue',
  RoleManagement: '@/pages/system/AccessControlPage.vue',
  SystemPermissions: '@/pages/system/AccessControlPage.vue',
  SystemAudit: '@/pages/system/AuditPage.vue',
  DocumentTemplateManagement: '@/pages/system/DocumentTemplatePage.vue',
  ApprovalDetail: '@/router.ts#V2LegacyApprovalDetailRedirect',
  Project: '@/router.ts#V2ProjectRedirect',
  ProjectList: '@/pages/projects/ProjectPage.vue',
  ProjectOverview: '@/pages/projects/ProjectPage.vue',
  ProjectMembers: '@/pages/projects/ProjectPage.vue',
  ProjectEdit: '@/pages/projects/ProjectPage.vue',
  ProjectSchedule: '@/pages/delivery/SchedulePage.vue',
  SiteDailyLog: '@/pages/delivery/DailyLogPage.vue',
  QualitySafety: '@/pages/delivery/QualitySafetyPage.vue',
  TechnicalManagement: '@/pages/delivery/TechnicalManagementPage.vue',
  ProjectCloseout: '@/pages/delivery/ProjectCloseoutPage.vue',
  Contract: '@/router.ts#V2ContractRootRedirect',
  ContractLedger: '@/pages/commercial/ContractPage.vue',
  ContractCreate: '@/pages/commercial/ContractPage.vue',
  ContractDetail: '@/pages/commercial/ContractPage.vue',
  ContractEdit: '@/pages/commercial/ContractPage.vue',
  Variation: '@/router.ts#V2VariationRootRedirect',
  VariationOrder: '@/pages/commercial/VariationPage.vue',
  BidCost: '@/pages/commercial/BidCostPage.vue',
  CostTarget: '@/router.ts#V2CostTargetRootRedirect',
  CostTargetList: '@/pages/commercial/CostTargetPage.vue',
  CostTargetCreate: '@/pages/commercial/CostTargetPage.vue',
  CostTargetEdit: '@/pages/commercial/CostTargetPage.vue',
  Cost: '@/router.ts#V2CostRootRedirect',
  CostLedger: '@/pages/commercial/CostLedgerPage.vue',
  CostSummary: '@/pages/commercial/CostSummaryPage.vue',
  CostControl: '@/pages/commercial/CostControlPage.vue',
  ProjectBudget: '@/pages/commercial/CostBudgetPage.vue',
  ProductionMeasurement: '@/pages/commercial/ProductionMeasurementPage.vue',
  SupplierSourcing: '@/pages/supply-chain/SupplierSourcingPage.vue',
  Purchase: '@/router.ts#V2PurchaseRedirect',
  PurchaseOrder: '@/pages/supply-chain/PurchaseExecutionPage.vue',
  PurchaseReceipt: '@/pages/supply-chain/PurchaseExecutionPage.vue',
  InventoryPurchaseRequest: '@/pages/supply-chain/PurchaseExecutionPage.vue',
  Inventory: '@/router.ts#V2InventoryRedirect',
  InventoryWarehouse: '@/pages/supply-chain/InventoryWorkspacePage.vue',
  InventoryStock: '@/pages/supply-chain/InventoryWorkspacePage.vue',
  InventoryTransaction: '@/router.ts#V2InventoryTransactionRedirect',
  InventoryMaterialRequisition: '@/pages/supply-chain/RequisitionWorkspacePage.vue',
  Subcontract: '@/router.ts#V2SubcontractRedirect',
  SubcontractTask: '@/pages/subcontract/SubcontractWorkspacePage.vue',
  SubcontractMeasure: '@/pages/subcontract/SubcontractWorkspacePage.vue',
  Settlement: '@/router.ts#V2SettlementRedirect',
  SettlementList: '@/pages/settlement/SettlementWorkspacePage.vue',
  SettlementDetail: '@/pages/settlement/SettlementWorkspacePage.vue',
  Payment: '@/router.ts#V2PaymentRedirect',
  PaymentApplication: '@/pages/finance/ReceivablesWorkspacePage.vue',
  ExpenseApplication: '@/pages/finance/ReceivablesWorkspacePage.vue',
  RevenueOperations: '@/pages/finance/ReceivablesWorkspacePage.vue',
  Invoice: '@/pages/finance/ReceivablesWorkspacePage.vue',
  FinanceOperations: '@/pages/finance/FinanceControlWorkspacePage.vue',
  CashJournal: '@/pages/finance/FinanceControlWorkspacePage.vue',
  CashForecast: '@/pages/finance/FinanceControlWorkspacePage.vue',
  AccountingEntry: '@/pages/finance/FinanceControlWorkspacePage.vue',
  FinancialClose: '@/pages/finance/FinanceControlWorkspacePage.vue',
}

const acceptedRoutePermissions = {
  Profile: null,
  Settings: null,
  Help: null,
  Org: 'org:list',
  Material: 'material:dict:list',
  MaterialDictionary: 'material:dict:list',
  Inventory: 'inventory:warehouse:list',
  InventoryWarehouse: 'inventory:warehouse:list',
  Subcontract: 'subtask:query',
  SubcontractTask: 'subtask:query',
  ExpenseApplication: 'expense:query',
  RevenueOperations: 'revenue:operations:query',
  ProjectBudget: 'cost:target:query',
  AccountingEntry: 'accounting:query',
  CashForecast: 'finance:forecast:query',
  FinancialClose: 'finance:close:query',
  System: 'system:dict:list',
  SystemDict: 'system:dict:list',
  SystemPermissions: 'system:menu:query',
  SystemData: null,
}

const sourceAvailableRoutes = {}

const m2AcceptanceEvidence = 'docs/archive/v1.5/quality/第53条主线-M2-工作台与新版驾驶舱验收报告.md'
const m3ProjectAcceptanceEvidence =
  'docs/archive/v1.5/quality/ISSUE-053-011-M3项目对象工作区验收报告.md'
const m3DeliveryAcceptanceEvidence =
  'docs/archive/v1.5/quality/ISSUE-053-012-M3项目计划与现场日报验收报告.md'
const m3QualityAcceptanceEvidence =
  'docs/archive/v1.5/quality/ISSUE-053-013-M3质量安全整改闭环验收报告.md'
const m3TechnicalAcceptanceEvidence =
  'docs/archive/v1.5/quality/ISSUE-053-014-M3技术管理图纸与RFI闭环验收报告.md'
const m3CloseoutAcceptanceEvidence =
  'docs/archive/v1.5/quality/ISSUE-053-015-M3竣工收尾闭环验收报告.md'
const m4ContractAcceptanceEvidence =
  'docs/archive/v1.5/quality/ISSUE-053-018-M4合同台账与全生命周期V2验收报告.md'
const m4VariationBidAcceptanceEvidence =
  'docs/archive/v1.5/quality/ISSUE-053-019-M4变更签证与投标成本V2验收报告.md'
const m4CostTargetAcceptanceEvidence =
  'docs/archive/v1.5/quality/ISSUE-053-020-M4目标成本版本V2验收报告.md'
const m4CostsAcceptanceEvidence =
  'docs/archive/v1.5/quality/ISSUE-053-021-M4成本台账核对与动态利润V2验收报告.md'
const m4BudgetMeasurementAcceptanceEvidence =
  'docs/archive/v1.5/quality/ISSUE-053-022-M4项目预算与产值计量V2验收报告.md'
const m5SupplierSourcingAcceptanceEvidence =
  'docs/archive/v1.5/quality/ISSUE-053-025-M5供应商招采与履约V2验收报告.md'
const m5PurchaseReceiptAcceptanceEvidence =
  'docs/archive/v1.5/quality/ISSUE-053-026-M5采购申请订单与验收V2验收报告.md'
const m5InventoryAcceptanceEvidence =
  'docs/archive/v1.5/quality/ISSUE-053-027-M5仓库库存与来源流水V2验收报告.md'
const m5RequisitionAcceptanceEvidence =
  'docs/archive/v1.5/quality/ISSUE-053-028-M5领料出库与退料V2验收报告.md'
const m6SubcontractAcceptanceEvidence =
  'docs/archive/v1.5/quality/ISSUE-053-031-M6分包任务与计量V2验收报告.md'
const m6SettlementAcceptanceEvidence =
  'docs/archive/v1.5/quality/ISSUE-053-032-M6结算台账详情与追溯V2验收报告.md'
const m6FinanceAcceptanceEvidence =
  'docs/archive/v1.5/quality/ISSUE-053-033-M6付款费用收入回款与发票V2验收报告.md'
const m6FinanceControlAcceptanceEvidence =
  'docs/archive/v1.5/quality/ISSUE-053-034-M6资金运营日记账预测凭证与月结V2验收报告.md'
const m7GlobalAcceptanceEvidence =
  'docs/archive/v1.5/quality/ISSUE-053-036-M7登录与错误深链V2验收报告.md'
const m7GlobalRoutes = new Set(['Login', 'Forbidden', 'NotFound'])
const m7AccountAcceptanceEvidence =
  'docs/archive/v1.5/quality/ISSUE-053-037-M7个人设置与帮助V2验收报告.md'
const m7AccountRoutes = new Set(['Profile', 'Settings', 'Help'])
const m7MasterDataAcceptanceEvidence =
  'docs/archive/v1.5/quality/ISSUE-053-038-M7基础资料V2验收报告.md'
const m7MasterDataRoutes = new Set(['Partner', 'Org', 'Material', 'MaterialDictionary'])
const m7CostSubjectAcceptanceEvidence =
  'docs/archive/v1.5/quality/ISSUE-053-039-M7成本科目中心V2验收报告.md'
const m7CostSubjectRoutes = new Set([
  'CostSubject',
  'CostSubjectTaxonomy',
  'CostSubjectRules',
  'CostSubjectScope',
  'CostSubjectTrace',
])
const m7WorkflowAcceptanceEvidence =
  'docs/archive/v1.5/quality/ISSUE-053-040-M7流程配置与adminOnly门V2验收报告.md'
const m7WorkflowRoutes = new Set(['ApprovalProcess'])
const m7SystemAcceptanceEvidence = 'docs/archive/v1.5/quality/ISSUE-053-041-M7系统管理V2验收报告.md'
const m7SystemRoutes = new Set([
  'System',
  'SystemDict',
  'SystemUsers',
  'SystemData',
  'RoleManagement',
  'SystemPermissions',
  'SystemAudit',
  'DocumentTemplateManagement',
])

function findVariable(sourceFile, name) {
  for (const statement of sourceFile.statements) {
    if (!ts.isVariableStatement(statement)) continue
    for (const declaration of statement.declarationList.declarations) {
      if (ts.isIdentifier(declaration.name) && declaration.name.text === name)
        return declaration.initializer
    }
  }
  throw new Error(`Missing variable: ${name}`)
}

function property(object, name) {
  return object.properties.find((item) => {
    if (!ts.isPropertyAssignment(item)) return false
    return (
      (ts.isIdentifier(item.name) && item.name.text === name) ||
      (ts.isStringLiteral(item.name) && item.name.text === name)
    )
  })
}

function literal(node) {
  if (!node) return undefined
  if (ts.isStringLiteralLike(node)) return node.text
  if (node.kind === ts.SyntaxKind.TrueKeyword) return true
  if (node.kind === ts.SyntaxKind.FalseKeyword) return false
  return undefined
}

function objectValue(object, name) {
  const item = property(object, name)
  return item && ts.isPropertyAssignment(item) ? item.initializer : undefined
}

function routePath(parent, child) {
  if (child.startsWith('/')) return child
  const base = parent === '/' ? '' : parent.replace(/\/$/, '')
  return `${base}/${child}` || '/'
}

function domainFor(path) {
  if (/^\/(dashboard|approval|alert)/.test(path)) return '工作台'
  if (/^\/(project|site|quality-safety|technical-management|project-closeout)/.test(path))
    return '项目履约'
  if (/^\/(contract|variation|bid-cost|cost-target|cost|budget|production-measurement)/.test(path))
    return '商务合约'
  if (/^\/(supplier-sourcing|purchase|inventory)/.test(path)) return '供应链与物资'
  if (/^\/(subcontract|settlement)/.test(path)) return '分包与结算'
  if (
    /^\/(payment|revenue|invoice|finance-operations|cash-journal|cash-forecast|accounting-entry|financial-close)/.test(
      path,
    )
  )
    return '资金财务'
  if (/^\/(partner|org|material)/.test(path)) return '基础资料'
  return '系统与全局'
}

function permissionMapFrom(node) {
  if (!node || !ts.isObjectLiteralExpression(node))
    throw new Error('ROUTE_PERMISSION_MAP must be an object')
  return Object.fromEntries(
    node.properties.flatMap((item) => {
      if (!ts.isPropertyAssignment(item)) return []
      const key =
        ts.isIdentifier(item.name) || ts.isStringLiteral(item.name) ? item.name.text : undefined
      const value = literal(item.initializer)
      return key && typeof value === 'string' ? [[key, value]] : []
    }),
  )
}

function extractRoutes(array, permissions, sourceFile, parentPath = '', inheritedAdmin = false) {
  if (!ts.isArrayLiteralExpression(array)) throw new Error('routes/children must be an array')
  const result = []
  for (const element of array.elements) {
    if (!ts.isObjectLiteralExpression(element)) continue
    const pathValue = literal(objectValue(element, 'path'))
    if (typeof pathValue !== 'string') continue
    const fullPath = routePath(parentPath, pathValue)
    const name = literal(objectValue(element, 'name'))
    const metaNode = objectValue(element, 'meta')
    const meta = metaNode && ts.isObjectLiteralExpression(metaNode) ? metaNode : undefined
    const explicitAdmin = meta ? literal(objectValue(meta, 'adminOnly')) : undefined
    const effectiveAdmin = typeof explicitAdmin === 'boolean' ? explicitAdmin : inheritedAdmin
    const componentNode = objectValue(element, 'component')
    const componentText = componentNode?.getText(sourceFile) || ''
    const component = componentText.match(/import\(['"]([^'"]+)['"]\)/)?.[1] || null
    const redirect = literal(objectValue(element, 'redirect')) || null

    if (typeof name === 'string') {
      const acceptedView = acceptedRoutes[name] || null
      const sourceView = sourceAvailableRoutes[name] || null
      const v2View = acceptedView || sourceView
      const isM3Project = name === 'Project' || name.startsWith('Project')
      const isM3Delivery = name === 'ProjectSchedule' || name === 'SiteDailyLog'
      const isM3Quality = name === 'QualitySafety'
      const isM3Technical = name === 'TechnicalManagement'
      const isM3Closeout = name === 'ProjectCloseout'
      const isM4Contract = name === 'Contract' || name.startsWith('Contract')
      const isM4VariationBid =
        name === 'Variation' || name === 'VariationOrder' || name === 'BidCost'
      const isM4CostTarget = name === 'CostTarget' || name.startsWith('CostTarget')
      const isM4Costs = ['Cost', 'CostLedger', 'CostSummary', 'CostControl'].includes(name)
      const isM4BudgetMeasurement = ['ProjectBudget', 'ProductionMeasurement'].includes(name)
      const isM5SupplierSourcing = name === 'SupplierSourcing'
      const isM5PurchaseReceipt = [
        'Purchase',
        'PurchaseOrder',
        'PurchaseReceipt',
        'InventoryPurchaseRequest',
      ].includes(name)
      const isM5Inventory = [
        'Inventory',
        'InventoryWarehouse',
        'InventoryStock',
        'InventoryTransaction',
      ].includes(name)
      const isM5Requisition = name === 'InventoryMaterialRequisition'
      const isM6Subcontract = ['Subcontract', 'SubcontractTask', 'SubcontractMeasure'].includes(
        name,
      )
      const isM6Settlement = ['Settlement', 'SettlementList', 'SettlementDetail'].includes(name)
      const isM6Finance = [
        'Payment',
        'PaymentApplication',
        'ExpenseApplication',
        'RevenueOperations',
        'Invoice',
      ].includes(name)
      const isM6FinanceControl = [
        'FinanceOperations',
        'CashJournal',
        'CashForecast',
        'AccountingEntry',
        'FinancialClose',
      ].includes(name)
      result.push({
        name,
        path: fullPath,
        legacyView: component,
        v2View,
        permission: Object.hasOwn(acceptedRoutePermissions, name)
          ? acceptedRoutePermissions[name]
          : permissions[name] || null,
        adminOnly: effectiveAdmin,
        public: meta ? literal(objectValue(meta, 'public')) === true : false,
        redirect,
        domain: domainFor(fullPath),
        status: acceptedView ? 'V2_ACCEPTED' : sourceView ? 'V2_SOURCE_AVAILABLE' : 'LEGACY_ONLY',
        stitchDesign: name === 'Dashboard' ? '用户已选新版经营驾驶舱视觉概念；M2 已验收' : null,
        testEvidence: v2View ? 'frontend-admin-v2/tests/unit；frontend-admin-v2/e2e' : null,
        acceptanceEvidence: acceptedView
          ? isM6FinanceControl
            ? m6FinanceControlAcceptanceEvidence
            : isM6Finance
              ? m6FinanceAcceptanceEvidence
              : isM6Settlement
                ? m6SettlementAcceptanceEvidence
                : isM6Subcontract
                  ? m6SubcontractAcceptanceEvidence
                  : isM5Requisition
                    ? m5RequisitionAcceptanceEvidence
                    : isM5Inventory
                      ? m5InventoryAcceptanceEvidence
                      : isM5PurchaseReceipt
                        ? m5PurchaseReceiptAcceptanceEvidence
                        : isM5SupplierSourcing
                          ? m5SupplierSourcingAcceptanceEvidence
                          : isM4BudgetMeasurement
                            ? m4BudgetMeasurementAcceptanceEvidence
                            : isM4Costs
                              ? m4CostsAcceptanceEvidence
                              : isM4CostTarget
                                ? m4CostTargetAcceptanceEvidence
                                : isM4Contract
                                  ? m4ContractAcceptanceEvidence
                                  : isM4VariationBid
                                    ? m4VariationBidAcceptanceEvidence
                                    : isM3Delivery
                                      ? m3DeliveryAcceptanceEvidence
                                      : isM3Quality
                                        ? m3QualityAcceptanceEvidence
                                        : isM3Technical
                                          ? m3TechnicalAcceptanceEvidence
                                          : isM3Closeout
                                            ? m3CloseoutAcceptanceEvidence
                                            : isM3Project
                                              ? m3ProjectAcceptanceEvidence
                                              : m2AcceptanceEvidence
          : null,
      })
      if (m7GlobalRoutes.has(name))
        result[result.length - 1].acceptanceEvidence = m7GlobalAcceptanceEvidence
      if (m7AccountRoutes.has(name))
        result[result.length - 1].acceptanceEvidence = m7AccountAcceptanceEvidence
      if (m7MasterDataRoutes.has(name))
        result[result.length - 1].acceptanceEvidence = m7MasterDataAcceptanceEvidence
      if (m7CostSubjectRoutes.has(name))
        result[result.length - 1].acceptanceEvidence = m7CostSubjectAcceptanceEvidence
      if (m7WorkflowRoutes.has(name))
        result[result.length - 1].acceptanceEvidence = m7WorkflowAcceptanceEvidence
      if (m7SystemRoutes.has(name))
        result[result.length - 1].acceptanceEvidence = m7SystemAcceptanceEvidence
    }

    const children = objectValue(element, 'children')
    if (children)
      result.push(...extractRoutes(children, permissions, sourceFile, fullPath, effectiveAdmin))
  }
  return result
}

function markdownCell(value) {
  if (value === null || value === undefined || value === '') return '—'
  return String(value).replaceAll('|', '\\|').replaceAll('\n', ' ')
}

function renderMarkdown(ledger) {
  const lines = [
    '# 第53条主线 UI V2 路由迁移台账',
    '',
    '> 自动生成文件。Legacy 冻结源：`archive/v1.6/frontend-admin-legacy/src/router/index.ts`。修改路由后运行 `pnpm generate:route-ledger`；CI 使用 `pnpm check:route-ledger` 防漂移。',
    '',
    `- 命名路由：${ledger.summary.namedRoutes}`,
    `- Legacy 路由视图引用：${ledger.summary.legacyRouteViewEntries}`,
    `- Legacy 独立页面模块：${ledger.summary.uniqueLegacyViews}`,
    `- ` + '`LEGACY_ONLY`' + `：${ledger.summary.legacyOnly}`,
    `- ` + '`V2_SOURCE_AVAILABLE`' + `：${ledger.summary.v2SourceAvailable}`,
    `- ` + '`V2_ACCEPTED`' + `：${ledger.summary.v2Accepted}`,
    '',
    '| 域 | route name | URL | Legacy 视图 | V2 视图 | permission | adminOnly | 状态 | Stitch / 测试 / 验收 |',
    '|---|---|---|---|---|---|---:|---|---|',
  ]

  for (const route of ledger.routes) {
    const evidence = [route.stitchDesign, route.testEvidence, route.acceptanceEvidence]
      .filter(Boolean)
      .join('；')
    lines.push(
      `| ${markdownCell(route.domain)} | ${markdownCell(route.name)} | ${markdownCell(route.path)} | ${markdownCell(route.legacyView)} | ${markdownCell(route.v2View)} | ${markdownCell(route.permission)} | ${route.adminOnly ? '是' : '否'} | ${route.status} | ${markdownCell(evidence)} |`,
    )
  }
  lines.push('')
  return lines.join('\n')
}

function assertAcceptedRouteSources(routerSource, catalogSource) {
  const missingViews = Object.entries(acceptedRoutes).flatMap(([name, view]) => {
    const marker = view.startsWith('@/pages/') ? view.replace('@/', './') : view.split('#')[1]
    return marker && !routerSource.includes(marker) ? [`${name} -> ${view}`] : []
  })
  if (missingViews.length) {
    throw new Error(`Accepted V2 route sources missing: ${missingViews.join(', ')}`)
  }

  const routeSources = `${routerSource}\n${catalogSource}`
  const missingPermissions = [
    ...new Set(Object.values(acceptedRoutePermissions).filter(Boolean)),
  ].filter((permission) => !routeSources.includes(permission))
  if (missingPermissions.length) {
    throw new Error(`Accepted route permissions missing: ${missingPermissions.join(', ')}`)
  }
}

async function buildLedger() {
  const [source, v2RouterSource, navigationCatalogSource] = await Promise.all([
    readFile(routerPath, 'utf8'),
    readFile(v2RouterPath, 'utf8'),
    readFile(navigationCatalogPath, 'utf8'),
  ])
  assertAcceptedRouteSources(v2RouterSource, navigationCatalogSource)
  const sourceFile = ts.createSourceFile(
    routerPath,
    source,
    ts.ScriptTarget.Latest,
    true,
    ts.ScriptKind.TS,
  )
  const routesNode = findVariable(sourceFile, 'routes')
  const permissions = permissionMapFrom(findVariable(sourceFile, 'ROUTE_PERMISSION_MAP'))
  const routes = extractRoutes(routesNode, permissions, sourceFile)
  const names = new Set(routes.map((route) => route.name))
  if (names.size !== routes.length) throw new Error('Duplicate route names found')
  return {
    schemaVersion: 1,
    source: 'archive/v1.6/frontend-admin-legacy/src/router/index.ts',
    summary: {
      namedRoutes: routes.length,
      legacyRouteViewEntries: routes.filter((route) => route.legacyView?.startsWith('@/pages/'))
        .length,
      uniqueLegacyViews: new Set(
        routes.map((route) => route.legacyView).filter((view) => view?.startsWith('@/pages/')),
      ).size,
      legacyOnly: routes.filter((route) => route.status === 'LEGACY_ONLY').length,
      v2SourceAvailable: routes.filter((route) => route.status === 'V2_SOURCE_AVAILABLE').length,
      v2Accepted: routes.filter((route) => route.status === 'V2_ACCEPTED').length,
    },
    routes,
  }
}

async function assertCurrent(path, expected) {
  const current = await readFile(path, 'utf8').catch(() => '')
  if (current !== expected) throw new Error(`Route ledger drift: ${path}`)
}

const ledger = await buildLedger()
const json = `${JSON.stringify(ledger, null, 2)}\n`
const markdown = renderMarkdown(ledger)

if (process.argv.includes('--write')) {
  await mkdir(dirname(jsonPath), { recursive: true })
  await writeFile(jsonPath, json, 'utf8')
  await writeFile(markdownPath, markdown, 'utf8')
  console.log(
    `Route ledger generated: ${ledger.summary.namedRoutes} routes, ${ledger.summary.legacyRouteViewEntries} view entries, ${ledger.summary.uniqueLegacyViews} unique views.`,
  )
} else if (process.argv.includes('--check')) {
  await assertCurrent(jsonPath, json)
  await assertCurrent(markdownPath, markdown)
  console.log(
    `Route ledger current: ${ledger.summary.namedRoutes} routes, ${ledger.summary.legacyRouteViewEntries} view entries, ${ledger.summary.uniqueLegacyViews} unique views.`,
  )
} else {
  console.log(json)
}
