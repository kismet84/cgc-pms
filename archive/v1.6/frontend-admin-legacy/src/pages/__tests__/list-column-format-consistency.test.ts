import { describe, expect, it } from 'vitest'
import { readFileSync } from 'node:fs'
import { dirname, resolve } from 'node:path'
import { fileURLToPath } from 'node:url'

const currentDir = dirname(fileURLToPath(import.meta.url))

function readSource(relativePath: string) {
  return readFileSync(resolve(currentDir, relativePath), 'utf-8')
}

describe('core list column and format consistency', () => {
  it('uses shared list-table presets for amount formatting and column widths', () => {
    const presetSource = readSource('../../composables/listTablePresets.ts')

    expect(presetSource).toContain('LIST_COLUMN_WIDTH')
    expect(presetSource).toContain('formatWanAmount')
    expect(presetSource).toContain('formatCurrencyAmount')
    expect(presetSource).toContain('buildAmountColumn')
    expect(presetSource).toContain('buildActionColumn')
  })

  it('removes per-page amount formatter duplication from core list pages', () => {
    const projectSource = readSource('../project/index.vue')
    const contractSource = readSource('../contract/composables/useContractLedger.ts')
    const receiptSource = readSource('../receipt/composables/useReceiptList.ts')
    const requisitionSource = readSource('../requisition/composables/useRequisitionList.ts')
    const invoiceSource = readSource('../invoice/composables/useInvoiceList.ts')

    expect(projectSource).toContain("from '@/composables/listTablePresets'")
    expect(projectSource).not.toContain('function fmtAmount(')

    expect(contractSource).toContain("from '@/composables/listTablePresets'")
    expect(contractSource).not.toContain('function fmtAmount(')

    expect(receiptSource).toContain("from '@/composables/listTablePresets'")
    expect(receiptSource).not.toContain('export function fmtAmount(')

    expect(requisitionSource).toContain("from '@/composables/listTablePresets'")
    expect(requisitionSource).not.toContain('export function fmtAmount(')

    expect(invoiceSource).toContain("from '@/composables/listTablePresets'")
    expect(invoiceSource).not.toContain('export function fmtAmount(')
  })

  it('standardizes amount/date/status/action columns on representative core lists', () => {
    const projectSource = readSource('../project/index.vue')
    const contractSource = readSource('../contract/composables/useContractLedger.ts')
    const paymentConfigSource = readSource('../payment/pageConfig.ts')
    const settlementConfigSource = readSource('../settlement/pageConfig.ts')

    expect(projectSource).toContain('buildAmountColumn(')
    expect(projectSource).toContain('buildStatusColumn(')
    expect(projectSource).toContain("buildActionColumn('ops'")

    expect(contractSource).toContain('buildAmountColumn(')
    expect(contractSource).toContain('buildDateColumn(')
    expect(contractSource).toContain('buildStatusColumn(')
    expect(contractSource).toContain("buildActionColumn('ops'")

    expect(paymentConfigSource).toContain('buildAmountColumn(')
    expect(paymentConfigSource).toContain('buildStatusColumn(')
    expect(paymentConfigSource).toContain('buildActionColumn(')

    expect(settlementConfigSource).toContain('buildAmountColumn(')
    expect(settlementConfigSource).toContain('buildStatusColumn(')
    expect(settlementConfigSource).toContain('buildDateTimeColumn(')
    expect(settlementConfigSource).toContain("buildActionColumn('ops'")
  })
})
