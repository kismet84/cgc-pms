import { request } from '@/api/request'
import type {
  DocumentBusinessType,
  DocumentGeneration,
  DocumentGenerationPage,
  DocumentTemplateCreatePayload,
  DocumentTemplateDetail,
  DocumentTemplateDraft,
  DocumentTemplateFieldCatalog,
  DocumentTemplateSummary,
  DocumentTemplateValidationResult,
  DocumentTemplateVersion,
} from '@/types/document'

export function previewBusinessDocument(
  businessType: DocumentBusinessType,
  businessId: string,
) {
  return request<Blob>({
    url: '/documents/generations/preview',
    method: 'post',
    data: { businessType, businessId },
    responseType: 'blob',
    timeout: 30_000,
  })
}

export function generateBusinessDocument(
  businessType: DocumentBusinessType,
  businessId: string,
  idempotencyKey: string,
) {
  return request<DocumentGeneration>({
    url: '/documents/generations',
    method: 'post',
    data: { businessType, businessId, idempotencyKey },
    timeout: 30_000,
  })
}

export function getBusinessDocumentHistory(
  businessType: DocumentBusinessType,
  businessId: string,
) {
  return request<DocumentGenerationPage>({
    url: '/documents/generations',
    method: 'get',
    params: { businessType, businessId, pageNo: 1, pageSize: 100 },
  })
}

export function getBusinessDocumentDownloadUrl(generationId: string) {
  return request<string>({
    url: `/documents/generations/${generationId}/download`,
    method: 'get',
  })
}

export function getDocumentTemplates(businessType?: DocumentBusinessType) {
  return request<DocumentTemplateSummary[]>({
    url: '/document-templates',
    method: 'get',
    params: { businessType },
  })
}

export function getDocumentTemplate(templateId: string) {
  return request<DocumentTemplateDetail>({
    url: `/document-templates/${templateId}`,
    method: 'get',
  })
}

export function getDocumentTemplateFieldCatalog(businessType: DocumentBusinessType) {
  return request<DocumentTemplateFieldCatalog>({
    url: '/document-templates/catalog',
    method: 'get',
    params: { businessType },
  })
}

export function createDocumentTemplate(payload: DocumentTemplateCreatePayload) {
  return request<DocumentTemplateVersion>({
    url: '/document-templates',
    method: 'post',
    data: payload,
  })
}

export function importDocumentTemplate(payload: DocumentTemplateCreatePayload) {
  return request<DocumentTemplateVersion>({
    url: '/document-templates/import',
    method: 'post',
    data: payload,
  })
}

export function createDocumentTemplateVersion(templateId: string, payload: DocumentTemplateDraft) {
  return request<DocumentTemplateVersion>({
    url: `/document-templates/${templateId}/versions`,
    method: 'post',
    data: payload,
  })
}

export function copyDocumentTemplateVersion(templateId: string, sourceVersionId: string) {
  return request<DocumentTemplateVersion>({
    url: `/document-templates/${templateId}/versions/${sourceVersionId}/copy`,
    method: 'post',
  })
}

export function updateDocumentTemplateVersion(versionId: string, payload: DocumentTemplateDraft) {
  return request<void>({
    url: `/document-templates/versions/${versionId}`,
    method: 'put',
    data: payload,
  })
}

export function validateDocumentTemplate(
  businessType: DocumentBusinessType,
  payload: DocumentTemplateDraft,
) {
  return request<DocumentTemplateValidationResult>({
    url: '/document-templates/validate',
    method: 'post',
    data: { businessType, ...payload },
  })
}

export function publishDocumentTemplateVersion(versionId: string) {
  return request<DocumentTemplateVersion>({
    url: `/document-templates/versions/${versionId}/publish`,
    method: 'post',
  })
}

export function disableDocumentTemplateVersion(versionId: string) {
  return request<void>({
    url: `/document-templates/versions/${versionId}/disable`,
    method: 'post',
  })
}

export function bindDocumentDefaultTemplate(versionId: string, expectedLockVersion: number) {
  return request<void>({
    url: `/document-templates/versions/${versionId}/default`,
    method: 'put',
    params: { expectedLockVersion },
  })
}

export function exportDocumentTemplateVersion(versionId: string) {
  return request<DocumentTemplateCreatePayload>({
    url: `/document-templates/versions/${versionId}/export`,
    method: 'get',
  })
}

export function previewDocumentTemplateVersion(versionId: string, businessId: string) {
  return request<Blob>({
    url: `/document-templates/versions/${versionId}/preview`,
    method: 'post',
    params: { businessId },
    responseType: 'blob',
    timeout: 30_000,
  })
}
