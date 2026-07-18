import { describe, expect, it } from 'vitest'
import { readFileSync } from 'node:fs'
import { dirname, resolve } from 'node:path'
import { fileURLToPath } from 'node:url'

const currentDir = dirname(fileURLToPath(import.meta.url))
const source = readFileSync(resolve(currentDir, '../detail.vue'), 'utf-8')

describe('settlement document generation entry', () => {
  it('keeps document actions behind action permissions', () => {
    expect(source).toContain("userStore.hasPermission('document:generate')")
    expect(source).toContain("userStore.hasPermission('document:history:query')")
    expect(source).toContain('canGenerateDocument')
    expect(source).toContain('canViewDocumentHistory')
  })

  it('only offers formal generation for approved finalized settlements', () => {
    expect(source).toContain("detail.approvalStatus === 'APPROVED'")
    expect(source).toContain("detail.settlementStatus === 'FINALIZED'")
    expect(source).toContain("previewBusinessDocument('SETTLEMENT', settlementId)")
    expect(source).toContain("generateBusinessDocument(\n      'SETTLEMENT',")
  })

  it('uses finalized baseline marker for idempotency and exposes history download', () => {
    expect(source).toContain('settlement:${settlementId}:finalized:${marker}')
    expect(source).toContain("getBusinessDocumentHistory('SETTLEMENT', settlementId)")
    expect(source).toContain('downloadGeneratedDocument(record)')
  })
})
