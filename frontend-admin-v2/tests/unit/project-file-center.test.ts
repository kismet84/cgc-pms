import { createPinia, setActivePinia } from 'pinia'
import { flushPromises, mount } from '@vue/test-utils'
import { createMemoryHistory, createRouter } from 'vue-router'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import ProjectFileCenterPage from '@/pages/project/ProjectFileCenterPage.vue'
import {
  getProjectFileDownloadUrl,
  loadProjectFiles,
  requestProjectFilePreview,
} from '@/services/project-files'
import { loadProjectDictionary, loadVisibleProjects } from '@/services/projects'
import { useSessionStore } from '@/stores/session'

vi.mock('@/services/project-files', () => ({
  loadProjectFiles: vi.fn(),
  createProjectFile: vi.fn(),
  addProjectFileVersion: vi.fn(),
  requestProjectFilePreview: vi.fn(),
  getProjectFileDownloadUrl: vi.fn(),
}))
vi.mock('@/services/projects', () => ({
  loadVisibleProjects: vi.fn(),
  loadProjectDictionary: vi.fn(),
}))

const managed = {
  id: 'catalog-1',
  projectId: 'project-1',
  projectName: '江南项目',
  fileCode: 'FILE-P001-20260805-001',
  displayName: '施工组织设计',
  categoryCode: 'TECHNICAL',
  sourceKind: 'MANAGED' as const,
  maintainMode: 'MANAGED' as const,
  versions: [
    {
      id: 'version-1',
      versionNo: 1,
      sysFileId: 'file-1',
      createdBy: null,
      createdAt: '2026-08-04T08:00:00Z',
      virusScanStatus: 'CLEAN',
      previewStatus: 'READY' as const,
    },
    {
      id: 'version-2',
      versionNo: 2,
      sysFileId: 'file-2',
      submitterName: '张三',
      createdAt: '2026-08-05T08:00:00Z',
      virusScanStatus: 'CLEAN',
      previewStatus: 'READY' as const,
    },
  ],
}

async function mountPage(permissions = ['project:file:query', 'project:file:manage']) {
  const router = createRouter({
    history: createMemoryHistory(),
    routes: [{ path: '/project/files', component: ProjectFileCenterPage }],
  })
  await router.push('/project/files')
  await router.isReady()
  useSessionStore().replaceUserInfo({
    tenantId: '1001',
    userId: '1',
    username: 'tester',
    roles: ['USER'],
    permissions,
  })
  return mount(ProjectFileCenterPage, { global: { plugins: [router] }, attachTo: document.body })
}

beforeEach(() => {
  document.body.innerHTML = ''
  setActivePinia(createPinia())
  vi.clearAllMocks()
  vi.mocked(loadProjectFiles).mockResolvedValue({
    records: [managed],
    total: 1,
    pageNo: 1,
    pageSize: 20,
  })
  vi.mocked(loadVisibleProjects).mockResolvedValue([
    { id: 'project-1', projectCode: 'P001', projectName: '江南项目', status: 'ACTIVE' },
  ])
  vi.mocked(loadProjectDictionary).mockResolvedValue([
    { id: '1', dictLabel: '技术资料', dictValue: 'TECHNICAL', orderNum: 1, status: 'ENABLE' },
  ])
  vi.mocked(requestProjectFilePreview).mockResolvedValue({
    status: 'READY',
    url: 'https://files.example/preview',
  })
  vi.mocked(getProjectFileDownloadUrl).mockResolvedValue('https://files.example/download')
  vi.spyOn(window, 'open').mockImplementation(() => null)
})

describe('ProjectFileCenterPage', () => {
  it('defaults to highest version and changes version-dependent metadata locally', async () => {
    const wrapper = await mountPage()
    await flushPromises()

    expect(wrapper.text()).toContain('FILE-P001-20260805-001')
    expect(
      wrapper
        .findAll('thead th')
        .slice(0, 2)
        .map((item) => item.text()),
    ).toEqual(['编号', '项目'])
    expect(wrapper.text()).toContain('张三')
    expect(wrapper.text()).toContain('V2')
    const versionSelect = wrapper.get('select[aria-label="FILE-P001-20260805-001版本"]')
    await versionSelect.setValue('version-1')

    expect(wrapper.text()).toContain('历史导入')
    expect(loadProjectFiles).toHaveBeenCalledTimes(1)
  })

  it('opens a safe blank tab before requesting the selected preview URL', async () => {
    const popup = { opener: window, location: { href: '' }, close: vi.fn() } as unknown as Window
    vi.mocked(window.open).mockReturnValue(popup)
    const wrapper = await mountPage()
    await flushPromises()

    await wrapper.get('tbody th button').trigger('click')
    await flushPromises()

    expect(window.open).toHaveBeenCalledWith('about:blank', '_blank')
    expect(popup.opener).toBeNull()
    expect(requestProjectFilePreview).toHaveBeenCalledWith('version-2', expect.any(AbortSignal))
    expect(popup.location.href).toBe('https://files.example/preview')
  })

  it('reloads when query keeps the same route', async () => {
    const wrapper = await mountPage()
    await flushPromises()

    const queryButton = wrapper.findAll('button').find((button) => button.text().trim() === '查询')
    expect(queryButton).toBeDefined()
    await queryButton!.trigger('click')
    await flushPromises()

    expect(loadProjectFiles).toHaveBeenCalledTimes(2)
  })

  it('keeps business rows read-only and hides manage actions without permission', async () => {
    vi.mocked(loadProjectFiles).mockResolvedValue({
      records: [
        {
          ...managed,
          id: 'business-1',
          sourceKind: 'BUSINESS',
          maintainMode: 'READ_ONLY',
          sourceHint: '由合同模块维护',
        },
      ],
      total: 1,
      pageNo: 1,
      pageSize: 20,
    })
    const wrapper = await mountPage(['project:file:query'])
    await flushPromises()

    expect(wrapper.text()).toContain('由合同模块维护')
    expect(wrapper.text()).not.toContain('上传新版本')
    expect(wrapper.text()).not.toContain('新建文件')
  })
})
