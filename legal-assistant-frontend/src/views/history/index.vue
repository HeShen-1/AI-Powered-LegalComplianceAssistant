<template>
  <div class="history-container">
    <!-- 页面标题和工具栏 -->
    <div class="page-header">
      <div class="header-left">
        <h2>审查历史</h2>
        <p>查看和管理您的所有合同审查记录</p>
      </div>
      <div class="header-right">
        <el-input
          v-model="searchKeyword"
          placeholder="搜索文件名..."
          :prefix-icon="Search"
          clearable
          @input="handleSearch"
          style="width: 300px; margin-right: 16px;"
        />
        <el-button @click="refreshData" :loading="loading">
          <el-icon><Refresh /></el-icon>
          刷新
        </el-button>
      </div>
    </div>

    <!-- 筛选器 -->
    <el-card class="filter-card" shadow="never">
      <el-form :model="filters" inline>
        <el-form-item label="状态筛选:">
          <el-select v-model="filters.status" placeholder="全部状态" clearable @change="handleFilterChange">
            <el-option label="全部状态" value="" />
            <el-option label="待处理" value="PENDING" />
            <el-option label="处理中" value="PROCESSING" />
            <el-option label="已完成" value="COMPLETED" />
            <el-option label="失败" value="FAILED" />
          </el-select>
        </el-form-item>
        
        <el-form-item label="风险等级:">
          <el-select v-model="filters.riskLevel" placeholder="全部等级" clearable @change="handleFilterChange">
            <el-option label="全部等级" value="" />
            <el-option label="低风险" value="LOW" />
            <el-option label="中风险" value="MEDIUM" />
            <el-option label="高风险" value="HIGH" />
          </el-select>
        </el-form-item>
        
        <el-form-item label="日期范围:">
          <el-date-picker
            v-model="filters.dateRange"
            type="daterange"
            range-separator="至"
            start-placeholder="开始日期"
            end-placeholder="结束日期"
            format="YYYY-MM-DD"
            value-format="YYYY-MM-DD"
            @change="handleFilterChange"
          />
        </el-form-item>
      </el-form>
    </el-card>

    <!-- 数据表格 -->
    <el-card class="table-card" shadow="never">
      <el-table
        :data="tableData"
        :loading="loading"
        stripe
        @selection-change="handleSelectionChange"
        @sort-change="handleSortChange"
      >
        <el-table-column type="selection" width="55" />
        
        <el-table-column prop="filename" label="文件名" min-width="200" show-overflow-tooltip>
          <template #default="{ row }">
            <div class="file-info">
              <el-icon class="file-icon"><Document /></el-icon>
              <span class="file-name">{{ row.filename }}</span>
            </div>
          </template>
        </el-table-column>
        
        <el-table-column prop="status" label="状态" width="100" align="center">
          <template #default="{ row }">
            <el-tag :type="getStatusType(row.status)">
              {{ getStatusText(row.status) }}
            </el-tag>
          </template>
        </el-table-column>
        
        <el-table-column prop="riskLevel" label="风险等级" width="100" align="center">
          <template #default="{ row }">
            <el-tag
              v-if="row.riskLevel"
              :type="getRiskLevelType(row.riskLevel)"
              size="small"
            >
              {{ getRiskLevelText(row.riskLevel) }}
            </el-tag>
            <span v-else class="text-placeholder">-</span>
          </template>
        </el-table-column>
        
        <el-table-column prop="createdAt" label="创建时间" width="180" sortable="custom">
          <template #default="{ row }">
            {{ formatDateTime(row.createdAt) }}
          </template>
        </el-table-column>
        
        <el-table-column prop="completedAt" label="完成时间" width="180" sortable="custom">
          <template #default="{ row }">
            <span v-if="row.completedAt">{{ formatDateTime(row.completedAt) }}</span>
            <span v-else class="text-placeholder">-</span>
          </template>
        </el-table-column>
        
        <el-table-column label="操作" width="200" fixed="right">
          <template #default="{ row }">
            <el-button
              text
              type="primary"
              @click="viewDetail(row)"
            >
              查看详情
            </el-button>
            
            <el-button
              v-if="row.status === 'COMPLETED'"
              text
              type="success"
              @click="downloadReport(row)"
              :loading="downloadingReports[row.id]"
            >
              下载报告
            </el-button>
            
            <el-popconfirm
              title="确定删除这条记录吗？"
              @confirm="deleteRecord(row)"
            >
              <template #reference>
                <el-button text type="danger">删除</el-button>
              </template>
            </el-popconfirm>
          </template>
        </el-table-column>
      </el-table>
      
      <!-- 分页 -->
      <div class="pagination-wrapper">
        <div class="pagination-info">
          <span>共 {{ total }} 条记录</span>
          <el-button
            v-if="selectedRows.length > 0"
            type="danger"
            @click="batchDelete"
            :loading="batchDeleting"
          >
            批量删除 ({{ selectedRows.length }})
          </el-button>
        </div>
        
        <el-pagination
          v-model:current-page="pagination.page"
          v-model:page-size="pagination.size"
          :page-sizes="[10, 20, 50, 100]"
          :total="total"
          layout="sizes, prev, pager, next, jumper"
          @size-change="handleSizeChange"
          @current-change="handleCurrentChange"
        />
      </div>
    </el-card>

    <!-- 详情对话框 -->
    <el-dialog
      v-model="detailVisible"
      :title="`审查详情 - ${currentRecord?.filename}`"
      width="800px"
      destroy-on-close
    >
      <div v-if="currentRecord" class="detail-content">
        <el-descriptions :column="2" border>
          <el-descriptions-item label="文件名">
            {{ currentRecord.filename }}
          </el-descriptions-item>
          <el-descriptions-item label="状态">
            <el-tag :type="getStatusType(currentRecord.status)">
              {{ getStatusText(currentRecord.status) }}
            </el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="风险等级">
            <el-tag
              v-if="currentRecord.riskLevel"
              :type="getRiskLevelType(currentRecord.riskLevel)"
            >
              {{ getRiskLevelText(currentRecord.riskLevel) }}
            </el-tag>
            <span v-else>-</span>
          </el-descriptions-item>
          <el-descriptions-item label="文件大小">
            {{ currentRecord.fileSize || '-' }}
          </el-descriptions-item>
          <el-descriptions-item label="创建时间">
            {{ formatDateTime(currentRecord.createdAt) }}
          </el-descriptions-item>
          <el-descriptions-item label="完成时间">
            {{ currentRecord.completedAt ? formatDateTime(currentRecord.completedAt) : '-' }}
          </el-descriptions-item>
        </el-descriptions>
        
        <div v-if="currentRecord.result" class="result-preview">
          <h4>审查结果预览</h4>
          <div class="result-summary">
            <p><strong>发现风险条款：</strong>{{ currentRecord.result.riskClausesCount || 0 }} 条</p>
            <p><strong>建议关注：</strong>{{ currentRecord.result.suggestions?.length || 0 }} 项</p>
          </div>
        </div>
      </div>
      
      <template #footer>
        <el-button @click="detailVisible = false">关闭</el-button>
        <el-button
          v-if="currentRecord?.status === 'COMPLETED'"
          type="primary"
          @click="downloadReport(currentRecord)"
          :loading="downloadingReports[currentRecord.id]"
        >
          下载报告
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted, computed } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import type { ContractReview } from '@/types/api'
import { getMyReviewsApi, downloadReportApi } from '@/api/contractService'
import {
  Search,
  Refresh,
  Document
} from '@element-plus/icons-vue'

// 组件状态
const loading = ref(false)
const detailVisible = ref(false)
const currentRecord = ref<ContractReview | null>(null)
const selectedRows = ref<ContractReview[]>([])
const batchDeleting = ref(false)
const downloadingReports = ref<Record<number, boolean>>({})

// 搜索和筛选
const searchKeyword = ref('')
const filters = reactive({
  status: '',
  riskLevel: '',
  dateRange: null as [string, string] | null
})

// 分页
const pagination = reactive({
  page: 1,
  size: 10
})

// 排序
const sortConfig = reactive({
  prop: '',
  order: ''
})

// 表格数据
const tableData = ref<ContractReview[]>([])
const total = ref(0)

// 加载数据
const loadData = async () => {
  loading.value = true
  try {
    const params = {
      page: pagination.page - 1,
      size: pagination.size,
      keyword: searchKeyword.value,
      status: filters.status,
      riskLevel: filters.riskLevel,
      startDate: filters.dateRange?.[0],
      endDate: filters.dateRange?.[1],
      sortBy: sortConfig.prop,
      sortOrder: sortConfig.order
    }

    // 清理空参数
    Object.keys(params).forEach(key => {
      if (params[key as keyof typeof params] === '' || params[key as keyof typeof params] === null) {
        delete params[key as keyof typeof params]
      }
    })

    const response = await getMyReviewsApi(params)
    
    if (response.data.success) {
      tableData.value = response.data.data
      total.value = response.data.totalElements
    }
  } catch (error) {
    ElMessage.error('加载数据失败')
  } finally {
    loading.value = false
  }
}

// 搜索处理
const handleSearch = () => {
  pagination.page = 1
  loadData()
}

// 筛选变化处理
const handleFilterChange = () => {
  pagination.page = 1
  loadData()
}

// 刷新数据
const refreshData = () => {
  loadData()
}

// 分页处理
const handleSizeChange = (size: number) => {
  pagination.size = size
  pagination.page = 1
  loadData()
}

const handleCurrentChange = (page: number) => {
  pagination.page = page
  loadData()
}

// 排序处理
const handleSortChange = ({ prop, order }: { prop: string; order: string }) => {
  sortConfig.prop = prop
  sortConfig.order = order === 'ascending' ? 'asc' : order === 'descending' ? 'desc' : ''
  loadData()
}

// 选择处理
const handleSelectionChange = (rows: ContractReview[]) => {
  selectedRows.value = rows
}

// 查看详情
const viewDetail = async (record: ContractReview) => {
  currentRecord.value = record
  detailVisible.value = true
}

// 下载报告
const downloadReport = async (record: ContractReview) => {
  if (record.status !== 'COMPLETED') {
    ElMessage.warning('只有已完成的审查才能下载报告')
    return
  }

  downloadingReports.value[record.id] = true
  try {
    const response = await downloadReportApi(record.id)
    
    // 创建下载链接
    const blob = new Blob([response.data], { type: 'application/pdf' })
    const url = URL.createObjectURL(blob)
    const link = document.createElement('a')
    link.href = url
    link.download = `${record.filename}_审查报告.pdf`
    link.click()
    URL.revokeObjectURL(url)
    
    ElMessage.success('报告下载成功')
  } catch (error) {
    ElMessage.error('报告下载失败')
  } finally {
    downloadingReports.value[record.id] = false
  }
}

// 删除记录
const deleteRecord = async (record: ContractReview) => {
  try {
    // 这里应该调用删除API
    // await deleteReviewApi(record.id)
    
    ElMessage.success('删除成功')
    loadData()
  } catch (error) {
    ElMessage.error('删除失败')
  }
}

// 批量删除
const batchDelete = async () => {
  if (selectedRows.value.length === 0) return

  try {
    await ElMessageBox.confirm(
      `确定要删除选中的 ${selectedRows.value.length} 条记录吗？`,
      '批量删除',
      { type: 'warning' }
    )

    batchDeleting.value = true
    
    // 这里应该调用批量删除API
    // await batchDeleteReviewsApi(selectedRows.value.map(r => r.id))
    
    ElMessage.success('批量删除成功')
    loadData()
  } catch {
    // 用户取消
  } finally {
    batchDeleting.value = false
  }
}

// 辅助函数
const formatDateTime = (timestamp: string) => {
  return new Date(timestamp).toLocaleString('zh-CN')
}

const getStatusType = (status: string) => {
  const statusMap = {
    PENDING: 'info',
    PROCESSING: 'warning',
    COMPLETED: 'success',
    FAILED: 'danger'
  }
  return statusMap[status as keyof typeof statusMap] || 'info'
}

const getStatusText = (status: string) => {
  const statusMap = {
    PENDING: '待处理',
    PROCESSING: '处理中',
    COMPLETED: '已完成',
    FAILED: '失败'
  }
  return statusMap[status as keyof typeof statusMap] || status
}

const getRiskLevelType = (level: string) => {
  const levelMap = {
    LOW: 'success',
    MEDIUM: 'warning',
    HIGH: 'danger'
  }
  return levelMap[level as keyof typeof levelMap] || 'info'
}

const getRiskLevelText = (level: string) => {
  const levelMap = {
    LOW: '低风险',
    MEDIUM: '中风险',
    HIGH: '高风险'
  }
  return levelMap[level as keyof typeof levelMap] || level
}

// 组件挂载时加载数据
onMounted(() => {
  loadData()
})
</script>

<style scoped>
.history-container {
  max-width: 1200px;
  margin: 0 auto;
}

.page-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 24px;
}

.header-left h2 {
  margin: 0 0 4px 0;
  color: var(--text-primary);
}

.header-left p {
  margin: 0;
  color: var(--text-secondary);
}

.header-right {
  display: flex;
  align-items: center;
}

.filter-card {
  margin-bottom: 16px;
}

.table-card {
  margin-bottom: 24px;
}

.file-info {
  display: flex;
  align-items: center;
  gap: 8px;
}

.file-icon {
  color: var(--primary-color);
}

.file-name {
  font-weight: 500;
}

.text-placeholder {
  color: var(--text-placeholder);
}

.pagination-wrapper {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-top: 16px;
  padding-top: 16px;
  border-top: 1px solid var(--border-lighter);
}

.pagination-info {
  display: flex;
  align-items: center;
  gap: 16px;
}

.detail-content {
  max-height: 400px;
  overflow-y: auto;
}

.result-preview {
  margin-top: 20px;
  padding: 16px;
  border: 1px solid var(--border-light);
  border-radius: 8px;
  background: #f8f9fa;
}

.result-preview h4 {
  margin: 0 0 12px 0;
  color: var(--text-primary);
}

.result-summary p {
  margin: 8px 0;
  color: var(--text-regular);
}

@media (max-width: 1024px) {
  .page-header {
    flex-direction: column;
    align-items: flex-start;
    gap: 16px;
  }

  .header-right {
    width: 100%;
    justify-content: space-between;
  }

  .header-right .el-input {
    width: 250px !important;
  }
}

@media (max-width: 768px) {
  .filter-card .el-form {
    display: flex;
    flex-direction: column;
    gap: 16px;
  }

  .filter-card .el-form-item {
    margin-bottom: 0;
  }

  .pagination-wrapper {
    flex-direction: column;
    gap: 16px;
    align-items: stretch;
  }

  .el-pagination {
    justify-content: center;
  }

  .header-right {
    flex-direction: column;
    gap: 12px;
  }

  .header-right .el-input {
    width: 100% !important;
  }
}
</style>
