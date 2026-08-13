import type { NavigationDomain } from '../types'

export const workbenchDomain: NavigationDomain = {
  id: 'workbench',
  label: '工作台',
  badge: '台',
  workspaces: [
    {
      id: 'cockpit',
      label: '经营驾驶舱',
      defaultPath: '/dashboard',
      tabs: [
        {
          path: '/dashboard',
          label: '驾驶舱',
          permission: 'dashboard:view',
        },
      ],
    },
    {
      id: 'my-work',
      label: '我的工作',
      defaultPath: '/approval/todo',
      matchPrefixes: ['/approval'],
      tabs: [
        {
          path: '/approval/todo',
          label: '待我处理',
        },
        {
          path: '/approval/done',
          label: '我已处理',
        },
        {
          path: '/approval/cc',
          label: '抄送我的',
        },
        {
          path: '/approval/mine',
          label: '我发起',
        },
      ],
    },
    {
      id: 'reports',
      label: '报表中心',
      defaultPath: '/dashboard/reports',
      permission: 'report:catalog:query',
      tabs: [{ path: '/dashboard/reports', label: '报表目录', permission: 'report:catalog:query' }],
    },
    {
      id: 'communication',
      label: '站内通讯',
      defaultPath: '/communication',
      adminBypassesPermission: true,
      tabs: [
        {
          path: '/communication',
          label: '消息',
          permission: 'communication:view',
          adminBypassesPermission: true,
        },
      ],
    },
  ],
}
