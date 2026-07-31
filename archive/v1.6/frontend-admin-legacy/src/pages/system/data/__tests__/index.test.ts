import { describe, it, expect, vi, beforeEach } from 'vitest'
import { mount } from '@vue/test-utils'
import { nextTick } from 'vue'

const { mockRequest, mockMessage, mockConfirm } = vi.hoisted(() => ({
  mockRequest: vi.fn().mockResolvedValue('数据库已清空'),
  mockMessage: {
    success: vi.fn(),
    error: vi.fn(),
  },
  mockConfirm: vi.fn(),
}))

vi.mock('@/api/request', () => ({
  request: mockRequest,
}))

vi.mock('ant-design-vue', async () => {
  const actual = await vi.importActual('ant-design-vue')
  return {
    ...(actual as object),
    message: mockMessage,
    Modal: {
      confirm: mockConfirm,
    },
  }
})

import SystemDataPage from '../index.vue'

describe('SystemDataPage', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    mockRequest.mockResolvedValue('数据库已清空')
  })

  function mountPage() {
    return mount(SystemDataPage, {
      global: {
        stubs: {
          'a-breadcrumb': { template: '<div class="stub-breadcrumb"><slot /></div>' },
          'a-breadcrumb-item': { template: '<span class="stub-breadcrumb-item"><slot /></span>' },
          'a-button': {
            template: '<button class="stub-button" @click="$emit(\'click\')"><slot /></button>',
            emits: ['click'],
          },
        },
      },
    })
  }

  it('sends explicit non-prod confirmation code when clearing database', async () => {
    const wrapper = mountPage()

    await wrapper.find('.stub-button').trigger('click')
    expect(mockConfirm).toHaveBeenCalledTimes(1)

    const confirmOptions = mockConfirm.mock.calls[0][0]
    await confirmOptions.onOk()
    await nextTick()

    expect(mockRequest).toHaveBeenCalledWith({
      url: '/system/clear-database',
      method: 'DELETE',
      params: { confirm: 'CLEAR_NON_PROD_DATABASE' },
    })
    expect(mockMessage.success).toHaveBeenCalledWith('数据库已清空')
  })
})
