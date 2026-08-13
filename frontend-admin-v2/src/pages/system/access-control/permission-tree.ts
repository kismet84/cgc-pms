import { navigationDomains, type NavigationDomain } from '@/navigation/catalog'
import type { MenuRecord } from '@/services/system-management'

export type PermissionNode = {
  id: string
  label: string
  menuType: MenuRecord['menuType']
  path?: string
  perms?: string
  status: string
  menuIds: string[]
  children: PermissionNode[]
}

export type PermissionRow = {
  node: PermissionNode
  depth: number
  hasChildren: boolean
}

const menuTypeOrder: Record<MenuRecord['menuType'], number> = { DIR: 0, MENU: 1, BUTTON: 2 }

function compareMenus(left: MenuRecord, right: MenuRecord): number {
  return (
    menuTypeOrder[left.menuType] - menuTypeOrder[right.menuType] ||
    left.orderNum - right.orderNum ||
    left.menuName.localeCompare(right.menuName)
  )
}

function normalizePath(value?: string): string {
  return (value ?? '').replace(/^\/v2(?=\/|$)/, '').replace(/\/+$/, '')
}

export function buildPermissionTree(
  menus: MenuRecord[],
  domains: NavigationDomain[] = navigationDomains,
): PermissionNode[] {
  const claimed = new Set<string>()
  const roots: PermissionNode[] = []
  const tabNodes = new Map<string, PermissionNode>()
  const containerNodes = new Map<string, PermissionNode>()
  const childrenByParent = new Map<string, MenuRecord[]>()
  for (const menu of menus) {
    const children = childrenByParent.get(menu.parentId) ?? []
    children.push(menu)
    childrenByParent.set(menu.parentId, children)
  }
  for (const children of childrenByParent.values()) children.sort(compareMenus)

  const navigationTabs = domains.flatMap((domain) =>
    domain.workspaces.flatMap((workspace) => workspace.tabs),
  )
  const navigationRecordIds = new Set(
    menus
      .filter(
        (menu) =>
          menu.menuType !== 'BUTTON' &&
          navigationTabs.some(
            (tab) =>
              (Boolean(tab.permission) && menu.perms === tab.permission) ||
              normalizePath(menu.path) === normalizePath(tab.path),
          ),
      )
      .map((menu) => menu.id),
  )
  const take = (predicate: (menu: MenuRecord) => boolean): MenuRecord | undefined =>
    menus.find((menu) => !claimed.has(menu.id) && predicate(menu))
  const actualNode = (menu: MenuRecord, label = menu.menuName): PermissionNode => {
    claimed.add(menu.id)
    return {
      id: menu.id,
      label,
      menuType: menu.menuType,
      path: menu.path,
      perms: menu.perms,
      status: menu.status,
      menuIds: [menu.id],
      children: (childrenByParent.get(menu.id) ?? [])
        .filter((child) => !claimed.has(child.id) && !navigationRecordIds.has(child.id))
        .map((child) => actualNode(child)),
    }
  }

  domains.forEach((domain, domainIndex) => {
    const domainRecord = take((menu) => menu.menuType === 'DIR' && menu.menuName === domain.label)
    if (domainRecord) claimed.add(domainRecord.id)
    const domainNode: PermissionNode = {
      id: `navigation:domain:${domain.id}`,
      label: domain.label,
      menuType: 'DIR',
      status: domainRecord?.status ?? 'ENABLE',
      menuIds: domainRecord ? [domainRecord.id] : [],
      children: [],
    }
    containerNodes.set(`domain:${domain.id}`, domainNode)

    domain.workspaces.forEach((workspace, workspaceIndex) => {
      const workspaceRecord = take(
        (menu) => menu.menuType === 'DIR' && menu.menuName === workspace.label,
      )
      if (workspaceRecord) claimed.add(workspaceRecord.id)
      const workspaceNode: PermissionNode = {
        id: `navigation:workspace:${domain.id}:${workspace.id}`,
        label: workspace.label,
        menuType: 'DIR',
        status: workspaceRecord?.status ?? 'ENABLE',
        menuIds: workspaceRecord ? [workspaceRecord.id] : [],
        children: [],
      }
      containerNodes.set(`workspace:${domain.id}:${workspace.id}`, workspaceNode)

      workspace.tabs.forEach((tab) => {
        const tabRecord = take(
          (menu) =>
            menu.menuType !== 'BUTTON' &&
            ((Boolean(tab.permission) && menu.perms === tab.permission) ||
              normalizePath(menu.path) === normalizePath(tab.path)),
        )
        const tabNode = tabRecord
          ? actualNode(tabRecord, tab.label)
          : {
              id: `navigation:tab:${domain.id}:${workspace.id}:${normalizePath(tab.path)}`,
              label: tab.label,
              menuType: 'MENU' as const,
              path: tab.path,
              perms: tab.permission,
              status: 'MISSING',
              menuIds: [],
              children: [],
            }
        tabNodes.set(normalizePath(tab.path), tabNode)
        workspaceNode.children.push(tabNode)
      })
      if (
        workspaceNode.menuIds.length ||
        workspaceNode.children.some((node) => node.menuIds.length || node.children.length)
      ) {
        workspaceNode.id += `:${workspaceIndex}`
        domainNode.children.push(workspaceNode)
      }
    })
    if (domainNode.menuIds.length || domainNode.children.length) {
      domainNode.id += `:${domainIndex}`
      roots.push(domainNode)
    }
  })

  const legacyContainerTargets: Record<string, string> = {
    '/master-data': 'domain:master-data',
    '/contract': 'domain:commercial',
    '/contract-domain': 'domain:commercial',
    '/cost-domain': 'domain:commercial',
    '/procurement-inventory': 'domain:supply',
    '/system': 'domain:system-management',
    '/subcontract-domain': 'domain:subcontract-settlement',
    '/payment-invoice': 'domain:finance',
    '/inventory': 'workspace:supply:inventory',
    '/invoice': 'workspace:finance:receivables-payables',
    '/approval-center': 'workspace:system-management:workflow',
    '/alert': 'workspace:workbench:cockpit',
  }
  for (const menu of menus) {
    if (claimed.has(menu.id) || menu.menuType !== 'DIR') continue
    const target = containerNodes.get(legacyContainerTargets[normalizePath(menu.path)])
    if (!target) continue
    target.menuIds.push(menu.id)
    claimed.add(menu.id)
  }

  const contextualTargets: Record<string, string> = {
    'contract:submit': '/contract/ledger',
    'contract:change:submit': '/contract/ledger',
    'variation:order:submit': '/variation/order',
    'purchase:order:submit': '/purchase/order',
    'receipt:submit': '/purchase/receipt',
    'subcontract:measure:submit': '/subcontract/measure',
    'settlement:submit': '/settlement/list',
    'payment:app:submit': '/payment/application',
    'workflow:approve': '/approval/todo',
    'workflow:reject': '/approval/todo',
    'workflow:transfer': '/approval/todo',
    'workflow:add-sign': '/approval/todo',
    'workflow:withdraw': '/approval/mine',
    'workflow:resubmit': '/approval/mine',
    'project:member:list': '/project/list',
    'project:member:add': '/project/list',
    'project:member:edit': '/project/list',
    'project:member:delete': '/project/list',
    'inventory:transaction:list': '/inventory/stock',
    'inventory:transaction:add': '/inventory/stock',
    'alert:view': '/dashboard',
    'alert:edit': '/dashboard',
    'alert:evaluate': '/dashboard',
    'payment:record:query': '/finance-operations',
    'payment:record:reverse': '/finance-operations',
    'payment:record:writeback': '/finance-operations',
    'payment:trace:query': '/finance-operations',
    'system:user:query': '/system/users',
    'system:role:query': '/system/roles',
    'system:menu:query': '/system/permissions',
    'project:query': '/project/list',
    'contract:query': '/contract/ledger',
    'partner:query': '/partner',
    'org:query': '/org',
  }
  for (const menu of [...menus].sort(compareMenus)) {
    if (claimed.has(menu.id) || !menu.perms) continue
    const target = tabNodes.get(contextualTargets[menu.perms])
    if (target) target.children.push(actualNode(menu))
  }

  const notificationDirectory = take((menu) => menu.id === '730')
  if (notificationDirectory) claimed.add(notificationDirectory.id)
  const notificationMenu = take(
    (menu) =>
      menu.id === '761' || menu.perms === 'notification:view' || menu.perms === 'notification:edit',
  )
  if (notificationDirectory || notificationMenu) {
    roots.push({
      id: 'navigation:global',
      label: '全局功能',
      menuType: 'DIR',
      status: 'ENABLE',
      menuIds: notificationDirectory ? [notificationDirectory.id] : [],
      children: notificationMenu ? [actualNode(notificationMenu, '顶栏通知中心')] : [],
    })
  }

  const unclaimed = new Set(menus.filter((menu) => !claimed.has(menu.id)).map((menu) => menu.id))
  const unmatched = menus
    .filter((menu) => unclaimed.has(menu.id) && !unclaimed.has(menu.parentId))
    .sort(compareMenus)
    .map((menu) => actualNode(menu))
  if (unmatched.length) {
    roots.push({
      id: 'navigation:unmatched',
      label: '待治理配置（非导航入口）',
      menuType: 'DIR',
      status: 'REVIEW',
      menuIds: [],
      children: unmatched,
    })
  }
  return roots
}

export function buildPermissionNodeMap(tree: PermissionNode[]): Map<string, PermissionNode> {
  const result = new Map<string, PermissionNode>()
  const visit = (node: PermissionNode): void => {
    result.set(node.id, node)
    node.children.forEach(visit)
  }
  tree.forEach(visit)
  return result
}

export function flattenPermissionTree(
  tree: PermissionNode[],
  expandedIds: Set<string>,
): PermissionRow[] {
  const rows: PermissionRow[] = []
  const visit = (node: PermissionNode, depth: number): void => {
    rows.push({ node, depth, hasChildren: node.children.length > 0 })
    if (expandedIds.has(node.id)) node.children.forEach((child) => visit(child, depth + 1))
  }
  tree.forEach((node) => visit(node, 0))
  return rows
}

export function collectSubtreeMenuIds(
  nodeMap: Map<string, PermissionNode>,
  nodeId: string,
): string[] {
  const result = new Set<string>()
  const visit = (node: PermissionNode): void => {
    node.menuIds.forEach((id) => result.add(id))
    node.children.forEach(visit)
  }
  const node = nodeMap.get(nodeId)
  if (node) visit(node)
  return [...result]
}
