import { describe, expect, it } from 'vitest'
import { readFileSync } from 'node:fs'
import { dirname, resolve } from 'node:path'
import { fileURLToPath } from 'node:url'

const currentDir = dirname(fileURLToPath(import.meta.url))
const paymentSource = readFileSync(resolve(currentDir, '../index.vue'), 'utf-8')
const formModalSource = readFileSync(
  resolve(currentDir, '../components/PaymentFormModal.vue'),
  'utf-8',
)

describe('PaymentPage server-generated application code', () => {
  it('does not require users to maintain an application code', () => {
    expect(paymentSource).not.toMatch(/applyCode:s*''/)
    expect(formModalSource).not.toContain('v-model:value="props.formData.applyCode"')
    expect(formModalSource).not.toContain('label="申请编号"')
  })

  it('still displays the server-generated code in list and edit data', () => {
    expect(paymentSource).toContain('record.applyCode')
    expect(paymentSource).toContain('applyCode: detail.applyCode')
  })
})
