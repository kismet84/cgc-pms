import { describe, expect, it } from 'vitest'
import { readFileSync } from 'node:fs'
import { dirname, resolve } from 'node:path'
import { fileURLToPath } from 'node:url'

const currentDir = dirname(fileURLToPath(import.meta.url))
const paymentSource = readFileSync(resolve(currentDir, '../index.vue'), 'utf-8')
const formModalSource = readFileSync(resolve(currentDir, '../components/PaymentFormModal.vue'), 'utf-8')

describe('PaymentPage save chain integrity', () => {
  describe('createApplication returns string (not {id}) — Bug FE-01 fix', () => {
    it('handleSubmit uses returned string directly, not .id property', () => {
      // createApplication returns Promise<string>, so res is a string.
      // The fix: const id = await createApplication(formData) then saveBasis(id, cleaned payload)
      expect(paymentSource).toMatch(/const\s+id\s+=\s+await\s+createApplication\(formData\)/)
      expect(paymentSource).toMatch(/const\s+basisPayload\s+=\s+buildBasisPayload\(\)/)
      expect(paymentSource).toMatch(/await\s+saveBasis\(id,\s*basisPayload\)/)
    })

    it('does NOT reference res.id for createApplication result', () => {
      // The old buggy pattern was "const res = await createApplication(...); saveBasis(res.id, ...)"
      // This must not appear in the source
      expect(paymentSource).not.toMatch(/saveBasis\(res\.id/)
    })
  })

  describe('handleEdit basis load failure protection', () => {
    it('loads application detail before filling edit form', () => {
      const handleEditFn = paymentSource.match(/async function handleEdit[\s\S]*?\n\}/)
      expect(handleEditFn?.[0]).toMatch(/await\s+getApplicationDetail\(record\.id\)/)
      expect(handleEditFn?.[0]).toMatch(/applyCode:\s*detail\.applyCode/)
      expect(handleEditFn?.[0]).toMatch(/applyAmount:\s*detail\.applyAmount/)
      expect(handleEditFn?.[0]).toMatch(/applyReason:\s*detail\.applyReason/)
      expect(handleEditFn?.[0]).toMatch(/detail\.basis\?\.length\s*\?\s*detail\.basis\s*:\s*\(await\s+getBasisList\(record\.id\)\)/)
    })

    it('shows error message and returns early on getBasisList failure', () => {
      // On failure, the function should show error and return without opening modal
      expect(paymentSource).toMatch(/message\.error\([\s\S]*?加载付款依据/)
      expect(paymentSource).toMatch(/catch[\s\S]*?message\.error[\s\S]*?return/)
    })

    it('does NOT set basisList to empty array on failure', () => {
      // The old buggy pattern: catch { basisList.value = [] } would allow saving empty list
      // After the fix, the function returns early with message.error instead
      const handleEditFn = paymentSource.match(/async function handleEdit[\s\S]*?\n\}/)
      if (handleEditFn) {
        expect(handleEditFn[0]).not.toMatch(/catch[\s\S]*?basisList\.value\s*=\s*\[\]/)
      }
    })
  })

  describe('payment basis backend contract', () => {
    it('uses backend basis field names in the modal and submit payload', () => {
      expect(formModalSource).toContain('v-model:value="item.basisType"')
      expect(formModalSource).toContain('v-model:value="item.basisId"')
      expect(formModalSource).toContain('v-model:value="item.basisAmount"')
      expect(paymentSource).toMatch(/await\s+saveBasis\(id,\s*basisPayload\)/)
    })

    it('does not submit stale sourceType/sourceId/amount field names for basis rows', () => {
      expect(formModalSource).not.toContain('v-model:value="item.sourceType"')
      expect(formModalSource).not.toContain('v-model:value="item.sourceId"')
      expect(formModalSource).not.toContain('v-model:value="item.amount"')
    })
  })
})
