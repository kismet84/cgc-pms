import { computed } from 'vue'
import type { NavigationAccess, NavigationWorkspace } from '@/router/navigation'
import { useUserStore } from '@/stores/user'

export function useNavigationAccess() {
  const userStore = useUserStore()
  const isAdmin = computed(
    () => userStore.roles.includes('ADMIN') || userStore.roles.includes('SUPER_ADMIN'),
  )

  function canAccess(item: NavigationAccess) {
    if (item.adminOnly && userStore.roles.length > 0 && !isAdmin.value) return false
    if (
      item.permission &&
      userStore.roles.length > 0 &&
      !isAdmin.value &&
      !userStore.hasPermission(item.permission)
    ) {
      return false
    }
    return true
  }

  function getVisibleTabs(workspace: NavigationWorkspace) {
    if (!canAccess(workspace)) return []
    return workspace.tabs.filter((tab) => canAccess(tab))
  }

  return { canAccess, getVisibleTabs, isAdmin }
}
