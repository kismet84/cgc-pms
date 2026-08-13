import { describe, expect, it } from 'vitest'
import { readFileSync, readdirSync } from 'node:fs'
import { resolve } from 'node:path'
import {
  allPartnerOptions,
  contractOptions,
  linkedPartnerOptions,
} from '@/pages/finance/receivables-workspace/model'

const source = (path: string) => readFileSync(resolve(process.cwd(), path), 'utf8')

const directorySource = (path: string) =>
  readdirSync(resolve(process.cwd(), path), { withFileTypes: true })
    .filter((entry) => entry.isFile() && /\.(ts|vue)$/.test(entry.name))
    .map((entry) => source(`${path}/${entry.name}`))
    .join('\n')

describe('M94 finance route boundaries', () => {
  it('keeps historical contract and partner candidates readable but disabled', () => {
    const contracts = [
      {
        id: 'active-contract',
        contractCode: 'HT-A',
        contractName: '有效合同',
        approvalStatus: 'APPROVED',
        contractStatus: 'PERFORMING',
        contractType: 'MAIN',
      },
      {
        id: 'historical-contract',
        contractCode: 'HT-H',
        contractName: '历史合同',
        approvalStatus: 'APPROVED',
        contractStatus: 'TERMINATED',
        contractType: 'MAIN',
      },
    ] as Parameters<typeof contractOptions>[0]
    const partners = [
      { id: 'expected-partner', partnerCode: 'P-A', partnerName: '有效单位' },
      { id: 'historical-partner', partnerCode: 'P-H', partnerName: '历史单位' },
    ] as Parameters<typeof linkedPartnerOptions>[0]

    expect(contractOptions(contracts, 'revenue', 'historical-contract')).toEqual([
      { value: 'active-contract', label: 'HT-A · 有效合同' },
      { value: 'historical-contract', label: 'HT-H · 历史合同（历史值）', disabled: true },
    ])
    expect(linkedPartnerOptions(partners, 'expected-partner', 'historical-partner').at(-1)).toEqual(
      {
        value: 'historical-partner',
        label: 'P-H · 历史单位（历史值）',
        disabled: true,
      },
    )
    expect(allPartnerOptions(partners, 'missing-partner').at(-1)).toEqual({
      value: 'missing-partner',
      label: '历史收款单位（历史值）',
      disabled: true,
    })
  })

  it('routes receivables responsibilities to four focused pages', () => {
    const router = source('src/router/components.ts')
    const routes = {
      '/payment/application': 'PaymentApplicationPage',
      '/payment/expense': 'ExpenseApplicationPage',
      '/revenue': 'RevenueOperationsPage',
      '/invoice': 'InvoiceManagementPage',
    }

    for (const [path, component] of Object.entries(routes)) {
      expect(router).toContain(`'${path}': ${component}`)
      expect(router).toContain(`const ${component} = () =>`)
    }
    expect(new Set(Object.values(routes)).size).toBe(4)
  })

  it('routes finance controls to six focused pages', () => {
    const router = source('src/router/components.ts')
    const routes = {
      '/finance-operations': 'FinanceOperationsPage',
      '/cash-journal': 'CashJournalPage',
      '/fund-accounts': 'FundAccountsPage',
      '/cash-forecast': 'CashForecastPage',
      '/accounting-entry': 'AccountingEntryPage',
      '/financial-close': 'FinancialClosePage',
    }

    for (const [path, component] of Object.entries(routes)) {
      expect(router).toContain(`'${path}': ${component}`)
      expect(router).toContain(`const ${component} = () =>`)
    }
    expect(new Set(Object.values(routes)).size).toBe(6)
  })

  it('keeps compatibility entry points free of finance service state', () => {
    const receivables = source('src/pages/finance/ReceivablesWorkspacePage.vue')
    const control = source('src/pages/finance/FinanceControlWorkspacePage.vue')

    expect(receivables).not.toContain("from '@/services/finance'")
    expect(control).not.toContain("from '@/services/finance'")
    expect(receivables).toContain('PaymentApplicationPage')
    expect(control).toContain('FinanceOperationsPage')
  })

  it('preserves payment source, rollback, writeback and reversal contracts', () => {
    const page = source('src/pages/finance/receivables-workspace/PaymentApplicationPage.vue')

    expect(page).toContain("sourceType !== 'MAT_RECEIPT'")
    expect(page).toContain("sourceType === 'DIRECT' ? paymentId")
    expect(page).toContain('await savePaymentSources(paymentId')
    expect(page).toContain('await deletePayment(createdPaymentId)')
    expect(page).toContain('await writebackPayment(command)')
    expect(page).toContain('await reversePaymentRecord(target.id')
    expect(page).not.toMatch(/Number\((?:value\.)?(?:applyAmount|payAmount)/)
  })

  it('preserves evidence-before-confirm and server reread ordering', () => {
    const revenue = source('src/pages/finance/receivables-workspace/RevenueOperationsPage.vue')
    const salesUpload = revenue.indexOf("'SALES_INVOICE',")
    const salesConfirm = revenue.indexOf('await confirmSalesInvoice(')
    const collectionUpload = revenue.indexOf("'COLLECTION_RECORD',")
    const collectionConfirm = revenue.indexOf('await confirmCollection(')

    expect(salesUpload).toBeGreaterThan(0)
    expect(salesConfirm).toBeGreaterThan(salesUpload)
    expect(collectionUpload).toBeGreaterThan(0)
    expect(collectionConfirm).toBeGreaterThan(collectionUpload)
    expect(revenue).toContain('await load()')
  })

  it('keeps finance amount inputs as strings and every focused page server-authoritative', () => {
    const receivables = directorySource('src/pages/finance/receivables-workspace')
    const control = directorySource('src/pages/finance/finance-control-workspace')

    expect(receivables).not.toMatch(
      /Number\((?:value\.)?(?:applyAmount|amount|invoiceAmount|taxAmount|grossAmount|collectionAmount|allocationAmount)/,
    )
    expect(control).not.toMatch(/Number\((?:value\.)?(?:openingBalance|amount)/)
    for (const action of [
      'archiveCashJournal',
      'reverseCashJournal',
      'reviewAccountingEntry',
      'postAccountingEntry',
      'reverseAccountingEntry',
      'submitCashForecast',
      'closeFinancePeriod',
      'reopenFinancePeriod',
    ]) {
      expect(control).toContain(action)
    }
    expect(control.match(/await load\(\)/g)?.length).toBeGreaterThanOrEqual(6)
  })
})
