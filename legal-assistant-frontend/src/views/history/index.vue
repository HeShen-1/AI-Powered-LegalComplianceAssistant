<template>
  <div class="history-container">
    <el-card shadow="never">
      <template #header>
        <div class="page-header">
          <div class="header-left">
            <h3>📋 审查历史</h3>
            <p class="header-subtitle">查看和管理您的合同审查记录</p>
          </div>
          <div class="header-right">
            <el-button type="primary" @click="refreshList">
              <el-icon><Refresh /></el-icon>
              刷新
            </el-button>
          </div>
        </div>
      </template>
      
      <!-- 搜索和筛选 -->
      <div class="search-section">
        <el-row :gutter="20">
          <el-col :xs="24" :sm="12" :md="6">
            <el-input
              v-model="searchQuery"
              placeholder="搜索文件名..."
              clearable
              @input="handleSearch"
            >
              <template #prefix>
                <el-icon><Search /></el-icon>
              </template>
            </el-input>
          </el-col>
          <el-col :xs="24" :sm="12" :md="5">
            <el-select
              v-model="statusFilter"
              placeholder="状态筛选"
              clearable
              @change="handleFilter"
              style="width: 100%"
            >
              <el-option label="全部" value="" />
              <el-option label="处理中" value="PROCESSING" />
              <el-option label="已完成" value="COMPLETED" />
              <el-option label="失败" value="FAILED" />
            </el-select>
          </el-col>
          <el-col :xs="24" :sm="12" :md="5">
            <el-select
              v-model="riskFilter"
              placeholder="风险等级"
              clearable
              @change="handleFilter"
              style="width: 100%"
            >
              <el-option label="全部" value="" />
              <el-option label="高风险" value="HIGH" />
              <el-option label="中风险" value="MEDIUM" />
              <el-option label="低风险" value="LOW" />
            </el-select>
          </el-col>
          <el-col :xs="24" :sm="24" :md="8">
            <el-date-picker
              v-model="dateRange"
              type="daterange"
              range-separator="至"
              start-placeholder="开始日期"
              end-placeholder="结束日期"
              format="YYYY-MM-DD"
              value-format="YYYY-MM-DD"
              :unlink-panels="true"
              style="width: 100%"
              @change="handleFilter"
            />
          </el-col>
        </el-row>
      </div>
      
      <!-- 数据表格 -->
      <div class="table-section">
        <el-table
          v-loading="loading"
          :data="filteredList"
          stripe
          @sort-change="handleSortChange"
        >
          <el-table-column prop="originalFilename" label="文件名" min-width="200">
            <template #default="{ row }">
              <div class="filename-cell">
                <el-icon class="file-icon"><Document /></el-icon>
                <span class="filename">{{ row.originalFilename }}</span>
              </div>
            </template>
          </el-table-column>
          
          <el-table-column prop="reviewStatus" label="状态" width="100" align="center">
            <template #default="{ row }">
              <el-tag :type="getStatusType(row.reviewStatus)" size="small">
                {{ getStatusText(row.reviewStatus) }}
              </el-tag>
            </template>
          </el-table-column>
          
          <el-table-column prop="riskLevel" label="风险等级" width="100" align="center">
            <template #default="{ row }">
              <el-tag
                v-if="row.riskLevel"
                :type="getRiskType(row.riskLevel)"
                size="small"
              >
                {{ getRiskText(row.riskLevel) }}
              </el-tag>
              <span v-else class="text-muted">-</span>
            </template>
          </el-table-column>
          
          <el-table-column
            prop="createdAt"
            label="创建时间"
            width="160"
            sortable="custom"
          >
            <template #default="{ row }">
              {{ formatDateTime(row.createdAt) }}
            </template>
          </el-table-column>
          
          <el-table-column
            prop="completedAt"
            label="完成时间"
            width="160"
            sortable="custom"
          >
            <template #default="{ row }">
              <span v-if="row.completedAt">{{ formatDateTime(row.completedAt) }}</span>
              <span v-else class="text-muted">-</span>
            </template>
          </el-table-column>
          
          <el-table-column label="操作" width="200" align="center" fixed="right">
            <template #default="{ row }">
              <div class="action-buttons">
                <el-button
                  v-if="row.reviewStatus === 'COMPLETED'"
                  type="success"
                  size="small"
                  @click="downloadReport(row)"
                >
                  下载报告
                </el-button>
                <el-button
                  type="danger"
                  size="small"
                  @click="deleteRecord(row)"
                >
                  删除
                </el-button>
              </div>
            </template>
          </el-table-column>
        </el-table>
        
        <!-- 分页 -->
        <div class="pagination-section">
          <el-pagination
            v-model:current-page="currentPage"
            v-model:page-size="pageSize"
            :total="total"
            :page-sizes="[10, 20, 50, 100]"
            layout="total, sizes, prev, pager, next, jumper"
            @size-change="handleSizeChange"
            @current-change="handleCurrentChange"
          />
        </div>
      </div>
    </el-card>
    
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  Refresh,
  Search,
  Document
} from '@element-plus/icons-vue'
import { useUserStore } from '@/store/modules/user'
import { getMyReviewsApi, downloadReportApi, deleteReviewApi } from '@/api/contractService'

// 类型定义
interface ContractReview {
  id: number
  userId: number
  filename: string
  filePath: string
  status: 'PENDING' | 'PROCESSING' | 'COMPLETED' | 'FAILED'
  riskLevel?: 'LOW' | 'MEDIUM' | 'HIGH'
  createdAt: string
  completedAt?: string
  result?: any
}

const userStore = useUserStore()

// 响应式数据
const loading = ref(false)
const reviewList = ref<ContractReview[]>([])
const searchQuery = ref('')
const statusFilter = ref('')
const riskFilter = ref('')
const dateRange = ref<[string, string] | null>(null)
const currentPage = ref(1)
const pageSize = ref(20)
const total = ref(0)
const sortField = ref('')
const sortOrder = ref('')

// 计算属性
const filteredList = computed(() => {
  let list = [...reviewList.value]
  
  // 搜索过滤
  if (searchQuery.value) {
    const query = searchQuery.value.toLowerCase()
    list = list.filter(item => 
      item.originalFilename.toLowerCase().includes(query)
    )
  }
  
  // 状态过滤
  if (statusFilter.value) {
    list = list.filter(item => item.reviewStatus === statusFilter.value)
  }
  
  // 风险等级过滤
  if (riskFilter.value) {
    list = list.filter(item => item.riskLevel === riskFilter.value)
  }
  
  // 日期范围过滤
  if (dateRange.value && dateRange.value.length === 2) {
    const [startDate, endDate] = dateRange.value
    list = list.filter(item => {
      const itemDate = item.createdAt.split('T')[0]
      return itemDate >= startDate && itemDate <= endDate
    })
  }
  
  // 排序
  if (sortField.value) {
    list.sort((a, b) => {
      const aVal = a[sortField.value as keyof ContractReview]
      const bVal = b[sortField.value as keyof ContractReview]
      
      if (sortOrder.value === 'ascending') {
        return aVal > bVal ? 1 : -1
      } else {
        return aVal < bVal ? 1 : -1
      }
    })
  }
  
  return list
})

// 工具函数
const formatDateTime = (dateStr: string) => {
  return new Date(dateStr).toLocaleString('zh-CN', {
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit'
  })
}

const getStatusType = (status: string) => {
  const typeMap = {
    PENDING: 'info',
    PROCESSING: 'warning',
    COMPLETED: 'success',
    FAILED: 'danger'
  }
  return typeMap[status as keyof typeof typeMap] || 'info'
}

const getStatusText = (status: string) => {
  const textMap = {
    PENDING: '待处理',
    PROCESSING: '处理中',
    COMPLETED: '已完成',
    FAILED: '失败'
  }
  return textMap[status as keyof typeof textMap] || status
}

const getRiskType = (level: string) => {
  const typeMap = {
    LOW: 'success',
    MEDIUM: 'warning',
    HIGH: 'danger'
  }
  return typeMap[level as keyof typeof typeMap] || 'info'
}

const getRiskText = (level: string) => {
  const textMap = {
    LOW: '低风险',
    MEDIUM: '中风险',
    HIGH: '高风险'
  }
  return textMap[level as keyof typeof textMap] || level
}

// 数据获取
const fetchReviewList = async () => {
  loading.value = true
  try {
    // 调用真实API获取审查记录
    // 注意：后端接收的参数是 page 和 size，page 从 0 开始
    const response = await getMyReviewsApi({ 
      page: currentPage.value - 1,  // 后端 page 从 0 开始，前端从 1 开始
      size: pageSize.value 
    })
    
    if (response.data && response.data.data) {
      reviewList.value = response.data.data.content || []
      total.value = response.data.data.totalElements || 0
    } else {
      reviewList.value = []
      total.value = 0
    }
  } catch (error) {
    console.error('Failed to fetch review list:', error)
    reviewList.value = []
    total.value = 0
  } finally {
    loading.value = false
  }
}

// 事件处理
const refreshList = () => {
  fetchReviewList()
}

const handleSearch = () => {
  // 搜索逻辑在计算属性中处理
}

const handleFilter = () => {
  // 过滤逻辑在计算属性中处理
}

const handleSortChange = ({ prop, order }: { prop: string; order: string }) => {
  sortField.value = prop
  sortOrder.value = order
}

const handleSizeChange = (size: number) => {
  pageSize.value = size
  currentPage.value = 1
  fetchReviewList()  // 重新获取数据
}

const handleCurrentChange = (page: number) => {
  currentPage.value = page
  fetchReviewList()  // 重新获取数据
}


const downloadReport = async (record: ContractReview | null) => {
  if (!record) return
  
  try {
    // 调用真实API下载报告
    const response = await downloadReportApi(record.id)
    
    // 从响应头中获取文件名，如果没有则使用默认名称
    let filename = '审查报告.pdf'
    const contentDisposition = response.headers['content-disposition']
    if (contentDisposition) {
      // 优先解析UTF-8编码的文件名 (filename*=UTF-8''...)
      const utf8Match = contentDisposition.match(/filename\*=UTF-8''([^;]+)/)
      if (utf8Match && utf8Match[1]) {
        try {
          filename = decodeURIComponent(utf8Match[1])
        } catch (e) {
          console.warn('UTF-8文件名解码失败:', e)
        }
      } else {
        // 回退到普通文件名解析
        const filenameMatch = contentDisposition.match(/filename[^;=\n]*=((['"]).*?\2|[^;\n]*)/)
        if (filenameMatch && filenameMatch[1]) {
          filename = filenameMatch[1].replace(/['"]/g, '')
        }
      }
    }
    
    // 如果响应头中没有文件名，则生成一个
    if (filename === '审查报告.pdf') {
      const filenameWithoutExt = record.originalFilename.replace(/\.[^/.]+$/, '')
      filename = `${filenameWithoutExt}_审查报告.pdf`
    }
    
    // 创建Blob URL并下载
    const blob = new Blob([response.data], { type: 'application/pdf' })
    const url = window.URL.createObjectURL(blob)
    const link = document.createElement('a')
    link.href = url
    link.download = filename
    document.body.appendChild(link)
    link.click()
    document.body.removeChild(link)
    window.URL.revokeObjectURL(url)
    
    ElMessage.success('报告下载成功')
  } catch (error) {
    console.error('Failed to download report:', error)
    ElMessage.error('报告下载失败')
  }
}

const deleteRecord = async (record: ContractReview) => {
  try {
    await ElMessageBox.confirm(
      `确定要删除审查记录"${record.originalFilename}"吗？此操作不可恢复。`,
      '删除确认',
      {
        confirmButtonText: '确定删除',
        cancelButtonText: '取消',
        type: 'warning'
      }
    )
    
    // 调用真实API删除
    await deleteReviewApi(record.id)
    
    // 从列表中移除
    const index = reviewList.value.findIndex(item => item.id === record.id)
    if (index > -1) {
      reviewList.value.splice(index, 1)
      total.value--
    }
    
    ElMessage.success('删除成功')
  } catch (error: any) {
    // 用户取消操作或删除失败
    if (error !== 'cancel') {
      console.error('Failed to delete review:', error)
      ElMessage.error('删除失败，请稍后重试')
    }
  }
}

// 组件挂载时获取数据
onMounted(() => {
  fetchReviewList()
})
</script>

<style scoped>
.history-container {
  max-width: 1200px;
  margin: 0 auto;
}

.page-header {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
}

.header-left h3 {
  margin: 0 0 8px 0;
  font-size: 20px;
  color: #2c3e50;
}

.header-subtitle {
  margin: 0;
  color: #7f8c8d;
  font-size: 14px;
}

.search-section {
  margin-bottom: 20px;
  padding: 20px;
  background-color: #f8f9fa;
  border-radius: 6px;
}

.table-section {
  margin-top: 20px;
}

.filename-cell {
  display: flex;
  align-items: center;
}

.file-icon {
  margin-right: 8px;
  color: #409EFF;
}

.filename {
  font-weight: 500;
}

.action-buttons {
  display: flex;
  gap: 8px;
  justify-content: center;
  align-items: center;
  flex-wrap: nowrap;
}

.text-muted {
  color: #909399;
}

.pagination-section {
  margin-top: 20px;
  text-align: right;
}

.result-content {
  max-height: 600px;
  overflow-y: auto;
}

.result-overview {
  margin-bottom: 30px;
}

.stat-card {
  text-align: center;
  padding: 20px;
  background-color: #f8f9fa;
  border-radius: 6px;
}

.stat-number {
  font-size: 28px;
  font-weight: bold;
  color: #409EFF;
  margin-bottom: 8px;
}

.stat-label {
  color: #606266;
  font-size: 14px;
}

.result-section {
  margin-bottom: 30px;
}

.result-section h4 {
  margin: 0 0 15px 0;
  color: #2c3e50;
  font-size: 16px;
}

.risk-item {
  margin-bottom: 15px;
  padding: 15px;
  background-color: #f8f9fa;
  border-radius: 6px;
  border-left: 4px solid #e9ecef;
}

.risk-header {
  display: flex;
  align-items: center;
  margin-bottom: 8px;
}

.risk-title {
  margin-left: 10px;
  font-weight: 600;
  color: #2c3e50;
}

.risk-description {
  margin: 8px 0;
  color: #5a6c7d;
  line-height: 1.5;
}

.risk-suggestion {
  margin-top: 10px;
  padding: 8px;
  background-color: #e8f4fd;
  border-radius: 4px;
  font-size: 13px;
  color: #0c5aa6;
}

.clause-content {
  line-height: 1.6;
}

.clause-content p {
  margin: 8px 0;
}

/* 响应式设计 */
@media (max-width: 768px) {
  .page-header {
    flex-direction: column;
    gap: 15px;
  }
  
  .search-section .el-row {
    gap: 15px;
  }
  
  .search-section .el-col {
    margin-bottom: 15px;
  }
  
  .pagination-section {
    text-align: center;
  }
  
  .stat-card {
    padding: 15px;
  }
  
  .stat-number {
    font-size: 24px;
  }
}

@media (max-width: 480px) {
  .search-section {
    padding: 15px;
  }
  
  .el-table :deep(.el-table__cell) {
    padding: 8px 4px;
  }
  
  .filename-cell {
    flex-direction: column;
    align-items: flex-start;
    gap: 4px;
  }
}
</style>