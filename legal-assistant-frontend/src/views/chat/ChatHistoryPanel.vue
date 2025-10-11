<template>
  <div class="chat-history-panel">
    <!-- 顶部标题和新建按钮 -->
    <div class="panel-header">
      <h3 class="panel-title">
        <el-icon><ChatDotRound /></el-icon>
        <span>聊天历史</span>
      </h3>
      <el-button 
        type="primary" 
        size="small" 
        :icon="Plus"
        @click="handleNewChat"
      >
        新建聊天
      </el-button>
    </div>

    <!-- 会话列表 -->
    <div class="sessions-list" v-loading="loading">
      <!-- 空状态 -->
      <div v-if="!loading && sessions.length === 0" class="empty-state">
        <el-empty 
          description="暂无聊天历史" 
          :image-size="80"
        />
      </div>

      <!-- 会话项列表 -->
      <div 
        v-for="session in sessions" 
        :key="session.id"
        class="session-item"
        :class="{ active: session.id === activeSessionId }"
        @click="handleSelectSession(session.id)"
      >
        <div class="session-content">
          <div class="session-title">{{ session.title }}</div>
          <div class="session-meta">
            <span class="session-time">{{ formatTime(session.updatedAt) }}</span>
            <span v-if="session.messageCount" class="session-count">
              {{ session.messageCount }} 条消息
            </span>
          </div>
        </div>
        <el-button
          class="delete-btn"
          type="danger"
          :icon="Delete"
          size="small"
          circle
          @click.stop="handleDeleteSession(session.id)"
        />
      </div>
    </div>

    <!-- 底部统计信息 -->
    <div class="panel-footer">
      <span class="session-count-text">
        共 {{ sessions.length }} 个会话
      </span>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted } from 'vue'
import { useChatHistoryStore } from '@/store/modules/chatHistory'
import { ElMessageBox } from 'element-plus'
import { Plus, Delete, ChatDotRound } from '@element-plus/icons-vue'
import dayjs from 'dayjs'
import relativeTime from 'dayjs/plugin/relativeTime'
import 'dayjs/locale/zh-cn'

// 配置dayjs
dayjs.extend(relativeTime)
dayjs.locale('zh-cn')

// 定义emits
const emit = defineEmits<{
  newChat: []
  selectSession: [sessionId: string]
}>()

// 使用store
const chatHistoryStore = useChatHistoryStore()

// 计算属性
const sessions = computed(() => chatHistoryStore.sessions)
const activeSessionId = computed(() => chatHistoryStore.activeSessionId)
const loading = computed(() => chatHistoryStore.loading)

/**
 * 格式化时间显示
 */
const formatTime = (timeStr: string) => {
  const time = dayjs(timeStr)
  const now = dayjs()
  
  // 如果是今天，显示相对时间
  if (time.isSame(now, 'day')) {
    return time.fromNow()
  }
  
  // 如果是昨天
  if (time.isSame(now.subtract(1, 'day'), 'day')) {
    return '昨天 ' + time.format('HH:mm')
  }
  
  // 如果是今年，显示月日
  if (time.isSame(now, 'year')) {
    return time.format('MM-DD HH:mm')
  }
  
  // 否则显示完整日期
  return time.format('YYYY-MM-DD')
}

/**
 * 处理新建聊天
 */
const handleNewChat = () => {
  chatHistoryStore.createNewSession()
  emit('newChat')
}

/**
 * 处理选择会话
 */
const handleSelectSession = (sessionId: string) => {
  emit('selectSession', sessionId)
}

/**
 * 处理删除会话
 */
const handleDeleteSession = async (sessionId: string) => {
  try {
    await ElMessageBox.confirm(
      '确定要删除这个会话吗？此操作不可恢复。',
      '删除确认',
      {
        confirmButtonText: '确定',
        cancelButtonText: '取消',
        type: 'warning',
      }
    )
    
    // 执行删除
    await chatHistoryStore.deleteSession(sessionId)
  } catch (error) {
    // 用户取消删除或删除失败
    console.log('取消删除或删除失败:', error)
  }
}

// 组件挂载时加载会话列表
onMounted(() => {
  chatHistoryStore.fetchSessions()
})
</script>

<style scoped lang="scss">
.chat-history-panel {
  display: flex;
  flex-direction: column;
  height: 100%;
  background: #ffffff;
  border-right: 1px solid #e4e7ed;
}

.panel-header {
  padding: 16px;
  border-bottom: 1px solid #e4e7ed;
  display: flex;
  align-items: center;
  justify-content: space-between;
  flex-shrink: 0;

  .panel-title {
    display: flex;
    align-items: center;
    gap: 8px;
    margin: 0;
    font-size: 16px;
    font-weight: 600;
    color: #303133;

    .el-icon {
      font-size: 20px;
      color: #409eff;
    }
  }
}

.sessions-list {
  flex: 1;
  overflow-y: auto;
  padding: 8px;

  .empty-state {
    display: flex;
    align-items: center;
    justify-content: center;
    height: 100%;
    min-height: 200px;
  }

  .session-item {
    display: flex;
    align-items: center;
    padding: 12px;
    margin-bottom: 4px;
    border-radius: 8px;
    cursor: pointer;
    transition: all 0.2s;
    background: #ffffff;
    border: 1px solid transparent;

    &:hover {
      background: #f5f7fa;
      border-color: #e4e7ed;

      .delete-btn {
        opacity: 1;
      }
    }

    &.active {
      background: #ecf5ff;
      border-color: #409eff;

      .session-title {
        color: #409eff;
        font-weight: 600;
      }
    }

    .session-content {
      flex: 1;
      min-width: 0;
      margin-right: 8px;

      .session-title {
        font-size: 14px;
        color: #303133;
        margin-bottom: 4px;
        overflow: hidden;
        text-overflow: ellipsis;
        white-space: nowrap;
      }

      .session-meta {
        display: flex;
        align-items: center;
        gap: 8px;
        font-size: 12px;
        color: #909399;

        .session-time {
          flex-shrink: 0;
        }

        .session-count {
          flex-shrink: 0;
          padding: 2px 6px;
          background: #f0f2f5;
          border-radius: 4px;
        }
      }
    }

    .delete-btn {
      opacity: 0;
      transition: opacity 0.2s;
      flex-shrink: 0;
    }
  }
}

.panel-footer {
  padding: 12px 16px;
  border-top: 1px solid #e4e7ed;
  text-align: center;
  flex-shrink: 0;

  .session-count-text {
    font-size: 12px;
    color: #909399;
  }
}

/* 滚动条样式 */
.sessions-list::-webkit-scrollbar {
  width: 6px;
}

.sessions-list::-webkit-scrollbar-track {
  background: #f5f7fa;
  border-radius: 3px;
}

.sessions-list::-webkit-scrollbar-thumb {
  background: #dcdfe6;
  border-radius: 3px;

  &:hover {
    background: #c0c4cc;
  }
}
</style>

