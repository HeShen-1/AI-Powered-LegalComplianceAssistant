<template>
  <div class="profile-container">
    <el-row :gutter="20">
      <!-- 个人信息卡片 -->
      <el-col :xs="24" :lg="8">
        <el-card class="profile-card" shadow="never">
          <div class="profile-header">
            <el-avatar :size="80" :src="userAvatar">
              <el-icon size="40"><User /></el-icon>
            </el-avatar>
            <div class="profile-info">
              <h3>{{ userStore.userInfo?.fullName || userStore.userInfo?.username }}</h3>
              <p class="profile-role">
                <el-tag :type="userStore.isAdmin ? 'danger' : 'primary'" size="small">
                  {{ userStore.isAdmin ? '管理员' : '普通用户' }}
                </el-tag>
              </p>
              <p class="profile-email">{{ userStore.userInfo?.email }}</p>
            </div>
          </div>
          
          <div class="profile-stats">
            <div class="stat-item">
              <div class="stat-number">{{ userStats.totalReviews }}</div>
              <div class="stat-label">总审查数</div>
            </div>
            <div class="stat-item">
              <div class="stat-number">{{ userStats.completedReviews }}</div>
              <div class="stat-label">已完成</div>
            </div>
            <div class="stat-item">
              <div class="stat-number">{{ userStats.joinDays }}</div>
              <div class="stat-label">加入天数</div>
            </div>
          </div>
        </el-card>
      </el-col>
      
      <!-- 详细信息和设置 -->
      <el-col :xs="24" :lg="16">
        <el-tabs v-model="activeTab" class="profile-tabs">
          <!-- 基本信息 -->
          <el-tab-pane label="基本信息" name="basic">
            <el-card shadow="never">
              <el-descriptions :column="2" border>
                <el-descriptions-item label="用户名">
                  {{ userStore.userInfo?.username }}
                </el-descriptions-item>
                <el-descriptions-item label="邮箱">
                  {{ userStore.userInfo?.email }}
                </el-descriptions-item>
                <el-descriptions-item label="姓名">
                  {{ userStore.userInfo?.fullName }}
                </el-descriptions-item>
                <el-descriptions-item label="角色">
                  <el-tag :type="userStore.isAdmin ? 'danger' : 'primary'" size="small">
                    {{ userStore.isAdmin ? '管理员' : '普通用户' }}
                  </el-tag>
                </el-descriptions-item>
                <el-descriptions-item label="账户状态">
                  <el-tag :type="userStore.userInfo?.enabled ? 'success' : 'danger'" size="small">
                    {{ userStore.userInfo?.enabled ? '正常' : '禁用' }}
                  </el-tag>
                </el-descriptions-item>
                <el-descriptions-item label="注册时间">
                  {{ formatDateTime(userStore.userInfo?.createdAt) }}
                </el-descriptions-item>
              </el-descriptions>
            </el-card>
          </el-tab-pane>
          
          <!-- 使用统计 -->
          <el-tab-pane label="使用统计" name="stats">
            <el-card shadow="never">
              <div class="stats-section">
                <h4>📊 使用情况统计</h4>
                <el-row :gutter="20">
                  <el-col :xs="24" :sm="12">
                    <div class="stat-card">
                      <div class="stat-header">
                        <el-icon size="24" color="#409EFF"><Document /></el-icon>
                        <span>合同审查</span>
                      </div>
                      <div class="stat-content">
                        <div class="stat-row">
                          <span>总审查数：</span>
                          <strong>{{ userStats.totalReviews }}</strong>
                        </div>
                        <div class="stat-row">
                          <span>已完成：</span>
                          <strong>{{ userStats.completedReviews }}</strong>
                        </div>
                        <div class="stat-row">
                          <span>处理中：</span>
                          <strong>{{ userStats.processingReviews }}</strong>
                        </div>
                        <div class="stat-row">
                          <span>高风险发现：</span>
                          <strong>{{ userStats.highRiskCount }}</strong>
                        </div>
                      </div>
                    </div>
                  </el-col>
                  <el-col :xs="24" :sm="12">
                    <div class="stat-card">
                      <div class="stat-header">
                        <el-icon size="24" color="#67C23A"><ChatDotRound /></el-icon>
                        <span>AI问答</span>
                      </div>
                      <div class="stat-content">
                        <div class="stat-row">
                          <span>总提问数：</span>
                          <strong>{{ userStats.totalQuestions }}</strong>
                        </div>
                        <div class="stat-row">
                          <span>本月提问：</span>
                          <strong>{{ userStats.monthlyQuestions }}</strong>
                        </div>
                        <div class="stat-row">
                          <span>平均响应时间：</span>
                          <strong>{{ userStats.avgResponseTime }}s</strong>
                        </div>
                        <div class="stat-row">
                          <span>满意度：</span>
                          <strong>{{ userStats.satisfaction }}%</strong>
                        </div>
                      </div>
                    </div>
                  </el-col>
                </el-row>
              </div>
            </el-card>
          </el-tab-pane>
          
          <!-- 系统设置 -->
          <el-tab-pane label="系统设置" name="settings">
            <el-card shadow="never">
              <div class="settings-section">
                <h4>⚙️ 偏好设置</h4>
                <el-empty description="暂无可配置项" />
              </div>
            </el-card>
          </el-tab-pane>
        </el-tabs>
      </el-col>
    </el-row>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import {
  User,
  Document,
  ChatDotRound
} from '@element-plus/icons-vue'
import { useUserStore } from '@/store/modules/user'
import { getUserStatsApi } from '@/api/userService'

// 类型定义
interface UserStats {
  totalReviews: number
  completedReviews: number
  processingReviews: number
  highRiskCount: number
  totalQuestions: number
  monthlyQuestions: number
  avgResponseTime: number
  satisfaction: number
  joinDays: number
}

const userStore = useUserStore()

// 响应式数据
const activeTab = ref('basic')
const userAvatar = ref('')

const userStats = ref<UserStats>({
  totalReviews: 0,
  completedReviews: 0,
  processingReviews: 0,
  highRiskCount: 0,
  totalQuestions: 0,
  monthlyQuestions: 0,
  avgResponseTime: 0,
  satisfaction: 0,
  joinDays: 0
})

// 工具函数
const formatDateTime = (dateStr?: string) => {
  if (!dateStr) return '-'
  return new Date(dateStr).toLocaleString('zh-CN', {
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit'
  })
}

const calculateJoinDays = (createdAt?: string) => {
  if (!createdAt) return 0
  const joinDate = new Date(createdAt)
  const now = new Date()
  const diffTime = Math.abs(now.getTime() - joinDate.getTime())
  return Math.ceil(diffTime / (1000 * 60 * 60 * 24))
}

// 数据获取
const fetchUserStats = async () => {
  try {
    if (!userStore.userInfo?.id) {
      console.error('User ID not available')
      return
    }
    
    // 调用真实API获取统计数据
    const response = await getUserStatsApi(userStore.userInfo.id)
    
    // 后端直接返回UserStatsDto对象，不包装在ApiResponse中
    if (response.data) {
      userStats.value = {
        totalReviews: response.data.totalReviews || 0,
        completedReviews: response.data.completedReviews || 0,
        processingReviews: response.data.processingReviews || 0,
        highRiskCount: response.data.highRiskCount || 0,
        totalQuestions: response.data.totalQuestions || 0,
        monthlyQuestions: response.data.monthlyQuestions || 0,
        avgResponseTime: response.data.avgResponseTime || 0,
        satisfaction: response.data.satisfaction || 0,
        joinDays: response.data.joinDays || 0
      }
    } else {
      // 使用默认值
      userStats.value = {
        totalReviews: 0,
        completedReviews: 0,
        processingReviews: 0,
        highRiskCount: 0,
        totalQuestions: 0,
        monthlyQuestions: 0,
        avgResponseTime: 0,
        satisfaction: 0,
        joinDays: calculateJoinDays(userStore.userInfo?.createdAt)
      }
    }
  } catch (error) {
    console.error('Failed to fetch user stats:', error)
    // 使用默认值
    userStats.value = {
      totalReviews: 0,
      completedReviews: 0,
      processingReviews: 0,
      highRiskCount: 0,
      totalQuestions: 0,
      monthlyQuestions: 0,
      avgResponseTime: 0,
      satisfaction: 0,
      joinDays: calculateJoinDays(userStore.userInfo?.createdAt)
    }
  }
}

// 组件挂载时初始化
onMounted(() => {
  fetchUserStats()
})
</script>

<style scoped>
.profile-container {
  max-width: 1200px;
  margin: 0 auto;
}

.profile-card {
  text-align: center;
}

.profile-header {
  margin-bottom: 30px;
}

.profile-info {
  margin-top: 20px;
}

.profile-info h3 {
  margin: 0 0 8px 0;
  font-size: 20px;
  color: #2c3e50;
}

.profile-role {
  margin: 8px 0;
}

.profile-email {
  margin: 8px 0;
  color: #7f8c8d;
  font-size: 14px;
}

.profile-stats {
  display: flex;
  justify-content: space-around;
  margin: 30px 0;
  padding: 20px 0;
  border-top: 1px solid #f0f0f0;
  border-bottom: 1px solid #f0f0f0;
}

.stat-item {
  text-align: center;
}

.stat-number {
  font-size: 24px;
  font-weight: bold;
  color: #409EFF;
  margin-bottom: 4px;
}

.stat-label {
  color: #7f8c8d;
  font-size: 12px;
}

.profile-tabs {
  margin-top: 0;
}

.stats-section h4 {
  margin: 0 0 20px 0;
  color: #2c3e50;
}

.stat-card {
  border: 1px solid #e4e7ed;
  border-radius: 8px;
  padding: 20px;
  height: 100%;
}

.stat-header {
  display: flex;
  align-items: center;
  margin-bottom: 16px;
  font-weight: 600;
  color: #2c3e50;
}

.stat-header span {
  margin-left: 8px;
}

.stat-content {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.stat-row {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 4px 0;
}

.settings-section h4 {
  margin: 0 0 20px 0;
  color: #2c3e50;
}

/* 响应式设计 */
@media (max-width: 768px) {
  .profile-stats {
    flex-direction: column;
    gap: 20px;
  }
  
  .stat-card {
    margin-bottom: 20px;
  }
  
  .stat-row {
    font-size: 14px;
  }
}

@media (max-width: 480px) {
  .profile-info h3 {
    font-size: 18px;
  }
  
  .stat-number {
    font-size: 20px;
  }
  
  .stat-card {
    padding: 15px;
  }
  
  .profile-tabs :deep(.el-tabs__nav-wrap) {
    padding: 0 10px;
  }
}
</style>
