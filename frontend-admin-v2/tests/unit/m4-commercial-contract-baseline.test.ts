import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { readFileSync, readdirSync } from 'node:fs'
import { resolve } from 'node:path'
import {
  COMMERCIAL_API,
  COMMERCIAL_MONEY_FIELDS,
  COMMERCIAL_QUERY_PERMISSIONS,
} from '@cgc-pms/frontend-contracts'
import { loadContract, loadContractPage, loadCostSummaryHistory } from '@/services/commercial'

const fetchMock = vi.fn<typeof fetch>()

function apiResponse<T>(data: T): Response {
  return new Response(JSON.stringify({ code: '0', message: 'success', data }), {
    status: 200,
    headers: { 'Content-Type': 'application/json' },
  })
}

beforeEach(() => {
  fetchMock.mockReset()
  fetchMock.mockImplementation(async () => apiResponse({ records: [], total: 0 }))
  vi.stubGlobal('fetch', fetchMock)
})

afterEach(() => vi.unstubAllGlobals())

describe('M4 commercial contract baseline', () => {
  it('freezes nine query permissions without an administrator fallback', () => {
    expect(COMMERCIAL_QUERY_PERMISSIONS).toEqual({
      contract: 'contract:query',
      variation: 'variation:order:query',
      bidCost: 'bid:query',
      costTarget: 'cost:target:query',
      costLedger: 'cost:ledger:query',
      costSummary: 'cost:summary:view',
      costControl: 'cost:control:query',
      budget: 'budget:query',
      measurement: 'measurement:query',
    })
    expect(JSON.stringify(COMMERCIAL_API)).not.toContain('/cost/subject')
  })

  it('encodes non-empty contract filters and passes the abort signal', async () => {
    const controller = new AbortController()
    await loadContractPage(
      {
        pageNo: 2,
        pageSize: 20,
        keyword: ' 合同 A&B ',
        contractCode: '',
        projectId: ' P/1 ',
        contractStatus: 'PERFORMING',
      },
      controller.signal,
    )

    expect(fetchMock.mock.calls[0]?.[0]).toBe(
      '/api/contracts?pageNo=2&pageSize=20&keyword=%E5%90%88%E5%90%8C+A%26B&projectId=P%2F1&contractStatus=PERFORMING',
    )
    expect(fetchMock.mock.calls[0]?.[1]).toMatchObject({
      method: 'GET',
      body: undefined,
      signal: controller.signal,
    })
  })

  it('uses only encoded GET detail and history endpoints', async () => {
    fetchMock.mockImplementation(async () => apiResponse({}))
    const signal = new AbortController().signal

    await loadContract(' C/1 ', signal)
    await loadCostSummaryHistory(' P/1 ', signal)

    expect(fetchMock.mock.calls.map(([url]) => String(url))).toEqual([
      '/api/contracts/C%2F1',
      '/api/cost-summary/P%2F1/history',
    ])
    for (const [, options] of fetchMock.mock.calls) {
      expect(options).toMatchObject({ method: 'GET', body: undefined, signal })
    }
  })

  it('rejects empty ids before sending requests', () => {
    expect(() => loadContract(' ')).toThrow('合同ID不能为空')
    expect(() => loadCostSummaryHistory(' ')).toThrow('项目ID不能为空')
    expect(fetchMock).not.toHaveBeenCalled()
  })

  it('preserves authoritative money as decimal strings', async () => {
    fetchMock.mockImplementationOnce(async () =>
      apiResponse([
        {
          id: '1',
          projectId: '2',
          targetCost: '9007199254740993.01',
          actualCost: '0',
          expectedProfit: '-0.01',
        },
      ]),
    )

    await expect(loadCostSummaryHistory('2')).resolves.toMatchObject([
      { targetCost: '9007199254740993.01', actualCost: '0', expectedProfit: '-0.01' },
    ])
    expect(Object.values(COMMERCIAL_MONEY_FIELDS).flat()).toContain('actual_saving_amount')
  })

  it('keeps the shared contract and service free of UI, Legacy and money coercion', () => {
    const sources = [
      readFileSync(resolve('../packages/frontend-contracts/src/commercial.ts'), 'utf-8'),
      readFileSync(resolve('src/services/commercial.ts'), 'utf-8'),
    ].join('\n')

    expect(sources).not.toMatch(/from ["'](?:vue|pinia|vue-router)/)
    expect(sources).not.toContain('frontend-admin/')
    expect(sources).not.toMatch(/\b(?:parseFloat|parseInt)\s*\(/)
    expect(sources).not.toMatch(/\bNumber\s*\(/)
    expect(sources).not.toMatch(/method:\s*["'](?:POST|PUT|PATCH|DELETE)["']/)
  })

  it('supplies required semantic copy to every commercial page state and alert', () => {
    const directory = resolve('src/pages/commercial')
    const failures: string[] = []
    for (const file of readdirSync(directory).filter((name) => name.endsWith('.vue'))) {
      const source = readFileSync(resolve(directory, file), 'utf-8')
      for (const tag of source.match(/<V2PageState\b[^>]*>/g) ?? []) {
        if (!/\bdescription="[^"]+"/.test(tag)) failures.push(`${file}: V2PageState description`)
      }
      for (const tag of source.match(/<V2Alert\b[^>]*>/g) ?? []) {
        if (!/\btitle="[^"]+"/.test(tag)) failures.push(`${file}: V2Alert title`)
      }
    }
    expect(failures).toEqual([])
  })
})
