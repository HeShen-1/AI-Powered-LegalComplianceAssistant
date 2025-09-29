<template>
  <div class="admin-users-container">
    <el-card shadow="never">
      <template #header>
        <div class="card-header">
          <div>
            <h3>用户管理</h3>
            <p>管理系统中的所有用户账户</p>
          </div>
          <el-button type="primary" @click="showAddUser = true">
            <el-icon><Plus /></el-icon>
            添加用户
          </el-button>
        </div>
      </template>

      <!-- 搜索和筛选 -->
      <div class="filter-section">
        <el-form inline>
          <el-form-item>
            <el-input
              v-model="searchKeyword"
              placeholder="搜索用户名或邮箱..."
              :prefix-icon="Search"
              clearable
              @input="handleSearch"
            />
          </el-form-item>
          <el-form-item>
            <el-select v-model="filters.role" placeholder="角色筛选" clearable>
              <el-option label="全部角色" value="" />
              <el-option label="管理员" value="ADMIN" />
              <el-option label="普通用户" value="USER" />
            </el-select>
          </el-form-item>
          <el-form-item>
            <el-select v-model="filters.enabled" placeholder="状态筛选" clearable>
              <el-option label="全部状态" value="" />
              <el-option label="已启用" :value="true" />
              <el-option label="已禁用" :value="false" />
            </el-select>
          </el-form-item>
        </el-form>
      </div>

      <!-- 用户表格 -->
      <el-table :data="users" :loading="loading" stripe>
        <el-table-column prop="id" label="ID" width="80" />
        
        <el-table-column prop="username" label="用户名" min-width="120">
          <template #default="{ row }">
            <div class="user-info">
              <el-avatar :size="32" :src="row.avatar">
                <el-icon><UserFilled /></el-icon>
              </el-avatar>
              <span>{{ row.username }}</span>
            </div>
          </template>
        </el-table-column>
        
        <el-table-column prop="fullName" label="真实姓名" min-width="100" />
        <el-table-column prop="email" label="邮箱" min-width="180" />
        
        <el-table-column prop="role" label="角色" width="100" align="center">
          <template #default="{ row }">
            <el-tag :type="row.role === 'ADMIN' ? 'danger' : 'primary'">
              {{ row.role === 'ADMIN' ? '管理员' : '普通用户' }}
            </el-tag>
          </template>
        </el-table-column>
        
        <el-table-column prop="enabled" label="状态" width="100" align="center">
          <template #default="{ row }">
            <el-tag :type="row.enabled ? 'success' : 'danger'">
              {{ row.enabled ? '已启用' : '已禁用' }}
            </el-tag>
          </template>
        </el-table-column>
        
        <el-table-column prop="createdAt" label="注册时间" width="180">
          <template #default="{ row }">
            {{ formatDateTime(row.createdAt) }}
          </template>
        </el-table-column>
        
        <el-table-column label="操作" width="200" fixed="right">
          <template #default="{ row }">
            <el-button text type="primary" @click="editUser(row)">
              编辑
            </el-button>
            <el-button
              text
              :type="row.enabled ? 'warning' : 'success'"
              @click="toggleUserStatus(row)"
            >
              {{ row.enabled ? '禁用' : '启用' }}
            </el-button>
            <el-button text type="danger" @click="deleteUser(row)">
              删除
            </el-button>
          </template>
        </el-table-column>
      </el-table>

      <!-- 分页 -->
      <div class="pagination-wrapper">
        <el-pagination
          v-model:current-page="pagination.page"
          v-model:page-size="pagination.size"
          :page-sizes="[10, 20, 50]"
          :total="total"
          layout="total, sizes, prev, pager, next, jumper"
          @size-change="loadUsers"
          @current-change="loadUsers"
        />
      </div>
    </el-card>

    <!-- 添加/编辑用户对话框 -->
    <el-dialog
      v-model="showAddUser"
      :title="editingUser ? '编辑用户' : '添加用户'"
      width="500px"
    >
      <el-form
        ref="userFormRef"
        :model="userForm"
        :rules="userFormRules"
        label-width="80px"
      >
        <el-form-item label="用户名" prop="username">
          <el-input v-model="userForm.username" :disabled="!!editingUser" />
        </el-form-item>
        
        <el-form-item label="真实姓名" prop="fullName">
          <el-input v-model="userForm.fullName" />
        </el-form-item>
        
        <el-form-item label="邮箱" prop="email">
          <el-input v-model="userForm.email" />
        </el-form-item>
        
        <el-form-item v-if="!editingUser" label="密码" prop="password">
          <el-input v-model="userForm.password" type="password" show-password />
        </el-form-item>
        
        <el-form-item label="角色" prop="role">
          <el-radio-group v-model="userForm.role">
            <el-radio label="USER">普通用户</el-radio>
            <el-radio label="ADMIN">管理员</el-radio>
          </el-radio-group>
        </el-form-item>
        
        <el-form-item label="状态" prop="enabled">
          <el-switch v-model="userForm.enabled" />
        </el-form-item>
      </el-form>
      
      <template #footer>
        <el-button @click="cancelUserForm">取消</el-button>
        <el-button type="primary" @click="saveUser" :loading="saving">
          确定
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import type { FormInstance, FormRules } from 'element-plus'
import type { User } from '@/types/api'
import { getUserInfoApi, toggleUserStatusApi } from '@/api/userService'
import {
  Plus,
  Search,
  UserFilled
} from '@element-plus/icons-vue'

// 组件状态
const loading = ref(false)
const saving = ref(false)
const showAddUser = ref(false)
const editingUser = ref<User | null>(null)
const userFormRef = ref<FormInstance>()

// 搜索和筛选
const searchKeyword = ref('')
const filters = reactive({
  role: '',
  enabled: ''
})

// 分页
const pagination = reactive({
  page: 1,
  size: 10
})

// 数据
const users = ref<User[]>([])
const total = ref(0)

// 用户表单
const userForm = reactive({
  username: '',
  fullName: '',
  email: '',
  password: '',
  role: 'USER' as 'USER' | 'ADMIN',
  enabled: true
})

// 表单验证规则
const userFormRules: FormRules = {
  username: [
    { required: true, message: '请输入用户名', trigger: 'blur' },
    { min: 3, max: 20, message: '用户名长度在 3 到 20 个字符', trigger: 'blur' }
  ],
  fullName: [
    { required: true, message: '请输入真实姓名', trigger: 'blur' }
  ],
  email: [
    { required: true, message: '请输入邮箱', trigger: 'blur' },
    { type: 'email', message: '请输入正确的邮箱格式', trigger: 'blur' }
  ],
  password: [
    { required: true, message: '请输入密码', trigger: 'blur' },
    { min: 6, message: '密码至少6位', trigger: 'blur' }
  ]
}

// 加载用户列表
const loadUsers = async () => {
  loading.value = true
  try {
    // 这里应该调用获取用户列表的API
    // 暂时使用模拟数据
    users.value = [
      {
        id: 1,
        username: 'admin',
        fullName: '系统管理员',
        email: 'admin@example.com',
        role: 'ADMIN',
        enabled: true,
        createdAt: new Date().toISOString()
      },
      {
        id: 2,
        username: 'user1',
        fullName: '张三',
        email: 'user1@example.com',
        role: 'USER',
        enabled: true,
        createdAt: new Date().toISOString()
      }
    ] as User[]
    total.value = users.value.length
  } catch (error) {
    ElMessage.error('加载用户列表失败')
  } finally {
    loading.value = false
  }
}

// 搜索处理
const handleSearch = () => {
  pagination.page = 1
  loadUsers()
}

// 编辑用户
const editUser = (user: User) => {
  editingUser.value = user
  Object.assign(userForm, {
    username: user.username,
    fullName: user.fullName,
    email: user.email,
    password: '',
    role: user.role,
    enabled: user.enabled
  })
  showAddUser.value = true
}

// 切换用户状态
const toggleUserStatus = async (user: User) => {
  try {
    await toggleUserStatusApi(user.id, !user.enabled)
    user.enabled = !user.enabled
    ElMessage.success(`用户已${user.enabled ? '启用' : '禁用'}`)
  } catch (error) {
    ElMessage.error('操作失败')
  }
}

// 删除用户
const deleteUser = async (user: User) => {
  try {
    await ElMessageBox.confirm(
      `确定要删除用户 "${user.username}" 吗？`,
      '确认删除',
      { type: 'warning' }
    )

    // 这里应该调用删除API
    ElMessage.success('用户删除成功')
    loadUsers()
  } catch {
    // 用户取消
  }
}

// 保存用户
const saveUser = async () => {
  if (!userFormRef.value) return

  const valid = await userFormRef.value.validate().catch(() => false)
  if (!valid) return

  saving.value = true
  try {
    if (editingUser.value) {
      // 编辑用户
      ElMessage.success('用户更新成功')
    } else {
      // 添加用户
      ElMessage.success('用户添加成功')
    }
    
    showAddUser.value = false
    loadUsers()
  } catch (error) {
    ElMessage.error('操作失败')
  } finally {
    saving.value = false
  }
}

// 取消表单
const cancelUserForm = () => {
  showAddUser.value = false
  editingUser.value = null
  Object.assign(userForm, {
    username: '',
    fullName: '',
    email: '',
    password: '',
    role: 'USER',
    enabled: true
  })
  userFormRef.value?.clearValidate()
}

// 辅助函数
const formatDateTime = (timestamp: string) => {
  return new Date(timestamp).toLocaleString('zh-CN')
}

// 组件挂载时加载数据
onMounted(() => {
  loadUsers()
})
</script>

<style scoped>
.admin-users-container {
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

.filter-section {
  margin-bottom: 20px;
}

.user-info {
  display: flex;
  align-items: center;
  gap: 8px;
}

.pagination-wrapper {
  margin-top: 20px;
  text-align: center;
}

@media (max-width: 768px) {
  .card-header {
    flex-direction: column;
    align-items: flex-start;
    gap: 16px;
  }

  .filter-section .el-form {
    display: flex;
    flex-direction: column;
    gap: 12px;
  }

  .filter-section .el-form-item {
    margin-bottom: 0;
  }
}
</style>
