<template>
  <el-container class="layout-container">
    <!-- 侧边栏 -->
    <el-aside :width="isCollapse ? '64px' : '220px'" class="sidebar">
      <div class="sidebar-header">
        <div v-if="!isCollapse" class="logo">
          <el-icon class="logo-icon"><Document /></el-icon>
          <span class="logo-text">法律助手</span>
        </div>
        <el-icon v-else class="logo-icon-collapsed"><Document /></el-icon>
      </div>
      
      <el-menu
        :default-active="activeMenuIndex"
        :collapse="isCollapse"
        :unique-opened="true"
        class="sidebar-menu"
        background-color="#001529"
        text-color="rgba(255, 255, 255, 0.65)"
        active-text-color="#1890ff"
        @select="handleMenuSelect"
      >
        <el-menu-item
          v-for="item in menuItems"
          :key="item.index"
          :index="item.index"
        >
          <el-icon><component :is="item.icon" /></el-icon>
          <template #title>{{ item.title }}</template>
        </el-menu-item>
        
        <!-- 管理员菜单 -->
        <el-sub-menu v-if="userStore.isAdmin" index="admin">
          <template #title>
            <el-icon><Setting /></el-icon>
            <span>系统管理</span>
          </template>
          <el-menu-item
            v-for="item in adminMenuItems"
            :key="item.index"
            :index="item.index"
          >
            <el-icon><component :is="item.icon" /></el-icon>
            <template #title>{{ item.title }}</template>
          </el-menu-item>
        </el-sub-menu>
      </el-menu>
    </el-aside>

    <el-container>
      <!-- 顶部导航 -->
      <el-header class="header">
        <div class="header-left">
          <el-button
            text
            @click="toggleCollapse"
            class="collapse-btn"
          >
            <el-icon><component :is="isCollapse ? 'Expand' : 'Fold'" /></el-icon>
          </el-button>
          
          <el-breadcrumb separator="/">
            <el-breadcrumb-item>{{ currentPageTitle }}</el-breadcrumb-item>
          </el-breadcrumb>
        </div>
        
        <div class="header-right">
          <!-- 系统健康状态 -->
          <el-tooltip content="系统状态" placement="bottom">
            <el-button
              text
              circle
              @click="checkSystemHealth"
              :loading="healthChecking"
            >
              <el-icon :color="systemHealthy ? '#67c23a' : '#f56c6c'">
                <CircleCheck v-if="systemHealthy" />
                <CircleClose v-else />
              </el-icon>
            </el-button>
          </el-tooltip>
          
          <!-- 用户菜单 -->
          <el-dropdown @command="handleUserCommand">
            <span class="user-dropdown">
              <el-avatar :size="32" :src="userStore.userInfo?.avatar || undefined">
                <el-icon><User /></el-icon>
              </el-avatar>
              <span class="username">{{ userStore.userInfo?.fullName || userStore.userInfo?.username }}</span>
              <el-icon class="arrow-down"><ArrowDown /></el-icon>
            </span>
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item command="profile">
                  <el-icon><User /></el-icon>
                  个人中心
                </el-dropdown-item>
                <el-dropdown-item command="settings">
                  <el-icon><Setting /></el-icon>
                  系统设置
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

      <!-- 主内容区域 -->
      <el-main class="main-content">
        <router-view v-slot="{ Component, route }">
          <transition name="fade" mode="out-in">
            <component :is="Component" :key="route.path" />
          </transition>
        </router-view>
      </el-main>
    </el-container>
  </el-container>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useUserStore } from '@/store/modules/user'
import { getHealthStatusApi } from '@/api/healthService'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  Document,
  Dashboard,
  ChatDotRound,
  Clock,
  User,
  Setting,
  UserFilled,
  Collection,
  DataAnalysis,
  Expand,
  Fold,
  ArrowDown,
  CircleCheck,
  CircleClose,
  SwitchButton
} from '@element-plus/icons-vue'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()

// 侧边栏折叠状态
const isCollapse = ref(false)
const systemHealthy = ref(true)
const healthChecking = ref(false)

// 菜单配置
const menuItems = [
  { index: '/dashboard', title: '工作台', icon: 'Dashboard' },
  { index: '/chat', title: 'AI智能问答', icon: 'ChatDotRound' },
  { index: '/contract', title: '合同审查', icon: 'Document' },
  { index: '/history', title: '审查历史', icon: 'Clock' },
  { index: '/profile', title: '个人中心', icon: 'User' }
]

const adminMenuItems = [
  { index: '/admin/users', title: '用户管理', icon: 'UserFilled' },
  { index: '/admin/knowledge', title: '知识库管理', icon: 'Collection' },
  { index: '/admin/statistics', title: '系统统计', icon: 'DataAnalysis' }
]

// 当前激活的菜单项
const activeMenuIndex = computed(() => {
  return route.path
})

// 当前页面标题
const currentPageTitle = computed(() => {
  return route.meta?.title as string || '法律合规智能审查助手'
})

// 切换侧边栏折叠状态
const toggleCollapse = () => {
  isCollapse.value = !isCollapse.value
}

// 处理菜单选择
const handleMenuSelect = (index: string) => {
  if (index !== route.path) {
    router.push(index)
  }
}

// 处理用户下拉菜单命令
const handleUserCommand = (command: string) => {
  switch (command) {
    case 'profile':
      router.push('/profile')
      break
    case 'settings':
      ElMessage.info('设置功能开发中...')
      break
    case 'logout':
      handleLogout()
      break
  }
}

// 处理退出登录
const handleLogout = async () => {
  try {
    await ElMessageBox.confirm('确定要退出登录吗？', '提示', {
      confirmButtonText: '确定',
      cancelButtonText: '取消',
      type: 'warning'
    })
    
    userStore.logout()
    ElMessage.success('已退出登录')
  } catch {
    // 用户取消
  }
}

// 检查系统健康状态
const checkSystemHealth = async () => {
  healthChecking.value = true
  try {
    const response = await getHealthStatusApi()
    systemHealthy.value = response.data.status === 'UP'
    ElMessage.success(`系统状态: ${systemHealthy.value ? '正常' : '异常'}`)
  } catch (error) {
    systemHealthy.value = false
    ElMessage.error('无法获取系统状态')
  } finally {
    healthChecking.value = false
  }
}

// 组件挂载时检查系统健康状态
onMounted(() => {
  checkSystemHealth()
  
  // 定期检查系统健康状态（每5分钟）
  setInterval(checkSystemHealth, 5 * 60 * 1000)
})
</script>

<style scoped>
.layout-container {
  height: 100vh;
}

.sidebar {
  background: #001529;
  overflow: hidden;
  transition: width 0.3s;
}

.sidebar-header {
  height: 64px;
  display: flex;
  align-items: center;
  justify-content: center;
  border-bottom: 1px solid #1f1f1f;
}

.logo {
  display: flex;
  align-items: center;
  color: white;
  font-size: 18px;
  font-weight: 600;
}

.logo-icon {
  font-size: 24px;
  margin-right: 8px;
  color: #1890ff;
}

.logo-icon-collapsed {
  font-size: 24px;
  color: #1890ff;
}

.logo-text {
  white-space: nowrap;
}

.sidebar-menu {
  border: none;
  height: calc(100vh - 64px);
}

.header {
  background: white;
  border-bottom: 1px solid #f0f0f0;
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 20px;
}

.header-left {
  display: flex;
  align-items: center;
}

.collapse-btn {
  margin-right: 16px;
  font-size: 18px;
}

.header-right {
  display: flex;
  align-items: center;
  gap: 16px;
}

.user-dropdown {
  display: flex;
  align-items: center;
  cursor: pointer;
  padding: 8px;
  border-radius: 4px;
  transition: background-color 0.3s;
}

.user-dropdown:hover {
  background-color: #f5f5f5;
}

.username {
  margin: 0 8px;
  font-size: 14px;
}

.arrow-down {
  font-size: 12px;
}

.main-content {
  background: #f5f5f5;
  padding: 20px;
  overflow: auto;
}

.fade-enter-active,
.fade-leave-active {
  transition: opacity 0.3s ease;
}

.fade-enter-from,
.fade-leave-to {
  opacity: 0;
}
</style>
