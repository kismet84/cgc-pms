import { readFileSync } from 'node:fs'
import { dirname, resolve } from 'node:path'
import { fileURLToPath } from 'node:url'
import { describe, expect, it } from 'vitest'

import { scanContent } from '../../../../scripts/check-ui-style-consistency.mjs'

const currentDir = dirname(fileURLToPath(import.meta.url))
const sourcePath = 'src/pages/contract/ContractLedgerPage.vue'
const source = readFileSync(resolve(currentDir, '../ContractLedgerPage.vue'), 'utf-8')
const composableSource = readFileSync(
  resolve(currentDir, '../composables/useContractLedger.ts'),
  'utf-8',
)

describe('ContractLedgerPage UI consistency', () => {
  it('uses tokenized classes for the redesigned ledger workspace', () => {
    const findings = scanContent(sourcePath, source)

    expect(findings).toEqual([])
    expect(source).toContain('class="lg-list-page lg-page app-page cl-redesign-page"')
    expect(source).toContain('class="lg-page-head cl-page-head"')
    expect(source).toContain('class="cl-page-meta-row"')
    expect(source).toContain('gap: 5em')
    expect(source).toContain('min-height: 0')
    expect(source).not.toContain('class="cl-page-title"')
    expect(source).not.toContain('<h1')
    expect(source).toContain('class="lg-search-bar cl-query-panel"')
    expect(source).toContain('class="cl-keyword-search"')
    expect(source).toContain('class="cl-search-prefix-icon"')
    expect(source).toContain('class="lg-grid cl-workspace"')
    expect(source).toContain('class="lg-list-table-panel cl-table-panel"')
    expect(source).toContain('class="lg-table-wrap cl-table-wrap"')
    expect(source).toContain('.cl-table-wrap :deep(.vxe-header--column .vxe-cell)')
    expect(source).toContain('text-align: center')
    expect(source).toContain('class="cl-contract-link"')
    expect(source).toContain('@click="handleView(row)"')
    expect(source).toContain('class="cl-row-actions"')
    expect(source).toContain('ColumnSettingsButton')
    expect(source).toContain(':columns="columnSettings"')
    expect(source).toContain(':visible="colVisible"')
    expect(source).toContain('@toggle="toggleCol"')
    expect(source).toContain(':columns="visibleColumns"')
    expect(composableSource).toContain("title: '甲方'")
    expect(composableSource).toContain("buildDateColumn('signedDate', '签订日期'")
    expect(composableSource).toContain('partyAName: false')
    expect(composableSource).toContain('signedDate: false')
    expect(source).toContain('color: var(--text-secondary)')
    expect(source).toContain('aria-label="刷新合同台账"')
    expect(source).toContain(':aria-label="`打开合同操作菜单：${row.contractCode}`"')
    expect(source).not.toContain('type="link" size="small" @click="handleView(row)"')
  })
})
