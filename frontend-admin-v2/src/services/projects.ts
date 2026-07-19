import type { PageResult, ProjectListItem } from '@cgc-pms/frontend-contracts'
import { apiRequest } from '@/services/request'

const PROJECT_PAGE_SIZE = 200

export async function loadVisibleProjects(signal?: AbortSignal): Promise<ProjectListItem[]> {
  const projects: ProjectListItem[] = []
  let pageNo = 1
  let total = 0

  do {
    const page = await apiRequest<PageResult<ProjectListItem>>(
      `/projects?pageNo=${pageNo}&pageSize=${PROJECT_PAGE_SIZE}`,
      { signal },
    )
    projects.push(...page.records)
    total = page.total
    if (!page.records.length) break
    pageNo += 1
  } while (projects.length < total)

  return projects
}
