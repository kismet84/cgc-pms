import { describe, expect, it } from 'vitest'
import { readFileSync } from 'node:fs'
import { dirname, resolve } from 'node:path'
import { fileURLToPath } from 'node:url'

const currentDir = dirname(fileURLToPath(import.meta.url))
const specPaths = [
  '../../../e2e/approval.spec.ts',
  '../../../e2e/contract.spec.ts',
  '../../../e2e/payment-invoice.spec.ts',
]

describe('legacy e2e auth bootstrap cleanup', () => {
  it.each(specPaths)('%s no longer writes sessionStorage login shortcuts', (relativePath) => {
    const source = readFileSync(resolve(currentDir, relativePath), 'utf-8')
    expect(source).not.toContain("sessionStorage.setItem('cgc_pms_userinfo'")
  })
})
