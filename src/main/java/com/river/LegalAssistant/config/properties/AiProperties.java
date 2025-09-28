package com.river.LegalAssistant.config.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * AI配置属性
 */
@Data
@Component
@ConfigurationProperties(prefix = "app.ai")
public class AiProperties {
    
    /**
     * 模型配置
     */
    private Models models = new Models();
    
    /**
     * 提示词模板配置
     */
    private Map<String, String> prompts;
    
    /**
     * 模型配置类
     */
    @Data
    public static class Models {
        /**
         * 聊天模型
         */
        private String chat = "qwen3:8b";
        
        /**
         * 嵌入模型
         */
        private String embedding = "nomic-embed-text";
        
        /**
         * 备用模型
         */
        private String fallback = "llama3.2:3b";
    }
}
