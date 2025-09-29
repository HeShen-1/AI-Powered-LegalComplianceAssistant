<template>
  <div class="error-container">
    <div class="error-content">
      <div class="error-image">
        <el-icon class="error-icon"><WarningFilled /></el-icon>
      </div>
      
      <div class="error-info">
        <h1 class="error-code">404</h1>
        <h2 class="error-title">页面不存在</h2>
        <p class="error-description">
          抱歉，您访问的页面不存在或已被移除。
          <br>
          请检查URL是否正确，或返回首页继续使用。
        </p>
        
        <div class="error-actions">
          <el-button type="primary" size="large" @click="goHome">
            <el-icon><HomeFilled /></el-icon>
            返回首页
          </el-button>
          
          <el-button size="large" @click="goBack">
            <el-icon><Back /></el-icon>
            返回上页
          </el-button>
        </div>
        
        <div class="helpful-links">
          <p>您可能想要：</p>
          <div class="link-buttons">
            <el-button text type="primary" @click="$router.push('/chat')">
              <el-icon><ChatDotRound /></el-icon>
              AI智能问答
            </el-button>
            
            <el-button text type="primary" @click="$router.push('/contract')">
              <el-icon><Document /></el-icon>
              合同审查
            </el-button>
            
            <el-button text type="primary" @click="$router.push('/history')">
              <el-icon><Clock /></el-icon>
              查看历史
            </el-button>
          </div>
        </div>
      </div>
    </div>
    
    <div class="error-footer">
      <p>如果问题持续存在，请联系技术支持</p>
    </div>
  </div>
</template>

<script setup lang="ts">
import { useRouter } from 'vue-router'
import {
  WarningFilled,
  HomeFilled,
  Back,
  ChatDotRound,
  Document,
  Clock
} from '@element-plus/icons-vue'

const router = useRouter()

// 返回首页
const goHome = () => {
  router.push('/')
}

// 返回上一页
const goBack = () => {
  if (window.history.length > 1) {
    router.go(-1)
  } else {
    router.push('/')
  }
}
</script>

<style scoped>
.error-container {
  min-height: 100vh;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  background: linear-gradient(135deg, #f5f7fa 0%, #c3cfe2 100%);
  padding: 20px;
}

.error-content {
  text-align: center;
  max-width: 600px;
  width: 100%;
}

.error-image {
  margin-bottom: 32px;
}

.error-icon {
  font-size: 120px;
  color: #f56c6c;
  opacity: 0.8;
}

.error-info {
  background: white;
  padding: 48px 32px;
  border-radius: 16px;
  box-shadow: 0 8px 32px rgba(0, 0, 0, 0.1);
  margin-bottom: 24px;
}

.error-code {
  font-size: 72px;
  font-weight: 700;
  color: var(--text-primary);
  margin: 0 0 16px 0;
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  -webkit-background-clip: text;
  -webkit-text-fill-color: transparent;
  background-clip: text;
}

.error-title {
  font-size: 32px;
  font-weight: 600;
  color: var(--text-primary);
  margin: 0 0 16px 0;
}

.error-description {
  font-size: 16px;
  color: var(--text-secondary);
  line-height: 1.6;
  margin: 0 0 32px 0;
}

.error-actions {
  display: flex;
  gap: 16px;
  justify-content: center;
  margin-bottom: 32px;
}

.helpful-links {
  text-align: center;
}

.helpful-links p {
  font-size: 14px;
  color: var(--text-secondary);
  margin: 0 0 16px 0;
}

.link-buttons {
  display: flex;
  gap: 16px;
  justify-content: center;
  flex-wrap: wrap;
}

.error-footer {
  text-align: center;
  color: var(--text-placeholder);
  font-size: 14px;
}

.error-footer p {
  margin: 0;
}

/* 响应式设计 */
@media (max-width: 768px) {
  .error-container {
    padding: 16px;
  }

  .error-icon {
    font-size: 80px;
  }

  .error-info {
    padding: 32px 24px;
  }

  .error-code {
    font-size: 48px;
  }

  .error-title {
    font-size: 24px;
  }

  .error-description {
    font-size: 14px;
  }

  .error-actions {
    flex-direction: column;
    align-items: center;
  }

  .error-actions .el-button {
    width: 200px;
  }

  .link-buttons {
    flex-direction: column;
    align-items: center;
    gap: 8px;
  }
}

@media (max-width: 480px) {
  .error-icon {
    font-size: 64px;
  }

  .error-code {
    font-size: 36px;
  }

  .error-title {
    font-size: 20px;
  }

  .error-info {
    padding: 24px 16px;
  }
}

/* 动画效果 */
.error-icon {
  animation: bounce 2s infinite;
}

@keyframes bounce {
  0%, 20%, 50%, 80%, 100% {
    transform: translateY(0);
  }
  40% {
    transform: translateY(-10px);
  }
  60% {
    transform: translateY(-5px);
  }
}

.error-content {
  animation: fadeInUp 0.8s ease-out;
}

@keyframes fadeInUp {
  from {
    opacity: 0;
    transform: translateY(30px);
  }
  to {
    opacity: 1;
    transform: translateY(0);
  }
}
</style>
