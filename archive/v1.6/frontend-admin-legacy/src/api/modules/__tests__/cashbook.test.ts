import { beforeEach, describe, expect, it, vi } from 'vitest'

const { requestMock } = vi.hoisted(() => ({ requestMock: vi.fn() }))

vi.mock('@/api/request', () => ({ request: requestMock }))

import {
  archiveCashJournalEntry,
  createCashJournalEntry,
  createFundAccount,
  exportCashJournal,
  getCashJournalDetail,
  getCashJournalList,
  getCashJournalSummary,
  getFundAccounts,
  getManageableFundAccounts,
  reopenCashJournalEntry,
  reverseCashJournalEntry,
  setFundAccountEnabled,
  updateCashJournalEntry,
  updateFundAccount,
} from '../cashbook'

describe('cashbook API contracts', () => {
  beforeEach(() => requestMock.mockReset().mockResolvedValue({}))

  it('uses the cash journal query, summary, detail and export endpoints', async () => {
    const params = { pageNo: 2, pageSize: 20, sourceType: 'PAY_RECORD', sourceId: '88' }
    await getCashJournalList(params)
    await getCashJournalSummary(params)
    await getCashJournalDetail('99')
    await exportCashJournal(params)

    expect(requestMock).toHaveBeenNthCalledWith(1, {
      url: '/cash-journal-entries',
      method: 'get',
      params,
    })
    expect(requestMock).toHaveBeenNthCalledWith(2, {
      url: '/cash-journal-entries/summary',
      method: 'get',
      params,
    })
    expect(requestMock).toHaveBeenNthCalledWith(3, {
      url: '/cash-journal-entries/99',
      method: 'get',
    })
    expect(requestMock).toHaveBeenNthCalledWith(4, {
      url: '/cash-journal-entries/export',
      method: 'get',
      params,
      responseType: 'blob',
    })
  })

  it('uses the create, update and controlled state endpoints', async () => {
    const data = {
      direction: 'IN' as const,
      amount: '12.30',
      businessDate: '2026-07-10',
      summary: '收款',
    }
    await createCashJournalEntry(data)
    await updateCashJournalEntry('10', data)
    await archiveCashJournalEntry('10')
    await reverseCashJournalEntry('10', '录入错误')
    await reopenCashJournalEntry('10', '管理员修正')

    expect(requestMock).toHaveBeenNthCalledWith(1, {
      url: '/cash-journal-entries',
      method: 'post',
      data,
    })
    expect(requestMock).toHaveBeenNthCalledWith(2, {
      url: '/cash-journal-entries/10',
      method: 'put',
      data,
    })
    expect(requestMock).toHaveBeenNthCalledWith(3, {
      url: '/cash-journal-entries/10/archive',
      method: 'post',
    })
    expect(requestMock).toHaveBeenNthCalledWith(4, {
      url: '/cash-journal-entries/10/reverse',
      method: 'post',
      data: { reason: '录入错误' },
    })
    expect(requestMock).toHaveBeenNthCalledWith(5, {
      url: '/cash-journal-entries/10/reopen',
      method: 'post',
      data: { reason: '管理员修正' },
    })
  })

  it('uses the fund account endpoints', async () => {
    const account = {
      accountCode: 'BANK-01',
      accountName: '基本户',
      accountType: 'BANK' as const,
      openingDate: '2026-07-01',
      openingBalance: '100.00',
    }
    await getFundAccounts()
    await getManageableFundAccounts()
    await createFundAccount(account)
    await updateFundAccount('20', account)
    await setFundAccountEnabled('20', false)

    expect(requestMock).toHaveBeenNthCalledWith(1, { url: '/fund-accounts', method: 'get' })
    expect(requestMock).toHaveBeenNthCalledWith(2, { url: '/fund-accounts/manage', method: 'get' })
    expect(requestMock).toHaveBeenNthCalledWith(3, {
      url: '/fund-accounts',
      method: 'post',
      data: account,
    })
    expect(requestMock).toHaveBeenNthCalledWith(4, {
      url: '/fund-accounts/20',
      method: 'put',
      data: account,
    })
    expect(requestMock).toHaveBeenNthCalledWith(5, {
      url: '/fund-accounts/20/enabled',
      method: 'put',
      params: { enabled: false },
    })
  })
})
