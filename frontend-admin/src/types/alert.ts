/** 预警日志 VO — 对应后端 AlertLog 实体 */
export interface AlertLogVO {
  id: string | number
  tenantId: string | number
  projectId: string | number
  /** 关联合同ID（部分预警按合同维度） */
  contractId?: string | number
  /** 规则类型: DYNAMIC_COST_EXCEEDS_TARGET / MATERIAL_EXCEEDS_BUDGET / ... */
  ruleType: string
  /** 严重度: HIGH / MEDIUM / LOW */
  severity: string
  /** 预警消息 */
  message: string
  /** 触发时间 */
  triggeredAt: string
  /** 0=未读, 1=已读 */
  isRead: number
  createdAt: string
  updatedAt: string
  remark?: string
  /** 可选业务定位字段，兼容并行后端扩展 */
  bizType?: string
  bizId?: string | number
  sourceType?: string
  sourceId?: string | number
  businessType?: string
  businessId?: string | number
  /** 可选分类字段，兼容并行后端扩展 */
  alertDomain?: string
  alertCategory?: string
  category?: string
  processStatus?: string
  handledStatus?: string
  processedAt?: string
  archivedAt?: string
  handledBy?: string
  handledAt?: string
  statusRemark?: string
  /** 可选默认域标记，兼容并行后端扩展 */
  defaultScope?: boolean | number | string
  isDefaultScope?: boolean | number | string
  inDefaultScope?: boolean | number | string
}

export interface AlertSubscriptionConfig {
  enabled: boolean
  channels: string[]
  domains: string[]
  minSeverity: 'LOW' | 'MEDIUM' | 'HIGH'
  notifyOnStatusChanged: boolean
}

export interface AlertSubscriptionOptions {
  domains: string[]
  channels: string[]
  minSeverityOptions: Array<'LOW' | 'MEDIUM' | 'HIGH'>
}

export interface AlertSubscriptionResponse {
  defaultSubscription: AlertSubscriptionConfig
  rawUserOverrides: Partial<AlertSubscriptionConfig> | null
  effectiveSubscription: AlertSubscriptionConfig
  availableOptions: AlertSubscriptionOptions
}

/** 规则类型中文映射 */
export const RULE_TYPE_LABELS: Record<string, string> = {
  DYNAMIC_COST_EXCEEDS_TARGET: '动态成本超目标',
  MATERIAL_EXCEEDS_BUDGET: '材料超预算',
  SUBCONTRACT_EXCEEDS_CONTRACT: '分包超合同',
  CONTRACT_OVERDUE: '合同超期',
  PAYMENT_EXCEEDS_RATIO: '付款超比例',
  WARRANTY_EARLY_RELEASE: '质保金提前释放',
  CONTRACT_EXPIRING: '合同到期',
  VARIATION_UNCONFIRMED: '变更未确认',
  PURCHASE_DELIVERY_OVERDUE: '采购交期逾期',
  CASH_JOURNAL_ARCHIVE_OVERDUE: '资金流水归档逾期',
}

/** 规则分类映射 */
export const RULE_CATEGORY_LABELS: Record<string, string> = {
  COST: '成本类',
  CONTRACT: '合同类',
  PAYMENT: '资金类',
  VARIATION: '变更类',
  PURCHASE: '采购类',
  FINANCE: '资金日记账',
}

export const ALERT_CHANNEL_LABELS: Record<string, string> = {
  IN_APP: '站内信',
  EMAIL: '邮件',
  WECHAT: '企业微信',
  SMS: '短信',
}

export const ALERT_CATEGORY_LABELS: Record<string, string> = {
  COST_DYNAMIC: '动态成本',
  COST_MATERIAL: '材料预算',
  COST_SUBCONTRACT: '分包合同',
  CONTRACT_TERM: '合同期限',
  CONTRACT_WARRANTY: '质保金',
  PAYMENT_RATIO: '付款比例',
  VARIATION_CONFIRM: '变更确认',
  PURCHASE_DELIVERY: '采购交付',
  CASH_JOURNAL_CLOSURE: '流水归档',
  OTHER: '其他',
}

export const ALERT_PROCESS_STATUS_LABELS: Record<string, string> = {
  OPEN: '待处理',
  PROCESSED: '已处理',
  ARCHIVED: '已归档',
  INVALID: '已失效',
}

export const ALERT_PROCESS_STATUS_COLOR: Record<string, string> = {
  OPEN: 'blue',
  PROCESSED: 'green',
  ARCHIVED: 'default',
  INVALID: 'orange',
}

export const RULE_TYPE_CATEGORY_MAP: Record<string, keyof typeof RULE_CATEGORY_LABELS> = {
  DYNAMIC_COST_EXCEEDS_TARGET: 'COST',
  MATERIAL_EXCEEDS_BUDGET: 'COST',
  SUBCONTRACT_EXCEEDS_CONTRACT: 'COST',
  CONTRACT_OVERDUE: 'CONTRACT',
  CONTRACT_EXPIRING: 'CONTRACT',
  VARIATION_UNCONFIRMED: 'VARIATION',
  PAYMENT_EXCEEDS_RATIO: 'PAYMENT',
  WARRANTY_EARLY_RELEASE: 'CONTRACT',
  PURCHASE_DELIVERY_OVERDUE: 'PURCHASE',
  CASH_JOURNAL_ARCHIVE_OVERDUE: 'FINANCE',
}

export function getAlertRuleCategory(
  ruleType?: string,
  alertDomain?: string,
  category?: string,
): string {
  const normalizedCategory = String(alertDomain ?? category ?? '').trim()
  if (normalizedCategory) return normalizedCategory
  return RULE_TYPE_CATEGORY_MAP[String(ruleType ?? '').trim()] ?? 'UNKNOWN'
}

/** 严重度颜色映射 */
export const SEVERITY_COLOR: Record<string, string> = {
  HIGH: 'red',
  MEDIUM: 'orange',
  LOW: 'default',
}
