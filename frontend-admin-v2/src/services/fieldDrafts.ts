export type FieldDraftStatus =
  'DRAFT' | 'PENDING' | 'SYNCING' | 'RETRYABLE' | 'CONFLICT' | 'REJECTED' | 'SYNCED'

export type FieldDraftKind = 'DAILY_LOG' | 'QUALITY_ISSUE' | 'QUALITY_RECTIFICATION'

export interface FieldDraft<T = unknown> {
  id: string
  kind: FieldDraftKind
  clientRequestId: string
  status: FieldDraftStatus
  payload: T
  createdAt: number
  updatedAt: number
  expiresAt: number
  error?: string
}

export interface FieldDraftAttachment {
  id: string
  operationId: string
  name: string
  type: string
  size: number
  file: Blob
}

const DB_PREFIX = 'cgc-pms-field:'
const DB_VERSION = 1
const SCHEMA_VERSION = '1'
const TTL_MS = 7 * 24 * 60 * 60 * 1_000
const MAX_OPERATIONS = 50
const MAX_ATTACHMENTS = 20
const KNOWN_DATABASES_KEY = 'cgc-pms-field-databases'
const CLEAR_SIGNAL_KEY = 'cgc-pms-field-clear'
const FORBIDDEN_KEYS = /(?:authorization|cookie|password|refresh.?token|access.?token|userinfo)/i
const openDatabases = new Set<IDBDatabase>()

window.addEventListener('storage', (event) => {
  if (event.key === CLEAR_SIGNAL_KEY) closeOpenDatabases()
})

export function fieldDraftDatabaseName(tenantId: string, userId: string): string {
  const tenant = tenantId.trim()
  const user = userId.trim()
  if (!tenant || !user) throw new TypeError('离线草稿缺少租户或用户标识')
  return `${DB_PREFIX}${tenant}:${user}:${SCHEMA_VERSION}`
}

export function isExpired(draft: Pick<FieldDraft, 'expiresAt'>, now = Date.now()): boolean {
  return draft.expiresAt <= now
}

export function fieldDraftStatusLabel(status: FieldDraftStatus): string {
  return {
    DRAFT: '本地草稿',
    PENDING: '待同步',
    SYNCING: '同步中',
    RETRYABLE: '可重试',
    CONFLICT: '版本冲突',
    REJECTED: '服务端拒绝',
    SYNCED: '已同步',
  }[status]
}

export function fieldDraftSyncFailure(code?: string, status?: number): FieldDraftStatus {
  if (code?.includes('VERSION_CONFLICT') || code === 'IDEMPOTENCY_CONFLICT') return 'CONFLICT'
  if (code && /(?:FORBIDDEN|VALIDATION|NOT_FOUND|STATE|PERMISSION)/.test(code)) return 'REJECTED'
  if (status && status >= 400 && status < 500) return 'REJECTED'
  return 'RETRYABLE'
}

export function hasDraftCapacity(count: number, existing: boolean, limit: number): boolean {
  return existing || count < limit
}

export function cloneDraftPayload<T>(value: T): T {
  return JSON.parse(JSON.stringify(value)) as T
}

export class FieldDraftRepository {
  readonly databaseName: string
  private database: Promise<IDBDatabase> | null = null

  constructor(tenantId: string, userId: string) {
    this.databaseName = fieldDraftDatabaseName(tenantId, userId)
    rememberDatabase(this.databaseName)
  }

  async list(kind?: FieldDraftKind): Promise<FieldDraft[]> {
    await this.prune()
    const database = await this.open()
    const rows = await request<FieldDraft[]>(
      database.transaction('operations').objectStore('operations').getAll(),
    )
    return rows
      .filter((row) => !kind || row.kind === kind)
      .sort((a, b) => b.updatedAt - a.updatedAt)
  }

  async get<T>(id: string): Promise<FieldDraft<T> | undefined> {
    await this.prune()
    const database = await this.open()
    return request<FieldDraft<T> | undefined>(
      database.transaction('operations').objectStore('operations').get(id),
    )
  }

  async put<T>(
    input: Pick<FieldDraft<T>, 'id' | 'kind' | 'clientRequestId' | 'status' | 'payload'> &
      Partial<Pick<FieldDraft, 'error'>>,
  ): Promise<FieldDraft<T>> {
    const payload = cloneDraftPayload(input.payload)
    assertSafePayload(payload)
    await this.prune()
    const database = await this.open()
    const existing = await this.get<T>(input.id)
    if (!existing) {
      const count = await request<number>(
        database.transaction('operations').objectStore('operations').count(),
      )
      if (!hasDraftCapacity(count, false, MAX_OPERATIONS))
        throw new RangeError('本地操作已达 50 条上限')
    }
    const now = Date.now()
    const draft: FieldDraft<T> = {
      ...input,
      payload,
      createdAt: existing?.createdAt ?? now,
      updatedAt: now,
      expiresAt: now + TTL_MS,
    }
    await request(
      database.transaction('operations', 'readwrite').objectStore('operations').put(draft),
    )
    return draft
  }

  async remove(id: string): Promise<void> {
    const database = await this.open()
    const transaction = database.transaction(['operations', 'attachments'], 'readwrite')
    transaction.objectStore('operations').delete(id)
    const attachments = await request<FieldDraftAttachment[]>(
      transaction.objectStore('attachments').getAll(),
    )
    for (const attachment of attachments) {
      if (attachment.operationId === id)
        transaction.objectStore('attachments').delete(attachment.id)
    }
    await completed(transaction)
  }

  async putAttachment(operationId: string, file: File): Promise<FieldDraftAttachment> {
    const database = await this.open()
    const id = `${operationId}:${file.name}:${file.lastModified}`
    const existing = await request<FieldDraftAttachment | undefined>(
      database.transaction('attachments').objectStore('attachments').get(id),
    )
    const count = await request<number>(
      database.transaction('attachments').objectStore('attachments').count(),
    )
    if (!hasDraftCapacity(count, Boolean(existing), MAX_ATTACHMENTS)) {
      throw new RangeError('本地附件已达 20 个上限')
    }
    const attachment = { id, operationId, name: file.name, type: file.type, size: file.size, file }
    await request(
      (await this.open())
        .transaction('attachments', 'readwrite')
        .objectStore('attachments')
        .put(attachment),
    )
    return attachment
  }

  async attachments(operationId: string): Promise<FieldDraftAttachment[]> {
    const database = await this.open()
    const rows = await request<FieldDraftAttachment[]>(
      database.transaction('attachments').objectStore('attachments').getAll(),
    )
    return rows.filter((row) => row.operationId === operationId)
  }

  async removeAttachments(operationId: string): Promise<void> {
    const database = await this.open()
    const rows = await request<FieldDraftAttachment[]>(
      database.transaction('attachments').objectStore('attachments').getAll(),
    )
    const transaction = database.transaction('attachments', 'readwrite')
    for (const row of rows) {
      if (row.operationId === operationId) transaction.objectStore('attachments').delete(row.id)
    }
    await completed(transaction)
  }

  async clear(): Promise<void> {
    const database = await this.database?.catch(() => null)
    database?.close()
    if (database) openDatabases.delete(database)
    this.database = null
    await deleteDatabase(this.databaseName)
    forgetDatabase(this.databaseName)
  }

  private async prune(): Promise<void> {
    const database = await this.open()
    const rows = await request<FieldDraft[]>(
      database.transaction('operations').objectStore('operations').getAll(),
    )
    await Promise.all(rows.filter((row) => isExpired(row)).map((row) => this.remove(row.id)))
  }

  private open(): Promise<IDBDatabase> {
    if (!('indexedDB' in window)) return Promise.reject(new Error('当前浏览器不支持本地草稿'))
    if (!this.database) {
      this.database = new Promise((resolve, reject) => {
        const pending = indexedDB.open(this.databaseName, DB_VERSION)
        pending.onupgradeneeded = () => {
          const database = pending.result
          if (!database.objectStoreNames.contains('operations'))
            database.createObjectStore('operations', { keyPath: 'id' })
          if (!database.objectStoreNames.contains('attachments'))
            database.createObjectStore('attachments', { keyPath: 'id' })
        }
        pending.onsuccess = () => {
          openDatabases.add(pending.result)
          pending.result.onversionchange = () => {
            pending.result.close()
            openDatabases.delete(pending.result)
          }
          resolve(pending.result)
        }
        pending.onerror = () => reject(pending.error)
      })
    }
    return this.database
  }
}

export async function clearAllFieldDrafts(): Promise<void> {
  const names = knownDatabases()
  try {
    localStorage.setItem(CLEAR_SIGNAL_KEY, crypto.randomUUID())
  } catch {
    // Current tab cleanup still proceeds when cross-tab signaling is unavailable.
  }
  closeOpenDatabases()
  await Promise.allSettled(names.map(deleteDatabase))
  localStorage.removeItem(KNOWN_DATABASES_KEY)
}

function closeOpenDatabases(): void {
  for (const database of openDatabases) database.close()
  openDatabases.clear()
}

function assertSafePayload(value: unknown, seen = new Set<unknown>()): void {
  if (!value || typeof value !== 'object' || value instanceof Blob || seen.has(value)) return
  seen.add(value)
  for (const [key, child] of Object.entries(value)) {
    if (FORBIDDEN_KEYS.test(key)) throw new TypeError('本地草稿禁止保存认证或完整用户信息')
    assertSafePayload(child, seen)
  }
}

function request<T = undefined>(pending: IDBRequest<T>): Promise<T> {
  return new Promise((resolve, reject) => {
    pending.onsuccess = () => resolve(pending.result)
    pending.onerror = () => reject(pending.error)
  })
}

function completed(transaction: IDBTransaction): Promise<void> {
  return new Promise((resolve, reject) => {
    transaction.oncomplete = () => resolve()
    transaction.onerror = () => reject(transaction.error)
    transaction.onabort = () => reject(transaction.error)
  })
}

function deleteDatabase(name: string): Promise<void> {
  if (!('indexedDB' in window)) return Promise.resolve()
  return new Promise((resolve, reject) => {
    const pending = indexedDB.deleteDatabase(name)
    pending.onsuccess = () => resolve()
    pending.onerror = () => reject(pending.error)
    pending.onblocked = () => reject(new Error('本地草稿仍被其他页面占用'))
  })
}

function knownDatabases(): string[] {
  try {
    const value = JSON.parse(localStorage.getItem(KNOWN_DATABASES_KEY) || '[]') as unknown
    return Array.isArray(value)
      ? value.filter((name): name is string => typeof name === 'string')
      : []
  } catch {
    return []
  }
}

function rememberDatabase(name: string): void {
  localStorage.setItem(
    KNOWN_DATABASES_KEY,
    JSON.stringify([...new Set([...knownDatabases(), name])]),
  )
}

function forgetDatabase(name: string): void {
  localStorage.setItem(
    KNOWN_DATABASES_KEY,
    JSON.stringify(knownDatabases().filter((item) => item !== name)),
  )
}
