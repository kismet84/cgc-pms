import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

const { apiRequest } = vi.hoisted(() => ({
  apiRequest: vi.fn().mockResolvedValue(undefined),
}))

vi.mock('@/services/request', () => ({ apiRequest }))

beforeEach(() => {
  vi.useFakeTimers()
  vi.setSystemTime(new Date('2026-07-26T12:00:00Z'))
  apiRequest.mockClear()
})

afterEach(() => {
  vi.useRealTimers()
  vi.resetModules()
  vi.unstubAllGlobals()
})

describe('V2 client error reporter', () => {
  it('sends only bounded fields and deduplicates the same error', async () => {
    const { reportClientError } = await import('@/services/clientErrorReporter')
    const error = new ReferenceError('token=secret https://host/private')

    await reportClientError('VUE', error)
    await reportClientError('VUE', error)

    expect(apiRequest).toHaveBeenCalledTimes(1)
    const payload = apiRequest.mock.calls[0]?.[1]?.body
    expect(payload).toMatchObject({ app: 'V2', source: 'VUE', kind: 'REFERENCE_ERROR' })
    expect(payload.fingerprint).toMatch(/^[a-f0-9]{64}$/)
    expect(JSON.stringify(payload)).not.toContain('secret')
    expect(JSON.stringify(payload)).not.toContain('host')
  })

  it('limits one browser to five reports per minute', async () => {
    const { reportClientError } = await import('@/services/clientErrorReporter')
    for (let index = 0; index < 6; index += 1) {
      await reportClientError('PROMISE', new Error(`failure-${index}`))
    }
    expect(apiRequest).toHaveBeenCalledTimes(5)
  })

  it('uses a different fingerprint salt after a new browser session', async () => {
    const firstModule = await import('@/services/clientErrorReporter')
    await firstModule.reportClientError('VUE', new Error('same private value'))
    const first = apiRequest.mock.calls[0]?.[1]?.body.fingerprint

    vi.resetModules()
    const secondModule = await import('@/services/clientErrorReporter')
    await secondModule.reportClientError('VUE', new Error('same private value'))
    const second = apiRequest.mock.calls[1]?.[1]?.body.fingerprint

    expect(second).not.toBe(first)
  })

  it.each([
    ['WebCrypto is unavailable', undefined],
    ['WebCrypto digest fails', vi.fn().mockRejectedValue(new Error('digest unavailable'))],
  ])('uses a random fingerprint when %s', async (_case, digest) => {
    vi.stubGlobal('crypto', {
      getRandomValues: (bytes: Uint8Array) => bytes.fill(0xab),
      ...(digest ? { subtle: { digest } } : {}),
    })
    const { reportClientError } = await import('@/services/clientErrorReporter')

    await reportClientError('VUE', new Error('private material'))

    expect(apiRequest.mock.calls[0]?.[1]?.body.fingerprint).toMatch(/^[a-f0-9]{64}$/)
  })
})
