import { mount } from '@vue/test-utils'
import { describe, expect, it, vi } from 'vitest'
import DashboardCostView from '../components/DashboardCostView.vue'

const { pushMock, messageSuccessMock } = vi.hoisted(() => ({
  pushMock: vi.fn(),
  messageSuccessMock: vi.fn(),
}))

vi.mock('vue-router', () => ({
  useRouter: () => ({
    push: pushMock,
  }),
}))

vi.mock('ant-design-vue', () => ({
  message: {
    success: messageSuccessMock,
  },
}))

vi.mock('vue-echarts', () => ({
  default: {
    name: 'VChart',
    template: '<div class="chart-stub"></div>',
  },
}))

function mountCostView() {
  return mount(DashboardCostView, {
    props: {
      loading: false,
      breakdown: null,
      data: {
        projectId: 'project-1',
        projectName: '项目A',
        targetCost: '1000000',
        dynamicCost: '1200000',
        costDeviation: '200000',
        contractLockedCost: '300000',
        actualCost: '400000',
        estimatedRemainingCost: '500000',
        expectedProfit: '150000',
        contractIncome: '2000000',
        trendPoints: [
          {
            month: '2026-05',
            targetCost: '500000',
            dynamicCost: '600000',
            costDeviation: '100000',
          },
        ],
        subjectRankings: [
          {
            costSubjectId: 'sub-1',
            costSubjectName: '人工费',
            targetCost: '500000',
            actualCost: '400000',
            dynamicCost: '420000',
            costDeviation: '20000',
            ratio: '80',
          },
        ],
        overBudgetAlerts: [],
        overdueItems: [],
        pendingPayments: [],
        ledgerRows: [
          {
            rowType: 'cost',
            costSubjectId: 'sub-1',
            costSubjectName: '人工费',
            contractCode: 'CT-001',
            contractName: '测试合同',
            budgetAmount: '500000',
            actualAmount: '400000',
            completionRatio: '80.00%',
            deviationAmount: '20000',
            deviationRatio: '4.00%',
            status: '正常',
            ownerName: '张三',
          },
        ],
        ledgerTotal: 1,
      },
    },
    global: {
      stubs: {
        ATooltip: { template: '<div class="tooltip-stub"><slot /></div>' },
        ATag: { template: '<span class="tag-stub"><slot /></span>' },
        ASegmented: true,
        ASelect: true,
        ASelectOption: true,
        ARangePicker: true,
        AInput: true,
        AButton: true,
        APagination: true,
        AInputNumber: true,
        InfoCircleOutlined: true,
      },
    },
  })
}

describe('DashboardCostView', () => {
  it('mounts the split ledger panel and preserves key cost interactions', async () => {
    pushMock.mockReset()
    messageSuccessMock.mockReset()

    const wrapper = mountCostView()

    expect(wrapper.findComponent({ name: 'DashboardCostLedgerPanel' }).exists()).toBe(true)
    expect(wrapper.find('.cost-ledger-reference').exists()).toBe(true)

    await wrapper.find('.cost-rank-row').trigger('click')
    expect(wrapper.emitted('barClick')).toEqual([[{ name: '人工费' }]])

    await wrapper.find('.cost-ledger-actions a').trigger('click')
    expect(pushMock).toHaveBeenCalledWith({
      path: '/cost/ledger',
      query: { projectId: 'project-1', costSubjectId: 'sub-1' },
    })
  })
})
