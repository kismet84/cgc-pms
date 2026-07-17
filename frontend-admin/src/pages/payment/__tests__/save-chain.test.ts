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

describe('PaymentPage save chain integrity', () => {
  describe('createApplication returns string (not {id})', () => {
    it('uses the returned id for sources and attachments', () => {
      expect(paymentSource).toMatch(/id\s*=\s*await\s+createApplication\(formData\)/)
      expect(paymentSource).toMatch(/await\s+saveApplicationSources\(id!,\s*sources\)/)
      expect(paymentSource).toMatch(/uploadFile\(proofFile\.value,\s*'PAYMENT',\s*id!/)
    })

    it('does NOT reference res.id for createApplication result', () => {
      // The old buggy pattern was "const res = await createApplication(...); saveBasis(res.id, ...)"
      // This must not appear in the source
      expect(paymentSource).not.toMatch(/saveApplicationSources\(res\.id/)
    })
  })

  describe('handleEdit source load failure protection', () => {
    it('loads application detail before filling edit form', () => {
      const handleEditFn = paymentSource.match(/async function handleEdit[\s\S]*?\n\}/)
      expect(handleEditFn?.[0]).toMatch(/await\s+getApplicationDetail\(record\.id\)/)
      expect(handleEditFn?.[0]).toMatch(/applyCode:\s*detail\.applyCode/)
      expect(handleEditFn?.[0]).toMatch(/applyAmount:\s*detail\.applyAmount/)
      expect(handleEditFn?.[0]).toMatch(/applyReason:\s*detail\.applyReason/)
      expect(handleEditFn?.[0]).toMatch(/await\s+getApplicationSources\(record\.id\)/)
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
        expect(handleEditFn[0]).not.toMatch(/catch[\s\S]*?sourceList\.value\s*=\s*\[\]/)
      }
    })
  })

  describe('unified payment source backend contract', () => {
    it('uses source type, reference and amount fields', () => {
      expect(formModalSource).toContain('v-model:value="record.sourceType"')
      expect(formModalSource).toContain('v-model:value="record.sourceRefId"')
      expect(formModalSource).toContain('v-model:value="record.sourceAmount"')
      expect(paymentSource).toMatch(/await\s+saveApplicationSources\(id!,\s*sources\)/)
    })

    it('selects subcontract payment documents and shows server-computed available balance', () => {
      expect(paymentSource).toContain('getPaymentSourceOptions')
      expect(paymentSource).not.toContain('excludeApplicationId')
      expect(formModalSource).toContain("record.sourceType === 'SUB_MEASURE'")
      expect(formModalSource).toContain("record.sourceType === 'SETTLEMENT'")
      expect(formModalSource).toContain('请选择当前上下文内的可付业务单据')
      expect(formModalSource).toContain('可申请 ${option.availableAmount}')
      expect(formModalSource).toContain(':disabled="record.sourceType === \'DIRECT\'"')
    })

    it('does not submit stale sourceType/sourceId/amount field names for basis rows', () => {
      expect(formModalSource).not.toContain('v-model:value="record.sourceId"')
      expect(formModalSource).not.toContain('v-model:value="record.amount"')
    })
  })
})
