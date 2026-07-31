import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { readFileSync } from 'node:fs'
import { resolve } from 'node:path'
import { SUBCONTRACT_PERMISSIONS } from '@cgc-pms/frontend-contracts'
import {
  computeSettlement,
  createSettlement,
  deleteSettlement,
  loadSettlement,
  loadSettlementApprovalRecords,
  loadSettlementAttachments,
  loadSettlementCosts,
  loadSettlementKpi,
  loadSettlementPayments,
  loadSettlementSources,
  loadSettlementVariations,
  saveSettlementItems,
  submitSettlement,
  updateSettlement,
} from '@/services/subcontract'

const fetchMock = vi.fn<typeof fetch>()
const response = (data: unknown = null, status = 200) =>
  new Response(JSON.stringify({ code: status === 200 ? '0' : String(status), data }), {
    status,
    headers: { 'Content-Type': 'application/json' },
  })

beforeEach(() => {
  fetchMock.mockReset().mockImplementation(async (url, init) => {
    if (init?.method === 'POST' && String(url) === '/api/settlements')
      return response('9007199254740993')
    return response({})
  })
  vi.stubGlobal('fetch', fetchMock)
})
afterEach(() => vi.unstubAllGlobals())

describe('M6 settlement V2', () => {
  it('keeps settlement query and mutations separately authorized', () => {
    expect(SUBCONTRACT_PERMISSIONS.settlement).toEqual({
      query: 'settlement:query',
      add: 'settlement:add',
      edit: 'settlement:edit',
      delete: 'settlement:delete',
      submit: 'settlement:submit',
    })
  })

  it('uses encoded trace endpoints and preserves server-authoritative strings', async () => {
    const signal = new AbortController().signal
    await loadSettlement('S/1', signal)
    await loadSettlementKpi({ projectId: 'P/1', settlementStatus: 'DRAFT' }, signal)
    await computeSettlement('C/1', signal)
    await loadSettlementSources('S/1', signal)
    await loadSettlementVariations('S/1', signal)
    await loadSettlementPayments('S/1', signal)
    await loadSettlementCosts('S/1', signal)
    await loadSettlementAttachments('S/1', signal)
    await loadSettlementApprovalRecords('S/1', signal)
    const id = await createSettlement({
      contractId: '9007199254740993',
      deductionAmount: '0.01',
      remark: null,
    })
    await updateSettlement('S/1', {
      contractId: '9007199254740993',
      deductionAmount: '9007199254740993.01',
    })
    await saveSettlementItems('S/1', [{ sourceType: 'CT_CONTRACT', sourceId: '9007199254740995' }])
    await submitSettlement('S/1')
    await deleteSettlement('S/1')

    expect(id).toBe('9007199254740993')
    const urls = fetchMock.mock.calls.map(([url]) => String(url))
    expect(urls).toContain('/api/settlements/S%2F1')
    expect(urls).toContain('/api/settlements/compute/C%2F1')
    expect(urls).toContain('/api/settlements/S%2F1/approval-records')
    expect(urls).toContain('/api/settlements/S%2F1/items/batch')
    const itemWrite = fetchMock.mock.calls.find(([url]) =>
      String(url).endsWith('/settlements/S%2F1/items/batch'),
    )
    expect(JSON.parse(String(itemWrite?.[1]?.body))).toEqual([
      { sourceType: 'CT_CONTRACT', sourceId: '9007199254740995' },
    ])
    expect(fetchMock.mock.calls.filter(([, init]) => init?.signal === signal)).toHaveLength(9)
  })

  it('binds all three routes to one real page and contains no client money calculation', () => {
    const page = readFileSync(
      resolve(process.cwd(), 'src/pages/settlement/SettlementWorkspacePage.vue'),
      'utf8',
    )
    const router = readFileSync(resolve(process.cwd(), 'src/router.ts'), 'utf8')
    const catalog = readFileSync(resolve(process.cwd(), 'src/navigation/catalog.ts'), 'utf8')

    expect(router).toContain("path: '/settlement'")
    expect(router).toContain("path: '/settlement/:id'")
    expect(router).toContain('SettlementWorkspacePage')
    expect(catalog).not.toMatch(/path: '\/settlement\/list'[\s\S]{0,160}migration: 'pending'/)
    expect(page).toContain('await loadDetail(id)')
    expect(page).toContain("sourceType: 'CT_CONTRACT'")
    expect(page).toContain(
      "uploadSiteFile(uploadFile.value, 'SETTLEMENT', selected.value.id, 'OTHER')",
    )
    expect(page).toContain('aria-label="选择结算附件"')
    expect(page).toContain('class="v2-file-input"')
    expect(page).not.toContain('<label for="settlement-file">选择附件</label>')
    expect(page).toContain("contractType: 'SUB'")
    expect(page).toContain('v-if="deletable"')
    expect(page).not.toContain('v-if="canDelete && editable"')
    expect(page).toMatch(
      /\.settlement-workspace__summary \.v2-detail-dialog__facts\s*{\s*grid-template-columns: minmax\(0, 1fr\)/,
    )
    expect(page).not.toMatch(
      /frontend-admin\/src|Legacy|label="[^"]*ID|\b(?:Number|parseFloat|parseInt)\s*\(|(?:contractAmount|changeAmount|measuredAmount|deductionAmount|paidAmount|finalAmount|unpaidAmount|warrantyAmount)\s*[+\-*/]=/,
    )
  })

  it.each([403, 404, 409, 422, 500])('preserves HTTP %s as a typed failure', async (status) => {
    fetchMock.mockImplementationOnce(async () => response(null, status))
    await expect(loadSettlement('S1')).rejects.toMatchObject({ status, code: String(status) })
  })
})
