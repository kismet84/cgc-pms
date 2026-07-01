import { describe, expect, it } from 'vitest'
import { readFileSync } from 'node:fs'
import { dirname, resolve } from 'node:path'
import { fileURLToPath } from 'node:url'

const currentDir = dirname(fileURLToPath(import.meta.url))
const source = readFileSync(resolve(currentDir, '../purchase-request.vue'), 'utf-8')

describe('purchase request modal filters', () => {
  it('uses a narrower material column', () => {
    expect(source).toMatch(/title:\s*'物料'[\s\S]*?width:\s*240/)
    expect(source).toMatch(/width:\s*'240px',\s*minWidth:\s*'240px',\s*maxWidth:\s*'240px'/)
  })

  it('loads purchase contracts by selected project instead of all contracts', () => {
    expect(source).toMatch(/async function loadContractsByProject/)
    expect(source).toMatch(/projectId,\s*contractType:\s*'PURCHASE'/)
    expect(source).toMatch(/async function handleProjectChange\(projectId\?\: string\)/)
    expect(source).toMatch(/formData\.contractId\s*=\s*undefined/)
    expect(source).toMatch(/@change="handleProjectChange"/)
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
})
