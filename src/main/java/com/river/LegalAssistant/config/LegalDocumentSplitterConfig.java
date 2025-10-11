package com.river.LegalAssistant.config;

import com.river.LegalAssistant.service.splitter.LegalDocumentSplitter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 法律文档分割器配置类
 * 
 * <p>提供法律文档分割器的配置和Bean定义,支持通过配置文件自定义分割参数。
 * 
 * @author River
 */
@Configuration
@Slf4j
public class LegalDocumentSplitterConfig {
    
    /**
     * 最大Token限制
     * 默认512,适用于大多数Embedding模型
     */
    @Value("${app.rag.legal-splitter.max-tokens:512}")
    private int maxTokens;
    
    /**
     * 是否启用层级结构解析
     * 默认true,启用编、章、节、条的层级识别
     */
    @Value("${app.rag.legal-splitter.enable-hierarchical:true}")
    private boolean enableHierarchicalParsing;
    
    /**
     * 二次分割时的重叠字符数
     * 默认50,用于保持上下文连续性
     */
    @Value("${app.rag.legal-splitter.chunk-overlap:50}")
    private int chunkOverlap;
    
    /**
     * 是否启用法律文档分割器
     * 默认true,可通过配置禁用以回退到通用分割器
     */
    @Value("${app.rag.legal-splitter.enabled:true}")
    private boolean enabled;
    
    /**
     * 创建法律文档分割器Bean
     * 
     * @return LegalDocumentSplitter实例
     */
    @Bean
    public LegalDocumentSplitter legalDocumentSplitter() {
        if (!enabled) {
            log.warn("法律文档分割器已禁用,将使用通用分割器");
            return null;
        }
        
        log.info("初始化法律文档分割器配置:");
        log.info("  - 最大Token数: {}", maxTokens);
        log.info("  - 层级结构解析: {}", enableHierarchicalParsing);
        log.info("  - 重叠字符数: {}", chunkOverlap);
        
        return new LegalDocumentSplitter(maxTokens, enableHierarchicalParsing, chunkOverlap);
    }
}

