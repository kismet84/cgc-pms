import { readFileSync } from 'node:fs'
import { dirname, resolve } from 'node:path'
import { fileURLToPath } from 'node:url'

const currentDir = dirname(fileURLToPath(import.meta.url))
const source = readFileSync(resolve(currentDir, '../index.vue'), 'utf-8')

describe('drawing RFI technical scheme workbench', () => {
  it('covers the full field-to-archive evidence chain', () => {
    expect(source).toContain('方案 → 图纸 → 会审 → RFI → 改版 → 交底 → 施工 → 验收')
    expect(source).toContain('receiveDrawingVersion')
    expect(source).toContain('reviewTechnicalRfiResponse')
    expect(source).toContain('createConstructionReference')
    expect(source).toContain('confirmAcceptanceArchive')
    expect(source).toContain('getDrawingTrace')
  })

  it('uses stage-specific attachments and segregated permissions', () => {
    expect(source).toContain("'SCHEME_FILE'")
    expect(source).toContain("'DRAWING_FILE'")
    expect(source).toContain("'REVIEW_MINUTES'")
    expect(source).toContain("'RFI_EVIDENCE'")
    expect(source).toContain("'DESIGN_RESPONSE'")
    expect(source).toContain("'DISCLOSURE_RECORD'")
    expect(source).toContain("'ACCEPTANCE_ARCHIVE'")
    expect(source).toContain("can('technical:drawing:review')")
    expect(source).toContain("can('technical:rfi:accept')")
  })
})
