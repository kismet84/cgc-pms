import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import type { ProjectRecord } from '@cgc-pms/frontend-contracts'
import {
  cleanMemberCommand,
  cleanProjectCommand,
  isSuperAdmin,
  projectCommand,
} from '@/pages/projects/model'
import {
  addProjectMember,
  archiveProject,
  changeProjectStatus,
  createProject,
  deleteProject,
  deleteProjectMember,
  loadProjectDictionary,
  loadProjectUsers,
  submitProject,
  updateProject,
  updateProjectMember,
} from '@/services/projects'

const fetchMock = vi.fn<typeof fetch>()
const ok = (data: unknown = null) =>
  new Response(JSON.stringify({ code: '0', message: 'success', data }), {
    status: 200,
    headers: { 'Content-Type': 'application/json' },
  })

beforeEach(() => {
  fetchMock.mockReset()
  fetchMock.mockImplementation(async () => ok('1'))
  vi.stubGlobal('fetch', fetchMock)
})
afterEach(() => vi.unstubAllGlobals())

describe('M3 project object service', () => {
  it('maps every project write to the controlled endpoint and method', async () => {
    const command = { projectName: 'A', projectType: 'BUILDING' }
    await createProject(command)
    await updateProject('P/1', command)
    await archiveProject('P/1')
    await submitProject('P/1')
    await changeProjectStatus('P/1', { targetStatus: 'ACTIVE', reason: 'approved' })
    await deleteProject('P/1')
    expect(fetchMock.mock.calls.map(([url, init]) => [url, init?.method])).toEqual([
      ['/api/projects', 'POST'],
      ['/api/projects/P%2F1', 'PUT'],
      ['/api/projects/P%2F1/archive', 'PUT'],
      ['/api/projects/P%2F1/submit', 'POST'],
      ['/api/projects/P%2F1/status', 'PUT'],
      ['/api/projects/P%2F1', 'DELETE'],
    ])
    expect(JSON.parse(String(fetchMock.mock.calls[4]?.[1]?.body))).toEqual({
      targetStatus: 'ACTIVE',
      reason: 'approved',
    })
  })

  it('keeps member operations separate and encodes both ids', async () => {
    const command = { userId: '9', roleCode: 'PM' }
    await addProjectMember('P/1', command)
    await updateProjectMember('P/1', 'M/2', command)
    await deleteProjectMember('P/1', 'M/2')
    expect(fetchMock.mock.calls.map(([url, init]) => [url, init?.method])).toEqual([
      ['/api/projects/P%2F1/members', 'POST'],
      ['/api/projects/P%2F1/members/M%2F2', 'PUT'],
      ['/api/projects/P%2F1/members/M%2F2', 'DELETE'],
    ])
  })

  it('loads dictionaries and user choices only through their scoped APIs', async () => {
    fetchMock.mockImplementation(async () => ok([]))
    await loadProjectDictionary('project/type')
    await loadProjectUsers()
    expect(fetchMock.mock.calls.map(([url]) => url)).toEqual([
      '/api/system/dict/data/by-code/project%2Ftype',
      '/api/system/users?pageNo=1&pageSize=200',
    ])
  })
})

describe('M3 project object model', () => {
  it('trims required values and drops empty optional values without converting amounts', () => {
    expect(
      cleanProjectCommand({
        projectName: ' A ',
        projectType: ' BUILDING ',
        contractAmount: '9007199254740993.01',
        targetCost: '  ',
      }),
    ).toEqual({ projectName: 'A', projectType: 'BUILDING', contractAmount: '9007199254740993.01' })
    expect(cleanMemberCommand({ userId: ' 9 ', roleCode: ' PM ', positionName: ' ' })).toEqual({
      userId: '9',
      roleCode: 'PM',
    })
  })

  it('copies only editable project fields and recognizes only SUPER_ADMIN for deletion', () => {
    const record = {
      id: '1',
      tenantId: '1',
      orgId: '1',
      projectCode: 'P1',
      projectName: 'A',
      projectType: 'BUILDING',
      projectAddress: '',
      ownerUnit: '',
      supervisorUnit: '',
      designUnit: '',
      contractAmount: '1.00',
      targetCost: '2.00',
      plannedStartDate: '2026-01-01',
      plannedEndDate: '2026-12-31',
      projectManagerId: '9',
      status: 'ACTIVE',
      approvalStatus: 'DRAFT',
      createdBy: '9',
      createdAt: '',
      updatedAt: '',
    } satisfies ProjectRecord
    expect(projectCommand(record)).toMatchObject({
      projectName: 'A',
      contractAmount: '1.00',
      targetCost: '2.00',
    })
    expect(projectCommand(record)).not.toHaveProperty('projectCode')
    expect(isSuperAdmin(['ADMIN'])).toBe(false)
    expect(isSuperAdmin(['super_admin'])).toBe(true)
  })
})
