import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import WorkflowProcessPage from '@/pages/system/WorkflowProcessPage.vue'
import * as processService from '@/services/workflow-process'

vi.mock('@/services/workflow-process', () => ({
  createWorkflowTemplateNode: vi.fn(),
  deleteWorkflowTemplateNode: vi.fn(),
  loadWorkflowTemplate: vi.fn(),
  loadWorkflowTemplates: vi.fn(),
  reorderWorkflowTemplateNodes: vi.fn(),
  updateWorkflowTemplate: vi.fn(),
  updateWorkflowTemplateNode: vi.fn(),
}))

const summary = {
  id: '10',
  templateCode: 'FLOW-CONTRACT',
  templateName: '合同审批',
  businessType: 'CONTRACT_APPROVAL',
  enabled: 1,
  amountMin: '100.2300',
  amountMax: '900.4500',
  nodeCount: 2,
}

const detail = {
  ...summary,
  nodes: [
    {
      id: '21',
      templateId: '10',
      nodeCode: 'N1',
      nodeName: '项目经理审批',
      nodeOrder: 1,
      nodeType: 'APPROVAL',
      approveMode: 'SEQUENTIAL',
      approverConfig: '{"type":"USER","userId":1}',
      allowTransfer: 1,
      allowAddSign: 1,
    },
    {
      id: '22',
      templateId: '10',
      nodeCode: 'N2',
      nodeName: '总经理审批',
      nodeOrder: 2,
      nodeType: 'APPROVAL',
      approveMode: 'OR_SIGN',
      approverConfig: '{"type":"ROLE","roleId":2}',
      allowTransfer: 0,
      allowAddSign: 1,
    },
  ],
}

function button(text: string): HTMLButtonElement {
  const target = [...document.body.querySelectorAll<HTMLButtonElement>('button')].find(
    (item) => item.textContent?.trim() === text,
  )
  if (!target) throw new Error(`missing button: ${text}`)
  return target
}

function recordLink(text: string): HTMLButtonElement {
  const target = [
    ...document.body.querySelectorAll<HTMLButtonElement>('.v2-table__record-link'),
  ].find((item) => item.textContent?.trim() === text)
  if (!target) throw new Error(`missing record link: ${text}`)
  return target
}

beforeEach(() => {
  vi.clearAllMocks()
  document.body.innerHTML = ''
  vi.mocked(processService.loadWorkflowTemplates).mockResolvedValue({
    pageNo: 1,
    pageSize: 10,
    total: 1,
    records: [summary],
  })
  vi.mocked(processService.loadWorkflowTemplate).mockResolvedValue(detail)
  vi.mocked(processService.updateWorkflowTemplate).mockResolvedValue()
  vi.mocked(processService.reorderWorkflowTemplateNodes).mockResolvedValue()
})

describe('M7 workflow process page', () => {
  it('renders server template and stable amount strings', async () => {
    const wrapper = mount(WorkflowProcessPage, { attachTo: document.body })
    await flushPromises()
    expect(wrapper.text()).toContain('合同审批')
    expect(processService.loadWorkflowTemplates).toHaveBeenCalledWith(
      expect.objectContaining({ pageNo: 1, pageSize: 10 }),
      expect.any(AbortSignal),
    )
    expect(wrapper.text()).not.toContain('筛选流程')
    expect(
      wrapper.find('.workflow-process-page__filters').element.closest('.v2-card__header'),
    ).not.toBeNull()

    recordLink('FLOW-CONTRACT').click()
    await flushPromises()

    expect(document.body.textContent).toContain('审批流程详情')
    expect(document.body.textContent).toContain('项目经理审批')
    expect(document.body.textContent).toContain('100.2300 ～ 900.4500')
  })

  it('updates a template then rereads detail and list facts', async () => {
    mount(WorkflowProcessPage, { attachTo: document.body })
    await flushPromises()
    recordLink('FLOW-CONTRACT').click()
    await flushPromises()
    button('编辑模板').click()
    await flushPromises()

    expect(document.body.querySelectorAll('.v2-dialog__backdrop')).toHaveLength(1)
    expect(document.body.querySelector('[role="dialog"] h2')?.textContent).toContain('编辑审批流程')

    const name = document.body.querySelector<HTMLInputElement>('[aria-label="流程名称"]')!
    name.value = '合同审批-调整'
    name.dispatchEvent(new Event('input', { bubbles: true }))
    button('保存流程').click()
    await flushPromises()

    expect(processService.updateWorkflowTemplate).toHaveBeenCalledWith(
      '10',
      expect.objectContaining({
        templateName: '合同审批-调整',
        amountMin: '100.2300',
        amountMax: '900.4500',
      }),
    )
    expect(processService.loadWorkflowTemplate).toHaveBeenCalledTimes(2)
    expect(processService.loadWorkflowTemplates).toHaveBeenCalledTimes(2)
    expect(document.body.querySelectorAll('.v2-dialog__backdrop')).toHaveLength(1)
    expect(document.body.querySelector('[role="dialog"] h2')?.textContent).toContain('审批流程详情')
  })

  it('reorders complete node ids then rereads server detail', async () => {
    mount(WorkflowProcessPage, { attachTo: document.body })
    await flushPromises()
    recordLink('FLOW-CONTRACT').click()
    await flushPromises()

    button('下移').click()
    await flushPromises()

    expect(processService.reorderWorkflowTemplateNodes).toHaveBeenCalledWith('10', ['22', '21'])
    expect(processService.loadWorkflowTemplate).toHaveBeenCalledTimes(2)
  })
})
