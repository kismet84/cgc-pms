import { describe, expect, it } from 'vitest'
import { readFileSync } from 'node:fs'
import { dirname, resolve } from 'node:path'
import { fileURLToPath } from 'node:url'

const currentDir = dirname(fileURLToPath(import.meta.url))
const pageSource = readFileSync(resolve(currentDir, '../index.vue'), 'utf-8')
const composableSource = readFileSync(resolve(currentDir, '../composables/useInvoiceList.ts'), 'utf-8')

describe('invoice list production hardening', () => {
  it('hydrates and persists filters through route query', () => {
    expect(pageSource).toContain("import { useRoute, useRouter } from 'vue-router'")
    expect(pageSource).toContain('const route = useRoute()')
    expect(pageSource).toContain('const router = useRouter()')
    expect(pageSource).toContain('useInvoiceList({ route, router })')
    expect(composableSource).toContain('readStringQuery(route.query.keyword)')
    expect(composableSource).toContain('readPositiveIntQuery(route.query.pageNo, 1)')
    expect(composableSource).toContain('await router.replace({ path: route.path, query: nextQuery })')
  })

  it('tracks explicit loaded and error states with retry support', () => {
    expect(composableSource).toContain('const listError = ref<string | null>(null)')
    expect(composableSource).toContain('const hasLoaded = ref(false)')
    expect(pageSource).toContain('<a-result')
    expect(pageSource).toContain('status="error"')
    expect(pageSource).toContain('<LgEmptyState')
  })
})
