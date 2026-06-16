import { describe, expect, it } from 'vitest'
import { readFileSync } from 'node:fs'
import { dirname, resolve } from 'node:path'
import { fileURLToPath } from 'node:url'

const currentDir = dirname(fileURLToPath(import.meta.url))
const pageSource = readFileSync(resolve(currentDir, '../process.vue'), 'utf-8')
const apiSource = readFileSync(resolve(currentDir, '../../../api/modules/workflow.ts'), 'utf-8')

describe('ApprovalProcessManagement source wiring', () => {
  it('renders approval process management copy and node operations', () => {
    expect(pageSource).toContain('审批流程管理')
    expect(pageSource).toContain('流程模板')
    expect(pageSource).toContain('新增节点')
    expect(pageSource).toContain('删除节点')
    expect(pageSource).toContain('节点顺序')
    expect(pageSource).toContain('审批人配置')
  })

  it('loads template list and detail through workflow APIs', () => {
    expect(pageSource).toMatch(/getWorkflowTemplates\(/)
    expect(pageSource).toMatch(/getWorkflowTemplateDetail\(/)
    expect(pageSource).toMatch(/updateWorkflowTemplate\(/)
  })

  it('wires node create update delete and reorder APIs', () => {
    expect(pageSource).toMatch(/createWorkflowTemplateNode\(/)
    expect(pageSource).toMatch(/updateWorkflowTemplateNode\(/)
    expect(pageSource).toMatch(/deleteWorkflowTemplateNode\(/)
    expect(pageSource).toMatch(/reorderWorkflowTemplateNodes\(/)
    expect(pageSource).toMatch(/Modal\.confirm\(/)
  })

  it('exports workflow template API helpers', () => {
    for (const helper of [
      'getWorkflowTemplates',
      'getWorkflowTemplateDetail',
      'updateWorkflowTemplate',
      'createWorkflowTemplateNode',
      'updateWorkflowTemplateNode',
      'deleteWorkflowTemplateNode',
      'reorderWorkflowTemplateNodes',
    ]) {
      expect(apiSource).toContain(`function ${helper}`)
    }
  })
})
