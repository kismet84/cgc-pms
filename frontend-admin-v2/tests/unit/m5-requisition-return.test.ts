import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { readFileSync } from 'node:fs'
import { resolve } from 'node:path'
import { REQUISITION_PERMISSIONS } from '@cgc-pms/frontend-contracts'
import {
  confirmMaterialReturn,
  createRequisition,
  deleteRequisition,
  loadMaterialReturn,
  loadMaterialReturnItems,
  loadRequisition,
  loadRequisitionFormOptions,
  loadRequisitionItems,
  loadRequisitions,
  loadRequisitionTrace,
  reverseMaterialReturn,
  saveRequisitionItems,
  stockOutRequisition,
  submitRequisition,
  updateRequisition,
} from '@/services/supply-chain'

const fetchMock = vi.fn<typeof fetch>()
const response = (data: unknown = {}) =>
  new Response(JSON.stringify({ code: '0', message: 'success', data }), {
    status: 200,
    headers: { 'Content-Type': 'application/json' },
  })

beforeEach(() => {
  fetchMock.mockReset().mockResolvedValue(response({}))
  vi.stubGlobal('fetch', fetchMock)
})
afterEach(() => vi.unstubAllGlobals())

describe('M5 requisition, stock-out and return contract', () => {
  it('keeps all seven actions independently permissioned', () => {
    expect(Object.values(REQUISITION_PERMISSIONS)).toEqual([
      'requisition:query',
      'requisition:add',
      'requisition:edit',
      'requisition:delete',
      'requisition:submit',
      'requisition:stock-out',
      'requisition:return',
    ])
  })

  it('uses server stages, exact rollback, idempotency and no inventory totals', () => {
    const source = readFileSync(
      resolve(process.cwd(), 'src/pages/supply-chain/RequisitionWorkspacePage.vue'),
      'utf8',
    )
    expect(source).toContain("selected.value?.approvalStatus === 'DRAFT'")
    expect(source).toContain("selected.value?.approvalStatus === 'APPROVED'")
    expect(source).toContain("selected.value?.stockOutFlag !== '1'")
    expect(source).toContain('crypto.randomUUID()')
    expect(source).not.toContain('await deleteRequisition(createdId)')
    expect(source).toContain("hasPermission('requisition:self')")
    expect(source).toContain('canUseRequisitionSelf.value ||')
    expect(source).toContain('发起领料申请')
    expect(source).toContain('requisition-page__workspace')
    expect(source).not.toContain('data-master-detail="true"')
    expect(source).toContain('panel-class="v2-dialog-standard v2-detail-dialog"')
    expect(source).toContain(':close-on-backdrop="true"')
    expect(source).toContain('panel-class="v2-dialog-standard"')
    expect(source).toContain(':close-on-backdrop="false"')
    expect(source).toContain('tabindex="0"')
    expect(source).toContain('<th v-if="!projectId">项目</th>')
    expect(source).toContain('<th>仓库编码</th>')
    expect(source).toContain('<th>仓库名称</th>')
    expect(source).toContain('dateFrom: reportPeriod.value?.startDate')
    expect(source).toContain('dateTo: reportPeriod.value?.endDate')
    expect(source).toContain('editorItems')
    expect(source).toContain('loadRequisitionFormOptions')
    expect(source).toContain('loadStocks({')
    expect(source).toContain('stockedMaterialIds')
    expect(source).toContain("hasPermission('inventory:stock:list')")
    expect(source).toContain('stockFilterReady')
    expect(source).toContain('item.availableQty')
    expect(source).toContain('saveEditor(true)')
    expect(source).toContain('await Promise.all([loadRequisition(id), loadRequisitionItems(id)])')
    expect(source).toContain("'MAT_REQUISITION', 'REQUISITION'")
    expect(source).toContain('loadRequisitionTrace')
    expect(source).toContain("hasPermission('procurement:trace:query')")
    expect(source).toContain("{{ item.warehouseCode || '仓库编码缺失' }}")
    expect(source).toContain("{{ item.warehouseName || '仓库名称缺失' }}")
    expect(source).not.toContain('loadWarehouses')
    expect(source).not.toContain('loadMaterials')
    expect(source).not.toContain('loadPartners')
    expect(source).not.toContain('loadContractPage')
    expect(source).toContain('!requisitionSelfOnly.value && item.unitPrice.trim()')
    expect(source).toContain(':options="warehouseOptions"')
    expect(source).toContain(':options="materialOptions"')
    expect(source).not.toContain('class="requisition-page__filters"')
    expect(source).not.toContain("{{ item.warehouseId || '-' }}")
    expect(source).not.toMatch(/label="[^"]*ID/)
    expect(source).not.toMatch(
      /availableQty\s*[+]=|totalAmount\s*[+]=|stock\/in|stock\/out|frontend-admin\/src/,
    )
  })

  it('loads requisition form options from the project-scoped no-amount endpoint', async () => {
    const signal = new AbortController().signal
    const options = {
      warehouses: [{ id: 'W1', warehouseCode: 'WH-1', warehouseName: '项目仓', projectId: 'P1' }],
      materials: [{ id: 'M1', materialCode: 'MAT-1', materialName: '钢筋', unit: '吨' }],
      partners: [{ id: 'S1', partnerCode: 'SUP-1', partnerName: '供应商' }],
      contracts: [{ id: 'C1', contractCode: 'CT-1', contractName: '采购合同', projectId: 'P1' }],
    }
    fetchMock.mockResolvedValueOnce(response(options))

    await expect(loadRequisitionFormOptions('P/1', signal)).resolves.toEqual(options)
    expect(fetchMock).toHaveBeenCalledWith(
      '/api/requisitions/form-options?projectId=P%2F1',
      expect.objectContaining({ method: 'GET', headers: expect.any(Headers), signal }),
    )
  })

  it('encodes ids and preserves decimal strings across the closed loop', async () => {
    const signal = new AbortController().signal
    fetchMock.mockImplementation(async (url, init) => {
      if (
        init?.method === 'POST' &&
        (String(url).endsWith('/requisitions') ||
          String(url).endsWith('/material-returns/confirm') ||
          String(url).endsWith('/reverse'))
      )
        return response('9007199254740993')
      if (String(url).endsWith('/items')) return response([])
      if (String(url).includes('/procurement-traces/'))
        return response({
          requisitionItems: [],
          stockTransactions: [],
          costs: [],
          materialReturnItems: [],
          approvalInstances: [],
          approvalRecords: [],
        })
      return response({ records: [], total: 0, pageNo: 1, pageSize: 20 })
    })
    await loadRequisitions(
      { projectId: 'P/1', dateFrom: '2026-07-01', dateTo: '2026-07-31', pageNo: 2 },
      signal,
    )
    await loadRequisition('R/1', signal)
    await loadRequisitionItems('R/1', signal)
    const id = await createRequisition({ projectId: 'P1', warehouseId: 'W1' })
    await updateRequisition('R/1', { projectId: 'P1', warehouseId: 'W1' })
    await saveRequisitionItems('R/1', [
      {
        requisitionId: 'R/1',
        materialId: 'M1',
        quantity: '9007199254740993.1234',
        unitPrice: '3.25',
      },
    ])
    await submitRequisition('R/1')
    await stockOutRequisition('R/1')
    await loadRequisitionTrace('R/1', signal)
    const returnId = await confirmMaterialReturn({
      requisitionItemId: 'RI1',
      originalStockTxnId: 'T1',
      quantity: '1.2500',
      returnDate: '2026-07-24',
      reason: '余料',
      idempotencyKey: 'K1',
    })
    await loadMaterialReturn('MR/1', signal)
    await loadMaterialReturnItems('MR/1', signal)
    await reverseMaterialReturn('MR/1', '误退')
    await deleteRequisition('R/1')
    expect(id).toBe('9007199254740993')
    expect(returnId).toBe('9007199254740993')
    const calls = fetchMock.mock.calls
    expect(calls.map(([url]) => String(url))).toContain('/api/requisitions/R%2F1/stock-out')
    expect(calls.map(([url]) => String(url))).toContain(
      '/api/requisitions?projectId=P%2F1&dateFrom=2026-07-01&dateTo=2026-07-31&pageNo=2',
    )
    expect(calls.map(([url]) => String(url))).toContain(
      '/api/procurement-traces/requisitions/R%2F1',
    )
    expect(calls.map(([url]) => String(url))).toContain('/api/material-returns/MR%2F1/items')
    const itemWrite = calls.find(([url]) => String(url).endsWith('/requisitions/R%2F1/items/batch'))
    expect(JSON.parse(String(itemWrite?.[1]?.body))[0].quantity).toBe('9007199254740993.1234')
  })

  it('propagates duplicate stock-out without client retry', async () => {
    fetchMock.mockResolvedValueOnce(
      new Response(
        JSON.stringify({ code: 'REQUISITION_ALREADY_STOCKED_OUT', message: '领料单已出库' }),
        {
          status: 409,
          headers: { 'Content-Type': 'application/json' },
        },
      ),
    )
    await expect(stockOutRequisition('R1')).rejects.toMatchObject({
      status: 409,
      code: 'REQUISITION_ALREADY_STOCKED_OUT',
    })
    expect(fetchMock).toHaveBeenCalledTimes(1)
  })
})
