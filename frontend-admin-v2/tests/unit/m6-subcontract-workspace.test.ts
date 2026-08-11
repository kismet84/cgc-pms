import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { readFileSync } from 'node:fs'
import { resolve } from 'node:path'
import { SUBCONTRACT_PERMISSIONS } from '@cgc-pms/frontend-contracts'
import {
  createSubcontractMeasure,
  createSubcontractTask,
  deleteSubcontractMeasure,
  deleteSubcontractTask,
  loadSubcontractMeasure,
  loadSubcontractMeasureItems,
  loadSubcontractTask,
  saveSubcontractMeasureItems,
  submitSubcontractMeasure,
  updateSubcontractMeasure,
  updateSubcontractTask,
} from '@/services/subcontract'

const fetchMock = vi.fn<typeof fetch>()
const response = (data: unknown = null) =>
  new Response(JSON.stringify({ code: '0', message: 'success', data }), {
    status: 200,
    headers: { 'Content-Type': 'application/json' },
  })

beforeEach(() => {
  fetchMock.mockReset().mockImplementation(async (url, init) => {
    if (init?.method === 'POST' && ['/api/sub-tasks', '/api/sub-measures'].includes(String(url)))
      return response('9007199254740993')
    if (String(url).endsWith('/items')) return response([])
    return response({})
  })
  vi.stubGlobal('fetch', fetchMock)
})
afterEach(() => vi.unstubAllGlobals())

describe('M6 subcontract task and measure V2', () => {
  it('keeps query and mutation permissions separate', () => {
    expect(SUBCONTRACT_PERMISSIONS).toEqual({
      task: {
        query: 'subtask:query',
        add: 'subtask:add',
        edit: 'subtask:edit',
        delete: 'subtask:delete',
      },
      measure: {
        query: 'subcontract:measure:query',
        add: 'subcontract:measure:add',
        edit: 'subcontract:measure:edit',
        delete: 'subcontract:measure:delete',
        submit: 'subcontract:measure:submit',
      },
      settlement: {
        query: 'settlement:query',
        add: 'settlement:add',
        edit: 'settlement:edit',
        delete: 'settlement:delete',
        submit: 'settlement:submit',
      },
    })
  })

  it('uses encoded endpoints and preserves decimal strings', async () => {
    const signal = new AbortController().signal
    await loadSubcontractTask('T/1', signal)
    const taskId = await createSubcontractTask({
      projectId: 'P1',
      contractId: 'C1',
      partnerId: 'S1',
      taskName: '地下室劳务',
      progressPercent: '9007199254740993.1234',
    })
    await updateSubcontractTask('T/1', {
      projectId: 'P1',
      contractId: 'C1',
      partnerId: 'S1',
      taskName: '地下室劳务',
      progressPercent: '0.0100',
    })
    await deleteSubcontractTask('T/1')

    await loadSubcontractMeasure('M/1', signal)
    await loadSubcontractMeasureItems('M/1', signal)
    const measureId = await createSubcontractMeasure({
      projectId: 'P1',
      contractId: 'C1',
      partnerId: 'S1',
      measurePeriod: '2026-07',
      measureDate: '2026-07-25',
    })
    await updateSubcontractMeasure('M/1', {
      projectId: 'P1',
      contractId: 'C1',
      partnerId: 'S1',
      measurePeriod: '2026-07',
      measureDate: '2026-07-25',
    })
    await saveSubcontractMeasureItems('M/1', [
      { contractItemId: 'CI1', currentQuantity: '9007199254740993.1234' },
    ])
    await submitSubcontractMeasure('M/1')
    await deleteSubcontractMeasure('M/1')

    expect(taskId).toBe('9007199254740993')
    expect(measureId).toBe('9007199254740993')
    const urls = fetchMock.mock.calls.map(([url]) => String(url))
    expect(urls).toContain('/api/sub-tasks/T%2F1')
    expect(urls).toContain('/api/sub-measures/M%2F1/items/batch')
    expect(urls).toContain('/api/sub-measures/M%2F1/submit')
    const itemWrite = fetchMock.mock.calls.find(([url]) =>
      String(url).endsWith('/sub-measures/M%2F1/items/batch'),
    )
    expect(JSON.parse(String(itemWrite?.[1]?.body))).toEqual([
      { contractItemId: 'CI1', currentQuantity: '9007199254740993.1234' },
    ])
    expect(fetchMock.mock.calls.filter(([, init]) => init?.signal === signal)).toHaveLength(3)
  })

  it('binds real routes and re-reads every successful business write', () => {
    const page = readFileSync(
      resolve(process.cwd(), 'src/pages/subcontract/SubcontractWorkspacePage.vue'),
      'utf8',
    )
    const router = readFileSync(resolve(process.cwd(), 'src/router.ts'), 'utf8')
    const catalog = readFileSync(resolve(process.cwd(), 'src/navigation/catalog.ts'), 'utf8')

    expect(router).toContain("'/subcontract/task'")
    expect(router).toContain("'/subcontract/measure'")
    expect(router).toContain("path: '/subcontract'")
    expect(router).toContain('SubcontractWorkspacePage')
    expect(catalog).not.toMatch(
      /path: '\/subcontract\/(?:task|measure)'[\s\S]{0,160}migration: 'pending'/,
    )
    expect(page).toContain('await loadPage()')
    expect(page.match(/await selectRecordById\(/g)).toHaveLength(3)
    expect(page).toContain('form.contractId !== value')
    expect(page).toContain('暂未取得最新结果，请刷新重试。')
    expect(page).toContain('<BusinessAttachmentPanel')
    expect(page).toContain('business-type="SUBCONTRACT"')
    expect(page).toContain('document-type="MEASURE_SUPPORT"')
    expect(page).toContain(':can-upload="selectedEditable"')
    expect(page).toContain(':can-delete="selectedEditable"')
    expect(page).toContain('loadContractItems')
    expect(page).toContain('loadContract(contractId, controller.signal)')
    expect(page).toContain("contractType: 'SUB'")
    expect(page).toContain("item.approvalStatus === 'APPROVED'")
    expect(page).toContain("item.contractStatus === 'PERFORMING'")
    expect(page).toContain('item.id === form.contractId')
    expect(page).toContain('excludedPredecessorIds')
    expect(page).toContain("formMode.value === 'edit'")
    expect(page).toContain('children.get(item.predecessorTaskId)')
    expect(page).toContain("historicalName || '历史分包任务'")
    expect(page).toContain('form.predecessorTaskId = predecessorTaskId')
    expect(page).toContain('form.subTaskId = subTaskId')
    expect(page).toContain('form.partnerId = contract?.partyBId')
    expect(page).toContain('let listGeneration = 0')
    expect(page).toContain('listController?.abort()')
    expect(page).not.toMatch(/\.subcontract-workspace__facts div\s*\{[^}]*background:/)
    expect(page).toMatch(
      /\.subcontract-workspace__facts\s*\{[^}]*background:\s*transparent;[^}]*border:\s*0;[^}]*box-shadow:\s*none;/,
    )
    expect(page).not.toMatch(
      /frontend-admin\/src|Legacy|label="[^"]*ID|\b(?:Number|parseFloat|parseInt)\s*\(|(?:reportedAmount|approvedAmount|netAmount|amount)\s*[+\-*/]=/,
    )
  })
})
