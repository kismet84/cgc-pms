import type { NotificationRecord } from '@cgc-pms/frontend-contracts'
import { onBeforeUnmount, ref, watch, type Ref } from 'vue'
import {
  loadNotificationSummary,
  markAllNotificationsRead,
  markNotificationRead,
  openNotificationStream,
} from '@/services/alerts'
import type { ResilientStream } from '@/services/notificationStream'

export function useShellNotifications(canRequest: Ref<boolean>, canEdit: Ref<boolean>) {
  const notificationOpen = ref(false)
  const notificationItems = ref<NotificationRecord[]>([])
  const notificationUnreadCount = ref<number | null>(null)
  const notificationLoading = ref(false)
  const notificationError = ref('')
  let controller: AbortController | null = null
  let stream: ResilientStream | null = null

  function clear(): void {
    controller?.abort()
    controller = null
    stream?.close()
    stream = null
    notificationItems.value = []
    notificationUnreadCount.value = null
    notificationLoading.value = false
    notificationError.value = ''
  }

  async function refresh(): Promise<void> {
    if (!canRequest.value) return
    controller?.abort()
    const request = new AbortController()
    controller = request
    notificationLoading.value = true
    notificationError.value = ''
    try {
      const [page, unread] = await loadNotificationSummary(request.signal)
      if (request.signal.aborted || controller !== request) return
      notificationItems.value = page.records
      notificationUnreadCount.value = unread.count
    } catch {
      if (!request.signal.aborted && controller === request) {
        notificationItems.value = []
        notificationUnreadCount.value = null
        notificationError.value = '通知摘要加载失败'
      }
    } finally {
      if (controller === request) {
        controller = null
        notificationLoading.value = false
      }
    }
  }

  function start(): void {
    clear()
    void refresh()
    let opened = false
    stream = openNotificationStream(
      () => {
        if (opened) void refresh()
        else opened = true
      },
      () => void refresh(),
    )
  }

  function openNotifications(): void {
    notificationOpen.value = true
    if (canRequest.value) void refresh()
  }

  async function readNotification(id: string): Promise<void> {
    if (!canEdit.value) return
    try {
      await markNotificationRead(id)
      await refresh()
    } catch {
      notificationError.value = '通知已读操作失败'
    }
  }

  async function readAllNotifications(): Promise<void> {
    if (!canEdit.value) return
    try {
      await markAllNotificationsRead()
      await refresh()
    } catch {
      notificationError.value = '全部已读操作失败'
    }
  }

  watch(canRequest, (allowed) => (allowed ? start() : clear()), { immediate: true })
  onBeforeUnmount(clear)

  return {
    notificationOpen,
    notificationItems,
    notificationUnreadCount,
    notificationLoading,
    notificationError,
    openNotifications,
    readNotification,
    readAllNotifications,
  }
}
