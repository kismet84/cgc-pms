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
})
