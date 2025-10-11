package com.river.LegalAssistant.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

/**
 * 聊天记忆管理服务
 * 
 * 提供跨不同AI模型的聊天记忆管理功能：
 * - Ollama模型聊天记忆管理
 * - DeepSeek模型聊天记忆管理
 * - 会话生命周期管理
 * - 记忆清理和维护
 * 
 * 基于SpringAI ChatMemory实现
 */
@Service
@Slf4j
public class ChatMemoryService {

    private final ChatMemory ollamaChatMemory;
    private final ChatMemory deepSeekChatMemory;

    public ChatMemoryService(@Qualifier("ollamaChatMemory") ChatMemory ollamaChatMemory,
                            @Qualifier("deepSeekChatMemory") ChatMemory deepSeekChatMemory) {
        this.ollamaChatMemory = ollamaChatMemory;
        this.deepSeekChatMemory = deepSeekChatMemory;
        log.info("聊天记忆服务初始化完成");
    }

    /**
     * AI模型类型枚举
     */
    public enum ModelType {
        OLLAMA("ollama"),
        DEEPSEEK("deepseek");

        private final String value;

        ModelType(String value) {
            this.value = value;
        }

        public String getValue() {
            return value;
        }
    }

    /**
     * 获取指定模型的聊天记忆
     */
    public ChatMemory getChatMemory(ModelType modelType) {
        return switch (modelType) {
            case OLLAMA -> ollamaChatMemory;
            case DEEPSEEK -> deepSeekChatMemory;
        };
    }

    /**
     * 获取会话的聊天历史
     * 
     * @param conversationId 会话ID
     * @param modelType AI模型类型
     * @return 聊天历史消息列表
     */
    public List<org.springframework.ai.chat.messages.Message> getChatHistory(String conversationId, ModelType modelType) {
        log.debug("获取会话聊天历史: conversationId={}, modelType={}", conversationId, modelType);
        
        try {
            ChatMemory chatMemory = getChatMemory(modelType);
            List<org.springframework.ai.chat.messages.Message> messages = chatMemory.get(conversationId);
            
            log.info("成功获取会话 {} 的聊天历史，共 {} 条消息 (模型: {})", 
                    conversationId, messages.size(), modelType.getValue());
            
            return messages;
        } catch (Exception e) {
            log.error("获取聊天历史失败: conversationId={}, modelType={}", conversationId, modelType, e);
            throw new RuntimeException("聊天历史获取失败", e);
        }
    }

    /**
     * 添加消息到聊天记忆
     * 
     * @param conversationId 会话ID
     * @param message 消息
     * @param modelType AI模型类型
     */
    public void addMessage(String conversationId, org.springframework.ai.chat.messages.Message message, ModelType modelType) {
        log.debug("添加消息到聊天记忆: conversationId={}, messageType={}, modelType={}", 
                conversationId, message.getMessageType(), modelType);
        
        try {
            ChatMemory chatMemory = getChatMemory(modelType);
            chatMemory.add(conversationId, message);
            
            log.info("成功添加消息到会话 {} 的聊天记忆 (模型: {}, 消息类型: {})", 
                    conversationId, modelType.getValue(), message.getMessageType());
            
        } catch (Exception e) {
            log.error("添加消息到聊天记忆失败: conversationId={}, modelType={}", conversationId, modelType, e);
            throw new RuntimeException("聊天记忆添加失败", e);
        }
    }

    /**
     * 批量添加消息到聊天记忆
     * 
     * @param conversationId 会话ID
     * @param messages 消息列表
     * @param modelType AI模型类型
     */
    public void addMessages(String conversationId, List<org.springframework.ai.chat.messages.Message> messages, ModelType modelType) {
        log.debug("批量添加消息到聊天记忆: conversationId={}, messageCount={}, modelType={}", 
                conversationId, messages.size(), modelType);
        
        try {
            ChatMemory chatMemory = getChatMemory(modelType);
            
            for (org.springframework.ai.chat.messages.Message message : messages) {
                chatMemory.add(conversationId, message);
            }
            
            log.info("成功批量添加 {} 条消息到会话 {} 的聊天记忆 (模型: {})", 
                    messages.size(), conversationId, modelType.getValue());
            
        } catch (Exception e) {
            log.error("批量添加消息到聊天记忆失败: conversationId={}, modelType={}", conversationId, modelType, e);
            throw new RuntimeException("聊天记忆批量添加失败", e);
        }
    }

    /**
     * 清除指定会话的聊天记忆
     * 
     * @param conversationId 会话ID
     * @param modelType AI模型类型
     */
    public void clearChatMemory(String conversationId, ModelType modelType) {
        log.debug("清除会话聊天记忆: conversationId={}, modelType={}", conversationId, modelType);
        
        try {
            ChatMemory chatMemory = getChatMemory(modelType);
            chatMemory.clear(conversationId);
            
            log.info("成功清除会话 {} 的聊天记忆 (模型: {})", conversationId, modelType.getValue());
            
        } catch (Exception e) {
            log.error("清除聊天记忆失败: conversationId={}, modelType={}", conversationId, modelType, e);
            throw new RuntimeException("聊天记忆清除失败", e);
        }
    }

    /**
     * 清除所有模型的指定会话聊天记忆
     * 
     * @param conversationId 会话ID
     */
    public void clearAllModelsChatMemory(String conversationId) {
        log.debug("清除所有模型的会话聊天记忆: conversationId={}", conversationId);
        
        for (ModelType modelType : ModelType.values()) {
            try {
                clearChatMemory(conversationId, modelType);
            } catch (Exception e) {
                log.warn("清除模型 {} 的会话 {} 聊天记忆失败: {}", modelType.getValue(), conversationId, e.getMessage());
            }
        }
        
        log.info("完成清除所有模型的会话 {} 聊天记忆", conversationId);
    }

    /**
     * 生成新的会话ID
     * 
     * @return 新的会话ID
     */
    public String generateConversationId() {
        String conversationId = "chat-" + UUID.randomUUID().toString();
        log.debug("生成新的会话ID: {}", conversationId);
        return conversationId;
    }

    /**
     * 生成带用户前缀的会话ID
     * 
     * @param userId 用户ID
     * @return 新的会话ID
     */
    public String generateConversationId(String userId) {
        String conversationId = "user-" + userId + "-chat-" + UUID.randomUUID().toString();
        log.debug("生成用户会话ID: {}", conversationId);
        return conversationId;
    }

    /**
     * 检查会话是否存在聊天记忆
     * 
     * @param conversationId 会话ID
     * @param modelType AI模型类型
     * @return 是否存在聊天记忆
     */
    public boolean hasMemory(String conversationId, ModelType modelType) {
        try {
            List<org.springframework.ai.chat.messages.Message> messages = getChatHistory(conversationId, modelType);
            boolean hasMemory = !messages.isEmpty();
            
            log.debug("检查会话记忆存在性: conversationId={}, modelType={}, hasMemory={}", 
                    conversationId, modelType, hasMemory);
            
            return hasMemory;
        } catch (Exception e) {
            log.warn("检查会话记忆存在性失败: conversationId={}, modelType={}", conversationId, modelType, e);
            return false;
        }
    }

    /**
     * 获取会话的消息数量
     * 
     * @param conversationId 会话ID
     * @param modelType AI模型类型
     * @return 消息数量
     */
    public int getMessageCount(String conversationId, ModelType modelType) {
        try {
            List<org.springframework.ai.chat.messages.Message> messages = getChatHistory(conversationId, modelType);
            int count = messages.size();
            
            log.debug("获取会话消息数量: conversationId={}, modelType={}, count={}", 
                    conversationId, modelType, count);
            
            return count;
        } catch (Exception e) {
            log.warn("获取会话消息数量失败: conversationId={}, modelType={}", conversationId, modelType, e);
            return 0;
        }
    }

    /**
     * 获取健康状态信息
     * 
     * @return 健康状态信息
     */
    public ChatMemoryHealthInfo getHealthInfo() {
        ChatMemoryHealthInfo healthInfo = new ChatMemoryHealthInfo();
        
        try {
            // 测试Ollama聊天记忆连接
            String testConversationId = "health-check-" + System.currentTimeMillis();
            ollamaChatMemory.get(testConversationId);
            healthInfo.setOllamaMemoryHealthy(true);
        } catch (Exception e) {
            log.warn("Ollama聊天记忆健康检查失败", e);
            healthInfo.setOllamaMemoryHealthy(false);
            healthInfo.setOllamaMemoryError(e.getMessage());
        }

        try {
            // 测试DeepSeek聊天记忆连接
            String testConversationId = "health-check-" + System.currentTimeMillis();
            deepSeekChatMemory.get(testConversationId);
            healthInfo.setDeepSeekMemoryHealthy(true);
        } catch (Exception e) {
            log.warn("DeepSeek聊天记忆健康检查失败", e);
            healthInfo.setDeepSeekMemoryHealthy(false);
            healthInfo.setDeepSeekMemoryError(e.getMessage());
        }

        healthInfo.setOverallHealthy(healthInfo.isOllamaMemoryHealthy() && healthInfo.isDeepSeekMemoryHealthy());
        
        log.debug("聊天记忆健康状态: {}", healthInfo);
        return healthInfo;
    }

    /**
     * 聊天记忆健康状态信息类
     */
    public static class ChatMemoryHealthInfo {
        private boolean overallHealthy;
        private boolean ollamaMemoryHealthy;
        private boolean deepSeekMemoryHealthy;
        private String ollamaMemoryError;
        private String deepSeekMemoryError;

        // Getters and Setters
        public boolean isOverallHealthy() {
            return overallHealthy;
        }

        public void setOverallHealthy(boolean overallHealthy) {
            this.overallHealthy = overallHealthy;
        }

        public boolean isOllamaMemoryHealthy() {
            return ollamaMemoryHealthy;
        }

        public void setOllamaMemoryHealthy(boolean ollamaMemoryHealthy) {
            this.ollamaMemoryHealthy = ollamaMemoryHealthy;
        }

        public boolean isDeepSeekMemoryHealthy() {
            return deepSeekMemoryHealthy;
        }

        public void setDeepSeekMemoryHealthy(boolean deepSeekMemoryHealthy) {
            this.deepSeekMemoryHealthy = deepSeekMemoryHealthy;
        }

        public String getOllamaMemoryError() {
            return ollamaMemoryError;
        }

        public void setOllamaMemoryError(String ollamaMemoryError) {
            this.ollamaMemoryError = ollamaMemoryError;
        }

        public String getDeepSeekMemoryError() {
            return deepSeekMemoryError;
        }

        public void setDeepSeekMemoryError(String deepSeekMemoryError) {
            this.deepSeekMemoryError = deepSeekMemoryError;
        }

        @Override
        public String toString() {
            return "ChatMemoryHealthInfo{" +
                    "overallHealthy=" + overallHealthy +
                    ", ollamaMemoryHealthy=" + ollamaMemoryHealthy +
                    ", deepSeekMemoryHealthy=" + deepSeekMemoryHealthy +
                    ", ollamaMemoryError='" + ollamaMemoryError + '\'' +
                    ", deepSeekMemoryError='" + deepSeekMemoryError + '\'' +
                    '}';
        }
    }
}
