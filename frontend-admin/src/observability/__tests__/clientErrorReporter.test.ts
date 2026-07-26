import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

const { postClientError } = vi.hoisted(() => ({
  postClientError: vi.fn().mockResolvedValue(undefined),
}))

vi.mock('@/api/request', () => ({ postClientError }))

beforeEach(() => {
  vi.useFakeTimers()
  vi.setSystemTime(new Date('2026-07-26T12:00:00Z'))
  postClientError.mockClear()
})

afterEach(() => {
  vi.useRealTimers()
  vi.resetModules()
  vi.unstubAllGlobals()
})

describe('client error reporter', () => {
  it('sends only bounded fields and deduplicates the same error', async () => {
    const { reportClientError } = await import('../clientErrorReporter')
    const error = new TypeError('password=secret https://host/private')

    await reportClientError('VUE', error)
    await reportClientError('VUE', error)

    expect(postClientError).toHaveBeenCalledTimes(1)
    const payload = postClientError.mock.calls[0]?.[0]
    expect(payload).toMatchObject({ app: 'LEGACY', source: 'VUE', kind: 'TYPE_ERROR' })
    expect(payload.fingerprint).toMatch(/^[a-f0-9]{64}$/)
    expect(JSON.stringify(payload)).not.toContain('secret')
    expect(JSON.stringify(payload)).not.toContain('host')
  })

  it('limits one browser to five reports per minute', async () => {
    const { reportClientError } = await import('../clientErrorReporter')
    for (let index = 0; index < 6; index += 1) {
      await reportClientError('WINDOW', new Error(`failure-${index}`))
    }
    expect(postClientError).toHaveBeenCalledTimes(5)
  })

  it('uses a different fingerprint salt after a new browser session', async () => {
    const firstModule = await import('../clientErrorReporter')
    await firstModule.reportClientError('VUE', new Error('same private value'))
    const first = postClientError.mock.calls[0]?.[0].fingerprint

    vi.resetModules()
    const secondModule = await import('../clientErrorReporter')
    await secondModule.reportClientError('VUE', new Error('same private value'))
    const second = postClientError.mock.calls[1]?.[0].fingerprint

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
    const { reportClientError } = await import('../clientErrorReporter')

    await reportClientError('VUE', new Error('private material'))

    expect(postClientError.mock.calls[0]?.[0].fingerprint).toMatch(/^[a-f0-9]{64}$/)
  })
})
