import type { UserInfo } from '@cgc-pms/frontend-contracts'
import { createPinia, setActivePinia } from 'pinia'
import { flushPromises, mount } from '@vue/test-utils'
import { createMemoryHistory, createRouter } from 'vue-router'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import AccountPage from '@/pages/account/AccountPage.vue'
import { changePassword, loadPreferences, savePreferences, updateProfile } from '@/services/account'
import { getCurrentUser } from '@/services/auth'
import { ApiClientError } from '@/services/request'
import { toastItems } from '@/components/toast'
import { useSessionStore } from '@/stores/session'

vi.mock('@/services/auth', () => ({
  getCurrentUser: vi.fn(),
  login: vi.fn(),
  logout: vi.fn(),
}))
vi.mock('@/services/account', () => ({
  changePassword: vi.fn(),
  loadPreferences: vi.fn(),
  savePreferences: vi.fn(),
  updateProfile: vi.fn(),
}))

const initialUser: UserInfo = {
  userId: '7',
  username: 'demo.user',
  realName: '原姓名',
  phone: '13800000000',
  email: 'before@example.com',
  avatar: '',
  roles: ['USER'],
  permissions: [],
}
const defaultPreferences = {
  sidebarCollapsed: false,
  notificationEnabled: true,
  theme: 'light' as const,
  tableDensity: 'middle' as const,
}

async function render(path: string) {
  const router = createRouter({
    history: createMemoryHistory(),
    routes: ['/profile', '/settings', '/help'].map((routePath) => ({
      path: routePath,
      component: AccountPage,
    })),
  })
  await router.push(path)
  await router.isReady()
  const wrapper = mount(AccountPage, { global: { plugins: [router] } })
  await flushPromises()
  return wrapper
}

beforeEach(() => {
  setActivePinia(createPinia())
  vi.clearAllMocks()
  toastItems.splice(0)
  vi.mocked(getCurrentUser).mockResolvedValue(initialUser)
  vi.mocked(updateProfile).mockResolvedValue(initialUser)
  vi.mocked(changePassword).mockResolvedValue()
  vi.mocked(loadPreferences).mockResolvedValue(defaultPreferences)
  vi.mocked(savePreferences).mockResolvedValue(defaultPreferences)
})

describe('M7 account self-service page', () => {
  it('updates only profile fields and refreshes session from server', async () => {
    const refreshed = { ...initialUser, realName: '服务端姓名', phone: '13900000000' }
    vi.mocked(getCurrentUser).mockResolvedValueOnce(initialUser).mockResolvedValueOnce(refreshed)
    const wrapper = await render('/profile')

    await wrapper.get('input[autocomplete="name"]').setValue('  新姓名  ')
    await wrapper.get('#profile-form').trigger('submit')
    await flushPromises()

    expect(updateProfile).toHaveBeenCalledWith({
      realName: '新姓名',
      phone: '13800000000',
      email: 'before@example.com',
      avatar: '',
    })
    expect(getCurrentUser).toHaveBeenCalledTimes(2)
    expect(useSessionStore().userInfo?.realName).toBe('服务端姓名')
    expect(wrapper.get('input[autocomplete="name"]').element).toHaveProperty('value', '服务端姓名')
  })

  it('validates passwords, omits confirmation from request, then clears secrets', async () => {
    const wrapper = await render('/profile')
    const fields = wrapper.findAll('input[type="password"]')
    await fields[0]!.setValue('old-password')
    await fields[1]!.setValue('weak')
    await fields[2]!.setValue('weak')
    await wrapper.get('#password-form').trigger('submit')
    expect(changePassword).not.toHaveBeenCalled()

    await fields[1]!.setValue('NewSecure1!')
    await fields[2]!.setValue('NewSecure1!')
    await wrapper.get('#password-form').trigger('submit')
    await flushPromises()

    expect(changePassword).toHaveBeenCalledWith({
      oldPassword: 'old-password',
      newPassword: 'NewSecure1!',
    })
    expect(fields.every((field) => (field.element as HTMLInputElement).value === '')).toBe(true)
  })

  it('saves preferences and then rereads the server result', async () => {
    const calls: string[] = []
    vi.mocked(loadPreferences)
      .mockImplementationOnce(async () => {
        calls.push('get')
        return defaultPreferences
      })
      .mockImplementationOnce(async () => {
        calls.push('get')
        return { ...defaultPreferences, sidebarCollapsed: true }
      })
    vi.mocked(savePreferences).mockImplementation(async (body) => {
      calls.push('put')
      return body
    })
    const wrapper = await render('/settings')

    await wrapper.get('#settings-form').trigger('submit')
    await flushPromises()

    expect(calls).toEqual(['get', 'put', 'get'])
    expect(wrapper.get('input[type="checkbox"]').element).toHaveProperty('checked', true)
  })

  it('shows safe failures and only verifiable help content', async () => {
    vi.mocked(getCurrentUser).mockRejectedValue(
      new ApiClientError({ code: 'PROFILE_FAILED', message: '资料读取失败' }),
    )
    const profile = await render('/profile')
    expect(profile.text()).not.toContain('资料读取失败')
    expect(toastItems).toEqual([
      expect.objectContaining({ type: 'error', message: '资料读取失败' }),
    ])

    const help = await render('/help')
    expect(help.text()).toContain('HttpOnly Cookie')
    expect(help.text()).not.toMatch(/400-|每日凌晨|保留30天|v1\.0\.0|Ctrl\+S|Ctrl\+Enter/)
  })
})
