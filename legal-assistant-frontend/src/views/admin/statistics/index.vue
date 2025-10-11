<template>
  <div class="statistics-container">
    <!-- 概览统计卡片 -->
    <el-row :gutter="20" class="overview-cards">
      <el-col :xs="24" :sm="12" :lg="6">
        <el-card class="stat-card" shadow="hover">
          <div class="stat-content">
            <div class="stat-icon users">
              <el-icon size="32"><User /></el-icon>
            </div>
            <div class="stat-info">
              <div class="stat-number">{{ overview.totalUsers }}</div>
              <div class="stat-label">总用户数</div>
              <div class="stat-change positive">+{{ overview.newUsersToday }} 今日新增</div>
            </div>
          </div>
        </el-card>
      </el-col>
      
      <el-col :xs="24" :sm="12" :lg="6">
        <el-card class="stat-card" shadow="hover">
          <div class="stat-content">
            <div class="stat-icon reviews">
              <el-icon size="32"><Document /></el-icon>
            </div>
            <div class="stat-info">
              <div class="stat-number">{{ overview.totalReviews }}</div>
              <div class="stat-label">总审查数</div>
              <div class="stat-change positive">+{{ overview.reviewsToday }} 今日审查</div>
            </div>
          </div>
        </el-card>
      </el-col>
      
      <el-col :xs="24" :sm="12" :lg="6">
        <el-card class="stat-card" shadow="hover">
          <div class="stat-content">
            <div class="stat-icon questions">
              <el-icon size="32"><ChatDotRound /></el-icon>
            </div>
            <div class="stat-info">
              <div class="stat-number">{{ overview.totalQuestions }}</div>
              <div class="stat-label">AI问答数</div>
              <div class="stat-change positive">+{{ overview.questionsToday }} 今日问答</div>
            </div>
          </div>
        </el-card>
      </el-col>
      
      <el-col :xs="24" :sm="12" :lg="6">
        <el-card class="stat-card" shadow="hover">
          <div class="stat-content">
            <div class="stat-icon documents">
              <el-icon size="32"><FolderOpened /></el-icon>
            </div>
            <div class="stat-info">
              <div class="stat-number">{{ overview.totalDocuments }}</div>
              <div class="stat-label">知识库文档</div>
              <div class="stat-change positive">+{{ overview.documentsToday }} 今日新增</div>
            </div>
          </div>
        </el-card>
      </el-col>
    </el-row>
    
    <!-- 图表区域 -->
    <el-row :gutter="20" class="charts-section">
      <!-- 用户增长趋势 -->
      <el-col :xs="24" :lg="12">
        <el-card class="chart-card" shadow="never">
          <template #header>
            <div class="chart-header">
              <h4>📈 用户增长趋势</h4>
              <el-radio-group v-model="userChartPeriod" size="small" @change="updateUserChart">
                <el-radio-button label="7d">7天</el-radio-button>
                <el-radio-button label="30d">30天</el-radio-button>
                <el-radio-button label="90d">90天</el-radio-button>
              </el-radio-group>
            </div>
          </template>
          <div ref="userChartRef" class="chart-container"></div>
        </el-card>
      </el-col>
      
      <!-- 审查统计 -->
      <el-col :xs="24" :lg="12">
        <el-card class="chart-card" shadow="never">
          <template #header>
            <div class="chart-header">
              <h4>📊 审查统计分布</h4>
              <el-radio-group v-model="reviewChartType" size="small" @change="updateReviewChart">
                <el-radio-button label="status">状态分布</el-radio-button>
                <el-radio-button label="risk">风险分布</el-radio-button>
              </el-radio-group>
            </div>
          </template>
          <div ref="reviewChartRef" class="chart-container"></div>
        </el-card>
      </el-col>
      
      <!-- 系统使用情况 -->
      <el-col :xs="24" :lg="12">
        <el-card class="chart-card" shadow="never">
          <template #header>
            <h4>⚡ 系统使用情况</h4>
          </template>
          <div ref="usageChartRef" class="chart-container"></div>
        </el-card>
      </el-col>
      
      <!-- 热门功能 -->
      <el-col :xs="24" :lg="12">
        <el-card class="chart-card" shadow="never">
          <template #header>
            <h4>🔥 热门功能使用</h4>
          </template>
          <div ref="featureChartRef" class="chart-container"></div>
        </el-card>
      </el-col>
    </el-row>
    
    <!-- 详细数据表格 -->
    <el-row :gutter="20" class="tables-section">
      <!-- 活跃用户 -->
      <el-col :xs="24" :lg="12">
        <el-card class="table-card" shadow="never">
          <template #header>
            <div class="table-header">
              <h4>👑 活跃用户排行</h4>
              <el-button type="text" size="small" @click="exportActiveUsers">
                导出数据
              </el-button>
            </div>
          </template>
          <el-table :data="activeUsers" stripe max-height="400">
            <el-table-column prop="rank" label="排名" width="60" align="center" />
            <el-table-column prop="username" label="用户名" min-width="100">
              <template #default="{ row }">
                <div class="user-cell">
                  <el-avatar :size="24">
                    <el-icon><User /></el-icon>
                  </el-avatar>
                  <span class="username">{{ row.username }}</span>
                </div>
              </template>
            </el-table-column>
            <el-table-column prop="reviewCount" label="审查数" width="80" align="center" />
            <el-table-column prop="questionCount" label="问答数" width="80" align="center" />
            <el-table-column prop="lastActive" label="最后活跃" width="100">
              <template #default="{ row }">
                <span class="last-active">{{ row.lastActive }}</span>
              </template>
            </el-table-column>
          </el-table>
        </el-card>
      </el-col>
      
      <!-- 系统监控 -->
      <el-col :xs="24" :lg="12">
        <el-card class="table-card" shadow="never">
          <template #header>
            <div class="table-header">
              <h4>🖥️ 系统监控</h4>
              <el-button type="text" size="small" @click="refreshSystemStatus">
                刷新状态
              </el-button>
            </div>
          </template>
          <div class="system-status">
            <div class="status-item">
              <div class="status-label">CPU使用率</div>
              <div class="status-value">
                <el-progress :percentage="systemStatus.cpu" :color="getProgressColor(systemStatus.cpu)" />
                <span class="percentage">{{ systemStatus.cpu }}%</span>
              </div>
            </div>
            
            <div class="status-item">
              <div class="status-label">内存使用率</div>
              <div class="status-value">
                <el-progress :percentage="systemStatus.memory" :color="getProgressColor(systemStatus.memory)" />
                <span class="percentage">{{ systemStatus.memory }}%</span>
              </div>
            </div>
            
            <div class="status-item">
              <div class="status-label">磁盘使用率</div>
              <div class="status-value">
                <el-progress :percentage="systemStatus.disk" :color="getProgressColor(systemStatus.disk)" />
                <span class="percentage">{{ systemStatus.disk }}%</span>
              </div>
            </div>
            
            <div class="status-item">
              <div class="status-label">数据库连接</div>
              <div class="status-value">
                <el-tag :type="systemStatus.database ? 'success' : 'danger'" size="small">
                  {{ systemStatus.database ? '正常' : '异常' }}
                </el-tag>
                <span class="connection-count">{{ systemStatus.dbConnections }}/100</span>
              </div>
            </div>
            
            <div class="status-item">
              <div class="status-label">AI服务状态</div>
              <div class="status-value">
                <el-tag :type="systemStatus.aiService ? 'success' : 'danger'" size="small">
                  {{ systemStatus.aiService ? '正常' : '异常' }}
                </el-tag>
                <span class="response-time">{{ systemStatus.aiResponseTime }}ms</span>
              </div>
            </div>
            
            <div class="status-item">
              <div class="status-label">系统运行时间</div>
              <div class="status-value">
                <span class="uptime">{{ systemStatus.uptime }}</span>
              </div>
            </div>
          </div>
        </el-card>
      </el-col>
    </el-row>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, onUnmounted, nextTick } from 'vue'
import { ElMessage } from 'element-plus'
import {
  User,
  Document,
  ChatDotRound,
  FolderOpened
} from '@element-plus/icons-vue'
import * as echarts from 'echarts'

// 类型定义
interface Overview {
  totalUsers: number
  newUsersToday: number
  totalReviews: number
  reviewsToday: number
  totalQuestions: number
  questionsToday: number
  totalDocuments: number
  documentsToday: number
}

interface ActiveUser {
  rank: number
  username: string
  reviewCount: number
  questionCount: number
  lastActive: string
}

interface SystemStatus {
  cpu: number
  memory: number
  disk: number
  database: boolean
  dbConnections: number
  aiService: boolean
  aiResponseTime: number
  uptime: string
}

// 响应式数据
const overview = ref<Overview>({
  totalUsers: 0,
  newUsersToday: 0,
  totalReviews: 0,
  reviewsToday: 0,
  totalQuestions: 0,
  questionsToday: 0,
  totalDocuments: 0,
  documentsToday: 0
})

const activeUsers = ref<ActiveUser[]>([])
const systemStatus = ref<SystemStatus>({
  cpu: 0,
  memory: 0,
  disk: 0,
  database: true,
  dbConnections: 0,
  aiService: true,
  aiResponseTime: 0,
  uptime: ''
})

const userChartPeriod = ref('30d')
const reviewChartType = ref('status')

// 图表引用
const userChartRef = ref<HTMLElement>()
const reviewChartRef = ref<HTMLElement>()
const usageChartRef = ref<HTMLElement>()
const featureChartRef = ref<HTMLElement>()

// 图表实例
let userChart: echarts.ECharts | null = null
let reviewChart: echarts.ECharts | null = null
let usageChart: echarts.ECharts | null = null
let featureChart: echarts.ECharts | null = null

// 定时器
let statusTimer: NodeJS.Timeout | null = null

// 工具函数
const getProgressColor = (percentage: number) => {
  if (percentage < 50) return '#67C23A'
  if (percentage < 80) return '#E6A23C'
  return '#F56C6C'
}

// 数据获取
const fetchOverviewData = async () => {
  try {
    // TODO: 调用真实API获取统计数据
    // 目前暂无统计API，保持为0
    overview.value = {
      totalUsers: 0,
      newUsersToday: 0,
      totalReviews: 0,
      reviewsToday: 0,
      totalQuestions: 0,
      questionsToday: 0,
      totalDocuments: 0,
      documentsToday: 0
    }
  } catch (error) {
    console.error('Failed to fetch overview data:', error)
  }
}

const fetchActiveUsers = async () => {
  try {
    // TODO: 调用真实API获取活跃用户数据
    // 目前暂无相关API，返回空数组
    activeUsers.value = []
  } catch (error) {
    console.error('Failed to fetch active users:', error)
  }
}

const fetchSystemStatus = async () => {
  try {
    // TODO: 调用真实API获取系统状态
    // 目前暂无系统监控API，返回默认值
    systemStatus.value = {
      cpu: 0,
      memory: 0,
      disk: 0,
      database: true,
      dbConnections: 0,
      aiService: true,
      aiResponseTime: 0,
      uptime: '未知'
    }
  } catch (error) {
    console.error('Failed to fetch system status:', error)
  }
}

// 图表初始化
const initUserChart = () => {
  if (!userChartRef.value) return
  
  userChart = echarts.init(userChartRef.value)
  updateUserChart()
}

const updateUserChart = () => {
  if (!userChart) return
  
  // TODO: 从真实API获取用户增长数据
  const dates = []
  const users = []
  const days = userChartPeriod.value === '7d' ? 7 : userChartPeriod.value === '30d' ? 30 : 90
  
  for (let i = days - 1; i >= 0; i--) {
    const date = new Date()
    date.setDate(date.getDate() - i)
    dates.push(date.toLocaleDateString('zh-CN', { month: '2-digit', day: '2-digit' }))
    users.push(0) // 暂无真实数据
  }
  
  const option = {
    tooltip: {
      trigger: 'axis',
      axisPointer: {
        type: 'cross'
      }
    },
    grid: {
      left: '3%',
      right: '4%',
      bottom: '3%',
      containLabel: true
    },
    xAxis: {
      type: 'category',
      data: dates,
      axisLine: {
        lineStyle: {
          color: '#E4E7ED'
        }
      }
    },
    yAxis: {
      type: 'value',
      axisLine: {
        lineStyle: {
          color: '#E4E7ED'
        }
      }
    },
    series: [
      {
        name: '用户数',
        type: 'line',
        smooth: true,
        data: users,
        itemStyle: {
          color: '#409EFF'
        },
        areaStyle: {
          color: new echarts.graphic.LinearGradient(0, 0, 0, 1, [
            { offset: 0, color: 'rgba(64, 158, 255, 0.3)' },
            { offset: 1, color: 'rgba(64, 158, 255, 0.1)' }
          ])
        }
      }
    ]
  }
  
  userChart.setOption(option)
}

const initReviewChart = () => {
  if (!reviewChartRef.value) return
  
  reviewChart = echarts.init(reviewChartRef.value)
  updateReviewChart()
}

const updateReviewChart = () => {
  if (!reviewChart) return
  
  // TODO: 从真实API获取审查统计数据
  let data, colors
  
  if (reviewChartType.value === 'status') {
    data = [
      { value: 0, name: '已完成' },
      { value: 0, name: '处理中' },
      { value: 0, name: '失败' },
      { value: 0, name: '待处理' }
    ]
    colors = ['#67C23A', '#E6A23C', '#F56C6C', '#909399']
  } else {
    data = [
      { value: 0, name: '低风险' },
      { value: 0, name: '中风险' },
      { value: 0, name: '高风险' }
    ]
    colors = ['#67C23A', '#E6A23C', '#F56C6C']
  }
  
  const option = {
    tooltip: {
      trigger: 'item',
      formatter: '{a} <br/>{b}: {c} ({d}%)'
    },
    legend: {
      orient: 'vertical',
      left: 'left'
    },
    color: colors,
    series: [
      {
        name: reviewChartType.value === 'status' ? '审查状态' : '风险等级',
        type: 'pie',
        radius: ['40%', '70%'],
        center: ['60%', '50%'],
        data: data,
        emphasis: {
          itemStyle: {
            shadowBlur: 10,
            shadowOffsetX: 0,
            shadowColor: 'rgba(0, 0, 0, 0.5)'
          }
        }
      }
    ]
  }
  
  reviewChart.setOption(option)
}

const initUsageChart = () => {
  if (!usageChartRef.value) return
  
  usageChart = echarts.init(usageChartRef.value)
  
  // TODO: 从真实API获取24小时使用数据
  const hours = []
  const usage = []
  
  for (let i = 0; i < 24; i++) {
    hours.push(`${i}:00`)
    usage.push(0) // 暂无真实数据
  }
  
  const option = {
    tooltip: {
      trigger: 'axis'
    },
    grid: {
      left: '3%',
      right: '4%',
      bottom: '3%',
      containLabel: true
    },
    xAxis: {
      type: 'category',
      data: hours,
      axisLine: {
        lineStyle: {
          color: '#E4E7ED'
        }
      }
    },
    yAxis: {
      type: 'value',
      name: '使用次数',
      axisLine: {
        lineStyle: {
          color: '#E4E7ED'
        }
      }
    },
    series: [
      {
        name: '使用次数',
        type: 'bar',
        data: usage,
        itemStyle: {
          color: new echarts.graphic.LinearGradient(0, 0, 0, 1, [
            { offset: 0, color: '#67C23A' },
            { offset: 1, color: '#85CE61' }
          ])
        }
      }
    ]
  }
  
  usageChart.setOption(option)
}

const initFeatureChart = () => {
  if (!featureChartRef.value) return
  
  featureChart = echarts.init(featureChartRef.value)
  
  // TODO: 从真实API获取功能使用统计
  const data = [
    { value: 0, name: '合同审查' },
    { value: 0, name: 'AI问答' },
    { value: 0, name: '文档管理' },
    { value: 0, name: '用户管理' },
    { value: 0, name: '系统设置' }
  ]
  
  const option = {
    tooltip: {
      trigger: 'item',
      formatter: '{a} <br/>{b}: {c} ({d}%)'
    },
    series: [
      {
        name: '功能使用',
        type: 'pie',
        radius: '70%',
        data: data,
        emphasis: {
          itemStyle: {
            shadowBlur: 10,
            shadowOffsetX: 0,
            shadowColor: 'rgba(0, 0, 0, 0.5)'
          }
        },
        label: {
          show: true,
          formatter: '{b}: {d}%'
        }
      }
    ]
  }
  
  featureChart.setOption(option)
}

// 事件处理
const refreshSystemStatus = () => {
  fetchSystemStatus()
  ElMessage.success('系统状态已刷新')
}

const exportActiveUsers = () => {
  // 模拟导出功能
  const csvContent = 'data:text/csv;charset=utf-8,' + 
    '排名,用户名,审查数,问答数,最后活跃\n' +
    activeUsers.value.map(user => 
      `${user.rank},${user.username},${user.reviewCount},${user.questionCount},${user.lastActive}`
    ).join('\n')
  
  const encodedUri = encodeURI(csvContent)
  const link = document.createElement('a')
  link.setAttribute('href', encodedUri)
  link.setAttribute('download', '活跃用户排行.csv')
  document.body.appendChild(link)
  link.click()
  document.body.removeChild(link)
  
  ElMessage.success('数据导出成功')
}

// 窗口大小变化处理
const handleResize = () => {
  userChart?.resize()
  reviewChart?.resize()
  usageChart?.resize()
  featureChart?.resize()
}

// 组件挂载和卸载
onMounted(async () => {
  await fetchOverviewData()
  await fetchActiveUsers()
  await fetchSystemStatus()
  
  await nextTick()
  
  initUserChart()
  initReviewChart()
  initUsageChart()
  initFeatureChart()
  
  // 定时更新系统状态
  statusTimer = setInterval(fetchSystemStatus, 30000) // 30秒更新一次
  
  // 监听窗口大小变化
  window.addEventListener('resize', handleResize)
})

onUnmounted(() => {
  if (statusTimer) {
    clearInterval(statusTimer)
  }
  
  userChart?.dispose()
  reviewChart?.dispose()
  usageChart?.dispose()
  featureChart?.dispose()
  
  window.removeEventListener('resize', handleResize)
})
</script>

<style scoped>
.statistics-container {
  max-width: 1200px;
  margin: 0 auto;
}

.overview-cards {
  margin-bottom: 20px;
}

.stat-card {
  height: 120px;
}

.stat-content {
  display: flex;
  align-items: center;
  height: 100%;
}

.stat-icon {
  width: 60px;
  height: 60px;
  border-radius: 12px;
  display: flex;
  align-items: center;
  justify-content: center;
  margin-right: 16px;
}

.stat-icon.users {
  background-color: #e3f2fd;
  color: #1976d2;
}

.stat-icon.reviews {
  background-color: #e8f5e8;
  color: #388e3c;
}

.stat-icon.questions {
  background-color: #fff3e0;
  color: #f57c00;
}

.stat-icon.documents {
  background-color: #f3e5f5;
  color: #7b1fa2;
}

.stat-info {
  flex: 1;
}

.stat-number {
  font-size: 28px;
  font-weight: bold;
  color: #2c3e50;
  margin-bottom: 4px;
}

.stat-label {
  color: #7f8c8d;
  font-size: 14px;
  margin-bottom: 4px;
}

.stat-change {
  font-size: 12px;
}

.stat-change.positive {
  color: #67C23A;
}

.stat-change.negative {
  color: #F56C6C;
}

.charts-section {
  margin-bottom: 20px;
}

.chart-card {
  margin-bottom: 20px;
}

.chart-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.chart-header h4 {
  margin: 0;
  color: #2c3e50;
}

.chart-container {
  height: 300px;
  width: 100%;
}

.tables-section {
  margin-top: 20px;
}

.table-card {
  margin-bottom: 20px;
}

.table-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.table-header h4 {
  margin: 0;
  color: #2c3e50;
}

.user-cell {
  display: flex;
  align-items: center;
  gap: 8px;
}

.username {
  font-weight: 500;
}

.last-active {
  color: #7f8c8d;
  font-size: 12px;
}

.system-status {
  padding: 10px 0;
}

.status-item {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 20px;
}

.status-item:last-child {
  margin-bottom: 0;
}

.status-label {
  font-weight: 500;
  color: #2c3e50;
  min-width: 100px;
}

.status-value {
  display: flex;
  align-items: center;
  flex: 1;
  margin-left: 20px;
}

.status-value .el-progress {
  flex: 1;
  margin-right: 10px;
}

.percentage,
.connection-count,
.response-time {
  min-width: 60px;
  text-align: right;
  font-size: 12px;
  color: #7f8c8d;
}

.uptime {
  font-weight: 500;
  color: #67C23A;
}

/* 响应式设计 */
@media (max-width: 768px) {
  .overview-cards .el-col {
    margin-bottom: 15px;
  }
  
  .stat-content {
    flex-direction: column;
    text-align: center;
    padding: 10px;
  }
  
  .stat-icon {
    margin-right: 0;
    margin-bottom: 10px;
  }
  
  .stat-number {
    font-size: 24px;
  }
  
  .chart-header {
    flex-direction: column;
    gap: 10px;
    align-items: flex-start;
  }
  
  .chart-container {
    height: 250px;
  }
  
  .status-item {
    flex-direction: column;
    align-items: flex-start;
    gap: 8px;
  }
  
  .status-value {
    width: 100%;
    margin-left: 0;
  }
}

@media (max-width: 480px) {
  .stat-card {
    height: auto;
    padding: 15px;
  }
  
  .chart-container {
    height: 200px;
  }
  
  .table-header {
    flex-direction: column;
    gap: 10px;
    align-items: flex-start;
  }
  
  .user-cell {
    flex-direction: column;
    align-items: flex-start;
    gap: 4px;
  }
}
</style>