import { beforeEach, describe, expect, it, vi } from 'vitest'
import { mount } from '@vue/test-utils'
import { defineComponent, h, useAttrs, useSlots, type PropType } from 'vue'
import SidebarMenu from '@/layouts/components/SidebarMenu.vue'

const mockPush = vi.fn()
const mockRoles = vi.hoisted(() => ({ value: ['ADMIN'] as string[] }))
const mockPath = vi.hoisted(() => ({ value: '/dashboard' }))
const mockHasPermission = vi.hoisted(() => vi.fn(() => true))

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
    hasPermission: mockHasPermission,
  }),
}))

const AMenuStub = defineComponent({
  name: 'AMenuStub',
  props: {
    openKeys: {
      type: Array as PropType<string[]>,
      default: () => [],
    },
  },
  emits: ['click'],
  setup(props, { emit, slots }) {
    return () =>
      h(
        'nav',
        {
          class: 'menu-stub',
          'data-open-keys': props.openKeys.join(','),
        },
        h(
          'div',
          {
            onClick: (event: Event) => {
              const target = event.target as HTMLElement | null
              const key = target?.closest('[data-menu-key]')?.getAttribute('data-menu-key')
              if (key) emit('click', { key })
            },
          },
          slots.default?.(),
        ),
      )
  },
})

const ASubMenuStub = defineComponent({
  name: 'ASubMenuStub',
  setup() {
    const attrs = useAttrs()
    const slots = useSlots()
    return () =>
      h('section', { class: 'submenu-stub', ...attrs }, [
        h('div', { class: 'submenu-title-stub' }, slots.title?.()),
        h('div', { class: 'submenu-content-stub' }, slots.default?.()),
      ])
  },
})

const AMenuItemStub = defineComponent({
  name: 'AMenuItemStub',
  setup() {
    const attrs = useAttrs()
    const slots = useSlots()
    return () => h('div', { class: 'menu-item-stub', ...attrs }, slots.default?.())
  },
})

function mountMenu() {
  return mount(SidebarMenu, {
    global: {
      stubs: {
        'a-menu': AMenuStub,
        'a-sub-menu': ASubMenuStub,
        'a-menu-item': AMenuItemStub,
      },
    },
  })
}

function findSubmenuLabels(wrapper: ReturnType<typeof mount>, key: string) {
  return wrapper
    .find(`[data-submenu-key="${key}"]`)
    .findAll('[data-menu-key]')
    .map((node) => node.text())
}

describe('SidebarMenu', () => {
  beforeEach(() => {
    mockRoles.value = ['ADMIN']
    mockPath.value = '/dashboard'
    mockPush.mockClear()
    mockHasPermission.mockReset().mockReturnValue(true)
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

    expect(wrapper.findAll('[data-menu-title-key]').map((node) => node.text().trim())).toEqual([
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

    expect(findSubmenuLabels(wrapper, '/master-data')).toEqual([
      '合作方管理',
      '组织架构',
      '材料字典',
      '成本科目',
    ])

    expect(findSubmenuLabels(wrapper, '/project-operations')).toEqual([
      '项目列表',
      '合同台账',
      '签证变更',
      '成本目标',
      '成本台账',
      '成本核对',
      '现场日报',
      '项目计划',
      '质量安全整改',
      '投标成本',
    ])

    expect(wrapper.find('[data-menu-key="/cost-target/index"]').exists()).toBe(true)
    expect(wrapper.find('[data-menu-key="/cost/subject"]').exists()).toBe(true)
  })

  it('places payment, invoice and workflow menus under the approved domains', () => {
    const wrapper = mountMenu()

    expect(findSubmenuLabels(wrapper, '/settlement-domain')).toEqual([
      '项目预算',
      '结算台账',
      '费用申请',
      '付款申请',
      '资金运营',
      '收入与回款',
      '产值计量',
      '资金日记账',
      '会计凭证',
      '发票管理',
    ])

    expect(findSubmenuLabels(wrapper, '/workflow-system')).toEqual([
      '审批流程',
      '用户管理',
      '角色管理',
      '权限清单',
      '字典管理',
      '数据管理',
      '操作审计',
    ])
  })

  it('hides the cash journal entry without query permission', () => {
    mockRoles.value = ['USER']
    mockHasPermission.mockImplementation((code: string) => code !== 'cashbook:journal:query')

    const wrapper = mountMenu()

    expect(wrapper.find('[data-menu-key="/cash-journal"]').exists()).toBe(false)
  })

  it('shows the operation audit entry only with audit query permission', () => {
    mockRoles.value = ['USER']
    mockHasPermission.mockImplementation((code: string) => code === 'audit:query')
    expect(mountMenu().find('[data-menu-key="/system/audit"]').exists()).toBe(true)

    mockHasPermission.mockReturnValue(false)
    expect(mountMenu().find('[data-menu-key="/system/audit"]').exists()).toBe(false)
  })

  it('shows permission menus to administrators without explicit permission codes', () => {
    mockRoles.value = ['ADMIN']
    mockHasPermission.mockReturnValue(false)

    const wrapper = mountMenu()

    expect(wrapper.find('[data-menu-key="/cash-journal"]').exists()).toBe(true)
  })

  it('navigates to the first visible child when a root section title is clicked', async () => {
    const wrapper = mountMenu()

    await wrapper.find('[data-menu-title-key="/workflow-system"]').trigger('click')

    expect(mockPush).toHaveBeenCalledTimes(1)
    expect(mockPush).toHaveBeenCalledWith('/approval/process')
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
