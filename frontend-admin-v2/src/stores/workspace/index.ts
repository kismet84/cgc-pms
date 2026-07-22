import { computed, ref } from 'vue'
import { defineStore } from 'pinia'
import { buildDashboardReportPeriods } from '@cgc-pms/frontend-contracts'
import type { LocationQuery, RouteParamsGeneric } from 'vue-router'
import { loadVisibleProjects } from '@/services/projects'
import { navigationDomains } from '@/navigation/catalog'
import { registerSessionCacheClearer } from '@/stores/session'

export interface ContextOption {
  value: string
  label: string
  status?: string
}

export interface ObjectContext {
  kind: 'project' | 'contract' | 'settlement'
  id: string
}

const projectContextPermissions = new Set(
  navigationDomains.flatMap((domain) =>
    domain.workspaces.flatMap((workspace) =>
      workspace.tabs
        .filter((tab) => tab.workspaceContext?.project && tab.permission)
        .map((tab) => tab.permission!),
    ),
  ),
)

function queryValue(value: LocationQuery[string]): string | null {
  return typeof value === 'string' && value.trim() ? value : null
}

function paramValue(value: RouteParamsGeneric[string]): string | null {
  if (typeof value === 'string' && value.trim()) return value
  if (Array.isArray(value)) return value.find((item) => item.trim()) ?? null
  return null
}

export const useWorkspaceStore = defineStore('v2-workspace', () => {
  const projects = ref<ContextOption[]>([])
  const reportPeriods = ref<ContextOption[]>([])
  const requestedProjectId = ref<string | null>(null)
  const requestedReportPeriod = ref<string | null>(null)
  const objectContext = ref<ObjectContext | null>(null)
  let contextLoadGeneration = 0
  let contextLoadController: AbortController | null = null

  const selectedProjectId = computed(() =>
    projects.value.some((item) => item.value === requestedProjectId.value)
      ? requestedProjectId.value
      : null,
  )
  const selectedReportPeriod = computed(() =>
    reportPeriods.value.some((item) => item.value === requestedReportPeriod.value)
      ? requestedReportPeriod.value
      : null,
  )

  function setProjects(options: ContextOption[]): void {
    projects.value = [...options]
    if (
      requestedProjectId.value &&
      !projects.value.some((item) => item.value === requestedProjectId.value)
    ) {
      requestedProjectId.value = null
    }
  }

  function setReportPeriods(options: ContextOption[]): void {
    reportPeriods.value = [...options]
    if (
      requestedReportPeriod.value &&
      !reportPeriods.value.some((item) => item.value === requestedReportPeriod.value)
    ) {
      requestedReportPeriod.value = null
    }
  }

  function selectProject(value: string): void {
    requestedProjectId.value = projects.value.some((item) => item.value === value) ? value : null
  }

  function selectReportPeriod(value: string): void {
    requestedReportPeriod.value = reportPeriods.value.some((item) => item.value === value)
      ? value
      : null
  }

  function syncRoute(path: string, query: LocationQuery, params: RouteParamsGeneric): void {
    if (Object.hasOwn(query, 'projectId')) requestedProjectId.value = queryValue(query.projectId)
    if (Object.hasOwn(query, 'period')) requestedReportPeriod.value = queryValue(query.period)

    const projectId = paramValue(params.projectId)
    const contractId = paramValue(params.id)
    if (projectId && path.startsWith('/project/')) {
      objectContext.value = { kind: 'project', id: projectId }
    } else if (contractId && path.startsWith('/contract/')) {
      objectContext.value = { kind: 'contract', id: contractId }
    } else if (contractId && path.startsWith('/settlement/')) {
      objectContext.value = { kind: 'settlement', id: contractId }
    } else {
      objectContext.value = null
    }
  }

  async function initialize(roles: string[], permissions: string[]): Promise<void> {
    const generation = ++contextLoadGeneration
    contextLoadController?.abort()
    setReportPeriods(buildDashboardReportPeriods())

    const canLoadProjectContext =
      roles.some((role) => role === 'ADMIN' || role === 'SUPER_ADMIN') ||
      permissions.includes('*') ||
      permissions.some((permission) => projectContextPermissions.has(permission))
    if (!canLoadProjectContext) {
      setProjects([])
      return
    }

    const controller = new AbortController()
    contextLoadController = controller
    try {
      const visibleProjects = await loadVisibleProjects(controller.signal)
      if (generation !== contextLoadGeneration) return
      setProjects(
        visibleProjects.map((project) => ({
          value: project.id,
          label: project.projectName,
          status: project.status,
        })),
      )
    } finally {
      if (contextLoadController === controller) contextLoadController = null
    }
  }

  function clear(): void {
    contextLoadGeneration += 1
    contextLoadController?.abort()
    contextLoadController = null
    projects.value = []
    reportPeriods.value = []
    requestedProjectId.value = null
    requestedReportPeriod.value = null
    objectContext.value = null
  }

  registerSessionCacheClearer(clear)

  return {
    projects,
    reportPeriods,
    selectedProjectId,
    selectedReportPeriod,
    objectContext,
    setProjects,
    setReportPeriods,
    selectProject,
    selectReportPeriod,
    syncRoute,
    initialize,
    clear,
  }
})
