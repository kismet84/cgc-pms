import {
  PROJECT_API,
  type DictionaryItem,
  type PageResult,
  type ProjectListItem,
  type ProjectMember,
  type ProjectMemberQuery,
  type ProjectOverview,
  type ProjectQuery,
  type ProjectRecord,
  type ProjectMemberCommand,
  type ProjectStatusCommand,
  type ProjectUpsertCommand,
  type ProjectUserOption,
} from '@cgc-pms/frontend-contracts'
import { apiRequest } from '@/services/request'

const PROJECT_PAGE_SIZE = 200

export function loadProjectPage(
  query: ProjectQuery = {},
  signal?: AbortSignal,
): Promise<PageResult<ProjectRecord>> {
  return apiRequest<PageResult<ProjectRecord>>(withQuery(PROJECT_API.list, query), { signal })
}

export function loadProject(projectId: string, signal?: AbortSignal): Promise<ProjectRecord> {
  return apiRequest<ProjectRecord>(PROJECT_API.detail(requiredId(projectId)), { signal })
}

export function loadProjectOverview(
  projectId: string,
  signal?: AbortSignal,
): Promise<ProjectOverview> {
  return apiRequest<ProjectOverview>(PROJECT_API.overview(requiredId(projectId)), { signal })
}

export function loadProjectMembers(
  projectId: string,
  query: ProjectMemberQuery = {},
  signal?: AbortSignal,
): Promise<PageResult<ProjectMember>> {
  return apiRequest<PageResult<ProjectMember>>(
    withQuery(PROJECT_API.members(requiredId(projectId)), query),
    { signal },
  )
}

export async function loadVisibleProjects(signal?: AbortSignal): Promise<ProjectListItem[]> {
  const projects: ProjectListItem[] = []
  let pageNo = 1
  let total = 0

  do {
    const page = await loadProjectPage({ pageNo, pageSize: PROJECT_PAGE_SIZE }, signal)
    projects.push(...page.records)
    total = page.total
    if (!page.records.length) break
    pageNo += 1
  } while (projects.length < total)

  return projects
}

export function createProject(command: ProjectUpsertCommand): Promise<string> {
  return apiRequest<string, ProjectUpsertCommand>(PROJECT_API.list, {
    method: 'POST',
    body: command,
  })
}

export function updateProject(projectId: string, command: ProjectUpsertCommand): Promise<void> {
  return apiRequest<void, ProjectUpsertCommand>(PROJECT_API.detail(requiredId(projectId)), {
    method: 'PUT',
    body: command,
  })
}

export function archiveProject(projectId: string): Promise<void> {
  return apiRequest<void>(PROJECT_API.archive(requiredId(projectId)), { method: 'PUT' })
}

export function submitProject(projectId: string): Promise<string> {
  return apiRequest<string>(PROJECT_API.submit(requiredId(projectId)), { method: 'POST' })
}

export function changeProjectStatus(
  projectId: string,
  command: ProjectStatusCommand,
): Promise<void> {
  return apiRequest<void, ProjectStatusCommand>(PROJECT_API.status(requiredId(projectId)), {
    method: 'PUT',
    body: command,
  })
}

export function deleteProject(projectId: string): Promise<void> {
  return apiRequest<void>(PROJECT_API.detail(requiredId(projectId)), { method: 'DELETE' })
}

export function addProjectMember(
  projectId: string,
  command: ProjectMemberCommand,
): Promise<string> {
  return apiRequest<string, ProjectMemberCommand>(PROJECT_API.members(requiredId(projectId)), {
    method: 'POST',
    body: command,
  })
}

export function updateProjectMember(
  projectId: string,
  memberId: string,
  command: ProjectMemberCommand,
): Promise<void> {
  return apiRequest<void, ProjectMemberCommand>(
    PROJECT_API.member(requiredId(projectId), requiredId(memberId)),
    { method: 'PUT', body: command },
  )
}

export function deleteProjectMember(projectId: string, memberId: string): Promise<void> {
  return apiRequest<void>(PROJECT_API.member(requiredId(projectId), requiredId(memberId)), {
    method: 'DELETE',
  })
}

export function loadProjectDictionary(
  code: string,
  signal?: AbortSignal,
): Promise<DictionaryItem[]> {
  return apiRequest<DictionaryItem[]>(PROJECT_API.dictionary(requiredId(code)), { signal })
}

export function loadProjectUsers(signal?: AbortSignal): Promise<PageResult<ProjectUserOption>> {
  return apiRequest<PageResult<ProjectUserOption>>(`${PROJECT_API.users}?pageNo=1&pageSize=200`, {
    signal,
  })
}

function withQuery(path: string, query: ProjectQuery | ProjectMemberQuery): string {
  const params = new URLSearchParams()
  for (const [key, value] of Object.entries(query)) {
    if (typeof value === 'number') {
      if (Number.isInteger(value) && value > 0) params.set(key, String(value))
    } else if (value?.trim()) {
      params.set(key, value.trim())
    }
  }
  const encoded = params.toString()
  return encoded ? `${path}?${encoded}` : path
}

function requiredId(value: string): string {
  const normalized = value.trim()
  if (!normalized) throw new TypeError('项目ID不能为空')
  return normalized
}
