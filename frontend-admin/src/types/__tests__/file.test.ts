import { describe, expect, it } from 'vitest'
import { FILE_VIRUS_SCAN_STATUSES } from '../file'
import type { SysFileVO, VirusScanStatus } from '../file'

describe('file types', () => {
  it('exposes only reserved non-passing virus scan statuses', () => {
    expect(FILE_VIRUS_SCAN_STATUSES).toEqual(['NOT_SCANNED', 'NOT_CONFIGURED', 'FAILED'])
    expect(FILE_VIRUS_SCAN_STATUSES).not.toContain('PASSED')
  })

  it('keeps virus scan placeholder fields explicit and not safe-passed', () => {
    const status: VirusScanStatus = 'NOT_CONFIGURED'
    const file: SysFileVO = {
      id: 'f1',
      businessType: 'INVOICE_ATTACHMENT',
      businessId: 'i1',
      fileName: 'hash.pdf',
      originalName: 'invoice.pdf',
      fileSize: 12,
      contentType: 'application/pdf',
      storagePath: 'INVOICE_ATTACHMENT/i1/hash.pdf',
      bucketName: 'test-bucket',
      presignedUrl: 'http://minio.local/test?X-Amz-Expires=300&X-Amz-Signature=test',
      createdAt: '2026-07-09 00:00:00',
      virusScanStatus: status,
      virusScanCode: 'VIRUS_SCAN_NOT_CONFIGURED',
      virusScanMessage: '未接入病毒扫描能力，文件未标记为安全检查通过',
      virusScanPassed: false,
    }

    expect(file.virusScanStatus).toBe('NOT_CONFIGURED')
    expect(file.virusScanCode).toBe('VIRUS_SCAN_NOT_CONFIGURED')
    expect(file.virusScanPassed).toBe(false)
    expect(file.virusScanMessage).not.toContain('已安全扫描')
  })
})
