import { onBeforeUnmount, onMounted, ref, watch, type Ref } from 'vue'
import { loadCommunicationUnreadCount, openCommunicationStream } from '@/services/communication'
import type { ResilientStream } from '@/services/notificationStream'

export function useShellCommunication(canView: Ref<boolean>) {
  const communicationUnreadCount = ref<number | null>(null)
  let controller: AbortController | null = null
  let stream: ResilientStream | null = null

  function clear(): void {
    controller?.abort()
    controller = null
    stream?.close()
    stream = null
    communicationUnreadCount.value = null
  }

  async function refresh(): Promise<void> {
    if (!canView.value) return
    controller?.abort()
    const request = new AbortController()
    controller = request
    try {
      const unread = await loadCommunicationUnreadCount(request.signal)
      if (!request.signal.aborted) communicationUnreadCount.value = unread.count
    } catch {
      if (!request.signal.aborted) communicationUnreadCount.value = null
    } finally {
      if (controller === request) controller = null
    }
  }

  function start(): void {
    clear()
    void refresh()
    let opened = false
    let skipHandshake = true
    stream = openCommunicationStream(
      (event) => {
        const handshake =
          event.action === 'REFRESH' && !event.conversationId && !event.messageId && !event.seq
        if (handshake && skipHandshake) {
          skipHandshake = false
          return
        }
        if (event.action === 'PING') return
        void refresh()
        window.dispatchEvent(new CustomEvent('communication-refresh', { detail: event }))
      },
      undefined,
      () => {
        skipHandshake = true
        if (opened) void refresh()
        else opened = true
      },
    )
  }

  watch(canView, (allowed) => (allowed ? start() : clear()), { immediate: true })
  onMounted(() => window.addEventListener('communication-unread-changed', refresh))
  onBeforeUnmount(() => {
    window.removeEventListener('communication-unread-changed', refresh)
    clear()
  })

  return { communicationUnreadCount }
}
