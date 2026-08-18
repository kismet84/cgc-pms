import fs from 'node:fs'
import path from 'node:path'
import { fileURLToPath } from 'node:url'

const repoRoot = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '../..')
const providerDir = path.join(repoRoot, 'backend/src/main/java/com/cgcpms/document/provider')
const output = path.join(repoRoot, 'backend/src/main/resources/document/system-document-templates.json')

const names = {
  PAYMENT: '付款申请单（历史兼容）', SETTLEMENT: '工程结算单', PURCHASE_REQUEST: '采购申请单',
  PURCHASE_ORDER: '采购订单', MATERIAL_RECEIPT: '材料验收单', MATERIAL_REQUISITION: '材料领用单',
  SUB_MEASURE: '分包计量单', PAY_REQUEST: '付款申请单', EXPENSE: '费用申请单',
  OWNER_SETTLEMENT: '业主结算单', FINANCE_COST_ALLOCATION: '财务成本分摊单',
  FINANCE_COST_ALLOCATION_REVERSAL: '财务成本分摊冲销单', CONTRACT_APPROVAL: '合同审批单',
  VAR_ORDER: '工程签证单', CT_CHANGE: '合同变更单', COST_TARGET: '目标成本单',
  PROJECT_BUDGET: '项目预算单', PRODUCTION_MEASUREMENT: '产值计量单',
  COST_CORRECTIVE_ACTION: '成本纠偏单', BID_COST_TARGET_TRANSFER: '投标成本转入单',
  BID_COST_TARGET_TRANSFER_REVERSAL: '投标成本转入冲销单', PROJECT_APPROVAL: '项目立项审批单',
  PROJECT_SCHEDULE: '项目进度计划单', PROJECT_PERIOD_PLAN: '项目期间计划单',
  PROJECT_COMMENCEMENT: '项目开工单', PROJECT_CORRECTIVE_ACTION: '项目纠偏单',
  TECHNICAL_SCHEME: '技术方案审批单', PROJECT_FINAL_ACCEPTANCE: '项目竣工验收单'
}

const legacyCodes = {
  PAYMENT: 'SYSTEM_PAYMENT_APPLICATION_V1', SETTLEMENT: 'SYSTEM_SETTLEMENT_V1',
  PURCHASE_REQUEST: 'SYSTEM_PURCHASE_REQUEST_V1', PURCHASE_ORDER: 'SYSTEM_PURCHASE_ORDER_V1',
  MATERIAL_RECEIPT: 'SYSTEM_MATERIAL_RECEIPT_V1'
}

const groupTitles = {
  project: '项目信息', contract: '合同信息', payment: '付款申请', settlement: '结算信息',
  purchaseRequest: '申请概要', purchaseOrder: '订单概要', receipt: '验收概要', requisition: '领用概要',
  measure: '计量概要', expense: '费用概要', allocation: '分摊概要', reversal: '冲销概要',
  variation: '签证概要', change: '变更概要', target: '目标成本概要', budget: '预算概要',
  measurement: '计量概要', corrective: '纠偏概要', transfer: '转入概要', schedule: '计划概要',
  progress: '进度快照', period: '期间概要', commencement: '开工概要', scheme: '方案概要',
  acceptance: '验收概要', partner: '往来单位', payee: '收款信息', request: '申请关联',
  supplier: '供应商信息', task: '任务信息', action: '成本纠偏概要'
}

const collectionTitles = {
  BID_COST_TARGET_TRANSFER: { items: '转入明细' },
  BID_COST_TARGET_TRANSFER_REVERSAL: { items: '冲销明细' },
  COST_CORRECTIVE_ACTION: { items: '科目成本明细' },
  COST_TARGET: { items: '成本科目明细' },
  FINANCE_COST_ALLOCATION: { items: '项目分摊明细' },
  FINANCE_COST_ALLOCATION_REVERSAL: { items: '项目冲销明细' },
  MATERIAL_RECEIPT: { items: '材料验收明细' },
  MATERIAL_REQUISITION: { items: '材料领用明细' },
  PAY_REQUEST: { basis: '付款依据', sources: '付款来源', invoices: '发票明细' },
  PAYMENT: { basis: '付款依据', sources: '付款来源', invoices: '发票明细' },
  PRODUCTION_MEASUREMENT: { lines: '计量清单', submissions: '业主报量明细' },
  PROJECT_BUDGET: { lines: '科目预算明细' },
  PROJECT_FINAL_ACCEPTANCE: { items: '分项验收明细' },
  PROJECT_PERIOD_PLAN: { items: '期间任务明细' },
  PROJECT_SCHEDULE: { tasks: '任务进度明细' },
  PURCHASE_ORDER: { items: '采购材料明细' },
  PURCHASE_REQUEST: { items: '申请材料明细' },
  SETTLEMENT: { items: '结算明细', variations: '变更明细', payments: '付款明细', costs: '成本明细' },
  SUB_MEASURE: { items: '计量清单' },
  VAR_ORDER: { items: '签证明细' }
}

const files = fs.readdirSync(providerDir).filter(name => name.endsWith('DocumentDataProvider.java'))
const sources = files.map(name => fs.readFileSync(path.join(providerDir, name), 'utf8'))
const payRequestSource = sources.find(source => /return\s+"PAY_REQUEST"/.test(source))

function fieldsFrom(source) {
  const fields = []
  const pattern = /\b(field|item)\(\s*"([^"]+)"\s*,\s*"([^"]+)"\s*,\s*"([^"]+)"(?:\s*,\s*(?:true|false|"([^"]+)"))?/g
  for (const match of source.matchAll(pattern)) {
    fields.push({ path: match[2], label: match[3], valueType: match[4], collectionPath: match[1] === 'item' ? match[5] : null })
  }
  return fields.filter((field, index, all) => all.findIndex(value => value.path === field.path) === index)
}

function id(value) {
  return value.replace(/[^A-Za-z0-9_-]/g, '-').replace(/^[^A-Za-z]/, 's-').slice(0, 64)
}

function buildDefinition(source) {
  const businessType = source.match(/businessType\s*\(\s*\)\s*\{\s*return\s+"([A-Z0-9_]+)"/s)?.[1]
  const schemaVersion = source.match(/\bSCHEMA\s*=\s*"([^"]+)"/)?.[1]
  if (!businessType || !schemaVersion || !names[businessType]) return null
  const fields = fieldsFrom(businessType === 'PAYMENT' ? payRequestSource : source)
  if (!fields.length) throw new Error(`No fields parsed for ${businessType}`)
  const scalars = fields.filter(field => !field.collectionPath)
  const collections = Map.groupBy(fields.filter(field => field.collectionPath), field => field.collectionPath)
  const identifier = scalars.find(field => /编号|编码|单号/.test(field.label)) ?? scalars[0]
  const landscape = [...collections.values()].some(group => group.length > 6)
  const width = landscape ? 297 : 210
  const height = landscape ? 210 : 297
  const sections = []
  const systemFields = scalars.filter(field => /(?:created|updated|approved|posted|finalized|activated|effective)At$/i.test(field.path))
  const longFields = scalars.filter(field => field.valueType === 'TEXT' && !systemFields.includes(field)
    && /(?:说明|备注|原因|描述|依据|结论|措施|摘要|计划)$/.test(field.label))
  const regularFields = scalars.filter(field => !systemFields.includes(field) && !longFields.includes(field))
  for (const [group, groupFields] of Map.groupBy(regularFields, field => field.path.split('.')[0])) {
    sections.push({
      id: id(`grid-${group}`), type: 'FIELD_GRID', title: groupTitles[group] ?? `${groupFields[0].label}信息`,
      columns: groupFields.length === 1 ? 1 : groupFields.length === 2 ? 2 : 3,
      cells: groupFields.map(field => ({ label: field.label, fieldPath: field.path }))
    })
  }
  for (const field of longFields) {
    sections.push({ id: id(`note-${field.path}`), type: 'NOTE', title: field.label, fieldPath: field.path })
  }
  for (const [collectionPath, collectionFields] of collections) {
    const anchors = collectionFields.filter(field => /编号|编码|名称|项目|科目/.test(field.label)).slice(0, 2)
    const chunks = []
    if (collectionFields.length <= 8) chunks.push(collectionFields)
    else {
      const rest = collectionFields.filter(field => !anchors.includes(field))
      for (let index = 0; index < rest.length; index += 8 - anchors.length) {
        chunks.push([...anchors, ...rest.slice(index, index + 8 - anchors.length)])
      }
    }
    chunks.forEach((chunk, index) => sections.push({
      id: id(`table-${collectionPath}-${index + 1}`), type: 'COLLECTION_TABLE',
      title: `${collectionTitles[businessType]?.[collectionPath]
        ?? `${collectionFields[0].label.replace(/编号|编码|名称/g, '') || '业务'}明细`}${chunks.length > 1 ? `（${index + 1}）` : ''}`,
      collectionPath,
      columns: chunk.map(field => ({ fieldPath: field.path, header: field.label }))
    }))
  }
  if (systemFields.length) {
    sections.push({ id: 'system-record', type: 'FIELD_GRID', title: '系统记录', columns: Math.min(3, systemFields.length),
      cells: systemFields.map(field => ({ label: field.label, fieldPath: field.path })) })
  }
  sections.push({ id: 'generation-note', type: 'NOTE', title: '生成说明', text: '本单据由 CGC-PMS 根据当前业务数据生成，请结合原始业务资料复核。' })
  sections.push({ id: 'signature-grid', type: 'SIGNATURE_GRID', title: '签认栏', labels: ['编制', '复核', '审批', '日期'] })

  return {
    templateCode: legacyCodes[businessType] ?? `SYSTEM_${businessType}_V1`,
    templateName: names[businessType], businessType, schemaVersion, orientation: landscape ? 'LANDSCAPE' : 'PORTRAIT',
    designSchema: {
      layoutVersion: 2, schemaVersion,
      page: { size: 'A4', orientation: landscape ? 'LANDSCAPE' : 'PORTRAIT', marginMm: { top: 12, right: 12, bottom: 14, left: 12 } },
      elements: [
        { id: 'document-header', type: 'FIELD', xMm: 12, yMm: 12, widthMm: width - 24, heightMm: 7,
          text: `${names[businessType]} ·`, fieldPath: identifier.path, fontSizePt: 9, align: 'LEFT', repeat: 'HEADER', zIndex: 1 },
        { id: 'document-title', type: 'TEXT', xMm: 12, yMm: 22, widthMm: width - 24, heightMm: 12,
          text: names[businessType], fontSizePt: 18, align: 'CENTER', repeat: 'BODY', zIndex: 1 },
        { id: 'document-footer', type: 'TEXT', xMm: 12, yMm: height - 28, widthMm: width - 24, heightMm: 6,
          text: 'CGC-PMS 系统生成', fontSizePt: 8, align: 'CENTER', repeat: 'FOOTER', zIndex: 1 }
      ],
      tables: [], sections
    }
  }
}

const definitions = sources.map(buildDefinition).filter(Boolean).sort((a, b) => a.businessType.localeCompare(b.businessType))
if (definitions.length !== 28) throw new Error(`Expected 28 definitions, got ${definitions.length}`)
fs.mkdirSync(path.dirname(output), { recursive: true })
fs.writeFileSync(output, `${JSON.stringify(definitions, null, 2)}\n`, 'utf8')
console.log(`Generated ${definitions.length} system document templates: ${path.relative(repoRoot, output)}`)
