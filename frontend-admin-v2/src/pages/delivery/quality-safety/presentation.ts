export function qualityStatusTone(
  status: string,
): 'success' | 'warning' | 'danger' | 'info' | 'neutral' {
  if (['ACTIVE', 'COMPLETED', 'CLOSED', 'PASSED', 'POSTED'].includes(status)) return 'success'
  if (['RECTIFYING', 'PENDING_REINSPECTION', 'SUBMITTED'].includes(status)) return 'warning'
  if (['REJECTED', 'CRITICAL'].includes(status)) return 'danger'
  return 'neutral'
}
