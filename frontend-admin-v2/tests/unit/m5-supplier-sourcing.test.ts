import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { readFileSync } from 'node:fs'
import { resolve } from 'node:path'
import { SUPPLIER_SOURCING_PERMISSIONS } from '@cgc-pms/frontend-contracts'
import {
  awardSourcingEvent,
  confirmSupplierPerformance,
  createBidEvaluation,
  createSourcingEvent,
  createSupplierBlacklist,
  createSupplierPerformance,
  createSupplierQuote,
  declineSourcingSupplier,
  inviteSourcingSuppliers,
  linkSourcingContract,
  loadBidEvaluations,
  loadSourcingEvents,
  loadSourcingSuppliers,
  loadSourcingTrace,
  loadSupplierPerformance,
  loadSupplierPerformanceCandidates,
  loadSupplierQuotes,
  loadSupplierReturns,
  loadSupplierSourcingWorkspace,
  publishSourcingEvent,
  reviewSupplierBlacklist,
  startSourcingEvaluation,
  submitSupplierBlacklist,
  submitSupplierQuote,
} from '@/services/supply-chain'

const fetchMock = vi.fn<typeof fetch>()
const response = (data: unknown = {}) =>
  new Response(JSON.stringify({ code: '0', message: 'success', data }), {
    status: 200,
    headers: { 'Content-Type': 'application/json' },
  })

beforeEach(() => {
  fetchMock.mockReset().mockImplementation(async () => response([]))
  vi.stubGlobal('fetch', fetchMock)
})
afterEach(() => vi.unstubAllGlobals())

describe('M5 supplier sourcing closed-loop contract', () => {
  it('keeps seven permissions separate and page actions server-authoritative', () => {
    expect(Object.values(SUPPLIER_SOURCING_PERMISSIONS)).toEqual([
      'supplier:sourcing:query',
      'supplier:sourcing:maintain',
      'supplier:sourcing:quote',
      'supplier:sourcing:evaluate',
      'supplier:sourcing:award',
      'supplier:performance:evaluate',
      'supplier:blacklist:review',
    ])
    const source = readFileSync(
      resolve(process.cwd(), 'src/pages/supply-chain/SupplierSourcingPage.vue'),
      'utf8',
    )
    expect(source).toContain("selected.status === 'DRAFT'")
    expect(source).toContain("selected.status === 'PUBLISHED'")
    expect(source).toContain("selected.status === 'EVALUATING'")
    expect(source).toContain("selected.status === 'AWARDED'")
    expect(source).toContain('loadSourcingTrace')
    const headingCard = source.match(/<V2Card title="供应商招采履约"[\s\S]*?<\/V2Card>/)?.[0] ?? ''
    expect(headingCard).not.toMatch(/<template #title-extra\b/)
    expect(headingCard).toContain('新建招采事件')
    expect(headingCard).toContain('登记履约评价')
    expect(headingCard).toContain('退货登记统一在采购执行办理')
    const eventCard = source.match(/<V2Card v-else>[\s\S]*?<\/V2Card>/)?.[0] ?? ''
    expect(eventCard).toContain('aria-label="招采事件列表"')
    expect(eventCard).not.toMatch(/<template #title-extra\b/)
    const performanceCard =
      source.match(/<V2Card title="履约评价、退货与黑名单"[\s\S]*?<\/V2Card>/)?.[0] ?? ''
    expect(performanceCard).toMatch(/<template #title-extra\b/)
    expect(performanceCard).toContain('评价 {{ performanceTotal }}')
    expect(performanceCard).toContain('退货 {{ returnTotal }}')
    for (const loader of [
      'loadPurchaseRequests',
      'loadSupplierPerformanceCandidates',
      'loadContractPage',
    ])
      expect(source).toContain(loader)
    for (const model of [
      'form.purchaseRequestId',
      'form.partnerId',
      'form.contractId',
      'form.purchaseOrderId',
    ])
      expect(source).toMatch(new RegExp(`<V2Select[\\s\\S]{0,180}v-model="${model}"`))
    expect(source).toContain(
      "inviteSourcingSuppliers(targetId.value, [required('partnerId', '供应商')])",
    )
    expect(source).not.toContain('form.partnerIds')
    expect(source).not.toMatch(/label="[^"]*ID/)
    expect(source).toContain('item.partnerCode} · ${item.partnerName')
    expect(source).toContain('item.requestCode ||')
    expect(source).toContain('item.contractCode} · ${item.contractName')
    expect(source).toContain('item.orderCode ||')
    expect(source).toContain(':options="invitePartnerOptions"')
    expect(source).toContain('trace.value?.invitedSuppliers')
    expect(source).toContain("contractType: 'PURCHASE'")
    expect(source).toContain("approvalStatus: 'APPROVED'")
    expect(source).toContain("contractStatus: 'PERFORMING'")
    expect(source).toContain('partyBId: selected.value?.awardedPartnerId')
    expect(source).not.toContain("item.orderStatus === 'COMPLETED'")
    expect(source).toContain('loadSupplierPerformanceCandidates')
    expect(source).not.toContain("show('return')")
    expect(source).not.toContain('createSupplierReturn')
    expect(source).not.toContain('confirmSupplierReturn')
    expect(source).not.toContain('{{ projectLabel }}')
    expect(source).not.toMatch(/frontend-admin\/src|Legacy/)
  })

  it('covers every backend endpoint with encoded ids and abortable reads', async () => {
    const signal = new AbortController().signal
    await loadSourcingEvents(' P/1 ', signal)
    await createSourcingEvent({
      projectId: 'P1',
      purchaseRequestId: 'R1',
      sourcingCode: 'S1',
      sourcingTitle: '采购',
      sourcingType: 'INQUIRY',
      deadline: '2026-08-01T12:00',
      currencyCode: 'CNY',
    })
    await loadSourcingSuppliers(' E/1 ', signal)
    await inviteSourcingSuppliers('E/1', ['S1'])
    await publishSourcingEvent('E/1')
    await declineSourcingSupplier('E/1', 'P&1', '不参与')
    await loadSupplierQuotes('E/1', signal)
    await createSupplierQuote({
      sourcingEventId: 'E1',
      partnerId: 'P1',
      quoteCode: 'Q1',
      totalAmount: '9007199254740993.12',
      taxRate: '13',
      deliveryDays: 7,
      validityDate: '2026-08-30',
      commercialTerms: '月结',
    })
    await submitSupplierQuote('Q/1')
    await startSourcingEvaluation('E/1')
    await createBidEvaluation({
      quoteId: 'Q1',
      commercialScore: '90',
      technicalScore: '80',
      deliveryScore: '70',
      qualityScore: '60',
      evaluationComment: '通过',
    })
    await loadBidEvaluations('E/1', signal)
    await awardSourcingEvent('E/1', 'Q1', '综合第一')
    await linkSourcingContract('E/1', 'C1')
    await loadSupplierPerformance('P1', signal)
    await loadSupplierPerformanceCandidates({ pageNo: 1, pageSize: 100, projectId: 'P1' }, signal)
    await createSupplierPerformance('O1', '88.5', '良好')
    await confirmSupplierPerformance('PE/1')
    await loadSupplierReturns('P1', signal)
    await loadSupplierSourcingWorkspace(
      {
        eventPageNo: 1,
        performancePageNo: 2,
        returnPageNo: 3,
        pageSize: 10,
        projectId: 'P1',
      },
      signal,
    )
    await createSupplierBlacklist('PE1', '建议列入')
    await submitSupplierBlacklist('B/1')
    await reviewSupplierBlacklist('B/1', 'APPROVE', '同意')
    await loadSourcingTrace('E/1', signal)

    const calls = fetchMock.mock.calls
    expect(calls.map(([url]) => String(url))).toContain(
      '/api/supplier-sourcing/events?projectId=P%2F1',
    )
    expect(calls.map(([url]) => String(url))).toContain(
      '/api/supplier-sourcing/events/E%2F1/suppliers/P%261/decline',
    )
    expect(calls.map(([url]) => String(url))).toContain('/api/supplier-sourcing/events/E%2F1/trace')
    expect(calls.map(([url]) => String(url))).toContain(
      '/api/supplier-sourcing/workspace?eventPageNo=1&performancePageNo=2&returnPageNo=3&pageSize=10&projectId=P1',
    )
    expect(calls.map(([url]) => String(url))).toContain(
      '/api/supplier-sourcing/performance-candidates?pageNo=1&pageSize=100&projectId=P1',
    )
    expect(calls.filter(([, init]) => init?.signal === signal)).toHaveLength(9)
    const quoteCall = calls.find(
      ([url, init]) => String(url) === '/api/supplier-sourcing/quotes' && init?.method === 'POST',
    )
    const quoteBody = JSON.parse(String(quoteCall?.[1]?.body))
    expect(quoteBody.totalAmount).toBe('9007199254740993.12')
  })

  it('propagates illegal-state 409 without rewriting the request', async () => {
    fetchMock.mockResolvedValueOnce(
      new Response(
        JSON.stringify({ code: 'SOURCING_STATE_INVALID', message: '当前状态不可发布' }),
        { status: 409, headers: { 'Content-Type': 'application/json' } },
      ),
    )
    await expect(publishSourcingEvent('E1')).rejects.toMatchObject({
      status: 409,
      code: 'SOURCING_STATE_INVALID',
    })
    expect(fetchMock).toHaveBeenCalledTimes(1)
  })
})
