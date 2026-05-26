import { createRouter, createWebHistory } from 'vue-router'
import { ElMessage } from 'element-plus'
import { useUserStore } from '@/store/user'

const routes = [
  {
    path: '/login',
    redirect: '/admin/login'
  },
  {
    path: '/admin/login',
    name: 'AdminLogin',
    component: () => import('@/views/login/AdminLogin.vue'),
    meta: { title: '管理员登录', guest: true }
  },
  {
    path: '/super-admin/login',
    name: 'SuperAdminLogin',
    component: () => import('@/views/login/SuperAdminLogin.vue'),
    meta: { title: '超级管理员登录', guest: true }
  },
  {
    path: '/root-admin/login',
    name: 'RootAdminLogin',
    component: () => import('@/views/login/RootAdminLogin.vue'),
    meta: { title: '最高级管理员登录', guest: true }
  },
  {
    path: '/',
    component: () => import('@/views/AdminLayout.vue'),
    meta: { requiresAuth: true },
    children: [
      {
        path: '',
        name: 'Dashboard',
        component: () => import('@/views/Dashboard.vue'),
        meta: { title: '管理控制台' }
      },
      {
        path: 'users',
        name: 'UserManage',
        component: () => import('@/views/manage/UserManage.vue'),
        meta: { title: '用户管理' }
      },
      {
        path: 'countries',
        name: 'CountryManage',
        component: () => import('@/views/manage/CountryManage.vue'),
        meta: { title: '国家管理' }
      },
      {
        path: 'cities',
        name: 'CityManage',
        component: () => import('@/views/manage/CityManage.vue'),
        meta: { title: '城市管理' }
      },
      {
        path: 'city-detail/:id',
        name: 'CityDetail',
        component: () => import('@/views/manage/CityDetail.vue'),
        meta: { title: '城市详情' }
      },
      {
        path: 'metro-lines',
        name: 'MetroLineManage',
        component: () => import('@/views/manage/MetroLineManage.vue'),
        meta: { title: '地铁线路管理' }
      },
      {
        path: 'metro-line-detail/:id',
        name: 'MetroLineDetail',
        component: () => import('@/views/manage/MetroLineDetail.vue'),
        meta: { title: '线路详情' }
      },
      {
        path: 'metro-stations',
        name: 'MetroStationManage',
        component: () => import('@/views/manage/MetroStationManage.vue'),
        meta: { title: '地铁站管理' }
      },
      {
        path: 'metro-station-detail/:id',
        name: 'MetroStationDetail',
        component: () => import('@/views/manage/MetroStationDetail.vue'),
        meta: { title: '站点详情' }
      },
      {
        path: 'station-reorder/:lineId?',
        name: 'StationReorder',
        component: () => import('@/views/manage/StationReorder.vue'),
        meta: { title: '站点排序' }
      },
      {
        path: 'station-line-assign',
        name: 'StationLineAssign',
        component: () => import('@/views/manage/StationLineAssign.vue'),
        meta: { title: '站点线路分配', minRoleCode: 2 }
      },
      {
        path: 'geocode-console',
        name: 'GeocodeConsole',
        component: () => import('@/views/manage/GeocodeConsole.vue'),
        meta: { title: '地理编码', minRoleCode: 3 }
      },
      {
        path: 'geocode-query',
        name: 'GeocodeQuery',
        component: () => import('@/views/manage/GeocodeQuery.vue'),
        meta: { title: '地理编码查询', minRoleCode: 3 }
      },
      {
        path: 'cache-manage',
        name: 'CacheManage',
        component: () => import('@/views/manage/CacheManage.vue'),
        meta: { title: '缓存管理', minRoleCode: 3 }
      },
      {
        path: 'system-config',
        name: 'SystemConfigManage',
        component: () => import('@/views/manage/SystemConfigManage.vue'),
        meta: { title: '系统配置', minRoleCode: 4 }
      },
      {
        path: 'agent-config',
        name: 'AgentConfigManage',
        component: () => import('@/views/manage/AgentConfigManage.vue'),
        meta: { title: 'Agent 助手配置', minRoleCode: 3 }
      },
      {
        path: 'chat-manage',
        name: 'ChatSessionManage',
        component: () => import('@/views/manage/ChatSessionManage.vue'),
        meta: { title: 'Agent 会话管理', minRoleCode: 2 }
      },
      {
        path: 'decrypt-approval',
        name: 'DecryptApproval',
        component: () => import('@/views/manage/DecryptApproval.vue'),
        meta: { title: '明文查看审批', minRoleCode: 3 }
      },
      {
        path: 'rag-knowledge',
        name: 'RagKnowledgeManage',
        component: () => import('@/views/manage/RagKnowledgeManage.vue'),
        meta: { title: 'RAG 知识库', minRoleCode: 2 }
      },
      {
        path: 'ticket-order-manage',
        name: 'TicketOrderManage',
        component: () => import('@/views/manage/TicketOrderManage.vue'),
        meta: { title: '订单管理', minRoleCode: 2 }
      },
      {
        path: 'messages',
        name: 'MessageManage',
        component: () => import('@/views/manage/MessageManage.vue'),
        meta: { title: '消息中心', minRoleCode: 2 }
      },
    ]
  },
  {
    path: '/:pathMatch(.*)*',
    name: 'NotFound',
    component: () => import('@/views/NotFound.vue'),
    meta: { title: '页面不存在' }
  },
]

const router = createRouter({
  history: createWebHistory('/admin/'),
  routes,
})

router.beforeEach((to, from, next) => {
  document.title = to.meta.title || '城市轨道交通管理系统'

  const userStore = useUserStore()

  if (to.meta.guest) {
    if (userStore.isLoggedIn()) {
      return next('/')
    }
    return next()
  }

  if (to.meta.requiresAuth || to.matched.some(r => r.meta.requiresAuth)) {
    if (!userStore.isLoggedIn()) {
      return next('/admin/login')
    }
  }

  // 角色权限校验
  const minRole = to.meta.minRoleCode || (to.matched.find(r => r.meta.minRoleCode)?.meta.minRoleCode)
  if (minRole && userStore.roleCode < minRole) {
    ElMessage.error('权限不足')
    return next('/')
  }

  next()
})

export default router
