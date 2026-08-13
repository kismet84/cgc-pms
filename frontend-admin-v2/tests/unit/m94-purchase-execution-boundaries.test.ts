import { readFileSync } from 'node:fs'
import { resolve } from 'node:path'
import { describe, expect, it } from 'vitest'

const purchaseRoot = resolve('src/pages/supply-chain/purchase-execution')
const read = (path: string) => readFileSync(resolve(path), 'utf8')
const workspace = (name: string) => read(resolve(purchaseRoot, name))

describe('M94 purchase execution boundaries', () => {
  it('keeps the old public page as a small compatibility dispatcher', () => {
    const source = read(resolve('src/pages/supply-chain/PurchaseExecutionPage.vue'))

    expect(source.split(/\r?\n/).length).toBeLessThan(25)
    expect(source).toContain('PurchaseRequestWorkspace')
    expect(source).toContain('PurchaseOrderWorkspace')
    expect(source).toContain('MaterialReceiptWorkspace')
    expect(source).not.toContain('@/services/')
  })

  it('routes the three URLs directly to focused workspaces', () => {
    const router = read(resolve('src/router/components.ts'))

    expect(router).toContain("'/inventory/purchase-request': PurchaseRequestWorkspace")
    expect(router).toContain("'/purchase/order': PurchaseOrderWorkspace")
    expect(router).toContain("'/purchase/receipt': MaterialReceiptWorkspace")
  })

  it('gives every focused workspace its own list, detail and save lifecycle', () => {
    const sources = [
      workspace('PurchaseRequestWorkspace.vue'),
      workspace('PurchaseOrderWorkspace.vue'),
      workspace('MaterialReceiptWorkspace.vue'),
    ]

    for (const source of sources) {
      expect(source).toContain('const records = ref<')
      expect(source).toContain('const selected = ref<')
      expect(source).toContain('async function loadPage()')
      expect(source).toContain('async function selectRecord(')
      expect(source).toContain('async function save(')
      expect(source).toContain('async function submitSelected(')
      expect(source).not.toContain('route.path')
      expect(source).toContain('<style src="./purchase-execution.css"></style>')
      expect(source).not.toContain('<style scoped src="./purchase-execution.css"></style>')
    }
  })

  it('keeps deep-link and compensated-save contracts at their owners', () => {
    const request = workspace('PurchaseRequestWorkspace.vue')
    const order = workspace('PurchaseOrderWorkspace.vue')
    const receipt = workspace('MaterialReceiptWorkspace.vue')
    const orderApplication = read(resolve(purchaseRoot, 'application/save-purchase-order.ts'))
    const receiptApplication = read(resolve(purchaseRoot, 'application/save-material-receipt.ts'))

    expect(request).toContain('route.query.requestId')
    expect(request).toContain('./application/save-purchase-request')
    expect(order).toContain('./application/save-purchase-order')
    expect(order).toContain('NewPurchaseOrderSaveError')
    expect(receipt).toContain('./application/save-material-receipt')
    expect(receipt).toContain('NewMaterialReceiptSaveError')
    expect(orderApplication).toContain('await dependencies.deleteDraft(id)')
    expect(receiptApplication).toContain('await dependencies.deleteDraft(id)')
  })

  it('shares only cohesive presentation and attachment behavior', () => {
    const detail = workspace('PurchaseExecutionDetail.vue')
    const attachments = workspace('PurchaseExecutionAttachments.vue')
    const model = workspace('model.ts')

    expect(detail).toContain('<PurchaseExecutionAttachments')
    expect(detail).not.toContain('@/services/supply-chain')
    expect(attachments).toContain('listSiteFiles')
    expect(attachments).toContain('loadDocumentGenerationHistory')
    expect(attachments).toContain('Promise.allSettled')
    expect(attachments).toContain("'附件读取失败'")
    expect(attachments).toContain("'单据历史读取失败'")
    expect(model).toContain('requestDetailTable')
    expect(model).toContain('orderDetailTable')
    expect(model).toContain('receiptDetailTable')
    expect(model).not.toMatch(/\b(?:ref|reactive|watch)\s*\(/)
  })
})
