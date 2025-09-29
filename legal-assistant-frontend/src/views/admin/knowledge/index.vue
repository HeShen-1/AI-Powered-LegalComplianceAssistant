<template>
  <div class="admin-knowledge-container">
    <el-card shadow="never">
      <template #header>
        <div class="card-header">
          <div>
            <h3>知识库管理</h3>
            <p>管理法律知识库文档，支持批量上传和分类管理</p>
          </div>
          <div class="header-actions">
            <el-button @click="showUploadDialog = true">
              <el-icon><Upload /></el-icon>
              上传文档
            </el-button>
            <el-button type="primary" @click="showBatchUpload = true">
              <el-icon><FolderAdd /></el-icon>
              批量上传
            </el-button>
          </div>
        </div>
      </template>

      <!-- 统计信息 -->
      <div class="stats-section">
        <div class="stat-item">
          <div class="stat-icon">
            <el-icon><Document /></el-icon>
          </div>
          <div class="stat-info">
            <div class="stat-number">{{ stats.totalDocs }}</div>
            <div class="stat-label">总文档数</div>
          </div>
        </div>
        
        <div class="stat-item">
          <div class="stat-icon">
            <el-icon><Collection /></el-icon>
          </div>
          <div class="stat-info">
            <div class="stat-number">{{ stats.totalChunks }}</div>
            <div class="stat-label">知识片段</div>
          </div>
        </div>
        
        <div class="stat-item">
          <div class="stat-icon">
            <el-icon><Folder /></el-icon>
          </div>
          <div class="stat-info">
            <div class="stat-number">{{ stats.categories }}</div>
            <div class="stat-label">分类数量</div>
          </div>
        </div>
      </div>

      <!-- 搜索和筛选 -->
      <div class="filter-section">
        <el-form inline>
          <el-form-item>
            <el-input
              v-model="searchKeyword"
              placeholder="搜索文档名称..."
              :prefix-icon="Search"
              clearable
              @input="handleSearch"
            />
          </el-form-item>
          <el-form-item>
            <el-select v-model="filters.category" placeholder="选择分类" clearable>
              <el-option label="全部分类" value="" />
              <el-option
                v-for="category in categories"
                :key="category"
                :label="category"
                :value="category"
              />
            </el-select>
          </el-form-item>
          <el-form-item>
            <el-button @click="refreshData" :loading="loading">
              <el-icon><Refresh /></el-icon>
              刷新
            </el-button>
          </el-form-item>
        </el-form>
      </div>

      <!-- 文档表格 -->
      <el-table
        :data="documents"
        :loading="loading"
        stripe
        @selection-change="handleSelectionChange"
      >
        <el-table-column type="selection" width="55" />
        
        <el-table-column prop="filename" label="文档名称" min-width="200">
          <template #default="{ row }">
            <div class="doc-info">
              <el-icon class="doc-icon"><Document /></el-icon>
              <span class="doc-name">{{ row.filename }}</span>
            </div>
          </template>
        </el-table-column>
        
        <el-table-column prop="category" label="分类" width="120">
          <template #default="{ row }">
            <el-tag v-if="row.category" type="info" size="small">
              {{ row.category }}
            </el-tag>
            <span v-else class="text-placeholder">未分类</span>
          </template>
        </el-table-column>
        
        <el-table-column prop="chunksCount" label="知识片段" width="100" align="center">
          <template #default="{ row }">
            <el-tag type="success" size="small">
              {{ row.chunksCount }}
            </el-tag>
          </template>
        </el-table-column>
        
        <el-table-column prop="description" label="描述" min-width="200" show-overflow-tooltip>
          <template #default="{ row }">
            <span v-if="row.description">{{ row.description }}</span>
            <span v-else class="text-placeholder">暂无描述</span>
          </template>
        </el-table-column>
        
        <el-table-column prop="uploadedAt" label="上传时间" width="180">
          <template #default="{ row }">
            {{ formatDateTime(row.uploadedAt) }}
          </template>
        </el-table-column>
        
        <el-table-column label="操作" width="150" fixed="right">
          <template #default="{ row }">
            <el-button text type="primary" @click="editDocument(row)">
              编辑
            </el-button>
            <el-popconfirm
              title="确定删除这个文档吗？"
              @confirm="deleteDocument(row)"
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
            v-if="selectedDocs.length > 0"
            type="danger"
            @click="batchDelete"
            :loading="batchDeleting"
          >
            批量删除 ({{ selectedDocs.length }})
          </el-button>
        </div>
        
        <el-pagination
          v-model:current-page="pagination.page"
          v-model:page-size="pagination.size"
          :page-sizes="[10, 20, 50]"
          :total="total"
          layout="sizes, prev, pager, next, jumper"
          @size-change="loadDocuments"
          @current-change="loadDocuments"
        />
      </div>
    </el-card>

    <!-- 单文档上传对话框 -->
    <el-dialog v-model="showUploadDialog" title="上传文档" width="500px">
      <el-form :model="uploadForm" label-width="80px">
        <el-form-item label="文档分类">
          <el-select
            v-model="uploadForm.category"
            placeholder="选择或输入新分类"
            filterable
            allow-create
            style="width: 100%"
          >
            <el-option
              v-for="category in categories"
              :key="category"
              :label="category"
              :value="category"
            />
          </el-select>
        </el-form-item>
        
        <el-form-item label="文档描述">
          <el-input
            v-model="uploadForm.description"
            type="textarea"
            :rows="3"
            placeholder="请输入文档描述"
          />
        </el-form-item>
        
        <el-form-item label="选择文件">
          <el-upload
            ref="uploadRef"
            :auto-upload="false"
            :on-change="handleFileSelect"
            :before-upload="beforeUpload"
            accept=".pdf,.docx,.doc,.txt"
            drag
          >
            <el-icon class="el-icon--upload"><UploadFilled /></el-icon>
            <div class="el-upload__text">
              将文件拖拽到此处，或<em>点击选择文件</em>
            </div>
            <template #tip>
              <div class="el-upload__tip">
                支持 PDF、Word、文本格式，单个文件不超过 20MB
              </div>
            </template>
          </el-upload>
        </el-form-item>
      </el-form>
      
      <template #footer>
        <el-button @click="cancelUpload">取消</el-button>
        <el-button
          type="primary"
          @click="startUpload"
          :loading="uploading"
          :disabled="!selectedFile"
        >
          开始上传
        </el-button>
      </template>
    </el-dialog>

    <!-- 批量上传对话框 -->
    <el-dialog v-model="showBatchUpload" title="批量上传" width="600px">
      <el-form :model="batchUploadForm" label-width="80px">
        <el-form-item label="统一分类">
          <el-select
            v-model="batchUploadForm.category"
            placeholder="选择或输入新分类"
            filterable
            allow-create
            style="width: 100%"
          >
            <el-option
              v-for="category in categories"
              :key="category"
              :label="category"
              :value="category"
            />
          </el-select>
        </el-form-item>
        
        <el-form-item label="选择文件">
          <el-upload
            ref="batchUploadRef"
            :auto-upload="false"
            :on-change="handleBatchFileSelect"
            :on-remove="handleBatchFileRemove"
            multiple
            accept=".pdf,.docx,.doc,.txt"
            drag
          >
            <el-icon class="el-icon--upload"><UploadFilled /></el-icon>
            <div class="el-upload__text">
              选择多个文件进行批量上传
            </div>
            <template #tip>
              <div class="el-upload__tip">
                支持选择多个文件，单个文件不超过 20MB
              </div>
            </template>
          </el-upload>
        </el-form-item>
      </el-form>
      
      <!-- 上传进度 -->
      <div v-if="batchProgress.total > 0" class="batch-progress">
        <h4>上传进度</h4>
        <el-progress
          :percentage="Math.round((batchProgress.completed / batchProgress.total) * 100)"
          :status="batchProgress.completed === batchProgress.total ? 'success' : undefined"
        />
        <p>{{ batchProgress.completed }}/{{ batchProgress.total }} 个文件已完成</p>
      </div>
      
      <template #footer>
        <el-button @click="cancelBatchUpload">取消</el-button>
        <el-button
          type="primary"
          @click="startBatchUpload"
          :loading="batchUploading"
          :disabled="selectedFiles.length === 0"
        >
          开始批量上传
        </el-button>
      </template>
    </el-dialog>

    <!-- 编辑文档对话框 -->
    <el-dialog v-model="showEditDialog" title="编辑文档" width="500px">
      <el-form :model="editForm" label-width="80px">
        <el-form-item label="文档名称">
          <el-input v-model="editForm.filename" disabled />
        </el-form-item>
        
        <el-form-item label="文档分类">
          <el-select
            v-model="editForm.category"
            placeholder="选择或输入新分类"
            filterable
            allow-create
            style="width: 100%"
          >
            <el-option
              v-for="category in categories"
              :key="category"
              :label="category"
              :value="category"
            />
          </el-select>
        </el-form-item>
        
        <el-form-item label="文档描述">
          <el-input
            v-model="editForm.description"
            type="textarea"
            :rows="3"
            placeholder="请输入文档描述"
          />
        </el-form-item>
      </el-form>
      
      <template #footer>
        <el-button @click="showEditDialog = false">取消</el-button>
        <el-button type="primary" @click="saveEdit" :loading="saving">
          保存
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import type { UploadInstance, UploadRawFile } from 'element-plus'
import type { KnowledgeDocument } from '@/types/api'
import {
  uploadDocumentApi,
  batchUploadDocumentsApi,
  getDocumentsApi,
  deleteDocumentApi
} from '@/api/knowledgeBaseService'
import {
  Upload,
  FolderAdd,
  Document,
  Collection,
  Folder,
  Search,
  Refresh,
  UploadFilled
} from '@element-plus/icons-vue'

// 组件状态
const loading = ref(false)
const uploading = ref(false)
const batchUploading = ref(false)
const saving = ref(false)
const batchDeleting = ref(false)

const showUploadDialog = ref(false)
const showBatchUpload = ref(false)
const showEditDialog = ref(false)

const uploadRef = ref<UploadInstance>()
const batchUploadRef = ref<UploadInstance>()

// 搜索和筛选
const searchKeyword = ref('')
const filters = reactive({
  category: ''
})

// 分页
const pagination = reactive({
  page: 1,
  size: 10
})

// 数据
const documents = ref<KnowledgeDocument[]>([])
const total = ref(0)
const selectedDocs = ref<KnowledgeDocument[]>([])
const selectedFile = ref<File | null>(null)
const selectedFiles = ref<File[]>([])
const categories = ref<string[]>(['法律法规', '合同模板', '案例分析', '司法解释'])

// 统计信息
const stats = reactive({
  totalDocs: 0,
  totalChunks: 0,
  categories: 0
})

// 表单数据
const uploadForm = reactive({
  category: '',
  description: ''
})

const batchUploadForm = reactive({
  category: ''
})

const editForm = reactive({
  id: '',
  filename: '',
  category: '',
  description: ''
})

// 批量上传进度
const batchProgress = reactive({
  total: 0,
  completed: 0
})

// 加载文档列表
const loadDocuments = async () => {
  loading.value = true
  try {
    const params = {
      page: pagination.page - 1,
      size: pagination.size,
      category: filters.category,
      keyword: searchKeyword.value
    }

    const response = await getDocumentsApi(params)
    if (response.data.success) {
      documents.value = response.data.data
      total.value = response.data.totalElements
      
      // 更新统计信息
      stats.totalDocs = total.value
      stats.totalChunks = documents.value.reduce((sum, doc) => sum + doc.chunksCount, 0)
      stats.categories = new Set(documents.value.map(doc => doc.category).filter(Boolean)).size
    }
  } catch (error) {
    ElMessage.error('加载文档列表失败')
  } finally {
    loading.value = false
  }
}

// 搜索处理
const handleSearch = () => {
  pagination.page = 1
  loadDocuments()
}

// 刷新数据
const refreshData = () => {
  loadDocuments()
}

// 选择变化处理
const handleSelectionChange = (docs: KnowledgeDocument[]) => {
  selectedDocs.value = docs
}

// 文件选择处理
const handleFileSelect = (file: any) => {
  selectedFile.value = file.raw
}

const handleBatchFileSelect = (file: any) => {
  selectedFiles.value.push(file.raw)
}

const handleBatchFileRemove = (file: any) => {
  const index = selectedFiles.value.findIndex(f => f.name === file.name)
  if (index > -1) {
    selectedFiles.value.splice(index, 1)
  }
}

// 文件上传前验证
const beforeUpload = (file: UploadRawFile) => {
  const validTypes = ['application/pdf', 'application/vnd.openxmlformats-officedocument.wordprocessingml.document', 'application/msword', 'text/plain']
  const isValidType = validTypes.includes(file.type)
  const isValidSize = file.size / 1024 / 1024 < 20

  if (!isValidType) {
    ElMessage.error('只支持 PDF、Word、文本格式的文件')
    return false
  }

  if (!isValidSize) {
    ElMessage.error('文件大小不能超过 20MB')
    return false
  }

  return false // 阻止自动上传
}

// 开始单文档上传
const startUpload = async () => {
  if (!selectedFile.value) return

  uploading.value = true
  try {
    const response = await uploadDocumentApi(
      selectedFile.value,
      uploadForm.category,
      uploadForm.description
    )

    if (response.data.success) {
      ElMessage.success('文档上传成功')
      showUploadDialog.value = false
      loadDocuments()
    }
  } catch (error) {
    ElMessage.error('文档上传失败')
  } finally {
    uploading.value = false
  }
}

// 取消上传
const cancelUpload = () => {
  showUploadDialog.value = false
  selectedFile.value = null
  uploadForm.category = ''
  uploadForm.description = ''
  uploadRef.value?.clearFiles()
}

// 开始批量上传
const startBatchUpload = async () => {
  if (selectedFiles.value.length === 0) return

  batchUploading.value = true
  batchProgress.total = selectedFiles.value.length
  batchProgress.completed = 0

  try {
    const response = await batchUploadDocumentsApi(
      selectedFiles.value,
      batchUploadForm.category
    )

    if (response.data.success) {
      batchProgress.completed = response.data.successCount
      ElMessage.success(`批量上传完成，成功 ${response.data.successCount} 个文件`)
      showBatchUpload.value = false
      loadDocuments()
    }
  } catch (error) {
    ElMessage.error('批量上传失败')
  } finally {
    batchUploading.value = false
  }
}

// 取消批量上传
const cancelBatchUpload = () => {
  showBatchUpload.value = false
  selectedFiles.value = []
  batchUploadForm.category = ''
  batchProgress.total = 0
  batchProgress.completed = 0
  batchUploadRef.value?.clearFiles()
}

// 编辑文档
const editDocument = (doc: KnowledgeDocument) => {
  Object.assign(editForm, {
    id: doc.id,
    filename: doc.filename,
    category: doc.category || '',
    description: doc.description || ''
  })
  showEditDialog.value = true
}

// 保存编辑
const saveEdit = async () => {
  saving.value = true
  try {
    // 这里应该调用更新文档信息的API
    ElMessage.success('文档信息更新成功')
    showEditDialog.value = false
    loadDocuments()
  } catch (error) {
    ElMessage.error('更新失败')
  } finally {
    saving.value = false
  }
}

// 删除文档
const deleteDocument = async (doc: KnowledgeDocument) => {
  try {
    await deleteDocumentApi(doc.id)
    ElMessage.success('文档删除成功')
    loadDocuments()
  } catch (error) {
    ElMessage.error('删除失败')
  }
}

// 批量删除
const batchDelete = async () => {
  try {
    await ElMessageBox.confirm(
      `确定要删除选中的 ${selectedDocs.value.length} 个文档吗？`,
      '批量删除',
      { type: 'warning' }
    )

    batchDeleting.value = true
    
    // 这里应该调用批量删除API
    for (const doc of selectedDocs.value) {
      await deleteDocumentApi(doc.id)
    }
    
    ElMessage.success('批量删除成功')
    loadDocuments()
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

// 组件挂载时加载数据
onMounted(() => {
  loadDocuments()
})
</script>

<style scoped>
.admin-knowledge-container {
  max-width: 1200px;
  margin: 0 auto;
}

.card-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.card-header h3 {
  margin: 0 0 4px 0;
  color: var(--text-primary);
}

.card-header p {
  margin: 0;
  color: var(--text-secondary);
  font-size: 14px;
}

.header-actions {
  display: flex;
  gap: 12px;
}

.stats-section {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(200px, 1fr));
  gap: 20px;
  margin-bottom: 24px;
}

.stat-item {
  display: flex;
  align-items: center;
  gap: 16px;
  padding: 20px;
  border: 1px solid var(--border-light);
  border-radius: 8px;
  background: linear-gradient(135deg, #f8f9fa 0%, #e9ecef 100%);
}

.stat-icon {
  width: 50px;
  height: 50px;
  border-radius: 8px;
  background: var(--primary-color);
  color: white;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 20px;
}

.stat-number {
  font-size: 24px;
  font-weight: 600;
  color: var(--text-primary);
  line-height: 1;
}

.stat-label {
  font-size: 14px;
  color: var(--text-secondary);
  margin-top: 4px;
}

.filter-section {
  margin-bottom: 20px;
}

.doc-info {
  display: flex;
  align-items: center;
  gap: 8px;
}

.doc-icon {
  color: var(--primary-color);
}

.doc-name {
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

.batch-progress {
  margin-top: 20px;
  padding: 16px;
  border: 1px solid var(--border-light);
  border-radius: 8px;
  background: #f8f9fa;
}

.batch-progress h4 {
  margin: 0 0 12px 0;
  color: var(--text-primary);
}

.batch-progress p {
  margin: 8px 0 0 0;
  color: var(--text-regular);
  font-size: 14px;
}

@media (max-width: 1024px) {
  .card-header {
    flex-direction: column;
    align-items: flex-start;
    gap: 16px;
  }

  .header-actions {
    width: 100%;
    justify-content: flex-end;
  }
}

@media (max-width: 768px) {
  .filter-section .el-form {
    display: flex;
    flex-direction: column;
    gap: 12px;
  }

  .filter-section .el-form-item {
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

  .header-actions {
    flex-direction: column;
    gap: 8px;
  }

  .stats-section {
    grid-template-columns: 1fr;
    gap: 16px;
  }
}
</style>
