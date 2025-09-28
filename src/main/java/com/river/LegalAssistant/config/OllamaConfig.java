package com.river.LegalAssistant.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;

/**
 * Ollama 专用配置
 * 确保只使用本地 Ollama 模型，不依赖任何云服务
 */
@Configuration
@Slf4j
@ConditionalOnProperty(name = "spring.ai.ollama.base-url")
public class OllamaConfig {

    public OllamaConfig() {
        log.info("=== Ollama 配置初始化 ===");
        log.info("使用本地 Ollama 服务，不依赖任何云端 AI 服务");
        log.info("确保 Ollama 服务运行在 http://localhost:11434");
        log.info("推荐模型:");
        log.info("  - 聊天模型: qwen2:1.5b, llama3.2:3b");
        log.info("  - 嵌入模型: nomic-embed-text");
        log.info("========================");
    }
}
