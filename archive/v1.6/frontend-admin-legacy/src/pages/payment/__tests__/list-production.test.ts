import { describe, expect, it } from 'vitest'
import { readFileSync } from 'node:fs'
import { dirname, resolve } from 'node:path'
import { fileURLToPath } from 'node:url'

const currentDir = dirname(fileURLToPath(import.meta.url))
const source = readFileSync(resolve(currentDir, '../index.vue'), 'utf-8')

describe('payment list production hardening', () => {
  it('keeps filters and pagination in route query for refresh persistence', () => {
    expect(source).toContain("import { useRoute, useRouter } from 'vue-router'")
    expect(source).toContain('const route = useRoute()')
    expect(source).toContain('const router = useRouter()')
    expect(source).toContain('readStringQuery(route.query.projectId)')
    expect(source).toContain('readPositiveIntQuery(route.query.pageNo, 1)')
    expect(source).toContain('await router.replace({ path: route.path, query: nextQuery })')
  })

  it('renders explicit error and empty states with retry entry', () => {
    expect(source).toContain('const listError = ref<string | null>(null)')
    expect(source).toContain('const hasLoaded = ref(false)')
    expect(source).toContain('<a-result')
    expect(source).toContain('status="error"')
    expect(source).toContain('@click="fetchData"')
    expect(source).toContain('<LgEmptyState')
  })
})
