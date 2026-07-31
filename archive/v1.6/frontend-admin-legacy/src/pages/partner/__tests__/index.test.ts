import { readFileSync } from 'node:fs'
import { dirname, resolve } from 'node:path'
import { fileURLToPath } from 'node:url'
import { describe, expect, it } from 'vitest'

const currentDir = dirname(fileURLToPath(import.meta.url))
const source = readFileSync(resolve(currentDir, '../index.vue'), 'utf-8')

describe('partner default supplier lead days', () => {
  it('only shows the natural-day input and detail for suppliers', () => {
    expect(source).toContain('v-if="formData.partnerType === \'SUPPLIER\'"')
    expect(source).toContain('label="默认提前期（自然日）"')
    expect(source).toContain(':min="0"')
    expect(source).toContain(':max="3650"')
    expect(source).toContain(':precision="0"')
    expect(source).toContain('v-if="detailPartner.partnerType === \'SUPPLIER\'"')
  })

  it('sends null outside supplier mode and preserves zero for suppliers', () => {
    expect(source).toMatch(
      /defaultLeadDays:\s*formData\.partnerType === 'SUPPLIER'\s*\? \(formData\.defaultLeadDays \?\? null\)\s*: null/,
    )
    expect(source).toContain('defaultLeadDays: record.defaultLeadDays ?? undefined')
  })

  it('only accepts partner type codes returned by dictionary API', () => {
    expect(source).toContain("getDictDataByCode('partner_type')")
    expect(source).toContain(
      'partnerTypeOptions.value.some((item) => item.dictValue === formData.partnerType)',
    )
    expect(source).not.toContain("dictValue: 'PARTY_A'")
    expect(source).not.toContain("dictValue: 'PARTY_B'")
    expect(source).not.toContain("dictValue: 'OTHER'")
    expect(source).not.toContain("partnerType === 'PARTY_A'")
  })
})
