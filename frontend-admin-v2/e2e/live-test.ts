import { test as base, expect } from '@playwright/test'

export const test = base.extend({
  page: async ({ page }, use) => {
    await page.addInitScript(() => {
      class LiveTestEventSource extends EventTarget {
        static readonly CONNECTING = 0
        static readonly OPEN = 1
        static readonly CLOSED = 2
        readonly CONNECTING = 0
        readonly OPEN = 1
        readonly CLOSED = 2
        readonly url: string
        readonly withCredentials = false
        readyState = LiveTestEventSource.OPEN
        onopen: ((event: Event) => void) | null = null
        onmessage: ((event: MessageEvent) => void) | null = null
        onerror: ((event: Event) => void) | null = null

        constructor(url: string | URL) {
          super()
          this.url = String(url)
          queueMicrotask(() => {
            if (this.readyState !== LiveTestEventSource.OPEN) return
            const event = new Event('open')
            this.onopen?.(event)
            this.dispatchEvent(event)
          })
        }

        close(): void {
          this.readyState = LiveTestEventSource.CLOSED
        }
      }

      Object.defineProperty(window, 'EventSource', {
        configurable: true,
        value: LiveTestEventSource,
      })
    })
    await use(page)
  },
})

export const streamTest = base
export { expect }
