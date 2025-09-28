package com.river.LegalAssistant.config.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 应用配置属性总类
 */
@Data
@Component
@ConfigurationProperties(prefix = "app")
public class AppProperties {
    
    /**
     * 文件上传配置
     */
    private UploadProperties upload = new UploadProperties();
    
    /**
     * RAG配置
     */
    private RagProperties rag = new RagProperties();
    
    /**
     * AI配置
     */
    private AiProperties ai = new AiProperties();
}
