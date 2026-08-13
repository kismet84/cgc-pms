import type { NavigationDomain } from '../types'

export const commercialDomain: NavigationDomain = {
  id: 'commercial',
  label: '商务合约',
  badge: '商',
  workspaces: [
    {
      id: 'contracts',
      label: '合同与变更',
      defaultPath: '/contract/ledger',
      matchPrefixes: ['/contract', '/variation'],
      tabs: [
        {
          path: '/contract/ledger',
          label: '合同台账',
          permission: 'contract:query',
        },
        {
          path: '/variation/order',
          label: '签证变更',
          permission: 'variation:order:query',
        },
      ],
    },
    {
      id: 'cost-budget',
      label: '成本预算与产值',
      defaultPath: '/cost-budget',
      matchPrefixes: ['/cost-budget', '/cost-target', '/budget'],
      tabs: [
        {
          path: '/cost-budget',
          label: '项目成本预算',
          permission: 'cost:target:query',
        },
        {
          path: '/production-measurement',
          label: '产值计量',
          permission: 'measurement:query',
        },
      ],
    },
    {
      id: 'cost-control',
      label: '成本核算与控制',
      defaultPath: '/cost/ledger',
      matchPrefixes: ['/cost'],
      tabs: [
        {
          path: '/cost/ledger',
          label: '成本台账',
          permission: 'cost:ledger:query',
        },
        {
          path: '/cost/summary',
          label: '成本核对',
          permission: 'cost:summary:view',
        },
        {
          path: '/cost/control',
          label: '动态利润控制',
          permission: 'cost:control:query',
        },
      ],
    },
  ],
}
