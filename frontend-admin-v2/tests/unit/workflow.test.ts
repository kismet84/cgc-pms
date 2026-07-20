import { canPerformWorkflowAction, type WorkflowTask } from '@cgc-pms/frontend-contracts'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import {
  WORKFLOW_BUSINESS_TYPES,
  workflowBusinessTypeLabel,
  workflowApproveModeLabel,
  workflowRows,
  workflowStatusLabel,
} from '@/pages/workbench/model'
import {
  approveWorkflowTask,
  loadWorkflowBusinessTypes,
  loadWorkflowList,
} from '@/services/workflow'
import { apiRequest } from '@/services/request'

vi.mock('@/services/request', () => ({ apiRequest: vi.fn() }))

const task: WorkflowTask = {
  id: '91',
  instanceId: '81',
  nodeInstanceId: '71',
  businessType: 'PAYMENT',
  businessId: 'PAY-2026-001',
  approverId: '1',
  approverName: '审批人',
  taskStatus: 'PENDING',
  roundNo: 1,
  taskVersion: 0,
  receivedAt: '2026-07-20T08:00:00',
  title: '付款审批',
  instanceStatus: 'RUNNING',
}

beforeEach(() => vi.mocked(apiRequest).mockReset())

describe('workflow contract and service', () => {
  it('requires both server action and client permission', () => {
    expect(canPerformWorkflowAction('approve', ['approve'], ['workflow:approve'])).toBe(true)
    expect(canPerformWorkflowAction('approve', [], ['workflow:approve'])).toBe(false)
    expect(canPerformWorkflowAction('approve', ['approve'], [])).toBe(false)
    expect(canPerformWorkflowAction('reject', ['reject'], [])).toBe(false)
    expect(canPerformWorkflowAction('reject', ['reject'], ['*'])).toBe(true)
  })

  it('normalizes each list row without inventing workflow facts', () => {
    expect(workflowRows('todo', [task])).toEqual([
      expect.objectContaining({
        instanceId: '81',
        businessId: 'PAY-2026-001',
        title: '付款审批',
        status: 'PENDING',
      }),
    ])
    expect(workflowStatusLabel('RUNNING')).toBe('审批中')
    expect(workflowStatusLabel('APPROVE')).toBe('已同意')
    expect(workflowStatusLabel('CUSTOM')).toBe('其他状态')
    expect(workflowBusinessTypeLabel('CONTRACT_APPROVAL')).toBe('合同审批')
    expect(workflowBusinessTypeLabel('DEMO_APPROVAL_SCENARIO')).toBe('演示审批场景')
    expect(workflowBusinessTypeLabel('UNREGISTERED')).toBe('其他业务审批')
    expect(WORKFLOW_BUSINESS_TYPES).toContainEqual(['PAY_REQUEST', '付款申请'])
    expect(workflowStatusLabel('ACTIVE')).toBe('处理中')
    expect(workflowApproveModeLabel('SEQUENTIAL')).toBe('顺序审批')
  })

  it('uses existing list and idempotent action endpoints', async () => {
    vi.mocked(apiRequest).mockResolvedValue({ records: [], total: 0, pageNo: 1, pageSize: 20 })
    await loadWorkflowList('todo', { pageNo: 1, pageSize: 20, keyword: '付款' })
    expect(apiRequest).toHaveBeenCalledWith(
      '/workflow/tasks/todo?pageNo=1&pageSize=20&keyword=%E4%BB%98%E6%AC%BE',
      expect.objectContaining({ signal: undefined }),
    )

    await approveWorkflowTask('91', {
      action: 'APPROVE',
      idempotencyKey: 'approval-91-once',
    })
    expect(apiRequest).toHaveBeenLastCalledWith('/workflow/tasks/91/approve', {
      method: 'POST',
      body: { action: 'APPROVE', idempotencyKey: 'approval-91-once' },
    })
  })

  it('loads business type options from the current user and tab scope', async () => {
    vi.mocked(apiRequest).mockResolvedValue(['CONTRACT_APPROVAL'])
    await loadWorkflowBusinessTypes('todo')
    expect(apiRequest).toHaveBeenCalledWith('/workflow/business-types?tab=todo', {
      signal: undefined,
    })
  })
})
