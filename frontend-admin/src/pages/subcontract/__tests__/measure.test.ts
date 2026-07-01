import { describe, expect, it } from 'vitest'
import { readFileSync } from 'node:fs'
import { dirname, resolve } from 'node:path'
import { fileURLToPath } from 'node:url'

const currentDir = dirname(fileURLToPath(import.meta.url))
const source = readFileSync(resolve(currentDir, '../measure.vue'), 'utf-8')

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
    expect(source).toMatch(/approvalStatus\s*===\s*'DRAFT'/)
  })

  it('wires 提交审批 button to handleSubmitApproval handler', () => {
    expect(source).toMatch(/handleSubmitApproval\(row\)/)
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
})
