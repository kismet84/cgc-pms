import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'

import V2StatusToggle from '@/components/V2StatusToggle.vue'

describe('V2StatusToggle', () => {
  it('renders an accessible pill without a checkbox and emits toggle', async () => {
    const wrapper = mount(V2StatusToggle, {
      props: { enabled: true, ariaLabel: '切换用户状态' },
    })

    expect(wrapper.find('input').exists()).toBe(false)
    expect(wrapper.attributes('role')).toBe('switch')
    expect(wrapper.attributes('aria-checked')).toBe('true')
    expect(wrapper.classes()).toContain('is-enabled')

    await wrapper.trigger('click')
    expect(wrapper.emitted('toggle')).toHaveLength(1)
  })

  it('renders the disabled state as a gray, disabled pill', () => {
    const wrapper = mount(V2StatusToggle, {
      props: { enabled: false, disabled: true },
    })

    expect(wrapper.text()).toBe('停用')
    expect(wrapper.classes()).toContain('is-disabled')
    expect(wrapper.attributes('aria-checked')).toBe('false')
    expect(wrapper.attributes('disabled')).toBeDefined()
  })
})
