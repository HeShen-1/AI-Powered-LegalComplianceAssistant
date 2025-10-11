package com.river.LegalAssistant.health;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * AI服务健康检查
 * 检查Ollama、DeepSeek和向量数据库的可用性
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class AiHealthIndicator implements HealthIndicator {
    
    private final ChatModel ollamaChatModel;
    private final VectorStore vectorStore;
    
    @Override
    public Health health() {
        Map<String, Object> details = new HashMap<>();
        boolean overallHealthy = true;
        
        // 检查Ollama服务
        boolean ollamaHealthy = checkOllama(details);
        if (!ollamaHealthy) {
            overallHealthy = false;
        }
        
        // 检查向量数据库
        boolean vectorStoreHealthy = checkVectorStore(details);
        if (!vectorStoreHealthy) {
            overallHealthy = false;
        }
        
        // 添加总体状态
        details.put("overall", overallHealthy ? "UP" : "DOWN");
        
        return overallHealthy 
            ? Health.up().withDetails(details).build() 
            : Health.down().withDetails(details).build();
    }
    
    /**
     * 检查Ollama服务
     */
    private boolean checkOllama(Map<String, Object> details) {
        try {
            // 发送简单的测试消息
            String response = ollamaChatModel.call("test");
            if (response != null && !response.isEmpty()) {
                details.put("ollama", "UP");
                details.put("ollamaMessage", "服务正常");
                return true;
            } else {
                details.put("ollama", "DOWN");
                details.put("ollamaMessage", "响应为空");
                return false;
            }
        } catch (Exception e) {
            log.warn("Ollama服务健康检查失败: {}", e.getMessage());
            details.put("ollama", "DOWN");
            details.put("ollamaError", e.getMessage());
            return false;
        }
    }
    
    /**
     * 检查向量数据库
     */
    private boolean checkVectorStore(Map<String, Object> details) {
        try {
            // 执行简单的相似性搜索测试
            vectorStore.similaritySearch("test");
            details.put("vectorStore", "UP");
            details.put("vectorStoreMessage", "服务正常");
            return true;
        } catch (Exception e) {
            log.error("向量数据库健康检查失败: {}", e.getMessage());
            details.put("vectorStore", "DOWN");
            details.put("vectorStoreError", e.getMessage());
            return false;
        }
    }
}

