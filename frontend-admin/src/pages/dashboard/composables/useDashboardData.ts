import { ref, computed, watch, onMounted } from 'vue'
import { message } from 'ant-design-vue'
import { useUserStore } from '@/stores/user'
import { getProjectList } from '@/api/modules/project'
import {
  getProjectManagerView,
  getBusinessManagerView,
  getCostManagerView,
  getFinanceView,
  getManagementView,
  getCostBreakdown,
} from '@/api/modules/dashboard'
import type { ProjectVO } from '@/types/project'
import type {
  BusinessManagerDashboardVO,
  CostBreakdownVO,
  CostManagerDashboardVO,
  DashboardRole,
  FinanceDashboardVO,
  ManagementDashboardVO,
  ProjectManagerDashboardVO,
  SubjectBreakdown,
} from '@/types/dashboard'

export function useDashboardData() {
  const userStore = useUserStore()

  const availableRoles = computed<DashboardRole[]>(() => {
    const perms = userStore.permissions
    const roles: DashboardRole[] = []
    if (userStore.roles.includes('ADMIN')) return ['pm', 'bm', 'cost', 'finance', 'mgmt']
    if (perms.includes('dashboard:project-manager:view')) roles.push('pm')
    if (perms.includes('dashboard:business-manager:view')) roles.push('bm')
    if (perms.includes('dashboard:cost-manager:view')) roles.push('cost')
    if (perms.includes('dashboard:finance:view')) roles.push('finance')
    if (perms.includes('dashboard:management:view')) roles.push('mgmt')
    return roles.length > 0 ? roles : ['pm', 'bm', 'cost', 'finance', 'mgmt']
  })

  const activeRole = ref<DashboardRole>(availableRoles.value[0] ?? 'pm')

  const roleLabel: Record<DashboardRole, string> = {
    pm: '项目总',
    bm: '商务经理',
    cost: '成本经理',
    finance: '财务',
    mgmt: '管理层',
  }

  const projectList = ref<ProjectVO[]>([])
  const selectedProjectId = ref<string | undefined>(undefined)
  const pmData = ref<ProjectManagerDashboardVO | null>(null)
  const bmData = ref<BusinessManagerDashboardVO | null>(null)
  const costData = ref<CostManagerDashboardVO | null>(null)
  const financeData = ref<FinanceDashboardVO | null>(null)
  const mgmtData = ref<ManagementDashboardVO | null>(null)
  const costBreakdown = ref<CostBreakdownVO | null>(null)
  const loading = ref(false)
  const drillSubject = ref<SubjectBreakdown | null>(null)
  const drillVisible = ref(false)
  const drillChildren = ref<SubjectBreakdown[]>([])

  async function fetchProjects() {
    try {
      const res = await getProjectList({ pageNum: 1, pageSize: 50 })
      projectList.value = res.records
    } catch (e: unknown) {
      console.error(e)
      projectList.value = []
    }
  }

  function needsProject(role: DashboardRole) {
    return role !== 'mgmt'
  }

  async function fetchViewData() {
    const pid = selectedProjectId.value || undefined
    loading.value = true
    try {
      switch (activeRole.value) {
        case 'pm':
          pmData.value = await getProjectManagerView(pid)
          break
        case 'bm':
          bmData.value = await getBusinessManagerView(pid)
          break
        case 'cost':
          costData.value = await getCostManagerView(pid)
          if (pid) {
            costBreakdown.value = await getCostBreakdown(pid)
          } else {
            costBreakdown.value = null
          }
          break
        case 'finance':
          financeData.value = await getFinanceView(pid)
          break
        case 'mgmt':
          mgmtData.value = await getManagementView()
          break
      }
    } catch (e: unknown) {
      console.error(e)
      message.error('加载仪表盘数据失败')
    } finally {
      loading.value = false
    }
  }

  watch([activeRole, selectedProjectId], () => {
    fetchViewData()
  })

  function handleBarClick(params: { name?: string }) {
    if (!params.name || !costBreakdown.value) return
    const subs = costBreakdown.value.subjectBreakdowns
    const clicked = subs.find((s) => s.costSubjectName === params.name)
    if (!clicked) return
    drillSubject.value = clicked
    drillChildren.value = subs.filter(
      (s) => s.parentSubjectId === clicked.costSubjectId && s.level === 2,
    )
    drillVisible.value = true
  }

  function closeDrill() {
    drillVisible.value = false
    drillSubject.value = null
    drillChildren.value = []
  }

  onMounted(async () => {
    await fetchProjects()
    if (
      needsProject(activeRole.value) &&
      !selectedProjectId.value &&
      projectList.value.length > 0
    ) {
      selectedProjectId.value = projectList.value[0].id
    }
    await fetchViewData()
  })

  return {
    availableRoles,
    activeRole,
    roleLabel,
    projectList,
    selectedProjectId,
    pmData,
    bmData,
    costData,
    financeData,
    mgmtData,
    costBreakdown,
    loading,
    drillSubject,
    drillVisible,
    drillChildren,
    fetchProjects,
    needsProject,
    handleBarClick,
    closeDrill,
  }
}
