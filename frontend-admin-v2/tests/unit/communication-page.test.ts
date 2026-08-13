import { createPinia, setActivePinia } from 'pinia'
import { enableAutoUnmount, flushPromises, mount } from '@vue/test-utils'
import { readFileSync } from 'node:fs'
import { resolve } from 'node:path'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import CommunicationPage from '@/pages/communication/CommunicationPage.vue'
import * as communication from '@/services/communication'
import { useSessionStore } from '@/stores/session'

vi.mock('@/services/communication', () => ({
  addConversationMembers: vi.fn(),
  closeConversation: vi.fn(),
  createConversation: vi.fn(),
  createMessageDraft: vi.fn(),
  deleteMessageDraft: vi.fn(),
  getCommunicationAttachmentUrl: vi.fn(),
  leaveConversation: vi.fn(),
  loadConversationMembers: vi.fn(),
  loadCommunicationUsers: vi.fn(),
  loadConversations: vi.fn(),
  loadMessages: vi.fn(),
  loadPreviousMessages: vi.fn(),
  markConversationRead: vi.fn(),
  removeConversationMember: vi.fn(),
  renameConversation: vi.fn(),
  sendMessage: vi.fn(),
  transferConversationOwner: vi.fn(),
  updateConversationRole: vi.fn(),
  uploadCommunicationAttachment: vi.fn(),
}))

enableAutoUnmount(afterEach)

const conversation = {
  id: '9007199254740993',
  type: 'DIRECT' as const,
  name: '张三',
  ownerUserId: null,
  lastMessageSeq: '9007199254740995',
  lastMessageAt: '2026-08-05T09:00:00',
  status: 'ACTIVE' as const,
  role: 'MEMBER' as const,
  unreadCount: 1,
}
const unsafeText = '<img src=x onerror=alert(1)>'
const message = {
  id: '9007199254740994',
  conversationId: conversation.id,
  senderId: '2',
  seq: '9007199254740995',
  body: unsafeText,
  senderName: '张三',
  createdAt: '2026-08-05T09:00:00',
  attachments: [],
}

beforeEach(() => {
  setActivePinia(createPinia())
  vi.clearAllMocks()
  sessionStorage.clear()
  useSessionStore().replaceUserInfo({
    tenantId: '1001',
    userId: '1',
    username: 'tester',
    roles: ['USER'],
    permissions: ['communication:view', 'communication:send'],
  })
  vi.mocked(communication.loadConversations).mockResolvedValue([conversation])
  vi.mocked(communication.loadConversationMembers).mockResolvedValue([])
  vi.mocked(communication.loadCommunicationUsers).mockResolvedValue([])
  vi.mocked(communication.loadPreviousMessages).mockResolvedValue([message])
  vi.mocked(communication.markConversationRead).mockResolvedValue()
})

describe('CommunicationPage', () => {
  it('keeps chat unread state separate from the notification center', () => {
    const shell = [
      readFileSync(resolve('src/layouts/AppShell.vue'), 'utf8'),
      readFileSync(resolve('src/layouts/ShellHeaderWorkspace.vue'), 'utf8'),
    ].join('\n')
    const communication = readFileSync(resolve('src/layouts/useShellCommunication.ts'), 'utf8')
    const notifications = readFileSync(resolve('src/layouts/useShellNotifications.ts'), 'utf8')
    expect(shell).toContain('useShellCommunication')
    expect(communication).toContain('loadCommunicationUnreadCount')
    expect(notifications).toContain('loadNotificationSummary')
    expect(shell).toContain('communicationUnreadCount')
    expect(shell).toContain('notificationUnreadCount')
    expect(shell).toContain('to="/communication"')
  })

  it('renders server message body as plain text and advances read cursor', async () => {
    const wrapper = mount(CommunicationPage)
    await flushPromises()

    expect(wrapper.text()).toContain(unsafeText)
    expect(wrapper.find('img').exists()).toBe(false)
    expect(communication.markConversationRead).toHaveBeenCalledWith(
      conversation.id,
      '9007199254740995',
    )
  })

  it('loads only the latest bounded page on first render', async () => {
    const latestPage = Array.from({ length: 100 }, (_, index) => ({
      ...message,
      id: 'message-' + String(index + 1),
      seq: String(9901 + index),
      body: '消息' + String(index + 1),
    }))
    vi.mocked(communication.loadPreviousMessages).mockResolvedValueOnce(latestPage)

    const wrapper = mount(CommunicationPage)
    await flushPromises()

    expect(communication.loadPreviousMessages).toHaveBeenCalledTimes(1)
    expect(communication.loadPreviousMessages).toHaveBeenNthCalledWith(
      1,
      conversation.id,
      '0',
      100,
      expect.any(AbortSignal),
    )
    expect(wrapper.findAll('.communication-page__messages article')).toHaveLength(100)
    expect(communication.markConversationRead).toHaveBeenCalledWith(conversation.id, '10000')
  })

  it('prepends older pages without exceeding the 200-message DOM window', async () => {
    const page = (start: number) =>
      Array.from({ length: 100 }, (_, index) => ({
        ...message,
        id: `message-${start + index}`,
        seq: String(start + index),
        body: `消息-${start + index}`,
      }))
    vi.mocked(communication.loadPreviousMessages)
      .mockResolvedValueOnce(page(9901))
      .mockResolvedValueOnce(page(9801))
      .mockResolvedValueOnce(page(9701))
    const wrapper = mount(CommunicationPage)
    await flushPromises()

    const loadEarlier = () =>
      wrapper.findAll('button').find((button) => button.text().includes('加载更早消息'))!
    await loadEarlier().trigger('click')
    await flushPromises()
    await loadEarlier().trigger('click')
    await flushPromises()

    const articles = wrapper.findAll('.communication-page__messages article')
    expect(articles).toHaveLength(200)
    expect(articles[0]!.text()).toContain('消息-9701')
    expect(articles.at(-1)!.text()).toContain('消息-9900')
    expect(wrapper.text()).not.toContain('消息-10000')
    expect(communication.markConversationRead).toHaveBeenCalledTimes(1)
  })

  it('does not append SSE while browsing history and jumps to one latest snapshot', async () => {
    const page = (start: number) =>
      Array.from({ length: 100 }, (_, index) => ({
        ...message,
        id: `message-${start + index}`,
        seq: String(start + index),
        body: `消息-${start + index}`,
      }))
    vi.mocked(communication.loadPreviousMessages)
      .mockResolvedValueOnce(page(9901))
      .mockResolvedValueOnce(page(9801))
    const wrapper = mount(CommunicationPage)
    await flushPromises()
    await wrapper
      .findAll('button')
      .find((button) => button.text().includes('加载更早消息'))!
      .trigger('click')
    await flushPromises()
    vi.mocked(communication.markConversationRead).mockClear()

    window.dispatchEvent(
      new CustomEvent('communication-refresh', {
        detail: { action: 'REFRESH', conversationId: conversation.id, seq: '10001' },
      }),
    )
    await flushPromises()

    expect(communication.loadPreviousMessages).toHaveBeenCalledTimes(2)
    expect(wrapper.text()).toContain('有新消息，跳至最新')
    expect(communication.markConversationRead).not.toHaveBeenCalled()

    vi.mocked(communication.loadPreviousMessages).mockResolvedValueOnce(page(9902))
    await wrapper
      .findAll('button')
      .find((button) => button.text().includes('有新消息，跳至最新'))!
      .trigger('click')
    await flushPromises()

    expect(communication.loadPreviousMessages).toHaveBeenLastCalledWith(
      conversation.id,
      '0',
      100,
      expect.any(AbortSignal),
    )
    expect(communication.markConversationRead).toHaveBeenCalledWith(conversation.id, '10001')
    expect(wrapper.findAll('.communication-page__messages article')).toHaveLength(100)
  })

  it('ignores stale history when a later conversation wins', async () => {
    const conversationB = { ...conversation, id: '9007199254740099', name: '李四' }
    const messageA = { ...message, body: 'A的慢响应' }
    const messageB = {
      ...message,
      id: '9007199254740100',
      conversationId: conversationB.id,
      seq: '2',
      body: 'B的消息',
    }
    let resolveA!: (messages: Array<typeof message>) => void
    let slowSignal: AbortSignal | undefined
    const delayedA = new Promise<Array<typeof message>>((resolve) => {
      resolveA = resolve
    })
    vi.mocked(communication.loadConversations).mockResolvedValue([conversation, conversationB])
    vi.mocked(communication.loadPreviousMessages)
      .mockResolvedValueOnce([message])
      .mockImplementation((id, _beforeSeq, _pageSize, signal) => {
        if (id === conversation.id) {
          slowSignal = signal
          return delayedA
        }
        return Promise.resolve([messageB])
      })
    const wrapper = mount(CommunicationPage)
    await flushPromises()

    const buttonA = wrapper.findAll('button').find((button) => button.text().includes('张三'))
    const buttonB = wrapper.findAll('button').find((button) => button.text().includes('李四'))
    expect(buttonA).toBeDefined()
    expect(buttonB).toBeDefined()
    await buttonA!.trigger('click')
    await buttonB!.trigger('click')
    await flushPromises()
    expect(slowSignal?.aborted).toBe(true)
    resolveA([messageA])
    await flushPromises()

    expect(wrapper.text()).toContain('B的消息')
    expect(wrapper.text()).not.toContain('A的慢响应')
    expect(communication.markConversationRead).toHaveBeenCalledWith(conversationB.id, '2')
  })

  it('creates then finalizes one text draft', async () => {
    vi.mocked(communication.createMessageDraft).mockResolvedValue({
      ...message,
      id: 'draft',
      seq: null,
    })
    vi.mocked(communication.sendMessage).mockResolvedValue({ ...message, id: 'sent', body: '你好' })
    const wrapper = mount(CommunicationPage)
    await flushPromises()

    await wrapper.get('#communication-message').setValue('你好')
    await wrapper.get('form').trigger('submit')
    await flushPromises()

    expect(communication.createMessageDraft).toHaveBeenCalledWith(
      conversation.id,
      '你好',
      expect.any(String),
    )
    expect(communication.sendMessage).toHaveBeenCalledWith('draft')
    expect(wrapper.text()).toContain('消息已发送')
  })

  it('does not append or mark read when send completes after switching conversations', async () => {
    const conversationB = { ...conversation, id: 'conversation-b', name: '李四' }
    const messageB = {
      ...message,
      id: 'message-b',
      conversationId: conversationB.id,
      seq: '2',
      body: 'B的消息',
    }
    let resolveSend!: (value: typeof message) => void
    vi.mocked(communication.loadConversations).mockResolvedValue([conversation, conversationB])
    vi.mocked(communication.loadPreviousMessages).mockImplementation((id) =>
      Promise.resolve(id === conversationB.id ? [messageB] : [message]),
    )
    vi.mocked(communication.createMessageDraft).mockResolvedValue({
      ...message,
      id: 'draft-a',
      seq: null,
      body: '发给A',
    })
    vi.mocked(communication.sendMessage).mockReturnValue(
      new Promise((resolve) => {
        resolveSend = resolve
      }),
    )
    const wrapper = mount(CommunicationPage)
    await flushPromises()

    await wrapper.get('#communication-message').setValue('发给A')
    await wrapper.get('form').trigger('submit')
    await flushPromises()
    const buttonB = wrapper.findAll('button').find((button) => button.text().includes('李四'))
    await buttonB!.trigger('click')
    await flushPromises()
    vi.mocked(communication.markConversationRead).mockClear()

    resolveSend({ ...message, id: 'sent-a', seq: '3', body: '发给A' })
    await flushPromises()

    const visibleMessages = wrapper.findAll('.communication-page__messages article')
    expect(visibleMessages).toHaveLength(1)
    expect(visibleMessages[0]!.text()).toContain('B的消息')
    expect(wrapper.text()).not.toContain('消息已发送')
    expect(communication.markConversationRead).not.toHaveBeenCalled()
  })

  it('reuses the same idempotency key when finalize response is lost', async () => {
    vi.mocked(communication.createMessageDraft).mockResolvedValue({
      ...message,
      id: 'draft',
      seq: null,
      body: '你好',
    })
    vi.mocked(communication.sendMessage)
      .mockRejectedValueOnce(new Error('response lost'))
      .mockResolvedValueOnce({ ...message, id: 'sent', body: '你好' })
    const wrapper = mount(CommunicationPage)
    await flushPromises()

    await wrapper.get('#communication-message').setValue('你好')
    await wrapper.get('form').trigger('submit')
    await flushPromises()
    const firstClientId = vi.mocked(communication.createMessageDraft).mock.calls[0]?.[2]
    await wrapper.get('form').trigger('submit')
    await flushPromises()

    expect(communication.createMessageDraft).toHaveBeenNthCalledWith(
      2,
      conversation.id,
      '你好',
      firstClientId,
    )
    expect(communication.sendMessage).toHaveBeenCalledTimes(2)
  })

  it('abandons old draft when attachment payload changes', async () => {
    const fileA = new File(['a'], 'a.txt', { type: 'text/plain', lastModified: 1 })
    const fileB = new File(['b'], 'b.txt', { type: 'text/plain', lastModified: 2 })
    const fileC = new File(['c'], 'c.txt', { type: 'text/plain', lastModified: 3 })
    vi.mocked(communication.createMessageDraft)
      .mockResolvedValueOnce({ ...message, id: 'draft-a', seq: null, body: '', attachments: [] })
      .mockResolvedValueOnce({ ...message, id: 'draft-b', seq: null, body: '', attachments: [] })
    vi.mocked(communication.uploadCommunicationAttachment)
      .mockResolvedValueOnce({} as never)
      .mockRejectedValueOnce(new Error('upload failed'))
      .mockResolvedValue({} as never)
    vi.mocked(communication.sendMessage).mockResolvedValue({ ...message, id: 'sent' })
    const wrapper = mount(CommunicationPage)
    await flushPromises()
    const input = wrapper.get('input[type="file"]').element as HTMLInputElement

    Object.defineProperty(input, 'files', { configurable: true, value: [fileA, fileB] })
    await wrapper.get('input[type="file"]').trigger('change')
    await wrapper.get('form').trigger('submit')
    await flushPromises()
    const firstClientId = vi.mocked(communication.createMessageDraft).mock.calls[0]?.[2]

    Object.defineProperty(input, 'files', { configurable: true, value: [fileC, fileB] })
    await wrapper.get('input[type="file"]').trigger('change')
    await wrapper.get('form').trigger('submit')
    await flushPromises()

    expect(communication.deleteMessageDraft).toHaveBeenCalledWith('draft-a')
    expect(vi.mocked(communication.createMessageDraft).mock.calls[1]?.[2]).not.toBe(firstClientId)
    expect(communication.uploadCommunicationAttachment).toHaveBeenNthCalledWith(3, fileC, 'draft-b')
    expect(communication.sendMessage).toHaveBeenCalledWith('draft-b')
  })

  it('filters group member candidates by action and requester role', async () => {
    const group = {
      ...conversation,
      type: 'GROUP' as const,
      name: '项目群',
      ownerUserId: '1',
      role: 'OWNER' as const,
    }
    vi.mocked(communication.loadConversations).mockResolvedValue([group])
    vi.mocked(communication.loadCommunicationUsers).mockResolvedValue([
      { id: '2', username: 'admin', realName: '管理员' },
      { id: '3', username: 'member', realName: '成员' },
      { id: '5', username: 'outsider', realName: '待加入' },
    ])
    vi.mocked(communication.loadConversationMembers).mockResolvedValue([
      { userId: '1', username: 'owner', role: 'OWNER', userStatus: 'ENABLE' },
      { userId: '2', username: 'admin', role: 'ADMIN', userStatus: 'ENABLE' },
      { userId: '3', username: 'member', role: 'MEMBER', userStatus: 'ENABLE' },
      { userId: '4', username: 'disabled', role: 'MEMBER', userStatus: 'DISABLE' },
    ])
    const wrapper = mount(CommunicationPage)
    await flushPromises()
    const targetValues = () =>
      wrapper
        .get('#communication-member-target')
        .findAll('option')
        .map((option) => option.attributes('value'))

    expect(targetValues()).toEqual(['', '5'])
    await wrapper.get('#communication-member-action').setValue('REMOVE')
    expect(targetValues()).toEqual(['', '2', '3', '4'])
    await wrapper.get('#communication-member-action').setValue('SET_ADMIN')
    expect(targetValues()).toEqual(['', '3', '4'])
    await wrapper.get('#communication-member-action').setValue('SET_MEMBER')
    expect(targetValues()).toEqual(['', '2'])
    await wrapper.get('#communication-member-action').setValue('TRANSFER_OWNER')
    expect(targetValues()).toEqual(['', '2', '3'])
  })

  it('lets an administrator remove members but not owners or other administrators', async () => {
    const group = {
      ...conversation,
      type: 'GROUP' as const,
      name: '项目群',
      ownerUserId: '9',
      role: 'ADMIN' as const,
    }
    vi.mocked(communication.loadConversations).mockResolvedValue([group])
    vi.mocked(communication.loadConversationMembers).mockResolvedValue([
      { userId: '9', username: 'owner', role: 'OWNER', userStatus: 'ENABLE' },
      { userId: '2', username: 'admin', role: 'ADMIN', userStatus: 'ENABLE' },
      { userId: '3', username: 'member', role: 'MEMBER', userStatus: 'ENABLE' },
    ])
    const wrapper = mount(CommunicationPage)
    await flushPromises()

    expect(
      wrapper
        .get('#communication-member-action')
        .findAll('option')
        .map((option) => option.attributes('value')),
    ).toEqual(['ADD', 'REMOVE'])
    await wrapper.get('#communication-member-action').setValue('REMOVE')
    expect(
      wrapper
        .get('#communication-member-target')
        .findAll('option')
        .map((option) => option.attributes('value')),
    ).toEqual(['', '3'])
  })

  it('ignores stale group members when a later conversation wins', async () => {
    const groupA = {
      ...conversation,
      type: 'GROUP' as const,
      name: 'A群',
      ownerUserId: '1',
      role: 'OWNER' as const,
    }
    const groupB = { ...groupA, id: 'group-b', name: 'B群' }
    const membersA = [
      { userId: '2', username: 'user-a', role: 'MEMBER' as const, userStatus: 'ENABLE' },
    ]
    const membersB = [
      { userId: '3', username: 'user-b', role: 'MEMBER' as const, userStatus: 'ENABLE' },
    ]
    let delayA = false
    let resolveA!: (members: typeof membersA) => void
    vi.mocked(communication.loadConversations).mockResolvedValue([groupA, groupB])
    vi.mocked(communication.loadCommunicationUsers).mockResolvedValue([
      { id: '2', username: 'user-a' },
      { id: '3', username: 'user-b' },
    ])
    vi.mocked(communication.loadConversationMembers).mockImplementation((id) => {
      if (id === groupA.id && delayA) {
        return new Promise((resolve) => {
          resolveA = resolve
        })
      }
      return Promise.resolve(id === groupA.id ? membersA : membersB)
    })
    const wrapper = mount(CommunicationPage)
    await flushPromises()
    delayA = true

    const buttonA = wrapper.findAll('button').find((button) => button.text().includes('A群'))
    const buttonB = wrapper.findAll('button').find((button) => button.text().includes('B群'))
    await buttonA!.trigger('click')
    await buttonB!.trigger('click')
    await flushPromises()
    resolveA(membersA)
    await flushPromises()

    const values = wrapper
      .get('#communication-member-target')
      .findAll('option')
      .map((option) => option.attributes('value'))
    expect(values).toEqual(['', '2'])
  })

  it('does not reload members with old owner privileges after transfer', async () => {
    let role: 'OWNER' | 'MEMBER' = 'OWNER'
    const group = () => ({
      ...conversation,
      type: 'GROUP' as const,
      name: '项目群',
      ownerUserId: role === 'OWNER' ? '1' : '2',
      role,
    })
    vi.mocked(communication.loadConversations).mockImplementation(() => Promise.resolve([group()]))
    vi.mocked(communication.loadConversationMembers).mockResolvedValue([
      { userId: '1', username: 'owner', role: 'OWNER', userStatus: 'ENABLE' },
      { userId: '2', username: 'next-owner', role: 'MEMBER', userStatus: 'ENABLE' },
    ])
    vi.mocked(communication.transferConversationOwner).mockImplementation(() => {
      role = 'MEMBER'
      return Promise.resolve()
    })
    const wrapper = mount(CommunicationPage)
    await flushPromises()

    await wrapper.get('#communication-member-action').setValue('TRANSFER_OWNER')
    await wrapper.get('#communication-member-target').setValue('2')
    await wrapper
      .get('.communication-page__group .communication-page__actions button')
      .trigger('click')
    await flushPromises()

    expect(communication.transferConversationOwner).toHaveBeenCalledWith(conversation.id, '2')
    expect(communication.loadConversationMembers).toHaveBeenCalledTimes(1)
    expect(wrapper.find('.communication-page__group').exists()).toBe(false)
  })
})
