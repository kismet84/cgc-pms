/** 预警日志 VO — 对应后端 AlertLog 实体 */
export interface AlertLogVO {
  id: number
  tenantId: number
  projectId: number
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
}

/** 严重度颜色映射 */
export const SEVERITY_COLOR: Record<string, string> = {
  HIGH: 'red',
  MEDIUM: 'orange',
  LOW: 'default',
}
