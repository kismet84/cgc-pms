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
})
