package com.river.LegalAssistant.service;

import com.river.LegalAssistant.entity.KnowledgeDocument;
import com.river.LegalAssistant.repository.KnowledgeDocumentRepository;
import com.river.LegalAssistant.service.splitter.LegalDocumentSplitter;
import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.DocumentSplitter;
import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.document.splitter.DocumentSplitters;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.EmbeddingStoreIngestor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

/**
 * 向量数据库管理服务
 * 负责重建、清理和优化向量数据库内容
 * 支持法律文档专用分割器
 */
@Service
@Slf4j
public class VectorDatabaseManagementService {

    private final VectorStore springAiVectorStore;
    private final KnowledgeDocumentRepository knowledgeDocumentRepository;
    private final JdbcTemplate jdbcTemplate;
    private final EtlService etlService; // 保留用于Spring AI向量存储
    private final DocumentProcessingService documentProcessingService; // 新的统一处理服务
    private final EmbeddingStore<TextSegment> langchain4jEmbeddingStore;
    private final dev.langchain4j.model.embedding.EmbeddingModel langchain4jEmbeddingModel;
    private final LegalDocumentSplitter legalDocumentSplitter; // 保留用于向后兼容

    @Value("${app.etl.chunk-size:2000}")
    private int chunkSize;
    
    @Value("${app.etl.chunk-overlap:400}")
    private int chunkOverlap;
    
    @Value("${app.etl.min-chunk-size:50}")
    private int minChunkSize;
    
    /**
     * 构造函数
     */
    public VectorDatabaseManagementService(
            VectorStore springAiVectorStore,
            KnowledgeDocumentRepository knowledgeDocumentRepository,
            JdbcTemplate jdbcTemplate,
            EtlService etlService,
            DocumentProcessingService documentProcessingService,
            @Qualifier("langchain4jEmbeddingStore") EmbeddingStore<TextSegment> langchain4jEmbeddingStore,
            @Qualifier("langchain4jEmbeddingModel") dev.langchain4j.model.embedding.EmbeddingModel langchain4jEmbeddingModel,
            LegalDocumentSplitter legalDocumentSplitter) {
        this.springAiVectorStore = springAiVectorStore;
        this.knowledgeDocumentRepository = knowledgeDocumentRepository;
        this.jdbcTemplate = jdbcTemplate;
        this.etlService = etlService;
        this.documentProcessingService = documentProcessingService;
        this.langchain4jEmbeddingStore = langchain4jEmbeddingStore;
        this.langchain4jEmbeddingModel = langchain4jEmbeddingModel;
        this.legalDocumentSplitter = legalDocumentSplitter;
        log.info("VectorDatabaseManagementService 初始化完成,已启用统一文档处理服务和法律文档分割器");
    }

    /**
     * 完全重建向量数据库
     * 清理所有现有数据并重新索引所有文档
     */
    @Transactional
    public CompletableFuture<VectorRebuildResult> rebuildAllVectorDatabases() {
        log.info("开始完全重建向量数据库，chunk配置: size={}, overlap={}, minSize={}", 
                chunkSize, chunkOverlap, minChunkSize);
        
        long startTime = System.currentTimeMillis();
        VectorRebuildResult.VectorRebuildResultBuilder resultBuilder = VectorRebuildResult.builder()
            .startTime(LocalDateTime.now())
            .success(false)
            .chunkSize(chunkSize)
            .chunkOverlap(chunkOverlap);

        return CompletableFuture.supplyAsync(() -> {
            try {
                // 1. 清理现有向量数据
                log.info("步骤1: 清理现有向量数据");
                int springAiClearedCount = clearSpringAiVectorStore();
                int langchain4jClearedCount = clearLangChain4jEmbeddingStore();
                
                log.info("向量数据清理完成 - Spring AI: {} 条, LangChain4j: {} 条", 
                        springAiClearedCount, langchain4jClearedCount);

                // 2. 获取所有需要重新索引的文档
                log.info("步骤2: 获取所有文档进行重新索引");
                List<KnowledgeDocument> allDocuments = knowledgeDocumentRepository.findAll();
                log.info("发现 {} 个文档需要重新索引", allDocuments.size());

                if (allDocuments.isEmpty()) {
                    log.warn("没有找到任何文档进行重新索引");
                    return resultBuilder
                        .success(true)
                        .message("向量数据库已清理，但没有文档需要重新索引")
                        .springAiClearedCount(springAiClearedCount)
                        .langchain4jClearedCount(langchain4jClearedCount)
                        .processedDocuments(0)
                        .totalChunks(0)
                        .endTime(LocalDateTime.now())
                        .durationMs(System.currentTimeMillis() - startTime)
                        .build();
                }

                // 3. 重新索引所有文档
                log.info("步骤3: 重新索引所有文档");
                AtomicInteger processedCount = new AtomicInteger(0);
                AtomicInteger totalChunks = new AtomicInteger(0);
                AtomicInteger failedCount = new AtomicInteger(0);

                for (KnowledgeDocument doc : allDocuments) {
                    try {
                        log.debug("正在处理文档: {} (ID: {})", doc.getTitle(), doc.getId());
                        
                        // 处理Spring AI向量存储
                        EtlService.EtlProcessResult springResult = processDocumentForSpringAI(doc);
                        
                        // 处理LangChain4j向量存储
                        int langchainChunks = processDocumentForLangChain4j(doc);
                        
                        if (springResult.success() && langchainChunks > 0) {
                            processedCount.incrementAndGet();
                            totalChunks.addAndGet(springResult.chunkCount() + langchainChunks);
                            log.debug("文档处理完成: {} - Spring AI: {} 块, LangChain4j: {} 块", 
                                    doc.getTitle(), springResult.chunkCount(), langchainChunks);
                        } else {
                            failedCount.incrementAndGet();
                            log.error("文档处理失败: {} - Spring AI成功: {}, LangChain4j块数: {}", 
                                    doc.getTitle(), springResult.success(), langchainChunks);
                        }
                        
                        // 每处理10个文档记录一次进度
                        if (processedCount.get() % 10 == 0) {
                            log.info("进度: {}/{} 文档已处理, 总块数: {}", 
                                    processedCount.get(), allDocuments.size(), totalChunks.get());
                        }
                        
                    } catch (Exception e) {
                        failedCount.incrementAndGet();
                        log.error("处理文档时发生异常: {} (ID: {})", doc.getTitle(), doc.getId(), e);
                    }
                }

                long duration = System.currentTimeMillis() - startTime;
                log.info("向量数据库重建完成 - 处理文档: {}/{}, 总块数: {}, 耗时: {}ms", 
                        processedCount.get(), allDocuments.size(), totalChunks.get(), duration);

                return resultBuilder
                    .success(true)
                    .message("向量数据库重建完成")
                    .springAiClearedCount(springAiClearedCount)
                    .langchain4jClearedCount(langchain4jClearedCount)
                    .processedDocuments(processedCount.get())
                    .failedDocuments(failedCount.get())
                    .totalChunks(totalChunks.get())
                    .endTime(LocalDateTime.now())
                    .durationMs(duration)
                    .build();

            } catch (Exception e) {
                log.error("向量数据库重建失败", e);
                return resultBuilder
                    .success(false)
                    .message("向量数据库重建失败: " + e.getMessage())
                    .endTime(LocalDateTime.now())
                    .durationMs(System.currentTimeMillis() - startTime)
                    .build();
            }
        });
    }

    /**
     * 处理单个文档用于Spring AI向量存储
     */
    private EtlService.EtlProcessResult processDocumentForSpringAI(KnowledgeDocument doc) {
        try {
            if (doc.getContent() == null || doc.getContent().trim().isEmpty()) {
                log.warn("文档内容为空，跳过: {}", doc.getTitle());
                return new EtlService.EtlProcessResult(false, 0, 0, "文档内容为空");
            }

            // 准备元数据
            Map<String, Object> metadata = Map.of(
                "doc_id", doc.getId().toString(),
                "original_filename", doc.getTitle(),
                "document_type", doc.getDocumentType(),
                "source_file", doc.getSourceFile(),
                "file_hash", doc.getFileHash(),
                "source_type", "knowledge_base",
                "indexed_at", LocalDateTime.now().toString()
            );

            // 使用EtlService处理文档
            return etlService.processTextContent(doc.getContent(), metadata);

        } catch (Exception e) {
            log.error("Spring AI处理文档失败: {}", doc.getTitle(), e);
            return new EtlService.EtlProcessResult(false, 0, 0, "处理失败: " + e.getMessage());
        }
    }

    /**
     * 处理单个文档用于LangChain4j向量存储
     */
    private int processDocumentForLangChain4j(KnowledgeDocument doc) {
        try {
            if (doc.getContent() == null || doc.getContent().trim().isEmpty()) {
                log.warn("文档内容为空，跳过LangChain4j处理: {}", doc.getTitle());
                return 0;
            }

            // 使用新的统一文档处理服务
            DocumentProcessingService.ProcessingResult result = 
                documentProcessingService.processKnowledgeDocument(doc);
            
            if (result.isSuccess()) {
                log.debug("LangChain4j处理完成: {} - {} 个片段, 分割器: {}", 
                        doc.getTitle(), result.getSegmentCount(), result.getSplitterType());
                return result.getSegmentCount();
            } else {
                log.error("LangChain4j处理文档失败: {} - {}", doc.getTitle(), result.getMessage());
                return 0;
            }

        } catch (Exception e) {
            log.error("LangChain4j处理文档失败: {}", doc.getTitle(), e);
            return 0;
        }
    }
    
    /**
     * 根据文档类型选择合适的文档分割器
     */
    private DocumentSplitter selectSplitter(KnowledgeDocument doc) {
        String documentType = doc.getDocumentType();
        String fileName = doc.getTitle().toLowerCase();
        
        // 判断是否是法律文档
        boolean isLegalDocument = "LAW".equals(documentType) || 
                                 "REGULATION".equals(documentType) ||
                                 fileName.contains("法") || 
                                 fileName.contains("law") || 
                                 fileName.contains("法律") || 
                                 fileName.contains("法规") ||
                                 fileName.contains("条例") ||
                                 fileName.contains("规定");
        
        if (isLegalDocument && legalDocumentSplitter != null) {
            log.debug("文档 {} 识别为法律文档,使用法律文档分割器", doc.getTitle());
            return legalDocumentSplitter;
        } else {
            log.debug("文档 {} 使用通用递归分割器", doc.getTitle());
            return DocumentSplitters.recursive(chunkSize, chunkOverlap);
        }
    }

    /**
     * 清理Spring AI向量存储
     */
    private int clearSpringAiVectorStore() {
        try {
            // 获取清理前的数量
            int beforeCount = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM vector_store", Integer.class);
            
            // 清理数据
            jdbcTemplate.execute("TRUNCATE TABLE vector_store");
            
            log.info("Spring AI向量存储已清理，删除了 {} 条记录", beforeCount);
            return beforeCount;
        } catch (Exception e) {
            log.error("清理Spring AI向量存储失败", e);
            return 0;
        }
    }

    /**
     * 清理LangChain4j嵌入存储
     */
    private int clearLangChain4jEmbeddingStore() {
        try {
            // 获取清理前的数量
            int beforeCount = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM langchain4j_embeddings", Integer.class);
            
            // 清理数据
            jdbcTemplate.execute("TRUNCATE TABLE langchain4j_embeddings");
            
            log.info("LangChain4j嵌入存储已清理，删除了 {} 条记录", beforeCount);
            return beforeCount;
        } catch (Exception e) {
            log.error("清理LangChain4j嵌入存储失败", e);
            return 0;
        }
    }

    /**
     * 获取向量数据库统计信息
     */
    public VectorDatabaseStats getVectorDatabaseStats() {
        try {
            // 获取Spring AI统计
            Integer springAiCount = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM vector_store", Integer.class);
            
            // 获取LangChain4j统计  
            Integer langchain4jCount = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM langchain4j_embeddings", Integer.class);
            
            // 获取源文档统计
            Long sourceDocumentsCount = knowledgeDocumentRepository.count();

            return VectorDatabaseStats.builder()
                .springAiVectorCount(springAiCount != null ? springAiCount : 0)
                .langchain4jEmbeddingCount(langchain4jCount != null ? langchain4jCount : 0)
                .sourceDocumentsCount(sourceDocumentsCount)
                .chunkSize(chunkSize)
                .chunkOverlap(chunkOverlap)
                .minChunkSize(minChunkSize)
                .build();
        } catch (Exception e) {
            log.error("获取向量数据库统计信息失败", e);
            return VectorDatabaseStats.builder()
                .springAiVectorCount(0)
                .langchain4jEmbeddingCount(0)
                .sourceDocumentsCount(0L)
                .chunkSize(chunkSize)
                .chunkOverlap(chunkOverlap)
                .minChunkSize(minChunkSize)
                .build();
        }
    }

    // 结果类定义
    
    /**
     * 向量重建结果
     */
    @lombok.Data
    @lombok.Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class VectorRebuildResult {
        private boolean success;
        private String message;
        private LocalDateTime startTime;
        private LocalDateTime endTime;
        private long durationMs;
        
        private int springAiClearedCount;
        private int langchain4jClearedCount;
        private int processedDocuments;
        private int failedDocuments;
        private int totalChunks;
        
        private int chunkSize;
        private int chunkOverlap;
    }
    
    /**
     * 向量数据库统计信息
     */
    @lombok.Data
    @lombok.Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class VectorDatabaseStats {
        private int springAiVectorCount;
        private int langchain4jEmbeddingCount;
        private long sourceDocumentsCount;
        private int chunkSize;
        private int chunkOverlap;
        private int minChunkSize;
    }
}
