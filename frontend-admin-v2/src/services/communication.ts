import type {
  CommunicationEvent,
  CommunicationUnreadCount,
  CommunicationUserSummary,
  ConversationSummary,
  MessageRecord,
} from '@cgc-pms/frontend-contracts'
import { getSiteFileUrl, uploadSiteFile } from '@/services/delivery'
import { apiRequest } from '@/services/request'

const BASE = '/communications'

function id(value: string): string {
  const normalized = value.trim()
  if (!normalized) throw new TypeError('通讯ID不能为空')
  return encodeURIComponent(normalized)
}

export function loadCommunicationUsers(
  keyword = '',
  signal?: AbortSignal,
): Promise<CommunicationUserSummary[]> {
  const query = new URLSearchParams()
  if (keyword.trim()) query.set('keyword', keyword.trim())
  return apiRequest(`${BASE}/users${query.size ? `?${query}` : ''}`, { signal })
}

export function loadConversations(signal?: AbortSignal): Promise<ConversationSummary[]> {
  return apiRequest(`${BASE}/conversations`, { signal })
}

export function createConversation(command: {
  type: 'DIRECT' | 'GROUP'
  name?: string
  memberIds: string[]
}): Promise<ConversationSummary> {
  return apiRequest(`${BASE}/conversations`, { method: 'POST', body: command })
}

export function renameConversation(conversationId: string, name: string) {
  return apiRequest<ConversationSummary>(`${BASE}/conversations/${id(conversationId)}`, {
    method: 'PATCH',
    body: { name: name.trim() },
  })
}

export function addConversationMembers(conversationId: string, userIds: string[]) {
  return apiRequest<ConversationSummary>(`${BASE}/conversations/${id(conversationId)}/members`, {
    method: 'POST',
    body: { userIds },
  })
}

export function removeConversationMember(conversationId: string, userId: string) {
  return apiRequest<void>(`${BASE}/conversations/${id(conversationId)}/members/${id(userId)}`, {
    method: 'DELETE',
  })
}

export function updateConversationRole(
  conversationId: string,
  userId: string,
  role: 'ADMIN' | 'MEMBER',
) {
  return apiRequest<void>(
    `${BASE}/conversations/${id(conversationId)}/members/${id(userId)}/role`,
    { method: 'PUT', body: { role } },
  )
}

export function transferConversationOwner(conversationId: string, userId: string) {
  return apiRequest<void>(`${BASE}/conversations/${id(conversationId)}/owner`, {
    method: 'PUT',
    body: { userId },
  })
}

export function leaveConversation(conversationId: string) {
  return apiRequest<void>(`${BASE}/conversations/${id(conversationId)}/leave`, { method: 'POST' })
}

export function closeConversation(conversationId: string) {
  return apiRequest<void>(`${BASE}/conversations/${id(conversationId)}/close`, { method: 'POST' })
}

export function loadMessages(
  conversationId: string,
  afterSeq = '0',
  pageSize = 50,
  signal?: AbortSignal,
): Promise<MessageRecord[]> {
  const query = new URLSearchParams({ afterSeq, pageSize: String(pageSize) })
  return apiRequest(`${BASE}/conversations/${id(conversationId)}/messages?${query}`, { signal })
}

export function createMessageDraft(conversationId: string, body: string, clientMessageId: string) {
  return apiRequest<MessageRecord>(`${BASE}/conversations/${id(conversationId)}/drafts`, {
    method: 'POST',
    body: { body, clientMessageId },
  })
}

export function sendMessage(draftId: string) {
  return apiRequest<MessageRecord>(`${BASE}/messages/${id(draftId)}/send`, { method: 'POST' })
}

export function deleteMessageDraft(draftId: string) {
  return apiRequest<void>(`${BASE}/messages/${id(draftId)}`, { method: 'DELETE' })
}

export function markConversationRead(conversationId: string, seq: string) {
  return apiRequest<void>(`${BASE}/conversations/${id(conversationId)}/read`, {
    method: 'PUT',
    body: { seq },
  })
}

export function loadCommunicationUnreadCount(signal?: AbortSignal) {
  return apiRequest<CommunicationUnreadCount>(`${BASE}/unread-count`, { signal })
}

export function uploadCommunicationAttachment(file: File, draftId: string) {
  return uploadSiteFile(file, 'COMMUNICATION_MESSAGE', draftId, 'CHAT_ATTACHMENT')
}

export function getCommunicationAttachmentUrl(fileId: string) {
  return getSiteFileUrl(fileId)
}

export function openCommunicationStream(
  onEvent: (event: CommunicationEvent) => void,
  onError?: () => void,
): EventSource {
  const stream = new EventSource('/api/communications/stream')
  const receive = (event: MessageEvent<string>) => {
    try {
      onEvent(JSON.parse(event.data) as CommunicationEvent)
    } catch {
      onError?.()
    }
  }
  stream.addEventListener('connected', receive as EventListener)
  stream.addEventListener('communication', receive as EventListener)
  stream.onerror = () => onError?.()
  return stream
}
