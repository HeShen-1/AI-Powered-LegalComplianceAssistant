package com.river.LegalAssistant.config;

import com.river.LegalAssistant.service.PromptTemplateService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Scope;

/**
 * AI 模型配置
 * 
 * 根据Spring AI 1.0.2官方文档配置多个Chat模型：
 * - Ollama模型：用于基础AI服务
 * - DeepSeek模型：用于高级AI服务和Agent功能
 * 
 * 使用PromptTemplateService集中管理系统提示语，遵循DRY原则
 */
@Configuration
@RequiredArgsConstructor
@Slf4j
public class AiConfig {

    private final PromptTemplateService promptTemplateService;

    // Spring AI 1.0.2 使用自动配置，不需要手动创建相关Bean
    // 所有的模型配置通过application.yml来管理
    // PGVector向量存储也通过application.yml自动配置

    /**
     * 基础聊天客户端 - 使用Ollama模型
     */
    @Bean("basicChatClient")
    public ChatClient basicChatClient(@Qualifier("ollamaChatModel") ChatModel ollamaChatModel) {
        log.info("初始化基础聊天客户端，使用Ollama Chat模型");
        return ChatClient.builder(ollamaChatModel)
                .defaultSystem(promptTemplateService.getBasicLegalSystemPrompt())
                .build();
    }

    /**
     * 高级聊天客户端 - 使用DeepSeek模型
     * 根据官方文档，DeepSeek模型支持工具调用和推理能力
     */
    @Bean("advancedChatClient")
    public ChatClient advancedChatClient(@Qualifier("deepSeekChatModel") ChatModel deepSeekChatModel) {
        log.info("初始化高级聊天客户端，使用DeepSeek Chat模型");
        return ChatClient.builder(deepSeekChatModel)
                .defaultSystem(promptTemplateService.getAdvancedLegalSystemPrompt())
                .build();
    }

    /**
     * 默认聊天客户端 - 优先使用DeepSeek模型，降级到Ollama
     */
    @Bean
    @Primary
    public ChatClient chatClient(@Qualifier("deepSeekChatModel") ChatModel deepSeekChatModel, 
                                @Qualifier("ollamaChatModel") ChatModel ollamaChatModel) {
        try {
            log.info("初始化主要聊天客户端，优先使用DeepSeek模型");
            return ChatClient.builder(deepSeekChatModel)
                    .defaultSystem(promptTemplateService.getBasicLegalSystemPrompt())
                    .build();
        } catch (Exception e) {
            log.warn("DeepSeek模型不可用，降级使用Ollama模型: {}", e.getMessage());
            return ChatClient.builder(ollamaChatModel)
                    .defaultSystem(promptTemplateService.getBasicLegalSystemPrompt())
                    .build();
        }
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

    /**
     * Prototype 作用域的 ChatClient.Builder - 用于创建带聊天记忆的客户端
     * 每次注入时都会创建新实例，同时由Spring容器管理生命周期
     * 调用者需要自行添加 ChatMemory advisor 和系统提示
     * 
     * @param chatModel 聊天模型（DeepSeek）
     * @return ChatClient.Builder 实例（未配置ChatMemory）
     */
    @Bean
    @Scope("prototype")
    public ChatClient.Builder chatClientBuilder(
            @Qualifier("deepSeekChatModel") ChatModel chatModel) {
        log.debug("创建 ChatClient.Builder (prototype)");
        return ChatClient.builder(chatModel);
    }

    // 注意：在Spring AI 1.0.2中，QuestionAnswerAdvisor和QueryTransformer可能不可用
    // 这些功能将通过其他方式实现

    // VectorStore Bean 由 Spring AI 自动配置创建，不需要手动定义
    // AI健康检查现在由Spring AI自动配置处理
}
