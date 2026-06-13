/** File view object (matches backend SysFileVO) */
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
}
