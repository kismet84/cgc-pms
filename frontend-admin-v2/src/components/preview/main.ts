import { createApp } from 'vue'
import { createMemoryHistory, createRouter } from 'vue-router'
import DesignSystemPreview from './DesignSystemPreview.vue'
import '@/styles/base.css'

const router = createRouter({
  history: createMemoryHistory(),
  routes: [{ path: '/:pathMatch(.*)*', component: { render: () => null } }],
})

createApp(DesignSystemPreview).use(router).mount('#design-system-preview')
