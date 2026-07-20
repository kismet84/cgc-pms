import type { WorkflowInstance } from '@cgc-pms/frontend-contracts'
import { createPinia, setActivePinia } from 'pinia'
import { flushPromises, mount } from '@vue/test-utils'
import { createMemoryHistory, createRouter } from 'vue-router'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import WorkflowWorkbenchPage from '@/pages/workbench/WorkflowWorkbenchPage.vue'
import { approveWorkflowTask, loadWorkflowInstance, loadWorkflowList } from '@/services/workflow'
import { useSessionStore } from '@/stores/session'

vi.mock('@/services/workflow', () => ({
  loadWorkflowList: vi.fn(),
  loadWorkflowInstance: vi.fn(),
  approveWorkflowTask: vi.fn(),
  rejectWorkflowTask: vi.fn(),
  withdrawWorkflowInstance: vi.fn(),
  resubmitWorkflowInstance: vi.fn(),
  transferWorkflowTask: vi.fn(),
  addSignWorkflowTask: vi.fn(),
}))

const detail: WorkflowInstance = {
  id: '81',
  templateId: '1',
  templateName: '付款审批',
  businessType: 'PAYMENT',
  businessId: 'PAY-2026-001',
  title: '付款申请审批',
  instanceStatus: 'RUNNING',
  currentRound: 1,
  resubmitCount: 0,
  initiatorId: '8',
  initiatorName: '发起人',
  startedAt: '2026-07-20T08:00:00',
  availableActions: ['approve', 'reject'],
  nodes: [
    {
      id: '71',
      templateNodeId: '61',
      nodeCode: 'FINANCE',
      nodeName: '财务审批',
      nodeOrder: 1,
      approveMode: 'OR',
      nodeStatus: 'ACTIVE',
      roundNo: 1,
      tasks: [
        {
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
          title: '付款申请审批',
          instanceStatus: 'RUNNING',
        },
      ],
    },
  ],
  records: [],
}

beforeEach(() => {
  setActivePinia(createPinia())
  vi.mocked(loadWorkflowList).mockReset()
  vi.mocked(loadWorkflowInstance).mockReset().mockResolvedValue(detail)
  vi.mocked(approveWorkflowTask).mockReset()
  document.body.innerHTML = ''
})

afterEach(() => {
  document.body.innerHTML = ''
})

describe('WorkflowWorkbenchPage', () => {
  it('gates actions by server availability and permission, then blocks duplicate submission', async () => {
    let finish!: () => void
    vi.mocked(approveWorkflowTask).mockImplementation(
      () => new Promise<void>((resolve) => (finish = resolve)),
    )
    const router = createRouter({
      history: createMemoryHistory(),
      routes: [
        {
          path: '/approval/instances/:instanceId',
          component: WorkflowWorkbenchPage,
          meta: { workflowTab: 'todo' },
        },
        { path: '/approval/todo', component: { template: '<div />' } },
      ],
    })
    await router.push('/approval/instances/81')
    await router.isReady()
    const session = useSessionStore()
    session.userInfo = {
      userId: '1',
      username: 'approver',
      roles: ['USER'],
      permissions: ['workflow:instance:query', 'workflow:approve'],
    }
    session.status = 'authenticated'

    const wrapper = mount(WorkflowWorkbenchPage, {
      attachTo: document.body,
      global: { plugins: [router] },
    })
    await flushPromises()

    expect(document.body.textContent).toContain('同意')
    expect(
      [...document.body.querySelectorAll('button')].some(
        (button) => button.textContent?.trim() === '驳回',
      ),
    ).toBe(false)
    const approve = [...document.body.querySelectorAll('button')].find(
      (button) => button.textContent?.trim() === '同意',
    ) as HTMLButtonElement
    approve.click()
    await flushPromises()
    const confirm = [...document.body.querySelectorAll('button')].find(
      (button) => button.textContent?.trim() === '确认提交',
    ) as HTMLButtonElement
    confirm.click()
    confirm.click()
    await flushPromises()

    expect(approveWorkflowTask).toHaveBeenCalledTimes(1)
    expect(approveWorkflowTask).toHaveBeenCalledWith(
      '91',
      expect.objectContaining({ action: 'APPROVE', idempotencyKey: expect.any(String) }),
    )
    finish()
    await flushPromises()
    expect(loadWorkflowInstance).toHaveBeenCalledTimes(2)
    wrapper.unmount()
  })
})
