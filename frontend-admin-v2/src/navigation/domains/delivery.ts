import type { NavigationDomain } from '../types'

export const deliveryDomain: NavigationDomain = {
  id: 'delivery',
  label: '项目履约',
  badge: '项',
  workspaces: [
    {
      id: 'engineering-tender-workspace',
      label: '工程投标',
      defaultPath: '/engineering-tender/records',
      matchPrefixes: ['/engineering-tender'],
      tabs: [
        { path: '/engineering-tender/records', label: '投标记录', permission: 'bid:query' },
        { path: '/engineering-tender/costs', label: '投标成本', permission: 'bid:cost:query' },
      ],
    },
    {
      id: 'projects',
      label: '项目管理',
      defaultPath: '/project/list',
      matchPrefixes: ['/project'],
      tabs: [
        {
          path: '/project/list',
          label: '项目列表',
          permission: 'project:query',
        },
        {
          path: '/project/files',
          label: '文件中心',
          permission: 'project:file:query',
        },
      ],
    },
    {
      id: 'control',
      label: '技术管理',
      defaultPath: '/technical-management',
      tabs: [
        {
          path: '/technical-management',
          label: '图纸 RFI 技术闭环',
          permission: 'technical:query',
        },
      ],
    },
    {
      id: 'closeout',
      label: '项目收尾',
      defaultPath: '/project-closeout',
      tabs: [
        {
          path: '/project-closeout',
          label: '竣工收尾',
          permission: 'closeout:query',
        },
      ],
    },
  ],
}

export const constructionDomain: NavigationDomain = {
  id: 'construction',
  label: '施工管理',
  badge: '施',
  workspaces: [
    {
      id: 'construction-execution',
      label: '项目计划与施工履约',
      defaultPath: '/project-schedule',
      matchPrefixes: ['/project-schedule'],
      tabs: [
        {
          path: '/project-schedule',
          label: '项目计划',
          permission: 'schedule:query',
        },
        {
          path: '/site/daily-log',
          label: '施工履约',
          permission: 'site:daily:query',
          permissions: ['site:daily:query', 'site:daily:self'],
        },
      ],
    },
    {
      id: 'construction-quality',
      label: '质量安全整改闭环',
      defaultPath: '/quality-safety',
      tabs: [
        {
          path: '/quality-safety',
          label: '质量安全整改闭环',
          permission: 'quality:safety:query',
        },
      ],
    },
  ],
}
