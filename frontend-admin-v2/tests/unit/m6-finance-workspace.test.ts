import { describe, expect, it, vi } from 'vitest'
import { mount } from '@vue/test-utils'
import { readFileSync } from 'node:fs'
import { resolve } from 'node:path'
import {
  FINANCE_DECIMAL_FIELDS,
  FINANCE_API,
  type PaymentTraceRecord,
} from '@cgc-pms/frontend-contracts'
import PaymentTraceDialog from '@/components/finance/PaymentTraceDialog.vue'
import {
  createFundAccount,
  loadApprovedContractRevenues,
  loadCashForecastCycles,
  loadFinanceOperationsWorkspace,
  loadPaymentApplications,
  loadPaymentTraceByApplication,
  loadPaymentTraceByCashJournal,
  loadPaymentTraceByInvoice,
  loadPaymentTraceByVoucher,
  reversePaymentRecord,
  writebackPayment,
} from '@/services/finance'

describe('M6 finance workspace contract', () => {
  it('keeps finance endpoints and decimal fields stable', () => {
    expect(FINANCE_API.revenueSettlements).toBe('/revenue-operations/settlements')
    expect(FINANCE_API.contractRevenues).toBe('/revenue-operations/settlement-revenue-options')
    expect(FINANCE_DECIMAL_FIELDS.payment).toContain('applyAmount')
    expect(FINANCE_DECIMAL_FIELDS.invoice).toContain('invoiceAmount')
  })
  it('loads only approved contract revenue candidates in project and contract scope', async () => {
    const fetchMock = vi.fn(
      async () => new Response(JSON.stringify({ code: '0', data: { records: [], total: 0 } })),
    )
    vi.stubGlobal('fetch', fetchMock)
    await loadApprovedContractRevenues('P/1', 'C/1')
    expect(String(fetchMock.mock.calls[0]?.[0])).toContain(
      '/api/revenue-operations/settlement-revenue-options?projectId=P%2F1&contractId=C%2F1',
    )
    vi.unstubAllGlobals()
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
    expect(source).toContain("['PAID', 'PARTIALLY_PAID'].includes(row.payStatus || '')")
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
  it('binds material payment to an authoritative receipt source', () => {
    const source = readFileSync(
      resolve(process.cwd(), 'src/pages/finance/ReceivablesWorkspacePage.vue'),
      'utf8',
    )
    expect(source).toContain("sourceType !== 'MAT_RECEIPT'")
    expect(source).toContain('材料付款必须选择材料验收来源')
    expect(source).toContain("sourceType === 'DIRECT' ? paymentId")
    expect(source).toContain('await savePaymentSources(paymentId')
    expect(source).toContain('await deletePayment(createdPaymentId)')
    expect(source).toContain('本次新建草稿已回滚')
    expect(source).not.toContain('await savePaymentBasis(paymentId')
    expect(source).not.toContain(
      "editor.sourceType === 'DIRECT' && editor.expenseCategory === 'MATERIAL'",
    )
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
  it('binds owner settlements to an approved contract revenue fact', () => {
    const source = readFileSync(
      resolve(process.cwd(), 'src/pages/finance/ReceivablesWorkspacePage.vue'),
      'utf8',
    )
    expect(source).toContain("revenueId: required(value.revenueId, '已审批收入确认')")
    expect(source).toContain('await loadApprovedContractRevenues(editor.value.projectId, value)')
    expect(source).toContain("item.approvalStatus === 'APPROVED'")
    expect(source).toContain('label="已审批收入确认"')
  })
  it('filters finance editor candidates by active project and authoritative contract relations', () => {
    const source = readFileSync(
      resolve(process.cwd(), 'src/pages/finance/ReceivablesWorkspacePage.vue'),
      'utf8',
    )
    expect(source).toContain("item.status === 'ACTIVE'")
    expect(source).toContain("item.approvalStatus === 'APPROVED'")
    expect(source).toContain("item.contractStatus === 'PERFORMING'")
    expect(source).toContain("performing && item.contractType === 'MAIN'")
    expect(source).toContain('selectedContract.value?.partyBId')
    expect(source).toContain('selectedContract.value?.partyAId')
    expect(source).toContain(':options="payeePartnerOptions"')
    expect(source).not.toContain(
      "if (editorKind.value === 'expense') editor.value.payeePartnerId = contract?.partyBId",
    )
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
    expect(source).toContain(
      'const collectionId = value.id || (await createCollection(command)).id',
    )
    expect(source).toContain("'COLLECTION_RECORD',")
    expect(source).toContain("'BANK_RECEIPT'")
    expect(source).toContain('await confirmCollection(collectionId, command.allocations ?? [])')
    expect(source).toContain("openForm('collection')")
  })
  it('confirms sales invoices only after evidence upload', () => {
    const source = readFileSync(
      resolve(process.cwd(), 'src/pages/finance/ReceivablesWorkspacePage.vue'),
      'utf8',
    )
    const upload = source.indexOf("'SALES_INVOICE',")
    const confirm = source.indexOf('await confirmSalesInvoice(salesInvoiceId, command.allocations)')
    expect(upload).toBeGreaterThan(0)
    expect(confirm).toBeGreaterThan(upload)
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
  it('keeps fund-account maintenance on its own workspace tab', () => {
    const source = readFileSync(
      resolve(process.cwd(), 'src/pages/finance/FinanceControlWorkspacePage.vue'),
      'utf8',
    )
    expect(source).toContain("route.path === '/fund-accounts'")
    expect(source).toContain('v-else-if="mode === \'accounts\'"')
    expect(source).toContain('accounts.value = await loadFundAccounts(request.signal)')
    expect(source).toContain('journal.value = await loadCashJournal(')
    expect(source).toContain('@click="openFundAccount"')
    expect(source).toContain("uploadSiteFile(file, 'CASH_JOURNAL', row.id, 'BANK_RECEIPT')")
    expect(source).toContain("can('file:upload') || can('cashbook:journal:maintain')")
  })
  it('opens authoritative payment traces from application, journal, invoice and voucher', async () => {
    const fetchMock = vi.fn(async () => new Response(JSON.stringify({ code: '0', data: [] })))
    vi.stubGlobal('fetch', fetchMock)
    await loadPaymentTraceByApplication('APP/1')
    await loadPaymentTraceByCashJournal('J/1')
    await loadPaymentTraceByInvoice('INV/1')
    await loadPaymentTraceByVoucher('V/1')
    expect(fetchMock.mock.calls.map(([url]) => String(url))).toEqual([
      '/api/payment-traces/applications/APP%2F1',
      '/api/payment-traces/cash-journals/J%2F1',
      '/api/payment-traces/invoices/INV%2F1',
      '/api/payment-traces/vouchers/V%2F1',
    ])
    vi.unstubAllGlobals()
  })
  it('renders trace links without client-side relationship synthesis', () => {
    const receivables = readFileSync(
      resolve(process.cwd(), 'src/pages/finance/ReceivablesWorkspacePage.vue'),
      'utf8',
    )
    const control = readFileSync(
      resolve(process.cwd(), 'src/pages/finance/FinanceControlWorkspacePage.vue'),
      'utf8',
    )
    const dialog = readFileSync(
      resolve(process.cwd(), 'src/components/finance/PaymentTraceDialog.vue'),
      'utf8',
    )
    expect(receivables).toContain('loadPaymentTraceByApplication')
    expect(receivables).toContain('loadPaymentTraceByInvoice')
    expect(control).toContain("openTrace('journal', row.id)")
    expect(control).toContain("openTrace('voucher', row.id)")
    expect(dialog).toContain('缺链由接口直接拒绝，不在页面补链。')
  })
  it('renders the backend payment trace shape and conservation facts', () => {
    const trace: PaymentTraceRecord = {
      project: { id: 'P1', projectName: '项目一' },
      contract: { id: 'C1', contractName: '合同一', currentAmount: '100.00' },
      paymentApplication: { id: 'A1', applyCode: 'PAY-1', applyAmount: '60.00' },
      approvalRecords: [],
      applicationSources: [],
      expenses: [],
      settlements: [],
      settlementSubMeasures: [],
      subMeasures: [],
      subTasks: [],
      paymentRecords: [],
      paymentSourceAllocations: [],
      cashJournals: [],
      paymentDocuments: [],
      invoices: [],
      invoiceAllocations: [],
      budgetLedgers: [],
      accountingEntries: [],
      accountingEntryLines: [],
      contractBudgetAllocation: { id: 'CA1' },
      projectBudget: { id: 'B1' },
      projectBudgetLine: { id: 'BL1' },
      costSubject: { id: 'S1' },
      materialReceiptItems: [{ id: 'RI1' }],
      materialReceipts: [{ id: 'R1' }],
      budgetConservation: {
        netReserved: '40.00',
        netConsumed: '20.00',
        netPaid: '60.00',
        netCashOutflow: '60.00',
      },
    }
    const wrapper = mount(PaymentTraceDialog, {
      props: { open: true, traces: [trace] },
      global: { stubs: { teleport: true } },
    })

    const rendered = wrapper.text()
    for (const expected of [
      '合同预算1',
      '项目预算1',
      '项目预算行1',
      '成本科目1',
      '材料验收1',
      '验收明细1',
      '预算净占用40.00',
      '预算净消耗20.00',
      '来源净实付60.00',
      '现金净流出60.00',
    ])
      expect(rendered).toContain(expected)
  })
})
