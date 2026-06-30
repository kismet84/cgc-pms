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
    expect(source).toContain(
      'const params = { pageNo: pageNo.value, pageNum: pageNo.value, pageSize: pageSize.value }',
    )
    expect(source).toContain('getMyTodos(params)')
    expect(source).toContain('getMyDone(params)')
    expect(source).toContain('getMyCc(params)')
    expect(navigationSource).toContain("'/approval/mine'")
    expect(navigationSource).toContain("label: '我发起'")
  })

  it('uses API total as the visible count source for each approval tab', () => {
    expect(source).toContain('const tabTotals = ref')
    expect(source).toContain('syncActiveTotal(res.total)')
    expect(source).toContain("count: tabTotals.value.todo")
    expect(source).toContain("count: tabTotals.value.done")
    expect(source).toContain("count: tabTotals.value.cc")
    expect(source).toContain("count: tabTotals.value.mine")
    expect(source).toContain("label: '待办任务'")
    expect(source).toContain("label: '已处理记录'")
    expect(source).toContain("label: '抄送记录'")
    expect(source).toContain("label: '发起实例'")
    expect(source).not.toContain('count: todoData.value.length')
    expect(source).not.toContain('count: doneData.value.length')
    expect(source).not.toContain('count: ccData.value.length')
    expect(source).not.toContain('count: mineData.value.length')
  })

  it('uses tab-specific subtitles and empty-state text without changing detail entry', () => {
    expect(source).toContain("return '处理需要您审批的业务单据'")
    expect(source).toContain("return '查看您已处理的审批记录'")
    expect(source).toContain("return '查看抄送给您的业务单据'")
    expect(source).toContain("return '追踪您发起的审批实例'")
    expect(source).toContain("return '暂无待办任务'")
    expect(source).toContain("return '暂无已处理记录'")
    expect(source).toContain("return '暂无抄送记录'")
    expect(source).toContain("return '暂无发起记录'")
    expect(source).toContain('<template #empty>')
    expect(source).toContain('function shouldShowTableEmpty()')
    expect(source).toContain('return total.value === 0 && tableData.value.length === 0')
    expect(source).toContain(
      '<div v-if="shouldShowTableEmpty()" class="lg-empty-text">{{ tableEmptyText() }}</div>',
    )
    expect(source).not.toContain('<div class="lg-empty-text">{{ tableEmptyText() }}</div>')
    expect(source).toContain("handleDetail(row as { instanceId: string })")
  })

  it('filters my initiated instances by server-side instance status', () => {
    expect(source).toContain("const mineStatus = ref('')")
    expect(source).toContain("const mineStatusOptions = [")
    expect(source).toContain("{ label: '全部', value: '' }")
    expect(source).toContain("{ label: '审批中', value: 'RUNNING' }")
    expect(source).toContain("{ label: '已通过', value: 'APPROVED' }")
    expect(source).toContain("{ label: '已驳回', value: 'REJECTED' }")
    expect(source).toContain("{ label: '已撤回', value: 'WITHDRAWN' }")
    expect(source).toContain("Object.assign(params, { instanceStatus: mineStatus.value })")
    expect(source).toContain('function handleMineStatusChange')
    expect(source).toMatch(/function handleMineStatusChange[\s\S]*?pageNo\.value = 1[\s\S]*?fetchData\(\)/)
    expect(source).toContain('<a-segmented')
    expect(source).toContain('v-if="activeTab === \'mine\'"')
  })

  it('localizes withdrawn status and exposes resubmit in embedded detail', () => {
    expect(source).toContain('resubmitInstance')
    expect(source).toContain("WITHDRAWN: { text: '已撤回', color: 'default' }")
    expect(source).toContain('function getInstanceStatusMeta')
    expect(source).toContain('getInstanceStatusMeta(row.instanceStatus)')
    expect(source).not.toContain('{{ row.instanceStatus }}</a-tag>')
    expect(source).toContain('async function handleResubmit()')
    expect(source).toMatch(/handleResubmit[\s\S]*?resubmitInstance\(instanceId\)[\s\S]*?await refreshDetail\(\)/)
    expect(source).toContain("availableActions.includes('resubmit') && !isDetailRunning")
    expect(source).toContain('重新提交')
  })
})
