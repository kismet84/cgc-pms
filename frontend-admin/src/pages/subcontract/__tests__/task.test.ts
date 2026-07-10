import { describe, expect, it } from 'vitest'
import { readFileSync } from 'node:fs'
import { dirname, resolve } from 'node:path'
import { fileURLToPath } from 'node:url'

const currentDir = dirname(fileURLToPath(import.meta.url))
const source = readFileSync(resolve(currentDir, '../task.vue'), 'utf-8')
const configSource = readFileSync(resolve(currentDir, '../pageConfig.ts'), 'utf-8')

describe('subcontract task page quality guardrails', () => {
  it('extracts static status and grid config out of the giant component', () => {
    expect(source).toContain("from './pageConfig'")
    expect(configSource).toContain('export const SUBCONTRACT_TASK_STATUS_LABEL')
    expect(configSource).toContain('export const SUBCONTRACT_TASK_STATUS_COLOR')
    expect(configSource).toContain('export const SUBCONTRACT_TASK_GRID_COLUMNS')
  })

  it('keeps filters and pagination in route query for refresh persistence', () => {
    expect(source).toContain("import { useRoute, useRouter } from 'vue-router'")
    expect(source).toContain('const route = useRoute()')
    expect(source).toContain('readStringQuery(route.query.projectId)')
    expect(source).toContain('readStringQuery(route.query.status)')
    expect(source).toContain('readPositiveIntQuery(route.query.pageNo, 1)')
    expect(source).toContain('await router.replace({ path: route.path, query: nextQuery })')
  })

  it('renders explicit error and empty states with retry entry', () => {
    expect(source).toContain('const listError = ref<string | null>(null)')
    expect(source).toContain('const hasLoaded = ref(false)')
    expect(source).toContain('<a-result status="error" title="分包任务列表加载失败" :sub-title="listError">')
    expect(source).toContain('<LgEmptyState description="暂无符合条件的分包任务">')
    expect(source).toContain('@click="fetchData"')
  })

  it('derives a read-only WBS and gantt overview from current table data', () => {
    expect(source).toContain('const wbsTimelineRows = computed(() => {')
    expect(source).toContain('[...tableData.value].sort')
    expect(source).toContain('按 WBS 编码排序的平铺展示')
    expect(source).toContain('项目内 WBS 树与只读甘特展示')
    expect(source).toContain('暂无任务可生成 WBS/甘特概览')
  })

  it('shows required WBS fields and degrades missing plan dates explicitly', () => {
    expect(source).toContain("{{ item.row.taskCode || '-' }}")
    expect(source).toContain("{{ item.row.taskName || '-' }}")
    expect(source).toContain("{{ item.row.plannedStartDate || '未设置计划日期' }}")
    expect(source).toContain("{{ item.row.actualStartDate || '-' }}")
    expect(source).toContain(":percent=\"parseFloat(item.row.progressPercent || '0') || 0\"")
    expect(source).toContain('STATUS_LABEL[item.row.status]')
  })

  it('keeps gantt scope minimal without drag, dependency lines, or gantt libraries', () => {
    expect(source).toContain('function clampPercent(value: number)')
    expect(source).toContain("left: item.left + '%'")
    expect(source).toContain("width: item.width + '%'")
    expect(source).not.toMatch(/dhtmlx|frappe-gantt|gantt-task-react|gantt-elastic/i)
    expect(source).not.toMatch(/draggable|dragstart|dependency|dependencies|linkLine|依赖线/)
  })
})
