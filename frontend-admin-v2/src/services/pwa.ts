import { featureFlags } from './featureFlags'

const CACHE_PREFIX = 'cgc-pms-shell-'

export async function configurePwa(onUpdate: () => void): Promise<void> {
  if (!('serviceWorker' in navigator) || !('caches' in window)) return
  if (!featureFlags.pwa.enabled) {
    await disablePwa()
    return
  }

  const registration = await navigator.serviceWorker.register('/sw.js')
  const notifyWaiting = () => {
    if (registration.waiting) onUpdate()
  }
  notifyWaiting()
  registration.addEventListener('updatefound', () => {
    registration.installing?.addEventListener('statechange', () => {
      if (registration.installing?.state === 'installed' && navigator.serviceWorker.controller) {
        onUpdate()
      }
    })
  })
}

export async function disablePwa(): Promise<void> {
  const registrations = await navigator.serviceWorker.getRegistrations()
  await Promise.all(registrations.map((registration) => registration.unregister()))
  const names = await caches.keys()
  await Promise.all(
    names.filter((name) => name.startsWith(CACHE_PREFIX)).map((name) => caches.delete(name)),
  )
}
