import { createPinia, setActivePinia } from 'pinia'
import { mount } from '@vue/test-utils'
import { createMemoryHistory, createRouter } from 'vue-router'
import { describe, expect, it, vi } from 'vitest'
import CostBudgetPage from '@/pages/commercial/CostBudgetPage.vue'
import { useSessionStore } from '@/stores/session'

vi.mock('@/pages/commercial/CostTargetPage.vue', () => ({
  default: {
    props: { embedded: Boolean },
    template:
      '<section data-form="cost-budget"><h1 v-if="embedded">项目成本预算</h1><form data-actions="filters">筛选与新建</form></section>',
  },
}))

async function mountPage(permissions: string[], path = '/cost-budget') {
  setActivePinia(createPinia())
  const session = useSessionStore()
  session.userInfo = { userId: '1', username: 'tester', roles: ['USER'], permissions }
  session.status = 'authenticated'
  const router = createRouter({
    history: createMemoryHistory(),
    routes: [{ path: '/cost-budget', component: CostBudgetPage }],
  })
  await router.push(path)
  await router.isReady()
  const wrapper = mount(CostBudgetPage, { global: { plugins: [router] } })
  return { router, wrapper }
}

describe('cost budget workspace', () => {
  it('renders one project cost budget form without target/budget view switching', async () => {
    const { wrapper } = await mountPage(['cost:target:query'], '/cost-budget?projectId=P1')

    expect(wrapper.get('h1').text()).toBe('项目成本预算')
    expect(wrapper.findAll('h1')).toHaveLength(1)
    expect(wrapper.find('h2').exists()).toBe(false)
    expect(wrapper.find('[data-actions="filters"]').exists()).toBe(true)
    expect(wrapper.find('[data-form="cost-budget"]').exists()).toBe(true)
    expect(wrapper.find('nav[aria-label="成本预算视图"]').exists()).toBe(false)
  })
})
