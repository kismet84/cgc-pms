import { describe, expect, it } from 'vitest'
import { readFileSync } from 'node:fs'
import { dirname, resolve } from 'node:path'
import { fileURLToPath } from 'node:url'

const currentDir = dirname(fileURLToPath(import.meta.url))
const source = readFileSync(resolve(currentDir, '../task.vue'), 'utf-8')
const configSource = readFileSync(resolve(currentDir, '../pageConfig.ts'), 'utf-8')

describe('subcontract task page quality guardrails', () => {
  it('extracts static status and grid config out of the giant component', () => {
    expect(source).toContain("from './pageConfig'")
    expect(configSource).toContain('export const SUBCONTRACT_TASK_STATUS_LABEL')
    expect(configSource).toContain('export const SUBCONTRACT_TASK_STATUS_COLOR')
    expect(configSource).toContain('export const SUBCONTRACT_TASK_GRID_COLUMNS')
  })

  it('keeps filters and pagination in route query for refresh persistence', () => {
    expect(source).toContain("import { useRoute, useRouter } from 'vue-router'")
    expect(source).toContain('const route = useRoute()')
    expect(source).toContain('readStringQuery(route.query.projectId)')
    expect(source).toContain('readStringQuery(route.query.status)')
    expect(source).toContain('readPositiveIntQuery(route.query.pageNo, 1)')
    expect(source).toContain('await router.replace({ path: route.path, query: nextQuery })')
  })

  it('renders explicit error and empty states with retry entry', () => {
    expect(source).toContain('const listError = ref<string | null>(null)')
    expect(source).toContain('const hasLoaded = ref(false)')
    expect(source).toContain('<a-result status="error" title="分包任务列表加载失败" :sub-title="listError">')
    expect(source).toContain('<LgEmptyState description="暂无符合条件的分包任务">')
    expect(source).toContain('@click="fetchData"')
  })
})
