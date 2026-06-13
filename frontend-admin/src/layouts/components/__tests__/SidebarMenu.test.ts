import { describe, expect, it, vi } from 'vitest'
import { mount } from '@vue/test-utils'
import { defineComponent, h, type PropType } from 'vue'
import SidebarMenu from '@/layouts/components/SidebarMenu.vue'

type MenuItem = {
  key: string
  label: string
  children?: MenuItem[]
}

const mockPush = vi.fn()

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

const AMenuStub = defineComponent({
  name: 'AMenuStub',
  props: {
    items: {
      type: Array as PropType<MenuItem[]>,
      default: () => [],
    },
  },
  emits: ['click'],
  setup(props, { emit }) {
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

    return () => h('nav', { class: 'menu-stub' }, renderItems(props.items))
  },
})

describe('SidebarMenu', () => {
  it('uses full paths for nested menu item keys', () => {
    const wrapper = mount(SidebarMenu, {
      global: {
        stubs: {
          'a-menu': AMenuStub,
        },
      },
    })

    const keys = wrapper.findAll('[data-menu-key]').map((node) => node.attributes('data-menu-key'))

    expect(keys).toContain('/variation/order')
    expect(keys).toContain('/inventory/warehouse')
    expect(keys).not.toContain('order')
    expect(keys).not.toContain('warehouse')
  })
})
