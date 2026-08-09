import { computed, ref } from 'vue'
import { defineStore } from 'pinia'
import { buildDashboardReportPeriods } from '@cgc-pms/frontend-contracts'
import type { LocationQuery, RouteParamsGeneric } from 'vue-router'
import { loadVisibleProjects } from '@/services/projects'
import { getSessionNamespaceIdentity, registerSessionCacheClearer } from '@/stores/session'

const RECENT_PAGE_LIMIT = 8
const RECENT_PAGE_STORAGE_PREFIX = 'cgc-pms-recent-pages'

export interface ContextOption {
  value: string
  label: string
  status?: string
}

export interface ObjectContext {
  kind: 'project' | 'contract' | 'settlement'
  id: string
}

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
  const recentPaths = ref<string[]>([])
  const requestedProjectId = ref<string | null>(null)
  const requestedReportPeriod = ref<string | null>(null)
  const objectContext = ref<ObjectContext | null>(null)
  let contextLoadGeneration = 0
  let contextLoadController: AbortController | null = null
  let recentStorageKey: string | null = null

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

  function loadRecentPaths(): void {
    const identity = getSessionNamespaceIdentity()
    const key = identity
      ? `${RECENT_PAGE_STORAGE_PREFIX}:${identity.tenantId}:${identity.userId}`
      : null
    if (key === recentStorageKey) return
    recentStorageKey = key
    recentPaths.value = key ? readRecentPaths(key) : []
  }

  function recordRecentPath(path: string): void {
    if (!path.startsWith('/')) return
    loadRecentPaths()
    if (!recentStorageKey || recentPaths.value[0] === path) return
    recentPaths.value = [path, ...recentPaths.value.filter((item) => item !== path)].slice(
      0,
      RECENT_PAGE_LIMIT,
    )
    try {
      window.localStorage.setItem(recentStorageKey, JSON.stringify(recentPaths.value))
    } catch {
      // 存储不可用时保留当前窗口内的最近记录。
    }
  }

  async function initialize(): Promise<void> {
    const generation = ++contextLoadGeneration
    contextLoadController?.abort()
    setReportPeriods(buildDashboardReportPeriods())

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
    recentPaths.value = []
    recentStorageKey = null
    requestedProjectId.value = null
    requestedReportPeriod.value = null
    objectContext.value = null
  }

  registerSessionCacheClearer(clear)

  return {
    projects,
    reportPeriods,
    recentPaths,
    selectedProjectId,
    selectedReportPeriod,
    objectContext,
    setProjects,
    setReportPeriods,
    selectProject,
    selectReportPeriod,
    syncRoute,
    loadRecentPaths,
    recordRecentPath,
    initialize,
    clear,
  }
})

function readRecentPaths(key: string): string[] {
  try {
    const value = JSON.parse(window.localStorage.getItem(key) || '[]') as unknown
    if (!Array.isArray(value)) return []
    return value
      .filter((item): item is string => typeof item === 'string' && item.startsWith('/'))
      .filter((item, index, items) => items.indexOf(item) === index)
      .slice(0, RECENT_PAGE_LIMIT)
  } catch {
    return []
  }
}
