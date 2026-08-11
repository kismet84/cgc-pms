import { apiRequest } from './request'

export interface PageResult<T> {
  pageNo: number
  pageSize: number
  total: number
  records: T[]
}

export interface UserRecord {
  id: string
  username: string
  realName?: string
  phone?: string
  email?: string
  orgId?: string
  status: string
  roleNames: string[]
  roleIds: string[]
  createdAt?: string
}

export interface UserCommand {
  username?: string
  password?: string
  realName?: string
  phone?: string
  email?: string
  orgId?: string | null
  roleIds?: string[]
}

export interface RoleRecord {
  id: string
  roleCode: string
  roleName: string
  roleType?: string
  status: string
  dataScope: string
  roleLevel?: number
  userCount?: number
  menuIds: string[]
}

export const VISIBLE_ROLE_CODES = [
  'COMPANY_OWNER',
  'COMPANY_FINANCE',
  'PROJECT_MANAGER',
  'PROJECT_ACCOUNTANT',
  'TECHNICAL_LEAD',
  'SAFETY_LEAD',
  'CONSTRUCTION_LEAD',
  'PROCUREMENT_LEAD',
  'EMPLOYEE',
] as const

export interface MenuRecord {
  id: string
  parentId: string
  menuName: string
  menuType: 'DIR' | 'MENU' | 'BUTTON'
  path?: string
  component?: string
  perms?: string
  icon?: string
  orderNum: number
  status: string
  visible: number
}

export interface MenuCommand {
  parentId: string
  menuName: string
  menuType: MenuRecord['menuType']
  path: string
  component: string
  perms: string
  icon: string
  orderNum: number
  status: string
  visible: number
}

export interface DictTypeRecord {
  id: string
  groupId: string
  dictCode: string
  dictName: string
  dictClass: string
  status: string
  createdAt?: string
}

export interface DictGroupRecord {
  id: string
  groupCode: string
  groupName: string
  orderNum: number
  status: string
}

export interface DictDataRecord {
  id: string
  dictTypeId: string
  dictLabel: string
  dictValue: string
  cssClass?: string
  listClass?: string
  orderNum: number
  status: string
}

export interface DictTreeType extends DictTypeRecord {
  data: DictDataRecord[]
}

export interface DictGroupTreeRecord extends DictGroupRecord {
  types: DictTreeType[]
}

export interface AuditRecord {
  id: string
  userId?: string
  operationType?: string
  businessType?: string
  businessId?: string
  httpMethod?: string
  requestPath?: string
  successFlag?: number
  errorCode?: string
  sourceIp?: string
  durationMs?: number
  createdAt?: string
}

export interface DataMaintenanceRetainedGroup {
  code: string
  tableCount: number
  rowCount: number
}

export interface DataMaintenancePreview {
  database: string
  policyFingerprint: string
  eligible: boolean
  blockers: string[]
  retainedGroups: DataMaintenanceRetainedGroup[]
  clearTableCount: number
  clearRowCount: number
  sysFileCount: number
  ignoredViews: string[]
}

export type DocumentBusinessType = string
export type DocumentVersionStatus = 'DRAFT' | 'PUBLISHED' | 'DISABLED'

export interface DocumentBusinessTypeOption {
  businessType: DocumentBusinessType
  displayName: string
  schemaVersion: string
  providerReady: boolean
  fieldCount: number
}

export interface DocumentCatalogField {
  path: string
  label: string
  valueType: string
  nullable: boolean
  group?: string
  collectionPath?: string | null
  masked: boolean
  sortOrder?: number
}

export interface DocumentFieldCatalog {
  businessType: DocumentBusinessType
  displayName?: string
  schemaVersion: string
  fields: DocumentCatalogField[]
}

export type DocumentPageOrientation = 'PORTRAIT' | 'LANDSCAPE'

export interface DocumentCanvasElement {
  id: string
  type: 'TEXT' | 'FIELD' | 'DIVIDER'
  xMm: number
  yMm: number
  widthMm: number
  heightMm: number
  text?: string
  fieldPath?: string
  fontSizePt?: number
  align?: 'LEFT' | 'CENTER' | 'RIGHT'
  repeat?: 'BODY' | 'HEADER' | 'FOOTER'
  zIndex?: number
}

export interface DocumentCanvasTableColumn {
  fieldPath: string
  header: string
  widthMm: number
}

export interface DocumentCanvasTable {
  id: string
  collectionPath: string
  xMm: number
  yMm: number
  widthMm: number
  heightMm: number
  columns: DocumentCanvasTableColumn[]
}

export interface DocumentDesignSchema {
  schemaVersion: string
  page: {
    size: 'A4'
    orientation: DocumentPageOrientation
    marginMm: { top: number; right: number; bottom: number; left: number }
  }
  elements: DocumentCanvasElement[]
  tables: DocumentCanvasTable[]
}

export interface DocumentTemplateSummary {
  id: string
  templateCode: string
  templateName: string
  businessType: DocumentBusinessType
  enabled: number
  defaultVersionId?: string
  defaultLockVersion?: number
  updatedAt?: string
}

export interface DocumentTemplateVersion {
  id: string
  templateId: string
  versionNo: number
  status: DocumentVersionStatus
  schemaVersion: string
  templateContent: string
  fieldManifest: string
  designSchema?: string | null
  contentHash: string
  remark?: string
  publishedAt?: string
}

export interface DocumentTemplateDetail {
  template: DocumentTemplateSummary
  versions: DocumentTemplateVersion[]
  defaultBinding?: {
    templateId: string
    templateVersionId: string
    lockVersion: number
  }
}

export interface DocumentDraft {
  schemaVersion: string
  templateContent?: string
  fieldManifest?: string
  designSchema?: string
  remark?: string
}

export interface DocumentCreateCommand extends DocumentDraft {
  templateName: string
  businessType: DocumentBusinessType
}

export interface DocumentGenerationRecord {
  id: string
  businessType: DocumentBusinessType
  businessId: string
  status: 'PENDING' | 'RENDERING' | 'SUCCEEDED' | 'FAILED'
  fileId?: string | null
  failureCode?: string | null
  requestedAt?: string
  completedAt?: string | null
}

export function generateDocument(command: {
  businessType: DocumentBusinessType
  businessId: string
  idempotencyKey: string
  retryOfGenerationId?: string | null
}): Promise<DocumentGenerationRecord> {
  return apiRequest<DocumentGenerationRecord, typeof command>('/documents/generations', {
    method: 'POST',
    body: command,
  }).then(normalizeGeneration)
}

export function previewDocument(
  businessType: DocumentBusinessType,
  businessId: string,
): Promise<Blob> {
  return apiRequest<
    Blob,
    {
      businessType: DocumentBusinessType
      businessId: string
    }
  >('/documents/generations/preview', { method: 'POST', body: { businessType, businessId } })
}

export function loadDocumentGenerationHistory(
  businessType: DocumentBusinessType,
  businessId: string,
  pageNo = 1,
  pageSize = 20,
): Promise<PageResult<DocumentGenerationRecord>> {
  return apiRequest<PageResult<DocumentGenerationRecord>>(
    `/documents/generations?${params({ businessType, businessId, pageNo, pageSize })}`,
  ).then((page) => normalizePage(page, normalizeGeneration))
}

export function downloadDocumentGeneration(id: string): Promise<string> {
  return apiRequest<string>(`/documents/generations/${requiredId(id)}/download`)
}

export function retryDocumentGeneration(
  generation: DocumentGenerationRecord,
  idempotencyKey: string,
): Promise<DocumentGenerationRecord> {
  return generateDocument({
    businessType: generation.businessType,
    businessId: generation.businessId,
    idempotencyKey,
    retryOfGenerationId: generation.id,
  })
}

export async function loadUsers(
  query: {
    pageNo: number
    pageSize: number
    username?: string
    realName?: string
    status?: string
    roleId?: string
  },
  signal?: AbortSignal,
): Promise<PageResult<UserRecord>> {
  const page = await apiRequest<PageResult<UserRecord>>(`/system/users?${params(query)}`, {
    signal,
  })
  return normalizePage(page, normalizeUser)
}

export function loadUser(id: string): Promise<UserRecord> {
  return apiRequest<UserRecord>(`/system/users/${requiredId(id)}`).then(normalizeUser)
}

export function createUser(command: UserCommand): Promise<string> {
  return apiRequest<string, UserCommand>('/system/users', { method: 'POST', body: command }).then(
    String,
  )
}

export function updateUser(id: string, command: UserCommand): Promise<void> {
  return apiRequest<void, UserCommand>(`/system/users/${requiredId(id)}`, {
    method: 'PUT',
    body: command,
  })
}

export function updateUserStatus(id: string, status: string): Promise<void> {
  return apiRequest<void, { status: string }>(`/system/users/${requiredId(id)}/status`, {
    method: 'PATCH',
    body: { status },
  })
}

export function deleteUser(id: string): Promise<void> {
  return apiRequest<void>(`/system/users/${requiredId(id)}`, { method: 'DELETE' })
}

export function assignUserRoles(id: string, roleIds: string[]): Promise<void> {
  return apiRequest<void, { roleIds: string[] }>(`/system/users/${requiredId(id)}/roles`, {
    method: 'PUT',
    body: { roleIds },
  })
}

export function loadRoles(): Promise<RoleRecord[]> {
  return apiRequest<RoleRecord[]>('/system/roles').then((rows) => {
    const order = new Map<string, number>(VISIBLE_ROLE_CODES.map((code, index) => [code, index]))
    return rows
      .map(normalizeRole)
      .filter((role) => order.has(role.roleCode))
      .sort((left, right) => order.get(left.roleCode)! - order.get(right.roleCode)!)
  })
}

export function loadRole(id: string): Promise<RoleRecord> {
  return apiRequest<RoleRecord>(`/system/roles/${requiredId(id)}`).then(normalizeRole)
}

export function assignRoleMenus(id: string, menuIds: string[]): Promise<void> {
  return apiRequest<void, { menuIds: string[] }>(`/system/roles/${requiredId(id)}/menus`, {
    method: 'PUT',
    body: { menuIds },
  })
}

export function loadMenus(): Promise<MenuRecord[]> {
  return apiRequest<MenuRecord[]>('/system/menus').then((rows) => rows.map(normalizeMenu))
}

export function loadMenu(id: string): Promise<MenuRecord> {
  return apiRequest<MenuRecord>(`/system/menus/${requiredId(id)}`).then(normalizeMenu)
}

export function createMenu(command: MenuCommand): Promise<string> {
  return apiRequest<string, MenuCommand>('/system/menus', {
    method: 'POST',
    body: command,
  }).then(String)
}

export function updateMenu(id: string, command: MenuCommand): Promise<void> {
  return apiRequest<void, MenuCommand>(`/system/menus/${requiredId(id)}`, {
    method: 'PUT',
    body: command,
  })
}

export function deleteMenu(id: string): Promise<void> {
  return apiRequest<void>(`/system/menus/${requiredId(id)}`, { method: 'DELETE' })
}

export async function loadDictTypes(
  query: {
    pageNo: number
    pageSize: number
    groupId?: string
    dictCode?: string
    dictName?: string
    status?: string
    dictClass?: string
  },
  signal?: AbortSignal,
): Promise<PageResult<DictTypeRecord>> {
  const page = await apiRequest<PageResult<DictTypeRecord>>(`/system/dict/types?${params(query)}`, {
    signal,
  })
  return normalizePage(page, normalizeDictType)
}

export async function loadDictGroups(
  query: {
    pageNo: number
    pageSize: number
    keyword?: string
    status?: string
  },
  signal?: AbortSignal,
): Promise<PageResult<DictGroupRecord>> {
  const page = await apiRequest<PageResult<DictGroupRecord>>(
    `/system/dict/groups?${params(query)}`,
    { signal },
  )
  return normalizePage(page, normalizeDictGroup)
}

export function loadDictTree(keyword = '', signal?: AbortSignal): Promise<DictGroupTreeRecord[]> {
  return apiRequest<DictGroupTreeRecord[]>(`/system/dict/tree?${params({ keyword })}`, {
    signal,
  }).then((groups) => (groups ?? []).map(normalizeDictTreeGroup))
}

export function createDictGroup(command: Omit<DictGroupRecord, 'id'>): Promise<string> {
  return apiRequest<string, Omit<DictGroupRecord, 'id'>>('/system/dict/groups', {
    method: 'POST',
    body: command,
  }).then(String)
}

export function updateDictGroup(id: string, command: Omit<DictGroupRecord, 'id'>): Promise<void> {
  return apiRequest<void, Omit<DictGroupRecord, 'id'>>(`/system/dict/groups/${requiredId(id)}`, {
    method: 'PUT',
    body: command,
  })
}

export function deleteDictGroup(id: string): Promise<void> {
  return apiRequest<void>(`/system/dict/groups/${requiredId(id)}`, { method: 'DELETE' })
}

export function createDictType(command: Omit<DictTypeRecord, 'id' | 'createdAt'>): Promise<string> {
  return apiRequest<string, Omit<DictTypeRecord, 'id' | 'createdAt'>>('/system/dict/types', {
    method: 'POST',
    body: command,
  }).then(String)
}

export function updateDictType(
  id: string,
  command: Omit<DictTypeRecord, 'id' | 'createdAt'>,
): Promise<void> {
  return apiRequest<void, Omit<DictTypeRecord, 'id' | 'createdAt'>>(
    `/system/dict/types/${requiredId(id)}`,
    { method: 'PUT', body: command },
  )
}

export function deleteDictType(id: string): Promise<void> {
  return apiRequest<void>(`/system/dict/types/${requiredId(id)}`, { method: 'DELETE' })
}

export async function loadDictData(
  query: { pageNo: number; pageSize: number; typeId?: string; dictLabel?: string; status?: string },
  signal?: AbortSignal,
): Promise<PageResult<DictDataRecord>> {
  const page = await apiRequest<PageResult<DictDataRecord>>(`/system/dict/data?${params(query)}`, {
    signal,
  })
  return normalizePage(page, normalizeDictData)
}

export function loadEnabledDictDataByCode(
  code: string,
  signal?: AbortSignal,
): Promise<DictDataRecord[]> {
  return apiRequest<DictDataRecord[]>(`/system/dict/data/by-code/${requiredId(code)}`, {
    signal,
  }).then((rows) => (rows ?? []).map(normalizeDictData))
}

export function createDictData(command: Omit<DictDataRecord, 'id'>): Promise<string> {
  return apiRequest<string, Omit<DictDataRecord, 'id'>>('/system/dict/data', {
    method: 'POST',
    body: command,
  }).then(String)
}

export function updateDictData(id: string, command: Omit<DictDataRecord, 'id'>): Promise<void> {
  return apiRequest<void, Omit<DictDataRecord, 'id'>>(`/system/dict/data/${requiredId(id)}`, {
    method: 'PUT',
    body: command,
  })
}

export function deleteDictData(id: string): Promise<void> {
  return apiRequest<void>(`/system/dict/data/${requiredId(id)}`, { method: 'DELETE' })
}

export async function loadAuditLogs(
  query: {
    pageNo: number
    pageSize: number
    businessType?: string
    businessId?: string
    userId?: string
    startTime?: string
    endTime?: string
  },
  signal?: AbortSignal,
): Promise<PageResult<AuditRecord>> {
  const page = await apiRequest<PageResult<AuditRecord>>(`/audit-logs?${params(query)}`, { signal })
  return normalizePage(page, (row) => ({
    ...row,
    id: String(row.id),
    userId: row.userId == null ? undefined : String(row.userId),
  }))
}

export function loadDocumentTemplates(
  businessType: DocumentBusinessType,
  signal?: AbortSignal,
): Promise<DocumentTemplateSummary[]> {
  return apiRequest<DocumentTemplateSummary[]>(`/document-templates?${params({ businessType })}`, {
    signal,
  }).then((rows) => rows.map(normalizeTemplate))
}

export function loadDocumentBusinessTypes(
  signal?: AbortSignal,
): Promise<DocumentBusinessTypeOption[]> {
  return apiRequest<DocumentBusinessTypeOption[]>('/document-templates/business-types', {
    signal,
  }).then((rows) =>
    (rows ?? []).map((row) => ({
      ...row,
      providerReady: Boolean(row.providerReady),
      fieldCount: Number(row.fieldCount ?? 0),
    })),
  )
}

export function loadDocumentFieldCatalog(
  businessType: DocumentBusinessType,
  signal?: AbortSignal,
): Promise<DocumentFieldCatalog> {
  return apiRequest<DocumentFieldCatalog>(
    `/document-templates/catalog?${params({ businessType })}`,
    { signal },
  ).then((catalog) => ({
    ...catalog,
    fields: (catalog.fields ?? []).map((field, index) => ({
      ...field,
      group: field.group || (field.collectionPath ? '业务明细' : '基本信息'),
      sortOrder: Number(field.sortOrder ?? index),
    })),
  }))
}

export function previewDocumentTemplateHtml(command: {
  businessType: DocumentBusinessType
  designSchema: string
  businessId?: string
}): Promise<{ html: string }> {
  return apiRequest<{ html: string }, typeof command>('/document-templates/preview-html', {
    method: 'POST',
    body: command,
  })
}

export function previewDocumentTemplateVersionHtml(
  versionId: string,
  businessId?: string,
  signal?: AbortSignal,
): Promise<{ html: string }> {
  const query = params({ businessId }).toString()
  return apiRequest<{ html: string }>(
    `/document-templates/versions/${requiredId(versionId)}/preview-html${query ? `?${query}` : ''}`,
    { method: 'POST', signal },
  )
}

export function loadDocumentTemplate(id: string): Promise<DocumentTemplateDetail> {
  return apiRequest<DocumentTemplateDetail>(`/document-templates/${requiredId(id)}`).then(
    normalizeDocumentDetail,
  )
}

export function createDocumentTemplate(
  command: DocumentCreateCommand,
): Promise<DocumentTemplateVersion> {
  return apiRequest<DocumentTemplateVersion, DocumentCreateCommand>('/document-templates', {
    method: 'POST',
    body: command,
  }).then(normalizeDocumentVersion)
}

export function createDocumentVersion(
  templateId: string,
  command: DocumentDraft,
): Promise<DocumentTemplateVersion> {
  return apiRequest<DocumentTemplateVersion, DocumentDraft>(
    `/document-templates/${requiredId(templateId)}/versions`,
    { method: 'POST', body: command },
  ).then(normalizeDocumentVersion)
}

export function updateDocumentVersion(id: string, command: DocumentDraft): Promise<void> {
  return apiRequest<void, DocumentDraft>(`/document-templates/versions/${requiredId(id)}`, {
    method: 'PUT',
    body: command,
  })
}

export function publishDocumentVersion(id: string): Promise<DocumentTemplateVersion> {
  return apiRequest<DocumentTemplateVersion>(
    `/document-templates/versions/${requiredId(id)}/publish`,
    {
      method: 'POST',
    },
  ).then(normalizeDocumentVersion)
}

export function disableDocumentVersion(id: string): Promise<void> {
  return apiRequest<void>(`/document-templates/versions/${requiredId(id)}/disable`, {
    method: 'POST',
  })
}

export function enableDocumentVersion(id: string): Promise<DocumentTemplateVersion> {
  return apiRequest<DocumentTemplateVersion>(
    `/document-templates/versions/${requiredId(id)}/enable`,
    { method: 'POST' },
  ).then(normalizeDocumentVersion)
}

export function deleteDocumentTemplate(id: string): Promise<void> {
  return apiRequest<void>(`/document-templates/${requiredId(id)}`, { method: 'DELETE' })
}

export function bindDefaultDocumentVersion(id: string, expectedLockVersion: number): Promise<void> {
  return apiRequest<void>(
    `/document-templates/versions/${requiredId(id)}/default?${params({ expectedLockVersion })}`,
    { method: 'PUT' },
  )
}

export function loadDataMaintenancePreview(): Promise<DataMaintenancePreview> {
  return apiRequest<DataMaintenancePreview>('/system/data-maintenance/preview')
}

function normalizeUser(row: UserRecord): UserRecord {
  return {
    ...row,
    id: String(row.id),
    orgId: row.orgId == null ? undefined : String(row.orgId),
    roleNames: row.roleNames ?? [],
    roleIds: (row.roleIds ?? []).map(String),
  }
}

function normalizeRole(row: RoleRecord): RoleRecord {
  return {
    ...row,
    id: String(row.id),
    userCount: Number(row.userCount ?? 0),
    menuIds: (row.menuIds ?? []).map(String),
  }
}

function normalizeMenu(row: MenuRecord): MenuRecord {
  return {
    ...row,
    id: String(row.id),
    parentId: String(row.parentId ?? 0),
    orderNum: Number(row.orderNum ?? 0),
    visible: Number(row.visible ?? 1),
  }
}

function normalizeDictData(row: DictDataRecord): DictDataRecord {
  return {
    ...row,
    id: String(row.id),
    dictTypeId: String(row.dictTypeId),
    orderNum: Number(row.orderNum ?? 0),
  }
}

function normalizeDictGroup(row: DictGroupRecord): DictGroupRecord {
  return {
    ...row,
    id: String(row.id),
    orderNum: Number(row.orderNum ?? 0),
  }
}

function normalizeDictType(row: DictTypeRecord): DictTypeRecord {
  return { ...row, id: String(row.id), groupId: String(row.groupId) }
}

function normalizeDictTreeGroup(row: DictGroupTreeRecord): DictGroupTreeRecord {
  return {
    ...normalizeDictGroup(row),
    types: (row.types ?? []).map((type) => ({
      ...normalizeDictType(type),
      data: (type.data ?? []).map(normalizeDictData),
    })),
  }
}

function normalizeTemplate(row: DocumentTemplateSummary): DocumentTemplateSummary {
  return {
    ...row,
    id: String(row.id),
    defaultVersionId: row.defaultVersionId == null ? undefined : String(row.defaultVersionId),
    defaultLockVersion: row.defaultLockVersion == null ? undefined : Number(row.defaultLockVersion),
  }
}

function normalizeDocumentVersion(row: DocumentTemplateVersion): DocumentTemplateVersion {
  return { ...row, id: String(row.id), templateId: String(row.templateId) }
}

function normalizeGeneration(row: DocumentGenerationRecord): DocumentGenerationRecord {
  return {
    ...row,
    id: String(row.id),
    businessId: String(row.businessId),
    fileId: row.fileId == null ? row.fileId : String(row.fileId),
  }
}

function normalizeDocumentDetail(row: DocumentTemplateDetail): DocumentTemplateDetail {
  return {
    ...row,
    template: normalizeTemplate(row.template),
    versions: (row.versions ?? []).map(normalizeDocumentVersion),
    defaultBinding: row.defaultBinding
      ? {
          ...row.defaultBinding,
          templateId: String(row.defaultBinding.templateId),
          templateVersionId: String(row.defaultBinding.templateVersionId),
          lockVersion: Number(row.defaultBinding.lockVersion),
        }
      : undefined,
  }
}

function normalizePage<T, R>(page: PageResult<T>, mapper: (row: T) => R): PageResult<R> {
  return {
    pageNo: Number(page.pageNo),
    pageSize: Number(page.pageSize),
    total: Number(page.total),
    records: (page.records ?? []).map(mapper),
  }
}

function params(values: Record<string, string | number | undefined | null>): URLSearchParams {
  const query = new URLSearchParams()
  for (const [key, value] of Object.entries(values)) {
    if (value !== undefined && value !== null && String(value).trim() !== '') {
      query.set(key, String(value))
    }
  }
  return query
}

function requiredId(id: string): string {
  const value = id.trim()
  if (!value) throw new Error('业务标识不能为空')
  return encodeURIComponent(value)
}
