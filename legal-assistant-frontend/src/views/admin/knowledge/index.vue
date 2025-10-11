<template>
  <div class="knowledge-container">
    <el-card shadow="never">
      <template #header>
        <div class="page-header">
          <div class="header-left">
            <h3>📚 知识库管理</h3>
            <p class="header-subtitle">管理法律知识库文档和向量数据</p>
          </div>
          <div class="header-right">
            <el-button type="primary" @click="showUploadDialog = true">
              <el-icon><Upload /></el-icon>
              上传文档
            </el-button>
            <el-button type="warning" @click="rebuildIndex">
              <el-icon><Refresh /></el-icon>
              重建索引
            </el-button>
            <el-button @click="refreshList">
              <el-icon><Refresh /></el-icon>
              刷新
            </el-button>
          </div>
        </div>
      </template>
      
      <!-- 统计信息 -->
      <div class="stats-section">
        <el-row :gutter="20">
          <el-col :xs="24" :sm="6">
            <div class="stat-card">
              <div class="stat-icon documents">
                <el-icon size="24"><Document /></el-icon>
              </div>
              <div class="stat-info">
                <div class="stat-number">{{ stats.totalDocuments }}</div>
                <div class="stat-label">总文档数</div>
              </div>
            </div>
          </el-col>
          <el-col :xs="24" :sm="6">
            <div class="stat-card">
              <div class="stat-icon chunks">
                <el-icon size="24"><Menu /></el-icon>
              </div>
              <div class="stat-info">
                <div class="stat-number">{{ stats.totalChunks }}</div>
                <div class="stat-label">向量块数</div>
              </div>
            </div>
          </el-col>
          <el-col :xs="24" :sm="6">
            <div class="stat-card">
              <div class="stat-icon size">
                <el-icon size="24"><Folder /></el-icon>
              </div>
              <div class="stat-info">
                <div class="stat-number">{{ formatFileSize(stats.totalSize) }}</div>
                <div class="stat-label">总大小</div>
              </div>
            </div>
          </el-col>
          <el-col :xs="24" :sm="6">
            <div class="stat-card">
              <div class="stat-icon updated">
                <el-icon size="24"><Clock /></el-icon>
              </div>
              <div class="stat-info">
                <div class="stat-number">{{ stats.lastUpdated }}</div>
                <div class="stat-label">最后更新</div>
              </div>
            </div>
          </el-col>
        </el-row>
      </div>
      
      <!-- 搜索和筛选 -->
      <div class="search-section">
        <el-row :gutter="20">
          <el-col :xs="24" :sm="12" :md="8">
            <el-input
              v-model="searchQuery"
              placeholder="搜索文档名称..."
              clearable
              @input="handleSearch"
            >
              <template #prefix>
                <el-icon><Search /></el-icon>
              </template>
            </el-input>
          </el-col>
          <el-col :xs="24" :sm="12" :md="6">
            <el-select
              v-model="categoryFilter"
              placeholder="分类筛选"
              clearable
              @change="handleFilter"
            >
              <el-option label="全部" value="" />
              <el-option label="法律法规" value="law" />
              <el-option label="合同模板" value="contract" />
              <el-option label="案例分析" value="case" />
              <el-option label="其他" value="other" />
            </el-select>
          </el-col>
          <el-col :xs="24" :sm="12" :md="8">
            <el-date-picker
              v-model="dateRange"
              type="daterange"
              range-separator="至"
              start-placeholder="开始日期"
              end-placeholder="结束日期"
              format="YYYY-MM-DD"
              value-format="YYYY-MM-DD"
              :unlink-panels="true"
              @change="handleFilter"
            />
          </el-col>
        </el-row>
      </div>
      
      <!-- 文档表格 -->
      <div class="table-section">
        <el-table
          ref="tableRef"
          v-loading="loading"
          :data="filteredDocuments"
          stripe
          @selection-change="handleSelectionChange"
          @sort-change="handleSortChange"
        >
          <el-table-column type="selection" width="55" />
          
          <el-table-column prop="filename" label="文档名称" min-width="200">
            <template #default="{ row }">
              <div class="document-info">
                <el-icon class="file-icon" :color="getFileIconColor(row.filename)">
                  <Document />
                </el-icon>
                <div class="document-details">
                  <div class="filename" :title="row.filename">{{ removeHashPrefix(row.filename) }}</div>
                  <div class="description" v-if="row.description">{{ row.description }}</div>
                </div>
              </div>
            </template>
          </el-table-column>
          
          <el-table-column prop="category" label="分类" width="100" align="center">
            <template #default="{ row }">
              <el-tag :type="getCategoryType(row.category)" size="small">
                {{ getCategoryText(row.category) }}
              </el-tag>
            </template>
          </el-table-column>
          
          <el-table-column prop="chunksCount" label="向量块数" width="120" align="center" sortable="custom">
            <template #default="{ row }">
              <span class="chunks-count">{{ row.chunksCount }}</span>
            </template>
          </el-table-column>
          
          <el-table-column prop="size" label="文件大小" width="100" align="center" sortable="custom">
            <template #default="{ row }">
              {{ formatFileSize(row.size) }}
            </template>
          </el-table-column>
          
          <el-table-column
            prop="uploadedAt"
            label="上传时间"
            width="160"
            sortable="custom"
          >
            <template #default="{ row }">
              {{ formatDateTime(row.uploadedAt) }}
            </template>
          </el-table-column>
          
          <el-table-column label="操作" width="250" align="center" fixed="right">
            <template #default="{ row }">
              <div class="action-buttons">
                <el-button
                  type="primary"
                  size="small"
                  @click="viewDocument(row)"
                >
                  查看
                </el-button>
                <el-button
                  type="warning"
                  size="small"
                  @click="reprocessDocument(row)"
                >
                  重新处理
                </el-button>
                <el-button
                  type="danger"
                  size="small"
                  @click="deleteDocument(row)"
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
    
    <!-- 上传文档对话框 -->
    <el-dialog
      v-model="showUploadDialog"
      title="上传知识库文档"
      width="600px"
      :close-on-click-modal="false"
      @open="handleDialogOpen"
    >
      <el-form
        ref="uploadFormRef"
        :model="uploadForm"
        :rules="uploadRules"
        label-width="100px"
      >
        <el-form-item label="文档分类" prop="category">
          <el-select v-model="uploadForm.category" style="width: 100%">
            <el-option label="法律法规" value="law" />
            <el-option label="合同模板" value="contract" />
            <el-option label="案例分析" value="case" />
            <el-option label="其他" value="other" />
          </el-select>
        </el-form-item>
        
        <el-form-item label="文档描述" prop="description">
          <el-input
            v-model="uploadForm.description"
            type="textarea"
            :rows="3"
            placeholder="请输入文档描述（可选）"
          />
        </el-form-item>
        
        <el-form-item label="文档文件" prop="files">
          <el-upload
            ref="uploadRef"
            class="upload-area"
            drag
            multiple
            :auto-upload="false"
            :on-change="handleFileChange"
            :file-list="fileList"
            accept=".pdf,.doc,.docx,.txt"
          >
            <el-icon class="el-icon--upload"><UploadFilled /></el-icon>
            <div class="el-upload__text">
              将文件拖拽到此处，或<em>点击上传</em>
            </div>
            <template #tip>
              <div class="el-upload__tip">
                支持 PDF、DOC、DOCX、TXT 格式，单个文件不超过 50MB
              </div>
            </template>
          </el-upload>
        </el-form-item>
      </el-form>
      
      <!-- 上传进度 -->
      <div v-if="uploadProgress.length > 0" class="upload-progress">
        <h4>上传进度</h4>
        <div
          v-for="(progress, index) in uploadProgress"
          :key="index"
          class="progress-item"
        >
          <div class="progress-info">
            <span class="filename">{{ progress.filename }}</span>
            <span class="percentage">{{ progress.percentage }}%</span>
          </div>
          <el-progress :percentage="progress.percentage" :status="progress.status" />
        </div>
      </div>
      
      <template #footer>
        <el-button @click="showUploadDialog = false">取消</el-button>
        <el-button type="primary" :loading="uploadLoading" @click="handleUploadSubmit">
          开始上传
        </el-button>
      </template>
    </el-dialog>
    
    <!-- 文档查看对话框 -->
    <el-dialog
      v-model="showViewDialog"
      :title="`文档详情 - ${selectedDocument?.filename}`"
      width="80%"
      :close-on-click-modal="false"
    >
      <div v-if="selectedDocument" class="document-content">
        <el-descriptions :column="2" border>
          <el-descriptions-item label="文档名称">
            {{ selectedDocument.filename }}
          </el-descriptions-item>
          <el-descriptions-item label="分类">
            <el-tag :type="getCategoryType(selectedDocument.category)" size="small">
              {{ getCategoryText(selectedDocument.category) }}
            </el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="文件大小">
            {{ formatFileSize(selectedDocument.size) }}
          </el-descriptions-item>
          <el-descriptions-item label="向量块数">
            {{ selectedDocument.chunksCount }}
          </el-descriptions-item>
          <el-descriptions-item label="上传时间">
            {{ formatDateTime(selectedDocument.uploadedAt) }}
          </el-descriptions-item>
          <el-descriptions-item label="文档ID">
            {{ selectedDocument.id }}
          </el-descriptions-item>
          <el-descriptions-item label="描述" span="2">
            {{ selectedDocument.description || '无描述' }}
          </el-descriptions-item>
        </el-descriptions>
        
        <!-- 向量块信息 -->
        <div class="chunks-section">
          <div class="chunks-header">
            <h4>向量块信息</h4>
            <el-tag v-if="documentChunks.length > 0" type="info" size="small">
              共 {{ documentChunks.length }} 个向量块
            </el-tag>
          </div>
          
          <div v-if="documentChunks.length === 0" class="empty-chunks">
            <el-empty description="暂无向量块数据" />
          </div>
          
          <el-table 
            v-else 
            :data="documentChunks" 
            stripe 
            max-height="400"
            style="margin-top: 10px"
          >
            <el-table-column prop="index" label="序号" width="80" align="center" />
            <el-table-column prop="content" label="内容预览" min-width="350">
              <template #default="{ row }">
                <div class="chunk-content" :title="row.content">
                  {{ row.content.length > 150 ? row.content.substring(0, 150) + '...' : row.content }}
                </div>
              </template>
            </el-table-column>
            <el-table-column prop="tokens" label="Token数" width="100" align="center">
              <template #default="{ row }">
                <el-tag type="success" size="small">{{ row.tokens }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column label="内容长度" width="100" align="center">
              <template #default="{ row }">
                <span class="text-gray">{{ row.content.length }}</span>
              </template>
            </el-table-column>
          </el-table>
        </div>
      </div>
      
      <template #footer>
        <el-button @click="showViewDialog = false">关闭</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { ElMessage, ElMessageBox, type FormInstance, type FormRules, type UploadInstance } from 'element-plus'
import {
  Upload,
  Refresh,
  Search,
  Document,
  Menu,
  Folder,
  Clock,
  Delete,
  UploadFilled
} from '@element-plus/icons-vue'
import { useUserStore } from '@/store/modules/user'
import type { KnowledgeDocument } from '@/types/api'
import { getDocumentsApi, deleteDocumentApi, rebuildIndexApi, reprocessDocumentApi, getDocumentChunksApi } from '@/api/knowledgeBaseService'

// 类型定义
interface DocumentStats {
  totalDocuments: number
  totalChunks: number
  totalSize: number
  lastUpdated: string
}

interface UploadProgress {
  filename: string
  percentage: number
  status: 'success' | 'exception' | 'warning' | ''
}

interface DocumentChunk {
  index: number
  content: string
  tokens: number
  similarity: number
}

const userStore = useUserStore()

// 响应式数据
const loading = ref(false)
const documentList = ref<KnowledgeDocument[]>([])
const selectedRows = ref<KnowledgeDocument[]>([])
const searchQuery = ref('')
const categoryFilter = ref('')
const dateRange = ref<[string, string] | null>(null)
const currentPage = ref(1)
const pageSize = ref(20)
const total = ref(0)
const sortField = ref('')
const sortOrder = ref('')

const showUploadDialog = ref(false)
const showViewDialog = ref(false)
const uploadLoading = ref(false)
const selectedDocument = ref<KnowledgeDocument | null>(null)
const documentChunks = ref<DocumentChunk[]>([])

const uploadFormRef = ref<FormInstance>()
const uploadRef = ref<UploadInstance>()
const tableRef = ref()

const stats = ref<DocumentStats>({
  totalDocuments: 0,
  totalChunks: 0,
  totalSize: 0,
  lastUpdated: ''
})

const uploadForm = ref({
  category: 'law',
  description: ''
})

const fileList = ref([])
const uploadProgress = ref<UploadProgress[]>([])

// 计算属性
const uploadAction = computed(() => '/api/v1/knowledge-base/documents/upload-single')

const uploadHeaders = computed(() => ({
  'Authorization': `Bearer ${userStore.token}`
}))

const filteredDocuments = computed(() => {
  let list = [...documentList.value]
  
  // 搜索过滤
  if (searchQuery.value) {
    const query = searchQuery.value.toLowerCase()
    list = list.filter(doc => 
      doc.filename.toLowerCase().includes(query) ||
      (doc.description && doc.description.toLowerCase().includes(query))
    )
  }
  
  // 分类过滤
  if (categoryFilter.value) {
    list = list.filter(doc => doc.category === categoryFilter.value)
  }
  
  // 日期范围过滤
  if (dateRange.value && dateRange.value.length === 2) {
    const [startDate, endDate] = dateRange.value
    list = list.filter(doc => {
      const docDate = doc.uploadedAt.split('T')[0]
      return docDate >= startDate && docDate <= endDate
    })
  }
  
  // 排序
  if (sortField.value) {
    list.sort((a, b) => {
      const aVal = a[sortField.value as keyof KnowledgeDocument]
      const bVal = b[sortField.value as keyof KnowledgeDocument]
      
      if (sortOrder.value === 'ascending') {
        return aVal > bVal ? 1 : -1
      } else {
        return aVal < bVal ? 1 : -1
      }
    })
  }
  
  return list
})

// 表单验证规则
const uploadRules: FormRules = {
  category: [
    { required: true, message: '请选择文档分类', trigger: 'change' }
  ]
}

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

// 移除文件名中的哈希值前缀
const removeHashPrefix = (filename: string) => {
  // 匹配 64位十六进制哈希值_ 的模式
  const hashPattern = /^[a-f0-9]{64}_/
  return filename.replace(hashPattern, '')
}

const formatFileSize = (bytes: number) => {
  if (bytes === 0) return '0 B'
  const k = 1024
  const sizes = ['B', 'KB', 'MB', 'GB']
  const i = Math.floor(Math.log(bytes) / Math.log(k))
  return parseFloat((bytes / Math.pow(k, i)).toFixed(2)) + ' ' + sizes[i]
}

const getFileIconColor = (filename: string) => {
  const ext = filename.split('.').pop()?.toLowerCase()
  const colorMap: Record<string, string> = {
    pdf: '#ff4757',
    doc: '#3742fa',
    docx: '#3742fa',
    txt: '#2ed573'
  }
  return colorMap[ext || ''] || '#747d8c'
}

const getCategoryType = (category: string) => {
  const typeMap: Record<string, string> = {
    law: 'danger',
    contract: 'primary',
    case: 'warning',
    other: 'info'
  }
  return typeMap[category] || 'info'
}

const getCategoryText = (category: string) => {
  const textMap: Record<string, string> = {
    law: '法律法规',
    contract: '合同模板',
    case: '案例分析',
    other: '其他'
  }
  return textMap[category] || category
}

// 数据获取
const fetchDocumentList = async () => {
  loading.value = true
  try {
    // 调用真实API获取文档列表
    const response = await getDocumentsApi({
      page: currentPage.value - 1, // 后端页码从0开始
      size: pageSize.value,
      category: categoryFilter.value || undefined
    })
    
    if (response.data && response.data.data) {
      documentList.value = response.data.data.content || []
      total.value = response.data.data.totalElements || 0
      
      // 更新统计信息
      stats.value = {
        totalDocuments: total.value,
        totalChunks: documentList.value.reduce((sum, doc) => sum + (doc.chunksCount || 0), 0),
        totalSize: documentList.value.reduce((sum, doc) => sum + (doc.size || 0), 0),
        lastUpdated: documentList.value.length > 0 
          ? new Date().toLocaleDateString('zh-CN') 
          : '-'
      }
    } else {
      documentList.value = []
      total.value = 0
      stats.value = {
        totalDocuments: 0,
        totalChunks: 0,
        totalSize: 0,
        lastUpdated: '-'
      }
    }
  } catch (error) {
    console.error('Failed to fetch document list:', error)
    documentList.value = []
    total.value = 0
    stats.value = {
      totalDocuments: 0,
      totalChunks: 0,
      totalSize: 0,
      lastUpdated: '-'
    }
  } finally {
    loading.value = false
  }
}

// 事件处理
const refreshList = () => {
  fetchDocumentList()
}

const handleSearch = () => {
  // 搜索逻辑在计算属性中处理
}

const handleFilter = () => {
  // 重新从后端获取数据，应用分类筛选
  currentPage.value = 1
  fetchDocumentList()
}

const handleSortChange = ({ prop, order }: { prop: string; order: string }) => {
  sortField.value = prop
  sortOrder.value = order
}

const handleSizeChange = (size: number) => {
  pageSize.value = size
  currentPage.value = 1
  fetchDocumentList()
}

const handleCurrentChange = (page: number) => {
  currentPage.value = page
  fetchDocumentList()
}

const handleSelectionChange = (selection: KnowledgeDocument[]) => {
  selectedRows.value = selection
}

const viewDocument = async (document: KnowledgeDocument) => {
  selectedDocument.value = document
  
  // 清空之前的块信息
  documentChunks.value = []
  
  // 显示对话框
  showViewDialog.value = true
  
  // 加载文档的向量块信息
  loading.value = true
  try {
    const response = await getDocumentChunksApi(document.id)
    
    if (response.data && response.data.success && response.data.data) {
      // 转换为前端需要的格式
      documentChunks.value = response.data.data.map((chunk: any) => ({
        index: chunk.index + 1, // 前端从1开始显示
        content: chunk.content,
        tokens: chunk.tokens,
        similarity: chunk.similarity || 1.0
      }))
      
      ElMessage.success(`成功加载 ${documentChunks.value.length} 个向量块`)
    } else {
      ElMessage.warning('未能获取文档向量块信息')
    }
  } catch (error: any) {
    console.error('Failed to fetch document chunks:', error)
    const errorMessage = error.response?.data?.message || error.message || '获取文档向量块失败'
    ElMessage.error(errorMessage)
  } finally {
    loading.value = false
  }
}

const reprocessDocument = async (doc: KnowledgeDocument) => {
  try {
    await ElMessageBox.confirm(
      `确定要重新处理文档"${removeHashPrefix(doc.filename)}"吗？这将重新生成向量索引。`,
      '重新处理确认',
      {
        confirmButtonText: '确定',
        cancelButtonText: '取消',
        type: 'warning'
      }
    )
    
    loading.value = true
    try {
      const response = await reprocessDocumentApi(doc.id)
      
      if (response.data && response.data.success) {
        ElMessage.success({
          message: '文档重新处理任务已启动，请稍后刷新查看结果',
          duration: 3000
        })
        // 刷新列表
        setTimeout(() => {
          fetchDocumentList()
        }, 2000)
      } else {
        ElMessage.error(response.data?.message || '重新处理文档失败')
      }
    } catch (error: any) {
      console.error('Failed to reprocess document:', error)
      const errorMessage = error.response?.data?.message || error.message || '重新处理文档失败'
      ElMessage.error(errorMessage)
    } finally {
      loading.value = false
    }
  } catch {
    // 用户取消操作
  }
}

const deleteDocument = async (doc: KnowledgeDocument) => {
  try {
    await ElMessageBox.confirm(
      `确定要删除文档"${removeHashPrefix(doc.filename)}"吗？此操作不可恢复。`,
      '删除确认',
      {
        confirmButtonText: '确定删除',
        cancelButtonText: '取消',
        type: 'warning'
      }
    )
    
    // 调用真实API删除文档
    const response = await deleteDocumentApi(doc.id)
    
    if (response.data && response.data.success) {
      ElMessage.success('文档删除成功')
      // 刷新列表
      await fetchDocumentList()
    } else {
      ElMessage.error(response.data?.message || '删除文档失败')
    }
  } catch (error: any) {
    if (error !== 'cancel') {
      console.error('Failed to delete document:', error)
      const errorMessage = error.response?.data?.message || error.message || '删除文档失败'
      ElMessage.error(errorMessage)
    }
  }
}

const batchDelete = async () => {
  if (selectedRows.value.length === 0) {
    ElMessage.warning('请先选择要删除的文档')
    return
  }
  
  try {
    await ElMessageBox.confirm(
      `确定要删除选中的 ${selectedRows.value.length} 个文档吗？此操作不可恢复。`,
      '批量删除确认',
      {
        confirmButtonText: '确定删除',
        cancelButtonText: '取消',
        type: 'warning'
      }
    )
    
    // 调用真实API批量删除文档
    const deletePromises = selectedRows.value.map(doc => deleteDocumentApi(doc.id))
    const results = await Promise.allSettled(deletePromises)
    
    // 统计成功和失败的数量
    const successCount = results.filter(r => r.status === 'fulfilled').length
    const failedCount = results.filter(r => r.status === 'rejected').length
    
    // 清空选择
    tableRef.value?.clearSelection()
    
    if (failedCount === 0) {
      ElMessage.success(`成功删除 ${successCount} 个文档`)
    } else {
      ElMessage.warning(`删除完成：成功 ${successCount} 个，失败 ${failedCount} 个`)
    }
    
    // 刷新列表
    await fetchDocumentList()
  } catch (error: any) {
    if (error !== 'cancel') {
      console.error('Failed to batch delete documents:', error)
      ElMessage.error('批量删除失败')
    }
  }
}

const rebuildIndex = async () => {
  try {
    await ElMessageBox.confirm(
      '确定要重建知识库索引吗？这将重新处理所有文档的向量索引，可能需要较长时间。',
      '重建索引确认',
      {
        confirmButtonText: '确定',
        cancelButtonText: '取消',
        type: 'warning'
      }
    )
    
    loading.value = true
    try {
      const response = await rebuildIndexApi()
      
      if (response.data && response.data.success) {
        ElMessage.success({
          message: '知识库索引重建任务已启动，请稍后查看结果',
          duration: 3000
        })
      } else {
        ElMessage.error(response.data?.message || '启动索引重建任务失败')
      }
    } catch (error: any) {
      console.error('Failed to rebuild index:', error)
      const errorMessage = error.response?.data?.message || error.message || '启动索引重建任务失败'
      ElMessage.error(errorMessage)
    } finally {
      loading.value = false
    }
  } catch {
    // 用户取消操作
  }
}

// 打开上传对话框时的处理
const handleDialogOpen = () => {
  // 清空上次的文件列表和进度
  fileList.value = []
  uploadProgress.value = []
  // 重置表单
  uploadForm.value = {
    category: 'law',
    description: ''
  }
  uploadFormRef.value?.clearValidate()
}

// 上传相关函数
const handleFileChange = (file: any, fileListParam: any) => {
  // 验证文件
  const isValidType = ['application/pdf', 'application/msword', 'application/vnd.openxmlformats-officedocument.wordprocessingml.document', 'text/plain'].includes(file.raw.type)
  const isLt50M = file.raw.size / 1024 / 1024 < 50

  if (!isValidType) {
    ElMessage.error('只支持 PDF、DOC、DOCX、TXT 格式的文件!')
    uploadRef.value?.handleRemove(file)
    return false
  }
  if (!isLt50M) {
    ElMessage.error('文件大小不能超过 50MB!')
    uploadRef.value?.handleRemove(file)
    return false
  }
  
  fileList.value = fileListParam
}

const handleUploadSubmit = async () => {
  if (!uploadFormRef.value) return
  
  try {
    await uploadFormRef.value.validate()
    
    if (fileList.value.length === 0) {
      ElMessage.warning('请选择要上传的文件')
      return
    }
    
    uploadLoading.value = true
    uploadProgress.value = []
    
    // 手动上传每个文件
    const uploadPromises = fileList.value.map(async (fileItem: any) => {
      const formData = new FormData()
      formData.append('file', fileItem.raw)
      formData.append('category', uploadForm.value.category)
      if (uploadForm.value.description) {
        formData.append('description', uploadForm.value.description)
      }
      
      // 添加进度跟踪
      uploadProgress.value.push({
        filename: fileItem.name,
        percentage: 0,
        status: ''
      })
      
      try {
        const response = await fetch(uploadAction.value, {
          method: 'POST',
          headers: {
            'Authorization': uploadHeaders.value.Authorization
          },
          body: formData
        })
        
        const result = await response.json()
        
        // 更新进度
        const progressIndex = uploadProgress.value.findIndex(p => p.filename === fileItem.name)
        if (progressIndex > -1) {
          uploadProgress.value[progressIndex].percentage = 100
          uploadProgress.value[progressIndex].status = result.success ? 'success' : 'exception'
        }
        
        if (result.success) {
          ElMessage.success(`文档 ${fileItem.name} 上传成功`)
          return { success: true, filename: fileItem.name }
        } else {
          ElMessage.error(`文档 ${fileItem.name} 上传失败: ${result.message}`)
          return { success: false, filename: fileItem.name }
        }
      } catch (error: any) {
        // 更新进度为失败
        const progressIndex = uploadProgress.value.findIndex(p => p.filename === fileItem.name)
        if (progressIndex > -1) {
          uploadProgress.value[progressIndex].status = 'exception'
        }
        
        ElMessage.error(`文档 ${fileItem.name} 上传失败`)
        return { success: false, filename: fileItem.name }
      }
    })
    
    // 等待所有上传完成
    const results = await Promise.all(uploadPromises)
    const successCount = results.filter(r => r.success).length
    const failedCount = results.filter(r => !r.success).length
    
    uploadLoading.value = false
    
    if (failedCount === 0) {
      ElMessage.success(`所有文档上传成功，共 ${successCount} 个`)
      // 关闭对话框
      showUploadDialog.value = false
      
      // 重置表单
      uploadForm.value = {
        category: 'law',
        description: ''
      }
      fileList.value = []
      uploadProgress.value = []
      
      // 刷新列表
      refreshList()
    } else {
      ElMessage.warning(`上传完成：成功 ${successCount} 个，失败 ${failedCount} 个`)
    }
  } catch (error) {
    console.error('Upload failed:', error)
    uploadLoading.value = false
    ElMessage.error('上传失败')
  }
}

// 组件挂载时获取数据
onMounted(() => {
  fetchDocumentList()
})
</script>

<style scoped>
.knowledge-container {
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

.header-right {
  display: flex;
  gap: 12px;
}

.stats-section {
  margin-bottom: 20px;
}

.stat-card {
  display: flex;
  align-items: center;
  padding: 20px;
  background-color: #f8f9fa;
  border-radius: 8px;
  height: 100px;
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

.stat-icon.documents {
  background-color: #e3f2fd;
  color: #1976d2;
}

.stat-icon.chunks {
  background-color: #e8f5e8;
  color: #388e3c;
}

.stat-icon.size {
  background-color: #fff3e0;
  color: #f57c00;
}

.stat-icon.updated {
  background-color: #f3e5f5;
  color: #7b1fa2;
}

.stat-info {
  flex: 1;
}

.stat-number {
  font-size: 24px;
  font-weight: bold;
  color: #2c3e50;
  margin-bottom: 4px;
}

.stat-label {
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

.document-info {
  display: flex;
  align-items: center;
  gap: 12px;
}

.file-icon {
  font-size: 20px;
}

.document-details {
  display: flex;
  flex-direction: column;
}

.filename {
  font-weight: 500;
  color: #2c3e50;
}

.description {
  font-size: 12px;
  color: #7f8c8d;
  margin-top: 2px;
}

.pagination-section {
  margin-top: 20px;
  text-align: right;
}

.upload-area {
  width: 100%;
}

.upload-progress {
  margin-top: 20px;
}

.upload-progress h4 {
  margin: 0 0 15px 0;
  color: #2c3e50;
}

.progress-item {
  margin-bottom: 15px;
}

.progress-info {
  display: flex;
  justify-content: space-between;
  margin-bottom: 5px;
}

.progress-info .filename {
  font-weight: 500;
}

.progress-info .percentage {
  color: #409EFF;
}

.document-content {
  max-height: 600px;
  overflow-y: auto;
}

.chunks-section {
  margin-top: 30px;
}

.chunks-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 15px;
}

.chunks-section h4 {
  margin: 0;
  color: #2c3e50;
}

.empty-chunks {
  padding: 40px 0;
  text-align: center;
}

.chunk-content {
  line-height: 1.6;
  color: #5a6c7d;
  white-space: pre-wrap;
  word-break: break-word;
}

.text-gray {
  color: #909399;
  font-size: 13px;
}

.chunks-count {
  font-weight: 500;
  color: #409EFF;
}

.action-buttons {
  display: flex;
  gap: 8px;
  justify-content: center;
  flex-wrap: wrap;
}

.action-buttons .el-button {
  margin: 0;
}

/* 响应式设计 */
@media (max-width: 768px) {
  .page-header {
    flex-direction: column;
    gap: 15px;
  }
  
  .header-right {
    width: 100%;
    flex-wrap: wrap;
  }
  
  .stats-section .el-col {
    margin-bottom: 15px;
  }
  
  .stat-card {
    padding: 15px;
    height: 80px;
  }
  
  .stat-number {
    font-size: 20px;
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
}

@media (max-width: 480px) {
  .search-section {
    padding: 15px;
  }
  
  .header-right .el-button {
    flex: 1;
    font-size: 12px;
  }
  
  .document-info {
    flex-direction: column;
    align-items: flex-start;
    gap: 8px;
  }
  
  .el-table :deep(.el-table__cell) {
    padding: 8px 4px;
  }
}
</style>