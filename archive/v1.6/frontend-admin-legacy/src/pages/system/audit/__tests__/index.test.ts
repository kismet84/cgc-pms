import { flushPromises, mount } from '@vue/test-utils'
import { describe, expect, it, vi } from 'vitest'
import AuditLogPage from '../index.vue'

const mocks = vi.hoisted(() => ({ getAuditLogs: vi.fn() }))

vi.mock('@/api/modules/audit', () => ({ getAuditLogs: mocks.getAuditLogs }))

describe('system audit log page', () => {
  it('shows an error and retries the list request', async () => {
    mocks.getAuditLogs.mockRejectedValueOnce(new Error('offline')).mockResolvedValueOnce({
      records: [{ id: '1', operationType: 'CREATE', businessType: 'PROJECT' }],
      total: 1,
      pageNo: 1,
      pageSize: 20,
    })

    const wrapper = mount(AuditLogPage, {
      global: {
        stubs: {
          'a-alert': { template: '<div><slot name="action" /></div>' },
          'a-breadcrumb': { template: '<div><slot /></div>' },
          'a-breadcrumb-item': { template: '<span><slot /></span>' },
          'a-button': {
            emits: ['click'],
            template: '<button @click="$emit(\'click\')"><slot /></button>',
          },
          'a-empty': { template: '<div>暂无审计日志</div>' },
          'a-input': true,
          'a-pagination': true,
          'a-table': {
            props: ['dataSource'],
            template: '<div>{{ dataSource?.[0]?.operationType }}</div>',
          },
          'a-tag': true,
        },
      },
    })
    await flushPromises()

    expect(wrapper.text()).toContain('重新加载')
    await wrapper.get('button').trigger('click')
    await flushPromises()

    expect(mocks.getAuditLogs).toHaveBeenCalledTimes(2)
    expect(wrapper.text()).toContain('CREATE')
  })
})
