import { describe, expect, it } from 'vitest'
import { readFileSync } from 'node:fs'
import { dirname, resolve } from 'node:path'
import { fileURLToPath } from 'node:url'

const currentDir = dirname(fileURLToPath(import.meta.url))
const source = readFileSync(resolve(currentDir, '../order.vue'), 'utf-8')

describe('PurchaseOrderPage submit-approval button', () => {
  it('imports submitOrderForApproval from API module', () => {
    expect(source).toMatch(/import\s+\{[^}]*submitOrderForApproval[^}]*\}\s+from/)
  })

  it('has handleSubmitApproval function with Modal.confirm', () => {
    expect(source).toMatch(/function handleSubmitApproval[\s\S]*?Modal\.confirm\(/)
  })

  it('calls submitOrderForApproval inside handleSubmitApproval onOk', () => {
    expect(source).toMatch(/function handleSubmitApproval[\s\S]*?submitOrderForApproval\(/)
  })

  it('calls fetchData after successful submit', () => {
    expect(source).toMatch(/function handleSubmitApproval[\s\S]*?fetchData\(/)
  })

  it('renders 提交审批 button only when approvalStatus is DRAFT', () => {
    expect(source).toMatch(/approvalStatus\s*===\s*APPROVAL_DRAFT/)
  })

  it('wires 提交审批 button to handleSubmitApproval handler', () => {
    expect(source).toMatch(/handleSubmitApproval\(row\)/)
  })

  it('maps contract purchase order type to Chinese display text', () => {
    expect(source).toMatch(/CONTRACT:\s*'合同采购'/)
    expect(source).toMatch(/ORDER_TYPE_LABEL\[row\.orderType\]\s*\?\?\s*row\.orderType/)
  })

  it('loads only approved performing purchase contracts on mount', () => {
    expect(source).toContain("contractType: 'PURCHASE'")
    expect(source).toContain("contractStatus: 'PERFORMING'")
    expect(source).toContain("approvalStatus: 'APPROVED'")
  })

  it('reloads project contracts with approved performing purchase filters', () => {
    expect(source).toContain('projectId: v')
    expect(source).toContain('referenceStore.fetchContracts({')
  })

  it('reloads modal contract options with approved performing purchase filters after project change', () => {
    expect(source).toContain('formData.contractId = undefined')
    expect(source).toContain('formData.partnerId = undefined')
  })
})

describe('purchase order page quality guardrails', () => {
  it('does not open the modal after detail loading fails', () => {
    expect(source).toMatch(/catch[\s\S]*?message\.error\('加载明细失败'\)[\s\S]*?return/)
  })
})
