import { describe, it, expect, vi, beforeEach } from 'vitest'
import { mount } from '@vue/test-utils'

// ── Helpers ──
function flushPromises() {
  return new Promise((resolve) => setTimeout(resolve, 0))
}

function createFileLike(name: string, type: string, sizeMB: number = 0): File {
  return { name, type, size: sizeMB * 1024 * 1024 } as unknown as File
}

// ── Mock API modules ──
vi.mock('@/api/modules/invoice', () => ({
  getInvoiceList: vi.fn().mockResolvedValue({ records: [], total: 0 }),
  createInvoice: vi.fn().mockResolvedValue('123'),
  updateInvoice: vi.fn().mockResolvedValue(undefined),
  deleteInvoice: vi.fn().mockResolvedValue(undefined),
  verifyInvoice: vi.fn().mockResolvedValue(undefined),
  getPayRecordList: vi.fn().mockResolvedValue({ records: [] }),
}))

vi.mock('@/api/modules/file', () => ({
  uploadFile: vi.fn().mockResolvedValue({ id: '1' }),
}))

vi.mock('axios', () => ({
  default: {
    post: vi.fn(),
    isCancel: vi.fn(() => false),
  },
}))

// ── Mock ant-design-vue ──
vi.mock('ant-design-vue', () => ({
  message: {
    success: vi.fn(),
    error: vi.fn(),
    info: vi.fn(),
    warning: vi.fn(),
  },
  Modal: {
    confirm: vi.fn(),
  },
}))

// ── Import mocked modules ──
import { message } from 'ant-design-vue'
import InvoicePage from '@/pages/invoice/index.vue'
import type { InvoiceRecognizeResultVO } from '@/types/invoice'

// ── Stubs ──
const stubs = {
  'a-page-header': true,
  'a-table': true,
  'a-modal': true,
  'a-upload': true,
  'a-button': true,
  'a-form': true,
  'a-form-item': true,
  'a-input': true,
  'a-input-number': true,
  'a-select': true,
  'a-select-option': true,
  'a-date-picker': true,
  'a-textarea': true,
  'a-tag': true,
  'a-pagination': true,
  'upload-outlined': true,
}

describe('Invoice PDF Upload', () => {
  let wrapper: ReturnType<typeof mount>

  beforeEach(async () => {
    vi.clearAllMocks()
    wrapper = mount(InvoicePage, { global: { stubs } })
    await flushPromises()
  })

  it('should map recognize result to formData correctly', () => {
    const vm = wrapper.vm
    const result: InvoiceRecognizeResultVO = {
      invoiceNo: '12345678',
      invoiceAmount: '150000.00',
      invoiceType: 'VAT_SPECIAL',
    }
    vm.applyRecognitionResult(result)
    expect(vm.formData.invoiceNo).toBe('12345678')
    expect(vm.formData.invoiceAmount).toBe('150000.00')
    expect(vm.formData.invoiceType).toBe('VAT_SPECIAL')
  })

  it('should not overwrite manually entered fields', () => {
    const vm = wrapper.vm
    // Pre-fill invoiceNo manually
    vm.formData.invoiceNo = 'MANUAL-001'
    const result: InvoiceRecognizeResultVO = {
      invoiceNo: '12345678', // should NOT overwrite
      invoiceAmount: '150000.00', // should fill (empty)
    }
    vm.applyRecognitionResult(result)
    expect(vm.formData.invoiceNo).toBe('MANUAL-001') // preserved!
    expect(vm.formData.invoiceAmount).toBe('150000.00') // filled
  })

  it('should clear upload state on modal reset', () => {
    const vm = wrapper.vm
    // Pre-populate state
    vm.uploadFileList = [{ name: 'test.pdf' }]
    vm.recognizeResult = { invoiceNo: '123' }
    // Trigger modal reset via handleAdd
    vm.handleAdd()
    expect(vm.uploadFileList).toEqual([])
    expect(vm.recognizeResult).toBeNull()
  })

  it('should validate PDF file type in beforeUpload', () => {
    const vm = wrapper.vm
    const nonPdf = createFileLike('test.jpg', 'image/jpeg')
    const result = vm.handleBeforeUpload(nonPdf)
    expect(result).toBe(false)
    expect(vi.mocked(message.error)).toHaveBeenCalledWith('仅支持PDF格式')
  })

  it('should validate file size in beforeUpload', () => {
    const vm = wrapper.vm
    // PDF type passes, but size exceeds 50MB
    const oversized = createFileLike('large.pdf', 'application/pdf', 51)
    const result = vm.handleBeforeUpload(oversized)
    expect(result).toBe(false)
    expect(vi.mocked(message.error)).toHaveBeenCalledWith('文件大小不能超过50MB')
  })
})
