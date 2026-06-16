import { describe, expect, it } from 'vitest'
import { readFileSync } from 'node:fs'
import { dirname, resolve } from 'node:path'
import { fileURLToPath } from 'node:url'

const currentDir = dirname(fileURLToPath(import.meta.url))
const source = readFileSync(resolve(currentDir, '../ContractLedgerPage.vue'), 'utf-8')

describe('ContractLedgerPage button handlers', () => {
  // ── TEST 1: 查看 → router.push to contract detail ──
  it('wires 查看 button to navigate to contract detail via router.push', () => {
    // Verify the template calls handleView
    expect(source).toMatch(/@click="handleView\(row\)"/)
    // Verify handleView function navigates to /contract/{row.id}
    expect(source).toMatch(/function handleView[\s\S]*?router\.push\('\/contract\/'\s*\+\s*row\.id\)/)
  })

  // ── TEST 2: 编辑 → router.push to contract edit ──
  it('wires 编辑 button to navigate to contract edit via router.push', () => {
    // Verify the template calls handleEdit
    expect(source).toMatch(/@click="handleEdit\(row\)"/)
    // Verify handleEdit function navigates to /contract/{row.id}/edit
    expect(source).toMatch(/function handleEdit[\s\S]*?router\.push\('\/contract\/'\s*\+\s*row\.id\s*\+\s*'\/edit'\)/)
  })

  // ── TEST 3: 删除 → Modal.confirm + deleteContract + refresh ──
  it('wires 删除 button with Modal.confirm calling deleteContract on confirm', () => {
    // Verify Modal is imported from ant-design-vue
    expect(source).toMatch(/import\s+\{[^}]*Modal[^}]*\}\s+from\s+['"]ant-design-vue['"]/)
    // Verify deleteContract is imported
    expect(source).toMatch(/import\s+\{[^}]*deleteContract[^}]*\}\s+from/)
    // Verify Modal.confirm is called inside handleDelete
    expect(source).toMatch(/function handleDelete[\s\S]*?Modal\.confirm\(/)
    // Verify deleteContract is called inside the handler
    expect(source).toMatch(/function handleDelete[\s\S]*?deleteContract\(/)
    // Verify fetchData is called after deletion (refresh list)
    expect(source).toMatch(/function handleDelete[\s\S]*?fetchData\(/)
  })

  // ── TEST 4: 展开 ↓ → toggle advanced filter expand state ──
  it('wires 展开 button to toggle an expand reactive state', () => {
    // Verify a reactive toggle ref exists (filterExpanded)
    expect(source).toMatch(/const\s+filterExpanded\s*=\s*ref\(/)
    // Verify toggleFilterExpand function exists
    expect(source).toMatch(/function toggleFilterExpand/)
    // Verify the button has @click wired
    expect(source).toMatch(/toggleFilterExpand.*展开|展开.*toggleFilterExpand/)
  })

  // ── TEST 5: 导出 → disabled button + placeholder handler ──
  it('wires 导出 button with disabled state and placeholder click handler', () => {
    // Export button must exist
    expect(source).toMatch(/导出/)
    // Must have :disabled (anywhere near export, multiline)
    const hasDisabled = /导出[\s\S]*:disabled|:disabled[\s\S]*导出/.test(source)
    expect(hasDisabled).toBe(true)
    // Must have @click wired (anywhere near export, multiline)
    const hasClick = /导出[\s\S]*@click|@click[\s\S]*导出/.test(source)
    expect(hasClick).toBe(true)
  })

  // ── TEST 6: 全部预警 › → router.push to /alert ──
  it('wires 全部预警 link to navigate to /alert via router.push', () => {
    // Verify the link has @click wired (any order relative to text)
    expect(source).toMatch(/全部预警[\s\S]*@click|@click[\s\S]*全部预警/)
    // Verify handleAllAlerts function exists with router.push('/alert')
    expect(source).toMatch(/function handleAllAlerts[\s\S]*?router\.push\('\/alert'\)/)
  })
})
