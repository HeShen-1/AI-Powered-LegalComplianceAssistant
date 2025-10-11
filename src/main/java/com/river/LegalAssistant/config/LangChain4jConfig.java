package com.river.LegalAssistant.config;

import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.ollama.OllamaChatModel;
import dev.langchain4j.model.ollama.OllamaStreamingChatModel;
import dev.langchain4j.model.ollama.OllamaEmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.pgvector.PgVectorEmbeddingStore;
import dev.langchain4j.store.embedding.inmemory.InMemoryEmbeddingStore;
import dev.langchain4j.http.client.jdk.JdkHttpClientBuilder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

import java.time.Duration;

/**
 * LangChain4j 配置类
 * 
 * 为Advanced RAG功能配置独立的向量存储系统
 * 使用独立的表名以避免与Spring AI冲突
 */
@Configuration
@Slf4j
public class LangChain4jConfig {

    @Value("${spring.ai.ollama.base-url:http://localhost:11434}")
    private String ollamaBaseUrl;
    
    @Value("${app.ai.models.basic.chat:qwen2:1.5b}")
    private String chatModelName;
    
    @Value("${app.ai.models.basic.embedding:nomic-embed-text}")
    private String embeddingModelName;
    
    // 数据库配置
    @Value("${spring.datasource.url}")
    private String databaseUrl;
    
    @Value("${spring.datasource.username}")
    private String databaseUsername;
    
    @Value("${spring.datasource.password}")
    private String databasePassword;
    
    @Value("${spring.ai.vectorstore.pgvector.dimensions:768}")
    private Integer dimensions;

    /**
     * LangChain4j 聊天模型
     * 用于Advanced RAG中的查询路由和内容生成
     */
    @Bean("langchain4jChatModel")
    public ChatModel langchain4jChatModel() {
        log.info("初始化 LangChain4j 聊天模型: {}", chatModelName);
        
        return OllamaChatModel.builder()
                .baseUrl(ollamaBaseUrl)
                .modelName(chatModelName)
                .temperature(0.3)
                .timeout(Duration.ofMinutes(2))
                .httpClientBuilder(new JdkHttpClientBuilder())
                .build();
    }

    /**
     * LangChain4j 流式聊天模型
     * 用于Advanced RAG的流式输出
     */
    @Bean("langchain4jStreamingChatModel")
    public StreamingChatModel langchain4jStreamingChatModel() {
        log.info("初始化 LangChain4j 流式聊天模型: {}", chatModelName);
        
        return OllamaStreamingChatModel.builder()
                .baseUrl(ollamaBaseUrl)
                .modelName(chatModelName)
                .temperature(0.3)
                .timeout(Duration.ofMinutes(2))
                .httpClientBuilder(new JdkHttpClientBuilder())
                .build();
    }

    /**
     * LangChain4j 嵌入模型
     * 用于文档向量化和查询向量化
     */
    @Bean("langchain4jEmbeddingModel")
    public EmbeddingModel langchain4jEmbeddingModel() {
        log.info("初始化 LangChain4j 嵌入模型: {}", embeddingModelName);
        
        return OllamaEmbeddingModel.builder()
                .baseUrl(ollamaBaseUrl)
                .modelName(embeddingModelName)
                .timeout(Duration.ofMinutes(1))
                .httpClientBuilder(new JdkHttpClientBuilder())
                .build();
    }

    /**
     * LangChain4j PGVector 嵌入存储
     * 使用独立的表名以避免与Spring AI冲突
     * 仅在非测试环境中创建
     */
    @Bean("langchain4jEmbeddingStore")
    @Profile("!test")
    public EmbeddingStore<TextSegment> langchain4jEmbeddingStore() {
        log.info("初始化 LangChain4j PGVector 嵌入存储（独立表）");
        
        try {
            // 从完整的JDBC URL中提取数据库连接信息
            String host = extractHost(databaseUrl);
            int port = extractPort(databaseUrl);
            String database = extractDatabase(databaseUrl);
            
            // 使用独立的表名以避免与Spring AI冲突
            String tableName = "langchain4j_embeddings";
            
            log.info("连接 PGVector 数据库: {}:{}/{}, 表: {}, 维度: {}", 
                    host, port, database, tableName, dimensions);
            
            return PgVectorEmbeddingStore.builder()
                    .host(host)
                    .port(port)
                    .database(database)
                    .user(databaseUsername)
                    .password(databasePassword)
                    .table(tableName)  // 使用独立表名
                    .dimension(dimensions)
                    .createTable(true)  // 自动创建表
                    .dropTableFirst(false)  // 不删除现有表
                    .build();
                    
        } catch (Exception e) {
            log.error("初始化 LangChain4j PGVector 嵌入存储失败", e);
            throw new RuntimeException("无法初始化LangChain4j向量数据库连接: " + e.getMessage(), e);
        }
    }

    /**
     * 从JDBC URL中提取主机名
     */
    private String extractHost(String jdbcUrl) {
        // jdbc:postgresql://localhost:5432/legal_assistant
        String[] parts = jdbcUrl.split("//")[1].split(":");
        return parts[0];
    }

    /**
     * 从JDBC URL中提取端口
     */
    private int extractPort(String jdbcUrl) {
        try {
            String[] parts = jdbcUrl.split("//")[1].split(":");
            if (parts.length > 1) {
                String portPart = parts[1].split("/")[0];
                return Integer.parseInt(portPart);
            }
        } catch (Exception e) {
            log.warn("无法从JDBC URL提取端口，使用默认端口5432", e);
        }
        return 5432; // PostgreSQL默认端口
    }

    /**
     * 从JDBC URL中提取数据库名
     */
    private String extractDatabase(String jdbcUrl) {
        // jdbc:postgresql://localhost:5432/legal_assistant
        String[] parts = jdbcUrl.split("/");
        if (parts.length > 0) {
            String dbPart = parts[parts.length - 1];
            // 去除可能的查询参数
            return dbPart.split("\\?")[0];
        }
        return "legal_assistant";
    }

    /**
     * 测试环境使用内存嵌入存储
     */
    @Bean("langchain4jEmbeddingStore")
    @Profile("test")
    public EmbeddingStore<TextSegment> testEmbeddingStore() {
        log.info("初始化测试环境内存嵌入存储");
        return new InMemoryEmbeddingStore<>();
    }
}
