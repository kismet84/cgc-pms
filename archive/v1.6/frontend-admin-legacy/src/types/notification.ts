/** 通知 VO — 对应后端 NotificationVO（ID 已转换为 String 兼容 JS） */
export interface NotificationVO {
  id: string
  tenantId: string
  userId: string
  title: string
  content: string
  bizType: string
  bizId: string | null
  notifyType: string
  isRead: number
  readTime: string | null
  createdTime: string
}

/** GET /api/notifications/unread-count 响应 */
export interface UnreadCountResult {
  count: number
}

/** GET /api/notifications 查询参数 */
export interface NotificationListParams {
  pageNo?: number
  pageSize?: number
  unreadOnly?: boolean
}

/** SSE notification 事件 payload — 与后端 SseEmitter.send() 推送格式一致 */
export interface SseNotificationEvent {
  id: string
  title: string
  content: string
  bizType: string
  bizId: string | null
  createdTime: string
}
