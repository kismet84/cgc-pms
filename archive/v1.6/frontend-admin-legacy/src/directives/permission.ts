import type { Directive } from 'vue'
import { useUserStore } from '@/stores/user'

const vPermission: Directive<HTMLElement, string> = {
  mounted(el: HTMLElement, binding) {
    const code = binding.value
    if (!code) return

    const userStore = useUserStore()
    if (!userStore.hasPermission(code)) {
      el.parentNode?.removeChild(el)
    }
  },
}

export default vPermission
