<template>
  <div class="login-container">
    <div class="login-box">
      <div class="login-header">
        <div class="logo">
          <el-icon class="logo-icon"><Document /></el-icon>
          <h1 class="title">法律合规智能审查助手</h1>
        </div>
        <p class="subtitle">专业的法律文档AI审查平台</p>
      </div>

      <el-card class="login-form-card" shadow="hover">
        <template #header>
          <div class="form-header">
            <el-tabs v-model="activeTab" @tab-click="handleTabClick">
              <el-tab-pane label="登录" name="login" />
              <el-tab-pane label="注册" name="register" />
            </el-tabs>
          </div>
        </template>

        <!-- 登录表单 -->
        <el-form
          v-if="activeTab === 'login'"
          ref="loginFormRef"
          :model="loginForm"
          :rules="loginRules"
          size="large"
          @submit.prevent="handleLogin"
        >
          <el-form-item prop="username">
            <el-input
              v-model="loginForm.username"
              placeholder="请输入用户名"
              :prefix-icon="User"
              clearable
            />
          </el-form-item>
          
          <el-form-item prop="password">
            <el-input
              v-model="loginForm.password"
              type="password"
              placeholder="请输入密码"
              :prefix-icon="Lock"
              show-password
              clearable
              @keyup.enter="handleLogin"
            />
          </el-form-item>
          
          <el-form-item>
            <el-button
              type="primary"
              class="login-btn"
              :loading="loginLoading"
              @click="handleLogin"
            >
              <span v-if="!loginLoading">登录</span>
              <span v-else>登录中...</span>
            </el-button>
          </el-form-item>
        </el-form>

        <!-- 注册表单 -->
        <el-form
          v-else
          ref="registerFormRef"
          :model="registerForm"
          :rules="registerRules"
          size="large"
          @submit.prevent="handleRegister"
        >
          <el-form-item prop="username">
            <el-input
              v-model="registerForm.username"
              placeholder="请输入用户名"
              :prefix-icon="User"
              clearable
            />
          </el-form-item>
          
          <el-form-item prop="fullName">
            <el-input
              v-model="registerForm.fullName"
              placeholder="请输入真实姓名"
              :prefix-icon="UserFilled"
              clearable
            />
          </el-form-item>
          
          <el-form-item prop="email">
            <el-input
              v-model="registerForm.email"
              type="email"
              placeholder="请输入邮箱"
              :prefix-icon="Message"
              clearable
            />
          </el-form-item>
          
          <el-form-item prop="password">
            <el-input
              v-model="registerForm.password"
              type="password"
              placeholder="请输入密码"
              :prefix-icon="Lock"
              show-password
              clearable
            />
          </el-form-item>
          
          <el-form-item prop="confirmPassword">
            <el-input
              v-model="registerForm.confirmPassword"
              type="password"
              placeholder="请确认密码"
              :prefix-icon="Lock"
              show-password
              clearable
              @keyup.enter="handleRegister"
            />
          </el-form-item>
          
          <el-form-item>
            <el-button
              type="primary"
              class="login-btn"
              :loading="registerLoading"
              @click="handleRegister"
            >
              <span v-if="!registerLoading">注册</span>
              <span v-else>注册中...</span>
            </el-button>
          </el-form-item>
        </el-form>
      </el-card>

      <div class="login-footer">
        <!-- 演示账户提示 -->
        <div class="demo-accounts">
          <h4>演示账户</h4>
          <div class="account-list">
            <div class="account-item">
              <span class="account-type">普通用户:</span>
              <span class="account-info">demo / 123456</span>
            </div>
            <div class="account-item">
              <span class="account-type">管理员:</span>
              <span class="account-info">admin / 123456</span>
            </div>
          </div>
        </div>
        
        <p class="features">
          <el-icon><CircleCheck /></el-icon>
          <span>AI智能分析</span>
          <el-icon><CircleCheck /></el-icon>
          <span>专业法律意见</span>
          <el-icon><CircleCheck /></el-icon>
          <span>风险预警提醒</span>
        </p>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive } from 'vue'
import type { FormInstance, FormRules, TabsPaneContext } from 'element-plus'
import { ElMessage } from 'element-plus'
import { useUserStore } from '@/store/modules/user'
import { registerApi } from '@/api/userService'
import {
  Document,
  User,
  UserFilled,
  Lock,
  Message,
  CircleCheck
} from '@element-plus/icons-vue'

const userStore = useUserStore()

// 表单状态
const activeTab = ref('login')
const loginLoading = ref(false)
const registerLoading = ref(false)

// 表单引用
const loginFormRef = ref<FormInstance>()
const registerFormRef = ref<FormInstance>()

// 登录表单数据
const loginForm = reactive({
  username: '',
  password: ''
})

// 注册表单数据
const registerForm = reactive({
  username: '',
  fullName: '',
  email: '',
  password: '',
  confirmPassword: ''
})

// 表单验证规则
const loginRules: FormRules = {
  username: [
    { required: true, message: '请输入用户名', trigger: 'blur' },
    { min: 3, max: 20, message: '用户名长度在 3 到 20 个字符', trigger: 'blur' }
  ],
  password: [
    { required: true, message: '请输入密码', trigger: 'blur' },
    { min: 6, message: '密码至少6位', trigger: 'blur' }
  ]
}

const registerRules: FormRules = {
  username: [
    { required: true, message: '请输入用户名', trigger: 'blur' },
    { min: 3, max: 20, message: '用户名长度在 3 到 20 个字符', trigger: 'blur' },
    { pattern: /^[a-zA-Z0-9_]+$/, message: '用户名只能包含字母、数字和下划线', trigger: 'blur' }
  ],
  fullName: [
    { required: true, message: '请输入真实姓名', trigger: 'blur' },
    { min: 2, max: 10, message: '姓名长度在 2 到 10 个字符', trigger: 'blur' }
  ],
  email: [
    { required: true, message: '请输入邮箱', trigger: 'blur' },
    { type: 'email', message: '请输入正确的邮箱格式', trigger: 'blur' }
  ],
  password: [
    { required: true, message: '请输入密码', trigger: 'blur' },
    { min: 6, message: '密码至少6位', trigger: 'blur' },
    { pattern: /^(?=.*[a-z])(?=.*[A-Z])(?=.*\d)/, message: '密码必须包含大小写字母和数字', trigger: 'blur' }
  ],
  confirmPassword: [
    { required: true, message: '请确认密码', trigger: 'blur' },
    {
      validator: (rule, value, callback) => {
        if (value !== registerForm.password) {
          callback(new Error('两次输入的密码不一致'))
        } else {
          callback()
        }
      },
      trigger: 'blur'
    }
  ]
}

// 切换标签页
const handleTabClick = (tab: TabsPaneContext) => {
  activeTab.value = tab.name as string
}

// 处理登录
const handleLogin = async () => {
  if (!loginFormRef.value) return

  const valid = await loginFormRef.value.validate().catch(() => false)
  if (!valid) return

  loginLoading.value = true
  try {
    await userStore.login(loginForm.username, loginForm.password)
  } catch (error) {
    console.error('Login failed:', error)
  } finally {
    loginLoading.value = false
  }
}

// 处理注册
const handleRegister = async () => {
  if (!registerFormRef.value) return

  const valid = await registerFormRef.value.validate().catch(() => false)
  if (!valid) return

  registerLoading.value = true
  try {
    const response = await registerApi({
      username: registerForm.username,
      fullName: registerForm.fullName,
      email: registerForm.email,
      password: registerForm.password
    })

    if (response.data.success) {
      ElMessage.success('注册成功，请登录')
      activeTab.value = 'login'
      // 清空注册表单
      Object.assign(registerForm, {
        username: '',
        fullName: '',
        email: '',
        password: '',
        confirmPassword: ''
      })
    } else {
      ElMessage.error(response.data.message || '注册失败')
    }
  } catch (error: any) {
    ElMessage.error(error.response?.data?.message || '注册失败')
  } finally {
    registerLoading.value = false
  }
}
</script>

<style scoped>
.login-container {
  min-height: 100vh;
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 20px;
}

.login-box {
  width: 100%;
  max-width: 400px;
}

.login-header {
  text-align: center;
  margin-bottom: 30px;
}

.logo {
  display: flex;
  align-items: center;
  justify-content: center;
  margin-bottom: 16px;
}

.logo-icon {
  font-size: 48px;
  color: white;
  margin-right: 12px;
}

.title {
  color: white;
  font-size: 28px;
  font-weight: 300;
  margin: 0;
}

.subtitle {
  color: rgba(255, 255, 255, 0.8);
  font-size: 16px;
  margin: 0;
}

.login-form-card {
  border-radius: 12px;
  overflow: hidden;
}

.form-header {
  text-align: center;
}

.login-btn {
  width: 100%;
  height: 50px;
  font-size: 16px;
  border-radius: 8px;
}

.login-footer {
  margin-top: 30px;
  text-align: center;
}

.demo-accounts {
  margin-bottom: 24px;
  padding: 16px;
  background: rgba(255, 255, 255, 0.1);
  border-radius: 8px;
  backdrop-filter: blur(10px);
}

.demo-accounts h4 {
  margin: 0 0 12px 0;
  color: rgba(255, 255, 255, 0.9);
  font-size: 16px;
}

.account-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.account-item {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 8px 12px;
  background: rgba(255, 255, 255, 0.1);
  border-radius: 4px;
  font-size: 14px;
}

.account-type {
  color: rgba(255, 255, 255, 0.8);
}

.account-info {
  color: #67c23a;
  font-family: monospace;
  font-weight: 600;
}

.features {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 12px;
  color: rgba(255, 255, 255, 0.9);
  font-size: 14px;
  margin: 0;
}

.features .el-icon {
  color: #67c23a;
}

/* 深色模式适配 */
.dark .login-form-card {
  background-color: #1f1f1f;
  border-color: #303030;
}

/* 响应式设计 */
@media (max-width: 480px) {
  .login-container {
    padding: 10px;
  }
  
  .title {
    font-size: 24px;
  }
  
  .subtitle {
    font-size: 14px;
  }
  
  .features {
    flex-direction: column;
    gap: 8px;
  }
}
</style>
