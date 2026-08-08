import { featureFlags } from './featureFlags'

export interface ResilientStream {
  close(): void
}

const CLIENT_ID_KEY = 'cgc-pms-stream-client-id'
const CLIENT_DOCUMENT_KEY = 'cgc-pms-stream-document'
const CLIENT_ID_PATTERN = /^[A-Za-z0-9._-]{1,64}$/

function resolveClientId(): string {
  if (!featureFlags.notificationMultiClient.enabled) return 'default'
  const existing = sessionStorage.getItem(CLIENT_ID_KEY)
  const documentId = String(performance.timeOrigin)
  const navigation = performance.getEntriesByType('navigation')[0] as
    PerformanceNavigationTiming | undefined
  const sameDocument = sessionStorage.getItem(CLIENT_DOCUMENT_KEY) === documentId
  const reloadedDocument = navigation?.type === 'reload' || navigation?.type === 'back_forward'
  if (existing && CLIENT_ID_PATTERN.test(existing) && (sameDocument || reloadedDocument)) {
    sessionStorage.setItem(CLIENT_DOCUMENT_KEY, documentId)
    return existing
  }
  const created = crypto.randomUUID()
  sessionStorage.setItem(CLIENT_ID_KEY, created)
  sessionStorage.setItem(CLIENT_DOCUMENT_KEY, documentId)
  return created
}

const clientId = resolveClientId()

export function currentStreamClientId(): string {
  return clientId
}

export function openResilientStream<T>(
  path: string,
  eventNames: string[],
  onOpen: () => void,
  onEvent: (event: T) => void,
  onError?: () => void,
): ResilientStream {
  const url = new URL(`/api${path}`, window.location.origin)
  url.searchParams.set('clientId', clientId)
  const source = new EventSource(`${url.pathname}${url.search}`)
  const receive = (event: MessageEvent<string>) => {
    try {
      onEvent(JSON.parse(event.data) as T)
    } catch {
      onError?.()
    }
  }
  source.onopen = onOpen
  source.onerror = () => onError?.() // Native EventSource reconnects while source remains open.
  for (const name of eventNames) source.addEventListener(name, receive as EventListener)
  let closed = false
  const closeOnPageHide = (event: PageTransitionEvent) => {
    if (event.persisted) return
    close()
  }
  const close = () => {
    if (closed) return
    closed = true
    window.removeEventListener('pagehide', closeOnPageHide)
    source.close()
  }
  window.addEventListener('pagehide', closeOnPageHide)
  return { close }
}
