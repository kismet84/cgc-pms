import { describe, expect, it } from 'vitest'
import { readFileSync } from 'node:fs'
import { dirname, resolve } from 'node:path'
import { fileURLToPath } from 'node:url'

const currentDir = dirname(fileURLToPath(import.meta.url))
const source = readFileSync(resolve(currentDir, '../todo.vue'), 'utf-8')
const workflowApiSource = readFileSync(resolve(currentDir, '../../../api/modules/workflow.ts'), 'utf-8')
const navigationSource = readFileSync(resolve(currentDir, '../../../router/navigation.ts'), 'utf-8')

describe('approval work list route titles', () => {
  it('renders breadcrumb and subtitle from the active approval tab', () => {
    expect(source).toMatch(/<a-breadcrumb-item[^>]*>审批中心<\/a-breadcrumb-item>/)
    expect(source).toMatch(
      /<a-breadcrumb-item[^>]*>\{\{ pageHeaderTitle\(\) \}\}<\/a-breadcrumb-item>/,
    )
    expect(source).toMatch(/<p[^>]*>\s*\{\{ pageHeaderSubtitle\(\) \}\}[\s\S]*?<\/p>/)
  })

  it('maps payment application business type to Chinese display text', () => {
    expect(source).toMatch(/PAY_APPLICATION:\s*'付款申请'/)
    expect(source).toMatch(/businessTypeMap\[row\.businessType as string\]/)
  })

  it('wires my initiated tab to the mine instance API and tracking columns', () => {
    expect(source).toContain("{ key: 'mine', label: '我发起' }")
    expect(source).toContain('getMyInitiatedInstances')
    expect(source).toContain('mineData.value = res.records')
    expect(workflowApiSource).toContain("url: '/workflow/instances/mine'")
    expect(source).toContain("field: 'businessType'")
    expect(source).toContain("field: 'title'")
    expect(source).toContain("field: 'instanceStatus'")
    expect(source).toContain("field: 'createdAt'")
    expect(source).toContain("field: 'updatedAt'")
    expect(source).toContain("field: 'currentNodeName'")
    expect(source).toContain("handleDetail(row as { instanceId: string })")
  })

  it('keeps existing approval work entries alongside my initiated entry', () => {
    expect(source).toContain("{ key: 'todo', label: '我的待办' }")
    expect(source).toContain("{ key: 'done', label: '我的已办' }")
    expect(source).toContain("{ key: 'cc', label: '抄送我的' }")
    expect(source).toContain('getMyTodos(params)')
    expect(source).toContain('getMyDone(params)')
    expect(source).toContain('getMyCc(params)')
    expect(navigationSource).toContain("'/approval/mine'")
    expect(navigationSource).toContain("label: '我发起'")
  })
})
