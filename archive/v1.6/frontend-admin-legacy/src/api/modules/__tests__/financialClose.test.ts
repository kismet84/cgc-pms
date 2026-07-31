import { beforeEach, describe, expect, it, vi } from 'vitest'

const requestMock = vi.hoisted(() => vi.fn())
vi.mock('@/api/request', () => ({ request: requestMock }))

import {
  closeFinancialPeriod,
  createAdjustmentEntry,
  createFinancialPeriod,
  getFinancialCloseTrace,
  getFinancialPeriods,
  getFinancialStatements,
  reopenFinancialPeriod,
  resolveBankReconciliation,
  runFinancialCloseChecks,
} from '../financialClose'

describe('financial close API contracts', () => {
  beforeEach(() => requestMock.mockReset().mockResolvedValue({}))

  it('maps period lifecycle and trace endpoints', async () => {
    await getFinancialPeriods(2031)
    await createFinancialPeriod(2031, 1)
    await runFinancialCloseChecks(2031, 1)
    await closeFinancialPeriod(2031, 1, '月结')
    await reopenFinancialPeriod(2031, 1, '审计调整')
    await getFinancialCloseTrace('10')
    await getFinancialStatements(2031, 1)
    expect(requestMock.mock.calls.map((call) => call[0].url)).toEqual([
      '/financial-close/periods',
      '/financial-close/periods',
      '/financial-close/periods/2031/1/checks',
      '/financial-close/periods/2031/1/close',
      '/financial-close/periods/2031/1/reopen',
      '/financial-close/periods/10/trace',
      '/financial-close/periods/2031/1/statements',
    ])
  })

  it('maps adjustment and manual bank reconciliation endpoints', async () => {
    await createAdjustmentEntry({ reason: '调整' })
    await resolveBankReconciliation('20', { businessType: 'PAY_RECORD' })
    expect(requestMock).toHaveBeenNthCalledWith(1, {
      url: '/financial-close/adjustments',
      method: 'post',
      data: { reason: '调整' },
    })
    expect(requestMock).toHaveBeenNthCalledWith(2, {
      url: '/financial-close/bank-reconciliations/20/resolve',
      method: 'post',
      data: { businessType: 'PAY_RECORD' },
    })
  })
})
