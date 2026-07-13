import { describe, expect, it } from 'vitest'
import { readFileSync } from 'node:fs'
import { dirname, resolve } from 'node:path'
import { fileURLToPath } from 'node:url'

const currentDir = dirname(fileURLToPath(import.meta.url))
const pageSource = readFileSync(resolve(currentDir, '../index.vue'), 'utf-8')
const composableSource = readFileSync(
  resolve(currentDir, '../composables/useRequisitionList.ts'),
  'utf-8',
)

describe('requisition list production hardening', () => {
  it('hydrates and persists filters through route query', () => {
    expect(pageSource).toContain("import { useRoute, useRouter } from 'vue-router'")
    expect(pageSource).toContain('useRequisitionList({ route, router })')
    expect(composableSource).toContain('readStringQuery(route.query.projectId)')
    expect(composableSource).toContain('readStringQuery(route.query.requisitionCode)')
    expect(composableSource).toContain('readPositiveIntQuery(route.query.pageNo, 1)')
    expect(composableSource).toContain(
      'await router.replace({ path: route.path, query: nextQuery })',
    )
  })

  it('tracks explicit loaded and error states with retry support', () => {
    expect(composableSource).toContain('const listError = ref<string | null>(null)')
    expect(composableSource).toContain('const hasLoaded = ref(false)')
    expect(pageSource).toContain('<a-result')
    expect(pageSource).toContain('title="领料申请列表加载失败"')
    expect(pageSource).toContain('<LgEmptyState description="暂无符合条件的领料申请">')
  })
})
