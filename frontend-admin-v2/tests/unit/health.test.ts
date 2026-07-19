import { afterEach, describe, expect, it, vi } from 'vitest'
import { probeBackendHealth } from '@/services/health'

describe('probeBackendHealth', () => {
  afterEach(() => vi.unstubAllGlobals())

  it('uses a read-only actuator GET and reports UP', async () => {
    const fetchMock = vi.fn().mockResolvedValue({
      ok: true,
      json: async () => ({ status: 'UP' }),
    })
    vi.stubGlobal('fetch', fetchMock)

    await expect(probeBackendHealth()).resolves.toBe('up')
    expect(fetchMock).toHaveBeenCalledWith(
      '/api/actuator/health',
      expect.objectContaining({ method: 'GET' }),
    )
  })

  it('fails closed when the proxy is unavailable', async () => {
    vi.stubGlobal('fetch', vi.fn().mockRejectedValue(new Error('offline')))
    await expect(probeBackendHealth()).resolves.toBe('unavailable')
  })
})
