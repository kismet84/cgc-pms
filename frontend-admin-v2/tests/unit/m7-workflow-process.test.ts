import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import WorkflowProcessPage from '@/pages/system/WorkflowProcessPage.vue'
import * as processService from '@/services/workflow-process'
import * as systemService from '@/services/system-management'
import * as masterDataService from '@/services/master-data'

vi.mock('@/services/workflow-process', () => ({
  createWorkflowTemplateNode: vi.fn(),
  deleteWorkflowTemplateNode: vi.fn(),
  loadWorkflowTemplate: vi.fn(),
  loadWorkflowTemplates: vi.fn(),
  reorderWorkflowTemplateNodes: vi.fn(),
  updateWorkflowTemplate: vi.fn(),
  updateWorkflowTemplateNode: vi.fn(),
}))

vi.mock('@/services/system-management', () => ({
  loadRoles: vi.fn(),
  loadUsers: vi.fn(),
}))

vi.mock('@/services/master-data', () => ({
  loadPositions: vi.fn(),
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

const projectSummary = {
  id: '11',
  templateCode: 'FLOW-PROJECT',
  templateName: '项目立项审批',
  businessType: 'PROJECT_APPROVAL',
  enabled: 1,
  nodeCount: 1,
}

const projectDetail = {
  ...projectSummary,
  nodes: [
    {
      id: '31',
      templateId: '11',
      nodeCode: 'N1',
      nodeName: '项目立项审批',
      nodeOrder: 1,
      nodeType: 'APPROVAL',
      approveMode: 'SEQUENTIAL',
      approverConfig: '{"type":"ROLE","roleId":2}',
      allowTransfer: 1,
      allowAddSign: 1,
    },
  ],
}

const scheduleSummary = {
  id: '12',
  templateCode: 'FLOW-PROJECT-SCHEDULE',
  templateName: '项目计划审批',
  businessType: 'PROJECT_SCHEDULE',
  enabled: 1,
  nodeCount: 1,
}

const scheduleDetail = {
  ...scheduleSummary,
  nodes: [],
}

function button(text: string): HTMLButtonElement {
  const target = [...document.body.querySelectorAll<HTMLButtonElement>('button')].find(
    (item) => item.textContent?.trim() === text,
  )
  if (!target) throw new Error(`missing button: ${text}`)
  return target
}

function selectByLabel(text: string): HTMLSelectElement {
  const label = [...document.body.querySelectorAll<HTMLLabelElement>('label')].find(
    (item) => item.textContent?.replace('*', '').trim() === text,
  )
  const target = label?.htmlFor ? document.getElementById(label.htmlFor) : null
  if (!(target instanceof HTMLSelectElement)) throw new Error(`missing select: ${text}`)
  return target
}

beforeEach(() => {
  vi.clearAllMocks()
  document.body.innerHTML = ''
  vi.mocked(processService.loadWorkflowTemplates).mockResolvedValue({
    pageNo: 1,
    pageSize: 200,
    total: 3,
    records: [summary, projectSummary, scheduleSummary],
  })
  vi.mocked(processService.loadWorkflowTemplate).mockImplementation(async (id) => {
    if (id === '11') return projectDetail
    if (id === '12') return scheduleDetail
    return detail
  })
  vi.mocked(processService.updateWorkflowTemplate).mockResolvedValue()
  vi.mocked(processService.updateWorkflowTemplateNode).mockResolvedValue()
  vi.mocked(processService.reorderWorkflowTemplateNodes).mockResolvedValue()
  vi.mocked(systemService.loadUsers).mockResolvedValue({
    pageNo: 1,
    pageSize: 1000,
    total: 1,
    records: [
      {
        id: '1',
        username: 'manager',
        realName: '工程经理',
        status: 'ENABLE',
        roleNames: [],
        roleIds: [],
      },
    ],
  })
  vi.mocked(systemService.loadRoles).mockResolvedValue([
    {
      id: '2',
      roleCode: 'GENERAL_MANAGER',
      roleName: '总经理',
      status: 'ENABLE',
      dataScope: 'ALL',
      menuIds: [],
    },
  ])
  vi.mocked(masterDataService.loadPositions).mockResolvedValue({
    pageNo: 1,
    pageSize: 1000,
    total: 1,
    records: [
      {
        id: '3',
        companyId: '1',
        departmentId: '1',
        positionCode: 'PM',
        positionName: '项目经理岗',
        status: 'ENABLE',
      },
    ],
  })
})

describe('M7 workflow process page', () => {
  it('renders server template and stable amount strings', async () => {
    const wrapper = mount(WorkflowProcessPage, { attachTo: document.body })
    await flushPromises()
    expect(wrapper.text()).toContain('合同审批')
    expect(wrapper.text()).not.toContain('CONTRACT_APPROVAL')
    expect(processService.loadWorkflowTemplates).toHaveBeenCalledWith(
      expect.objectContaining({ pageNo: 1, pageSize: 200 }),
      expect.any(AbortSignal),
    )
    expect(wrapper.text()).not.toContain('筛选流程')
    expect(
      wrapper.find('.workflow-process-page__filters').element.closest('.v2-card__header'),
    ).not.toBeNull()

    expect(wrapper.text()).toContain('1. 业务模块')
    expect(wrapper.text()).toContain('2. 流程模板')
    expect(wrapper.text()).toContain('3. 流程配置')
    expect(wrapper.text()).toContain('基本设置')
    expect(wrapper.text()).toContain('审批节点')
    expect(document.body.textContent).toContain('项目经理审批')
    expect(document.body.textContent).toContain('100.2300 ～ 900.4500')
    expect(document.body.querySelector('[role="dialog"]')).toBeNull()
  })

  it('groups business types by module and filters templates by the selected module', async () => {
    const wrapper = mount(WorkflowProcessPage, { attachTo: document.body })
    await flushPromises()

    const module = wrapper.findAll('button').find((item) => item.text().includes('项目履约'))
    expect(module).toBeDefined()
    await module!.trigger('click')
    await flushPromises()

    expect(processService.loadWorkflowTemplate).toHaveBeenLastCalledWith('11')
    const templateColumn = wrapper.find('[aria-labelledby="workflow-templates-title"]')
    expect(templateColumn.text()).toContain('项目立项审批')
    expect(templateColumn.text()).toContain('项目计划审批')
    expect(templateColumn.text()).not.toContain('合同审批')
  })

  it('updates a template then rereads detail and list facts', async () => {
    mount(WorkflowProcessPage, { attachTo: document.body })
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
    expect(document.body.querySelectorAll('.v2-dialog__backdrop')).toHaveLength(0)
    expect(document.body.textContent).toContain('3. 流程配置')
  })

  it('reorders complete node ids then rereads server detail', async () => {
    mount(WorkflowProcessPage, { attachTo: document.body })
    await flushPromises()
    button('下移').click()
    await flushPromises()

    expect(processService.reorderWorkflowTemplateNodes).toHaveBeenCalledWith('10', ['22', '21'])
    expect(processService.loadWorkflowTemplate).toHaveBeenCalledTimes(2)
  })

  it('edits approvers through business selects without exposing JSON', async () => {
    mount(WorkflowProcessPage, { attachTo: document.body })
    await flushPromises()
    button('编辑').click()
    await flushPromises()

    expect(document.body.textContent).not.toContain('审批人配置 JSON')
    expect(selectByLabel('审批人类型').value).toBe('USER')
    expect(selectByLabel('审批人员').value).toBe('1')

    button('保存节点').click()
    await flushPromises()
    expect(processService.updateWorkflowTemplateNode).toHaveBeenCalledWith(
      '10',
      '21',
      expect.objectContaining({ approverConfig: '{"type":"USER","userId":"1"}' }),
    )
  })
})
