import { describe, expect, it } from 'vitest'
import { readFileSync } from 'node:fs'
import { dirname, resolve } from 'node:path'
import { fileURLToPath } from 'node:url'

const currentDir = dirname(fileURLToPath(import.meta.url))
const source = readFileSync(resolve(currentDir, '../order.vue'), 'utf-8')

describe('VariationOrderPage production guards', () => {
  it('uses pageNo instead of legacy pageNum in list request', () => {
    expect(source).toMatch(/getVarOrderList\(\{\s*[\s\S]*pageNo:\s*pageNo\.value/)
    expect(source).not.toMatch(/getVarOrderList\(\{\s*[\s\S]*pageNum:/)
  })

  it('uses APPROVING instead of PENDING for approval status labels', () => {
    expect(source).toMatch(/APPROVAL_STATUS_LABEL[\s\S]*APPROVING:\s*'审批中'/)
    expect(source).toMatch(/approvalStatusSummary[\s\S]*APPROVING:\s*'审批中'/)
    expect(source).not.toMatch(/APPROVAL_STATUS_LABEL[\s\S]*PENDING:\s*'审批中'/)
  })

  it('formats wan amount by dividing by 10000', () => {
    expect(source).toMatch(/function fmtWan[\s\S]*\/\s*10000/)
  })

  it('renders dedicated mobile list guarded by isMobile', () => {
    expect(source).toMatch(/class="vo-mobile-list"/)
    expect(source).toMatch(/v-if="isMobile"/)
    expect(source).toMatch(/class="vo-mobile-card"/)
    expect(source).toMatch(/v-else class="lg-table-wrap vo-table-wrap"/)
  })

  it('shows column settings button only on desktop', () => {
    expect(source).toMatch(/ColumnSettingsButton[\s\S]*v-if="!isMobile"/)
  })

  it('uses loading empty content tri-state in mobile branch', () => {
    expect(source).toMatch(/v-if="loading"/)
    expect(source).toMatch(/v-else-if="!tableData\.length"/)
    expect(source).toMatch(/<template v-else>/)
  })
})
