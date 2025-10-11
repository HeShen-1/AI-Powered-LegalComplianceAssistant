<template>
  <div class="users-container">
    <el-card shadow="never">
      <template #header>
        <div class="page-header">
          <div class="header-left">
            <h3>👥 用户管理</h3>
            <p class="header-subtitle">管理系统用户账户和权限</p>
          </div>
          <div class="header-right">
            <el-button type="primary" @click="showAddDialog = true">
              <el-icon><Plus /></el-icon>
              添加用户
            </el-button>
            <el-button @click="refreshList">
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
              placeholder="搜索用户名或邮箱..."
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
              v-model="roleFilter"
              placeholder="角色筛选"
              clearable
              @change="handleFilter"
              style="width: 100%"
            >
              <el-option label="全部" value="" />
              <el-option label="管理员" value="ADMIN" />
              <el-option label="普通用户" value="USER" />
            </el-select>
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
              <el-option label="正常" value="true" />
              <el-option label="禁用" value="false" />
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
      
      <!-- 用户表格 -->
      <div class="table-section">
        <el-table
          v-loading="loading"
          :data="filteredUsers"
          stripe
          @sort-change="handleSortChange"
        >
          <el-table-column type="selection" width="55" />
          
          <el-table-column prop="id" label="ID" width="80" sortable="custom" />
          
          <el-table-column prop="username" label="用户名" min-width="120">
            <template #default="{ row }">
              <div class="user-info">
                <el-avatar :size="32">
                  <el-icon><User /></el-icon>
                </el-avatar>
                <div class="user-details">
                  <div class="username">{{ row.username }}</div>
                  <div class="fullname">{{ row.fullName }}</div>
                </div>
              </div>
            </template>
          </el-table-column>
          
          <el-table-column prop="email" label="邮箱" min-width="180" />
          
          <el-table-column prop="role" label="角色" width="100" align="center">
            <template #default="{ row }">
              <el-tag :type="row.role === 'ADMIN' ? 'danger' : 'primary'" size="small">
                {{ row.role === 'ADMIN' ? '管理员' : '普通用户' }}
              </el-tag>
            </template>
          </el-table-column>
          
          <el-table-column prop="enabled" label="状态" width="80" align="center">
            <template #default="{ row }">
              <el-tag :type="row.enabled ? 'success' : 'danger'" size="small">
                {{ row.enabled ? '正常' : '禁用' }}
              </el-tag>
            </template>
          </el-table-column>
          
          <el-table-column
            prop="createdAt"
            label="注册时间"
            width="160"
            sortable="custom"
          >
            <template #default="{ row }">
              {{ formatDateTime(row.createdAt) }}
            </template>
          </el-table-column>
          
          <el-table-column label="操作" width="200" align="center" fixed="right">
            <template #default="{ row }">
              <el-button
                type="primary"
                size="small"
                @click="editUser(row)"
              >
                编辑
              </el-button>
              <el-button
                :type="row.enabled ? 'warning' : 'success'"
                size="small"
                @click="toggleUserStatus(row)"
              >
                {{ row.enabled ? '禁用' : '启用' }}
              </el-button>
              <el-button
                type="danger"
                size="small"
                @click="deleteUser(row)"
              >
                删除
              </el-button>
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
    
    <!-- 添加用户对话框 -->
    <el-dialog
      v-model="showAddDialog"
      title="添加用户"
      width="500px"
      :close-on-click-modal="false"
    >
      <el-form
        ref="addFormRef"
        :model="addForm"
        :rules="userRules"
        label-width="80px"
      >
        <el-form-item label="用户名" prop="username">
          <el-input v-model="addForm.username" />
        </el-form-item>
        <el-form-item label="邮箱" prop="email">
          <el-input v-model="addForm.email" type="email" />
        </el-form-item>
        <el-form-item label="姓名" prop="fullName">
          <el-input v-model="addForm.fullName" />
        </el-form-item>
        <el-form-item label="密码" prop="password">
          <el-input v-model="addForm.password" type="password" show-password />
        </el-form-item>
        <el-form-item label="角色" prop="role">
          <el-select v-model="addForm.role" style="width: 100%">
            <el-option label="普通用户" value="USER" />
            <el-option label="管理员" value="ADMIN" />
          </el-select>
        </el-form-item>
        <el-form-item label="状态" prop="enabled">
          <el-switch v-model="addForm.enabled" />
        </el-form-item>
      </el-form>
      
      <template #footer>
        <el-button @click="showAddDialog = false">取消</el-button>
        <el-button type="primary" :loading="addLoading" @click="handleAddSubmit">
          添加
        </el-button>
      </template>
    </el-dialog>
    
    <!-- 编辑用户对话框 -->
    <el-dialog
      v-model="showEditDialog"
      title="编辑用户"
      width="500px"
      :close-on-click-modal="false"
    >
      <el-form
        ref="editFormRef"
        :model="editForm"
        :rules="editUserRules"
        label-width="80px"
      >
        <el-form-item label="用户名" prop="username">
          <el-input v-model="editForm.username" disabled />
        </el-form-item>
        <el-form-item label="邮箱" prop="email">
          <el-input v-model="editForm.email" type="email" />
        </el-form-item>
        <el-form-item label="姓名" prop="fullName">
          <el-input v-model="editForm.fullName" />
        </el-form-item>
        <el-form-item label="角色" prop="role">
          <el-select v-model="editForm.role" style="width: 100%">
            <el-option label="普通用户" value="USER" />
            <el-option label="管理员" value="ADMIN" />
          </el-select>
        </el-form-item>
        <el-form-item label="状态" prop="enabled">
          <el-switch v-model="editForm.enabled" />
        </el-form-item>
      </el-form>
      
      <template #footer>
        <el-button @click="showEditDialog = false">取消</el-button>
        <el-button type="primary" :loading="editLoading" @click="handleEditSubmit">
          保存
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { ElMessage, ElMessageBox, type FormInstance, type FormRules } from 'element-plus'
import {
  Plus,
  Refresh,
  Search,
  User
} from '@element-plus/icons-vue'
import type { User as UserType } from '@/types/api'
import { getAllUsersApi, registerApi, toggleUserStatusApi, deleteUserApi } from '@/api/userService'

// 响应式数据
const loading = ref(false)
const userList = ref<UserType[]>([])
const searchQuery = ref('')
const roleFilter = ref('')
const statusFilter = ref('')
const dateRange = ref<[string, string] | null>(null)
const currentPage = ref(1)
const pageSize = ref(20)
const total = ref(0)
const sortField = ref('')
const sortOrder = ref('')

const showAddDialog = ref(false)
const showEditDialog = ref(false)
const addLoading = ref(false)
const editLoading = ref(false)

const addFormRef = ref<FormInstance>()
const editFormRef = ref<FormInstance>()

const addForm = ref({
  username: '',
  email: '',
  fullName: '',
  password: '',
  role: 'USER' as 'USER' | 'ADMIN',
  enabled: true
})

const editForm = ref({
  id: 0,
  username: '',
  email: '',
  fullName: '',
  role: 'USER' as 'USER' | 'ADMIN',
  enabled: true
})

// 计算属性
const filteredUsers = computed(() => {
  let list = [...userList.value]
  
  // 搜索过滤
  if (searchQuery.value) {
    const query = searchQuery.value.toLowerCase()
    list = list.filter(user => 
      user.username.toLowerCase().includes(query) ||
      user.email.toLowerCase().includes(query) ||
      user.fullName.toLowerCase().includes(query)
    )
  }
  
  // 角色过滤
  if (roleFilter.value) {
    list = list.filter(user => user.role === roleFilter.value)
  }
  
  // 状态过滤
  if (statusFilter.value) {
    const enabled = statusFilter.value === 'true'
    list = list.filter(user => user.enabled === enabled)
  }
  
  // 日期范围过滤
  if (dateRange.value && dateRange.value.length === 2) {
    const [startDate, endDate] = dateRange.value
    list = list.filter(user => {
      const userDate = user.createdAt.split('T')[0]
      return userDate >= startDate && userDate <= endDate
    })
  }
  
  // 排序
  if (sortField.value) {
    list.sort((a, b) => {
      const aVal = a[sortField.value as keyof UserType]
      const bVal = b[sortField.value as keyof UserType]
      
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
const userRules: FormRules = {
  username: [
    { required: true, message: '请输入用户名', trigger: 'blur' },
    { min: 2, max: 20, message: '用户名长度在 2 到 20 个字符', trigger: 'blur' },
    { pattern: /^[a-zA-Z0-9_]+$/, message: '用户名只能包含字母、数字和下划线', trigger: 'blur' }
  ],
  email: [
    { required: true, message: '请输入邮箱地址', trigger: 'blur' },
    { type: 'email', message: '请输入正确的邮箱地址', trigger: 'blur' }
  ],
  fullName: [
    { required: true, message: '请输入姓名', trigger: 'blur' },
    { min: 2, max: 10, message: '姓名长度在 2 到 10 个字符', trigger: 'blur' }
  ],
  password: [
    { required: true, message: '请输入密码', trigger: 'blur' },
    { min: 6, max: 20, message: '密码长度在 6 到 20 个字符', trigger: 'blur' }
  ],
  role: [
    { required: true, message: '请选择角色', trigger: 'change' }
  ]
}

const editUserRules: FormRules = {
  email: [
    { required: true, message: '请输入邮箱地址', trigger: 'blur' },
    { type: 'email', message: '请输入正确的邮箱地址', trigger: 'blur' }
  ],
  fullName: [
    { required: true, message: '请输入姓名', trigger: 'blur' },
    { min: 2, max: 10, message: '姓名长度在 2 到 10 个字符', trigger: 'blur' }
  ],
  role: [
    { required: true, message: '请选择角色', trigger: 'change' }
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

// 数据获取
const fetchUserList = async () => {
  loading.value = true
  try {
    // 调用真实API获取用户列表
    const response = await getAllUsersApi()
    
    if (response.data) {
      userList.value = Array.isArray(response.data) ? response.data : []
      total.value = userList.value.length
    } else {
      userList.value = []
      total.value = 0
    }
  } catch (error) {
    console.error('Failed to fetch user list:', error)
    userList.value = []
    total.value = 0
  } finally {
    loading.value = false
  }
}

// 事件处理
const refreshList = () => {
  fetchUserList()
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
}

const handleCurrentChange = (page: number) => {
  currentPage.value = page
}

const editUser = (user: UserType) => {
  editForm.value = {
    id: user.id,
    username: user.username,
    email: user.email,
    fullName: user.fullName,
    role: user.role,
    enabled: user.enabled
  }
  showEditDialog.value = true
}

const toggleUserStatus = async (user: UserType) => {
  const action = user.enabled ? '禁用' : '启用'
  try {
    await ElMessageBox.confirm(
      `确定要${action}用户"${user.username}"吗？`,
      '状态变更确认',
      {
        confirmButtonText: `确定${action}`,
        cancelButtonText: '取消',
        type: 'warning'
      }
    )
    
    // 调用真实API切换用户状态
    await toggleUserStatusApi(user.id, !user.enabled)
    
    // 更新用户状态
    const index = userList.value.findIndex(u => u.id === user.id)
    if (index > -1) {
      userList.value[index].enabled = !user.enabled
    }
    
    ElMessage.success(`用户${action}成功`)
  } catch (error: any) {
    if (error !== 'cancel') {
      console.error('Failed to toggle user status:', error)
      ElMessage.error(`用户${action}失败`)
    }
  }
}

const deleteUser = async (user: UserType) => {
  try {
    await ElMessageBox.confirm(
      `确定要删除用户"${user.username}"吗？此操作不可恢复，将删除用户的所有聊天记录，但会保留合同审查记录。`,
      '删除确认',
      {
        confirmButtonText: '确定删除',
        cancelButtonText: '取消',
        type: 'warning',
        distinguishCancelAndClose: true
      }
    )
    
    // 调用删除用户API
    await deleteUserApi(user.id)
    
    ElMessage.success('用户删除成功')
    
    // 刷新用户列表
    await fetchUserList()
  } catch (error: any) {
    if (error !== 'cancel') {
      console.error('Failed to delete user:', error)
      ElMessage.error('操作失败')
    }
  }
}

const handleAddSubmit = async () => {
  if (!addFormRef.value) return
  
  try {
    await addFormRef.value.validate()
    addLoading.value = true
    
    // 调用真实API注册新用户
    await registerApi({
      username: addForm.value.username,
      email: addForm.value.email,
      fullName: addForm.value.fullName,
      password: addForm.value.password
    })
    
    // 重置表单
    addForm.value = {
      username: '',
      email: '',
      fullName: '',
      password: '',
      role: 'USER',
      enabled: true
    }
    
    showAddDialog.value = false
    ElMessage.success('用户添加成功')
    
    // 刷新用户列表
    await fetchUserList()
  } catch (error: any) {
    console.error('Failed to add user:', error)
    const errorMsg = error?.response?.data?.message || error?.message || '添加用户失败'
    ElMessage.error(errorMsg)
  } finally {
    addLoading.value = false
  }
}

const handleEditSubmit = async () => {
  if (!editFormRef.value) return
  
  try {
    await editFormRef.value.validate()
    editLoading.value = true
    
    // 注意：后端的updateUser API只支持更新fullName和email
    // role和enabled需要通过其他API更新
    const { updateUserApi } = await import('@/api/userService')
    await updateUserApi(editForm.value.id, {
      fullName: editForm.value.fullName,
      email: editForm.value.email
    })
    
    showEditDialog.value = false
    ElMessage.success('用户信息更新成功')
    
    // 刷新用户列表
    await fetchUserList()
  } catch (error: any) {
    console.error('Failed to update user:', error)
    const errorMsg = error?.response?.data?.message || error?.message || '更新用户信息失败'
    ElMessage.error(errorMsg)
  } finally {
    editLoading.value = false
  }
}

// 组件挂载时获取数据
onMounted(() => {
  fetchUserList()
})
</script>

<style scoped>
.users-container {
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

.search-section {
  margin-bottom: 20px;
  padding: 20px;
  background-color: #f8f9fa;
  border-radius: 6px;
}

.table-section {
  margin-top: 20px;
}

.user-info {
  display: flex;
  align-items: center;
  gap: 12px;
}

.user-details {
  display: flex;
  flex-direction: column;
}

.username {
  font-weight: 500;
  color: #2c3e50;
}

.fullname {
  font-size: 12px;
  color: #7f8c8d;
}

.pagination-section {
  margin-top: 20px;
  text-align: right;
}

/* 响应式设计 */
@media (max-width: 768px) {
  .page-header {
    flex-direction: column;
    gap: 15px;
  }
  
  .header-right {
    width: 100%;
    justify-content: flex-start;
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
  
  .user-info {
    flex-direction: column;
    align-items: flex-start;
    gap: 8px;
  }
}

@media (max-width: 480px) {
  .search-section {
    padding: 15px;
  }
  
  .header-right .el-button {
    flex: 1;
  }
  
  .el-table :deep(.el-table__cell) {
    padding: 8px 4px;
  }
}
</style>