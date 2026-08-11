<script setup lang="ts">
import type {
  CommunicationEvent,
  CommunicationMemberSummary,
  CommunicationUserSummary,
  ConversationSummary,
  MessageRecord,
} from '@cgc-pms/frontend-contracts'
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { V2Card, V2ConfirmDialog } from '@/components'
import {
  addConversationMembers,
  closeConversation,
  createConversation,
  createMessageDraft,
  deleteMessageDraft,
  getCommunicationAttachmentUrl,
  leaveConversation,
  loadConversationMembers,
  loadCommunicationUsers,
  loadConversations,
  loadMessages,
  markConversationRead,
  removeConversationMember,
  renameConversation,
  sendMessage,
  transferConversationOwner,
  updateConversationRole,
  uploadCommunicationAttachment,
} from '@/services/communication'
import { useSessionStore } from '@/stores/session'

const session = useSessionStore()
const conversations = ref<ConversationSummary[]>([])
const users = ref<CommunicationUserSummary[]>([])
const members = ref<CommunicationMemberSummary[]>([])
const messages = ref<MessageRecord[]>([])
const selectedConversationId = ref('')
const selectedUserIds = ref<string[]>([])
const memberTargetId = ref('')
type MemberAction = 'ADD' | 'REMOVE' | 'SET_ADMIN' | 'SET_MEMBER' | 'TRANSFER_OWNER'
const memberAction = ref<MemberAction>('ADD')
const userKeyword = ref('')
const groupName = ref('')
const body = ref('')
const attachments = ref<File[]>([])
const loading = ref(true)
const messagesLoading = ref(false)
const sending = ref(false)
const error = ref('')
const status = ref('')
const pendingGroupAction = ref<'leave' | 'close' | null>(null)
const messageList = ref<HTMLElement | null>(null)
let requestController: AbortController | null = null
let conversationGeneration = 0
const SEND_RETRY_TTL_MS = 24 * 60 * 60 * 1_000

const selectedConversation = computed(() =>
  conversations.value.find((item) => item.id === selectedConversationId.value),
)
const canSend = computed(
  () =>
    session.hasAdminOrPermission('communication:send') &&
    selectedConversation.value?.status === 'ACTIVE' &&
    Boolean(body.value.trim() || attachments.value.length) &&
    !sending.value,
)
const canCreateGroup = computed(() => session.hasAdminOrPermission('communication:group:manage'))
function isManageableGroup(conversation?: ConversationSummary): boolean {
  return Boolean(
    conversation?.type === 'GROUP' &&
    conversation.status === 'ACTIVE' &&
    ['OWNER', 'ADMIN'].includes(conversation.role),
  )
}
const canManageGroup = computed(() => isManageableGroup(selectedConversation.value))
const memberActionOptions = computed(() => {
  const base = [
    { value: 'ADD' as const, label: '加入' },
    { value: 'REMOVE' as const, label: '移除' },
  ]
  if (selectedConversation.value?.role !== 'OWNER') return base
  return [
    ...base,
    { value: 'SET_ADMIN' as const, label: '设管理员' },
    { value: 'SET_MEMBER' as const, label: '设成员' },
    { value: 'TRANSFER_OWNER' as const, label: '转让群主' },
  ]
})
const memberCandidates = computed(() => {
  if (memberAction.value === 'ADD') {
    const activeIds = new Set(members.value.map((member) => member.userId))
    return users.value
      .filter((user) => !activeIds.has(user.id))
      .map((user) => ({ id: user.id, label: user.realName || user.username }))
  }
  const requesterRole = selectedConversation.value?.role
  return members.value
    .filter((member) => {
      if (memberAction.value === 'REMOVE') {
        return requesterRole === 'OWNER'
          ? ['ADMIN', 'MEMBER'].includes(member.role)
          : member.role === 'MEMBER'
      }
      if (requesterRole !== 'OWNER') return false
      if (memberAction.value === 'SET_ADMIN') return member.role === 'MEMBER'
      if (memberAction.value === 'SET_MEMBER') return member.role === 'ADMIN'
      return member.role !== 'OWNER' && member.userStatus === 'ENABLE'
    })
    .map((member) => ({
      id: member.userId,
      label: member.realName || member.username || member.userId,
    }))
})

watch(memberAction, () => {
  memberTargetId.value = ''
})

onMounted(() => {
  void initialize()
  window.addEventListener('communication-refresh', onCommunicationRefresh)
  window.addEventListener('focus', refreshAfterReconnect)
})

onBeforeUnmount(() => {
  requestController?.abort()
  window.removeEventListener('communication-refresh', onCommunicationRefresh)
  window.removeEventListener('focus', refreshAfterReconnect)
})

async function initialize(): Promise<void> {
  requestController?.abort()
  requestController = new AbortController()
  loading.value = true
  error.value = ''
  try {
    const [nextConversations, nextUsers] = await Promise.all([
      loadConversations(requestController.signal),
      session.hasAdminOrPermission('communication:send')
        ? loadCommunicationUsers('', requestController.signal)
        : Promise.resolve([]),
    ])
    conversations.value = nextConversations
    users.value = nextUsers
    if (!selectedConversationId.value && nextConversations[0]) {
      await selectConversation(nextConversations[0].id)
    }
  } catch {
    if (!requestController.signal.aborted) error.value = '通讯数据加载失败，请重试。'
  } finally {
    if (!requestController.signal.aborted) loading.value = false
  }
}

async function refreshConversations(): Promise<void> {
  conversations.value = await loadConversations()
  if (
    selectedConversationId.value &&
    !conversations.value.some((item) => item.id === selectedConversationId.value)
  ) {
    selectedConversationId.value = ''
    messages.value = []
    members.value = []
  } else if (!isManageableGroup(selectedConversation.value)) {
    members.value = []
  }
}

async function searchUsers(): Promise<void> {
  error.value = ''
  try {
    users.value = await loadCommunicationUsers(userKeyword.value)
  } catch {
    error.value = '用户搜索失败。'
  }
}

async function selectConversation(id: string): Promise<void> {
  const token = ++conversationGeneration
  selectedConversationId.value = id
  members.value = []
  memberTargetId.value = ''
  memberAction.value = 'ADD'
  messagesLoading.value = true
  error.value = ''
  try {
    const conversation = conversations.value.find((item) => item.id === id)
    const [loaded, loadedMembers] = await Promise.all([
      loadAllMessages(id),
      isManageableGroup(conversation) ? loadConversationMembers(id) : Promise.resolve([]),
    ])
    if (token !== conversationGeneration || selectedConversationId.value !== id) return
    messages.value = loaded
    members.value = loadedMembers
    await markCurrentRead()
    if (token !== conversationGeneration || selectedConversationId.value !== id) return
    await refreshConversations()
    if (token !== conversationGeneration || selectedConversationId.value !== id) return
    await scrollToLatest()
  } catch {
    if (token === conversationGeneration) error.value = '消息历史加载失败。'
  } finally {
    if (token === conversationGeneration) messagesLoading.value = false
  }
}

async function loadAllMessages(conversationId: string, afterSeq = '0'): Promise<MessageRecord[]> {
  const result: MessageRecord[] = []
  let cursor = afterSeq
  while (true) {
    const page = await loadMessages(conversationId, cursor, 100)
    result.push(...page)
    const next = page.at(-1)?.seq
    if (page.length < 100 || !next || next === cursor) return result
    cursor = next
  }
}

async function loadNewMessages(): Promise<void> {
  const conversationId = selectedConversationId.value
  if (!conversationId) return
  const token = conversationGeneration
  const afterSeq = messages.value.at(-1)?.seq ?? '0'
  const incoming = await loadAllMessages(conversationId, afterSeq)
  if (token !== conversationGeneration || selectedConversationId.value !== conversationId) return
  const known = new Set(messages.value.map((item) => item.id))
  messages.value.push(...incoming.filter((item) => !known.has(item.id)))
  await markCurrentRead()
  if (token !== conversationGeneration || selectedConversationId.value !== conversationId) return
  await scrollToLatest()
}

async function markCurrentRead(): Promise<void> {
  const last = messages.value.at(-1)
  if (!last || last.seq === '0' || !selectedConversationId.value) return
  await markConversationRead(selectedConversationId.value, last.seq)
  window.dispatchEvent(new Event('communication-unread-changed'))
}

async function onCommunicationRefresh(event: Event): Promise<void> {
  const detail = (event as CustomEvent<CommunicationEvent>).detail
  try {
    await refreshConversations()
    if (!detail?.conversationId || detail.conversationId === selectedConversationId.value) {
      await loadNewMessages()
    }
  } catch {
    status.value = '实时提示已收到，补拉失败；聚焦页面时将重试。'
  }
}

function refreshAfterReconnect(): void {
  void refreshConversations()
    .then(loadNewMessages)
    .catch(() => undefined)
}

async function startDirect(userId: string): Promise<void> {
  error.value = ''
  try {
    const created = await createConversation({ type: 'DIRECT', memberIds: [userId] })
    await refreshConversations()
    await selectConversation(created.id)
  } catch {
    error.value = '私聊创建失败。'
  }
}

async function createGroup(): Promise<void> {
  if (!groupName.value.trim() || !selectedUserIds.value.length) return
  error.value = ''
  try {
    const created = await createConversation({
      type: 'GROUP',
      name: groupName.value.trim(),
      memberIds: selectedUserIds.value,
    })
    groupName.value = ''
    selectedUserIds.value = []
    await refreshConversations()
    await selectConversation(created.id)
  } catch {
    error.value = '群聊创建失败。'
  }
}

function retryStorageKey(conversationId: string): string {
  return `cgc-pms.communication.send.${conversationId}`
}

interface SendRetryState {
  id: string
  createdAt: number
  fingerprint: string
  draftId?: string
}

function sendFingerprint(): string {
  return JSON.stringify({
    body: body.value.trim(),
    files: attachments.value.map(({ name, size, type, lastModified }) => ({
      name,
      size,
      type,
      lastModified,
    })),
  })
}

async function retryState(conversationId: string, fingerprint: string): Promise<SendRetryState> {
  const key = retryStorageKey(conversationId)
  try {
    const saved = JSON.parse(sessionStorage.getItem(key) ?? '{}') as Partial<SendRetryState>
    if (
      saved.id &&
      typeof saved.createdAt === 'number' &&
      typeof saved.fingerprint === 'string' &&
      Date.now() - saved.createdAt < SEND_RETRY_TTL_MS
    ) {
      if (saved.fingerprint === fingerprint) return saved as SendRetryState
      if (saved.draftId) await deleteMessageDraft(saved.draftId).catch(() => undefined)
    }
  } catch {
    sessionStorage.removeItem(key)
  }
  const next = { id: crypto.randomUUID(), createdAt: Date.now(), fingerprint }
  sessionStorage.setItem(key, JSON.stringify(next))
  return next
}

async function send(): Promise<void> {
  if (!canSend.value || !selectedConversationId.value) return
  const conversationId = selectedConversationId.value
  const outgoingBody = body.value.trim()
  const outgoingAttachments = [...attachments.value]
  const fingerprint = sendFingerprint()
  sending.value = true
  error.value = ''
  status.value = '正在发送…'
  try {
    const retry = await retryState(conversationId, fingerprint)
    const draft = await createMessageDraft(conversationId, outgoingBody, retry.id)
    sessionStorage.setItem(
      retryStorageKey(conversationId),
      JSON.stringify({ ...retry, draftId: draft.id }),
    )
    for (const file of outgoingAttachments.slice(draft.attachments.length)) {
      await uploadCommunicationAttachment(file, draft.id)
    }
    const sent = draft.seq ? draft : await sendMessage(draft.id)
    sessionStorage.removeItem(retryStorageKey(conversationId))
    await refreshConversations()
    if (selectedConversationId.value === conversationId) {
      messages.value.push(sent)
      if (sendFingerprint() === fingerprint) {
        body.value = ''
        attachments.value = []
      }
      status.value = '消息已发送。'
      if (sent.seq) {
        await markConversationRead(conversationId, sent.seq)
        window.dispatchEvent(new Event('communication-unread-changed'))
      }
      if (selectedConversationId.value === conversationId) await scrollToLatest()
    }
  } catch {
    if (selectedConversationId.value === conversationId) {
      error.value = '消息发送失败；重试将复用同一草稿和幂等键。'
      status.value = ''
    }
  } finally {
    sending.value = false
  }
}

function chooseAttachments(event: Event): void {
  const input = event.target as HTMLInputElement
  const files = Array.from(input.files ?? [])
  if (files.length > 5) {
    error.value = '每条消息最多选择 5 个附件。'
    input.value = ''
    return
  }
  attachments.value = files
  input.value = ''
}

async function downloadAttachment(fileId: string): Promise<void> {
  const target = window.open('about:blank', '_blank')
  if (target) target.opener = null
  try {
    const url = await getCommunicationAttachmentUrl(fileId)
    if (target) target.location.href = url
    else window.open(url, '_blank', 'noopener,noreferrer')
  } catch {
    target?.close()
    error.value = '附件下载地址获取失败。'
  }
}

async function manageGroup() {
  const conversation = selectedConversation.value
  if (!conversation || !memberTargetId.value) return
  const action = memberAction.value
  const token = conversationGeneration
  error.value = ''
  try {
    if (action === 'ADD') await addConversationMembers(conversation.id, [memberTargetId.value])
    if (action === 'REMOVE') await removeConversationMember(conversation.id, memberTargetId.value)
    if (action === 'SET_ADMIN' || action === 'SET_MEMBER')
      await updateConversationRole(
        conversation.id,
        memberTargetId.value,
        action === 'SET_ADMIN' ? 'ADMIN' : 'MEMBER',
      )
    if (action === 'TRANSFER_OWNER') {
      await transferConversationOwner(conversation.id, memberTargetId.value)
      members.value = []
      memberTargetId.value = ''
      await refreshConversations()
      if (token === conversationGeneration && selectedConversationId.value === conversation.id) {
        status.value = '群成员操作已完成。'
      }
      return
    }
    status.value = '群成员操作已完成。'
    await refreshConversations()
    if (token !== conversationGeneration || selectedConversationId.value !== conversation.id) return
    const loadedMembers = await loadConversationMembers(conversation.id)
    if (token !== conversationGeneration || selectedConversationId.value !== conversation.id) return
    members.value = loadedMembers
    memberTargetId.value = ''
  } catch {
    error.value = '群成员操作失败，请确认目标用户及当前角色。'
  }
}

async function renameGroup(): Promise<void> {
  const conversation = selectedConversation.value
  if (!conversation || !groupName.value.trim()) return
  try {
    await renameConversation(conversation.id, groupName.value)
    groupName.value = ''
    await refreshConversations()
  } catch {
    error.value = '群名称修改失败。'
  }
}

async function leaveSelected(): Promise<void> {
  if (!selectedConversation.value) return
  try {
    await leaveConversation(selectedConversation.value.id)
    await refreshConversations()
  } catch {
    error.value = '退出失败；群主需先转让群主或关闭群聊。'
  }
}

async function closeSelected(): Promise<void> {
  if (!selectedConversation.value) return
  try {
    await closeConversation(selectedConversation.value.id)
    await refreshConversations()
  } catch {
    error.value = '关闭群聊失败。'
  }
}

async function confirmGroupAction(): Promise<void> {
  const action = pendingGroupAction.value
  try {
    if (action === 'leave') await leaveSelected()
    if (action === 'close') await closeSelected()
  } finally {
    pendingGroupAction.value = null
  }
}

async function scrollToLatest(): Promise<void> {
  await nextTick()
  if (messageList.value) messageList.value.scrollTop = messageList.value.scrollHeight
}

function formatTime(value?: string | null): string {
  return value ? new Date(value).toLocaleString('zh-CN', { hour12: false }) : '暂无消息'
}

function formatSize(value: number): string {
  return value < 1024 * 1024
    ? `${Math.ceil(value / 1024)} KB`
    : `${(value / 1024 / 1024).toFixed(1)} MB`
}

function scanStatusLabel(value: string): string {
  return (
    {
      CLEAN: '安全',
      PENDING: '等待扫描',
      SCANNING: '扫描中',
      INFECTED: '检测到风险',
      FAILED: '扫描失败',
    }[value] ?? '状态未知'
  )
}
</script>

<template>
  <section class="communication-page" aria-labelledby="communication-title">
    <V2Card id="communication-title" title="站内通讯" :heading-level="1" />
    <p class="communication-page__intro">
      消息以纯文本显示；实时事件仅提示刷新，历史始终从服务端补拉。
    </p>

    <p v-if="error" class="communication-page__alert" role="alert">{{ error }}</p>
    <p class="communication-page__status" aria-live="polite">{{ status }}</p>

    <div v-if="loading" class="communication-page__state" role="status">正在加载通讯数据…</div>
    <div v-else class="communication-page__workspace">
      <aside class="communication-page__sidebar" aria-label="会话与联系人">
        <section>
          <h2>会话</h2>
          <p v-if="!conversations.length" class="communication-page__muted">暂无会话</p>
          <ul class="communication-page__conversation-list">
            <li v-for="item in conversations" :key="item.id">
              <button
                type="button"
                :class="{ 'is-active': item.id === selectedConversationId }"
                :aria-current="item.id === selectedConversationId ? 'page' : undefined"
                @click="selectConversation(item.id)"
              >
                <span
                  ><strong>{{ item.name }}</strong
                  ><small>{{ formatTime(item.lastMessageAt) }}</small></span
                >
                <b v-if="item.unreadCount" :aria-label="`${item.unreadCount} 条未读`">{{
                  item.unreadCount > 99 ? '99+' : item.unreadCount
                }}</b>
              </button>
            </li>
          </ul>
        </section>

        <section
          v-if="session.hasAdminOrPermission('communication:send')"
          class="communication-page__contacts"
        >
          <h2>发起通讯</h2>
          <label for="communication-user-search">查找同租户用户</label>
          <div class="communication-page__inline">
            <input
              id="communication-user-search"
              v-model="userKeyword"
              type="search"
              @keyup.enter="searchUsers"
            />
            <button type="button" @click="searchUsers">搜索</button>
          </div>
          <ul>
            <li v-for="user in users" :key="user.id">
              <label>
                <input v-model="selectedUserIds" type="checkbox" :value="user.id" />
                <span
                  >{{ user.realName || user.username }}<small>@{{ user.username }}</small></span
                >
              </label>
              <button type="button" @click="startDirect(user.id)">私聊</button>
            </li>
          </ul>
          <template v-if="canCreateGroup">
            <label for="communication-group-name">群名称</label>
            <div class="communication-page__inline">
              <input id="communication-group-name" v-model="groupName" maxlength="100" />
              <button
                type="button"
                :disabled="!groupName.trim() || !selectedUserIds.length"
                @click="createGroup"
              >
                建群
              </button>
            </div>
          </template>
        </section>
      </aside>

      <div class="communication-page__chat">
        <div v-if="!selectedConversation" class="communication-page__state">
          选择或创建会话后开始通讯。
        </div>
        <template v-else>
          <header class="communication-page__chat-header">
            <div>
              <h2>{{ selectedConversation.name }}</h2>
              <p>
                {{
                  selectedConversation.type === 'GROUP'
                    ? `群聊 · ${selectedConversation.role}`
                    : '私聊'
                }}
                · {{ selectedConversation.status === 'ACTIVE' ? '进行中' : '已关闭' }}
              </p>
            </div>
            <div v-if="selectedConversation.type === 'GROUP'" class="communication-page__actions">
              <button type="button" @click="pendingGroupAction = 'leave'">退出</button>
              <button
                v-if="selectedConversation.role === 'OWNER'"
                type="button"
                @click="pendingGroupAction = 'close'"
              >
                关闭
              </button>
            </div>
          </header>

          <section
            v-if="canManageGroup"
            class="communication-page__group"
            aria-labelledby="group-management-title"
          >
            <h3 id="group-management-title">群管理</h3>
            <label for="communication-member-action">操作</label>
            <select id="communication-member-action" v-model="memberAction">
              <option
                v-for="action in memberActionOptions"
                :key="action.value"
                :value="action.value"
              >
                {{ action.label }}
              </option>
            </select>
            <label for="communication-member-target">目标用户</label>
            <select id="communication-member-target" v-model="memberTargetId">
              <option value="">请选择</option>
              <option
                v-for="candidate in memberCandidates"
                :key="candidate.id"
                :value="candidate.id"
              >
                {{ candidate.label }}
              </option>
            </select>
            <div class="communication-page__actions">
              <button type="button" :disabled="!memberTargetId" @click="manageGroup">
                执行{{ memberActionOptions.find((action) => action.value === memberAction)?.label }}
              </button>
            </div>
            <div class="communication-page__inline">
              <input
                v-model="groupName"
                aria-label="新群名称"
                maxlength="100"
                placeholder="输入新群名称"
              />
              <button type="button" :disabled="!groupName.trim()" @click="renameGroup">改名</button>
            </div>
          </section>

          <div
            ref="messageList"
            class="communication-page__messages"
            role="log"
            aria-live="polite"
            aria-relevant="additions"
          >
            <p v-if="messagesLoading" class="communication-page__state">正在加载消息…</p>
            <p v-else-if="!messages.length" class="communication-page__state">暂无消息</p>
            <article
              v-for="message in messages"
              v-else
              :key="message.id"
              :class="{ 'is-own': message.senderId === session.userInfo?.userId }"
            >
              <header>
                <strong>{{ message.senderName }}</strong
                ><time>{{ formatTime(message.createdAt) }}</time>
              </header>
              <p v-if="message.body" class="communication-page__body">{{ message.body }}</p>
              <ul v-if="message.attachments.length" class="communication-page__attachments">
                <li v-for="file in message.attachments" :key="file.id">
                  <button type="button" @click="downloadAttachment(file.id)">
                    {{ file.originalName }}
                  </button>
                  <small
                    >{{ formatSize(file.fileSize) }} ·
                    {{ scanStatusLabel(file.virusScanStatus) }}</small
                  >
                </li>
              </ul>
            </article>
          </div>

          <form
            v-if="
              selectedConversation.status === 'ACTIVE' &&
              session.hasAdminOrPermission('communication:send')
            "
            class="communication-page__composer"
            @submit.prevent="send"
          >
            <label for="communication-message">消息</label>
            <textarea
              id="communication-message"
              v-model="body"
              maxlength="4000"
              rows="3"
              placeholder="输入纯文本消息"
              @keydown.ctrl.enter.prevent="send"
            ></textarea>
            <div class="communication-page__composer-footer">
              <label class="communication-page__file">
                选择附件
                <input type="file" multiple @change="chooseAttachments" />
              </label>
              <span>{{ attachments.length }}/5 个附件 · {{ body.length }}/4000 字</span>
              <button type="submit" :disabled="!canSend">{{ sending ? '发送中…' : '发送' }}</button>
            </div>
            <ul v-if="attachments.length" class="communication-page__pending-files">
              <li v-for="file in attachments" :key="`${file.name}-${file.size}`">
                {{ file.name }} · {{ formatSize(file.size) }}
              </li>
            </ul>
          </form>
        </template>
      </div>
    </div>

    <V2ConfirmDialog
      :open="Boolean(pendingGroupAction)"
      :title="pendingGroupAction === 'close' ? '关闭群聊' : '退出群聊'"
      :description="
        pendingGroupAction === 'close'
          ? '关闭后会话仅可查看历史，确认继续？'
          : '退出后将立即失去会话与附件访问，确认继续？'
      "
      :danger="pendingGroupAction === 'close'"
      @close="pendingGroupAction = null"
      @confirm="confirmGroupAction"
    />
  </section>
</template>

<style scoped>
.communication-page {
  min-height: 100%;
  display: grid;
  grid-template-rows: auto auto auto minmax(0, 1fr);
  gap: var(--v2-space-3);
}
.communication-page__chat-header,
.communication-page__composer-footer,
.communication-page__inline,
.communication-page__actions {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--v2-space-2);
}
.communication-page h2,
.communication-page h3,
.communication-page p {
  margin: 0;
}
.communication-page__intro,
.communication-page__muted,
.communication-page__status,
.communication-page small,
.communication-page__chat-header p {
  color: var(--v2-color-text-secondary);
  font-size: var(--v2-font-size-12);
}
.communication-page__alert {
  padding: var(--v2-space-3);
  color: var(--v2-color-danger-text);
  background: var(--v2-color-danger-soft);
  border-radius: var(--v2-radius-sm);
}
.communication-page__status:empty {
  display: none;
}
.communication-page__workspace {
  min-height: 35rem;
  display: grid;
  grid-template-columns: minmax(15rem, 19rem) minmax(0, 1fr);
  overflow: hidden;
  background: var(--v2-color-surface);
  border: 1px solid var(--v2-color-border);
  border-radius: var(--v2-radius-lg);
}
.communication-page__sidebar {
  overflow-y: auto;
  padding: var(--v2-space-4);
  border-inline-end: 1px solid var(--v2-color-border);
}
.communication-page__sidebar section {
  display: grid;
  gap: var(--v2-space-3);
}
.communication-page__sidebar section + section {
  margin-block-start: var(--v2-space-6);
}
.communication-page ul {
  margin: 0;
  padding: 0;
  list-style: none;
}
.communication-page button,
.communication-page input,
.communication-page select,
.communication-page textarea {
  min-height: var(--v2-control-height);
  font: inherit;
}
.communication-page button {
  padding: var(--v2-space-2) var(--v2-space-3);
  color: var(--v2-color-primary);
  background: var(--v2-color-surface);
  border: 1px solid var(--v2-color-border);
  border-radius: var(--v2-radius-sm);
  cursor: pointer;
}
.communication-page button:hover,
.communication-page button.is-active {
  background: var(--v2-color-primary-soft);
  border-color: var(--v2-color-primary);
}
.communication-page button:disabled {
  cursor: not-allowed;
  opacity: 0.55;
}
.communication-page input,
.communication-page select,
.communication-page textarea {
  box-sizing: border-box;
  width: 100%;
  padding: var(--v2-space-2);
  color: var(--v2-color-text-strong);
  background: var(--v2-color-surface);
  border: 1px solid var(--v2-color-border);
  border-radius: var(--v2-radius-sm);
}
.communication-page__conversation-list {
  display: grid;
  gap: var(--v2-space-1);
}
.communication-page__conversation-list button {
  width: 100%;
  display: flex;
  align-items: center;
  justify-content: space-between;
  text-align: start;
}
.communication-page__conversation-list span,
.communication-page__contacts label span {
  min-width: 0;
  display: grid;
}
.communication-page__conversation-list b {
  min-width: 1.5rem;
  padding: 0.15rem 0.35rem;
  color: white;
  background: var(--v2-color-danger);
  border-radius: 999px;
  font-size: var(--v2-font-size-11);
  text-align: center;
}
.communication-page__contacts > ul {
  max-height: 14rem;
  overflow-y: auto;
}
.communication-page__contacts li {
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto;
  align-items: center;
  gap: var(--v2-space-2);
  padding-block: var(--v2-space-1);
}
.communication-page__contacts li label {
  display: flex;
  align-items: center;
  gap: var(--v2-space-2);
}
.communication-page__contacts input[type='checkbox'] {
  width: 1rem;
  min-height: 1rem;
}
.communication-page__chat {
  min-width: 0;
  min-height: 0;
  display: grid;
  grid-template-rows: auto auto minmax(18rem, 1fr) auto;
}
.communication-page__chat-header,
.communication-page__group,
.communication-page__composer {
  padding: var(--v2-space-4);
  border-block-end: 1px solid var(--v2-color-border);
}
.communication-page__group {
  display: grid;
  grid-template-columns: auto minmax(10rem, 16rem) 1fr;
  align-items: center;
  gap: var(--v2-space-2);
  background: var(--v2-color-surface-subtle);
}
.communication-page__group .communication-page__inline {
  grid-column: 2 / -1;
}
.communication-page__actions {
  justify-content: flex-start;
  flex-wrap: wrap;
}
.communication-page__messages {
  min-height: 0;
  overflow-y: auto;
  padding: var(--v2-space-4);
  background: var(--v2-color-canvas);
}
.communication-page__messages article {
  width: min(42rem, 85%);
  margin-block-end: var(--v2-space-3);
  padding: var(--v2-space-3);
  background: var(--v2-color-surface);
  border: 1px solid var(--v2-color-border);
  border-radius: var(--v2-radius-md);
}
.communication-page__messages article.is-own {
  margin-inline-start: auto;
  background: var(--v2-color-primary-soft);
}
.communication-page__messages article header {
  display: flex;
  justify-content: space-between;
  gap: var(--v2-space-3);
}
.communication-page__messages time {
  color: var(--v2-color-text-muted);
  font-size: var(--v2-font-size-11);
}
.communication-page__body {
  margin-block-start: var(--v2-space-2) !important;
  white-space: pre-wrap;
  overflow-wrap: anywhere;
}
.communication-page__attachments,
.communication-page__pending-files {
  display: grid;
  gap: var(--v2-space-1);
  margin-block-start: var(--v2-space-2) !important;
}
.communication-page__attachments li {
  display: flex;
  align-items: center;
  gap: var(--v2-space-2);
}
.communication-page__attachments button {
  min-height: auto;
  padding: 0;
  border: 0;
  background: transparent;
  text-decoration: underline;
}
.communication-page__composer {
  border-block-start: 1px solid var(--v2-color-border);
  border-block-end: 0;
}
.communication-page__composer textarea {
  resize: vertical;
}
.communication-page__composer-footer {
  margin-block-start: var(--v2-space-2);
}
.communication-page__file {
  position: relative;
  padding: var(--v2-space-2) var(--v2-space-3);
  color: var(--v2-color-primary);
  border: 1px solid var(--v2-color-border);
  border-radius: var(--v2-radius-sm);
  cursor: pointer;
}
.communication-page__file input {
  position: absolute;
  width: 1px;
  height: 1px;
  min-height: 0;
  overflow: hidden;
  opacity: 0;
}
.communication-page__state {
  display: grid;
  place-items: center;
  min-height: 10rem;
  color: var(--v2-color-text-muted);
}
@media (max-width: 48rem) {
  .communication-page__workspace {
    grid-template-columns: 1fr;
    overflow: visible;
  }
  .communication-page__sidebar {
    max-height: 28rem;
    border-inline-end: 0;
    border-block-end: 1px solid var(--v2-color-border);
  }
  .communication-page__chat {
    min-height: 40rem;
  }
  .communication-page__group {
    grid-template-columns: 1fr;
  }
  .communication-page__group .communication-page__inline {
    grid-column: auto;
  }
  .communication-page__messages article {
    width: 92%;
  }
  .communication-page__composer-footer {
    align-items: stretch;
    flex-direction: column;
  }
}
</style>
