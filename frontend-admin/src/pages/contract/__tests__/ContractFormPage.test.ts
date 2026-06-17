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
    expect(source).toMatch(/const\s+isEdit\s*=\s*computed\(\(\)\s*=>\s*props\.mode/)
    expect(source).toMatch(/function\s+finishClose\(\)[\s\S]*?emit\('close'\)/)
    expect(source).toMatch(/function\s+doSubmit[\s\S]*?emit\('saved'\)/)
  })

  it('keeps route-driven edit mode working when not embedded', () => {
    expect(source).toMatch(/import\s+\{[^}]*useRoute[^}]*\}\s+from\s+['"]vue-router['"]/)
    expect(source).toMatch(/const\s+contractId\s*=\s*computed\(\(\)\s*=>\s*props\.contractId\s*\|\|\s*String\(route\.params\.id\s*\|\|\s*''\)\)/)
    expect(source).toMatch(/if\s*\(!isEmbedded\.value\)\s*\{\s*onBeforeRouteLeave/)
    expect(source).toMatch(/if\s*\(isEdit\.value\s*&&\s*contractId\.value\)\s*\{\s*await\s+loadContractDetail\(\)\s*\}/)
  })

  it('renders the wizard and hides the outer shell in embedded mode', () => {
    expect(source).toMatch(/<div\s+v-if="!isEmbedded"\s+class="pt-page-head"/)
    expect(source).toMatch(/<div\s+v-if="!isEmbedded"\s+class="cf-cancel"/)
    expect(source).toMatch(/<StepWizard/)
    expect(source).toMatch(/<ContractItemEditor/)
    expect(source).toMatch(/<PaymentTermEditor/)
  })
})
