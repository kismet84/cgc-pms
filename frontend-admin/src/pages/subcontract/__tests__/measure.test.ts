import { describe, expect, it } from 'vitest'
import { readFileSync } from 'node:fs'
import { dirname, resolve } from 'node:path'
import { fileURLToPath } from 'node:url'

const currentDir = dirname(fileURLToPath(import.meta.url))
const source = readFileSync(resolve(currentDir, '../measure.vue'), 'utf-8')
const analysisRailSource = readFileSync(
  resolve(currentDir, '../components/SubcontractMeasureAnalysisRail.vue'),
  'utf-8',
)

describe('SubcontractMeasurePage submit-approval button', () => {
  it('imports submitMeasureForApproval from API module', () => {
    expect(source).toMatch(/import\s+\{[^}]*submitMeasureForApproval[^}]*\}\s+from/)
  })

  it('has handleSubmitApproval function with Modal.confirm', () => {
    expect(source).toMatch(/function handleSubmitApproval[\s\S]*?Modal\.confirm\(/)
  })

  it('calls submitMeasureForApproval inside handleSubmitApproval onOk', () => {
    expect(source).toMatch(/function handleSubmitApproval[\s\S]*?submitMeasureForApproval\(/)
  })

  it('calls fetchData after successful submit', () => {
    expect(source).toMatch(/function handleSubmitApproval[\s\S]*?fetchData\(/)
  })

  it('renders 提交审批 button only when approvalStatus is DRAFT', () => {
    expect(source).toMatch(/approvalStatus\s*===\s*APPROVAL_DRAFT/)
  })

  it('wires 提交审批 button to handleSubmitApproval handler', () => {
    expect(source).toMatch(/handleSubmitApproval\(row\)/)
  })

  it('mounts split local components from the entry page', () => {
    expect(source).toContain(
      "import SubcontractMeasureAnalysisRail from './components/SubcontractMeasureAnalysisRail.vue'",
    )
    expect(source).toContain(
      "import SubcontractMeasureModal from './components/SubcontractMeasureModal.vue'",
    )
    expect(source).toContain('<SubcontractMeasureAnalysisRail')
    expect(source).toContain('<SubcontractMeasureModal')
  })

  it('keeps analysis rail as lg-grid sibling instead of nesting it under main column', () => {
    const mainColumnClose = source.indexOf('</div>\n\n      <SubcontractMeasureAnalysisRail')
    expect(mainColumnClose).toBeGreaterThan(-1)
    expect(source).toMatch(
      /<div class="lg-grid">[\s\S]*<div class="subcontract-measure-main-column">[\s\S]*<\/main>\s*<\/div>\s*<SubcontractMeasureAnalysisRail/,
    )
    expect(analysisRailSource).toContain(
      '<aside class="lg-analysis-rail subcontract-measure-analysis-rail"',
    )
  })

  it('keeps the key page shell classes and core action entry', () => {
    expect(source).toContain('class="lg-list-page lg-page app-page subcontract-measure-page"')
    expect(source).toContain('class="lg-search-bar subcontract-measure-search-bar"')
    expect(source).toContain('class="lg-list-table-panel subcontract-measure-table-panel"')
    expect(source).toContain('class="subcontract-measure-code-link"')
    expect(source).toContain('@click="handleAdd"')
  })

  it('opens businessId deeplink through detail API and clears query', () => {
    expect(source).toContain("import { useRoute, useRouter } from 'vue-router'")
    expect(source).toContain('getMeasureDetail')
    expect(source).toContain('const route = useRoute()')
    expect(source).toContain('const router = useRouter()')
    expect(source).toContain('async function openBusinessIdFromQuery()')
    expect(source).toContain('route.query.businessId')
    expect(source).toContain('await getMeasureDetail(String(businessId))')
    expect(source).toContain('await handleView(record)')
    expect(source).toContain('delete nextQuery.businessId')
    expect(source).toContain('await router.replace({ path: route.path, query: nextQuery })')
    expect(source).toMatch(/onMounted\([\s\S]*?openBusinessIdFromQuery\(\)/)
  })

  it('keeps KPI template classes and matching scoped style selectors', () => {
    expect(source).toContain('class="subcontract-measure-kpi-summary"')
    expect(source).toContain('class="subcontract-measure-kpi-item"')
    expect(source).toContain('class="subcontract-measure-kpi-icon is-blue"')
    expect(source).toContain('class="subcontract-measure-kpi-label"')
    expect(source).toContain('class="subcontract-measure-kpi-hint"')
    expect(source).toContain('.subcontract-measure-kpi-summary {')
    expect(source).toContain('.subcontract-measure-kpi-item {')
    expect(source).toContain('.subcontract-measure-kpi-icon {')
    expect(source).toContain('.subcontract-measure-kpi-label,')
    expect(source).toContain('.subcontract-measure-kpi-hint {')
  })
})
