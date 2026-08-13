import type {
  FieldQualityIssueCommand,
  FieldQualityRectificationCommand,
} from '@cgc-pms/frontend-contracts'
import {
  createQualityIssue,
  createQualityRectification,
  submitQualityRectification,
} from '@/services/quality'
import { uploadSiteFileIdempotently } from '@/services/delivery'
import {
  FieldDraftRepository,
  fieldDraftSyncFailure,
  type FieldDraft,
} from '@/services/fieldDrafts'
import { isApiClientError } from '@/services/request'

export type QualityDraftPayload =
  | { kind: 'ISSUE'; inspectionId: string; command: FieldQualityIssueCommand }
  | { kind: 'RECTIFICATION'; command: FieldQualityRectificationCommand }

export type QualityDraftSyncResult =
  | { kind: 'SYNCED'; draft: FieldDraft<QualityDraftPayload> }
  | { kind: 'OFFLINE'; draft: FieldDraft<QualityDraftPayload> }
  | { kind: 'MISSING_EVIDENCE'; draft: FieldDraft<QualityDraftPayload> }
  | { kind: 'FAILED'; draft: FieldDraft<QualityDraftPayload>; message: string }

export function qualityDraftId(payload: QualityDraftPayload): string {
  return payload.kind === 'ISSUE'
    ? `quality:issue:${payload.inspectionId}`
    : `quality:rectification:${payload.command.issueId}`
}

export async function persistQualityDraft(options: {
  repository: FieldDraftRepository
  payload: QualityDraftPayload
  currentDraft: FieldDraft<QualityDraftPayload> | null
  evidence: File | null
  status: 'DRAFT' | 'PENDING'
}): Promise<FieldDraft<QualityDraftPayload>> {
  const { repository, payload, currentDraft, evidence, status } = options
  const id = qualityDraftId(payload)
  const clientRequestId = currentDraft?.clientRequestId ?? crypto.randomUUID()
  payload.command.clientRequestId = clientRequestId
  const draft = await repository.put({
    id,
    kind: payload.kind === 'ISSUE' ? 'QUALITY_ISSUE' : 'QUALITY_RECTIFICATION',
    clientRequestId,
    status,
    payload,
  })
  if (evidence) await repository.putAttachment(id, evidence)
  return draft
}

export async function restoreQualityDraft(
  repository: FieldDraftRepository,
  payload: QualityDraftPayload,
): Promise<FieldDraft<QualityDraftPayload> | null> {
  const draft = await repository.get<QualityDraftPayload>(qualityDraftId(payload))
  return !draft || draft.status === 'SYNCED' ? null : draft
}

export async function synchronizeQualityDraft(options: {
  repository: FieldDraftRepository
  draft: FieldDraft<QualityDraftPayload>
  online: boolean
}): Promise<QualityDraftSyncResult> {
  const { repository, draft, online } = options
  if (!online) {
    return {
      kind: 'OFFLINE',
      draft: await repository.put({ ...draft, status: 'RETRYABLE', error: '当前离线' }),
    }
  }
  const attachments = await repository.attachments(draft.id)
  if (!attachments.length) return { kind: 'MISSING_EVIDENCE', draft }
  try {
    await repository.put({ ...draft, status: 'SYNCING' })
    if (draft.payload.kind === 'ISSUE') {
      const created = await createQualityIssue(draft.payload.inspectionId, draft.payload.command)
      await uploadDraftAttachments(attachments, 'QS_ISSUE', created.id, 'ISSUE_EVIDENCE')
    } else {
      const created = await createQualityRectification(draft.payload.command)
      if (created.status === 'DRAFT') {
        await uploadDraftAttachments(
          attachments,
          'QS_RECTIFICATION',
          created.id,
          'RECTIFICATION_EVIDENCE',
        )
        await submitQualityRectification(created.id)
      }
    }
    await repository.removeAttachments(draft.id)
    return {
      kind: 'SYNCED',
      draft: await repository.put({ ...draft, status: 'SYNCED' }),
    }
  } catch (error) {
    const code = isApiClientError(error) ? error.code : undefined
    const status = isApiClientError(error) ? error.status : undefined
    const message = isApiClientError(error) ? error.message : '质量安全本地草稿同步失败'
    return {
      kind: 'FAILED',
      draft: await repository.put({
        ...draft,
        status: fieldDraftSyncFailure(code, status),
        error: message,
      }),
      message,
    }
  }
}

async function uploadDraftAttachments(
  attachments: Awaited<ReturnType<FieldDraftRepository['attachments']>>,
  businessType: string,
  businessId: string,
  documentType: string,
): Promise<void> {
  for (const attachment of attachments) {
    await uploadSiteFileIdempotently(
      new File([attachment.file], attachment.name, { type: attachment.type }),
      businessType,
      businessId,
      documentType,
    )
  }
}
