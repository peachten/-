import { createRouter, createWebHistory } from 'vue-router'
import { useUserStore } from '../stores/user'

const routes = [
  {
    path: '/login',
    name: 'login',
    component: () => import('../views/Login.vue'),
    meta: { public: true }
  },
  {
    path: '/',
    component: () => import('../views/Layout.vue'),
    redirect: '/link',
    children: [
      {
        path: 'link',
        name: 'link',
        component: () => import('../views/LinkList.vue')
      },
      {
        path: 'group',
        name: 'group',
        component: () => import('../views/GroupList.vue')
      },
      {
        path: 'dashboard',
        name: 'dashboard',
        component: () => import('../views/Dashboard.vue')
      }
    ]
  },
  {
    path: '/:pathMatch(.*)*',
    redirect: '/link'
  }
]

const router = createRouter({
  history: createWebHistory(),
  routes
})

// 登录守卫：除 /login 外都需要 token
router.beforeEach((to) => {
  const userStore = useUserStore()

  if (!to.meta.public && !userStore.token) {
    return { path: '/login', query: { redirect: to.fullPath } }
  }
  if (to.path === '/login' && userStore.token) {
    return '/link'
  }
  return true
})

export default router
