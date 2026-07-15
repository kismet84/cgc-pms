import { describe, expect, it } from 'vitest'
import { readFileSync } from 'node:fs'
import { dirname, resolve } from 'node:path'
import { fileURLToPath } from 'node:url'

const currentDir = dirname(fileURLToPath(import.meta.url))
const orderSource = readFileSync(resolve(currentDir, '../order.vue'), 'utf-8')
const configSource = readFileSync(resolve(currentDir, '../pageConfig.ts'), 'utf-8')
const workspaceSource = readFileSync(
  resolve(currentDir, '../components/VariationOrderWorkspace.vue'),
  'utf-8',
)
const modalSource = readFileSync(
  resolve(currentDir, '../components/VariationOrderModal.vue'),
  'utf-8',
)

describe('VariationOrderPage save chain integrity', () => {
  describe('createVarOrder returns string (not {id}) — Bug FE-01 fix', () => {
    it('handleSubmit uses returned string directly, not .id property', () => {
      // createVarOrder returns Promise<string>, so res is a string.
      // The fix: const newId = await createVarOrder(formData) then saveVarOrderItems(newId, ...)
      expect(orderSource).toMatch(/const\s+newId\s+=\s+await\s+createVarOrder\(formData\)/)
      expect(orderSource).toMatch(/await\s+saveVarOrderItems\(newId,\s*effectiveItems\)/)
    })

    it('does NOT reference res.id for createVarOrder result', () => {
      // The old buggy pattern was "const res = await createVarOrder(...); saveVarOrderItems(res.id, ...)"
      // This must not appear in the source
      expect(orderSource).not.toMatch(/saveVarOrderItems\(res\.id/)
    })
  })

  describe('new variation defaults from contract items', () => {
    it('loads contract items and cost subjects for variation detail rows', () => {
      expect(orderSource).toMatch(/getContractItems/)
      expect(orderSource).toMatch(/getCostSubjectTree/)
      expect(orderSource).toMatch(/loadContractItems\(contractId\)/)
    })

    it('renders a cost subject selector in detail rows', () => {
      expect(modalSource).toMatch(/title="成本科目"/)
      expect(modalSource).toMatch(/v-model:value="item\.costSubjectId"/)
      expect(modalSource).toMatch(/:options="costSubjectOptions"/)
      expect(modalSource).toMatch(/popup-match-select-width="false"/)
    })

    it('saves only detail rows with quantity greater than zero', () => {
      expect(orderSource).toMatch(/activeItems\s*=\s*itemList\.value[\s\S]*?quantity[\s\S]*?>\s*0/)
      expect(orderSource).toMatch(/const\s+effectiveItems\s*=\s*activeItems/)
      expect(orderSource).toMatch(/saveVarOrderItems\((id|newId),\s*effectiveItems\)/)
    })

    it('blocks submit when project/contract/varType or valid detail is missing', () => {
      expect(orderSource).toMatch(
        /if\s*\(!formData\.projectId\)[\s\S]*?message\.warning\('请选择项目'\)/,
      )
      expect(orderSource).toMatch(
        /if\s*\(!formData\.contractId\)[\s\S]*?message\.warning\('请选择合同'\)/,
      )
      expect(orderSource).toMatch(
        /if\s*\(!formData\.varType\)[\s\S]*?message\.warning\('请选择变更类型'\)/,
      )
      expect(orderSource).toMatch(
        /if\s*\(!activeItems\.length\)[\s\S]*?message\.warning\('请至少保留一条有效明细'\)/,
      )
      expect(orderSource).toMatch(/missingCostSubject[\s\S]*?message\.warning\('请选择成本科目'\)/)
    })

    it('rolls back the newly created draft if saveVarOrderItems fails', () => {
      expect(orderSource).toMatch(/const\s+newId\s+=\s+await\s+createVarOrder\(formData\)/)
      expect(orderSource).toMatch(/await\s+saveVarOrderItems\(newId,\s*effectiveItems\)/)
      expect(orderSource).toMatch(
        /await\s+deleteVarOrder\(newId\)\.catch\(\(cleanupError: unknown\)\s*=>\s*\{/,
      )
      expect(orderSource).toContain('console.error(cleanupError)')
    })
  })

  describe('handleEdit detail load failure — Bug FE-02 fix', () => {
    it('shows error message and returns early on getVarOrderDetail failure', () => {
      // On failure, the function should return without opening the modal
      expect(orderSource).toMatch(/message\.error\([\s\S]*?加载变更明细/)
      // The function should return early (before modalVisible.value = true)
      expect(orderSource).toMatch(/catch[\s\S]*?message\.error[\s\S]*?return/)
    })

    it('does NOT set itemList to empty array on failure', () => {
      // The old buggy pattern: catch { itemList.value = [] } would allow saving empty list
      // After the fix, the function returns early with message.error instead
      const handleEditFn = orderSource.match(/async function handleEdit[\s\S]*?\n\}/)
      if (handleEditFn) {
        expect(handleEditFn[0]).not.toMatch(/catch[\s\S]*?itemList\.value\s*=\s*\[\]/)
      }
    })
  })

  describe('giant component minimum split', () => {
    it('extracts static variation config out of the page component', () => {
      expect(orderSource).toContain("from './pageConfig'")
      expect(configSource).toContain('export const VAR_TYPE_OPTIONS')
      expect(configSource).toContain('export const APPROVAL_STATUS_LABEL')
      expect(configSource).toContain('export function buildVariationGridColumns')
    })

    it('keeps workspace and modal markup in local variation components', () => {
      expect(orderSource).toContain(
        "import VariationOrderWorkspace from './components/VariationOrderWorkspace.vue'",
      )
      expect(orderSource).toContain(
        "import VariationOrderModal from './components/VariationOrderModal.vue'",
      )
      expect(workspaceSource).toContain(
        'class="lg-left vo-main-column project-operation-main-column"',
      )
      expect(workspaceSource).toContain(
        'class="lg-analysis-rail vo-analysis-rail project-operation-analysis-rail"',
      )
      expect(modalSource).toContain('<a-modal')
      expect(modalSource).toContain('title="成本科目"')
    })
  })
})
