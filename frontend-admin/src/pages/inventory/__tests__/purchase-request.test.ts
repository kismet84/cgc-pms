import { describe, expect, it } from 'vitest'
import { readFileSync } from 'node:fs'
import { dirname, resolve } from 'node:path'
import { fileURLToPath } from 'node:url'

const currentDir = dirname(fileURLToPath(import.meta.url))
const source = readFileSync(resolve(currentDir, '../purchase-request.vue'), 'utf-8')
const searchBarSource = readFileSync(
  resolve(currentDir, '../components/PurchaseRequestSearchBar.vue'),
  'utf-8',
)
const analysisPanelSource = readFileSync(
  resolve(currentDir, '../components/PurchaseRequestAnalysisPanel.vue'),
  'utf-8',
)
const modalSource = readFileSync(
  resolve(currentDir, '../components/PurchaseRequestModal.vue'),
  'utf-8',
)

describe('purchase request modal filters', () => {
  it('mounts local search, analysis and modal components while keeping page shell classes', () => {
    expect(source).toContain(
      "import PurchaseRequestSearchBar from './components/PurchaseRequestSearchBar.vue'",
    )
    expect(source).toContain(
      "import PurchaseRequestAnalysisPanel from './components/PurchaseRequestAnalysisPanel.vue'",
    )
    expect(source).toContain(
      "import PurchaseRequestModal from './components/PurchaseRequestModal.vue'",
    )
    expect(source).toContain('<div class="lg-list-page lg-page app-page purchase-request-page">')
    expect(source).toContain('<PurchaseRequestSearchBar')
    expect(source).toContain('<PurchaseRequestAnalysisPanel')
    expect(source).toContain('<PurchaseRequestModal')
    expect(searchBarSource).toContain('class="lg-search-bar purchase-request-search-bar"')
    expect(analysisPanelSource).toContain('class="lg-analysis-rail purchase-request-analysis-rail"')
  })

  it('uses a narrower material column', () => {
    expect(source).toMatch(/title:\s*'物料'[\s\S]*?width:\s*240/)
    expect(source).toMatch(/width:\s*'240px',\s*minWidth:\s*'240px',\s*maxWidth:\s*'240px'/)
  })

  it('loads purchase contracts by selected project instead of all contracts', () => {
    expect(source).toMatch(/async function loadContractsByProject/)
    expect(source).toMatch(/projectId,\s*contractType:\s*'PURCHASE'/)
    expect(source).toMatch(/async function handleProjectChange\(projectId\?\: string\)/)
    expect(source).toMatch(/formData\.contractId\s*=\s*undefined/)
    expect(modalSource).toMatch(
      /@change="\(\w+: string \| undefined\) => emit\('projectChange', \w+\)"/,
    )
    expect(source).toMatch(/await loadContractsByProject\(record\.projectId\)/)
    expect(source).not.toMatch(/fetchContracts\(\)/)
  })

  it('opens businessId deeplink through detail API and clears query', () => {
    expect(source).toContain("import { useRoute, useRouter } from 'vue-router'")
    expect(source).toContain('getPurchaseRequestDetail')
    expect(source).toContain('const route = useRoute()')
    expect(source).toContain('const router = useRouter()')
    expect(source).toContain('async function openBusinessIdFromQuery()')
    expect(source).toContain('route.query.businessId')
    expect(source).toContain('await getPurchaseRequestDetail(String(businessId))')
    expect(source).toContain('await handleView(record)')
    expect(source).toContain('delete nextQuery.businessId')
    expect(source).toContain('await router.replace({ path: route.path, query: nextQuery })')
    expect(source).toMatch(/onMounted\([\s\S]*?openBusinessIdFromQuery\(\)/)
  })

  it('uses explicit view mode so 查看态 is read-only and hides save entry', () => {
    expect(source).toMatch(/type ModalMode = 'create' \| 'edit' \| 'view'/)
    expect(source).toMatch(/const modalMode = ref<ModalMode>\('create'\)/)
    expect(source).toMatch(/const isViewMode = computed\(\(\) => modalMode\.value === 'view'\)/)
    expect(source).toMatch(
      /async function handleView\(record: PurchaseRequestVO\)[\s\S]*?modalMode\.value = 'view'/,
    )
    expect(modalSource).toMatch(
      /<a-modal[\s\S]*?:ok-button-props="isViewMode \? \{ style: \{ display: 'none' \} \} : undefined"/,
    )
    expect(modalSource).toMatch(/<a-modal[\s\S]*?:cancel-text="isViewMode \? '关闭' : '取消'"/)
    expect(modalSource).toMatch(/v-if="!isViewMode"[\s\S]*?\+ 添加物料/)
    expect(modalSource).toMatch(/v-else-if="column\.key === 'action'">[\s\S]*?v-if="!isViewMode"/)
    expect(modalSource).toContain(':disabled="isViewMode"')
    expect(source).toMatch(
      /async function handleModalOk\(\) \{[\s\S]*?if \(isViewMode\.value \|\| submitting\.value\) return/,
    )
  })

  it('keeps core list and submit handlers in the entry page after splitting', () => {
    expect(source).toContain('async function fetchData()')
    expect(source).toContain('function handleReset()')
    expect(source).toContain('function handleSubmit(record: PurchaseRequestVO)')
    expect(source).toContain('async function handleModalOk()')
    expect(source).toContain('const gridColumns = computed(() => [')
    expect(source).toContain('const itemColumns = [')
  })

  it('does not silently swallow cleanup failures when create flow rolls back', () => {
    expect(source).not.toContain('catch {')
    expect(source).toContain("console.error('采购申请创建回滚失败', cleanupError)")
  })

  it('covers APPROVED business status in fallback label and filter options', () => {
    expect(source).toContain("APPROVED: '已通过'")
    expect(searchBarSource).toContain('<a-select-option value="APPROVED">已通过</a-select-option>')
  })
})
