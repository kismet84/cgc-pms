export function isFeatureEnabled(value: string | boolean | undefined): boolean {
  return value === true || value === 'true'
}

function enabled(name: string): boolean {
  return isFeatureEnabled(import.meta.env[name])
}

export const featureFlags = Object.freeze({
  pwa: { enabled: enabled('VITE_FEATURE_PWA') },
  offlineDraft: { enabled: enabled('VITE_FEATURE_OFFLINE_DRAFT') },
  offlineSync: { enabled: enabled('VITE_FEATURE_OFFLINE_SYNC') },
  fieldDailyLog: { enabled: enabled('VITE_FEATURE_FIELD_DAILY_LOG') },
  fieldQualitySafety: { enabled: enabled('VITE_FEATURE_FIELD_QUALITY_SAFETY') },
  notificationMultiClient: { enabled: enabled('VITE_FEATURE_NOTIFICATION_MULTI_CLIENT') },
})
