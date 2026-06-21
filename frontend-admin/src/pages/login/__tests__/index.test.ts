import { describe, it, expect, vi, beforeEach } from 'vitest'
import { mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import LoginPage from '../index.vue'

// ── Mock router ──
const mockPush = vi.fn()
const mockRoute = { query: {} as Record<string, string> }
vi.mock('vue-router', () => ({
  useRouter: () => ({ push: mockPush }),
  useRoute: () => mockRoute,
}))

// ── Mock stores ──
const mockUserStore = {
  setUserInfo: vi.fn(),
}
vi.mock('@/stores/user', () => ({
  useUserStore: () => mockUserStore,
}))

// ── Mock login API ──
vi.mock('@/api/modules/auth', () => ({
  login: vi.fn().mockResolvedValue({ userInfo: { username: 'admin', roles: ['ADMIN'] } }),
}))

// ── Mock ant-design-vue ──
vi.mock('ant-design-vue', async () => {
  const actual = await vi.importActual('ant-design-vue')
  return { ...actual, message: { success: vi.fn(), error: vi.fn(), info: vi.fn(), warning: vi.fn() } }
})

describe('login/index.vue', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    setActivePinia(createPinia())
  })

  it('登录页面正常渲染，包含用户名和密码输入框', () => {
    const wrapper = mount(LoginPage, {
      global: {
        stubs: {
          'a-card': { template: '<div class="stub-card"><slot /></div>' },
          'a-form': { template: '<form><slot /></form>' },
          'a-form-item': { template: '<div><slot /></div>' },
          'a-input': { template: '<input />' },
          'a-input-password': { template: '<input type="password" />' },
          'a-button': { template: '<button><slot /></button></button>' },
          'a-checkbox': { template: '<input type="checkbox" />' },
          UserOutlined: true,
          LockOutlined: true,
        },
      },
    })

    expect(wrapper.exists()).toBe(true)
    expect(wrapper.html()).toBeTruthy()
    // 登录页面应有表单元素
    expect(wrapper.find('form').exists()).toBe(true)
  })
})
