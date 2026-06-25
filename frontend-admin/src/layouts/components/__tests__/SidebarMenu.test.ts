import { beforeEach, describe, expect, it, vi } from 'vitest'
import { mount } from '@vue/test-utils'
import { defineComponent, h, type PropType } from 'vue'
import SidebarMenu from '@/layouts/components/SidebarMenu.vue'

type MenuItem = {
  key: string
  label: string
  children?: MenuItem[]
}

const mockPush = vi.fn()
const mockRoles = vi.hoisted(() => ({ value: ['ADMIN'] as string[] }))
let renderedMenuItems: MenuItem[] = []

vi.mock('vue-router', async () => {
  const actual = await vi.importActual<typeof import('vue-router')>('vue-router')
  return {
    ...actual,
    useRoute: () => ({
      path: '/dashboard',
      matched: [{ path: '/' }, { path: '/dashboard' }],
    }),
    useRouter: () => ({
      push: mockPush,
    }),
  }
})

vi.mock('@/stores/user', () => ({
  useUserStore: () => ({
    roles: mockRoles.value,
  }),
}))

const AMenuStub = defineComponent({
  name: 'AMenuStub',
  props: {
    items: {
      type: Array as PropType<MenuItem[]>,
      default: () => [],
    },
    openKeys: {
      type: Array as PropType<string[]>,
      default: () => [],
    },
  },
  emits: ['click'],
  setup(props, { emit }) {
    renderedMenuItems = props.items as MenuItem[]
    const renderItems = (items: MenuItem[]) =>
      items.flatMap((item) => [
        h(
          'button',
          {
            type: 'button',
            'data-menu-key': item.key,
            onClick: () => emit('click', { key: item.key }),
          },
          item.label,
        ),
        ...(item.children ? renderItems(item.children) : []),
      ])

    return () =>
      h(
        'nav',
        {
          class: 'menu-stub',
          'data-open-keys': props.openKeys.join(','),
        },
        renderItems(props.items),
      )
  },
})

function mountMenu() {
  return mount(SidebarMenu, {
    global: {
      stubs: {
        'a-menu': AMenuStub,
      },
    },
  })
}

describe('SidebarMenu', () => {
  beforeEach(() => {
    mockRoles.value = ['ADMIN']
    renderedMenuItems = []
    mockPush.mockClear()
  })

  it('uses full paths for nested menu item keys', () => {
    const wrapper = mountMenu()
    const keys = wrapper.findAll('[data-menu-key]').map((node) => node.attributes('data-menu-key'))

    expect(keys).toContain('/variation/order')
    expect(keys).toContain('/inventory/warehouse')
    expect(keys).not.toContain('order')
    expect(keys).not.toContain('warehouse')
  })

  it('groups root menu items by business domains', () => {
    const wrapper = mountMenu()

    expect(renderedMenuItems.map((item) => item.label)).toEqual([
      '工作台',
      '合同管理',
      '成本管理',
      '采购与库存',
      '分包管理',
      '结算管理',
      '审批中心',
      '数据中心',
      '系统管理',
    ])
    expect(wrapper.text()).toContain('目标成本')
    expect(wrapper.text()).not.toContain('目标管理')
  })

  it('opens the domain containing the current route', () => {
    const wrapper = mountMenu()
    expect(wrapper.find('.menu-stub').attributes('data-open-keys')).toBe('/workbench')
  })

  it('places cost subject and target cost under cost domain', () => {
    const wrapper = mountMenu()

    const masterData = renderedMenuItems.find((item) => item.label === '数据中心')
    expect(masterData?.children?.map((item) => item.label)).toEqual([
      '合作方管理',
      '组织架构',
      '材料字典',
    ])

    const cost = renderedMenuItems.find((item) => item.label === '成本管理')
    expect(cost?.children?.map((item) => item.label)).toEqual([
      '成本台账',
      '成本汇总',
      '目标成本',
      '成本科目',
    ])

    expect(wrapper.find('[data-menu-key="/cost-target/index"]').exists()).toBe(true)
    expect(wrapper.find('[data-menu-key="/cost/subject"]').exists()).toBe(true)
  })

  it('places payment, invoice and approval process under approved domains', () => {
    mountMenu()

    const settlement = renderedMenuItems.find((item) => item.label === '结算管理')
    expect(settlement?.children?.map((item) => item.label)).toEqual([
      '结算列表',
      '付款申请',
      '发票管理',
    ])

    const approval = renderedMenuItems.find((item) => item.label === '审批中心')
    expect(approval?.children?.map((item) => item.label)).toEqual([
      '我的待办',
      '我的已办',
      '抄送我的',
      '审批流程管理',
    ])
  })

  it('shows approval process management only to administrators', () => {
    mockRoles.value = ['ADMIN']
    const adminWrapper = mountMenu()

    expect(adminWrapper.text()).toContain('审批流程管理')
    expect(
      adminWrapper.findAll('[data-menu-key]').map((node) => node.attributes('data-menu-key')),
    ).toContain('/approval/process')

    mockRoles.value = ['PROJECT_MANAGER']
    const userWrapper = mountMenu()

    expect(userWrapper.text()).not.toContain('审批流程管理')
    expect(
      userWrapper.findAll('[data-menu-key]').map((node) => node.attributes('data-menu-key')),
    ).not.toContain('/approval/process')
  })
})
