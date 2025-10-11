package com.river.LegalAssistant.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.ai.chat.memory.InMemoryChatMemoryRepository;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

import javax.sql.DataSource;

/**
 * 聊天记忆配置类
 * 
 * 基于SpringAI官方文档实现聊天记忆功能：
 * - 为每个AI模型创建独立的聊天记忆存储
 * - 使用InMemory存储作为临时解决方案，稍后可升级为JDBC存储
 * - 禁用SpringAI自动初始化，使用手动配置
 * 
 * 参考: https://docs.spring.io/spring-ai/reference/api/chat-memory.html
 */
@Configuration
@Slf4j
public class ChatMemoryConfig {

    /**
     * 默认聊天记忆，使用Ollama聊天记忆存储库
     */
    @Bean
    @Primary
    public ChatMemory defaultChatMemory(@Qualifier("ollamaChatMemory") ChatMemory ollamaChatMemory) {
        log.info("创建默认聊天记忆，使用Ollama聊天记忆");
        return ollamaChatMemory;
    }

    /**
     * Ollama模型聊天记忆存储库
     * 使用内存存储（临时解决方案）
     */
    @Bean("ollamaChatMemoryRepository")
    public InMemoryChatMemoryRepository ollamaChatMemoryRepository() {
        log.info("创建Ollama聊天记忆存储库（内存存储）");
        return new InMemoryChatMemoryRepository();
    }

    /**
     * DeepSeek模型聊天记忆存储库
     * 使用内存存储（临时解决方案）
     */
    @Bean("deepSeekChatMemoryRepository")
    public InMemoryChatMemoryRepository deepSeekChatMemoryRepository() {
        log.info("创建DeepSeek聊天记忆存储库（内存存储）");
        return new InMemoryChatMemoryRepository();
    }

    /**
     * Ollama模型聊天记忆
     * 保持较少的消息窗口，适合本地模型
     */
    @Bean("ollamaChatMemory")
    public ChatMemory ollamaChatMemory(@Qualifier("ollamaChatMemoryRepository") InMemoryChatMemoryRepository ollamaChatMemoryRepository) {
        log.info("创建Ollama聊天记忆，最大消息数: 15");
        return MessageWindowChatMemory.builder()
                .chatMemoryRepository(ollamaChatMemoryRepository)
                .maxMessages(15) // 本地模型保持较少的上下文
                .build();
    }

    /**
     * DeepSeek模型聊天记忆
     * 保持更多的消息窗口，适合云端高级模型
     */
    @Bean("deepSeekChatMemory")
    public ChatMemory deepSeekChatMemory(@Qualifier("deepSeekChatMemoryRepository") InMemoryChatMemoryRepository deepSeekChatMemoryRepository) {
        log.info("创建DeepSeek聊天记忆，最大消息数: 30");
        return MessageWindowChatMemory.builder()
                .chatMemoryRepository(deepSeekChatMemoryRepository)
                .maxMessages(30) // 高级模型保持更多的上下文
                .build();
    }
}
