import { readFileSync } from 'node:fs'
import { resolve } from 'node:path'
import { describe, expect, it } from 'vitest'

const source = (path: string) => readFileSync(resolve(process.cwd(), path), 'utf8')

describe('business dictionary consumer contract', () => {
  it('loads partner risk levels from the enabled dictionary endpoint', () => {
    const page = source('src/pages/master-data/PartnerPage.vue')

    expect(page).toContain("loadEnabledDictDataByCode('partner_risk_level', signal)")
    expect(page).toContain('riskLevels.value.map')
    expect(page).not.toMatch(/\{ value: '(?:LOW|MEDIUM|HIGH)', label:/)
  })

  it('loads finance editor options dynamically without hardcoded option fallbacks', () => {
    const page = source('src/pages/finance/ReceivablesWorkspacePage.vue')

    for (const code of ['pay_type', 'expense_category', 'invoice_type', 'pay_method']) {
      expect(page).toContain(`loadEnabledDictDataByCode('${code}', signal)`)
    }
    expect(page).toContain("payType: ''")
    expect(page).toContain("expenseCategory: ''")
    expect(page).toContain("invoiceType: ''")
    expect(page).toContain(':options="payMethodOptions"')
    expect(page).not.toMatch(
      /\{ value: '(?:FINAL|PROGRESS|CONTRACT|MATERIAL|LABOR|VAT_SPECIAL|VAT_NORMAL|BANK_TRANSFER)', label:/,
    )
  })

  it('uses settlement_final_status for final settlement state options and labels', () => {
    const page = source('src/pages/settlement/SettlementWorkspacePage.vue')

    expect(page).toContain("loadEnabledDictDataByCode('settlement_final_status', signal)")
    expect(page).toContain('settlementStatuses.value.map')
    expect(page).toContain("loadEnabledDictDataByCode('approval_status', signal)")
    expect(page).toContain('approvalStatuses.value.map')
    expect(page).toContain('settlementStatusLabel(record.settlementStatus)')
    expect(page).toContain('approvalStatusLabel(record.approvalStatus)')
    expect(page).not.toContain("{ value: 'CANCELLED', label:")
    expect(page).not.toMatch(/const settlementStatusOptions = \[/)
    expect(page).not.toMatch(/const approvalStatusOptions = \[/)
  })

  it('loads workflow instance status filter options from wf_instance_status', () => {
    const page = source('src/pages/workbench/WorkflowWorkbenchPage.vue')

    expect(page).toContain("loadEnabledDictDataByCode('wf_instance_status', signal)")
    expect(page).toContain('workflowInstanceStatuses.value.map')
    expect(page).not.toMatch(/const workflowInstanceStatusOptions = \[/)
    expect(page).not.toMatch(/\{ value: '(?:RUNNING|APPROVED|REJECTED|WITHDRAWN|VOIDED)', label:/)
  })
})
