import { describe, expect, it } from 'vitest'
import { readFileSync } from 'node:fs'
import { dirname, resolve } from 'node:path'
import { fileURLToPath } from 'node:url'

const currentDir = dirname(fileURLToPath(import.meta.url))
const source = readFileSync(resolve(currentDir, '../order.vue'), 'utf-8')

describe('purchase order list production hardening', () => {
  it('hydrates and persists filters through route query', () => {
    expect(source).toContain("import { useRoute, useRouter } from 'vue-router'")
    expect(source).toContain("from '@/composables/listPageQuery'")
    expect(source).toContain('readPositiveIntQuery')
    expect(source).toContain('readStringQuery')
    expect(source).toContain('replaceListQuery')
    expect(source).toContain('readStringQuery(route.query.projectId)')
    expect(source).toContain('readStringQuery(route.query.keyword)')
    expect(source).toContain('readPositiveIntQuery(route.query.pageNo, 1)')
    expect(source).toContain('await router.replace({ path: route.path, query: nextQuery })')
  })

  it('renders explicit error and empty states with retry entry', () => {
    expect(source).toContain('const listError = ref<string | null>(null)')
    expect(source).toContain('const hasLoaded = ref(false)')
    expect(source).toContain('<a-result')
    expect(source).toContain('title="采购订单列表加载失败"')
    expect(source).toContain('<LgEmptyState description="暂无符合条件的采购订单">')
  })
})
