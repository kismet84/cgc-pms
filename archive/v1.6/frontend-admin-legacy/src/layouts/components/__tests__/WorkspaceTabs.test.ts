import { beforeEach, describe, expect, it, vi } from 'vitest'
import { mount } from '@vue/test-utils'
import { defineComponent, h } from 'vue'
import WorkspaceTabs from '@/layouts/components/WorkspaceTabs.vue'

const mockPush = vi.fn()
const mockRoute = vi.hoisted(() => ({
  path: '/inventory/purchase-request',
  query: { projectId: '7' } as Record<string, string>,
  hash: '#flow',
}))
const mockRoles = vi.hoisted(() => ({ value: ['PROJECT_MANAGER'] as string[] }))
const mockHasPermission = vi.hoisted(() => vi.fn(() => true))

vi.mock('vue-router', () => ({
  useRoute: () => mockRoute,
  useRouter: () => ({ push: mockPush }),
}))

vi.mock('@/stores/user', () => ({
  useUserStore: () => ({
    roles: mockRoles.value,
    hasPermission: mockHasPermission,
  }),
}))

const ATabsStub = defineComponent({
  name: 'ATabsStub',
  props: { activeKey: { type: String, default: '' } },
  emits: ['change'],
  setup(props, { slots }) {
    return () =>
      h('div', { class: 'tabs-stub', 'data-active-key': props.activeKey }, slots.default?.())
  },
})

const ATabPaneStub = defineComponent({
  name: 'ATabPaneStub',
  props: {
    tab: { type: String, required: true },
  },
  setup(props) {
    return () => h('span', { class: 'tab-pane-stub' }, props.tab)
  },
})

function mountTabs() {
  return mount(WorkspaceTabs, {
    global: {
      stubs: {
        'a-tabs': ATabsStub,
        'a-tab-pane': ATabPaneStub,
      },
    },
  })
}

describe('WorkspaceTabs', () => {
  beforeEach(() => {
    mockRoute.path = '/inventory/purchase-request'
    mockRoute.query = { projectId: '7' }
    mockRoute.hash = '#flow'
    mockRoles.value = ['PROJECT_MANAGER']
    mockHasPermission.mockReset().mockReturnValue(true)
    mockPush.mockClear()
  })

  it('renders the procurement process as three route-backed tabs', () => {
    const wrapper = mountTabs()

    expect(wrapper.text()).toContain('采购执行')
    expect(wrapper.findAll('.tab-pane-stub').map((node) => node.text())).toEqual([
      '采购申请',
      '采购订单',
      '材料验收',
    ])
    expect(wrapper.find('.tabs-stub').attributes('data-active-key')).toBe(
      '/inventory/purchase-request',
    )
  })

  it('uses router navigation and preserves query and hash when changing tabs', async () => {
    const wrapper = mountTabs()

    wrapper.findComponent(ATabsStub).vm.$emit('change', '/purchase/order')
    await wrapper.vm.$nextTick()

    expect(mockPush).toHaveBeenCalledWith({
      path: '/purchase/order',
      query: { projectId: '7' },
      hash: '#flow',
    })
  })

  it('filters tabs by their independent permissions', () => {
    mockHasPermission.mockImplementation((permission: string) => permission !== 'receipt:query')

    expect(
      mountTabs()
        .findAll('.tab-pane-stub')
        .map((node) => node.text()),
    ).toEqual(['采购申请', '采购订单'])
  })

  it('does not duplicate page-owned approval tabs', () => {
    mockRoute.path = '/approval/todo'

    expect(mountTabs().find('.workspace-tabs').exists()).toBe(false)
  })
})
