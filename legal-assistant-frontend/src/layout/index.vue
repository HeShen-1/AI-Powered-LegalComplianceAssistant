<template>
  <el-container class="layout-container">
    <!-- 侧边栏 -->
    <el-aside :width="isCollapse ? '64px' : '200px'" class="sidebar">
      <div class="logo-container">
        <div class="logo">
          <el-icon size="24" color="#409EFF">
            <Document />
          </el-icon>
          <span v-show="!isCollapse" class="logo-text">法律助手</span>
        </div>
      </div>
      
      <el-menu
        :default-active="activeMenu"
        :collapse="isCollapse"
        :unique-opened="true"
        class="sidebar-menu"
        router
      >
        <!-- 普通用户菜单 -->
        <el-menu-item index="/dashboard">
          <el-icon><House /></el-icon>
          <template #title>工作台</template>
        </el-menu-item>
        
        <el-menu-item index="/chat">
          <el-icon><ChatDotRound /></el-icon>
          <template #title>AI智能问答</template>
        </el-menu-item>
        
        <el-menu-item index="/contract">
          <el-icon><Document /></el-icon>
          <template #title>合同审查</template>
        </el-menu-item>
        
        <el-menu-item index="/history">
          <el-icon><Clock /></el-icon>
          <template #title>审查历史</template>
        </el-menu-item>
        
        <!-- 管理员菜单 -->
        <el-sub-menu v-if="userStore.isAdmin" index="admin">
          <template #title>
            <el-icon><Setting /></el-icon>
            <span>系统管理</span>
          </template>
          
          <el-menu-item index="/admin/users">
            <el-icon><User /></el-icon>
            <template #title>用户管理</template>
          </el-menu-item>
          
          <el-menu-item index="/admin/knowledge">
            <el-icon><FolderOpened /></el-icon>
            <template #title>知识库管理</template>
          </el-menu-item>
          
          <el-menu-item index="/admin/statistics">
            <el-icon><DataAnalysis /></el-icon>
            <template #title>统计仪表盘</template>
          </el-menu-item>
        </el-sub-menu>
      </el-menu>
    </el-aside>
    
    <!-- 主内容区 -->
    <el-container>
      <!-- 顶部导航栏 -->
      <el-header class="header">
        <div class="header-left">
          <el-button
            type="text"
            size="large"
            @click="toggleSidebar"
          >
            <el-icon>
              <Expand v-if="isCollapse" />
              <Fold v-else />
            </el-icon>
          </el-button>
          
          <el-breadcrumb separator="/" class="breadcrumb">
            <el-breadcrumb-item
              v-for="item in breadcrumbList"
              :key="item.path"
              :to="item.path"
            >
              {{ item.title }}
            </el-breadcrumb-item>
          </el-breadcrumb>
        </div>
        
        <div class="header-right">
          <!-- 用户信息下拉菜单 -->
          <el-dropdown @command="handleCommand">
            <div class="user-info">
              <el-avatar :size="32" :src="userAvatar">
                <el-icon><User /></el-icon>
              </el-avatar>
              <span class="username">{{ userStore.userInfo?.fullName || userStore.userInfo?.username }}</span>
              <el-icon class="dropdown-icon"><ArrowDown /></el-icon>
            </div>
            
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item command="profile">
                  <el-icon><User /></el-icon>
                  个人资料
                </el-dropdown-item>
                <el-dropdown-item divided command="logout">
                  <el-icon><SwitchButton /></el-icon>
                  退出登录
                </el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
        </div>
      </el-header>
      
      <!-- 主内容 -->
      <el-main class="main-content">
        <router-view v-slot="{ Component, route }">
          <transition name="fade-transform" mode="out-in">
            <keep-alive>
              <component :is="Component" :key="route.path" />
            </keep-alive>
          </transition>
        </router-view>
      </el-main>
    </el-container>
  </el-container>
</template>

<script setup lang="ts">
import { ref, computed, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useUserStore } from '@/store/modules/user'
import { useAppStore } from '@/store/modules/app'
import {
  Document,
  House,
  ChatDotRound,
  Clock,
  Setting,
  User,
  FolderOpened,
  DataAnalysis,
  Expand,
  Fold,
  ArrowDown,
  SwitchButton
} from '@element-plus/icons-vue'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()
const appStore = useAppStore()

// 侧边栏折叠状态
const isCollapse = computed(() => appStore.sidebarCollapsed)

// 用户头像
const userAvatar = ref('')

// 当前激活的菜单项
const activeMenu = computed(() => route.path)

// 面包屑导航
const breadcrumbList = computed(() => {
  const matched = route.matched.filter(item => item.meta && item.meta.title)
  const breadcrumbs = matched.map(item => ({
    path: item.path,
    title: item.meta?.title || ''
  }))
  
  // 根据当前路由生成面包屑
  const pathMap: Record<string, string> = {
    '/dashboard': '工作台',
    '/chat': 'AI智能问答',
    '/contract': '合同审查',
    '/history': '审查历史',
    '/admin/users': '用户管理',
    '/admin/knowledge': '知识库管理',
    '/admin/statistics': '统计仪表盘',
    '/profile': '个人资料'
  }
  
  const currentPath = route.path
  const title = pathMap[currentPath] || '未知页面'
  
  return [
    { path: '/dashboard', title: '首页' },
    ...(currentPath !== '/dashboard' ? [{ path: currentPath, title }] : [])
  ]
})

// 切换侧边栏
const toggleSidebar = () => {
  appStore.toggleSidebar()
}

// 处理用户菜单命令
const handleCommand = async (command: string) => {
  switch (command) {
    case 'profile':
      await router.push('/profile')
      break
    case 'logout':
      try {
        await ElMessageBox.confirm('确定要退出登录吗？', '提示', {
          confirmButtonText: '确定',
          cancelButtonText: '取消',
          type: 'warning'
        })
        
        await userStore.logout()
        ElMessage.success('已退出登录')
        await router.push('/login')
      } catch {
        // 用户取消操作
      }
      break
  }
}

// 监听路由变化，在移动端自动收起侧边栏
watch(
  () => route.path,
  () => {
    if (window.innerWidth <= 768) {
      appStore.setSidebarCollapsed(true)
    }
  }
)

// 响应式处理
const handleResize = () => {
  if (window.innerWidth <= 768) {
    appStore.setSidebarCollapsed(true)
  } else {
    appStore.setSidebarCollapsed(false)
  }
}

// 监听窗口大小变化
window.addEventListener('resize', handleResize)
handleResize() // 初始化时执行一次
</script>

<style scoped>
.layout-container {
  height: 100vh;
}

.sidebar {
  background-color: #304156;
  transition: width 0.3s;
  overflow: hidden;
}

.logo-container {
  height: 60px;
  display: flex;
  align-items: center;
  justify-content: center;
  background-color: #2b3a4b;
}

.logo {
  display: flex;
  align-items: center;
  color: white;
  font-weight: 600;
  font-size: 16px;
}

.logo-text {
  margin-left: 8px;
  transition: opacity 0.3s;
}

.sidebar-menu {
  border: none;
  background-color: #304156;
}

.sidebar-menu :deep(.el-menu-item) {
  color: #bfcbd9;
  border-bottom: none;
}

.sidebar-menu :deep(.el-menu-item:hover) {
  background-color: #263445;
  color: #409EFF;
}

.sidebar-menu :deep(.el-menu-item.is-active) {
  background-color: #409EFF;
  color: white;
}

.sidebar-menu :deep(.el-sub-menu__title) {
  color: #bfcbd9;
  border-bottom: none;
}

.sidebar-menu :deep(.el-sub-menu__title:hover) {
  background-color: #263445;
  color: #409EFF;
}

.header {
  background-color: white;
  border-bottom: 1px solid #e4e7ed;
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 20px;
}

.header-left {
  display: flex;
  align-items: center;
}

.breadcrumb {
  margin-left: 20px;
}

.header-right {
  display: flex;
  align-items: center;
}

.user-info {
  display: flex;
  align-items: center;
  cursor: pointer;
  padding: 8px 12px;
  border-radius: 4px;
  transition: background-color 0.3s;
}

.user-info:hover {
  background-color: #f5f7fa;
}

.username {
  margin: 0 8px;
  font-size: 14px;
  color: #606266;
}

.dropdown-icon {
  font-size: 12px;
  color: #909399;
}

.main-content {
  background-color: #f0f2f5;
  padding: 20px;
  overflow-y: auto;
}

/* 页面切换动画 */
.fade-transform-enter-active,
.fade-transform-leave-active {
  transition: all 0.3s;
}

.fade-transform-enter-from {
  opacity: 0;
  transform: translateX(30px);
}

.fade-transform-leave-to {
  opacity: 0;
  transform: translateX(-30px);
}

/* 响应式设计 */
@media (max-width: 768px) {
  .header {
    padding: 0 15px;
  }
  
  .breadcrumb {
    margin-left: 10px;
  }
  
  .username {
    display: none;
  }
  
  .main-content {
    padding: 15px;
  }
}

@media (max-width: 480px) {
  .main-content {
    padding: 10px;
  }
}
</style>