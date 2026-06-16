import { describe, expect, it } from 'vitest'
import { readFileSync } from 'node:fs'
import { dirname, resolve } from 'node:path'
import { fileURLToPath } from 'node:url'

const currentDir = dirname(fileURLToPath(import.meta.url))
const source = readFileSync(resolve(currentDir, '../detail.vue'), 'utf-8')

describe('Approval confirm dialogs', () => {
  // ── TEST 1: 撤回确认 → Modal.confirm + withdrawInstance on confirm ──
  it('wires withdraw with Modal.confirm calling withdrawInstance on confirm', () => {
    // Verify Modal is imported from ant-design-vue
    expect(source).toMatch(/import\s+\{[^}]*Modal[^}]*\}\s+from\s+['"]ant-design-vue['"]/)
    // Verify withdrawInstance is imported
    expect(source).toMatch(/import\s+\{[^}]*withdrawInstance[^}]*\}\s+from/)
    // Verify Modal.confirm is called inside handleWithdraw
    expect(source).toMatch(/function handleWithdraw[\s\S]*?Modal\.confirm\(/)
    // Verify withdrawInstance is called inside the handler (inside onOk)
    expect(source).toMatch(/function handleWithdraw[\s\S]*?withdrawInstance\(/)
    // Verify fetchDetail is called after withdraw (refresh page)
    expect(source).toMatch(/function handleWithdraw[\s\S]*?fetchDetail\(/)
  })

  // ── TEST 2: 撤回取消 → cancelText present, API not called on cancel ──
  it('shows cancel button on withdraw dialog without calling API on cancel', () => {
    // Verify cancel text is present in the withdraw Modal.confirm block
    expect(source).toMatch(/function handleWithdraw[\s\S]*?cancelText:\s*['"]取消['"]/)
    // Verify the withdrawInstance call is inside onOk, not before Modal.confirm
    const handleWithdrawBlock = source.match(/function handleWithdraw[\s\S]*?\n\}/)?.[0] ?? ''
    const modalCallIndex = handleWithdrawBlock.indexOf('Modal.confirm(')
    const withdrawCallIndex = handleWithdrawBlock.indexOf('withdrawInstance(')
    expect(modalCallIndex).toBeGreaterThan(-1)
    expect(withdrawCallIndex).toBeGreaterThan(-1)
    expect(withdrawCallIndex).toBeGreaterThan(modalCallIndex)
  })

  // ── TEST 3: 重新提交确认 → Modal.confirm + resubmitInstance on confirm ──
  it('wires resubmit with Modal.confirm calling resubmitInstance on confirm', () => {
    // Verify resubmitInstance is imported
    expect(source).toMatch(/import\s+\{[^}]*resubmitInstance[^}]*\}\s+from/)
    // Verify Modal.confirm is called inside handleResubmit
    expect(source).toMatch(/function handleResubmit[\s\S]*?Modal\.confirm\(/)
    // Verify resubmitInstance is called inside the handler (inside onOk)
    expect(source).toMatch(/function handleResubmit[\s\S]*?resubmitInstance\(/)
    // Verify fetchDetail is called after resubmit (refresh page)
    expect(source).toMatch(/function handleResubmit[\s\S]*?fetchDetail\(/)
  })

  // ── TEST 4: 重新提交取消 → cancelText present, API not called on cancel ──
  it('shows cancel button on resubmit dialog without calling API on cancel', () => {
    // Verify cancel text is present in the resubmit Modal.confirm block
    expect(source).toMatch(/function handleResubmit[\s\S]*?cancelText:\s*['"]取消['"]/)
    // Verify the resubmitInstance call is inside onOk, not before Modal.confirm
    const handleResubmitBlock = source.match(/function handleResubmit[\s\S]*?\n\}/)?.[0] ?? ''
    const modalCallIndex = handleResubmitBlock.indexOf('Modal.confirm(')
    const resubmitCallIndex = handleResubmitBlock.indexOf('resubmitInstance(')
    expect(modalCallIndex).toBeGreaterThan(-1)
    expect(resubmitCallIndex).toBeGreaterThan(-1)
    expect(resubmitCallIndex).toBeGreaterThan(modalCallIndex)
  })
})
