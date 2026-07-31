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

  it('uses stable virtual paths for workspace menu keys', () => {
    const wrapper = mountMenu()
    const keys = wrapper.findAll('[data-menu-key]').map((node) => node.attributes('data-menu-key'))

    expect(keys).toContain('/commercial/contracts')
    expect(keys).toContain('/supply/inventory')
    expect(keys).not.toContain('/variation/order')
    expect(keys).not.toContain('/inventory/warehouse')
  })

  it('groups root menu items by business domains', () => {
    const wrapper = mountMenu()

    expect(wrapper.findAll('[data-menu-title-key]').map((node) => node.text().trim())).toEqual([
      '工作台',
      '项目履约',
      '商务合约',
      '供应链与物资',
      '分包与结算',
      '资金财务',
      '基础资料',
      '系统管理',
    ])
    expect(wrapper.text()).toContain('成本核算与控制')
    expect(wrapper.text()).toContain('我的工作')
  })

  it('opens the domain containing the current route', () => {
    const wrapper = mountMenu()
    expect(wrapper.find('.menu-stub').attributes('data-open-keys')).toBe('/workbench')
  })

  it('opens workbench for approval detail pages', () => {
    mockPath.value = '/approval/12345'
    const wrapper = mountMenu()

    expect(wrapper.find('.menu-stub').attributes('data-open-keys')).toBe('/workbench')
  })

  it('places cost subject and target cost under the new domains', () => {
    const wrapper = mountMenu()

    expect(findSubmenuLabels(wrapper, '/master-data')).toEqual([
      '合作方管理',
      '组织架构',
      '物资主数据',
      '成本科目中心',
    ])

    expect(findSubmenuLabels(wrapper, '/commercial')).toEqual([
      '合同与变更',
      '投标与成本目标',
      '成本核算与控制',
      '预算与产值',
    ])

    expect(wrapper.find('[data-menu-key="/commercial/target-cost"]').exists()).toBe(true)
    expect(wrapper.find('[data-menu-key="/master-data/finance"]').exists()).toBe(true)
  })

  it('places payment, invoice and workflow menus under the approved domains', () => {
    const wrapper = mountMenu()

    expect(findSubmenuLabels(wrapper, '/finance')).toEqual(['收付款与发票', '资金运营', '财务核算'])

    expect(findSubmenuLabels(wrapper, '/system-management')).toEqual([
      '流程配置',
      '访问控制',
      '系统配置',
      '操作审计',
      '数据维护',
    ])
  })

  it('hides a workspace when none of its tabs is permitted', () => {
    mockRoles.value = ['USER']
    mockHasPermission.mockImplementation((code: string) => code === 'audit:query')

    const wrapper = mountMenu()

    expect(wrapper.find('[data-menu-key="/finance/cash"]').exists()).toBe(false)
    expect(wrapper.find('[data-menu-key="/system-management/audit"]').exists()).toBe(true)
  })

  it('shows the operation audit entry only with audit query permission', () => {
    mockRoles.value = ['USER']
    mockHasPermission.mockImplementation((code: string) => code === 'audit:query')
    expect(mountMenu().find('[data-menu-key="/system-management/audit"]').exists()).toBe(true)

    mockHasPermission.mockReturnValue(false)
    expect(mountMenu().find('[data-menu-key="/system-management"]').exists()).toBe(false)
  })

  it('shows permission menus to administrators without explicit permission codes', () => {
    mockRoles.value = ['ADMIN']
    mockHasPermission.mockReturnValue(false)

    const wrapper = mountMenu()

    expect(wrapper.find('[data-menu-key="/finance/cash"]').exists()).toBe(true)
  })

  it('navigates to the first visible child when a root section title is clicked', async () => {
    const wrapper = mountMenu()

    await wrapper.find('[data-menu-title-key="/system-management"]').trigger('click')

    expect(mockPush).toHaveBeenCalledTimes(1)
    expect(mockPush).toHaveBeenCalledWith('/approval/process')
  })

  it('shows workflow admin menus only to administrators', () => {
    mockRoles.value = ['ADMIN']
    const adminWrapper = mountMenu()

    expect(adminWrapper.text()).toContain('流程配置')
    expect(adminWrapper.text()).toContain('访问控制')
    expect(
      adminWrapper.findAll('[data-menu-key]').map((node) => node.attributes('data-menu-key')),
    ).toContain('/system-management/workflow')
    expect(
      adminWrapper.findAll('[data-menu-key]').map((node) => node.attributes('data-menu-key')),
    ).toContain('/system-management/access-control')

    mockRoles.value = ['PROJECT_MANAGER']
    const userWrapper = mountMenu()

    expect(userWrapper.text()).not.toContain('流程配置')
    expect(userWrapper.text()).not.toContain('访问控制')
    expect(
      userWrapper.findAll('[data-menu-key]').map((node) => node.attributes('data-menu-key')),
    ).not.toContain('/system-management/workflow')
    expect(
      userWrapper.findAll('[data-menu-key]').map((node) => node.attributes('data-menu-key')),
    ).not.toContain('/system-management/access-control')
  })
})
