import { beforeEach, describe, expect, it, vi } from 'vitest'
import { mount } from '@vue/test-utils'
import { defineComponent, h } from 'vue'
import ObjectContextNavigation from '@/layouts/components/ObjectContextNavigation.vue'

const mockPush = vi.fn()
const mockRoute = vi.hoisted(() => ({
  path: '/project/42/overview',
  query: {} as Record<string, string>,
  hash: '',
}))
const mockHasPermission = vi.hoisted(() => vi.fn(() => true))

vi.mock('vue-router', () => ({
  useRoute: () => mockRoute,
  useRouter: () => ({ push: mockPush }),
}))

vi.mock('@/stores/user', () => ({
  useUserStore: () => ({
    roles: ['PROJECT_MANAGER'],
    hasPermission: mockHasPermission,
  }),
}))

const ATabsStub = defineComponent({
  name: 'ATabsStub',
  emits: ['change'],
  setup(_, { slots }) {
    return () => h('div', { class: 'tabs-stub' }, slots.default?.())
  },
})

const ATabPaneStub = defineComponent({
  name: 'ATabPaneStub',
  props: { tab: { type: String, required: true } },
  setup(props) {
    return () => h('span', { class: 'tab-pane-stub' }, props.tab)
  },
})

function mountContext() {
  return mount(ObjectContextNavigation, {
    global: {
      stubs: {
        'a-tabs': ATabsStub,
        'a-tab-pane': ATabPaneStub,
      },
    },
  })
}

describe('ObjectContextNavigation', () => {
  beforeEach(() => {
    mockRoute.path = '/project/42/overview'
    mockRoute.query = {}
    mockRoute.hash = ''
    mockHasPermission.mockReset().mockReturnValue(true)
    mockPush.mockClear()
  })

  it('builds project object navigation from the current project id', async () => {
    const wrapper = mountContext()

    expect(wrapper.findAll('.tab-pane-stub').map((node) => node.text())).toEqual([
      '项目总览',
      '项目成员',
      '编辑项目',
    ])

    wrapper.findComponent(ATabsStub).vm.$emit('change', '/project/42/members')
    await wrapper.vm.$nextTick()
    expect(mockPush).toHaveBeenCalledWith({ path: '/project/42/members', query: {}, hash: '' })
  })

  it('filters object actions by route permission', () => {
    mockHasPermission.mockImplementation((permission: string) => permission !== 'project:edit')

    expect(mountContext().text()).not.toContain('编辑项目')
  })

  it('keeps settlement detail contextual and links back to the ledger', async () => {
    mockRoute.path = '/settlement/88'
    const wrapper = mountContext()

    expect(wrapper.text()).toContain('结算详情')
    await wrapper.find('.object-context__back').trigger('click')
    expect(mockPush).toHaveBeenCalledWith('/settlement/list')
  })
})
