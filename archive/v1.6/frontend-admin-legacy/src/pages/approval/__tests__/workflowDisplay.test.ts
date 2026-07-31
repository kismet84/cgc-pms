import { describe, expect, it, vi } from 'vitest'

vi.mock('@/utils/dict', () => ({
  fetchDictData: vi.fn(),
  getDictLabelSync: vi.fn(
    (_dictCode: string, dictValue: string, fallback?: Record<string, string>) => {
      return fallback?.[dictValue] ?? dictValue
    },
  ),
  getDictTagColorSync: vi.fn(
    (_dictCode: string, dictValue: string, fallback?: Record<string, string>) => {
      return fallback?.[dictValue] ?? 'default'
    },
  ),
}))

import {
  canAccessWorkflowBusinessEntry,
  coreBusinessTypeOptions,
  getWorkflowBusinessEntry,
  getWorkflowBusinessEntryPath,
  getWorkflowBusinessTypeLabel,
  workflowBusinessEntryRegistry,
} from '../workflowDisplay'

describe('workflowDisplay registry', () => {
  it('注册冻结入口及产值计量闭环入口', () => {
    expect(workflowBusinessEntryRegistry).toHaveLength(8)
    expect(workflowBusinessEntryRegistry.map((entry) => entry.businessType)).toEqual([
      'CONTRACT',
      'CONTRACT_APPROVAL',
      'PURCHASE_REQUEST',
      'SUB_MEASURE',
      'PRODUCTION_MEASUREMENT',
      'PROJECT_SCHEDULE',
      'PROJECT_PERIOD_PLAN',
      'PROJECT_CORRECTIVE_ACTION',
    ])
  })

  it('为首批入口返回统一的标签、权限码和目标路由', () => {
    expect(getWorkflowBusinessTypeLabel('CONTRACT_APPROVAL')).toBe('合同审批')
    expect(getWorkflowBusinessTypeLabel('PURCHASE_REQUEST')).toBe('采购申请')
    expect(getWorkflowBusinessTypeLabel('SUB_MEASURE')).toBe('分包计量')
    expect(getWorkflowBusinessTypeLabel('PRODUCTION_MEASUREMENT')).toBe('产值计量')
    expect(getWorkflowBusinessTypeLabel('PROJECT_SCHEDULE')).toBe('项目基线/修订计划')

    expect(getWorkflowBusinessEntry({ businessType: 'CONTRACT_APPROVAL' })).toMatchObject({
      displayName: '合同审批',
      permissionCode: 'contract:query',
      openMode: 'route',
      forbiddenPolicy: 'disabled-with-tooltip',
    })
    expect(
      getWorkflowBusinessEntryPath({
        businessType: 'CONTRACT_APPROVAL',
        businessId: '101',
      }),
    ).toBe('/contract/101')
    expect(
      getWorkflowBusinessEntryPath({
        businessType: 'PURCHASE_REQUEST',
        businessId: '202',
      }),
    ).toBe('/inventory/purchase-request?businessId=202')
    expect(
      getWorkflowBusinessEntryPath({
        businessType: 'SUB_MEASURE',
        businessId: '303',
      }),
    ).toBe('/subcontract/measure?businessId=303')
    expect(
      getWorkflowBusinessEntryPath({ businessType: 'PRODUCTION_MEASUREMENT', businessId: '404' }),
    ).toBe('/production-measurement?businessId=404')
    expect(
      getWorkflowBusinessEntryPath({
        businessType: 'PROJECT_CORRECTIVE_ACTION',
        businessId: '505',
      }),
    ).toBe('/project-schedule?businessType=PROJECT_CORRECTIVE_ACTION&businessId=505')
  })

  it('付款申请当前只有标签，没有 registry 入口', () => {
    expect(getWorkflowBusinessTypeLabel('PAY_APPLICATION')).toBe('付款申请')
    expect(getWorkflowBusinessTypeLabel('PAY_REQUEST')).toBe('付款申请')
    expect(getWorkflowBusinessEntry({ businessType: 'PAY_APPLICATION' })).toBeNull()
    expect(getWorkflowBusinessEntry({ businessType: 'PAY_REQUEST' })).toBeNull()
    expect(getWorkflowBusinessEntryPath({ businessType: 'PAY_REQUEST', businessId: '9' })).toBe('')
  })

  it('未知业务类型不会误显入口', () => {
    expect(getWorkflowBusinessTypeLabel('UNKNOWN_TYPE')).toBe('未知业务类型')
    expect(getWorkflowBusinessEntry({ businessType: 'UNKNOWN_TYPE' })).toBeNull()
    expect(getWorkflowBusinessEntryPath({ businessType: 'UNKNOWN_TYPE', businessId: '7' })).toBe('')
    expect(
      getWorkflowBusinessEntryPath({ businessType: 'CONTRACT_APPROVAL', businessId: '' }),
    ).toBe('')
  })

  it('按冻结口径生成业务筛选项，不包含合同别名和付款申请', () => {
    expect(coreBusinessTypeOptions).toEqual([
      { label: '合同审批', value: 'CONTRACT_APPROVAL' },
      { label: '采购申请', value: 'PURCHASE_REQUEST' },
      { label: '分包计量', value: 'SUB_MEASURE' },
      { label: '产值计量', value: 'PRODUCTION_MEASUREMENT' },
      { label: '项目基线/修订计划', value: 'PROJECT_SCHEDULE' },
      { label: '项目月周计划', value: 'PROJECT_PERIOD_PLAN' },
      { label: '项目进度纠偏', value: 'PROJECT_CORRECTIVE_ACTION' },
    ])
  })

  it('保留管理员旁路和目标权限判断', () => {
    const hasPermission = vi.fn((code: string) => code === 'purchase:request:list')

    expect(
      canAccessWorkflowBusinessEntry({ businessType: 'PURCHASE_REQUEST' }, hasPermission, []),
    ).toBe(true)
    expect(canAccessWorkflowBusinessEntry({ businessType: 'SUB_MEASURE' }, hasPermission, [])).toBe(
      false,
    )
    expect(
      canAccessWorkflowBusinessEntry({ businessType: 'SUB_MEASURE' }, hasPermission, ['ADMIN']),
    ).toBe(true)
    expect(
      canAccessWorkflowBusinessEntry({ businessType: 'PAY_REQUEST' }, hasPermission, ['ADMIN']),
    ).toBe(false)
  })
})
