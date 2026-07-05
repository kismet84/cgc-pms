import { beforeEach, describe, expect, it, vi } from 'vitest'
import { nextTick } from 'vue'
import { useRequisitionForm } from './useRequisitionForm'
import {
  getRequisitionItems,
  saveRequisitionItems,
  updateRequisition,
} from '@/api/modules/requisition'

vi.mock('ant-design-vue', () => ({
  message: {
    error: vi.fn(),
    success: vi.fn(),
    warning: vi.fn(),
  },
}))

vi.mock('@/api/modules/requisition', () => ({
  createRequisition: vi.fn(),
  updateRequisition: vi.fn(),
  getRequisitionItems: vi.fn(),
  saveRequisitionItems: vi.fn(),
}))

describe('useRequisitionForm', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('保存编辑草稿时保留已有明细的真实物料ID', async () => {
    vi.mocked(getRequisitionItems).mockResolvedValue([
      {
        id: '970000000000005804',
        requisitionId: '970000000000005704',
        materialId: '970000000000005011',
        materialName: '',
        unit: '',
        quantity: '104.0000',
        unitPrice: '5000.0000',
        amount: '520000.0000',
        useLocation: '临边防护整改补充',
      },
    ])
    vi.mocked(updateRequisition).mockResolvedValue()
    vi.mocked(saveRequisitionItems).mockResolvedValue()
    const fetchData = vi.fn().mockResolvedValue(undefined)
    const form = useRequisitionForm(fetchData)

    await form.handleEdit({
      id: '970000000000005704',
      projectId: '10001',
      warehouseId: '20001',
      approvalStatus: 'DRAFT',
    })
    await nextTick()
    await form.handleModalOk()

    expect(saveRequisitionItems).toHaveBeenCalledWith(
      '970000000000005704',
      expect.arrayContaining([
        expect.objectContaining({
          materialId: '970000000000005011',
          quantity: '104.0000',
          amount: '520000.0000',
          useLocation: '临边防护整改补充',
        }),
      ]),
    )
    expect(form.modalVisible.value).toBe(false)
    expect(fetchData).toHaveBeenCalled()
  })
})
