import type { SendRetryState, SendRetryStore } from '@/pages/communication/application/send-message'

type RetryStorage = Pick<Storage, 'getItem' | 'setItem' | 'removeItem'>

function retryStorageKey(conversationId: string): string {
  return `cgc-pms.communication.send.${conversationId}`
}

export function createSessionSendRetryStore(storage: RetryStorage): SendRetryStore {
  return {
    load(conversationId) {
      const key = retryStorageKey(conversationId)
      try {
        const saved = JSON.parse(storage.getItem(key) ?? '{}') as Partial<SendRetryState>
        if (
          !saved.id ||
          typeof saved.createdAt !== 'number' ||
          typeof saved.fingerprint !== 'string'
        ) {
          return null
        }
        return saved as SendRetryState
      } catch {
        storage.removeItem(key)
        return null
      }
    },
    save(conversationId, state) {
      storage.setItem(retryStorageKey(conversationId), JSON.stringify(state))
    },
    clear(conversationId) {
      storage.removeItem(retryStorageKey(conversationId))
    },
  }
}
