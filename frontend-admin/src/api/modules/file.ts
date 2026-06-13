import { request } from '@/api/request'
import type { SysFileVO } from '@/types/file'

/** Upload a file (FormData multipart, extended timeout for large files) */
export function uploadFile(file: File, businessType: string, businessId: string) {
  const formData = new FormData()
  formData.append('file', file)
  return request<SysFileVO>({
    url: '/files/upload',
    method: 'post',
    data: formData,
    params: { businessType, businessId },
    timeout: 120000,
  })
}

/** Get presigned download URL for a file */
export function getFileUrl(id: string) {
  return request<string>({
    url: `/files/${id}/url`,
    method: 'get',
  })
}

/** List files by business type and ID */
export function listFiles(businessType: string, businessId: string) {
  return request<SysFileVO[]>({
    url: '/files',
    method: 'get',
    params: { businessType, businessId },
  })
}

/** Delete a file */
export function deleteFile(id: string) {
  return request<void>({
    url: `/files/${id}`,
    method: 'delete',
  })
}
