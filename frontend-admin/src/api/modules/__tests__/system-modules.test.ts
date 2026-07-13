import { beforeEach, describe, expect, it, vi } from 'vitest'

const { mockRequest } = vi.hoisted(() => ({
  mockRequest: vi.fn(),
}))

vi.mock('@/api/request', () => ({
  request: mockRequest,
}))

import { getAlertList, getAlertProcessingReport, markAlertRead, batchEvaluate } from '../alert'
import {
  createDictData,
  createDictType,
  deleteDictData,
  deleteDictType,
  getDictDataByCode,
  getDictDataDetail,
  getDictDataList,
  getDictTypeDetail,
  getDictTypeList,
  updateDictData,
  updateDictType,
} from '../dict'
import { deleteFile, getFileUrl, listFiles, uploadFile } from '../file'
import {
  createMenu,
  deleteMenu,
  getMenuDetail,
  getMenuList,
  getMenuTree,
  getRoles,
  getUserList,
  updateMenu,
  updateRoleMenus,
} from '../system'

describe('system-related API modules', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('builds system management requests', () => {
    const createPayload = {
      parentId: '10',
      menuName: '采购看板',
      menuType: 'MENU' as const,
      path: '/purchase-board',
      orderNum: 3,
    }
    getMenuTree()
    createMenu(createPayload)
    getRoles()
    updateRoleMenus(12, [1, 2])
    getUserList({ pageNum: 1, pageSize: 20 })

    expect(mockRequest).toHaveBeenNthCalledWith(1, { url: '/system/menus/tree', method: 'get' })
    expect(mockRequest).toHaveBeenNthCalledWith(2, {
      url: '/system/menus',
      method: 'post',
      data: createPayload,
    })
    expect(mockRequest).toHaveBeenNthCalledWith(3, { url: '/system/roles', method: 'get' })
    expect(mockRequest).toHaveBeenNthCalledWith(4, {
      url: '/system/roles/12/menus',
      method: 'put',
      data: { menuIds: [1, 2] },
    })
    expect(mockRequest).toHaveBeenNthCalledWith(5, {
      url: '/system/users',
      method: 'get',
      params: { pageNum: 1, pageSize: 20 },
    })
    expect(createPayload).not.toHaveProperty('id')
    expect(createPayload).not.toHaveProperty('tenantId')
    expect(createPayload).not.toHaveProperty('children')
    expect(createPayload).not.toHaveProperty('createdAt')
  })

  it('builds a bodyless menu deletion request', () => {
    deleteMenu('13')

    expect(mockRequest).toHaveBeenCalledOnce()
    expect(mockRequest).toHaveBeenCalledWith({
      url: '/system/menus/13',
      method: 'delete',
    })
    expect(mockRequest.mock.calls[0][0]).not.toHaveProperty('data')
  })

  it('builds an exact menu update request with business fields only', () => {
    const payload = {
      parentId: '10',
      menuName: '菜单概览（修改）',
      menuType: 'MENU' as const,
      path: '/system/overview-v2',
      component: 'system/overview/index',
      perms: 'system:menu:edit',
      icon: 'menu',
      orderNum: 8,
      status: 'ENABLE',
      visible: 1,
    }

    updateMenu('13', payload)

    expect(mockRequest).toHaveBeenCalledOnce()
    expect(mockRequest).toHaveBeenCalledWith({
      url: '/system/menus/13',
      method: 'put',
      data: payload,
    })
    expect(Object.keys(mockRequest.mock.calls[0][0].data).sort()).toEqual(
      [
        'parentId',
        'menuName',
        'menuType',
        'path',
        'component',
        'perms',
        'icon',
        'orderNum',
        'status',
        'visible',
      ].sort(),
    )
    for (const forbiddenField of [
      'id',
      'tenantId',
      'createdBy',
      'createdAt',
      'updatedBy',
      'updatedAt',
      'deletedFlag',
      'children',
    ]) {
      expect(payload).not.toHaveProperty(forbiddenField)
    }
  })

  it('builds a bodyless menu detail request', () => {
    getMenuDetail('13')

    expect(mockRequest).toHaveBeenCalledOnce()
    expect(mockRequest).toHaveBeenCalledWith({
      url: '/system/menus/13',
      method: 'get',
    })
    expect(mockRequest.mock.calls[0][0]).not.toHaveProperty('data')
    expect(mockRequest.mock.calls[0][0]).not.toHaveProperty('params')
  })

  it('builds a bodyless flat menu list request', () => {
    getMenuList()

    expect(mockRequest).toHaveBeenCalledOnce()
    expect(mockRequest).toHaveBeenCalledWith({
      url: '/system/menus',
      method: 'get',
    })
    expect(mockRequest.mock.calls[0][0]).not.toHaveProperty('data')
    expect(mockRequest.mock.calls[0][0]).not.toHaveProperty('params')
  })

  it('builds alert requests', () => {
    getAlertList({ projectId: 'p1', severity: 'HIGH', isRead: 0 })
    markAlertRead('a1')
    batchEvaluate()

    expect(mockRequest).toHaveBeenNthCalledWith(1, {
      url: '/alerts',
      method: 'get',
      params: { projectId: 'p1', severity: 'HIGH', isRead: 0 },
    })
    expect(mockRequest).toHaveBeenNthCalledWith(2, { url: '/alerts/a1/read', method: 'put' })
    expect(mockRequest).toHaveBeenNthCalledWith(3, {
      url: '/alerts/batch-evaluate',
      method: 'post',
    })
  })

  it('builds the alert processing report request from the active filter', () => {
    getAlertProcessingReport({
      pageNum: 1,
      pageSize: 20,
      projectId: 'p1',
      alertDomain: 'PURCHASE',
      severity: 'HIGH',
      processStatus: 'OPEN',
    })

    expect(mockRequest).toHaveBeenCalledWith({
      url: '/alerts/processing-report',
      method: 'get',
      params: {
        projectId: 'p1',
        ruleType: undefined,
        alertDomain: 'PURCHASE',
        severity: 'HIGH',
        isRead: undefined,
        processStatus: 'OPEN',
        triggeredStart: undefined,
        triggeredEnd: undefined,
      },
    })
  })

  it('builds dictionary requests', () => {
    const typePayload = { dictCode: 'cost_type', dictName: '成本类型', status: 'ACTIVE' }
    const dataPayload = {
      dictTypeId: 'dt1',
      dictLabel: '人工',
      dictValue: 'LABOR',
      status: 'ACTIVE',
    }

    getDictTypeList({ pageNum: 1, pageSize: 10, status: 'ACTIVE' })
    getDictTypeDetail('dt1')
    createDictType(typePayload)
    updateDictType('dt1', typePayload)
    deleteDictType('dt1')
    getDictDataList({ pageNum: 1, pageSize: 10, typeId: 'dt1' })
    getDictDataDetail('dd1')
    createDictData(dataPayload)
    updateDictData('dd1', dataPayload)
    getDictDataByCode('cost_type')
    deleteDictData('dd1')

    expect(mockRequest).toHaveBeenNthCalledWith(1, {
      url: '/system/dict/types',
      method: 'get',
      params: { pageNum: 1, pageSize: 10, status: 'ACTIVE' },
    })
    expect(mockRequest).toHaveBeenNthCalledWith(2, {
      url: '/system/dict/types/dt1',
      method: 'get',
    })
    expect(mockRequest).toHaveBeenNthCalledWith(3, {
      url: '/system/dict/types',
      method: 'post',
      data: typePayload,
    })
    expect(mockRequest).toHaveBeenNthCalledWith(4, {
      url: '/system/dict/types/dt1',
      method: 'put',
      data: typePayload,
    })
    expect(mockRequest).toHaveBeenNthCalledWith(5, {
      url: '/system/dict/types/dt1',
      method: 'delete',
    })
    expect(mockRequest).toHaveBeenNthCalledWith(6, {
      url: '/system/dict/data',
      method: 'get',
      params: { pageNum: 1, pageSize: 10, typeId: 'dt1' },
    })
    expect(mockRequest).toHaveBeenNthCalledWith(7, {
      url: '/system/dict/data/dd1',
      method: 'get',
    })
    expect(mockRequest).toHaveBeenNthCalledWith(8, {
      url: '/system/dict/data',
      method: 'post',
      data: dataPayload,
    })
    expect(mockRequest).toHaveBeenNthCalledWith(9, {
      url: '/system/dict/data/dd1',
      method: 'put',
      data: dataPayload,
    })
    expect(mockRequest).toHaveBeenNthCalledWith(10, {
      url: '/system/dict/data/by-code/cost_type',
      method: 'get',
    })
    expect(mockRequest).toHaveBeenNthCalledWith(11, {
      url: '/system/dict/data/dd1',
      method: 'delete',
    })
  })

  it('builds file requests', () => {
    const file = new File(['demo'], 'demo.pdf', { type: 'application/pdf' })

    uploadFile(file, 'invoice', 'i1')
    getFileUrl('f1')
    listFiles('invoice', 'i1')
    deleteFile('f1')

    const uploadCall = mockRequest.mock.calls[0][0]
    expect(uploadCall.url).toBe('/files/upload')
    expect(uploadCall.method).toBe('post')
    expect(uploadCall.params).toEqual({ businessType: 'invoice', businessId: 'i1' })
    expect(uploadCall.timeout).toBe(120000)
    expect(uploadCall.data).toBeInstanceOf(FormData)
    expect(mockRequest).toHaveBeenNthCalledWith(2, {
      url: '/files/f1/url',
      method: 'get',
      errorMessage: '文件下载失败，请确认权限或链接是否已过期',
    })
    expect(mockRequest).toHaveBeenNthCalledWith(3, {
      url: '/files',
      method: 'get',
      params: { businessType: 'invoice', businessId: 'i1' },
    })
    expect(mockRequest).toHaveBeenNthCalledWith(4, { url: '/files/f1', method: 'delete' })
  })
})
