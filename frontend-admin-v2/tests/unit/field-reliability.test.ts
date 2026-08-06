import { readFileSync } from 'node:fs'
import { resolve } from 'node:path'
import { describe, expect, it, vi } from 'vitest'
import {
  clearAllFieldDrafts,
  cloneDraftPayload,
  fieldDraftDatabaseName,
  fieldDraftStatusLabel,
  fieldDraftSyncFailure,
  hasDraftCapacity,
  isExpired,
} from '@/services/fieldDrafts'
import { isFeatureEnabled } from '@/services/featureFlags'

const source = (path: string) => readFileSync(resolve(process.cwd(), path), 'utf8')

describe('field reliability contracts', () => {
  it('keeps field capabilities fail-closed unless explicitly enabled', () => {
    expect(isFeatureEnabled(undefined)).toBe(false)
    expect(isFeatureEnabled('false')).toBe(false)
    expect(isFeatureEnabled('true')).toBe(true)
  })
  it('keeps Service Worker caching limited to same-origin shell and never handles API', () => {
    const worker = source('public/sw.js')
    expect(worker).toContain('url.origin !== self.location.origin')
    expect(worker).toContain("url.pathname.startsWith('/api/')")
    expect(worker.indexOf("url.pathname.startsWith('/api/')")).toBeLessThan(
      worker.indexOf('event.respondWith'),
    )
    expect(worker).toContain("const CACHE_NAME = 'cgc-pms-shell-v1'")
    expect(worker).toContain('self.skipWaiting()')
    expect(worker).toContain("cache.put('/index.html', response.clone())")
  })

  it('isolates IndexedDB namespaces and enforces TTL, capacity and cleanup contracts', async () => {
    expect(fieldDraftDatabaseName('tenant-a', 'user-1')).toBe('cgc-pms-field:tenant-a:user-1:1')
    expect(fieldDraftDatabaseName('tenant-b', 'user-1')).not.toBe(
      fieldDraftDatabaseName('tenant-a', 'user-1'),
    )
    expect(isExpired({ expiresAt: 99 }, 100)).toBe(true)
    expect(isExpired({ expiresAt: 101 }, 100)).toBe(false)
    expect(hasDraftCapacity(49, false, 50)).toBe(true)
    expect(hasDraftCapacity(50, false, 50)).toBe(false)
    expect(hasDraftCapacity(50, true, 50)).toBe(true)
    expect(fieldDraftStatusLabel('CONFLICT')).toBe('版本冲突')
    expect(fieldDraftSyncFailure('VERSION_CONFLICT')).toBe('CONFLICT')
    expect(fieldDraftSyncFailure('SITE_DAILY_LOG_VERSION_CONFLICT')).toBe('CONFLICT')
    expect(fieldDraftSyncFailure('VALIDATION_FAILED')).toBe('REJECTED')
    expect(fieldDraftSyncFailure('QS_ISSUE_NOT_RECTIFYING', 400)).toBe('REJECTED')
    expect(fieldDraftSyncFailure('NETWORK_ERROR')).toBe('RETRYABLE')
    const proxied = new Proxy({ command: { projectId: '1' } }, {})
    expect(cloneDraftPayload(proxied)).toEqual({ command: { projectId: '1' } })

    const repository = source('src/services/fieldDrafts.ts')
    expect(repository).toContain('const MAX_OPERATIONS = 50')
    expect(repository).toContain('const MAX_ATTACHMENTS = 20')
    expect(repository).toContain('const TTL_MS = 7 * 24 * 60 * 60 * 1_000')
    expect(repository).toContain('export async function clearAllFieldDrafts')
    expect(repository).toContain('event.key === CLEAR_SIGNAL_KEY')
    expect(repository).toContain('FORBIDDEN_KEYS')

    const deleted: string[] = []
    vi.stubGlobal('indexedDB', {
      deleteDatabase(name: string) {
        deleted.push(name)
        const pending: { onsuccess?: () => void } = {}
        queueMicrotask(() => pending.onsuccess?.())
        return pending
      },
    })
    localStorage.setItem(
      'cgc-pms-field-databases',
      JSON.stringify(['cgc-pms-field:tenant-a:user-1:1']),
    )
    await clearAllFieldDrafts()
    expect(deleted).toEqual(['cgc-pms-field:tenant-a:user-1:1'])
    expect(localStorage.getItem('cgc-pms-field-databases')).toBeNull()
    vi.unstubAllGlobals()
  })

  it('uses one client id per tab and closing one SSE client does not close another', async () => {
    const streams: Array<{ url: string; closed: boolean }> = []
    class FakeEventSource {
      onopen: (() => void) | null = null
      onerror: (() => void) | null = null
      readonly record: { url: string; closed: boolean }
      constructor(url: string) {
        this.record = { url, closed: false }
        streams.push(this.record)
      }
      addEventListener = vi.fn()
      close(): void {
        this.record.closed = true
      }
    }
    vi.stubGlobal('EventSource', FakeEventSource)
    vi.resetModules()
    const { currentStreamClientId, openResilientStream } =
      await import('@/services/notificationStream')
    const first = openResilientStream('/notifications/stream', ['notification'], vi.fn(), vi.fn())
    const second = openResilientStream('/notifications/stream', ['notification'], vi.fn(), vi.fn())

    expect(streams).toHaveLength(2)
    expect(streams.every((item) => item.url.includes(`clientId=${currentStreamClientId()}`))).toBe(
      true,
    )
    first.close()
    expect(streams.map((item) => item.closed)).toEqual([true, false])
    second.close()
    vi.unstubAllGlobals()
  })

  it('wires daily and quality drafts without duplicating server state machines', () => {
    const daily = source('src/pages/delivery/DailyLogPage.vue')
    const quality = source('src/pages/delivery/QualitySafetyPage.vue')
    for (const contract of [
      'saveLocalDailyDraft',
      'syncLocalDailyDraft',
      "repository.list('DAILY_LOG')",
      '本地草稿项目',
      '日报正式提交必须在线完成',
      'SITE_DAILY_LOG',
    ])
      expect(daily).toContain(contract)
    for (const contract of [
      "saveQualityDraft('ISSUE')",
      "saveQualityDraft('RECTIFICATION')",
      "syncQualityDraft('ISSUE')",
      '该业务动作必须在线完成',
      'QS_RECTIFICATION',
    ])
      expect(quality).toContain(contract)
    expect(quality).toContain('await submitQualityRectification(created.id)')
    expect(quality).toContain('await reinspectQualityRectification')
  })

  it('keeps 390px layouts bounded with scrollable ledgers and single-column forms', () => {
    const daily = source('src/pages/delivery/DailyLogPage.vue')
    const quality = source('src/pages/delivery/QualitySafetyPage.vue')
    expect(daily).toContain('.daily-log-page__table-wrap {\n  overflow: auto;')
    expect(daily).toContain('@media (max-width: 40rem)')
    expect(quality).toContain('.quality-page__table-wrap {\n  overflow-x: auto;')
    expect(quality).toContain('@media (max-width: 40rem)')
    expect(quality).toContain('grid-template-columns: 1fr;')
  })

  it('separates finite API, SSE and site-file proxy policies', () => {
    const nginx = source('nginx.conf')
    expect(nginx).toContain('location = /api/notifications/stream')
    expect(nginx).toContain('location = /api/communications/stream')
    expect(nginx).toContain('location ^~ /api/files/')
    expect(nginx).toContain('proxy_request_buffering off;')
    expect(nginx).toContain('proxy_read_timeout 60s;')
    expect(nginx).toContain('proxy_buffering on;')
  })
})
