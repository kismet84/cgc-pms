import { describe, expect, it } from 'vitest'
import { readFileSync } from 'node:fs'
import { dirname, resolve } from 'node:path'
import { fileURLToPath } from 'node:url'

const currentDir = dirname(fileURLToPath(import.meta.url))
const source = readFileSync(resolve(currentDir, '../index.vue'), 'utf-8')
const configSource = readFileSync(resolve(currentDir, '../pageConfig.ts'), 'utf-8')

describe('payment page quality guardrails', () => {
  it('avoids silent catch blocks in critical payment actions', () => {
    expect(source).not.toContain('catch {')
    expect(source).not.toMatch(/catch\s*\(e\)\s*\{/)
    expect(source).toContain("message.warning('验收单依据加载失败，可稍后重试')")
    expect(source).toContain("message.warning('分包计量依据加载失败，可稍后重试')")
    expect(source).toContain("getErrorMessage(e, '删除失败，请稍后重试')")
    expect(source).toContain("getErrorMessage(e, '操作失败，请稍后重试')")
    expect(source).toContain("getErrorMessage(e, '提交审批失败，请稍后重试')")
    expect(source).toContain("getErrorMessage(e, '回写失败，请稍后重试')")
  })

  it('extracts static payment page config out of the giant component', () => {
    expect(source).toContain("from './pageConfig'")
    expect(configSource).toContain('export const APPROVAL_STATUS_LABEL')
    expect(configSource).toContain('export const APPROVAL_STATUS_COLOR')
    expect(configSource).toContain('export const PAYMENT_GRID_COLUMNS')
  })

  it('keeps the page shell split into local subcomponents', () => {
    expect(source).toContain("from './components/PaymentOverviewPanel.vue'")
    expect(source).toContain("from './components/PaymentFormModal.vue'")
    expect(source).toContain('<PaymentOverviewPanel')
    expect(source).toContain('<PaymentFormModal')
  })
})
