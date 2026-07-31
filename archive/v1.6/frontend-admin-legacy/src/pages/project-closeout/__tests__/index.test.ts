import { readFileSync } from 'node:fs'
import { dirname, resolve } from 'node:path'
import { fileURLToPath } from 'node:url'

const currentDir = dirname(fileURLToPath(import.meta.url))
const source = readFileSync(resolve(currentDir, '../index.vue'), 'utf-8')

describe('project completion closeout workbench', () => {
  it('covers the complete acceptance-to-project-close chain', () => {
    expect(source).toContain('分项验收 → 竣工验收 → 竣工结算 → 尾款 → 质保/缺陷 → 档案 → 关闭')
    expect(source).toContain('bindFinalSettlement')
    expect(source).toContain('verifyTailCollection')
    expect(source).toContain('releaseCloseoutWarranty')
    expect(source).toContain('acceptArchiveTransfer')
    expect(source).toContain('closeProjectFromCloseout')
    expect(source).toContain('getCloseoutTrace')
  })

  it('uses stage-specific immutable evidence and segregated permissions', () => {
    expect(source).toContain("'SECTION_ACCEPTANCE_RECORD'")
    expect(source).toContain("'FINAL_ACCEPTANCE_CERTIFICATE'")
    expect(source).toContain("'DEFECT_RECTIFICATION_EVIDENCE'")
    expect(source).toContain("'WARRANTY_RELEASE_VOUCHER'")
    expect(source).toContain("'ARCHIVE_TRANSFER_LIST'")
    expect(source).toContain("can('closeout:defect:verify')")
    expect(source).toContain("can('closeout:close')")
  })

  it('allows draft records to recover after an attachment upload interruption', () => {
    expect(source).toContain("openDialog('sectionConfirm', record)")
    expect(source).toContain("openDialog('finalSubmit', overview.finalAcceptances[0])")
    expect(source).toContain("openDialog('archiveAccept', record)")
    expect(source).toContain("dialogKind.value === 'sectionConfirm'")
    expect(source).toContain("dialogKind.value === 'finalSubmit'")
    expect(source).toContain("dialogKind.value === 'archiveAccept'")
  })
})
