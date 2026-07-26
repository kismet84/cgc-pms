import { postClientError } from '@/api/request'

export type ClientErrorSource = 'VUE' | 'WINDOW' | 'PROMISE'
type ClientErrorKind =
  | 'ERROR'
  | 'TYPE_ERROR'
  | 'RANGE_ERROR'
  | 'REFERENCE_ERROR'
  | 'SYNTAX_ERROR'
  | 'UNKNOWN'

const DEDUP_WINDOW_MS = 60_000
const MAX_REPORTS_PER_WINDOW = 5
const sessionSalt = randomFingerprint()
const sentAt = new Map<string, number>()
let windowStartedAt = 0
let reportsInWindow = 0
let globalReportingInstalled = false

export async function reportClientError(source: ClientErrorSource, error: unknown): Promise<void> {
  const kind = errorKind(error)
  const fingerprint = await digest(`${sessionSalt}\n${errorMaterial(error)}`)
  const now = Date.now()
  if (now - windowStartedAt >= DEDUP_WINDOW_MS) {
    windowStartedAt = now
    reportsInWindow = 0
    sentAt.clear()
  }
  if (reportsInWindow >= MAX_REPORTS_PER_WINDOW) return
  if (now - (sentAt.get(fingerprint) ?? 0) < DEDUP_WINDOW_MS) return

  reportsInWindow += 1
  sentAt.set(fingerprint, now)
  try {
    await postClientError({ app: 'LEGACY', source, kind, fingerprint })
  } catch {
    // Observability must never create another user-facing error.
  }
}

export function installGlobalErrorReporting(): void {
  if (globalReportingInstalled) return
  globalReportingInstalled = true
  window.addEventListener('error', (event) => {
    void reportClientError('WINDOW', event.error ?? event.message)
  })
  window.addEventListener('unhandledrejection', (event) => {
    void reportClientError('PROMISE', event.reason)
  })
}

function errorKind(error: unknown): ClientErrorKind {
  const name = error instanceof Error ? error.name : ''
  if (name === 'Error') return 'ERROR'
  if (name === 'TypeError') return 'TYPE_ERROR'
  if (name === 'RangeError') return 'RANGE_ERROR'
  if (name === 'ReferenceError') return 'REFERENCE_ERROR'
  if (name === 'SyntaxError') return 'SYNTAX_ERROR'
  return 'UNKNOWN'
}

function errorMaterial(error: unknown): string {
  if (!(error instanceof Error)) return typeof error
  return `${error.name}\n${error.message}\n${error.stack ?? ''}`
}

async function digest(value: string): Promise<string> {
  if (typeof crypto === 'undefined' || !crypto.subtle) return randomFingerprint()
  const bytes = new TextEncoder().encode(value)
  try {
    const hash = await crypto.subtle.digest('SHA-256', bytes)
    return Array.from(new Uint8Array(hash), (byte) => byte.toString(16).padStart(2, '0')).join('')
  } catch {
    return randomFingerprint()
  }
}

function randomFingerprint(): string {
  const bytes = new Uint8Array(32)
  if (typeof crypto !== 'undefined' && crypto.getRandomValues) {
    crypto.getRandomValues(bytes)
    return Array.from(bytes, (byte) => byte.toString(16).padStart(2, '0')).join('')
  }
  return Array.from({ length: 64 }, () => Math.floor(Math.random() * 16).toString(16)).join('')
}
