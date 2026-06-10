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
          {
            path: 'create',
            name: 'ContractCreate',
            component: () => import('@/pages/contract/ContractFormPage.vue'),
            meta: { title: '新建合同', icon: 'file-add' },
          },
          {
            path: ':id',
            name: 'ContractDetail',
            component: () => import('@/pages/contract/ContractDetailPage.vue'),
            meta: { title: '合同详情', hidden: true },
          },
          {
            path: ':id/edit',
            name: 'ContractEdit',
            component: () => import('@/pages/contract/ContractFormPage.vue'),
            meta: { title: '编辑合同', hidden: true },
          },
        ],
      },
      {
        path: 'project',
        name: 'Project',
        component: () => import('@/pages/project/index.vue'),
        meta: { title: '项目管理', icon: 'ProjectOutlined' },
      },
      {
        path: 'partner',
        name: 'Partner',
        component: () => import('@/pages/partner/index.vue'),
        meta: { title: '合作方管理', icon: 'TeamOutlined' },
      },
      {
        path: 'approval',
        name: 'Approval',
        redirect: '/approval/todo',
        meta: { title: '审批管理', icon: 'AuditOutlined' },
        children: [
          {
            path: 'todo',
            name: 'ApprovalTodo',
            component: () => import('@/pages/approval/todo.vue'),
            meta: { title: '我的待办' },
          },
          {
            path: ':instanceId',
            name: 'ApprovalDetail',
            component: () => import('@/pages/approval/detail.vue'),
            meta: { title: '审批详情', hidden: true },
          },
        ],
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
