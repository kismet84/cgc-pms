import { reactive, ref, watch, type Ref } from 'vue'

export type V2ToastType = 'success' | 'info' | 'warn' | 'error'

export interface V2ToastItem {
  id: number
  type: V2ToastType
  title: string
  message: string
}

export const V2_TOAST_DURATION_MS = 3500
export const toastItems = reactive<V2ToastItem[]>([])

let nextId = 0
const timers = new Map<number, ReturnType<typeof setTimeout>>()

export function dismissToast(id: number): void {
  const index = toastItems.findIndex((item) => item.id === id)
  if (index >= 0) toastItems.splice(index, 1)
  clearTimeout(timers.get(id))
  timers.delete(id)
}

export function showToast(type: V2ToastType, title: string, message: string): number {
  const id = ++nextId
  toastItems.push({ id, type, title, message })
  timers.set(
    id,
    setTimeout(() => dismissToast(id), V2_TOAST_DURATION_MS),
  )
  return id
}

export function useToastMessage(type: V2ToastType = 'success', title = '操作成功'): Ref<string> {
  const message = ref('')
  watch(
    message,
    (value) => {
      if (!value) return
      showToast(type, title, value)
      message.value = ''
    },
    { flush: 'sync' },
  )
  return message
}
