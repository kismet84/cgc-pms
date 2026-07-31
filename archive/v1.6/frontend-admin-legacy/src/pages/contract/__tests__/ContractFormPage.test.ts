import { describe, expect, it } from 'vitest'
import { readFileSync } from 'node:fs'
import { dirname, resolve } from 'node:path'
import { fileURLToPath } from 'node:url'

const currentDir = dirname(fileURLToPath(import.meta.url))
const source = readFileSync(resolve(currentDir, '../ContractFormPage.vue'), 'utf-8')

describe('ContractFormPage modal-aware editing', () => {
  it('accepts embedded props and emits close/save for modal usage', () => {
    expect(source).toMatch(/interface Props/)
    expect(source).toMatch(/embedded\?: boolean/)
    expect(source).toMatch(/contractId\?: string/)
    expect(source).toMatch(/mode\?: 'create' \| 'edit'/)
    expect(source).toMatch(/const\s+emit\s*=\s*defineEmits<Emits>\(\)/)
    expect(source).toMatch(/const\s+isEmbedded\s*=\s*computed\(\(\)\s*=>\s*props\.embedded\)/)
    expect(source).toMatch(/const\s+isEdit\s*=\s*computed\(/)
    expect(source).toMatch(/emit\('close'\)/)
    expect(source).toMatch(/emit\('saved'\)/)
  })

  it('keeps route-driven edit mode working when not embedded', () => {
    expect(source).toMatch(/import\s+\{[^}]*useRoute[^}]*\}\s+from\s+['"]vue-router['"]/)
    expect(source).toMatch(/contractId\s*=\s*computed/)
    expect(source).toMatch(/isEdit\.value.*contractId\.value/)
  })

  it('renders the wizard and hides the outer shell in embedded mode', () => {
    expect(source).toMatch(/<div\s+v-if="!isEmbedded"\s+class="pt-page-head"/)
    expect(source).toMatch(/<div\s+v-if="!isEmbedded"\s+class="cf-cancel"/)
    expect(source).toMatch(/<StepWizard/)
    expect(source).toMatch(/<ContractItemEditor/)
    expect(source).toMatch(/<PaymentTermEditor/)
  })

  it('treats party A and party B as contract roles instead of obsolete partner types', () => {
    expect(source).toContain('partner.id !== formData.partyBId')
    expect(source).toContain('partner.id !== formData.partyAId')
    expect(source).not.toContain("partnerType === 'PARTY_A'")
    expect(source).not.toContain("partnerType === 'PARTY_B'")
  })
})
