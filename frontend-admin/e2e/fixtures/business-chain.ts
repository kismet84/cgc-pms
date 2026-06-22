import type { APIRequestContext, APIResponse } from '@playwright/test'

/**
 * E2E 业务链路测试共享 fixture — API 封装层
 *
 * 响应约定：
 * - 后端统一返回 { code: '00000', data: ..., message: ..., traceId: ... }
 * - 前端 Axios 拦截器在 code === '0' 时返回 res.data（拆掉外层包装）
 * - Playwright 的 APIRequestContext 直接返回原始 JSON，不做拦截
 *
 * 因此 expectApiOk() 断言 code === '00000'，然后返回 body.data。
 */

export function runId(): string {
  return `e2e-${Date.now()}-${Math.random().toString(36).slice(2, 8)}`
}

export function code(prefix: string): string {
  return `${prefix}-${runId()}`
}

export async function expectApiOk(response: APIResponse): Promise<unknown> {
  if (!response.ok()) {
    const text = await response.text()
    throw new Error(`API ${response.url()} returned HTTP ${response.status()}: ${text}`)
  }
  const body = await response.json()
  if (body.code !== '00000') {
    throw new Error(
      `API ${response.url()} returned biz error code=${body.code} message=${body.message}`,
    )
  }
  return body.data
}

// ── Auth ──

export async function apiLogin(request: APIRequestContext, baseURL: string): Promise<string> {
  const res = await request.post(`${baseURL}/api/auth/login`, {
    data: { username: 'admin', password: 'admin123' },
  })
  const data = (await expectApiOk(res)) as { token?: string }
  return data.token ?? ''
}

// ── Project ──

export async function apiCreateProject(
  request: APIRequestContext,
  baseURL: string,
  overrides: Record<string, unknown> = {},
) {
  const res = await request.post(`${baseURL}/api/projects`, {
    data: {
      projectCode: code('PRJ'),
      projectName: `E2E项目-${runId()}`,
      projectType: 'BUILDING',
      contractAmount: '5000000',
      plannedStartDate: '2025-01-01',
      plannedEndDate: '2026-12-31',
      status: 'ACTIVE',
      ...overrides,
    },
  })
  return expectApiOk(res)
}

export async function apiListProjects(request: APIRequestContext, baseURL: string) {
  const res = await request.get(`${baseURL}/api/projects`, {
    params: { pageNo: 1, pageSize: 10 },
  })
  const data = (await expectApiOk(res)) as { records: unknown[] }
  return data.records
}

// ── Partner ──

export async function apiCreatePartner(
  request: APIRequestContext,
  baseURL: string,
  overrides: Record<string, unknown> = {},
) {
  const res = await request.post(`${baseURL}/api/partners`, {
    data: {
      partnerCode: code('PT'),
      partnerName: `E2E合作方-${runId()}`,
      partnerType: 'SUPPLIER',
      status: 'ACTIVE',
      ...overrides,
    },
  })
  return expectApiOk(res)
}

// ── Contract ──

export async function apiCreateContract(
  request: APIRequestContext,
  baseURL: string,
  projectId: string,
  partnerId: string,
  overrides: Record<string, unknown> = {},
) {
  const res = await request.post(`${baseURL}/api/contracts/composite`, {
    data: {
      contract: {
        contractCode: code('CT'),
        contractName: `E2E合同-${runId()}`,
        contractType: 'SUB',
        projectId,
        partnerId,
        contractAmount: '1000000',
        signedDate: '2025-06-01',
        ...overrides,
      },
      items: [
        {
          itemName: 'AUTO',
          itemSpec: '1',
          unit: 'm3',
          quantity: 1,
          unitPrice: '1000000',
        },
      ],
      paymentTerms: [
        {
          termName: '进度款',
          paymentRatio: 100,
          paymentAmount: '1000000',
          paymentCondition: '完工后支付',
          plannedDate: '2026-06-01',
        },
      ],
      submitForApproval: false,
    },
  })
  return expectApiOk(res)
}

export async function apiSubmitContract(request: APIRequestContext, baseURL: string, id: string) {
  const res = await request.post(`${baseURL}/api/contracts/${id}/submit`)
  return expectApiOk(res)
}

// ── Warehouse ──

export async function apiCreateWarehouse(
  request: APIRequestContext,
  baseURL: string,
  overrides: Record<string, unknown> = {},
) {
  const res = await request.post(`${baseURL}/api/inventory/warehouses`, {
    data: {
      warehouseCode: code('WH'),
      warehouseName: `E2E仓库-${runId()}`,
      status: 'ACTIVE',
      ...overrides,
    },
  })
  return expectApiOk(res)
}

// ── Material ──

export async function apiCreateMaterial(
  request: APIRequestContext,
  baseURL: string,
  overrides: Record<string, unknown> = {},
) {
  const res = await request.post(`${baseURL}/api/materials`, {
    data: {
      materialCode: code('MAT'),
      materialName: `E2E物料-${runId()}`,
      unit: 'm3',
      materialType: 'RAW',
      ...overrides,
    },
  })
  return expectApiOk(res)
}

// ── Stock In / Out ──

export async function apiStockIn(
  request: APIRequestContext,
  baseURL: string,
  warehouseId: string,
  materialId: string,
  quantity: string,
  sourceType?: string,
  sourceId?: string,
) {
  const res = await request.post(`${baseURL}/api/inventory/stock/in`, {
    data: { warehouseId, materialId, quantity, sourceType, sourceId },
  })
  return expectApiOk(res)
}

// ── Purchase Request ──

export async function apiCreatePurchaseRequest(
  request: APIRequestContext,
  baseURL: string,
  projectId: string,
  materialId: string,
) {
  const res = await request.post(`${baseURL}/api/purchase-requests`, {
    data: {
      projectId,
      remark: `E2E采购申请-${runId()}`,
    },
  })
  return expectApiOk(res)
}

// ── Settlement ──

export async function apiCreateSettlement(
  request: APIRequestContext,
  baseURL: string,
  projectId: string,
  contractId: string,
  partnerId: string,
) {
  const res = await request.post(`${baseURL}/api/settlements`, {
    data: {
      projectId,
      contractId,
      partnerId,
      settlementType: 'FINAL',
      remark: `E2E结算-${runId()}`,
    },
  })
  return expectApiOk(res)
}

// ── Payment ──

export async function apiCreatePayment(
  request: APIRequestContext,
  baseURL: string,
  projectId: string,
  contractId: string,
  partnerId: string,
) {
  const res = await request.post(`${baseURL}/api/pay-applications`, {
    data: {
      projectId,
      contractId,
      partnerId,
      payType: 'PROGRESS',
      applyAmount: '100000',
      applyReason: `E2E付款-${runId()}`,
    },
  })
  return expectApiOk(res)
}

// ── Subcontract Measure ──

export async function apiCreateMeasure(
  request: APIRequestContext,
  baseURL: string,
  projectId: string,
  contractId: string,
  partnerId: string,
) {
  const res = await request.post(`${baseURL}/api/sub-measures`, {
    data: {
      projectId,
      contractId,
      partnerId,
      measureDate: '2025-12-01',
      remark: `E2E计量-${runId()}`,
    },
  })
  return expectApiOk(res)
}

// ── Receipt ──

export async function apiCreateReceipt(
  request: APIRequestContext,
  baseURL: string,
  projectId: string,
  warehouseId: string,
) {
  const res = await request.post(`${baseURL}/api/receipts`, {
    data: {
      projectId,
      warehouseId,
      receiptDate: '2025-12-01',
      remark: `E2E验收-${runId()}`,
    },
  })
  return expectApiOk(res)
}
