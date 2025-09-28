package com.river.LegalAssistant.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

/**
 * AI 模型配置
 */
@Configuration
@Slf4j
public class AiConfig {

    // Spring AI 1.0.2 使用自动配置，不需要手动创建Ollama相关Bean
    // 所有的Ollama模型配置通过application.yml来管理
    // PGVector向量存储也通过application.yml自动配置

    /**
     * 聊天客户端
     */
    @Bean
    @Primary
    public ChatClient chatClient(@Qualifier("ollamaChatModel") ChatModel chatModel) {
        log.info("初始化聊天客户端，使用Ollama Chat模型");
        return ChatClient.builder(chatModel)
                .defaultSystem("你是一个专业的法律合规智能助手，专门帮助用户进行合同审查和法律咨询。请始终提供准确、专业的法律建议，并在适当时引用相关法律条文。")
                .build();
    }

    /**
     * 指定主要的嵌入模型，解决多个EmbeddingModel Bean的冲突
     */
    @Bean
    @Primary
    public org.springframework.ai.embedding.EmbeddingModel primaryEmbeddingModel(
            @Qualifier("ollamaEmbeddingModel") org.springframework.ai.embedding.EmbeddingModel embeddingModel) {
        log.info("设置 Ollama Embedding 模型为主要嵌入模型");
        return embeddingModel;
    }

    // VectorStore Bean 由 Spring AI 自动配置创建，不需要手动定义
    // AI健康检查现在由Spring AI自动配置处理
}
