import type {
  PurchaseOrderItemRecord,
  PurchaseOrderRecord,
  PurchaseRequestItemRecord,
  PurchaseRequestRecord,
  ReceiptItemRecord,
  ReceiptRecord,
} from '@cgc-pms/frontend-contracts'
import { formatAmount, formatDecimal } from '@/shared/display'
import { isApiClientError } from '@/services/request'

export type PurchaseExecutionMode = 'request' | 'order' | 'receipt'
export type PurchaseExecutionRecord = PurchaseRequestRecord | PurchaseOrderRecord | ReceiptRecord

export interface DetailTable {
  columns: string[]
  rows: Array<{ key: string; cells: string[] }>
}

export interface RequestItemDraft {
  materialId: string
  budgetLineId: string
  quantity: string
  unit: string
  plannedDate: string
  useLocation: string
  remark: string
}

export interface OrderItemDraft extends RequestItemDraft {
  requestItemId: string
  unitPrice: string
  taxRate: string
  pricingMode: '' | 'FIXED' | 'ACTUAL'
  priceSource: '' | 'CONTRACT_ITEM' | 'RECENT_RECEIPT'
  priceSourceReceiptItemId: string
  priceEditable: boolean
}

export function errorText(error: unknown, fallback: string): string {
  if (isApiClientError(error)) return error.message
  return error instanceof Error ? error.message : fallback
}

export function statusLabel(status?: string | null): string {
  const labels: Record<string, string> = {
    DRAFT: '草稿',
    PENDING: '待处理',
    APPROVING: '审批中',
    APPROVED: '已通过',
    CONVERTED: '已转订单',
    REJECTED: '已驳回',
    IN_PROGRESS: '进行中',
    PERFORMING: '履约中',
    PARTIAL_RECEIVED: '部分到货',
    RECEIVED: '已到货',
    COMPLETED: '已完成',
    CANCELLED: '已取消',
    QUALIFIED: '合格',
    PARTIAL: '部分合格',
    PARTIAL_QUALIFIED: '部分合格',
    PARTIALLY_QUALIFIED: '部分合格',
    UNQUALIFIED: '不合格',
    RETURN: '退货',
    REPLACE: '换货',
    CONCESSION: '让步接收',
    RENDERING: '生成中',
    SUCCEEDED: '已生成',
    FAILED: '生成失败',
  }
  return status ? (labels[status] ?? '未知状态') : '未知状态'
}

function businessCode(value: string | null | undefined, label: string): string {
  return value && !/^\d{15,}$/.test(value) ? value : `未生成${label}号`
}

export function requestCode(record: PurchaseRequestRecord): string {
  return businessCode(record.requestCode, '采购申请')
}

export function orderCode(record: PurchaseOrderRecord): string {
  return businessCode(record.orderCode, '采购订单')
}

export function receiptCode(record: ReceiptRecord): string {
  return businessCode(record.receiptCode, '验收单')
}

export function recordAmount(record: PurchaseExecutionRecord): string {
  return record.totalAmount != null ? formatAmount(record.totalAmount) : '暂无金额'
}

export function required(form: Record<string, string>, name: string, label: string): string {
  const value = form[name]?.trim() ?? ''
  if (!value) throw new TypeError(`${label}不能为空`)
  return value
}

export function optional(form: Record<string, string>, name: string): string | undefined {
  return form[name]?.trim() || undefined
}

export function decimal(form: Record<string, string>, name: string, label: string): string {
  const value = required(form, name, label)
  if (!/^\d+(?:\.\d+)?$/.test(value)) throw new TypeError(`${label}必须为非负十进制数`)
  return value
}

export function positiveValue(value: string, label: string): string {
  const normalized = value.trim()
  if (!/^\d+(?:\.\d+)?$/.test(normalized) || /^0+(?:\.0+)?$/.test(normalized)) {
    throw new TypeError(`${label}必须大于0`)
  }
  return normalized
}

export function requiredDraft(value: string, label: string): string {
  const normalized = value.trim()
  if (!normalized) throw new TypeError(`${label}不能为空`)
  return normalized
}

export function taxRateValue(value: string, label: string): string {
  const normalized = value.trim()
  if (!/^(?:100(?:\.0+)?|\d{1,2}(?:\.\d+)?)$/.test(normalized)) {
    throw new TypeError(`${label}必须在0到100之间`)
  }
  return normalized
}

export function requiredSourceId(value: string | null | undefined, label: string): string {
  const normalized = value?.trim() || ''
  if (!normalized) throw new TypeError(`${label}缺失，请刷新后重试`)
  return normalized
}

export function newRequestItemDraft(): RequestItemDraft {
  return {
    materialId: '',
    budgetLineId: '',
    quantity: '1',
    unit: '',
    plannedDate: '',
    useLocation: '',
    remark: '',
  }
}

export function newOrderItemDraft(): OrderItemDraft {
  return {
    ...newRequestItemDraft(),
    requestItemId: '',
    unitPrice: '',
    taxRate: '0',
    pricingMode: '',
    priceSource: '',
    priceSourceReceiptItemId: '',
    priceEditable: false,
  }
}

function itemName(item: { materialName?: string | null }): string {
  return item.materialName || '物料名称缺失'
}

export function requestDetailTable(items: PurchaseRequestItemRecord[]): DetailTable {
  return {
    columns: ['物料', '规格', '单位', '数量', '使用部位', '计划日期'],
    rows: items.map((item, index) => ({
      key: item.id || `${index}`,
      cells: [
        itemName(item),
        item.specification || '-',
        item.unit || '-',
        formatDecimal(item.quantity),
        item.useLocation || '-',
        item.plannedDate || '-',
      ],
    })),
  }
}

export function orderDetailTable(items: PurchaseOrderItemRecord[]): DetailTable {
  return {
    columns: ['物料', '规格', '单位', '数量', '单价', '金额', '已收数量'],
    rows: items.map((item, index) => ({
      key: item.id || `${index}`,
      cells: [
        itemName(item),
        item.specification || '-',
        item.unit || '-',
        formatDecimal(item.quantity),
        formatAmount(item.unitPrice),
        formatAmount(item.amount),
        formatDecimal(item.receivedQuantity),
      ],
    })),
  }
}

export function receiptDetailTable(
  items: ReceiptItemRecord[],
  selected?: ReceiptRecord | null,
): DetailTable {
  return {
    columns: [
      '物料',
      '规格',
      '单位',
      '本次合格数量',
      '系统批次号',
      '订单数量',
      '累计收货',
      '剩余数量',
      '单价',
      '金额',
      '使用部位',
    ],
    rows: items.map((item, index) => ({
      key: item.id || `${index}`,
      cells: [
        itemName(item),
        item.specification || '-',
        item.unit || '-',
        formatDecimal(item.acceptedQuantity),
        item.systemBatchNo || selected?.systemBatchNo || '-',
        formatDecimal(item.orderedQuantity),
        formatDecimal(item.receivedQuantity),
        formatDecimal(item.remainingQuantity),
        formatAmount(item.unitPrice),
        formatAmount(item.amount),
        item.useLocation || '-',
      ],
    })),
  }
}
