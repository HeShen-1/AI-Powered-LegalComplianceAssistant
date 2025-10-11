package com.river.LegalAssistant.config;

import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.TimeUnit;

/**
 * 缓存配置
 * 使用Caffeine实现高性能本地缓存
 */
@Configuration
@EnableCaching
public class CacheConfig {
    
    /**
     * 配置缓存管理器
     */
    @Bean
    public CacheManager cacheManager() {
        CaffeineCacheManager cacheManager = new CaffeineCacheManager();
        
        // 配置缓存参数
        cacheManager.setCaffeine(Caffeine.newBuilder()
                .maximumSize(10000)  // 最大缓存条数
                .expireAfterWrite(1, TimeUnit.HOURS)  // 写入后1小时过期
                .recordStats());  // 启用统计信息
        
        // 定义缓存名称
        cacheManager.setCacheNames(java.util.List.of(
                "embeddings",       // 向量嵌入缓存
                "prompts",          // 提示词模板缓存
                "documents",        // 文档元数据缓存
                "knowledgeStats",   // 知识库统计信息缓存
                "parsedDocuments"   // 已解析文档缓存
        ));
        
        return cacheManager;
    }
}

