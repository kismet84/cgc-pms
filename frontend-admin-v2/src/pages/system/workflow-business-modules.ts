export interface WorkflowBusinessModule {
  key: string
  label: string
}

const workflowModules: Record<string, WorkflowBusinessModule> = {
  PROJECT_APPROVAL: { key: 'delivery', label: '项目履约' },
  PROJECT_SCHEDULE: { key: 'delivery', label: '项目履约' },
  PROJECT_PERIOD_PLAN: { key: 'delivery', label: '项目履约' },
  PROJECT_COMMENCEMENT: { key: 'delivery', label: '项目履约' },
  PROJECT_CORRECTIVE_ACTION: { key: 'delivery', label: '项目履约' },
  TECH_ITEM: { key: 'delivery', label: '项目履约' },
  TECHNICAL_SCHEME: { key: 'delivery', label: '项目履约' },
  PROJECT_FINAL_ACCEPTANCE: { key: 'delivery', label: '项目履约' },
  QS_RECTIFICATION: { key: 'delivery', label: '项目履约' },
  QS_CONSEQUENCE: { key: 'delivery', label: '项目履约' },
  CONTRACT_APPROVAL: { key: 'commercial', label: '商务合约' },
  VAR_ORDER: { key: 'commercial', label: '商务合约' },
  CT_CHANGE: { key: 'commercial', label: '商务合约' },
  COST_TARGET: { key: 'commercial', label: '商务合约' },
  PROJECT_BUDGET: { key: 'commercial', label: '商务合约' },
  PRODUCTION_MEASUREMENT: { key: 'commercial', label: '商务合约' },
  COST_CORRECTIVE_ACTION: { key: 'commercial', label: '商务合约' },
  BID_COST_TARGET_TRANSFER: { key: 'commercial', label: '商务合约' },
  BID_COST_TARGET_TRANSFER_REVERSAL: { key: 'commercial', label: '商务合约' },
  PURCHASE_REQUEST: { key: 'supply', label: '供应链与物资' },
  PURCHASE_ORDER: { key: 'supply', label: '供应链与物资' },
  MATERIAL_RECEIPT: { key: 'supply', label: '供应链与物资' },
  MATERIAL_REQUISITION: { key: 'supply', label: '供应链与物资' },
  SUB_MEASURE: { key: 'subcontract-settlement', label: '分包结算' },
  SETTLEMENT: { key: 'subcontract-settlement', label: '分包结算' },
  PAYMENT: { key: 'finance', label: '资金财务' },
  PAY_REQUEST: { key: 'finance', label: '资金财务' },
  EXPENSE: { key: 'finance', label: '资金财务' },
  CONTRACT_REVENUE: { key: 'finance', label: '资金财务' },
  OWNER_SETTLEMENT: { key: 'finance', label: '资金财务' },
  FINANCE_COST_ALLOCATION: { key: 'finance', label: '资金财务' },
  FINANCE_COST_ALLOCATION_REVERSAL: { key: 'finance', label: '资金财务' },
  COST_RULE_PLAN: { key: 'master-data', label: '基础资料' },
  COST_PROJECT_CONFIG: { key: 'master-data', label: '基础资料' },
  COST_RECALCULATION: { key: 'master-data', label: '基础资料' },
  COST_POST_CLOSE_ADJUSTMENT: { key: 'master-data', label: '基础资料' },
  COST_REVERSAL: { key: 'master-data', label: '基础资料' },
  COST_SUBJECT_MAPPING: { key: 'master-data', label: '基础资料' },
  DEMO_APPROVAL_SCENARIO: { key: 'system-management', label: '系统管理' },
}

const fallbackModule = { key: 'other', label: '其他' }

export const workflowModule = (businessType: string): WorkflowBusinessModule =>
  workflowModules[businessType] ?? fallbackModule
