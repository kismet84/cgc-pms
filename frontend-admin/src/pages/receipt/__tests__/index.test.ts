import { describe, expect, it } from 'vitest'
import { readFileSync } from 'node:fs'
import { dirname, resolve } from 'node:path'
import { fileURLToPath } from 'node:url'

const currentDir = dirname(fileURLToPath(import.meta.url))
const indexSource = readFileSync(resolve(currentDir, '../index.vue'), 'utf-8')
const useReceiptListSource = readFileSync(
  resolve(currentDir, '../composables/useReceiptList.ts'),
  'utf-8',
)
const formSource = readFileSync(resolve(currentDir, '../components/ReceiptFormModal.vue'), 'utf-8')
const formLogicSource = readFileSync(
  resolve(currentDir, '../composables/useReceiptForm.ts'),
  'utf-8',
)

describe('ReceiptIndexPage submit-approval button', () => {
  it('imports submitReceiptForApproval in useReceiptList composable', () => {
    expect(useReceiptListSource).toMatch(/import\s+\{[^}]*submitReceiptForApproval[^}]*\}\s+from/)
  })

  it('has handleSubmitApproval function with Modal.confirm in useReceiptList', () => {
    expect(useReceiptListSource).toMatch(/function handleSubmitApproval[\s\S]*?Modal\.confirm\(/)
  })

  it('calls submitReceiptForApproval inside handleSubmitApproval onOk in useReceiptList', () => {
    expect(useReceiptListSource).toMatch(
      /function handleSubmitApproval[\s\S]*?submitReceiptForApproval\(/,
    )
  })

  it('calls fetchData after successful submit in useReceiptList', () => {
    expect(useReceiptListSource).toMatch(/function handleSubmitApproval[\s\S]*?fetchData\(/)
  })

  it('renders 提交审批 button only when approvalStatus is DRAFT in index.vue', () => {
    expect(indexSource).toMatch(/approvalStatus\s*===\s*'DRAFT'/)
  })

  it('wires 提交审批 button to handleSubmitApproval handler in index.vue', () => {
    expect(indexSource).toMatch(/handleSubmitApproval\(row\)/)
  })

  it('maps ACCEPTED quality status to a Chinese label and tag color in useReceiptList', () => {
    expect(useReceiptListSource).toMatch(/ACCEPTED:\s*'让步接收'/)
    expect(useReceiptListSource).toMatch(/ACCEPTED:\s*'warning'/)
  })

  it('forces unqualified material disposition and receipt proof before save', () => {
    expect(formSource).toContain('item.dispositionType')
    expect(formSource).toContain('item.dispositionReason')
    expect(formLogicSource).toContain("uploadFile(proofFile.value, 'MATERIAL_RECEIPT'")
    expect(formLogicSource).toContain('不合格数量必须选择处置方式并填写原因')
  })

  it('creates supplier return, uploads proof, then confirms stock and budget reversal', () => {
    expect(indexSource).toContain('createSupplierReturn')
    expect(indexSource).toContain("uploadFile(supplierReturnFile.value, 'SUPPLIER_RETURN'")
    expect(indexSource).toContain('await confirmSupplierReturn(id)')
    expect(indexSource.indexOf('createSupplierReturn')).toBeLessThan(
      indexSource.indexOf('await confirmSupplierReturn(id)'),
    )
  })

  it('offers procurement trace from a receipt', () => {
    expect(indexSource).toContain("getProcurementTrace('receipts', row.id)")
    expect(indexSource).toContain('全链追溯')
  })
})
