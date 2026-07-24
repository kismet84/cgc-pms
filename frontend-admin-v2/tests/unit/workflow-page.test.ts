import type { WorkflowInstance } from '@cgc-pms/frontend-contracts'
import { createPinia, setActivePinia } from 'pinia'
import { flushPromises, mount } from '@vue/test-utils'
import { createMemoryHistory, createRouter } from 'vue-router'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import WorkflowWorkbenchPage from '@/pages/workbench/WorkflowWorkbenchPage.vue'
import {
  approveWorkflowTask,
  loadWorkflowBusinessTypes,
  loadWorkflowInstance,
  loadWorkflowList,
} from '@/services/workflow'
import { useSessionStore } from '@/stores/session'

vi.mock('@/services/workflow', () => ({
  loadWorkflowList: vi.fn(),
  loadWorkflowBusinessTypes: vi.fn(),
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
  businessId: '9001',
  businessCode: 'PAY-2026-001',
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
          businessId: '9001',
          businessCode: 'PAY-2026-001',
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
  vi.mocked(loadWorkflowBusinessTypes).mockReset().mockResolvedValue(['PAYMENT'])
  vi.mocked(loadWorkflowInstance).mockReset().mockResolvedValue(detail)
  vi.mocked(approveWorkflowTask).mockReset()
  document.body.innerHTML = ''
})

afterEach(() => {
  document.body.innerHTML = ''
})

describe('WorkflowWorkbenchPage', () => {
  it('hides redundant labels, opens detail from the event title, and keeps pagination', async () => {
    vi.mocked(loadWorkflowList).mockResolvedValue({
      records: [detail.nodes[0]!.tasks[0]!],
      total: 1,
      pageNo: 1,
      pageSize: 10,
    })
    const router = createRouter({
      history: createMemoryHistory(),
      routes: [
        {
          path: '/approval/todo',
          component: WorkflowWorkbenchPage,
          meta: { workflowTab: 'todo' },
        },
        {
          path: '/approval/instances/:instanceId',
          component: WorkflowWorkbenchPage,
          meta: { workflowTab: 'todo' },
        },
      ],
    })
    await router.push('/approval/todo')
    await router.isReady()
    const wrapper = mount(WorkflowWorkbenchPage, {
      global: { plugins: [router] },
    })
    await flushPromises()

    const heading = wrapper.get('h1')
    expect(heading.text()).toBe('审批工作台')
    expect(heading.classes()).toContain('v2-visually-hidden')
    expect(wrapper.get('.workflow-filter__keyword input').attributes('aria-label')).toBe(
      '搜索标题或业务编号',
    )
    expect(
      wrapper
        .findAll('.workflow-filter .v2-field__label')
        .every((label) => label.classes().includes('v2-visually-hidden')),
    ).toBe(true)
    const rowAction = wrapper.get('.workflow-table__title')
    expect(rowAction.text()).toBe('付款申请审批')
    expect(rowAction.classes()).toContain('v2-button--ghost')
    await rowAction.trigger('click')
    await flushPromises()
    expect(router.currentRoute.value.path).toBe('/approval/instances/81')
    expect(wrapper.get('[aria-label="审批任务分页"]').text()).toContain(
      '共 1 条上一页第 1 页下一页',
    )
    wrapper.unmount()
  })

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
    expect(document.body.querySelector('h1')?.textContent).toContain('审批工作台')
    expect(document.body.querySelector('[role="dialog"]')).not.toBeNull()
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

  it('keeps reject validation inside the action dialog', async () => {
    vi.mocked(loadWorkflowInstance).mockResolvedValue({
      ...detail,
      availableActions: ['reject'],
    })
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
      permissions: ['workflow:instance:query', 'workflow:reject'],
    }
    session.status = 'authenticated'
    const wrapper = mount(WorkflowWorkbenchPage, {
      attachTo: document.body,
      global: { plugins: [router] },
    })
    await flushPromises()

    const reject = [...document.body.querySelectorAll('button')].find(
      (button) => button.textContent?.trim() === '驳回',
    ) as HTMLButtonElement
    reject.click()
    await flushPromises()
    const confirm = [...document.body.querySelectorAll('button')].find(
      (button) => button.textContent?.trim() === '确认提交',
    ) as HTMLButtonElement
    confirm.click()
    await flushPromises()

    expect(document.body.textContent).toContain('驳回必须填写原因')
    expect(document.body.querySelector('textarea')?.getAttribute('aria-invalid')).toBe('true')
    wrapper.unmount()
  })
})
