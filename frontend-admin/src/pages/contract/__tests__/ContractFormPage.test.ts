import { describe, expect, it } from 'vitest'
import { readFileSync } from 'node:fs'
import { dirname, resolve } from 'node:path'
import { fileURLToPath } from 'node:url'

const currentDir = dirname(fileURLToPath(import.meta.url))
const source = readFileSync(resolve(currentDir, '../ContractFormPage.vue'), 'utf-8')

describe('ContractFormPage edit mode', () => {
  // ── TEST 1: Edit mode detection via useRoute ──
  it('imports useRoute and derives isEdit from route.params.id', () => {
    expect(source).toMatch(/import\s+\{[^}]*useRoute[^}]*\}\s+from\s+['"]vue-router['"]/)
    expect(source).toMatch(/const\s+route\s*=\s*useRoute\(\)/)
    expect(source).toMatch(/const\s+isEdit\s*=\s*computed\(\(\)\s*=>\s*!!route\.params\.id\)/)
    expect(source).toMatch(/const\s+contractId\s*=\s*computed\(\(\)\s*=>\s*String\(route\.params\.id\s*\|\|\s*''\)\)/)
  })

  // ── TEST 2: API imports for edit mode ──
  it('imports getContractDetail, updateContract, getContractItems, getPaymentTerms from API', () => {
    expect(source).toMatch(/import\s+\{[\s\S]*updateContract[\s\S]*\}\s+from/)
    expect(source).toMatch(/import\s+\{[\s\S]*getContractDetail[\s\S]*\}\s+from/)
    expect(source).toMatch(/import\s+\{[\s\S]*getContractItems[\s\S]*\}\s+from/)
    expect(source).toMatch(/import\s+\{[\s\S]*getPaymentTerms[\s\S]*\}\s+from/)
  })

  // ── TEST 3: loadContractDetail function exists ──
  it('has loadContractDetail function that fetches contract, items, and terms', () => {
    expect(source).toMatch(/async\s+function\s+loadContractDetail/)
    expect(source).toMatch(/getContractDetail\(contractId\.value\)/)
    expect(source).toMatch(/getContractItems\(contractId\.value\)/)
    expect(source).toMatch(/getPaymentTerms\(contractId\.value\)/)
  })

  // ── TEST 4: loadContractDetail populates formData from API response ──
  it('loadContractDetail populates all formData fields from contract response', () => {
    expect(source).toMatch(/formData\.contractName\s*=\s*contract\.contractName/)
    expect(source).toMatch(/formData\.contractType\s*=\s*contract\.contractType/)
    expect(source).toMatch(/formData\.projectId\s*=\s*contract\.projectId/)
    expect(source).toMatch(/formData\.partyAId\s*=\s*contract\.partyAId/)
    expect(source).toMatch(/formData\.contractAmount\s*=\s*Number\(contract\.contractAmount\)/)
    expect(source).toMatch(/formData\.warrantyRate\s*=\s*contract\.warrantyRate/)
  })

  // ── TEST 5: onMounted triggers loadContractDetail when isEdit ──
  it('onMounted calls loadContractDetail when isEdit is true', () => {
    expect(source).toMatch(/if\s*\(isEdit\.value\)\s*\{\s*await\s+loadContractDetail\(\)\s*\}/)
  })

  // ── TEST 6: Edit mode submit calls updateContract (PUT) ──
  it('doSubmit calls updateContract instead of createContract when isEdit', () => {
    expect(source).toMatch(/if\s*\(isEdit\.value\)\s*\{[\s\S]*?updateContract\(contractId\.value,\s*buildContractPayload\(\)\)/)
    expect(source).toMatch(/targetId\s*=\s*contractId\.value/)
  })

  // ── TEST 7: Create mode submit calls createContract (POST) ──
  it('doSubmit calls createContract when not in edit mode', () => {
    expect(source).toMatch(/\}\s*else\s*\{[\s\S]*?createContract\(buildContractPayload\(\)\)/)
    expect(source).toMatch(/targetId\s*=\s*created\?\.\s*id/)
  })

  // ── TEST 8: Submit saves items and terms for both modes ──
  it('doSubmit saves items and terms after creating/updating', () => {
    expect(source).toMatch(/saveContractItems\(targetId,\s*buildItemsPayload\(\)\)/)
    expect(source).toMatch(/savePaymentTerms\(targetId,\s*buildTermsPayload\(\)\)/)
  })

  // ── TEST 9: Breadcrumb reflects edit vs create mode ──
  it('breadcrumb shows 编辑合同 in edit mode and 新建合同 in create mode', () => {
    expect(source).toMatch(/\{\{\s*isEdit\s*\?\s*['"]编辑合同['"]\s*:\s*['"]新建合同['"]\s*\}\}/)
  })

  // ── TEST 10: beforeRouteLeave guard warns on unsaved changes ──
  it('has onBeforeRouteLeave guard for unsaved changes', () => {
    expect(source).toMatch(/onBeforeRouteLeave/)
    expect(source).toMatch(/未保存的修改/)
  })

  // ── TEST 11: Success message reflects mode ──
  it('success message differs between edit and create mode', () => {
    expect(source).toMatch(/isEdit\.value\s*\?\s*['\"]合同已更新并提交审批['\"]/)
    expect(source).toMatch(/isEdit\.value\s*\?\s*['\"]合同已更新['\"]\s*:\s*['\"]合同已保存为草稿['\"]/)
  })

  // ── TEST 12: Non-regression: create mode behavior unchanged ──
  it('preserves create mode submitForApproval call for both modes', () => {
    expect(source).toMatch(/submitForApproval\(targetId\)/)
  })

  // ── TEST 13: Non-regression: StepWizard structure unchanged ──
  it('preserves StepWizard component usage', () => {
    expect(source).toMatch(/<StepWizard/)
    expect(source).toMatch(/:current="current"/)
    expect(source).toMatch(/:steps="stepConfig"/)
    expect(source).toMatch(/@submit="handleSubmit"/)
  })

  // ── TEST 14: Non-regression: form validation unchanged ──
  it('preserves form validation rules', () => {
    expect(source).toMatch(/const\s+basicFormRef\s*=\s*ref<FormInstance>/)
    expect(source).toMatch(/contractName:\s*\[\s*\{\s*required:\s*true/)
    expect(source).toMatch(/async\s+function\s+validateBasic/)
    expect(source).toMatch(/function\s+validateItems/)
    expect(source).toMatch(/function\s+validateTerms/)
  })

  // ── TEST 15: loadingDetail ref exists for edit mode loading state ──
  it('has loadingDetail ref for edit mode loading state', () => {
    expect(source).toMatch(/const\s+loadingDetail\s*=\s*ref\(false\)/)
  })
})
