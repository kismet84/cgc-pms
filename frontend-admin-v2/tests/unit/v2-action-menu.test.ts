import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'
import V2ActionMenu from '@/components/V2ActionMenu.vue'

describe('V2ActionMenu', () => {
  it('keeps file input mounted until selection changes', async () => {
    const wrapper = mount(V2ActionMenu, {
      props: { label: '附件操作' },
      slots: {
        default: '<label><span>上传附件</span><input type="file" /></label>',
      },
    })
    const details = wrapper.get('details').element as HTMLDetailsElement
    details.open = true

    await wrapper.get('label').trigger('click')
    expect(details.open).toBe(true)

    await wrapper.get('input').trigger('change')
    expect(details.open).toBe(false)
  })

  it('still closes after ordinary actions', async () => {
    const wrapper = mount(V2ActionMenu, {
      props: { label: '普通操作' },
      slots: { default: '<button type="button">执行</button>' },
    })
    const details = wrapper.get('details').element as HTMLDetailsElement
    details.open = true

    await wrapper.get('button').trigger('click')
    expect(details.open).toBe(false)
  })
})
