package com.river.LegalAssistant.config.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * RAG（检索增强生成）配置属性
 */
@Data
@Component
@ConfigurationProperties(prefix = "app.rag")
public class RagProperties {
    
    /**
     * 文档分块大小
     */
    private int chunkSize = 1000;
    
    /**
     * 分块重叠大小
     */
    private int chunkOverlap = 100;
    
    /**
     * 相似度阈值
     */
    private double similarityThreshold = 0.7;
    
    /**
     * 最大结果数量
     */
    private int maxResults = 5;
}
