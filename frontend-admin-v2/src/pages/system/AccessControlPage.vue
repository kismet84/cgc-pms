<script setup lang="ts">
import { computed } from 'vue'
import { useRoute } from 'vue-router'
import PermissionListPage from './access-control/PermissionListPage.vue'
import RoleManagementPage from './access-control/RoleManagementPage.vue'
import UserManagementPage from './access-control/UserManagementPage.vue'

const route = useRoute()
const page = computed(() => {
  if (route.path.endsWith('/roles')) return RoleManagementPage
  if (route.path.endsWith('/permissions')) return PermissionListPage
  return UserManagementPage
})
</script>

<template>
  <component :is="page" />
</template>
