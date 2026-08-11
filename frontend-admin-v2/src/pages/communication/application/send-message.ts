import type { MessageRecord } from '@cgc-pms/frontend-contracts'

const SEND_RETRY_TTL_MS = 24 * 60 * 60 * 1_000

export interface SendRetryState {
  id: string
  createdAt: number
  fingerprint: string
  draftId?: string
}

export interface SendRetryStore {
  load(conversationId: string): SendRetryState | null
  save(conversationId: string, state: SendRetryState): void
  clear(conversationId: string): void
}

export interface SendMessageDependencies {
  retryStore: SendRetryStore
  now(): number
  createId(): string
  createDraft(conversationId: string, body: string, clientMessageId: string): Promise<MessageRecord>
  deleteDraft(draftId: string): Promise<unknown>
  uploadAttachment(file: File, draftId: string): Promise<unknown>
  sendDraft(draftId: string): Promise<MessageRecord>
}

export interface SendMessageCommand {
  conversationId: string
  body: string
  attachments: readonly File[]
}

export function messageSendFingerprint(body: string, attachments: readonly File[]): string {
  return JSON.stringify({
    body: body.trim(),
    files: attachments.map(({ name, size, type, lastModified }) => ({
      name,
      size,
      type,
      lastModified,
    })),
  })
}

export async function sendCommunicationMessage(
  command: SendMessageCommand,
  dependencies: SendMessageDependencies,
): Promise<{ message: MessageRecord; fingerprint: string }> {
  const fingerprint = messageSendFingerprint(command.body, command.attachments)
  const saved = dependencies.retryStore.load(command.conversationId)
  let retry: SendRetryState | null = null

  if (saved && dependencies.now() - saved.createdAt < SEND_RETRY_TTL_MS) {
    if (saved.fingerprint === fingerprint) {
      retry = saved
    } else if (saved.draftId) {
      try {
        await dependencies.deleteDraft(saved.draftId)
      } catch {
        // Stale draft cleanup is best effort; a new idempotency key must still be created.
      }
    }
  }

  if (!retry) {
    retry = { id: dependencies.createId(), createdAt: dependencies.now(), fingerprint }
    dependencies.retryStore.save(command.conversationId, retry)
  }

  const draft = await dependencies.createDraft(
    command.conversationId,
    command.body.trim(),
    retry.id,
  )
  dependencies.retryStore.save(command.conversationId, { ...retry, draftId: draft.id })

  for (const file of command.attachments.slice(draft.attachments.length)) {
    await dependencies.uploadAttachment(file, draft.id)
  }

  const message = draft.seq ? draft : await dependencies.sendDraft(draft.id)
  dependencies.retryStore.clear(command.conversationId)
  return { message, fingerprint }
}
