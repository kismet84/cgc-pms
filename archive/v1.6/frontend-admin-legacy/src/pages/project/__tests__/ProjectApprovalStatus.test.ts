import { readFileSync } from 'node:fs'
import { dirname, resolve } from 'node:path'
import { fileURLToPath } from 'node:url'

const currentDir = dirname(fileURLToPath(import.meta.url))
const source = readFileSync(resolve(currentDir, '../index.vue'), 'utf-8')

describe('project approval status authority', () => {
  it('renders approval status from the authoritative dictionary', () => {
    expect(source).toContain("const APPROVAL_STATUS_DICT = 'approval_status'")
    expect(source).toContain('fetchDictData(APPROVAL_STATUS_DICT)')
    expect(source).toContain(':approval-status-label="approvalStatusLabelMap"')
    expect(source).toContain(':approval-status-color="approvalStatusColorMap"')
    expect(source).not.toContain("PENDING: '审批中'")
    expect(source).not.toContain('const APPROVAL_STATUS_LABEL')
  })
})
