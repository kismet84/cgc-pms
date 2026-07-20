import type { PageResult } from "./api";

export type AlertSeverity = "HIGH" | "MEDIUM" | "LOW";
export type AlertProcessStatus = "OPEN" | "PROCESSED" | "ARCHIVED" | "INVALID";

export interface AlertRecord {
  id: string;
  projectId: string;
  contractId?: string;
  alertDomain?: string;
  alertCategory?: string;
  sourceType?: string;
  sourceId?: string;
  ruleType: string;
  severity: AlertSeverity;
  message: string;
  triggeredAt: string;
  isRead: number;
  acknowledgedBy?: string;
  processStatus?: AlertProcessStatus;
  statusRemark?: string;
}

export interface AlertQuery {
  pageNum?: number;
  pageSize?: number;
  projectId?: string;
  severity?: AlertSeverity | "";
  isRead?: number;
  processStatus?: AlertProcessStatus | "";
  ruleType?: string;
  alertDomain?: string;
  triggeredStart?: string;
  triggeredEnd?: string;
}

export interface AlertBatchFailure {
  alertId: string;
  reason: string;
}

export interface AlertBatchResult {
  total: number;
  success: number;
  failed: number;
  successIds: string[];
  failures: AlertBatchFailure[];
}

export interface NotificationRecord {
  id: string;
  title: string;
  content: string;
  bizType: string;
  bizId: string | null;
  notifyType: string;
  isRead: number;
  createdTime: string;
}

export interface NotificationUnreadCount {
  count: number;
}

export const ALERT_API = {
  list: "/alerts",
  markRead: (id: string) => `/alerts/${id}/read`,
  acknowledge: (id: string) => `/alerts/${id}/acknowledge`,
  updateStatus: (id: string) => `/alerts/${id}/status`,
  batchRead: "/alerts/batch/read",
  batchStatus: "/alerts/batch/status",
  evaluate: "/alerts/batch-evaluate",
} as const;

export const NOTIFICATION_API = {
  list: "/notifications",
  unreadCount: "/notifications/unread-count",
  markRead: (id: string) => `/notifications/${id}/read`,
  markAllRead: "/notifications/read-all",
} as const;

export type AlertPage = PageResult<AlertRecord>;

export function hasPermission(
  permissions: readonly string[],
  code: string,
): boolean {
  return permissions.includes("*") || permissions.includes(code);
}

export function canRequestAlertNotifications(
  permissions: readonly string[],
): boolean {
  return (
    hasPermission(permissions, "alert:view") &&
    hasPermission(permissions, "notification:view")
  );
}
