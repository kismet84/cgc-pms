import { beforeEach, describe, expect, it, vi } from 'vitest'
import {
  createWorkflowTemplateNode,
  deleteWorkflowTemplateNode,
  loadWorkflowTemplate,
  loadWorkflowTemplates,
  reorderWorkflowTemplateNodes,
  updateWorkflowTemplate,
  updateWorkflowTemplateNode,
} from '@/services/workflow-process'

const fetchMock = vi.fn()

function ok(data: unknown): Response {
  return new Response(JSON.stringify({ code: '0', message: 'success', data }), {
    status: 200,
    headers: { 'Content-Type': 'application/json' },
  })
}

beforeEach(() => {
  fetchMock.mockReset()
  vi.stubGlobal('fetch', fetchMock)
})

describe('M7 workflow process service', () => {
  it('normalizes ids and amount facts from list and detail', async () => {
    fetchMock
      .mockResolvedValueOnce(
        ok({
          pageNo: 1,
          pageSize: 20,
          total: 1,
          records: [
            {
              id: 10,
              templateCode: 'FLOW',
              templateName: '服务端流程',
              businessType: 'CONTRACT_APPROVAL',
              enabled: 1,
              amountMin: 100.23,
              nodeCount: 1,
            },
          ],
        }),
      )
      .mockResolvedValueOnce(
        ok({
          id: 10,
          templateCode: 'FLOW',
          templateName: '服务端流程',
          businessType: 'CONTRACT_APPROVAL',
          enabled: 1,
          nodeCount: 1,
          nodes: [{ id: 21, templateId: 10, nodeCode: 'N1', nodeName: '审批', nodeOrder: 1 }],
        }),
      )

    const page = await loadWorkflowTemplates({ pageNo: 1, pageSize: 20, enabled: '1' })
    const detail = await loadWorkflowTemplate('10')

    expect(page.records[0]).toMatchObject({ id: '10', amountMin: '100.23' })
    expect(detail.nodes?.[0]).toMatchObject({ id: '21', templateId: '10' })
    expect(fetchMock.mock.calls[0]?.[0]).toBe(
      '/api/workflow/templates?pageNo=1&pageSize=20&enabled=1',
    )
    expect(fetchMock.mock.calls[1]?.[0]).toBe('/api/workflow/templates/10')
  })

  it('uses exact mutation paths and bodies', async () => {
    fetchMock.mockImplementation(() => Promise.resolve(ok(null)))
    const template = {
      templateName: '流程',
      enabled: 1,
      amountMin: '1.00',
      amountMax: null,
      remark: '',
    }
    const node = {
      nodeName: '审批',
      nodeType: 'APPROVAL' as const,
      approveMode: 'SEQUENTIAL' as const,
      approverConfig: '{"type":"USER","userId":1}',
      allowTransfer: 1,
      allowAddSign: 1,
      remark: '',
    }

    await updateWorkflowTemplate('10', template)
    fetchMock.mockResolvedValueOnce(
      ok({ id: 21, templateId: 10, ...node, nodeCode: 'N1', nodeOrder: 1 }),
    )
    await createWorkflowTemplateNode('10', node)
    await updateWorkflowTemplateNode('10', '21', node)
    await reorderWorkflowTemplateNodes('10', ['22', '21'])
    await deleteWorkflowTemplateNode('10', '21')

    expect(fetchMock.mock.calls.map((call) => call[0])).toEqual([
      '/api/workflow/templates/10',
      '/api/workflow/templates/10/nodes',
      '/api/workflow/templates/10/nodes/21',
      '/api/workflow/templates/10/nodes/reorder',
      '/api/workflow/templates/10/nodes/21',
    ])
    expect(JSON.parse(String((fetchMock.mock.calls[3]?.[1] as RequestInit).body))).toEqual({
      nodeIds: ['22', '21'],
    })
  })
})
