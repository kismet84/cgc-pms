import {
  API_SUCCESS_CODE,
  AUTH_API,
  CSRF_CONTRACT,
  isLoginResult,
  type ApiResponse,
} from '@cgc-pms/frontend-contracts'

const API_PREFIX = '/api'
const ERROR_DEDUPE_WINDOW_MS = 1_500
const SENSITIVE_QUERY_KEYS = new Set(['access_token', 'authorization', 'refresh_token', 'token'])
const FORBIDDEN_AUTH_HEADERS = new Set(['authorization', 'x-refresh-token'])

export interface RequestNotice {
  code: string
  message: string
  traceId?: string | null
}

export interface RequestLifecycle {
  onError?: (notice: RequestNotice) => void
  onSessionExpired?: (notice: RequestNotice) => void | Promise<void>
}

export interface ApiRequestOptions<TBody = unknown> {
  method?: 'GET' | 'POST' | 'PUT' | 'PATCH' | 'DELETE' | 'HEAD' | 'OPTIONS'
  body?: TBody
  headers?: Record<string, string>
  notifyError?: boolean
  recover401?: boolean
  signal?: AbortSignal
}

interface ParsedResponse<T> {
  envelope: ApiResponse<T>
  response: Response
}

export class ApiClientError extends Error {
  readonly code: string
  readonly status?: number
  readonly traceId?: string | null

  constructor(notice: RequestNotice, status?: number) {
    super(notice.message)
    this.name = 'ApiClientError'
    this.code = notice.code
    this.status = status
    this.traceId = notice.traceId
  }
}

export class SessionExpiredError extends ApiClientError {
  constructor(traceId?: string | null) {
    super({ code: 'AUTH_SESSION_EXPIRED', message: '登录已过期，请重新登录', traceId }, 401)
    this.name = 'SessionExpiredError'
  }
}

export function isApiClientError(error: unknown): error is ApiClientError {
  if (error instanceof ApiClientError) return true
  if (!error || typeof error !== 'object') return false
  const candidate = error as Partial<ApiClientError>
  return (
    typeof candidate.name === 'string' &&
    typeof candidate.message === 'string' &&
    typeof candidate.code === 'string'
  )
}

let lifecycle: RequestLifecycle = {}
let refreshTask: Promise<void> | null = null
let sessionFailureNotified = false
let lastNotice: { key: string; at: number } | null = null

export function configureRequestLifecycle(next: RequestLifecycle): void {
  lifecycle = { ...next }
}

export function resetRequestRecoveryState(): void {
  sessionFailureNotified = false
  lastNotice = null
}

export async function apiRequest<T, TBody = unknown>(
  path: string,
  options: ApiRequestOptions<TBody> = {},
): Promise<T> {
  assertSafeRequest(path, options.headers)

  try {
    return await sendRequest<T, TBody>(path, options, false)
  } catch (error) {
    if (options.signal?.aborted) throw error
    const normalized = normalizeError(error)
    if (options.notifyError !== false && !(normalized instanceof SessionExpiredError)) {
      notifyErrorOnce(normalized)
    }
    throw normalized
  }
}

async function sendRequest<T, TBody>(
  path: string,
  options: ApiRequestOptions<TBody>,
  retried: boolean,
): Promise<T> {
  const method = options.method ?? 'GET'
  const response = await fetch(`${API_PREFIX}${path}`, {
    method,
    credentials: 'same-origin',
    headers: buildHeaders(method, options.headers, options.body),
    body: options.body === undefined ? undefined : JSON.stringify(options.body),
    signal: options.signal,
  })

  if (response.status === 401 && options.recover401 !== false && !retried) {
    await refreshSessionOnce()
    return sendRequest(path, options, true)
  }

  return parseResponse<T>(response)
}

async function refreshSessionOnce(): Promise<void> {
  if (!refreshTask) {
    refreshTask = performRefresh()
      .then(() => {
        sessionFailureNotified = false
      })
      .catch(async (error: unknown) => {
        const normalized = normalizeError(error)
        if (!sessionFailureNotified) {
          sessionFailureNotified = true
          await lifecycle.onSessionExpired?.({
            code: 'AUTH_SESSION_EXPIRED',
            message: '登录已过期，请重新登录',
            traceId: normalized.traceId,
          })
        }
        throw new SessionExpiredError(normalized.traceId)
      })
      .finally(() => {
        refreshTask = null
      })
  }
  return refreshTask
}

async function performRefresh(): Promise<void> {
  const response = await fetch(`${API_PREFIX}${AUTH_API.refresh}`, {
    method: 'POST',
    credentials: 'same-origin',
    headers: buildHeaders('POST'),
  })
  const result = await parseResponse<unknown>(response)
  if (!isLoginResult(result) || hasExposedToken(result)) {
    throw new ApiClientError({
      code: 'AUTH_RESPONSE_INVALID',
      message: '认证响应格式无效',
    })
  }
}

async function parseResponse<T>(response: Response): Promise<T> {
  let parsed: ParsedResponse<T>['envelope']
  try {
    parsed = (await response.json()) as ApiResponse<T>
  } catch {
    throw new ApiClientError(
      { code: 'API_MALFORMED_RESPONSE', message: '服务响应格式无效' },
      response.status,
    )
  }

  if (!response.ok) {
    throw new ApiClientError(
      {
        code: parsed.code || `HTTP_${response.status}`,
        message: safeMessage(parsed.message, '请求失败，请稍后重试'),
        traceId: parsed.traceId,
      },
      response.status,
    )
  }

  if (parsed.code !== API_SUCCESS_CODE) {
    throw new ApiClientError({
      code: parsed.code || 'API_BUSINESS_ERROR',
      message: safeMessage(parsed.message, '操作失败，请稍后重试'),
      traceId: parsed.traceId,
    })
  }

  return parsed.data
}

function buildHeaders(
  method: string,
  customHeaders: Record<string, string> = {},
  body?: unknown,
): Headers {
  const headers = new Headers({ Accept: 'application/json', ...customHeaders })
  if (body !== undefined && !headers.has('Content-Type')) {
    headers.set('Content-Type', 'application/json')
  }

  if (!CSRF_CONTRACT.safeMethods.includes(method as (typeof CSRF_CONTRACT.safeMethods)[number])) {
    const token = getCookie(CSRF_CONTRACT.cookieName)
    if (token) headers.set(CSRF_CONTRACT.headerName, token)
  }
  return headers
}

function getCookie(name: string): string | null {
  const prefix = `${name}=`
  const cookie = document.cookie
    .split(';')
    .map((item) => item.trim())
    .find((item) => item.startsWith(prefix))
  if (!cookie) return null
  try {
    return decodeURIComponent(cookie.slice(prefix.length)) || null
  } catch {
    return null
  }
}

function assertSafeRequest(path: string, headers: Record<string, string> = {}): void {
  if (!path.startsWith('/') || path.startsWith('//')) {
    throw new ApiClientError({ code: 'API_PATH_INVALID', message: 'API 路径无效' })
  }

  const url = new URL(path, 'http://v2.local')
  for (const key of url.searchParams.keys()) {
    if (SENSITIVE_QUERY_KEYS.has(key.toLowerCase())) {
      throw new ApiClientError({
        code: 'AUTH_SECRET_TRANSPORT_FORBIDDEN',
        message: '禁止在 URL 中传递认证凭据',
      })
    }
  }

  for (const name of Object.keys(headers)) {
    if (FORBIDDEN_AUTH_HEADERS.has(name.toLowerCase())) {
      throw new ApiClientError({
        code: 'AUTH_SECRET_TRANSPORT_FORBIDDEN',
        message: 'V2 仅允许使用 HttpOnly 同源 Cookie',
      })
    }
  }
}

function normalizeError(error: unknown): ApiClientError {
  if (isApiClientError(error)) return error
  return new ApiClientError({ code: 'NETWORK_ERROR', message: '网络异常，请稍后重试' })
}

function safeMessage(message: unknown, fallback: string): string {
  if (typeof message !== 'string') return fallback
  const trimmed = message.trim()
  if (!trimmed || trimmed.length > 160) return fallback
  return trimmed
    .replace(/\beyJ[A-Za-z0-9_-]+\.[A-Za-z0-9_-]+\.[A-Za-z0-9_-]+\b/g, '[redacted]')
    .replace(/\b(?:access_token|refresh_token|authorization)\s*[:=]\s*[^\s,;]+/gi, '[redacted]')
}

function hasExposedToken(value: unknown): boolean {
  if (!value || typeof value !== 'object') return false
  const candidate = value as { token?: unknown; refreshToken?: unknown }
  return (
    (typeof candidate.token === 'string' && candidate.token.length > 0) ||
    (typeof candidate.refreshToken === 'string' && candidate.refreshToken.length > 0)
  )
}

function notifyErrorOnce(error: ApiClientError): void {
  const now = Date.now()
  const key = `${error.code}:${error.message}`
  if (lastNotice?.key === key && now - lastNotice.at < ERROR_DEDUPE_WINDOW_MS) return
  lastNotice = { key, at: now }
  lifecycle.onError?.({ code: error.code, message: error.message, traceId: error.traceId })
}
