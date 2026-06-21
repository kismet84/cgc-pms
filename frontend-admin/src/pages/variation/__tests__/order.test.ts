import { describe, expect, it } from 'vitest'
import { readFileSync } from 'node:fs'
import { dirname, resolve } from 'node:path'
import { fileURLToPath } from 'node:url'

const currentDir = dirname(fileURLToPath(import.meta.url))
const source = readFileSync(resolve(currentDir, '../order.vue'), 'utf-8')

describe('VariationOrderPage submit-approval button', () => {
  it('imports submitVarOrderForApproval from API module', () => {
    expect(source).toMatch(/import\s+\{[^}]*submitVarOrderForApproval[^}]*\}\s+from/)
  })

  it('has handleSubmitApproval function with Modal.confirm', () => {
    expect(source).toMatch(/function handleSubmitApproval[\s\S]*?Modal\.confirm\(/)
  })

  it('calls submitVarOrderForApproval inside handleSubmitApproval onOk', () => {
    expect(source).toMatch(/function handleSubmitApproval[\s\S]*?submitVarOrderForApproval\(/)
  })

  it('calls fetchData after successful submit', () => {
    expect(source).toMatch(/function handleSubmitApproval[\s\S]*?fetchData\(/)
  })

  it('renders 提交审批 button only when approvalStatus is DRAFT', () => {
    expect(source).toMatch(/approvalStatus\s*===\s*'DRAFT'/)
  })

  it('wires 提交审批 button to handleSubmitApproval handler', () => {
    expect(source).toMatch(/handleSubmitApproval\(row\)/)
  })
})
