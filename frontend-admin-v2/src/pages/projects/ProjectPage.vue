<script setup lang="ts">
import { computed } from 'vue'
import { useRoute } from 'vue-router'
import ProjectEditPage from './project-routes/ProjectEditPage.vue'
import ProjectListPage from './project-routes/ProjectListPage.vue'
import ProjectMembersPage from './project-routes/ProjectMembersPage.vue'
import ProjectOverviewPage from './project-routes/ProjectOverviewPage.vue'

const route = useRoute()
const focusedPage = computed(() =>
  route.path === '/project/list'
    ? ProjectListPage
    : route.path.endsWith('/members')
      ? ProjectMembersPage
      : route.path.endsWith('/edit')
        ? ProjectEditPage
        : ProjectOverviewPage,
)
</script>

<template>
  <component :is="focusedPage" />
</template>
