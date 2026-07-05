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
const mockPath = vi.hoisted(() => ({ value: '/dashboard' }))
let renderedMenuItems: MenuItem[] = []

vi.mock('vue-router', async () => {
  const actual = await vi.importActual<typeof import('vue-router')>('vue-router')
  return {
    ...actual,
    useRoute: () => ({
      path: mockPath.value,
      matched: [{ path: '/' }, { path: mockPath.value }],
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
    mockPath.value = '/dashboard'
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
      '项目经营',
      '采购库存',
      '分包计量',
      '结算收付',
      '基础资料',
      '流程与系统',
    ])
    expect(wrapper.text()).toContain('成本目标')
    expect(wrapper.text()).toContain('我的待办')
  })

  it('opens the domain containing the current route', () => {
    const wrapper = mountMenu()
    expect(wrapper.find('.menu-stub').attributes('data-open-keys')).toBe('/workbench')
  })

  it('opens workflow and system for hidden approval detail pages', () => {
    mockPath.value = '/approval/12345'
    const wrapper = mountMenu()

    expect(wrapper.find('.menu-stub').attributes('data-open-keys')).toBe('/workflow-system')
  })

  it('places cost subject and target cost under the new domains', () => {
    const wrapper = mountMenu()

    const masterData = renderedMenuItems.find((item) => item.label === '基础资料')
    expect(masterData?.children?.map((item) => item.label)).toEqual([
      '合作方管理',
      '组织架构',
      '材料字典',
      '成本科目',
    ])

    const projectOperations = renderedMenuItems.find((item) => item.label === '项目经营')
    expect(projectOperations?.children?.map((item) => item.label)).toEqual([
      '项目列表',
      '合同台账',
      '签证变更',
      '成本目标',
      '成本台账',
      '成本核对',
    ])

    expect(wrapper.find('[data-menu-key="/cost-target/index"]').exists()).toBe(true)
    expect(wrapper.find('[data-menu-key="/cost/subject"]').exists()).toBe(true)
  })

  it('places payment, invoice and workflow menus under the approved domains', () => {
    mountMenu()

    const settlement = renderedMenuItems.find((item) => item.label === '结算收付')
    expect(settlement?.children?.map((item) => item.label)).toEqual([
      '结算台账',
      '付款申请',
      '发票管理',
    ])

    const workflow = renderedMenuItems.find((item) => item.label === '流程与系统')
    expect(workflow?.children?.map((item) => item.label)).toEqual([
      '我的已办',
      '抄送我的',
      '我发起',
      '审批流程',
      '用户管理',
      '角色管理',
      '权限清单',
      '字典管理',
      '数据管理',
    ])
  })

  it('shows workflow admin menus only to administrators', () => {
    mockRoles.value = ['ADMIN']
    const adminWrapper = mountMenu()

    expect(adminWrapper.text()).toContain('审批流程')
    expect(adminWrapper.text()).toContain('用户管理')
    expect(
      adminWrapper.findAll('[data-menu-key]').map((node) => node.attributes('data-menu-key')),
    ).toContain('/approval/process')
    expect(
      adminWrapper.findAll('[data-menu-key]').map((node) => node.attributes('data-menu-key')),
    ).toContain('/system/users')

    mockRoles.value = ['PROJECT_MANAGER']
    const userWrapper = mountMenu()

    expect(userWrapper.text()).not.toContain('审批流程')
    expect(userWrapper.text()).not.toContain('用户管理')
    expect(
      userWrapper.findAll('[data-menu-key]').map((node) => node.attributes('data-menu-key')),
    ).not.toContain('/approval/process')
    expect(
      userWrapper.findAll('[data-menu-key]').map((node) => node.attributes('data-menu-key')),
    ).not.toContain('/system/users')
  })
})
