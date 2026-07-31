import type { PageResult } from './api'

export type DocumentBusinessType = 'PAYMENT' | 'SETTLEMENT'
export type DocumentTemplateVersionStatus = 'DRAFT' | 'PUBLISHED' | 'DISABLED'

export interface DocumentGeneration {
  id: string
  generationNo: string
  businessType: DocumentBusinessType
  businessId: string
  templateVersionId: string
  status: 'PENDING' | 'RENDERING' | 'SUCCEEDED' | 'FAILED'
  failureCode?: string
  requestedAt: string
  completedAt?: string
  outputSha256?: string
}

export type DocumentGenerationPage = PageResult<DocumentGeneration>

export interface DocumentTemplateDraft {
  schemaVersion: string
  templateContent: string
  fieldManifest: string
  remark?: string
}

export interface DocumentTemplateCreatePayload extends DocumentTemplateDraft {
  templateCode: string
  templateName: string
  businessType: DocumentBusinessType
}

export interface DocumentTemplateVersion extends DocumentTemplateDraft {
  id: string
  templateId: string
  versionNo: number
  status: DocumentTemplateVersionStatus
  contentHash: string
  publishedBy?: string
  publishedAt?: string
  createdAt?: string
  updatedAt?: string
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

export interface DocumentTemplateDefinition {
  id: string
  templateCode: string
  templateName: string
  businessType: DocumentBusinessType
  enabled: number
}

export interface DocumentDefaultBinding {
  templateId: string
  templateVersionId: string
  lockVersion: number
}

export interface DocumentTemplateDetail {
  template: DocumentTemplateDefinition
  versions: DocumentTemplateVersion[]
  defaultBinding?: DocumentDefaultBinding
}

export interface DocumentTemplateField {
  path: string
  label: string
  valueType: 'TEXT' | 'MONEY' | 'NUMBER' | 'DATE' | 'DATETIME' | 'ENUM'
  nullable: boolean
  collectionPath?: string
  masked: boolean
}

export interface DocumentTemplateFieldCatalog {
  businessType: DocumentBusinessType
  schemaVersion: string
  fields: DocumentTemplateField[]
}

export interface DocumentTemplateValidationResult {
  schemaVersion: string
  fieldCount: number
  referencedFields: string[]
  collectionPaths: string[]
}
