<template>
  <div class="admin-statistics-container">
    <!-- 页面标题 -->
    <div class="page-header">
      <h2>系统统计</h2>
      <p>查看系统整体运行状况和使用数据分析</p>
    </div>

    <!-- 统计卡片 -->
    <div class="stats-overview">
      <el-card class="stat-card" shadow="hover">
        <div class="stat-content">
          <div class="stat-icon users-icon">
            <el-icon><User /></el-icon>
          </div>
          <div class="stat-info">
            <div class="stat-number">{{ overviewStats.totalUsers }}</div>
            <div class="stat-label">注册用户</div>
            <div class="stat-change positive">
              <el-icon><ArrowUp /></el-icon>
              +{{ overviewStats.newUsersToday }}
            </div>
          </div>
        </div>
      </el-card>

      <el-card class="stat-card" shadow="hover">
        <div class="stat-content">
          <div class="stat-icon reviews-icon">
            <el-icon><Document /></el-icon>
          </div>
          <div class="stat-info">
            <div class="stat-number">{{ overviewStats.totalReviews }}</div>
            <div class="stat-label">合同审查</div>
            <div class="stat-change positive">
              <el-icon><ArrowUp /></el-icon>
              +{{ overviewStats.reviewsToday }}
            </div>
          </div>
        </div>
      </el-card>

      <el-card class="stat-card" shadow="hover">
        <div class="stat-content">
          <div class="stat-icon chats-icon">
            <el-icon><ChatDotRound /></el-icon>
          </div>
          <div class="stat-info">
            <div class="stat-number">{{ overviewStats.totalChats }}</div>
            <div class="stat-label">AI对话</div>
            <div class="stat-change positive">
              <el-icon><ArrowUp /></el-icon>
              +{{ overviewStats.chatsToday }}
            </div>
          </div>
        </div>
      </el-card>

      <el-card class="stat-card" shadow="hover">
        <div class="stat-content">
          <div class="stat-icon docs-icon">
            <el-icon><Collection /></el-icon>
          </div>
          <div class="stat-info">
            <div class="stat-number">{{ overviewStats.totalDocs }}</div>
            <div class="stat-label">知识文档</div>
            <div class="stat-change positive">
              <el-icon><ArrowUp /></el-icon>
              +{{ overviewStats.docsToday }}
            </div>
          </div>
        </div>
      </el-card>
    </div>

    <!-- 图表区域 -->
    <div class="charts-section">
      <!-- 使用趋势图表 -->
      <el-card class="chart-card" shadow="never">
        <template #header>
          <div class="chart-header">
            <h3>使用趋势分析</h3>
            <el-radio-group v-model="trendPeriod" @change="loadTrendData">
              <el-radio-button label="7d">近7天</el-radio-button>
              <el-radio-button label="30d">近30天</el-radio-button>
              <el-radio-button label="90d">近90天</el-radio-button>
            </el-radio-group>
          </div>
        </template>
        
        <div ref="trendChartRef" class="chart-container"></div>
      </el-card>

      <!-- 用户活跃度分析 -->
      <el-card class="chart-card" shadow="never">
        <template #header>
          <h3>用户活跃度分析</h3>
        </template>
        
        <div ref="userActivityChartRef" class="chart-container"></div>
      </el-card>
    </div>

    <!-- 详细数据表格 -->
    <div class="details-section">
      <!-- 热门功能使用统计 -->
      <el-card class="details-card" shadow="never">
        <template #header>
          <h3>功能使用统计</h3>
        </template>
        
        <el-table :data="featureStats" stripe>
          <el-table-column prop="feature" label="功能名称" min-width="120">
            <template #default="{ row }">
              <div class="feature-info">
                <el-icon class="feature-icon">
                  <component :is="getFeatureIcon(row.feature)" />
                </el-icon>
                <span>{{ row.featureName }}</span>
              </div>
            </template>
          </el-table-column>
          
          <el-table-column prop="totalUsage" label="总使用次数" width="120" align="center">
            <template #default="{ row }">
              <el-tag type="primary">{{ row.totalUsage }}</el-tag>
            </template>
          </el-table-column>
          
          <el-table-column prop="todayUsage" label="今日使用" width="100" align="center">
            <template #default="{ row }">
              <el-tag type="success" size="small">{{ row.todayUsage }}</el-tag>
            </template>
          </el-table-column>
          
          <el-table-column prop="avgDailyUsage" label="日均使用" width="100" align="center" />
          
          <el-table-column prop="userCount" label="使用用户数" width="120" align="center" />
          
          <el-table-column prop="growthRate" label="增长率" width="100" align="center">
            <template #default="{ row }">
              <span :class="['growth-rate', row.growthRate >= 0 ? 'positive' : 'negative']">
                <el-icon>
                  <component :is="row.growthRate >= 0 ? 'ArrowUp' : 'ArrowDown'" />
                </el-icon>
                {{ Math.abs(row.growthRate) }}%
              </span>
            </template>
          </el-table-column>
        </el-table>
      </el-card>

      <!-- 系统性能指标 -->
      <el-card class="details-card" shadow="never">
        <template #header>
          <div class="chart-header">
            <h3>系统性能指标</h3>
            <el-button @click="refreshPerformanceData" :loading="performanceLoading">
              <el-icon><Refresh /></el-icon>
              刷新
            </el-button>
          </div>
        </template>
        
        <div class="performance-metrics">
          <div class="metric-item">
            <div class="metric-label">平均响应时间</div>
            <div class="metric-value">{{ performanceMetrics.avgResponseTime }}ms</div>
            <el-progress
              :percentage="Math.min((performanceMetrics.avgResponseTime / 1000) * 100, 100)"
              :color="getResponseTimeColor(performanceMetrics.avgResponseTime)"
              :show-text="false"
            />
          </div>
          
          <div class="metric-item">
            <div class="metric-label">系统可用率</div>
            <div class="metric-value">{{ performanceMetrics.uptime }}%</div>
            <el-progress
              :percentage="performanceMetrics.uptime"
              :color="performanceMetrics.uptime > 99 ? '#67c23a' : '#e6a23c'"
              :show-text="false"
            />
          </div>
          
          <div class="metric-item">
            <div class="metric-label">错误率</div>
            <div class="metric-value">{{ performanceMetrics.errorRate }}%</div>
            <el-progress
              :percentage="performanceMetrics.errorRate"
              :color="performanceMetrics.errorRate < 1 ? '#67c23a' : '#f56c6c'"
              :show-text="false"
            />
          </div>
          
          <div class="metric-item">
            <div class="metric-label">并发用户数</div>
            <div class="metric-value">{{ performanceMetrics.concurrentUsers }}</div>
            <el-progress
              :percentage="Math.min((performanceMetrics.concurrentUsers / 1000) * 100, 100)"
              color="#409eff"
              :show-text="false"
            />
          </div>
        </div>
      </el-card>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted, onUnmounted, nextTick } from 'vue'
import * as echarts from 'echarts'
import type { ECharts } from 'echarts'
import {
  User,
  Document,
  ChatDotRound,
  Collection,
  ArrowUp,
  ArrowDown,
  Refresh
} from '@element-plus/icons-vue'

// 组件状态
const performanceLoading = ref(false)
const trendPeriod = ref('7d')

// 图表引用
const trendChartRef = ref<HTMLElement>()
const userActivityChartRef = ref<HTMLElement>()
let trendChart: ECharts | null = null
let userActivityChart: ECharts | null = null

// 概览统计数据
const overviewStats = reactive({
  totalUsers: 1247,
  newUsersToday: 23,
  totalReviews: 8965,
  reviewsToday: 156,
  totalChats: 15432,
  chatsToday: 289,
  totalDocs: 534,
  docsToday: 8
})

// 功能使用统计
const featureStats = ref([
  {
    feature: 'chat',
    featureName: 'AI智能问答',
    totalUsage: 15432,
    todayUsage: 289,
    avgDailyUsage: 187,
    userCount: 856,
    growthRate: 12.5
  },
  {
    feature: 'contract',
    featureName: '合同审查',
    totalUsage: 8965,
    todayUsage: 156,
    avgDailyUsage: 108,
    userCount: 634,
    growthRate: 8.3
  },
  {
    feature: 'knowledge',
    featureName: '知识库查询',
    totalUsage: 6721,
    todayUsage: 98,
    avgDailyUsage: 81,
    userCount: 423,
    growthRate: -2.1
  },
  {
    feature: 'report',
    featureName: '报告下载',
    totalUsage: 3456,
    todayUsage: 67,
    avgDailyUsage: 42,
    userCount: 298,
    growthRate: 15.7
  }
])

// 系统性能指标
const performanceMetrics = reactive({
  avgResponseTime: 245,
  uptime: 99.8,
  errorRate: 0.2,
  concurrentUsers: 156
})

// 初始化趋势图表
const initTrendChart = () => {
  if (!trendChartRef.value) return

  trendChart = echarts.init(trendChartRef.value)
  
  const option = {
    title: {
      text: '系统使用趋势',
      left: 'center',
      textStyle: {
        fontSize: 16,
        fontWeight: 'normal'
      }
    },
    tooltip: {
      trigger: 'axis',
      axisPointer: {
        type: 'cross'
      }
    },
    legend: {
      data: ['用户注册', '合同审查', 'AI对话'],
      top: 30
    },
    grid: {
      left: '3%',
      right: '4%',
      bottom: '3%',
      containLabel: true
    },
    xAxis: {
      type: 'category',
      boundaryGap: false,
      data: ['1/1', '1/2', '1/3', '1/4', '1/5', '1/6', '1/7']
    },
    yAxis: {
      type: 'value'
    },
    series: [
      {
        name: '用户注册',
        type: 'line',
        stack: 'Total',
        smooth: true,
        data: [12, 18, 15, 22, 19, 25, 23]
      },
      {
        name: '合同审查',
        type: 'line',
        stack: 'Total',
        smooth: true,
        data: [89, 95, 103, 87, 112, 98, 156]
      },
      {
        name: 'AI对话',
        type: 'line',
        stack: 'Total',
        smooth: true,
        data: [167, 189, 203, 178, 234, 198, 289]
      }
    ]
  }

  trendChart.setOption(option)
}

// 初始化用户活跃度图表
const initUserActivityChart = () => {
  if (!userActivityChartRef.value) return

  userActivityChart = echarts.init(userActivityChartRef.value)
  
  const option = {
    title: {
      text: '用户活跃度分布',
      left: 'center',
      textStyle: {
        fontSize: 16,
        fontWeight: 'normal'
      }
    },
    tooltip: {
      trigger: 'item',
      formatter: '{a} <br/>{b}: {c} ({d}%)'
    },
    legend: {
      orient: 'vertical',
      left: 'left',
      top: 50,
      data: ['高活跃用户', '中活跃用户', '低活跃用户', '沉睡用户']
    },
    series: [
      {
        name: '用户活跃度',
        type: 'pie',
        radius: ['40%', '70%'],
        center: ['60%', '55%'],
        avoidLabelOverlap: false,
        label: {
          show: false,
          position: 'center'
        },
        emphasis: {
          label: {
            show: true,
            fontSize: '18',
            fontWeight: 'bold'
          }
        },
        labelLine: {
          show: false
        },
        data: [
          { value: 335, name: '高活跃用户', itemStyle: { color: '#67c23a' } },
          { value: 310, name: '中活跃用户', itemStyle: { color: '#409eff' } },
          { value: 234, name: '低活跃用户', itemStyle: { color: '#e6a23c' } },
          { value: 135, name: '沉睡用户', itemStyle: { color: '#f56c6c' } }
        ]
      }
    ]
  }

  userActivityChart.setOption(option)
}

// 加载趋势数据
const loadTrendData = () => {
  // 这里可以根据时间周期加载不同的数据
  console.log('Loading trend data for period:', trendPeriod.value)
}

// 刷新性能数据
const refreshPerformanceData = () => {
  performanceLoading.value = true
  
  // 模拟API调用
  setTimeout(() => {
    performanceMetrics.avgResponseTime = Math.floor(Math.random() * 200) + 200
    performanceMetrics.uptime = Number((99 + Math.random()).toFixed(1))
    performanceMetrics.errorRate = Number((Math.random() * 0.5).toFixed(1))
    performanceMetrics.concurrentUsers = Math.floor(Math.random() * 200) + 100
    
    performanceLoading.value = false
  }, 1000)
}

// 获取功能图标
const getFeatureIcon = (feature: string) => {
  const iconMap = {
    chat: 'ChatDotRound',
    contract: 'Document',
    knowledge: 'Collection',
    report: 'Download'
  }
  return iconMap[feature as keyof typeof iconMap] || 'Document'
}

// 获取响应时间颜色
const getResponseTimeColor = (time: number) => {
  if (time < 200) return '#67c23a'
  if (time < 500) return '#e6a23c'
  return '#f56c6c'
}

// 窗口大小变化处理
const handleResize = () => {
  trendChart?.resize()
  userActivityChart?.resize()
}

// 组件挂载
onMounted(async () => {
  await nextTick()
  initTrendChart()
  initUserActivityChart()
  
  window.addEventListener('resize', handleResize)
})

// 组件卸载
onUnmounted(() => {
  trendChart?.dispose()
  userActivityChart?.dispose()
  window.removeEventListener('resize', handleResize)
})
</script>

<style scoped>
.admin-statistics-container {
  max-width: 1200px;
  margin: 0 auto;
}

.page-header {
  margin-bottom: 24px;
  text-align: center;
}

.page-header h2 {
  margin: 0 0 8px 0;
  color: var(--text-primary);
}

.page-header p {
  margin: 0;
  color: var(--text-secondary);
}

.stats-overview {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(250px, 1fr));
  gap: 20px;
  margin-bottom: 32px;
}

.stat-card {
  border: none;
  transition: transform 0.3s, box-shadow 0.3s;
}

.stat-card:hover {
  transform: translateY(-2px);
  box-shadow: 0 8px 25px rgba(0, 0, 0, 0.1);
}

.stat-content {
  display: flex;
  align-items: center;
  gap: 16px;
}

.stat-icon {
  width: 60px;
  height: 60px;
  border-radius: 12px;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 24px;
  color: white;
}

.users-icon {
  background: linear-gradient(135deg, #667eea, #764ba2);
}

.reviews-icon {
  background: linear-gradient(135deg, #f093fb, #f5576c);
}

.chats-icon {
  background: linear-gradient(135deg, #4facfe, #00f2fe);
}

.docs-icon {
  background: linear-gradient(135deg, #43e97b, #38f9d7);
}

.stat-number {
  font-size: 32px;
  font-weight: 600;
  color: var(--text-primary);
  line-height: 1;
}

.stat-label {
  font-size: 14px;
  color: var(--text-secondary);
  margin: 4px 0;
}

.stat-change {
  display: flex;
  align-items: center;
  gap: 4px;
  font-size: 12px;
  font-weight: 500;
}

.stat-change.positive {
  color: #67c23a;
}

.stat-change.negative {
  color: #f56c6c;
}

.charts-section {
  display: grid;
  grid-template-columns: 2fr 1fr;
  gap: 24px;
  margin-bottom: 32px;
}

.chart-card {
  min-height: 400px;
}

.chart-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.chart-header h3 {
  margin: 0;
  color: var(--text-primary);
}

.chart-container {
  width: 100%;
  height: 300px;
}

.details-section {
  display: grid;
  grid-template-columns: 1fr;
  gap: 24px;
}

.details-card h3 {
  margin: 0;
  color: var(--text-primary);
}

.feature-info {
  display: flex;
  align-items: center;
  gap: 8px;
}

.feature-icon {
  color: var(--primary-color);
}

.growth-rate {
  display: flex;
  align-items: center;
  gap: 4px;
  font-weight: 500;
}

.growth-rate.positive {
  color: #67c23a;
}

.growth-rate.negative {
  color: #f56c6c;
}

.performance-metrics {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(200px, 1fr));
  gap: 24px;
}

.metric-item {
  text-align: center;
}

.metric-label {
  font-size: 14px;
  color: var(--text-secondary);
  margin-bottom: 8px;
}

.metric-value {
  font-size: 24px;
  font-weight: 600;
  color: var(--text-primary);
  margin-bottom: 12px;
}

@media (max-width: 1024px) {
  .charts-section {
    grid-template-columns: 1fr;
  }
  
  .stats-overview {
    grid-template-columns: repeat(auto-fit, minmax(200px, 1fr));
    gap: 16px;
  }
}

@media (max-width: 768px) {
  .stats-overview {
    grid-template-columns: 1fr;
  }
  
  .stat-content {
    justify-content: center;
    text-align: center;
  }
  
  .chart-header {
    flex-direction: column;
    gap: 12px;
    align-items: flex-start;
  }
  
  .performance-metrics {
    grid-template-columns: repeat(auto-fit, minmax(150px, 1fr));
    gap: 16px;
  }
}
</style>
