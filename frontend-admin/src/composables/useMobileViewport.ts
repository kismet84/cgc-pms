import { onBeforeUnmount, onMounted, ref } from 'vue'

export const MOBILE_VIEWPORT_BREAKPOINT = 500
export const MOBILE_VIEWPORT_QUERY = `(width < ${MOBILE_VIEWPORT_BREAKPOINT}px)`
export const COMPACT_DESKTOP_VIEWPORT_QUERY = '(500px <= width < 900px)'

export function useMobileViewport() {
  const isMobile = ref(false)
  const isCompactDesktop = ref(false)
  let mobileMediaQuery: MediaQueryList | undefined
  let compactDesktopMediaQuery: MediaQueryList | undefined
  let usingResizeFallback = false

  function syncMobileState(event: MediaQueryList | MediaQueryListEvent) {
    isMobile.value = event.matches
  }

  function syncCompactDesktopState(event: MediaQueryList | MediaQueryListEvent) {
    isCompactDesktop.value = event.matches
  }

  function syncFromViewportWidth() {
    isMobile.value = window.innerWidth < MOBILE_VIEWPORT_BREAKPOINT
    isCompactDesktop.value =
      window.innerWidth >= MOBILE_VIEWPORT_BREAKPOINT && window.innerWidth < 900
  }

  onMounted(() => {
    if (typeof window.matchMedia !== 'function') {
      usingResizeFallback = true
      syncFromViewportWidth()
      window.addEventListener('resize', syncFromViewportWidth)
      return
    }

    mobileMediaQuery = window.matchMedia(MOBILE_VIEWPORT_QUERY)
    compactDesktopMediaQuery = window.matchMedia(COMPACT_DESKTOP_VIEWPORT_QUERY)
    syncMobileState(mobileMediaQuery)
    syncCompactDesktopState(compactDesktopMediaQuery)
    mobileMediaQuery.addEventListener('change', syncMobileState)
    compactDesktopMediaQuery.addEventListener('change', syncCompactDesktopState)
  })

  onBeforeUnmount(() => {
    if (usingResizeFallback) window.removeEventListener('resize', syncFromViewportWidth)
    mobileMediaQuery?.removeEventListener('change', syncMobileState)
    compactDesktopMediaQuery?.removeEventListener('change', syncCompactDesktopState)
  })

  return { isMobile, isCompactDesktop }
}
