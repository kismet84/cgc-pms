import type { NavigationDomain } from '../types'

export const financeDomain: NavigationDomain = {
  id: 'finance',
  label: '资金财务',
  badge: '财',
  workspaces: [
    {
      id: 'receivables-payables',
      label: '收付款与发票',
      defaultPath: '/payment/application',
      tabs: [
        {
          path: '/payment/application',
          label: '付款申请',
          permission: 'payment:app:query',
        },
        {
          path: '/payment/expense',
          label: '费用申请',
          permission: 'expense:query',
        },
        {
          path: '/revenue',
          label: '收入与回款',
          permission: 'revenue:operations:query',
        },
        {
          path: '/invoice',
          label: '发票管理',
          permission: 'invoice:query',
        },
      ],
    },
    {
      id: 'cash',
      label: '资金运营',
      defaultPath: '/cash-journal',
      tabs: [
        {
          path: '/cash-journal',
          label: '资金日记账',
          permission: 'cashbook:journal:query',
        },
        {
          path: '/fund-accounts',
          label: '资金账户',
          permission: 'cashbook:journal:query',
        },
        {
          path: '/finance-operations',
          label: '资金运营',
          permission: 'finance:operations:query',
        },
        {
          path: '/cash-forecast',
          label: '项目资金预测',
          permission: 'finance:forecast:query',
        },
      ],
    },
    {
      id: 'accounting',
      label: '财务核算',
      defaultPath: '/accounting-entry',
      tabs: [
        {
          path: '/accounting-entry',
          label: '会计凭证',
          permission: 'accounting:query',
        },
        {
          path: '/financial-close',
          label: '财务核算与月结',
          permission: 'finance:close:query',
        },
      ],
    },
  ],
}
