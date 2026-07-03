import { ref, computed, watch, onMounted } from 'vue'
import { message } from 'ant-design-vue'
import { useUserStore } from '@/stores/user'
import { getProjectList } from '@/api/modules/project'
import {
  getProjectManagerView,
  getBusinessManagerView,
  getCostManagerView,
  getPurchaseManagerView,
  getProductionManagerView,
  getChiefEngineerView,
  getFinanceView,
  getManagementView,
  getCostBreakdown,
} from '@/api/modules/dashboard'
import type { ProjectVO } from '@/types/project'
import type {
  BusinessManagerDashboardVO,
  ChiefEngineerDashboardVO,
  CostBreakdownVO,
  CostManagerDashboardVO,
  DashboardRole,
  FinanceDashboardVO,
  ManagementDashboardVO,
  ProjectManagerDashboardVO,
  PurchaseManagerDashboardVO,
  ProductionManagerDashboardVO,
  SubjectBreakdown,
} from '@/types/dashboard'

export function formatDashboardMonth(date: Date) {
  const year = date.getFullYear()
  const month = String(date.getMonth() + 1).padStart(2, '0')
  return `${year}-${month}`
}

export const ALL_DASHBOARD_MONTH = ''
export const ALL_PROJECT_ID = '__ALL__'

export function buildDashboardMonthOptions(now: Date = new Date()) {
  return [
    { value: ALL_DASHBOARD_MONTH, label: '全部' },
    ...Array.from({ length: 12 }, (_, index) => {
      const month = new Date(now.getFullYear(), now.getMonth() - index, 1)
      const value = formatDashboardMonth(month)
      return { value, label: value }
    }),
  ]
}

export function useDashboardData() {
  const userStore = useUserStore()

  const availableRoles = computed<DashboardRole[]>(() => {
    const perms = userStore.permissions
    const roles: DashboardRole[] = []
    if (userStore.roles.some((role) => role === 'ADMIN' || role === 'SUPER_ADMIN')) {
      return ['pm', 'bm', 'cost', 'purchase', 'production', 'chiefEngineer', 'finance', 'mgmt']
    }
    if (perms.includes('dashboard:project-manager:view')) roles.push('pm')
    if (perms.includes('dashboard:business-manager:view')) roles.push('bm')
    if (perms.includes('dashboard:cost-manager:view')) roles.push('cost')
    if (perms.includes('dashboard:purchase-manager:view')) roles.push('purchase')
    if (perms.includes('dashboard:production-manager:view')) roles.push('production')
    if (perms.includes('dashboard:chief-engineer:view')) roles.push('chiefEngineer')
    if (perms.includes('dashboard:finance:view')) roles.push('finance')
    if (perms.includes('dashboard:management:view')) roles.push('mgmt')
    return roles.length > 0
      ? roles
      : ['pm', 'bm', 'cost', 'purchase', 'production', 'chiefEngineer', 'finance', 'mgmt']
  })

  const initialRole: DashboardRole = availableRoles.value.includes('cost')
    ? 'cost'
    : (availableRoles.value[0] ?? 'pm')
  const activeRole = ref<DashboardRole>(initialRole)

  const roleLabel: Record<DashboardRole, string> = {
    pm: '项目经理',
    bm: '商务经理',
    cost: '商务经理',
    purchase: '采购经理',
    production: '生产经理',
    chiefEngineer: '总工程师',
    finance: '财务',
    mgmt: '管理层',
  }

  const projectList = ref<ProjectVO[]>([])
  const selectedProjectId = ref<string | undefined>(ALL_PROJECT_ID)
  const selectedMonth = ref(ALL_DASHBOARD_MONTH)
  const monthOptions = computed(() => buildDashboardMonthOptions())
  const pmData = ref<ProjectManagerDashboardVO | null>(null)
  const bmData = ref<BusinessManagerDashboardVO | null>(null)
  const costData = ref<CostManagerDashboardVO | null>(null)
  const purchaseData = ref<PurchaseManagerDashboardVO | null>(null)
  const productionData = ref<ProductionManagerDashboardVO | null>(null)
  const chiefEngineerData = ref<ChiefEngineerDashboardVO | null>(null)
  const financeData = ref<FinanceDashboardVO | null>(null)
  const mgmtData = ref<ManagementDashboardVO | null>(null)
  const costBreakdown = ref<CostBreakdownVO | null>(null)
  const loading = ref(false)
  const bootstrapping = ref(true)
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
    const pid = selectedProjectId.value === ALL_PROJECT_ID ? undefined : selectedProjectId.value
    const month = selectedMonth.value || undefined
    loading.value = true
    try {
      switch (activeRole.value) {
        case 'pm':
          pmData.value = await getProjectManagerView(pid, month)
          break
        case 'bm':
          bmData.value = await getBusinessManagerView(pid)
          break
        case 'cost':
          costData.value = await getCostManagerView(pid, month)
          if (pid) {
            costBreakdown.value = await getCostBreakdown(pid)
          } else {
            costBreakdown.value = null
          }
          break
        case 'purchase':
          purchaseData.value = await getPurchaseManagerView(pid, month)
          break
        case 'production':
          productionData.value = await getProductionManagerView(pid, month)
          break
        case 'chiefEngineer':
          chiefEngineerData.value = await getChiefEngineerView(pid, month)
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

  watch([activeRole, selectedProjectId, selectedMonth], () => {
    if (bootstrapping.value) return
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
    await Promise.allSettled([fetchProjects(), fetchViewData()])
    bootstrapping.value = false
  })

  return {
    availableRoles,
    activeRole,
    roleLabel,
    projectList,
    selectedProjectId,
    selectedMonth,
    monthOptions,
    pmData,
    bmData,
    costData,
    purchaseData,
    productionData,
    chiefEngineerData,
    financeData,
    mgmtData,
    costBreakdown,
    loading,
    drillSubject,
    drillVisible,
    drillChildren,
    fetchProjects,
    fetchViewData,
    needsProject,
    handleBarClick,
    closeDrill,
  }
}
