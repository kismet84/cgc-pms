<script setup lang="ts">
import { ref, computed, onMounted, onUnmounted } from 'vue'
import { BellOutlined } from '@ant-design/icons-vue'
import { message } from 'ant-design-vue'
import {
  getNotifications,
  getUnreadCount,
  markAsRead,
  markAllAsRead,
  createNotificationStream,
} from '@/api/modules/notification'
import type { NotificationVO, SseNotificationEvent } from '@/types/notification'
import type { PageResult } from '@/types/api'

interface Props {
  label?: string
}

withDefaults(defineProps<Props>(), {
  label: '',
})

// ── State ──
const unreadCount = ref(0)
const notifications = ref<NotificationVO[]>([])
const loading = ref(false)
const markingIds = ref<Set<string>>(new Set())
const markingAll = ref(false)
const popoverOpen = ref(false)

let eventSource: EventSource | null = null

// ── Computed ──
/** Show at most 10 recent items in dropdown */
const recentNotifications = computed(() => notifications.value.slice(0, 10))

function formatTime(raw: string | null): string {
  if (!raw) return ''
  // Display only time portion for today, full date otherwise
  const d = new Date(raw.replace(' ', 'T'))
  if (isNaN(d.getTime())) return raw
  const now = new Date()
  const isToday = d.toDateString() === now.toDateString()
  const hh = String(d.getHours()).padStart(2, '0')
  const mm = String(d.getMinutes()).padStart(2, '0')
  if (isToday) return `${hh}:${mm}`
  const MM = String(d.getMonth() + 1).padStart(2, '0')
  const DD = String(d.getDate()).padStart(2, '0')
  return `${MM}-${DD} ${hh}:${mm}`
}

// ── Fetch ──
async function fetchUnreadCount() {
  try {
    const res = await getUnreadCount()
    unreadCount.value = res.count
  } catch (error) {
    if (import.meta.env.DEV) {
      console.error('NotificationBell: 加载未读数量失败', error)
    }
    message.error('加载未读数量失败')
  }
}

async function fetchNotifications() {
  loading.value = true
  try {
    const res: PageResult<NotificationVO> = await getNotifications({
      pageNo: 1,
      pageSize: 20,
    })
    notifications.value = res.records
  } catch (error) {
    if (import.meta.env.DEV) {
      console.error('NotificationBell: 加载通知列表失败', error)
    }
    notifications.value = []
    message.error('加载通知列表失败')
  } finally {
    loading.value = false
  }
}

// ── SSE ──
function connectSSE() {
  try {
    eventSource = createNotificationStream()

    eventSource.addEventListener('connected', () => {
      // connection established — no action needed
    })

    eventSource.addEventListener('notification', (event: MessageEvent) => {
      try {
        const data: SseNotificationEvent = JSON.parse(event.data)
        // Prepend to list
        const vo: NotificationVO = {
          id: data.id,
          tenantId: '',
          userId: '',
          title: data.title,
          content: data.content,
          bizType: data.bizType,
          bizId: data.bizId,
          notifyType: 'INFO',
          isRead: 0,
          readTime: null,
          createdTime: data.createdTime,
        }
        notifications.value = [vo, ...notifications.value]
        unreadCount.value += 1
      } catch (error) {
        if (import.meta.env.DEV) {
          console.error('NotificationBell: 解析通知消息失败', error)
        }
        message.error('解析通知消息失败')
      }
    })

    eventSource.onerror = () => {
      // SSE connection lost — browser will auto-reconnect;
      // if not, re-fetch count on next popover open
    }
  } catch (error) {
    if (import.meta.env.DEV) {
      console.error('NotificationBell: 建立消息推送连接失败', error)
    }
    message.error('建立消息推送连接失败')
  }
}

// ── Actions ──
async function handleMarkRead(record: NotificationVO) {
  if (record.isRead === 1) return
  markingIds.value.add(record.id)
  try {
    await markAsRead(record.id)
    record.isRead = 1
    if (unreadCount.value > 0) unreadCount.value -= 1
  } catch (err) {
    if (import.meta.env.DEV) {
      console.error('NotificationBell: 标记已读失败', err)
    }
    message.error('标记已读失败')
  } finally {
    markingIds.value.delete(record.id)
  }
}

async function handleMarkAllRead() {
  markingAll.value = true
  try {
    await markAllAsRead()
    notifications.value.forEach((n) => (n.isRead = 1))
    unreadCount.value = 0
    message.success('已全部标为已读')
  } catch (err) {
    if (import.meta.env.DEV) {
      console.error('NotificationBell: 操作失败', err)
    }
    message.error('操作失败')
  } finally {
    markingAll.value = false
  }
}

function handlePopoverChange(visible: boolean) {
  if (visible) {
    popoverOpen.value = true
    fetchNotifications()
    fetchUnreadCount()
  } else {
    popoverOpen.value = false
  }
}

// ── Lifecycle ──
onMounted(() => {
  fetchUnreadCount()
  connectSSE()
})

onUnmounted(() => {
  eventSource?.close()
})
</script>

<template>
  <a-popover
    trigger="click"
    placement="bottomRight"
    :arrow-point-at-center="true"
    overlay-class-name="nb-popover"
    @open-change="handlePopoverChange"
  >
    <span class="nb-trigger" :class="{ 'nb-trigger--with-label': label }" aria-label="通知">
      <a-badge
        :count="unreadCount"
        :offset="[-2, 6]"
        :overflow-count="99"
        :number-style="{ fontSize: '11px' }"
      >
        <BellOutlined style="font-size: 18px; cursor: pointer" />
      </a-badge>
      <span v-if="label" class="nb-trigger-label sidebar-bell-label">{{ label }}</span>
    </span>

    <template #content>
      <div class="nb-panel">
        <!-- Header -->
        <div class="nb-header">
          <span class="nb-title">通知</span>
          <a-button
            type="link"
            size="small"
            :loading="markingAll"
            :disabled="unreadCount === 0"
            @click="handleMarkAllRead"
          >
            全部标为已读
          </a-button>
        </div>

        <!-- List -->
        <div class="nb-list" :class="{ 'nb-empty': recentNotifications.length === 0 }">
          <template v-if="loading">
            <div class="nb-loading">加载中…</div>
          </template>
          <template v-else-if="recentNotifications.length === 0">
            <div class="nb-empty-state">暂无通知</div>
          </template>
          <template v-else>
            <div
              v-for="item in recentNotifications"
              :key="item.id"
              class="nb-item"
              :class="{ 'nb-unread': item.isRead === 0 }"
              @click="handleMarkRead(item)"
            >
              <div class="nb-item-dot" v-if="item.isRead === 0" />
              <div class="nb-item-body">
                <div class="nb-item-title">{{ item.title }}</div>
                <div class="nb-item-content">{{ item.content }}</div>
                <div class="nb-item-meta">
                  <a-tag v-if="item.bizType" size="small" color="blue">{{ item.bizType }}</a-tag>
                  <span class="nb-item-time">{{ formatTime(item.createdTime) }}</span>
                </div>
              </div>
              <a-spin v-if="markingIds.has(item.id)" size="small" class="nb-item-spin" />
            </div>
          </template>
        </div>
      </div>
    </template>
  </a-popover>
</template>

<style scoped>
.nb-trigger {
  display: inline-flex;
  align-items: center;
  gap: 16px;
  color: var(--text);
  transition: color 0.2s;
}

.nb-trigger--with-label {
  width: 100%;
  min-height: 40px;
}

.nb-trigger-label {
  font-size: 13px;
  line-height: 20px;
  color: currentcolor;
  white-space: nowrap;
}

.nb-trigger:hover {
  color: var(--primary);
}

/* ── Panel ── */
.nb-panel {
  width: 360px;
  max-height: 480px;
  display: flex;
  flex-direction: column;
}

.nb-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 12px 16px 10px;
  border-bottom: 1px solid #e5eaf3;
}

.nb-title {
  font-size: 15px;
  font-weight: 600;
  color: var(--text);
}

/* ── List ── */
.nb-list {
  flex: 1;
  overflow-y: auto;
  min-height: 60px;
  max-height: 400px;
}

.nb-list.nb-empty {
  display: flex;
  align-items: center;
  justify-content: center;
}

.nb-loading,
.nb-empty-state {
  padding: 32px 16px;
  text-align: center;
  color: var(--muted);
  font-size: 14px;
}

/* ── Item ── */
.nb-item {
  display: flex;
  align-items: flex-start;
  gap: 10px;
  padding: 12px 16px;
  cursor: pointer;
  transition: background 0.15s;
  border-bottom: 1px solid #f3f4f6;
}

.nb-item:last-child {
  border-bottom: none;
}

.nb-item:hover {
  background: #f8faff;
}

.nb-item.nb-unread {
  background: #f1f6ff;
}

.nb-item-dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: var(--primary);
  margin-top: 6px;
  flex-shrink: 0;
}

.nb-item-body {
  flex: 1;
  min-width: 0;
}

.nb-item-title {
  font-size: 14px;
  font-weight: 600;
  color: var(--text);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.nb-item-content {
  font-size: 13px;
  color: var(--text-secondary);
  margin-top: 4px;
  line-height: 1.5;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

.nb-item-meta {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-top: 6px;
}

.nb-item-time {
  font-size: 12px;
  color: var(--muted);
}

.nb-item-spin {
  margin-top: 4px;
}

/* ── Override popover padding ── */
:deep(.nb-popover .ant-popover-inner-content) {
  padding: 0;
}

:deep(.nb-popover .ant-popover-arrow) {
  display: none;
}
</style>
