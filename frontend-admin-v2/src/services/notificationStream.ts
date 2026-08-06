import { featureFlags } from './featureFlags'

export interface ResilientStream {
  close(): void
}

const clientId = featureFlags.notificationMultiClient.enabled ? crypto.randomUUID() : 'default'

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
  return { close: () => source.close() }
}
