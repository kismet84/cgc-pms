import { request } from '@/api/request'
import type { PageResult } from '@/types/api'
import type {
  SettlementVO,
  SettlementItemVO,
  SettlementQueryParams,
  SettlementKpiVO,
  SettlementComputeVO,
  SettlementVariationItemVO,
  SettlementPaymentItemVO,
  SettlementCostItemVO,
  SettlementAttachmentVO,
  SettlementApprovalRecordVO,
} from '@/types/settlement'

/** 结算列表分页查询 */
export function getSettlementList(params: SettlementQueryParams) {
  return request<PageResult<SettlementVO>>({
    url: '/settlements',
    method: 'get',
    params,
  })
}

/** 结算详情（含明细） */
export function getSettlementDetail(id: string) {
  return request<SettlementVO>({
    url: `/settlements/${id}`,
    method: 'get',
  })
}

/** 新建结算 */
export function createSettlement(data: Partial<SettlementVO>) {
  return request<string>({
    url: '/settlements',
    method: 'post',
    data,
  })
}

/** 更新结算 */
export function updateSettlement(id: string, data: Partial<SettlementVO>) {
  return request<void>({
    url: `/settlements/${id}`,
    method: 'put',
    data,
  })
}

/** 删除结算（仅 DRAFT 状态） */
export function deleteSettlement(id: string) {
  return request<void>({
    url: `/settlements/${id}`,
    method: 'delete',
  })
}

/** 获取结算明细列表 */
export function getSettlementItems(id: string) {
  return request<SettlementItemVO[]>({
    url: `/settlements/${id}/items`,
    method: 'get',
  })
}

/** 批量保存结算明细 */
export function saveSettlementItems(id: string, items: Partial<SettlementItemVO>[]) {
  return request<void>({
    url: `/settlements/${id}/items/batch`,
    method: 'post',
    data: items,
  })
}

/** 自动计算结算金额（只读预览） */
export function computeSettlementAmount(contractId: string) {
  return request<SettlementComputeVO>({
    url: `/settlements/compute/${contractId}`,
    method: 'get',
  })
}

/** KPI 统计 */
export function getSettlementKpi(params?: Partial<SettlementQueryParams>) {
  return request<SettlementKpiVO>({
    url: '/settlements/kpi',
    method: 'get',
    params,
  })
}

/** 获取结算关联的签证变更 */
export function getSettlementVariations(id: string) {
  return request<SettlementVariationItemVO[]>({
    url: `/settlements/${id}/variations`,
    method: 'get',
  })
}

/** 获取结算关联的付款明细 */
export function getSettlementPayments(id: string) {
  return request<SettlementPaymentItemVO[]>({
    url: `/settlements/${id}/payments`,
    method: 'get',
  })
}

/** 获取结算关联的成本明细 */
export function getSettlementCosts(id: string) {
  return request<SettlementCostItemVO[]>({
    url: `/settlements/${id}/costs`,
    method: 'get',
  })
}

/** 获取结算附件 */
export function getSettlementAttachments(id: string) {
  return request<SettlementAttachmentVO[]>({
    url: `/settlements/${id}/attachments`,
    method: 'get',
  })
}

/** 获取结算审批记录 */
export function getSettlementApprovalRecords(id: string) {
  return request<SettlementApprovalRecordVO[]>({
    url: `/settlements/${id}/approval-records`,
    method: 'get',
  })
}

/** 提交审批 */
export function submitSettlement(id: string) {
  return request<void>({
    url: `/settlements/${id}/submit`,
    method: 'post',
  })
}
