import { createRouter, createWebHistory } from 'vue-router'
import { useUserStore } from '@/store/user'

const routes = [
  {
    path: '/login',
    name: 'UserLogin',
    component: () => import('@/views/login/UserLogin.vue'),
    meta: { title: '用户登录', guest: true }
  },
  {
    path: '/register',
    name: 'UserRegister',
    component: () => import('@/views/login/UserRegister.vue'),
    meta: { title: '用户注册', guest: true }
  },
  {
    path: '/',
    name: 'Home',
    component: () => import('@/views/Home.vue'),
    meta: { title: '地铁购票' }
  },
  {
    path: '/tickets',
    name: 'TicketOrders',
    component: () => import('@/views/TicketOrders.vue'),
    meta: { title: '我的订单', requiresAuth: true }
  },
  {
    path: '/messages',
    name: 'MessageCenter',
    component: () => import('@/views/MessageCenter.vue'),
    meta: { title: '消息中心', requiresAuth: true }
  },
  {
    path: '/:pathMatch(.*)*',
    name: 'NotFound',
    redirect: '/'
  },
]

const router = createRouter({
  history: createWebHistory(),
  routes,
})

router.beforeEach((to, from, next) => {
  document.title = to.meta.title || '城市轨道交通查询与票务管理系统'

  const userStore = useUserStore()

  if (to.meta.guest) {
    if (userStore.isLoggedIn()) {
      return next('/')
    }
    return next()
  }

  if (to.meta.requiresAuth) {
    if (!userStore.isLoggedIn()) {
      return next('/login')
    }
  }

  next()
})

export default router
