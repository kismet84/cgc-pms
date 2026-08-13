import type { NavigationDomain } from '../types'

export const masterDataDomain: NavigationDomain = {
  id: 'master-data',
  label: '基础资料',
  badge: '基',
  workspaces: [
    {
      id: 'partners',
      label: '客户管理',
      defaultPath: '/partner',
      matchPrefixes: ['/partner'],
      tabs: [
        {
          path: '/partner',
          label: '客户管理',
          permission: 'partner:query',
        },
      ],
    },
    {
      id: 'organization',
      label: '组织架构',
      defaultPath: '/org',
      tabs: [{ path: '/org', label: '组织架构', permission: 'org:list' }],
    },
    {
      id: 'materials',
      label: '物资数据',
      defaultPath: '/material/dictionary',
      tabs: [
        {
          path: '/material/dictionary',
          label: '材料字典',
          permission: 'material:dict:list',
        },
      ],
    },
    {
      id: 'finance-data',
      label: '成本科目',
      defaultPath: '/cost/subject/taxonomy',
      matchPrefixes: ['/cost/subject'],
      tabs: [
        {
          path: '/cost/subject/taxonomy',
          label: '科目体系',
          permission: 'cost:query',
        },
        {
          path: '/cost/subject/rules',
          label: '归集规则',
          permission: 'cost:subject:rule:query',
        },
        {
          path: '/cost/subject/scope',
          label: '项目适用与目标成本',
          permission: 'cost:subject:scope:query',
        },
        {
          path: '/cost/subject/trace',
          label: '影响与转入追踪',
          permission: 'cost:subject:audit:query',
        },
      ],
    },
  ],
}

export const systemManagementDomain: NavigationDomain = {
  id: 'system-management',
  label: '系统管理',
  badge: '系',
  workspaces: [
    {
      id: 'workflow',
      label: '流程配置',
      defaultPath: '/approval/process',
      adminOnly: true,
      adminBypassesPermission: true,
      tabs: [
        {
          path: '/approval/process',
          label: '审批流程',
          permission: 'workflow:process:query',
          adminOnly: true,
          adminBypassesPermission: true,
        },
      ],
    },
    {
      id: 'access-control',
      label: '访问控制',
      defaultPath: '/system/users',
      adminOnly: true,
      tabs: [
        {
          path: '/system/users',
          label: '用户管理',
          permission: 'system:user:query',
        },
        {
          path: '/system/roles',
          label: '角色管理',
          permission: 'system:role:query',
        },
        {
          path: '/system/permissions',
          label: '权限清单',
          permission: 'system:menu:query',
        },
      ],
    },
    {
      id: 'configuration',
      label: '系统配置',
      defaultPath: '/system/dict',
      adminOnly: true,
      tabs: [
        {
          path: '/system/dict',
          label: '字典管理',
          permission: 'system:dict:list',
        },
        {
          path: '/system/document-templates',
          label: '业务单据模板',
          permission: 'document:template:query',
        },
      ],
    },
    {
      id: 'audit',
      label: '操作审计',
      defaultPath: '/system/audit',
      tabs: [
        {
          path: '/system/audit',
          label: '操作审计',
          permission: 'audit:query',
        },
      ],
    },
    {
      id: 'data',
      label: '数据维护',
      defaultPath: '/system/data',
      superAdminOnly: true,
      tabs: [
        {
          path: '/system/data',
          label: '数据维护',
          superAdminOnly: true,
        },
      ],
    },
  ],
}
