import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { V2ConfirmDialog, V2Input } from '@/components'
import DataMaintenancePage from '@/pages/system/DataMaintenancePage.vue'
import {
  bindDefaultDocumentVersion,
  clearNonProductionDatabase,
  loadAuditLogs,
  loadUsers,
} from '@/services/system-management'
import { apiRequest } from '@/services/request'

vi.mock('@/services/request', () => ({
  apiRequest: vi.fn(),
  isApiClientError: vi.fn(() => false),
}))

beforeEach(() => vi.mocked(apiRequest).mockReset())

describe('M7 system management contracts', () => {
  it('normalizes server identifiers without deriving list facts locally', async () => {
    vi.mocked(apiRequest).mockResolvedValue({
      pageNo: '1',
      pageSize: '20',
      total: '1',
      records: [{ id: 7, username: 'server.user', status: 'ENABLE', roleIds: [1] }],
    })

    const page = await loadUsers({ pageNo: 1, pageSize: 20, username: 'server user' })

    expect(apiRequest).toHaveBeenCalledWith(
      '/system/users?pageNo=1&pageSize=20&username=server+user',
      { signal: undefined },
    )
    expect(page).toEqual({
      pageNo: 1,
      pageSize: 20,
      total: 1,
      records: [
        {
          id: '7',
          username: 'server.user',
          status: 'ENABLE',
          roleIds: ['1'],
          roleNames: [],
          orgId: undefined,
        },
      ],
    })
  })

  it('uses read-only audit and optimistic default-binding endpoints', async () => {
    vi.mocked(apiRequest).mockResolvedValueOnce({
      pageNo: 1,
      pageSize: 20,
      total: 1,
      records: [{ id: 9, userId: 7 }],
    })
    await loadAuditLogs({ pageNo: 1, pageSize: 20, businessType: 'PAYMENT' })
    expect(apiRequest).toHaveBeenLastCalledWith(
      '/audit-logs?pageNo=1&pageSize=20&businessType=PAYMENT',
      { signal: undefined },
    )

    vi.mocked(apiRequest).mockResolvedValueOnce(undefined)
    await bindDefaultDocumentVersion('12', 3)
    expect(apiRequest).toHaveBeenLastCalledWith(
      '/document-templates/versions/12/default?expectedLockVersion=3',
      { method: 'PUT' },
    )
  })

  it('requires typed acknowledgement and final confirmation before mocked database clear', async () => {
    vi.mocked(apiRequest).mockResolvedValue('已清空 0 张业务数据表')
    const wrapper = mount(DataMaintenancePage)
    const action = wrapper
      .findAll('button')
      .find((button) => button.text() === '清空非生产业务数据')!

    expect(action.attributes('disabled')).toBeDefined()
    expect(apiRequest).not.toHaveBeenCalled()
    await wrapper.findComponent(V2Input).find('input').setValue('CLEAR_NON_PROD_DATABASE')
    await wrapper.get('input[type="checkbox"]').setValue(true)
    await action.trigger('click')
    expect(wrapper.findComponent(V2ConfirmDialog).props('open')).toBe(true)
    expect(apiRequest).not.toHaveBeenCalled()

    wrapper.findComponent(V2ConfirmDialog).vm.$emit('confirm')
    await flushPromises()

    expect(apiRequest).toHaveBeenCalledWith(
      '/system/clear-database?confirm=CLEAR_NON_PROD_DATABASE',
      { method: 'DELETE' },
    )
  })

  it('keeps the destructive service callable only through the exact backend contract', async () => {
    vi.mocked(apiRequest).mockResolvedValue('ok')
    await expect(clearNonProductionDatabase()).resolves.toBe('ok')
    expect(apiRequest).toHaveBeenCalledOnce()
  })
})
