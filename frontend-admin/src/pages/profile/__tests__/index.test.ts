import { describe, it, expect, vi, beforeEach } from 'vitest'
import { mount } from '@vue/test-utils'
import { nextTick } from 'vue'

// ── Hoisted mocks (must be declared before vi.mock which is hoisted) ──
const { mockRequest, mockSetUserInfo, mockMessage } = vi.hoisted(() => ({
  mockRequest: vi.fn().mockResolvedValue({}),
  mockSetUserInfo: vi.fn(),
  mockMessage: {
    success: vi.fn(),
    error: vi.fn(),
    warning: vi.fn(),
    info: vi.fn(),
  },
}))

const mockUserInfo = {
  userId: '1',
  username: 'admin',
  realName: '张三',
  avatar: 'https://example.com/avatar.png',
  phone: '13800138000',
  email: 'admin@cgc.com',
  roles: ['ADMIN'],
  permissions: ['*'],
  roleName: '系统管理员',
}

// ── Mock request ──
vi.mock('@/api/request', () => ({
  request: mockRequest,
}))

// ── Mock user store ──
vi.mock('@/stores/user', () => ({
  useUserStore: () => ({
    userInfo: mockUserInfo,
    setUserInfo: mockSetUserInfo,
  }),
}))

// ── Mock icons ──
vi.mock('@ant-design/icons-vue', () => ({
  UserOutlined: { template: '<span class="stub-icon">User</span>' },
  LockOutlined: { template: '<span class="stub-icon">Lock</span>' },
}))

// ── Mock ant-design-vue message ──
vi.mock('ant-design-vue', async () => {
  const actual = await vi.importActual('ant-design-vue')
  return {
    ...(actual as object),
    message: mockMessage,
  }
})

// ── Import after mocks ──
import ProfilePage from '../index.vue'

describe('Profile page', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  // ── Mount helper ──
  function mountProfile() {
    return mount(ProfilePage, {
      global: {
        stubs: {
          'a-avatar': { template: '<div class="stub-avatar"><slot /></div>' },
          'a-card': { template: '<div class="stub-card"><slot /><slot name="title" /></div>' },
          'a-form': {
            template: '<form class="stub-form" @submit.prevent="$emit(\'finish\')"><slot /></form>',
            emits: ['finish'],
          },
          'a-form-item': { template: '<div class="stub-form-item"><slot /></div>' },
          'a-input': {
            template:
              '<input class="stub-input" :value="value" @input="$emit(\'update:value\', $event.target.value)" />',
            props: ['value'],
            emits: ['update:value'],
          },
          'a-input-password': {
            template:
              '<input type="password" class="stub-input-password" :value="value" @input="$emit(\'update:value\', $event.target.value)" />',
            props: ['value'],
            emits: ['update:value'],
          },
          'a-button': {
            template:
              '<button type="submit" class="stub-button" @click="$emit(\'click\')"><slot /></button>',
            emits: ['click'],
          },
          'a-descriptions': { template: '<div class="stub-descriptions"><slot /></div>' },
          'a-descriptions-item': { template: '<div class="stub-descriptions-item"><slot /></div>' },
          'a-divider': { template: '<hr class="stub-divider" />' },
        },
      },
    })
  }

  // ── TEST 1: renders user realName from store ──
  it('renders user realName from store', () => {
    const wrapper = mountProfile()

    expect(wrapper.text()).toContain('张三')
  })

  // ── TEST 2: renders phone and email inputs with store values ──
  it('renders phone and email inputs with store values', () => {
    const wrapper = mountProfile()

    const inputs = wrapper.findAll('.stub-input')
    expect(inputs.length).toBeGreaterThanOrEqual(2)

    const phoneInput = inputs.find((el) => (el.element as HTMLInputElement).value === '13800138000')
    expect(phoneInput).toBeTruthy()

    const emailInput = inputs.find(
      (el) => (el.element as HTMLInputElement).value === 'admin@cgc.com',
    )
    expect(emailInput).toBeTruthy()
  })

  // ── TEST 3: submit profile form → API called with correct data ──
  it('submits profile form and calls API with correct data', async () => {
    mockRequest.mockResolvedValue({
      userId: '1',
      username: 'admin',
      realName: '李四',
      phone: '13800138000',
      email: 'admin@cgc.com',
      avatar: '',
      roles: ['ADMIN'],
      permissions: ['*'],
      roleName: '系统管理员',
    })

    const wrapper = mountProfile()

    const realNameInput = wrapper.findAll('.stub-input').at(0)
    expect(realNameInput).toBeTruthy()
    await realNameInput!.setValue('李四')

    // Trigger form submission on the profile edit form (first .stub-form)
    const forms = wrapper.findAll('.stub-form')
    const profileForm = forms.at(0)
    expect(profileForm).toBeTruthy()
    await profileForm!.trigger('submit')
    await nextTick()

    expect(mockRequest).toHaveBeenCalled()
    const callArgs = mockRequest.mock.calls[0][0]
    expect(callArgs.url).toBe('/profile')
    expect(callArgs.method).toBe('put')
    expect(callArgs.data.realName).toBe('李四')
    expect(callArgs.data.phone).toBe('13800138000')
    expect(callArgs.data.email).toBe('admin@cgc.com')
  })

  // ── TEST 4: password mismatch → validation error message shown ──
  it('shows error when confirm password does not match new password', async () => {
    const wrapper = mountProfile()

    const passwordInputs = wrapper.findAll('.stub-input-password')
    expect(passwordInputs.length).toBeGreaterThanOrEqual(3)

    await passwordInputs.at(0)!.setValue('old123')
    await passwordInputs.at(1)!.setValue('new456')
    await passwordInputs.at(2)!.setValue('mismatch')
    await nextTick()

    // Trigger form submission on the password change form (second .stub-form)
    const forms = wrapper.findAll('.stub-form')
    const passwordForm = forms.at(1)
    expect(passwordForm).toBeTruthy()
    await passwordForm!.trigger('submit')
    await nextTick()

    const hasErrorMessage =
      mockMessage.error.mock.calls.length > 0 || mockMessage.warning.mock.calls.length > 0
    expect(hasErrorMessage).toBe(true)

    const pwdCalls = mockRequest.mock.calls.filter(
      (call: unknown[]) => (call[0] as Record<string, unknown>)?.url === '/profile/password',
    )
    expect(pwdCalls.length).toBe(0)
  })

  it('shows warning when new password does not meet minimum strength', async () => {
    const wrapper = mountProfile()

    const passwordInputs = wrapper.findAll('.stub-input-password')
    expect(passwordInputs.length).toBeGreaterThanOrEqual(3)

    await passwordInputs.at(0)!.setValue('old123456')
    await passwordInputs.at(1)!.setValue('abcdefgh')
    await passwordInputs.at(2)!.setValue('abcdefgh')
    await nextTick()

    const forms = wrapper.findAll('.stub-form')
    const passwordForm = forms.at(1)
    expect(passwordForm).toBeTruthy()
    await passwordForm!.trigger('submit')
    await nextTick()

    expect(mockMessage.warning).toHaveBeenCalled()
    const pwdCalls = mockRequest.mock.calls.filter(
      (call: unknown[]) => (call[0] as Record<string, unknown>)?.url === '/profile/password',
    )
    expect(pwdCalls.length).toBe(0)
  })
})
