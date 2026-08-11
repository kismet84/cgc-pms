import type { MessageRecord } from '@cgc-pms/frontend-contracts'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import {
  messageSendFingerprint,
  sendCommunicationMessage,
  type SendMessageDependencies,
} from '@/pages/communication/application/send-message'
import { createSessionSendRetryStore } from '@/pages/communication/infrastructure/session-send-retry-store'

const draft: MessageRecord = {
  id: 'draft-1',
  conversationId: 'conversation-1',
  senderId: 'user-1',
  seq: null,
  body: '你好',
  senderName: '测试用户',
  createdAt: '2026-08-11T00:00:00Z',
  attachments: [],
}
const sent: MessageRecord = { ...draft, id: 'message-1', seq: '1' }

beforeEach(() => {
  sessionStorage.clear()
  vi.clearAllMocks()
})

describe('sendCommunicationMessage', () => {
  it('reuses one idempotency key after a lost finalize response', async () => {
    const createId = vi.fn(() => 'client-message-1')
    const createDraft = vi.fn(async () => draft)
    const sendDraft = vi
      .fn<(draftId: string) => Promise<MessageRecord>>()
      .mockRejectedValueOnce(new Error('response lost'))
      .mockResolvedValueOnce(sent)
    const dependencies = {
      retryStore: createSessionSendRetryStore(sessionStorage),
      now: () => 1_000,
      createId,
      createDraft,
      deleteDraft: vi.fn(async () => undefined),
      uploadAttachment: vi.fn(async () => undefined),
      sendDraft,
    } satisfies SendMessageDependencies
    const command = { conversationId: 'conversation-1', body: ' 你好 ', attachments: [] }

    await expect(sendCommunicationMessage(command, dependencies)).rejects.toThrow('response lost')
    const result = await sendCommunicationMessage(command, dependencies)

    expect(createDraft).toHaveBeenNthCalledWith(1, 'conversation-1', '你好', 'client-message-1')
    expect(createDraft).toHaveBeenNthCalledWith(2, 'conversation-1', '你好', 'client-message-1')
    expect(createId).toHaveBeenCalledTimes(1)
    expect(result.message).toEqual(sent)
    expect(dependencies.retryStore.load('conversation-1')).toBeNull()
  })

  it('creates the replacement key after best-effort stale draft cleanup', async () => {
    const retryStore = createSessionSendRetryStore(sessionStorage)
    retryStore.save('conversation-1', {
      id: 'old-client-message',
      createdAt: 100,
      fingerprint: 'old-payload',
      draftId: 'old-draft',
    })
    const now = vi.fn().mockReturnValueOnce(1_000).mockReturnValueOnce(2_000)
    const deleteDraft = vi.fn(async () => undefined)
    const createDraft = vi.fn(async () => draft)
    const dependencies = {
      retryStore,
      now,
      createId: () => 'new-client-message',
      createDraft,
      deleteDraft,
      uploadAttachment: vi.fn(async () => undefined),
      sendDraft: vi.fn(async () => {
        throw new Error('offline')
      }),
    } satisfies SendMessageDependencies

    await expect(
      sendCommunicationMessage(
        { conversationId: 'conversation-1', body: '新内容', attachments: [] },
        dependencies,
      ),
    ).rejects.toThrow('offline')

    expect(deleteDraft).toHaveBeenCalledWith('old-draft')
    expect(createDraft).toHaveBeenCalledWith('conversation-1', '新内容', 'new-client-message')
    expect(retryStore.load('conversation-1')).toMatchObject({
      id: 'new-client-message',
      createdAt: 2_000,
      draftId: 'draft-1',
    })
  })

  it('treats the exact TTL boundary as expired', async () => {
    const retryStore = createSessionSendRetryStore(sessionStorage)
    const fingerprint = messageSendFingerprint('你好', [])
    retryStore.save('conversation-1', {
      id: 'expired-client-message',
      createdAt: 1_000,
      fingerprint,
      draftId: 'expired-draft',
    })
    const deleteDraft = vi.fn(async () => undefined)
    const createDraft = vi.fn(async () => draft)
    const dependencies = {
      retryStore,
      now: () => 1_000 + 24 * 60 * 60 * 1_000,
      createId: () => 'new-client-message',
      createDraft,
      deleteDraft,
      uploadAttachment: vi.fn(async () => undefined),
      sendDraft: vi.fn(async () => {
        throw new Error('offline')
      }),
    } satisfies SendMessageDependencies

    await expect(
      sendCommunicationMessage(
        { conversationId: 'conversation-1', body: '你好', attachments: [] },
        dependencies,
      ),
    ).rejects.toThrow('offline')

    expect(deleteDraft).not.toHaveBeenCalled()
    expect(createDraft).toHaveBeenCalledWith('conversation-1', '你好', 'new-client-message')
  })

  it('uploads only missing attachments and skips finalization for a sent draft', async () => {
    const first = new File(['first'], 'first.txt', { type: 'text/plain', lastModified: 1 })
    const second = new File(['second'], 'second.txt', { type: 'text/plain', lastModified: 2 })
    const resumed: MessageRecord = {
      ...sent,
      attachments: [
        {
          id: 'attachment-1',
          originalName: first.name,
          fileSize: first.size,
          contentType: first.type,
          virusScanStatus: 'CLEAN',
        },
      ],
    }
    const retryStore = createSessionSendRetryStore(sessionStorage)
    const uploadAttachment = vi.fn(async () => undefined)
    const sendDraft = vi.fn(async () => sent)
    const dependencies = {
      retryStore,
      now: () => 1_000,
      createId: () => 'client-message-1',
      createDraft: vi.fn(async () => resumed),
      deleteDraft: vi.fn(async () => undefined),
      uploadAttachment,
      sendDraft,
    } satisfies SendMessageDependencies

    const result = await sendCommunicationMessage(
      {
        conversationId: 'conversation-1',
        body: '你好',
        attachments: [first, second],
      },
      dependencies,
    )

    expect(uploadAttachment).toHaveBeenCalledOnce()
    expect(uploadAttachment).toHaveBeenCalledWith(second, 'message-1')
    expect(sendDraft).not.toHaveBeenCalled()
    expect(result.message).toEqual(resumed)
    expect(retryStore.load('conversation-1')).toBeNull()
  })
})
