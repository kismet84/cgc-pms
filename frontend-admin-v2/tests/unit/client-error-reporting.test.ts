import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

const { apiRequest, captureException, sentryInit } = vi.hoisted(() => ({
  apiRequest: vi.fn().mockResolvedValue(undefined),
  captureException: vi.fn(),
  sentryInit: vi.fn(),
}))

vi.mock('@/services/request', () => ({ apiRequest }))
vi.mock('@sentry/vue', () => ({ captureException, init: sentryInit }))

beforeEach(() => {
  vi.useFakeTimers()
  vi.setSystemTime(new Date('2026-07-26T12:00:00Z'))
  apiRequest.mockClear()
  captureException.mockClear()
  sentryInit.mockClear()
})

afterEach(() => {
  vi.useRealTimers()
  vi.resetModules()
  vi.unstubAllEnvs()
  vi.unstubAllGlobals()
})

describe('V2 client error reporter', () => {
  it('enables Sentry only from environment configuration and excludes duplicate global handlers', async () => {
    vi.stubEnv('VITE_SENTRY_DSN', 'https://public@example.ingest.sentry.io/1')
    vi.stubEnv('VITE_SENTRY_ENVIRONMENT', 'test')
    vi.stubEnv('VITE_SENTRY_RELEASE', 'cgc-pms-frontend-v2@test')
    const { initializeClientErrorReporting, reportClientError } =
      await import('@/services/clientErrorReporter')

    initializeClientErrorReporting({} as never)

    expect(sentryInit).toHaveBeenCalledTimes(1)
    const options = sentryInit.mock.calls[0]?.[0]
    expect(options).toMatchObject({
      dsn: 'https://public@example.ingest.sentry.io/1',
      environment: 'test',
      release: 'cgc-pms-frontend-v2@test',
      sendDefaultPii: false,
      attachErrorHandler: false,
      tracesSampleRate: 0,
      dataCollection: { userInfo: false, httpBodies: [] },
    })
    expect(options.integrations([
      { name: 'GlobalHandlers' }, { name: 'BrowserApiErrors' }, { name: 'Dedupe' },
    ])).toEqual([
      { name: 'Dedupe' },
    ])

    const error = new TypeError('test failure')
    await reportClientError('VUE', error)
    expect(captureException).toHaveBeenCalledWith(error, {
      tags: { client_error_source: 'VUE', client_error_kind: 'TYPE_ERROR' },
    })
    await reportClientError('WINDOW', error)
    expect(captureException).toHaveBeenCalledTimes(1)
  })

  it('keeps Sentry disabled when no DSN is configured', async () => {
    vi.stubEnv('VITE_SENTRY_DSN', '')
    const { initializeClientErrorReporting } = await import('@/services/clientErrorReporter')

    initializeClientErrorReporting({} as never)

    expect(sentryInit).not.toHaveBeenCalled()
  })

  it('keeps the bounded API report when the Sentry SDK throws', async () => {
    vi.stubEnv('VITE_SENTRY_DSN', 'https://public@example.ingest.sentry.io/1')
    captureException.mockImplementationOnce(() => { throw new Error('SDK failure') })
    const { initializeClientErrorReporting, reportClientError } =
      await import('@/services/clientErrorReporter')
    initializeClientErrorReporting({} as never)

    await expect(reportClientError('WINDOW', new Error('test'))).resolves.toBeUndefined()
    expect(apiRequest).toHaveBeenCalledTimes(1)
  })

  it('keeps the application running and reports a diagnostic when Sentry initialization fails', async () => {
    vi.stubEnv('VITE_SENTRY_DSN', 'https://public@example.ingest.sentry.io/1')
    sentryInit.mockImplementationOnce(() => {
      throw new Error('invalid SDK configuration')
    })
    const warning = vi.spyOn(console, 'warn').mockImplementation(() => undefined)
    const { initializeClientErrorReporting } = await import('@/services/clientErrorReporter')

    expect(() => initializeClientErrorReporting({} as never)).not.toThrow()
    expect(warning).toHaveBeenCalledWith(
      'Sentry client error monitoring initialization failed',
      expect.any(Error),
    )
  })

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
