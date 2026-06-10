import { createRouter, createWebHistory, type RouteRecordRaw } from 'vue-router'
import BasicLayout from '@/layouts/BasicLayout.vue'
import { useUserStore } from '@/stores/user'

export const routes: RouteRecordRaw[] = [
  {
    path: '/login',
    name: 'Login',
    component: () => import('@/pages/login/index.vue'),
    meta: { title: '登录', hidden: true, public: true },
  },
  {
    path: '/',
    component: BasicLayout,
    redirect: '/dashboard',
    children: [
      {
        path: 'dashboard',
        name: 'Dashboard',
        component: () => import('@/pages/dashboard/index.vue'),
        meta: { title: '首页', icon: 'HomeOutlined' },
      },
      {
        path: 'contract',
        name: 'Contract',
        redirect: '/contract/ledger',
        meta: { title: '合同管理', icon: 'FileTextOutlined' },
        children: [
          {
            path: 'ledger',
            name: 'ContractLedger',
            component: () => import('@/pages/contract/ContractLedgerPage.vue'),
            meta: { title: '合同台账' },
          },
          {
            path: 'approval',
            name: 'ContractApproval',
            component: () => import('@/pages/dashboard/index.vue'),
            meta: { title: '合同审批' },
          },
        ],
      },
      {
        path: 'project',
        name: 'Project',
        component: () => import('@/pages/dashboard/index.vue'),
        meta: { title: '项目管理', icon: 'ProjectOutlined' },
      },
      {
        path: 'system',
        name: 'System',
        component: () => import('@/pages/dashboard/index.vue'),
        meta: { title: '系统设置', icon: 'SettingOutlined' },
      },
    ],
  },
  {
    path: '/:pathMatch(.*)*',
    name: 'NotFound',
    component: () => import('@/pages/error/404.vue'),
    meta: { title: '页面不存在', hidden: true, public: true },
  },
]

const router = createRouter({
  history: createWebHistory(),
  routes,
})

const WHITE_LIST = ['/login']

router.beforeEach((to) => {
  const userStore = useUserStore()
  if (to.meta?.public || WHITE_LIST.includes(to.path)) {
    return true
  }
  if (!userStore.isLogin) {
    return { path: '/login', query: { redirect: to.fullPath } }
  }
  return true
})

router.afterEach((to) => {
  const title = (to.meta?.title as string) || ''
  document.title = title ? `${title} - 建筑工程总包项目管理系统` : '建筑工程总包项目管理系统'
})

export default router
