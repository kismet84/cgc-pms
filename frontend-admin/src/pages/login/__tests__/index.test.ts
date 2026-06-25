import { describe, it, expect, vi, beforeEach } from 'vitest'
import { mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { login } from '@/api/modules/auth'
import LoginPage from '../index.vue'

// ── Mock router ──
const mockPush = vi.fn()
const mockRoute = { query: {} as Record<string, string | string[]> }
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
  return {
    ...actual,
    message: { success: vi.fn(), error: vi.fn(), info: vi.fn(), warning: vi.fn() },
  }
})

describe('login/index.vue', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    mockRoute.query = {}
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

  it('登录成功后允许跳转站内 redirect 路径', async () => {
    mockRoute.query = { redirect: '/contract/list?status=pending' }
    const wrapper = mount(LoginPage)

    wrapper.vm.formState.password = 'secret'
    await wrapper.vm.handleSubmit()

    expect(mockPush).toHaveBeenCalledWith('/contract/list?status=pending')
  })

  it('登录成功后拒绝外部 redirect 路径并回到首页', async () => {
    mockRoute.query = { redirect: 'https://evil.example/phishing' }
    const wrapper = mount(LoginPage)

    wrapper.vm.formState.password = 'secret'
    await wrapper.vm.handleSubmit()

    expect(mockPush).toHaveBeenCalledWith('/')
  })

  it('登录成功后拒绝双斜杠 redirect 路径并回到首页', async () => {
    mockRoute.query = { redirect: '//evil.example/phishing' }
    const wrapper = mount(LoginPage)

    wrapper.vm.formState.password = 'secret'
    await wrapper.vm.handleSubmit()

    expect(mockPush).toHaveBeenCalledWith('/')
  })

  it('登录成功后使用 redirect 数组中的第一个站内路径', async () => {
    mockRoute.query = { redirect: ['/invoice', 'https://evil.example'] }
    const wrapper = mount(LoginPage)

    wrapper.vm.formState.password = 'secret'
    await wrapper.vm.handleSubmit()

    expect(mockPush).toHaveBeenCalledWith('/invoice')
  })

  it('用户名或密码为空时不提交登录', async () => {
    const wrapper = mount(LoginPage)

    wrapper.vm.formState.username = ''
    wrapper.vm.formState.password = ''
    await wrapper.vm.handleSubmit()

    expect(mockPush).not.toHaveBeenCalled()
    expect(mockUserStore.setUserInfo).not.toHaveBeenCalled()
  })

  it('登录成功且无 redirect 时回到首页', async () => {
    const wrapper = mount(LoginPage)

    wrapper.vm.formState.password = 'secret'
    await wrapper.vm.handleSubmit()

    expect(mockPush).toHaveBeenCalledWith('/')
  })

  it('登录失败时不跳转', async () => {
    vi.mocked(login).mockRejectedValueOnce(new Error('bad credentials'))
    const wrapper = mount(LoginPage)

    wrapper.vm.formState.password = 'wrong'
    await wrapper.vm.handleSubmit()

    expect(mockPush).not.toHaveBeenCalled()
  })
})
