import { mount } from '@vue/test-utils'
import { defineComponent, h } from 'vue'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import DashboardPurchaseView from '../components/DashboardPurchaseView.vue'

const { routerPush } = vi.hoisted(() => ({ routerPush: vi.fn() }))

vi.mock('vue-router', () => ({
  useRouter: () => ({ push: routerPush }),
}))

const ATableStub = defineComponent({
  name: 'ATableStub',
  props: {
    columns: Array,
    dataSource: Array,
    locale: Object,
  },
  setup(props, { slots }) {
    return () => {
      const columns = (props.columns as Array<Record<string, unknown>>) ?? []
      const rows = (props.dataSource as Array<Record<string, unknown>>) ?? []

      return h('table', { class: 'table-stub' }, [
        h('thead', columns.map((column) => h('th', String(column.title ?? '')))),
        h(
          'tbody',
          rows.length
            ? rows.map((record, rowIndex) =>
                h(
                  'tr',
                  columns.map((column) =>
                    h(
                      'td',
                      slots.bodyCell
                        ? slots.bodyCell({
                            column,
                            record,
                            text: record[column.dataIndex as string],
                            index: rowIndex,
                          })
                        : String(record[column.dataIndex as string] ?? ''),
                    ),
                  ),
                ),
              )
            : h('tr', h('td', String((props.locale as { emptyText?: string })?.emptyText ?? ''))),
        ),
      ])
    }
  },
})

const stubs = {
  ATable: ATableStub,
  AButton: defineComponent({
    setup(_, { attrs, slots }) {
      return () => h('button', attrs, slots.default?.())
    },
  }),
  ATooltip: { template: '<span><slot /></span>' },
  AuditOutlined: true,
  ShoppingCartOutlined: true,
  WarningOutlined: true,
  InboxOutlined: true,
}

function baseData(overrides: Record<string, unknown> = {}) {
  return {
    projectId: 'project-1',
    projectName: '全部项目',
    pendingRequestCount: 0,
    activeOrderCount: 0,
    overdueDeliveryCount: 0,
    pendingReceiptCount: 0,
    lowStockItemCount: 0,
    totalOrderAmount: '0',
    recentRequests: [],
    purchaseOrders: [],
    overdueOrders: [],
    pendingReceipts: [],
    supplierScores: [],
    ...overrides,
  }
}

function mountPurchaseView(data: Record<string, unknown>) {
  return mount(DashboardPurchaseView, {
    props: { data, loading: false },
    global: { stubs },
  })
}

describe('DashboardPurchaseView', () => {
  beforeEach(() => {
    routerPush.mockReset()
  })

  it('renders supplier delivery score rows from the dashboard payload', () => {
    const wrapper = mountPurchaseView(
      baseData({
        supplierScores: [
          {
            partnerId: 'supplier-1',
            partnerName: '华北钢材',
            orderCount: 8,
            overdueOrderCount: 1,
            lateCompletedCount: 2,
            overdueIncompleteCount: 1,
            onTimeDeliveryRate: '87.50',
            performanceScore: '88',
          },
        ],
      }),
    )

    const text = wrapper.text()
    expect(text).toContain('供应商采购订单交期表现')
    expect(text).toContain('仅展示采购订单交期表现，不代表供应商综合评级')
    expect(text).toContain('华北钢材')
    expect(text).toContain('迟交完成数')
    expect(text).toContain('逾期未完成数')
    expect(text).toContain('8')
    expect(text).toContain('1')
    expect(text).toContain('87.50%')
    expect(text).toContain('88')
  })

  it('renders the supplier score empty state', () => {
    const wrapper = mountPurchaseView(baseData())

    expect(wrapper.text()).toContain('暂无供应商采购订单交期表现数据')
  })

  it('drills a supplier score into purchase orders with the existing partner filter', async () => {
    const wrapper = mountPurchaseView(
      baseData({
        supplierScores: [
          {
            partnerId: 'supplier-1',
            partnerName: '华北钢材',
            orderCount: 8,
            overdueOrderCount: 1,
            onTimeDeliveryRate: '87.50',
            performanceScore: '88',
          },
        ],
      }),
    )

    await wrapper.get('.supplier-order-drilldown').trigger('click')

    expect(routerPush).toHaveBeenCalledWith({
      path: '/purchase/order',
      query: { partnerId: 'supplier-1' },
    })
  })

  it('does not create an unreachable drilldown without partnerId', () => {
    const wrapper = mountPurchaseView(
      baseData({
        supplierScores: [
          {
            partnerName: '缺少标识供应商',
            orderCount: 1,
            overdueOrderCount: 0,
            onTimeDeliveryRate: '100.00',
            performanceScore: '100',
          },
        ],
      }),
    )

    expect(wrapper.find('.supplier-order-drilldown').exists()).toBe(false)
    expect(routerPush).not.toHaveBeenCalled()
  })

  it('keeps zero and missing supplier score fields stable', () => {
    const wrapper = mountPurchaseView(
      baseData({
        supplierScores: [
          {
            partnerId: 'supplier-2',
            partnerName: '零逾期供应商',
            orderCount: 0,
            overdueOrderCount: 0,
            onTimeDeliveryRate: '0.00',
            performanceScore: '0',
          },
          {
            partnerId: 'supplier-3',
            partnerName: '字段缺失供应商',
          },
        ],
      }),
    )

    const text = wrapper.text()
    expect(text).toContain('零逾期供应商')
    expect(text).toContain('0.00%')
    expect(text).toContain('字段缺失供应商')
    expect(text).not.toContain('NaN')
    expect(text).not.toContain('-%')
    expect(text).not.toContain('综合评分模型')
    expect(text).not.toContain('评级配置器')
    expect(text).not.toContain('黑名单')
  })
})
