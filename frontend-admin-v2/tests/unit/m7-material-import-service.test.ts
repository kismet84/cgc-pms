import { beforeEach, describe, expect, it, vi } from 'vitest'
import { apiRequest } from '@/services/request'
import { downloadMaterialImportTemplate, importMaterials } from '@/services/master-data'

vi.mock('@/services/request', () => ({ apiRequest: vi.fn() }))

describe('material import service contract', () => {
  beforeEach(() => vi.mocked(apiRequest).mockReset())

  it('uses Blob download and raw FormData upload without a multipart content-type header', async () => {
    const blob = new Blob(['xlsx'])
    vi.mocked(apiRequest).mockResolvedValueOnce(blob).mockResolvedValueOnce({ failed: 0 })
    const file = new File(['xlsx'], 'materials.xlsx')

    await expect(downloadMaterialImportTemplate()).resolves.toBe(blob)
    await importMaterials(file)

    expect(apiRequest).toHaveBeenNthCalledWith(1, '/materials/import-template', {
      headers: { Accept: 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet' },
    })
    const upload = vi.mocked(apiRequest).mock.calls[1]
    expect(upload?.[0]).toBe('/materials/import')
    expect(upload?.[1]?.method).toBe('POST')
    expect(upload?.[1]?.body).toBeInstanceOf(FormData)
    expect(upload?.[1]).not.toHaveProperty('headers')
    expect((upload?.[1]?.body as FormData).get('file')).toBe(file)
  })
})
