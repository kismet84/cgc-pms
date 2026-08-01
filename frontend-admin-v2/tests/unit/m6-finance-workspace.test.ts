import { describe, expect, it, vi } from 'vitest'
import { readFileSync } from 'node:fs'
import { resolve } from 'node:path'
import { FINANCE_DECIMAL_FIELDS, FINANCE_API } from '@cgc-pms/frontend-contracts'
import {
  createFundAccount,
  loadCashForecastCycles,
  loadFinanceOperationsWorkspace,
  loadPaymentApplications,
  reversePaymentRecord,
  savePaymentBasis,
  writebackPayment,
} from '@/services/finance'

describe('M6 finance workspace contract', () => {
  it('keeps finance endpoints and decimal fields stable', () => {
    expect(FINANCE_API.revenueSettlements).toBe('/revenue-operations/settlements')
    expect(FINANCE_DECIMAL_FIELDS.payment).toContain('applyAmount')
    expect(FINANCE_DECIMAL_FIELDS.invoice).toContain('invoiceAmount')
  })
  it('sends project filter without converting money', async () => {
    const fetchMock = vi.fn(
      async () =>
        new Response(
          JSON.stringify({
            code: '0',
            data: { records: [{ applyAmount: '9007199254740993.01' }], total: 1 },
          }),
        ),
    )
    vi.stubGlobal('fetch', fetchMock)
    const result = await loadPaymentApplications({ projectId: 'P/1' })
    expect(String(fetchMock.mock.calls[0]?.[0])).toContain('projectId=P%2F1')
    expect(result.records[0]?.applyAmount).toBe('9007199254740993.01')
    vi.unstubAllGlobals()
  })
  it('loads enterprise finance workspaces without forcing a project query', async () => {
    const fetchMock = vi.fn(async () => new Response(JSON.stringify({ code: '0', data: [] })))
    vi.stubGlobal('fetch', fetchMock)
    await loadFinanceOperationsWorkspace()
    await loadCashForecastCycles()
    expect(String(fetchMock.mock.calls[0]?.[0])).toMatch(/\/finance-operations\/workspace$/)
    expect(String(fetchMock.mock.calls[1]?.[0])).toMatch(/\/cash-forecasts\/workspace$/)
    vi.unstubAllGlobals()
  })
  it('keeps enterprise reads separate from project detail writes', () => {
    const source = readFileSync(
      resolve(process.cwd(), 'src/pages/finance/FinanceControlWorkspacePage.vue'),
      'utf8',
    )
    expect(source).toContain('企业资金概览')
    expect(source).toContain('项目资金对比')
    expect(source).toMatch(/loadFinanceOperationsWorkspace\(\s*projectId\.value \|\| undefined,/)
    expect(source).toMatch(/loadCashForecastCycles\(\s*projectId\.value \|\| undefined,/)
    expect(source).toContain('if (!projectRequired()) return')
    expect(source).toContain(
      "mode === 'operations' && projectId && can('finance:analytics:maintain')",
    )
    expect(source).not.toContain('资金运营和预测必须按单项目范围读取')
  })
  it('keeps abort state scoped to each finance request', () => {
    const source = readFileSync(
      resolve(process.cwd(), 'src/pages/finance/ReceivablesWorkspacePage.vue'),
      'utf8',
    )
    expect(source).toContain('const request = new AbortController()')
    expect(source).toContain('if (!request.signal.aborted)')
    expect(source).not.toContain('if (!controller.signal.aborted)')
    expect(source).toContain('dashboardStatusLabel(')
  })
  it('keeps payment writeback server-authoritative and string-valued', async () => {
    const fetchMock = vi.fn(
      async () =>
        new Response(
          JSON.stringify({
            code: '0',
            data: { id: 'PR-1', payAmount: '9007199254740993.01' },
          }),
        ),
    )
    vi.stubGlobal('fetch', fetchMock)
    await writebackPayment({
      payApplicationId: 'PA-1',
      payAmount: '9007199254740993.01',
      paidAt: '2026-07-28T15:30',
      fundAccountId: 'FA-1',
      payMethod: 'BANK_TRANSFER',
      externalTxnNo: 'BANK-20260728-001',
    })
    const request = fetchMock.mock.calls[0]?.[1] as RequestInit
    expect(JSON.parse(String(request.body))).toMatchObject({
      payApplicationId: 'PA-1',
      payAmount: '9007199254740993.01',
      fundAccountId: 'FA-1',
    })
    vi.unstubAllGlobals()
  })
  it('saves material receipt basis without converting money', async () => {
    const fetchMock = vi.fn(async () => new Response(JSON.stringify({ code: '0', data: null })))
    vi.stubGlobal('fetch', fetchMock)
    await savePaymentBasis('PA-1', [
      {
        basisType: 'MAT_RECEIPT',
        basisId: 'RI-1',
        basisAmount: '9007199254740993.01',
      },
    ])
    expect(String(fetchMock.mock.calls[0]?.[0])).toContain('/pay-applications/PA-1/basis/batch')
    const request = fetchMock.mock.calls[0]?.[1] as RequestInit
    expect(request.method).toBe('POST')
    expect(JSON.parse(String(request.body))).toEqual([
      {
        basisType: 'MAT_RECEIPT',
        basisId: 'RI-1',
        basisAmount: '9007199254740993.01',
      },
    ])
    vi.unstubAllGlobals()
  })
  it('binds authorized direct payment to itself and an approved receipt item', () => {
    const source = readFileSync(
      resolve(process.cwd(), 'src/pages/finance/ReceivablesWorkspacePage.vue'),
      'utf8',
    )
    expect(source).toContain("session.hasPermission('payment:direct')")
    expect(source).toContain("value: 'DIRECT:self'")
    expect(source).toContain("sourceType === 'DIRECT' ? paymentId")
    expect(source).toContain("basisType: 'MAT_RECEIPT'")
    expect(source).toContain('await savePaymentSources(paymentId')
    expect(source).toContain('await savePaymentBasis(paymentId')
    expect(source).toContain('loadReceipts({ pageNum: 1, pageSize: 200')
    expect(source).toContain('await loadReceiptItems(receipt.id)')
    expect(source).toContain("receipt.approvalStatus === 'APPROVED'")
    expect(source).toContain("receipt.qualityStatus === 'QUALIFIED'")
    expect(source).toContain("editor.sourceType === 'DIRECT'")
    expect(source).not.toContain('Number(value.applyAmount)')
  })
  it('exposes writeback only through the authoritative payment endpoint', () => {
    const source = readFileSync(
      resolve(process.cwd(), 'src/pages/finance/ReceivablesWorkspacePage.vue'),
      'utf8',
    )
    expect(source).toContain("session.hasPermission('payment:record:writeback')")
    expect(source).toContain('await writebackPayment(command)')
    expect(source).toContain(".replace('T', ' ')}:00")
    expect(source).not.toContain('Number(value.payAmount)')
    expect(source).toContain("value == null ? '' : String(value).trim()")
    expect(source).toContain(
      '{ payRecordId: command.payRecordId, allocatedAmount: command.invoiceAmount }',
    )
    expect(source).toContain("'INVOICE',")
    expect(source).toContain("'ELECTRONIC_INVOICE'")
  })
  it('binds sales invoices to receivables and uploaded evidence', () => {
    const source = readFileSync(
      resolve(process.cwd(), 'src/pages/finance/ReceivablesWorkspacePage.vue'),
      'utf8',
    )
    expect(source).toContain("receivableId: required(value.receivableId, '应收款')")
    expect(source).toContain("amount: required(value.allocationAmount, '分配金额')")
    expect(source).toContain("throw new TypeError('销项发票附件不能为空')")
    expect(source).toContain("'SALES_INVOICE',")
    expect(source).toContain("'ELECTRONIC_INVOICE'")
    expect(source).not.toContain('allocations: []')
  })
  it('uploads required expense evidence before submission', () => {
    const source = readFileSync(
      resolve(process.cwd(), 'src/pages/finance/ReceivablesWorkspacePage.vue'),
      'utf8',
    )
    expect(source).toContain("throw new TypeError('费用附件不能为空')")
    expect(source).toContain(
      "await uploadSiteFile(expenseAttachment.value, 'EXPENSE', expenseId, 'OTHER')",
    )
    expect(source).toContain('@change="onExpenseAttachment"')
  })
  it('retains created draft ids before fallible follow-up writes', () => {
    const source = readFileSync(
      resolve(process.cwd(), 'src/pages/finance/ReceivablesWorkspacePage.vue'),
      'utf8',
    )
    expect(source).toContain('value.id = paymentId')
    expect(source).toContain('value.id = expenseId')
    expect(source).toContain('session.hasAdminOrPermission(')
  })
  it('binds collections to receivables, fund accounts, and bank evidence', () => {
    const source = readFileSync(
      resolve(process.cwd(), 'src/pages/finance/ReceivablesWorkspacePage.vue'),
      'utf8',
    )
    expect(source).toContain("fundAccountId: required(value.fundAccountId, '资金账户')")
    expect(source).toContain("throw new TypeError('银行回单不能为空')")
    expect(source).toContain('await createCollection(collectionCommand(value))')
    expect(source).toContain("'COLLECTION_RECORD',")
    expect(source).toContain("'BANK_RECEIPT'")
    expect(source).toContain("openForm('collection')")
  })
  it('reverses payment through the authoritative reversal endpoint', async () => {
    const fetchMock = vi.fn(
      async () =>
        new Response(
          JSON.stringify({
            code: '0',
            data: { id: 'PR-2', payAmount: '-2850000.00' },
          }),
        ),
    )
    vi.stubGlobal('fetch', fetchMock)
    await reversePaymentRecord('PR-1', {
      reversalType: 'REVERSAL',
      externalTxnNo: 'REV-C3-20260730-001',
      reversedAt: '2026-07-30 10:00:00',
      reason: 'C3失败链安全恢复',
    })
    expect(String(fetchMock.mock.calls[0]?.[0])).toContain('/pay-records/PR-1/reverse')
    const request = fetchMock.mock.calls[0]?.[1] as RequestInit
    expect(JSON.parse(String(request.body))).toMatchObject({
      reversalType: 'REVERSAL',
      externalTxnNo: 'REV-C3-20260730-001',
      reason: 'C3失败链安全恢复',
    })
    vi.unstubAllGlobals()
  })
  it('exposes payment reversal only for successful server records and authorized users', () => {
    const source = readFileSync(
      resolve(process.cwd(), 'src/pages/finance/ReceivablesWorkspacePage.vue'),
      'utf8',
    )
    expect(source).toContain("session.hasPermission('payment:record:reverse')")
    expect(source).toContain('Boolean(paymentRecord(row))')
    expect(source).toContain('await reversePaymentRecord(target.id')
    expect(source).toContain('生成反向支付事实，不删除原支付记录。')
  })
  it('creates fund accounts without front-end decimal conversion', async () => {
    const fetchMock = vi.fn(
      async () =>
        new Response(
          JSON.stringify({
            code: '0',
            data: { id: 'FA-1', openingBalance: '9007199254740993.01' },
          }),
        ),
    )
    vi.stubGlobal('fetch', fetchMock)
    await createFundAccount({
      accountCode: 'V15-BANK-001',
      accountName: 'V1.5项目资金专户',
      accountType: 'BANK',
      openingDate: '2026-01-01',
      openingBalance: '9007199254740993.01',
    })
    const request = fetchMock.mock.calls[0]?.[1] as RequestInit
    expect(JSON.parse(String(request.body)).openingBalance).toBe('9007199254740993.01')
    vi.unstubAllGlobals()
  })
  it('keeps first fund-account creation reachable when journal is empty', () => {
    const source = readFileSync(
      resolve(process.cwd(), 'src/pages/finance/FinanceControlWorkspacePage.vue'),
      'utf8',
    )
    expect(source).toContain("!errorMessage && !hasRows && mode !== 'journal'")
    expect(source).toContain('@click="openFundAccount"')
    expect(source).toContain("uploadSiteFile(file, 'CASH_JOURNAL', row.id, 'BANK_RECEIPT')")
    expect(source).toContain("can('file:upload') || can('cashbook:journal:maintain')")
  })
})
