import { describe, expect, it } from 'vitest'
import { readFileSync } from 'node:fs'
import { dirname, resolve } from 'node:path'
import { fileURLToPath } from 'node:url'

const currentDir = dirname(fileURLToPath(import.meta.url))
const source = readFileSync(resolve(currentDir, '../todo.vue'), 'utf-8')
const helperSource = readFileSync(resolve(currentDir, '../workflowDisplay.ts'), 'utf-8')
const workflowApiSource = readFileSync(
  resolve(currentDir, '../../../api/modules/workflow.ts'),
  'utf-8',
)
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
    expect(helperSource).toMatch(/PAY_APPLICATION:\s*'付款申请'/)
    expect(helperSource).toContain("businessType: 'CONTRACT_APPROVAL'")
    expect(helperSource).toContain("displayName: '合同审批'")
    expect(helperSource).toContain('getWorkflowBusinessTypeLabel')
    expect(helperSource).toContain("return workflowBusinessTypeLabels[key] ?? '未知业务类型'")
    expect(source).toContain('function businessTypeLabel')
    expect(source).toContain('return getWorkflowBusinessTypeLabel(value)')
    expect(source).toContain('businessTypeLabel(row.businessType)')
    expect(source).not.toMatch(
      /businessTypeMap\[row\.businessType as string\][\s\S]*row\.businessType as string/,
    )
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
    expect(source).toContain('handleDetail(row as { instanceId: string })')
  })

  it('keeps existing approval work entries alongside my initiated entry', () => {
    expect(source).toContain("{ key: 'todo', label: '我的待办' }")
    expect(source).toContain("{ key: 'done', label: '我的已办' }")
    expect(source).toContain("{ key: 'cc', label: '抄送我的' }")
    expect(source).toContain('const params: PageParams = {')
    expect(source).toContain('getMyTodos(params)')
    expect(source).toContain('getMyDone(params)')
    expect(source).toContain('getMyCc(params)')
    expect(navigationSource).toContain("'/approval/mine'")
    expect(navigationSource).toContain("key: '/workflow-system'")
  })

  it('uses API total as the visible count source for each approval tab', () => {
    expect(source).toContain('const tabTotals = ref')
    expect(source).toContain('syncActiveTotal(res.total)')
    expect(source).toContain('count: tabTotals.value.todo')
    expect(source).toContain('count: tabTotals.value.done')
    expect(source).toContain('count: tabTotals.value.cc')
    expect(source).toContain('count: tabTotals.value.mine')
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
    expect(source).toContain('const hasLoaded = ref(false)')
    expect(source).toContain('const listError = ref<string | null>(null)')
    expect(source).toContain('const showEmptyState = computed(')
    expect(source).toContain(
      '<a-result status="error" title="审批列表加载失败" :sub-title="listError">',
    )
    expect(source).toContain('<LgEmptyState :description="tableEmptyText()">')
    expect(source).toContain(
      '<a-button v-if="hasActiveFilters" @click="handleFilterReset">清空筛选</a-button>',
    )
    expect(source).toContain('handleDetail(row as { instanceId: string })')
  })

  it('filters approval tabs with server-side query params', () => {
    expect(source).toContain("from '@/composables/listPageQuery'")
    expect(source).toContain('readPositiveIntQuery')
    expect(source).toContain('readStringQuery')
    expect(source).toContain('replaceListQuery')
    expect(source).toContain("const filterKeyword = ref('')")
    expect(source).toContain("const filterBusinessType = ref('')")
    expect(source).toContain("const filterInstanceStatus = ref('')")
    expect(source).toContain('const filterTimeRange = ref')
    expect(source).toContain('function hydrateFromRouteQuery()')
    expect(source).toContain("filterKeyword.value = readStringQuery(route.query.keyword) ?? ''")
    expect(source).toContain('pageNo.value = readPositiveIntQuery(route.query.pageNo, 1)')
    expect(source).toContain('pageSize.value = readPositiveIntQuery(route.query.pageSize, 20)')
    expect(source).toContain('async function syncRouteQuery()')
    expect(source).toContain('await router.replace({ path: route.path, query: nextQuery })')
    expect(source).toContain('function buildQueryParams()')
    expect(source).toContain('if (keyword) params.keyword = keyword')
    expect(source).toContain(
      'if (filterBusinessType.value) params.businessType = filterBusinessType.value',
    )
    expect(source).toContain(
      'if (filterInstanceStatus.value) params.instanceStatus = filterInstanceStatus.value',
    )
    expect(source).toContain(
      "params.startTime = filterTimeRange.value[0].startOf('day').format('YYYY-MM-DD HH:mm:ss')",
    )
    expect(source).toContain(
      "params.endTime = filterTimeRange.value[1].endOf('day').format('YYYY-MM-DD HH:mm:ss')",
    )
    expect(source).toContain('const params = buildQueryParams()')
    expect(source).toContain('getMyTodos(params)')
    expect(source).toContain('getMyDone(params)')
    expect(source).toContain('getMyCc(params)')
    expect(source).toContain('getMyInitiatedInstances(params)')
    expect(source).toMatch(/fetchData\(\) \{\s*listError\.value = null\s*await syncRouteQuery\(\)/)
    expect(source).toContain('function handleFilterSearch')
    expect(source).toMatch(
      /function handleFilterSearch[\s\S]*?pageNo\.value = 1[\s\S]*?fetchData\(\)/,
    )
    expect(source).toContain('function handleFilterReset')
    expect(source).toContain('router.push({')
    expect(source).toContain('path: `/approval/${key}`')
    expect(source).toContain('<a-input')
    expect(source).toContain('<a-range-picker v-model:value="filterTimeRange"')
    expect(source).not.toContain('tableData.value.filter')
  })

  it('limits approval filter options to the three core workflow business types', () => {
    expect(helperSource).toContain(
      'export const coreBusinessTypeOptions = workflowBusinessEntryRegistry',
    )
    expect(helperSource).toContain(".filter((entry) => entry.businessType !== 'CONTRACT')")
    expect(helperSource).toContain(
      '.map((entry) => ({ label: entry.displayName, value: entry.businessType }))',
    )
    expect(source).toContain(
      "const businessTypeFilterOptions = [{ label: '全部业务', value: '' }, ...coreBusinessTypeOptions]",
    )
    expect(source).toContain('const statusFilterOptions = [')
    expect(source).toContain("{ label: '全部', value: '' }")
    expect(helperSource).toContain("export const WF_INSTANCE_RUNNING = 'RUNNING'")
    expect(helperSource).toContain("export const WF_INSTANCE_APPROVED = 'APPROVED'")
    expect(helperSource).toContain("export const WF_INSTANCE_REJECTED = 'REJECTED'")
    expect(helperSource).toContain("export const WF_INSTANCE_WITHDRAWN = 'WITHDRAWN'")
    expect(helperSource).toContain("{ label: '审批中', value: WF_INSTANCE_RUNNING }")
    expect(helperSource).toContain("{ label: '已通过', value: WF_INSTANCE_APPROVED }")
    expect(helperSource).toContain("{ label: '已驳回', value: WF_INSTANCE_REJECTED }")
    expect(helperSource).toContain("{ label: '已撤回', value: WF_INSTANCE_WITHDRAWN }")
    expect(source).not.toContain('mineStatus')
    expect(source).not.toContain('<a-segmented')
  })

  it('localizes withdrawn status and exposes resubmit in embedded detail', () => {
    expect(source).toContain('resubmitInstance')
    expect(helperSource).toContain("[WF_INSTANCE_WITHDRAWN]: { text: '已撤回', color: 'default' }")
    expect(source).toContain('function getInstanceStatusMeta')
    expect(source).toContain('return getWorkflowInstanceStatusMeta(status)')
    expect(source).toContain('getInstanceStatusMeta(row.instanceStatus)')
    expect(source).not.toContain('{{ row.instanceStatus }}</a-tag>')
    expect(source).toContain('async function handleResubmit()')
    expect(source).toMatch(
      /handleResubmit[\s\S]*?resubmitInstance\(instanceId\)[\s\S]*?await refreshDetail\(\)/,
    )
    expect(source).toContain(
      "availableActions.includes('resubmit') && canShowInitiatorActions() && !isDetailRunning",
    )
    expect(source).toContain('重新提交')
  })

  it('keeps embedded detail actions read-only for done and cc tabs', () => {
    expect(source).toContain('function canShowApprovalActions()')
    expect(source).toContain("return activeTab.value === 'todo' && isDetailRunning.value")
    expect(source).toContain('function canShowInitiatorActions()')
    expect(source).toContain("return activeTab.value === 'mine'")
    expect(source).toContain('v-if="availableActions.length > 0 && canShowApprovalActions()"')
    expect(source).toContain(
      'v-if="availableActions.includes(\'withdraw\') && canShowInitiatorActions()"',
    )
    expect(source).toContain(
      'v-if="availableActions.includes(\'resubmit\') && canShowInitiatorActions() && !isDetailRunning"',
    )
  })

  it('shows embedded business document entry only for supported workflow business types', () => {
    expect(helperSource).toContain('export const workflowBusinessEntryRegistry')
    expect(helperSource).toContain("businessType: 'CONTRACT_APPROVAL'")
    expect(helperSource).toContain("displayName: '合同审批'")
    expect(helperSource).toContain("permissionCode: 'contract:query'")
    expect(helperSource).toContain("businessType: 'PURCHASE_REQUEST'")
    expect(helperSource).toContain("displayName: '采购申请'")
    expect(helperSource).toContain("permissionCode: 'purchase:request:list'")
    expect(helperSource).toContain("businessType: 'SUB_MEASURE'")
    expect(helperSource).toContain("displayName: '分包计量'")
    expect(helperSource).toContain("permissionCode: 'subcontract:measure:query'")
    expect(helperSource).toContain("openMode: 'route'")
    expect(helperSource).toContain("forbiddenPolicy: 'disabled-with-tooltip'")
    expect(helperSource).toContain('targetRoute:')
    expect(helperSource).toContain('export function getWorkflowBusinessEntry')
    expect(source).toContain('function businessEntryPath')
    expect(source).toContain('return getWorkflowBusinessEntryPath(record)')
    expect(source).toContain('useUserStore')
    expect(source).toContain('const userStore = useUserStore()')
    expect(source).toContain('function canOpenBusinessEntry')
    expect(source).toContain(
      'return canAccessWorkflowBusinessEntry(record, userStore.hasPermission, userStore.roles)',
    )
    expect(helperSource).toContain('targetRoute: (businessId: string) => `/contract/${businessId}`')
    expect(helperSource).toContain(
      'targetRoute: (businessId: string) => `/inventory/purchase-request?businessId=${businessId}`',
    )
    expect(helperSource).toContain(
      'targetRoute: (businessId: string) => `/subcontract/measure?businessId=${businessId}`',
    )
    expect(helperSource).toContain('export function getWorkflowBusinessEntryPermission')
    expect(helperSource).toContain('export function canAccessWorkflowBusinessEntry')
    expect(helperSource).toContain("roles.includes('ADMIN') || roles.includes('SUPER_ADMIN')")
    expect(source).toContain('function openBusinessEntry')
    expect(source).toMatch(
      /function openBusinessEntry[\s\S]*?if \(!canOpenBusinessEntry\(record\)\) return/,
    )
    expect(source).toContain('router.push(path)')
    expect(source).toContain('v-if="businessEntryPath(detail)"')
    expect(source).toContain('无权访问该业务单据')
    expect(source).toContain(':disabled="!canOpenBusinessEntry(detail)"')
    expect(source).toContain('查看业务单据')
  })
})
