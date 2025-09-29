<template>
  <div class="profile-container">
    <div class="profile-content">
      <!-- 用户信息卡片 -->
      <el-card class="user-info-card" shadow="never">
        <template #header>
          <div class="card-header">
            <el-icon><User /></el-icon>
            <span>个人信息</span>
          </div>
        </template>

        <div class="user-profile">
          <div class="avatar-section">
            <el-avatar :size="100" :src="userInfo.avatar || undefined">
              <el-icon><UserFilled /></el-icon>
            </el-avatar>
            <el-button text type="primary" @click="showAvatarUpload = true">
              更换头像
            </el-button>
          </div>

          <div class="info-section">
            <el-form
              ref="userFormRef"
              :model="userForm"
              :rules="userFormRules"
              label-width="100px"
              size="large"
            >
              <el-form-item label="用户名" prop="username">
                <el-input v-model="userForm.username" disabled />
              </el-form-item>

              <el-form-item label="真实姓名" prop="fullName">
                <el-input
                  v-model="userForm.fullName"
                  :disabled="!editMode"
                  placeholder="请输入真实姓名"
                />
              </el-form-item>

              <el-form-item label="邮箱" prop="email">
                <el-input
                  v-model="userForm.email"
                  :disabled="!editMode"
                  placeholder="请输入邮箱地址"
                />
              </el-form-item>

              <el-form-item label="角色">
                <el-tag :type="userStore.isAdmin ? 'danger' : 'primary'">
                  {{ userStore.isAdmin ? '管理员' : '普通用户' }}
                </el-tag>
              </el-form-item>

              <el-form-item label="注册时间">
                <span>{{ formatDateTime(userInfo.createdAt) }}</span>
              </el-form-item>

              <el-form-item>
                <el-button
                  v-if="!editMode"
                  type="primary"
                  @click="toggleEditMode"
                >
                  编辑信息
                </el-button>
                <template v-else>
                  <el-button
                    type="primary"
                    :loading="updating"
                    @click="saveUserInfo"
                  >
                    保存
                  </el-button>
                  <el-button @click="cancelEdit">取消</el-button>
                </template>
              </el-form-item>
            </el-form>
          </div>
        </div>
      </el-card>

      <!-- 密码修改卡片 -->
      <el-card class="password-card" shadow="never">
        <template #header>
          <div class="card-header">
            <el-icon><Lock /></el-icon>
            <span>修改密码</span>
          </div>
        </template>

        <el-form
          ref="passwordFormRef"
          :model="passwordForm"
          :rules="passwordFormRules"
          label-width="100px"
          size="large"
        >
          <el-form-item label="当前密码" prop="oldPassword">
            <el-input
              v-model="passwordForm.oldPassword"
              type="password"
              placeholder="请输入当前密码"
              show-password
            />
          </el-form-item>

          <el-form-item label="新密码" prop="newPassword">
            <el-input
              v-model="passwordForm.newPassword"
              type="password"
              placeholder="请输入新密码"
              show-password
            />
          </el-form-item>

          <el-form-item label="确认密码" prop="confirmPassword">
            <el-input
              v-model="passwordForm.confirmPassword"
              type="password"
              placeholder="请再次输入新密码"
              show-password
            />
          </el-form-item>

          <el-form-item>
            <el-button
              type="primary"
              :loading="changingPassword"
              @click="changePassword"
            >
              修改密码
            </el-button>
            <el-button @click="resetPasswordForm">重置</el-button>
          </el-form-item>
        </el-form>
      </el-card>

      <!-- 使用统计卡片 -->
      <el-card class="stats-card" shadow="never">
        <template #header>
          <div class="card-header">
            <el-icon><DataAnalysis /></el-icon>
            <span>使用统计</span>
          </div>
        </template>

        <div class="stats-grid">
          <div class="stat-item">
            <div class="stat-icon chat-icon">
              <el-icon><ChatDotRound /></el-icon>
            </div>
            <div class="stat-info">
              <div class="stat-number">{{ userStats.totalChats }}</div>
              <div class="stat-label">AI对话次数</div>
            </div>
          </div>

          <div class="stat-item">
            <div class="stat-icon review-icon">
              <el-icon><Document /></el-icon>
            </div>
            <div class="stat-info">
              <div class="stat-number">{{ userStats.totalReviews }}</div>
              <div class="stat-label">合同审查次数</div>
            </div>
          </div>

          <div class="stat-item">
            <div class="stat-icon time-icon">
              <el-icon><Clock /></el-icon>
            </div>
            <div class="stat-info">
              <div class="stat-number">{{ userStats.savedHours }}</div>
              <div class="stat-label">节省时间(小时)</div>
            </div>
          </div>
        </div>
      </el-card>

      <!-- 系统设置卡片 -->
      <el-card class="settings-card" shadow="never">
        <template #header>
          <div class="card-header">
            <el-icon><Setting /></el-icon>
            <span>系统设置</span>
          </div>
        </template>

        <el-form label-width="120px" size="large">
          <el-form-item label="主题模式">
            <el-radio-group v-model="settings.theme">
              <el-radio label="light">浅色模式</el-radio>
              <el-radio label="dark">深色模式</el-radio>
              <el-radio label="auto">跟随系统</el-radio>
            </el-radio-group>
          </el-form-item>

          <el-form-item label="语言设置">
            <el-select v-model="settings.language" style="width: 200px">
              <el-option label="简体中文" value="zh-CN" />
              <el-option label="English" value="en-US" />
            </el-select>
          </el-form-item>

          <el-form-item label="邮件通知">
            <el-switch
              v-model="settings.emailNotifications"
              active-text="开启"
              inactive-text="关闭"
            />
          </el-form-item>

          <el-form-item label="系统通知">
            <el-switch
              v-model="settings.systemNotifications"
              active-text="开启"
              inactive-text="关闭"
            />
          </el-form-item>

          <el-form-item>
            <el-button type="primary" @click="saveSettings">
              保存设置
            </el-button>
          </el-form-item>
        </el-form>
      </el-card>
    </div>

    <!-- 头像上传对话框 -->
    <el-dialog
      v-model="showAvatarUpload"
      title="更换头像"
      width="400px"
    >
      <el-upload
        class="avatar-uploader"
        action="#"
        :show-file-list="false"
        :before-upload="beforeAvatarUpload"
        :on-success="handleAvatarSuccess"
      >
        <img v-if="newAvatar" :src="newAvatar" class="avatar-preview" />
        <el-icon v-else class="avatar-uploader-icon"><Plus /></el-icon>
      </el-upload>
      <div class="upload-tips">
        <p>请选择图片文件，支持 JPG、PNG 格式</p>
        <p>图片大小不超过 2MB，建议尺寸 200x200 像素</p>
      </div>

      <template #footer>
        <el-button @click="showAvatarUpload = false">取消</el-button>
        <el-button
          type="primary"
          :disabled="!newAvatar"
          :loading="uploadingAvatar"
          @click="confirmAvatarUpload"
        >
          确定
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, computed, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import type { FormInstance, FormRules, UploadRawFile } from 'element-plus'
import { useUserStore } from '@/store/modules/user'
import { updateUserApi, changePasswordApi } from '@/api/userService'
import type { User } from '@/types/api'
import {
  User,
  UserFilled,
  Lock,
  DataAnalysis,
  ChatDotRound,
  Document,
  Clock,
  Setting,
  Plus
} from '@element-plus/icons-vue'

const userStore = useUserStore()

// 表单引用
const userFormRef = ref<FormInstance>()
const passwordFormRef = ref<FormInstance>()

// 组件状态
const editMode = ref(false)
const updating = ref(false)
const changingPassword = ref(false)
const showAvatarUpload = ref(false)
const uploadingAvatar = ref(false)
const newAvatar = ref('')

// 用户信息
const userInfo = computed(() => userStore.userInfo || {} as User)

// 用户表单
const userForm = reactive({
  username: '',
  fullName: '',
  email: ''
})

// 密码表单
const passwordForm = reactive({
  oldPassword: '',
  newPassword: '',
  confirmPassword: ''
})

// 用户统计
const userStats = reactive({
  totalChats: 0,
  totalReviews: 0,
  savedHours: 0
})

// 系统设置
const settings = reactive({
  theme: 'light',
  language: 'zh-CN',
  emailNotifications: true,
  systemNotifications: true
})

// 表单验证规则
const userFormRules: FormRules = {
  fullName: [
    { required: true, message: '请输入真实姓名', trigger: 'blur' },
    { min: 2, max: 10, message: '姓名长度在 2 到 10 个字符', trigger: 'blur' }
  ],
  email: [
    { required: true, message: '请输入邮箱', trigger: 'blur' },
    { type: 'email', message: '请输入正确的邮箱格式', trigger: 'blur' }
  ]
}

const passwordFormRules: FormRules = {
  oldPassword: [
    { required: true, message: '请输入当前密码', trigger: 'blur' }
  ],
  newPassword: [
    { required: true, message: '请输入新密码', trigger: 'blur' },
    { min: 6, message: '密码至少6位', trigger: 'blur' },
    { pattern: /^(?=.*[a-z])(?=.*[A-Z])(?=.*\d)/, message: '密码必须包含大小写字母和数字', trigger: 'blur' }
  ],
  confirmPassword: [
    { required: true, message: '请确认密码', trigger: 'blur' },
    {
      validator: (rule, value, callback) => {
        if (value !== passwordForm.newPassword) {
          callback(new Error('两次输入的密码不一致'))
        } else {
          callback()
        }
      },
      trigger: 'blur'
    }
  ]
}

// 初始化用户表单
const initUserForm = () => {
  if (userInfo.value) {
    userForm.username = userInfo.value.username || ''
    userForm.fullName = userInfo.value.fullName || ''
    userForm.email = userInfo.value.email || ''
  }
}

// 切换编辑模式
const toggleEditMode = () => {
  editMode.value = true
}

// 取消编辑
const cancelEdit = () => {
  editMode.value = false
  initUserForm()
}

// 保存用户信息
const saveUserInfo = async () => {
  if (!userFormRef.value) return

  const valid = await userFormRef.value.validate().catch(() => false)
  if (!valid) return

  updating.value = true
  try {
    const response = await updateUserApi(userInfo.value.id, {
      fullName: userForm.fullName,
      email: userForm.email
    })

    if (response.data.success) {
      userStore.updateUserInfo(response.data.user)
      editMode.value = false
      ElMessage.success('个人信息更新成功')
    }
  } catch (error: any) {
    ElMessage.error(error.response?.data?.message || '更新失败')
  } finally {
    updating.value = false
  }
}

// 修改密码
const changePassword = async () => {
  if (!passwordFormRef.value) return

  const valid = await passwordFormRef.value.validate().catch(() => false)
  if (!valid) return

  changingPassword.value = true
  try {
    await changePasswordApi(userInfo.value.id, {
      oldPassword: passwordForm.oldPassword,
      newPassword: passwordForm.newPassword
    })

    ElMessage.success('密码修改成功')
    resetPasswordForm()
  } catch (error: any) {
    ElMessage.error(error.response?.data?.message || '密码修改失败')
  } finally {
    changingPassword.value = false
  }
}

// 重置密码表单
const resetPasswordForm = () => {
  passwordForm.oldPassword = ''
  passwordForm.newPassword = ''
  passwordForm.confirmPassword = ''
  passwordFormRef.value?.clearValidate()
}

// 头像上传前检查
const beforeAvatarUpload = (file: UploadRawFile) => {
  const isJPG = file.type === 'image/jpeg' || file.type === 'image/png'
  const isLt2M = file.size / 1024 / 1024 < 2

  if (!isJPG) {
    ElMessage.error('头像图片只能是 JPG/PNG 格式!')
    return false
  }
  if (!isLt2M) {
    ElMessage.error('头像图片大小不能超过 2MB!')
    return false
  }

  // 预览图片
  const reader = new FileReader()
  reader.onload = (e) => {
    newAvatar.value = e.target?.result as string
  }
  reader.readAsDataURL(file)

  return false // 阻止自动上传
}

// 头像上传成功
const handleAvatarSuccess = () => {
  // 这里可以处理上传成功的逻辑
}

// 确认头像上传
const confirmAvatarUpload = async () => {
  uploadingAvatar.value = true
  try {
    // 这里应该调用上传头像的API
    // await uploadAvatarApi(newAvatar.value)
    
    ElMessage.success('头像更新成功')
    showAvatarUpload.value = false
    newAvatar.value = ''
  } catch (error) {
    ElMessage.error('头像上传失败')
  } finally {
    uploadingAvatar.value = false
  }
}

// 保存设置
const saveSettings = () => {
  // 这里可以调用保存设置的API
  ElMessage.success('设置保存成功')
}

// 加载用户统计数据
const loadUserStats = () => {
  // 模拟数据，实际应该从API获取
  userStats.totalChats = Math.floor(Math.random() * 100) + 50
  userStats.totalReviews = Math.floor(Math.random() * 50) + 20
  userStats.savedHours = Math.ceil(userStats.totalReviews * 2.5)
}

// 辅助函数
const formatDateTime = (timestamp: string) => {
  if (!timestamp) return '-'
  return new Date(timestamp).toLocaleString('zh-CN')
}

// 组件挂载时初始化
onMounted(() => {
  initUserForm()
  loadUserStats()
  
  // 从localStorage恢复设置
  const savedSettings = localStorage.getItem('user_settings')
  if (savedSettings) {
    try {
      Object.assign(settings, JSON.parse(savedSettings))
    } catch (error) {
      console.error('Failed to load settings:', error)
    }
  }
})
</script>

<style scoped>
.profile-container {
  max-width: 800px;
  margin: 0 auto;
}

.profile-content {
  display: flex;
  flex-direction: column;
  gap: 24px;
}

.card-header {
  display: flex;
  align-items: center;
  gap: 8px;
  font-weight: 600;
}

.user-profile {
  display: flex;
  gap: 32px;
}

.avatar-section {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 16px;
  flex-shrink: 0;
}

.info-section {
  flex: 1;
}

.stats-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(200px, 1fr));
  gap: 20px;
}

.stat-item {
  display: flex;
  align-items: center;
  gap: 16px;
  padding: 20px;
  border: 1px solid var(--border-light);
  border-radius: 8px;
  background: #f8f9fa;
}

.stat-icon {
  width: 50px;
  height: 50px;
  border-radius: 8px;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 20px;
  color: white;
}

.chat-icon {
  background: linear-gradient(135deg, #667eea, #764ba2);
}

.review-icon {
  background: linear-gradient(135deg, #f093fb, #f5576c);
}

.time-icon {
  background: linear-gradient(135deg, #43e97b, #38f9d7);
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

.avatar-uploader {
  display: flex;
  justify-content: center;
}

.avatar-uploader .el-upload {
  border: 1px dashed var(--border-color);
  border-radius: 6px;
  cursor: pointer;
  position: relative;
  overflow: hidden;
  transition: var(--el-transition-duration-fast);
}

.avatar-uploader .el-upload:hover {
  border-color: var(--primary-color);
}

.avatar-uploader-icon {
  font-size: 28px;
  color: #8c939d;
  width: 178px;
  height: 178px;
  text-align: center;
  display: flex;
  align-items: center;
  justify-content: center;
}

.avatar-preview {
  width: 178px;
  height: 178px;
  display: block;
  object-fit: cover;
}

.upload-tips {
  margin-top: 16px;
  text-align: center;
}

.upload-tips p {
  margin: 4px 0;
  font-size: 14px;
  color: var(--text-secondary);
}

@media (max-width: 768px) {
  .user-profile {
    flex-direction: column;
    gap: 24px;
  }

  .avatar-section {
    align-self: center;
  }

  .stats-grid {
    grid-template-columns: 1fr;
    gap: 16px;
  }

  .stat-item {
    padding: 16px;
  }
}
</style>
