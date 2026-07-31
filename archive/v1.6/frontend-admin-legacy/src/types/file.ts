/** File view object (matches backend SysFileVO) */
export const FILE_VIRUS_SCAN_STATUSES = [
  'CLEAN',
  'INFECTED',
  'NOT_SCANNED',
  'NOT_CONFIGURED',
  'FAILED',
] as const

export type VirusScanStatus = (typeof FILE_VIRUS_SCAN_STATUSES)[number]
export type VirusScanCode =
  | 'VIRUS_SCAN_CLEAN'
  | 'VIRUS_SCAN_INFECTED'
  | 'VIRUS_SCAN_NOT_SCANNED'
  | 'VIRUS_SCAN_NOT_CONFIGURED'
  | 'VIRUS_SCAN_FAILED'

export interface SysFileVO {
  id: string
  businessType: string
  businessId: string
  fileName: string
  originalName: string
  fileSize: number
  contentType: string
  storagePath: string
  bucketName: string
  presignedUrl: string
  createdAt: string
  virusScanStatus: VirusScanStatus
  virusScanCode: VirusScanCode
  virusScanMessage: string
  virusScanPassed: boolean
}
