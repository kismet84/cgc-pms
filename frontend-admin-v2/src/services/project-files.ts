import type { PageResult } from '@cgc-pms/frontend-contracts'
import { apiRequest } from '@/services/request'

export type ProjectFilePreviewStatus = 'PENDING' | 'PROCESSING' | 'READY' | 'FAILED' | 'UNSUPPORTED'

export interface ProjectFileVersion {
  id: string
  versionNo: number
  sysFileId: string
  submitterName?: string | null
  createdByName?: string | null
  createdBy?: string | null
  createdAt: string
  virusScanStatus: string
  previewStatus: ProjectFilePreviewStatus
}

export interface ProjectFileRecord {
  id: string
  projectId: string
  projectName?: string | null
  fileCode: string
  displayName: string
  categoryCode: string
  categoryName?: string | null
  sourceKind: 'MANAGED' | 'BUSINESS'
  maintainMode: 'MANAGED' | 'READ_ONLY'
  sourceHint?: string | null
  sourceRoute?: string | null
  versions: ProjectFileVersion[]
}

export interface ProjectFileQuery {
  pageNo?: number
  pageSize?: number
  projectId?: string
  keyword?: string
  categoryCode?: string
}

export interface ProjectFilePreview {
  status: ProjectFilePreviewStatus
  url?: string | null
  errorCode?: string | null
  message?: string | null
  retryAfterSeconds?: number | null
}

function queryString(query: ProjectFileQuery): string {
  const params = new URLSearchParams()
  for (const [key, value] of Object.entries(query)) {
    if (value === undefined || value === null || value === '') continue
    params.set(key, typeof value === 'string' ? value.trim() : String(value))
  }
  return params.toString()
}

function requiredId(value: string): string {
  const id = value.trim()
  if (!id) throw new TypeError('文件ID不能为空')
  return encodeURIComponent(id)
}

export function loadProjectFiles(
  query: ProjectFileQuery,
  signal?: AbortSignal,
): Promise<PageResult<ProjectFileRecord>> {
  const encoded = queryString(query)
  return apiRequest(`/project-files${encoded ? `?${encoded}` : ''}`, { signal })
}

export function createProjectFile(command: {
  projectId: string
  name: string
  categoryCode: string
  file: File
}): Promise<ProjectFileRecord> {
  const body = new FormData()
  body.append('projectId', command.projectId)
  body.append('name', command.name.trim())
  body.append('categoryCode', command.categoryCode)
  body.append('file', command.file)
  return apiRequest('/project-files', { method: 'POST', body })
}

export function addProjectFileVersion(catalogId: string, file: File): Promise<ProjectFileRecord> {
  const body = new FormData()
  body.append('file', file)
  return apiRequest(`/project-files/${requiredId(catalogId)}/versions`, { method: 'POST', body })
}

export function requestProjectFilePreview(
  versionId: string,
  signal?: AbortSignal,
): Promise<ProjectFilePreview> {
  return apiRequest(`/project-files/versions/${requiredId(versionId)}/preview`, {
    method: 'POST',
    signal,
  })
}

export function getProjectFileDownloadUrl(sysFileId: string): Promise<string> {
  return apiRequest(`/files/${requiredId(sysFileId)}/url`)
}
