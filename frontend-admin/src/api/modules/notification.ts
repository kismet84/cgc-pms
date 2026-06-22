import { request } from '@/api/request'
import service from '@/api/request'
import type { PageResult } from '@/types/api'
import type {
  NotificationVO,
  UnreadCountResult,
  NotificationListParams,
} from '@/types/notification'

/** 通知列表（分页） */
export function getNotifications(params: NotificationListParams) {
  return request<PageResult<NotificationVO>>({
    url: '/notifications',
    method: 'get',
    params,
  })
}

/** 未读通知数 */
export function getUnreadCount() {
  return request<UnreadCountResult>({
    url: '/notifications/unread-count',
    method: 'get',
  })
}

/** 标记单条通知为已读 */
export function markAsRead(id: string) {
  return request<{ id: string; read: boolean }>({
    url: `/notifications/${id}/read`,
    method: 'put',
  })
}

/** 全部标为已读 */
export function markAllAsRead() {
  return request<{ userId: string; allRead: boolean }>({
    url: '/notifications/read-all',
    method: 'put',
  })
}

/**
 * 创建 SSE EventSource 订阅实时通知流。
 * HttpOnly Cookie 由浏览器自动携带，无需手动传 token。
 *
 * 返回 EventSource 实例，调用方负责：
 * - `es.addEventListener('notification', handler)`
 * - `es.addEventListener('connected', handler)`
 * - `es.onerror = handler`
 * - 组件卸载时调用 `es.close()`
 */
export function createNotificationStream(): EventSource {
  const baseUrl = (service.defaults.baseURL ?? '/api') as string
  const es = new EventSource(`${baseUrl}/notifications/stream`, {
    withCredentials: true,
  })
  return es
}
