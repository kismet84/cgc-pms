import type { UserInfo } from '@cgc-pms/frontend-contracts'
import { apiRequest } from './request'

export interface ProfileUpdate {
  realName: string
  phone: string
  email: string
  avatar: string
}

export interface PasswordChange {
  oldPassword: string
  newPassword: string
}

export interface UserPreferences {
  sidebarCollapsed: boolean
  notificationEnabled: boolean
  theme: 'light' | 'dark'
  tableDensity: 'default' | 'middle' | 'small'
}

export const updateProfile = (body: ProfileUpdate) =>
  apiRequest<UserInfo, ProfileUpdate>('/profile', { method: 'PUT', body })

export const changePassword = (body: PasswordChange) =>
  apiRequest<void, PasswordChange>('/profile/password', { method: 'PUT', body })

export const loadPreferences = () => apiRequest<UserPreferences>('/profile/preferences')

export const savePreferences = (body: UserPreferences) =>
  apiRequest<UserPreferences, UserPreferences>('/profile/preferences', { method: 'PUT', body })
