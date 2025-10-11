import { createRouter, createWebHistory, type RouteRecordRaw } from 'vue-router'
import { ElMessage } from 'element-plus'

// 路由配置
const routes: Array<RouteRecordRaw> = [
  {
    path: '/login',
    name: 'Login',
    component: () => import('@/views/login/index.vue'),
    meta: { requiresAuth: false, title: '用户登录' }
  },
  {
    path: '/',
    component: () => import('@/layout/index.vue'),
    redirect: '/dashboard',
    meta: { requiresAuth: true },
    children: [
      {
        path: 'dashboard',
        name: 'Dashboard',
        component: () => import('@/views/dashboard/index.vue'),
        meta: { title: '工作台', icon: 'Dashboard' }
      },
      {
        path: 'chat',
        name: 'Chat',
        component: () => import('@/views/chat/index.vue'),
        meta: { title: 'AI智能问答', icon: 'ChatDotRound' }
      },
      {
        path: 'contract',
        name: 'Contract',
        component: () => import('@/views/contract/index.vue'),
        meta: { title: '合同审查', icon: 'Document' }
      },
      {
        path: 'history',
        name: 'History',
        component: () => import('@/views/history/index.vue'),
        meta: { title: '审查历史', icon: 'Clock' }
      },
      {
        path: 'profile',
        name: 'Profile',
        component: () => import('@/views/profile/index.vue'),
        meta: { title: '个人中心', icon: 'User' }
      }
    ]
  },
  {
    path: '/admin',
    component: () => import('@/layout/index.vue'),
    redirect: '/admin/users',
    meta: { requiresAuth: true, requiresAdmin: true },
    children: [
      {
        path: 'users',
        name: 'AdminUsers',
        component: () => import('@/views/admin/users/index.vue'),
        meta: { title: '用户管理', icon: 'UserFilled' }
      },
      {
        path: 'knowledge',
        name: 'AdminKnowledge',
        component: () => import('@/views/admin/knowledge/index.vue'),
        meta: { title: '知识库管理', icon: 'Collection' }
      },
      {
        path: 'statistics',
        name: 'AdminStatistics',
        component: () => import('@/views/admin/statistics/index.vue'),
        meta: { title: '系统统计', icon: 'DataAnalysis' }
      }
    ]
  },
  {
    path: '/:pathMatch(.*)*',
    name: 'NotFound',
    component: () => import('@/views/error/404.vue'),
    meta: { title: '页面不存在' }
  }
]

const router = createRouter({
  history: createWebHistory(),
  routes
})

// 全局前置守卫
router.beforeEach(async (to, from, next) => {
  // 设置页面标题
  if (to.meta?.title) {
    document.title = `${to.meta.title} - 法律合规智能审查助手`
  }
  
  // 如果访问登录页，检查是否已登录
  if (to.name === 'Login') {
    try {
      const { useUserStore } = await import('@/store/modules/user')
      const userStore = useUserStore()
      await userStore.initializeAuth()
      
      // 如果已经登录，重定向到首页
      if (userStore.isLoggedIn) {
        console.log('User already logged in, redirecting to dashboard')
        next('/')
        return
      }
    } catch (error) {
      console.error('Error checking login status:', error)
    }
    next()
    return
  }
  
  // 如果不需要认证，直接通过
  if (!to.meta?.requiresAuth) {
    next()
    return
  }

  try {
    // 动态导入 store 以避免初始化问题
    const { useUserStore } = await import('@/store/modules/user')
    const userStore = useUserStore()
    
    // 确保 store 已经初始化
    await userStore.initializeAuth()
    
    console.log('Router guard - to.path:', to.path, 'requiresAuth:', to.meta?.requiresAuth, 'isLoggedIn:', userStore.isLoggedIn, 'token:', !!userStore.token)
    
    // 检查是否需要认证
    if (to.meta?.requiresAuth && !userStore.isLoggedIn) {
      console.log('User not logged in, redirecting to /login')
      ElMessage.warning('请先登录')
      next({ path: '/login', query: { redirect: to.fullPath } })
      return
    }
    
    // 检查是否需要管理员权限
    if (to.meta?.requiresAdmin && !userStore.isAdmin) {
      ElMessage.error('权限不足')
      next('/')
      return
    }
    
    next()
  } catch (error) {
    console.error('Router guard error:', error)
    // 如果出错，跳转到登录页
    next({ path: '/login', query: { redirect: to.fullPath } })
  }
})

export default router
