import { mount } from '@vue/test-utils'
import { nextTick } from 'vue'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { showToast, V2ToastHost } from '@/components'

afterEach(() => {
  vi.runOnlyPendingTimers()
  vi.useRealTimers()
  document.body.innerHTML = ''
})

describe('V2 toast', () => {
  it('stacks four types and supports manual or timed dismissal', async () => {
    vi.useFakeTimers()
    const wrapper = mount(V2ToastHost, { attachTo: document.body })

    showToast('success', '操作成功', '数据已保存至云端')
    showToast('info', '提示信息', '新版本已可用')
    showToast('warn', '注意', '磁盘空间不足 10%')
    showToast('error', '错误', '网络连接已断开')
    await nextTick()

    expect(wrapper.findAll('.v2-toast')).toHaveLength(4)
    expect(wrapper.findAll('.v2-toast__progress')).toHaveLength(4)
    expect(wrapper.findAll('.v2-toast').map((toast) => toast.classes()[1])).toEqual([
      'v2-toast--success',
      'v2-toast--info',
      'v2-toast--warn',
      'v2-toast--error',
    ])

    await wrapper.find('.v2-toast__close').trigger('click')
    expect(wrapper.findAll('.v2-toast')).toHaveLength(3)

    await vi.advanceTimersByTimeAsync(3500)
    await nextTick()
    expect(wrapper.findAll('.v2-toast')).toHaveLength(0)

    wrapper.unmount()
  })
})
