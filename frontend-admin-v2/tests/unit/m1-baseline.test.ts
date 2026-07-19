import { defineComponent, h, nextTick } from 'vue'
import { mount } from '@vue/test-utils'
import { createMemoryHistory, createRouter } from 'vue-router'
import { describe, expect, it } from 'vitest'
import V2ErrorBoundary from '@/components/V2ErrorBoundary.vue'
import V2PageState from '@/components/V2PageState.vue'

describe('M1 responsive and error-state baseline', () => {
  it('exposes a named loading state without business data', () => {
    const wrapper = mount(V2PageState, {
      props: {
        kind: 'loading',
        title: '正在加载工作区',
        description: '只加载页面结构',
      },
    })

    expect(wrapper.get('[role="status"]').attributes('aria-live')).toBe('polite')
    expect(wrapper.get('h1').text()).toBe('正在加载工作区')
    expect(wrapper.findAll('.v2-skeleton')).toHaveLength(3)
  })

  it('captures descendant exceptions and offers a safe recovery state', async () => {
    const router = createRouter({
      history: createMemoryHistory(),
      routes: [{ path: '/', component: { template: '<div />' } }],
    })
    await router.push('/')
    await router.isReady()
    const Exploder = defineComponent({
      name: 'Exploder',
      setup() {
        throw new Error('render failed')
      },
    })

    const wrapper = mount(V2ErrorBoundary, {
      global: { plugins: [router] },
      slots: { default: () => h(Exploder) },
    })
    await nextTick()

    expect(wrapper.get('[role="alert"]').text()).toContain('页面暂时无法显示')
    expect(wrapper.get('button').text()).toBe('重试当前页面')
    expect(wrapper.text()).not.toContain('render failed')
  })
})
