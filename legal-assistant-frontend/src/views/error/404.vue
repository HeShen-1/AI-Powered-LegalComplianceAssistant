<template>
  <div class="error-container">
    <div class="error-content">
      <div class="error-illustration">
        <div class="error-number">404</div>
        <div class="error-icon">
          <el-icon size="120" color="#E6A23C">
            <Warning />
          </el-icon>
        </div>
      </div>
      
      <div class="error-info">
        <h1 class="error-title">页面不存在</h1>
        <p class="error-description">
          抱歉，您访问的页面不存在或已被移除。
        </p>
        <p class="error-suggestion">
          请检查URL是否正确，或者返回首页继续浏览。
        </p>
        
        <div class="error-actions">
          <el-button type="primary" size="large" @click="goHome">
            <el-icon><House /></el-icon>
            返回首页
          </el-button>
          <el-button size="large" @click="goBack">
            <el-icon><Back /></el-icon>
            返回上页
          </el-button>
        </div>
      </div>
    </div>
    
    <!-- 快捷导航 -->
    <div class="quick-nav">
      <h3>您可能想要访问：</h3>
      <div class="nav-links">
        <el-card
          v-for="link in quickLinks"
          :key="link.path"
          class="nav-card"
          shadow="hover"
          @click="navigateTo(link.path)"
        >
          <div class="nav-content">
            <el-icon :size="32" :color="link.color">
              <component :is="link.icon" />
            </el-icon>
            <div class="nav-info">
              <div class="nav-title">{{ link.title }}</div>
              <div class="nav-desc">{{ link.description }}</div>
            </div>
          </div>
        </el-card>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import {
  Warning,
  House,
  Back,
  Document,
  ChatDotRound,
  Clock,
  User
} from '@element-plus/icons-vue'

const router = useRouter()

// 快捷导航链接
const quickLinks = [
  {
    path: '/dashboard',
    title: '工作台',
    description: '查看系统概览和统计',
    icon: House,
    color: '#409EFF'
  },
  {
    path: '/contract',
    title: '合同审查',
    description: '上传合同进行AI审查',
    icon: Document,
    color: '#67C23A'
  },
  {
    path: '/chat',
    title: 'AI问答',
    description: '智能法律咨询服务',
    icon: ChatDotRound,
    color: '#E6A23C'
  },
  {
    path: '/history',
    title: '审查历史',
    description: '查看历史审查记录',
    icon: Clock,
    color: '#F56C6C'
  },
  {
    path: '/profile',
    title: '个人中心',
    description: '管理个人信息设置',
    icon: User,
    color: '#909399'
  }
]

// 事件处理
const goHome = () => {
  router.push('/')
  ElMessage.success('已返回首页')
}

const goBack = () => {
  if (window.history.length > 1) {
    router.go(-1)
  } else {
    router.push('/')
  }
}

const navigateTo = (path: string) => {
  router.push(path)
}
</script>

<style scoped>
.error-container {
  min-height: 100vh;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 20px;
  background: linear-gradient(135deg, #f5f7fa 0%, #c3cfe2 100%);
}

.error-content {
  text-align: center;
  margin-bottom: 60px;
}

.error-illustration {
  position: relative;
  margin-bottom: 40px;
}

.error-number {
  font-size: 180px;
  font-weight: bold;
  color: #E6A23C;
  opacity: 0.3;
  line-height: 1;
  margin-bottom: -60px;
}

.error-icon {
  position: relative;
  z-index: 1;
}

.error-info {
  max-width: 500px;
  margin: 0 auto;
}

.error-title {
  font-size: 32px;
  font-weight: 600;
  color: #2c3e50;
  margin: 0 0 16px 0;
}

.error-description {
  font-size: 18px;
  color: #5a6c7d;
  margin: 0 0 12px 0;
  line-height: 1.6;
}

.error-suggestion {
  font-size: 16px;
  color: #7f8c8d;
  margin: 0 0 40px 0;
  line-height: 1.6;
}

.error-actions {
  display: flex;
  gap: 16px;
  justify-content: center;
}

.quick-nav {
  max-width: 800px;
  width: 100%;
}

.quick-nav h3 {
  text-align: center;
  font-size: 20px;
  color: #2c3e50;
  margin: 0 0 30px 0;
}

.nav-links {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(240px, 1fr));
  gap: 20px;
}

.nav-card {
  cursor: pointer;
  transition: all 0.3s ease;
  border-radius: 12px;
}

.nav-card:hover {
  transform: translateY(-4px);
  box-shadow: 0 8px 25px rgba(0, 0, 0, 0.15);
}

.nav-content {
  display: flex;
  align-items: center;
  gap: 16px;
  padding: 8px;
}

.nav-info {
  flex: 1;
  text-align: left;
}

.nav-title {
  font-size: 16px;
  font-weight: 600;
  color: #2c3e50;
  margin-bottom: 4px;
}

.nav-desc {
  font-size: 13px;
  color: #7f8c8d;
  line-height: 1.4;
}

/* 动画效果 */
.error-illustration {
  animation: float 3s ease-in-out infinite;
}

@keyframes float {
  0%, 100% {
    transform: translateY(0);
  }
  50% {
    transform: translateY(-10px);
  }
}

.error-number {
  animation: pulse 2s ease-in-out infinite;
}

@keyframes pulse {
  0%, 100% {
    opacity: 0.3;
  }
  50% {
    opacity: 0.5;
  }
}

/* 响应式设计 */
@media (max-width: 768px) {
  .error-container {
    padding: 15px;
  }
  
  .error-number {
    font-size: 120px;
    margin-bottom: -40px;
  }
  
  .error-icon :deep(.el-icon) {
    font-size: 80px !important;
  }
  
  .error-title {
    font-size: 24px;
  }
  
  .error-description {
    font-size: 16px;
  }
  
  .error-suggestion {
    font-size: 14px;
  }
  
  .error-actions {
    flex-direction: column;
    align-items: center;
  }
  
  .error-actions .el-button {
    width: 200px;
  }
  
  .nav-links {
    grid-template-columns: 1fr;
    gap: 15px;
  }
  
  .nav-content {
    gap: 12px;
    padding: 4px;
  }
  
  .nav-content :deep(.el-icon) {
    font-size: 24px !important;
  }
}

@media (max-width: 480px) {
  .error-number {
    font-size: 80px;
    margin-bottom: -30px;
  }
  
  .error-icon :deep(.el-icon) {
    font-size: 60px !important;
  }
  
  .error-title {
    font-size: 20px;
  }
  
  .error-description {
    font-size: 14px;
  }
  
  .error-suggestion {
    font-size: 13px;
  }
  
  .quick-nav h3 {
    font-size: 18px;
  }
  
  .nav-title {
    font-size: 14px;
  }
  
  .nav-desc {
    font-size: 12px;
  }
}
</style>