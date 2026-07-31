import { describe, expect, it } from 'vitest'
import { readFileSync } from 'node:fs'
import { dirname, resolve } from 'node:path'
import { fileURLToPath } from 'node:url'

const currentDir = dirname(fileURLToPath(import.meta.url))
const source = readFileSync(resolve(currentDir, '../detail.vue'), 'utf-8')
const helperSource = readFileSync(resolve(currentDir, '../workflowDisplay.ts'), 'utf-8')

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

  it('shows business document entry for supported workflow business types', () => {
    expect(helperSource).toContain('export const workflowBusinessEntryRegistry')
    expect(helperSource).toContain("businessType: 'CONTRACT_APPROVAL'")
    expect(helperSource).toContain("displayName: '合同审批'")
    expect(helperSource).toContain("permissionCode: 'contract:query'")
    expect(helperSource).toContain("businessType: 'PURCHASE_REQUEST'")
    expect(helperSource).toContain("displayName: '采购申请'")
    expect(helperSource).toContain("permissionCode: 'purchase:request:list'")
    expect(helperSource).toContain("businessType: 'SUB_MEASURE'")
    expect(helperSource).toContain("displayName: '分包计量'")
    expect(helperSource).toContain("permissionCode: 'subcontract:measure:query'")
    expect(helperSource).toContain("openMode: 'route'")
    expect(helperSource).toContain("forbiddenPolicy: 'disabled-with-tooltip'")
    expect(helperSource).toContain('targetRoute:')
    expect(helperSource).toContain('export function getWorkflowBusinessEntry')
    expect(source).toContain('function businessEntryPath')
    expect(source).toContain('return getWorkflowBusinessEntryPath(record)')
    expect(source).toContain('useUserStore')
    expect(source).toContain('const userStore = useUserStore()')
    expect(source).toContain('function canOpenBusinessEntry')
    expect(source).toContain(
      'return canAccessWorkflowBusinessEntry(record, userStore.hasPermission, userStore.roles)',
    )
    expect(helperSource).toContain('targetRoute: (businessId: string) => `/contract/${businessId}`')
    expect(helperSource).toContain(
      'targetRoute: (businessId: string) => `/inventory/purchase-request?businessId=${businessId}`',
    )
    expect(helperSource).toContain(
      'targetRoute: (businessId: string) => `/subcontract/measure?businessId=${businessId}`',
    )
    expect(helperSource).toContain('export function getWorkflowBusinessEntryPermission')
    expect(helperSource).toContain('export function canAccessWorkflowBusinessEntry')
    expect(source).toContain('function openBusinessEntry')
    expect(source).toMatch(
      /function openBusinessEntry[\s\S]*?if \(!canOpenBusinessEntry\(record\)\) return/,
    )
    expect(source).toContain('router.push(path)')
    expect(source).toContain('v-if="businessEntryPath(detail)"')
    expect(source).toContain('无权访问该业务单据')
    expect(source).toContain(':disabled="!canOpenBusinessEntry(detail)"')
    expect(source).toContain('查看业务单据')
    expect(source).toContain('getWorkflowBusinessTypeLabel(detail.businessType)')
    expect(helperSource).toContain("return workflowBusinessTypeLabels[key] ?? '未知业务类型'")
  })
})
