import { describe, expect, it } from 'vitest'
import { readFileSync } from 'node:fs'
import { dirname, resolve } from 'node:path'
import { fileURLToPath } from 'node:url'

const currentDir = dirname(fileURLToPath(import.meta.url))
const paymentSource = readFileSync(resolve(currentDir, '../index.vue'), 'utf-8')
const formModalSource = readFileSync(resolve(currentDir, '../components/PaymentFormModal.vue'), 'utf-8')

describe('PaymentPage applyCode form chain', () => {
  it('keeps applyCode in form state for create and edit flows', () => {
    expect(paymentSource).toMatch(/const\s+formData\s*=\s*reactive<Partial<PayApplicationVO>>\(\{[\s\S]*?applyCode:\s*''/)
    expect(paymentSource).toMatch(/Object\.assign\(formData,\s*\{[\s\S]*?applyCode:\s*''/)
    expect(paymentSource).toMatch(/Object\.assign\(formData,\s*\{[\s\S]*?applyCode:\s*detail\.applyCode/)
  })

  it('renders applyCode input in create\/edit modal', () => {
    expect(formModalSource).toContain('label="申请编号"')
    expect(formModalSource).toContain('v-model:value="props.formData.applyCode"')
  })
})
