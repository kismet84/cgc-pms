import { mkdir, readFile, writeFile } from 'node:fs/promises'
import { dirname, resolve } from 'node:path'
import { fileURLToPath } from 'node:url'
import ts from 'typescript'

const scriptRoot = resolve(fileURLToPath(new URL('.', import.meta.url)))
const repositoryRoot = resolve(scriptRoot, '../..')
const routerPath = resolve(repositoryRoot, 'frontend-admin/src/router/index.ts')
const jsonPath = resolve(repositoryRoot, 'docs/ui-v2/route-migration-ledger.json')
const markdownPath = resolve(repositoryRoot, 'docs/ui-v2/route-migration-ledger.md')

const acceptedRoutes = {
  Dashboard: '@/pages/dashboard/DashboardPage.vue',
  ReportCatalog: '@/pages/workbench/ReportCatalogPage.vue',
  Alert: '@/router.ts#V2LegacyAlertRedirect',
  Approval: '@/router.ts#V2LegacyApprovalRedirect',
  ApprovalTodo: '@/pages/workbench/WorkflowWorkbenchPage.vue',
  ApprovalDone: '@/pages/workbench/WorkflowWorkbenchPage.vue',
  ApprovalCc: '@/pages/workbench/WorkflowWorkbenchPage.vue',
  ApprovalMine: '@/pages/workbench/WorkflowWorkbenchPage.vue',
  ApprovalDetail: '@/router.ts#V2LegacyApprovalDetailRedirect',
  Project: '@/router.ts#V2ProjectRedirect',
  ProjectList: '@/pages/projects/ProjectPage.vue',
  ProjectOverview: '@/pages/projects/ProjectPage.vue',
  ProjectMembers: '@/pages/projects/ProjectPage.vue',
  ProjectEdit: '@/pages/projects/ProjectPage.vue',
  ProjectSchedule: '@/pages/delivery/SchedulePage.vue',
  SiteDailyLog: '@/pages/delivery/DailyLogPage.vue',
}

const sourceAvailableRoutes = {}

const m2AcceptanceEvidence = 'docs/quality/第53条主线-M2-工作台与新版驾驶舱验收报告.md'
const m3ProjectAcceptanceEvidence = 'docs/quality/ISSUE-053-011-M3项目对象工作区验收报告.md'
const m3DeliveryAcceptanceEvidence = 'docs/quality/ISSUE-053-012-M3项目计划与现场日报验收报告.md'

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
  if (/^\/(project|site|quality-safety|technical-management)/.test(path)) return '项目履约'
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
      result.push({
        name,
        path: fullPath,
        legacyView: component,
        v2View,
        permission: permissions[name] || null,
        adminOnly: effectiveAdmin,
        public: meta ? literal(objectValue(meta, 'public')) === true : false,
        redirect,
        domain: domainFor(fullPath),
        status: acceptedView ? 'V2_ACCEPTED' : sourceView ? 'V2_SOURCE_AVAILABLE' : 'LEGACY_ONLY',
        stitchDesign: name === 'Dashboard' ? '用户已选新版经营驾驶舱视觉概念；M2 已验收' : null,
        testEvidence: v2View ? 'frontend-admin-v2/tests/unit；frontend-admin-v2/e2e' : null,
        acceptanceEvidence: acceptedView
          ? isM3Delivery
            ? m3DeliveryAcceptanceEvidence
            : isM3Project
              ? m3ProjectAcceptanceEvidence
              : m2AcceptanceEvidence
          : null,
      })
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
    '> 自动生成文件。源：`frontend-admin/src/router/index.ts`。修改路由后运行 `pnpm generate:route-ledger`；CI 使用 `pnpm check:route-ledger` 防漂移。',
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

async function buildLedger() {
  const source = await readFile(routerPath, 'utf8')
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
    source: 'frontend-admin/src/router/index.ts',
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
