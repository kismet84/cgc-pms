import { apiRequest } from '@/services/request'
import { normalizePage, params, requiredId, type PageResult } from './support'

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

export interface DocumentFlowGridCell {
  label: string
  fieldPath?: string
  text?: string
  colSpan?: 1 | 2 | 3
}

export type DocumentFlowSection =
  | {
      id: string
      type: 'FIELD_GRID'
      title?: string
      columns: 1 | 2 | 3
      cells: DocumentFlowGridCell[]
    }
  | {
      id: string
      type: 'COLLECTION_TABLE'
      title?: string
      collectionPath: string
      columns: Array<{ fieldPath: string; header: string }>
    }
  | { id: string; type: 'NOTE'; title?: string; fieldPath?: string; text?: string }
  | { id: string; type: 'SIGNATURE_GRID'; title?: string; labels: string[] }

export interface DocumentDesignSchema {
  layoutVersion?: 1 | 2
  schemaVersion: string
  page: {
    size: 'A4'
    orientation: DocumentPageOrientation
    marginMm: { top: number; right: number; bottom: number; left: number }
  }
  elements: DocumentCanvasElement[]
  tables: DocumentCanvasTable[]
  sections?: DocumentFlowSection[]
}

export interface SystemDocumentTemplateStatus {
  businessType: DocumentBusinessType
  templateCode: string
  templateName: string
  schemaVersion: string
  orientation: DocumentPageOrientation
  templateId?: string | null
  versionId?: string | null
  installed: boolean
  current: boolean
  defaultBinding: 'UNBOUND' | 'SYSTEM' | 'CUSTOM'
}

export interface SystemDocumentTemplateInstallResult {
  businessType: DocumentBusinessType
  templateId: string
  versionId: string
  action: 'CREATED' | 'UPGRADED' | 'UNCHANGED'
  bindingAction: 'BOUND' | 'UPDATED_SYSTEM' | 'PRESERVED_CUSTOM'
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

export function loadSystemDocumentTemplateStatuses(
  signal?: AbortSignal,
): Promise<SystemDocumentTemplateStatus[]> {
  return apiRequest<SystemDocumentTemplateStatus[]>('/document-templates/system/status', {
    signal,
  }).then((rows) =>
    rows.map((row) => ({
      ...row,
      templateId: row.templateId == null ? row.templateId : String(row.templateId),
      versionId: row.versionId == null ? row.versionId : String(row.versionId),
    })),
  )
}

export function installSystemDocumentTemplate(
  businessType: DocumentBusinessType,
): Promise<SystemDocumentTemplateInstallResult> {
  return apiRequest<SystemDocumentTemplateInstallResult>(
    `/document-templates/system/${encodeURIComponent(businessType)}`,
    { method: 'POST' },
  ).then(normalizeInstallResult)
}

export function installAllSystemDocumentTemplates(): Promise<
  SystemDocumentTemplateInstallResult[]
> {
  return apiRequest<SystemDocumentTemplateInstallResult[]>('/document-templates/system/install', {
    method: 'POST',
  }).then((rows) => rows.map(normalizeInstallResult))
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

function normalizeInstallResult(
  row: SystemDocumentTemplateInstallResult,
): SystemDocumentTemplateInstallResult {
  return { ...row, templateId: String(row.templateId), versionId: String(row.versionId) }
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
