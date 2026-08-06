import { beforeEach, describe, expect, it, vi } from 'vitest'
import {
  createConversation,
  createMessageDraft,
  loadCommunicationUsers,
  loadMessages,
  markConversationRead,
  openCommunicationStream,
  uploadCommunicationAttachment,
} from '@/services/communication'
import { uploadSiteFile } from '@/services/delivery'
import { apiRequest } from '@/services/request'

vi.mock('@/services/request', () => ({ apiRequest: vi.fn() }))
vi.mock('@/services/delivery', () => ({
  uploadSiteFile: vi.fn(),
  getSiteFileUrl: vi.fn(),
}))

beforeEach(() => vi.clearAllMocks())

describe('communication service', () => {
  it('keeps IDs and sequence values as strings in REST contracts', async () => {
    vi.mocked(apiRequest).mockResolvedValue([])

    await loadCommunicationUsers(' 张 三 ')
    await loadMessages('9007199254740993', '9007199254740995', 25)
    await createConversation({ type: 'DIRECT', memberIds: ['9007199254740997'] })
    await createMessageDraft('9007199254740993', 'hello', 'client-1')
    await markConversationRead('9007199254740993', '9007199254740995')

    expect(apiRequest).toHaveBeenNthCalledWith(
      1,
      '/communications/users?keyword=%E5%BC%A0+%E4%B8%89',
      {
        signal: undefined,
      },
    )
    expect(apiRequest).toHaveBeenNthCalledWith(
      2,
      '/communications/conversations/9007199254740993/messages?afterSeq=9007199254740995&pageSize=25',
      { signal: undefined },
    )
    expect(apiRequest).toHaveBeenNthCalledWith(3, '/communications/conversations', {
      method: 'POST',
      body: { type: 'DIRECT', memberIds: ['9007199254740997'] },
    })
    expect(apiRequest).toHaveBeenNthCalledWith(
      5,
      '/communications/conversations/9007199254740993/read',
      { method: 'PUT', body: { seq: '9007199254740995' } },
    )
  })

  it('reuses the governed file upload binding', async () => {
    const file = new File(['safe'], 'evidence.txt', { type: 'text/plain' })
    await uploadCommunicationAttachment(file, 'draft-1')
    expect(uploadSiteFile).toHaveBeenCalledWith(
      file,
      'COMMUNICATION_MESSAGE',
      'draft-1',
      'CHAT_ATTACHMENT',
    )
  })

  it('parses named SSE hints without exposing message content', () => {
    class FakeEventSource {
      static instance: FakeEventSource
      onerror: (() => void) | null = null
      listeners = new Map<string, EventListener>()
      constructor(readonly url: string) {
        FakeEventSource.instance = this
      }
      addEventListener(name: string, listener: EventListener) {
        this.listeners.set(name, listener)
      }
      close() {}
    }
    vi.stubGlobal('EventSource', FakeEventSource)
    const received = vi.fn()

    openCommunicationStream(received)
    FakeEventSource.instance.listeners.get('communication')?.(
      new MessageEvent('communication', {
        data: JSON.stringify({
          action: 'REFRESH',
          conversationId: '9',
          messageId: '10',
          seq: '11',
        }),
      }),
    )

    expect(FakeEventSource.instance.url).toMatch(
      /^\/api\/communications\/stream\?clientId=[0-9a-f-]+$/,
    )
    expect(received).toHaveBeenCalledWith({
      action: 'REFRESH',
      conversationId: '9',
      messageId: '10',
      seq: '11',
    })
    vi.unstubAllGlobals()
  })
})
