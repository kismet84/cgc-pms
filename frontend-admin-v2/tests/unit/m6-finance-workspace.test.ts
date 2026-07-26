import { describe, expect, it, vi } from 'vitest'
import { readFileSync } from 'node:fs'
import { resolve } from 'node:path'
import { FINANCE_DECIMAL_FIELDS, FINANCE_API } from '@cgc-pms/frontend-contracts'
import { loadPaymentApplications } from '@/services/finance'

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
  it('keeps abort state scoped to each finance request', () => {
    const source = readFileSync(
      resolve(process.cwd(), 'src/pages/finance/ReceivablesWorkspacePage.vue'),
      'utf8',
    )
    expect(source).toContain('const request = new AbortController()')
    expect(source).toContain('if (!request.signal.aborted)')
    expect(source).not.toContain('if (!controller.signal.aborted)')
  })
})
