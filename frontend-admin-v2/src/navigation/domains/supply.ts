import type { NavigationDomain } from '../types'

export const supplyDomain: NavigationDomain = {
  id: 'supply',
  label: '物资管理',
  badge: '物',
  workspaces: [
    {
      id: 'procurement',
      label: '采购执行',
      defaultPath: '/inventory/purchase-request',
      tabs: [
        {
          path: '/inventory/purchase-request',
          label: '采购申请',
          permission: 'purchase:request:list',
          permissions: ['purchase:request:list', 'purchase:request:self'],
        },
        { path: '/purchase/order', label: '采购订单', permission: 'purchase:order:query' },
        { path: '/purchase/receipt', label: '材料验收', permission: 'receipt:query' },
      ],
    },
    {
      id: 'inventory',
      label: '仓储库存',
      defaultPath: '/inventory/warehouse',
      tabs: [
        {
          path: '/inventory/warehouse',
          label: '仓库管理',
          permission: 'inventory:warehouse:list',
        },
        { path: '/inventory/stock', label: '库存台账', permission: 'inventory:stock:list' },
      ],
    },
    {
      id: 'requisition',
      label: '领用与退料',
      defaultPath: '/inventory/material-requisition',
      tabs: [
        {
          path: '/inventory/material-requisition',
          label: '领用与退料',
          permission: 'requisition:query',
          permissions: ['requisition:query', 'requisition:self'],
        },
      ],
    },
  ],
}

export const subcontractSettlementDomain: NavigationDomain = {
  id: 'subcontract-settlement',
  label: '分包结算',
  badge: '分',
  workspaces: [
    {
      id: 'performance',
      label: '分包履约',
      defaultPath: '/subcontract/task',
      tabs: [
        {
          path: '/subcontract/task',
          label: '分包任务',
          permission: 'subtask:query',
        },
        {
          path: '/subcontract/measure',
          label: '分包计量',
          permission: 'subcontract:measure:query',
        },
      ],
    },
    {
      id: 'settlements',
      label: '结算管理',
      defaultPath: '/settlement/list',
      matchPrefixes: ['/settlement'],
      tabs: [
        {
          path: '/settlement/list',
          label: '结算台账',
          permission: 'settlement:query',
        },
      ],
    },
  ],
}
