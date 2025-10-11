package com.river.LegalAssistant.service;

import com.river.LegalAssistant.entity.KnowledgeDocument;
import com.river.LegalAssistant.service.splitter.DocumentSplitterFactory;
import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.DocumentSplitter;
import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.EmbeddingStoreIngestor;
import lombok.Builder;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 统一文档处理服务
 * 
 * 这是系统中所有文档处理的统一入口，负责:
 * 1. 文档类型自动识别
 * 2. 分割器智能选择
 * 3. 文档分割和向量化
 * 4. 统一存储到向量数据库
 * 
 * 架构设计:
 * ┌─────────────────────────────────────────────┐
 * │        DocumentProcessingService            │
 * │  ┌──────────────────────────────────────┐   │
 * │  │  文档类型识别                         │   │
 * │  │  - 法律文档 → LegalDocumentSplitter   │   │
 * │  │  - 合同文档 → ContractSplitter        │   │
 * │  │  - 通用文档 → RecursiveSplitter       │   │
 * │  └──────────────────────────────────────┘   │
 * └─────────────────┬───────────────────────────┘
 *                   │
 *                   ▼
 * ┌─────────────────────────────────────────────┐
 * │      统一的向量存储                           │
 * │  - langchain4j_embeddings (主)              │
 * └─────────────────────────────────────────────┘
 * 
 * 特性:
 * - 插件化分割器架构
 * - 支持批量处理
 * - 完整的元数据管理
 * - 错误处理和降级策略
 * 
 * @author River
 * @since 1.0
 */
@Service
@Slf4j
public class DocumentProcessingService {
    
    private final DocumentSplitterFactory splitterFactory;
    private final EmbeddingModel embeddingModel;
    private final EmbeddingStore<TextSegment> embeddingStore;
    
    @Value("${app.etl.min-chunk-size:50}")
    private int minChunkSize;
    
    @Value("${app.document-processing.enable-quality-filter:true}")
    private boolean enableQualityFilter;
    
    /**
     * 构造函数
     */
    public DocumentProcessingService(
            DocumentSplitterFactory splitterFactory,
            @Qualifier("langchain4jEmbeddingModel") EmbeddingModel embeddingModel,
            @Qualifier("langchain4jEmbeddingStore") EmbeddingStore<TextSegment> embeddingStore) {
        this.splitterFactory = splitterFactory;
        this.embeddingModel = embeddingModel;
        this.embeddingStore = embeddingStore;
        log.info("DocumentProcessingService 初始化完成 - 统一文档处理服务已启用");
    }
    
    /**
     * 处理单个文档 - 主入口方法
     * 
     * @param content 文档内容
     * @param metadata 文档元数据
     * @param documentType 文档类型 (LAW, CONTRACT_TEMPLATE, CASE, REGULATION等)
     * @return 处理结果
     */
    public ProcessingResult processDocument(String content, Map<String, Object> metadata, String documentType) {
        log.info("开始处理文档 - 类型: {}, 长度: {} 字符", documentType, content.length());
        long startTime = System.currentTimeMillis();
        
        try {
            // 1. 验证输入
            if (content == null || content.trim().isEmpty()) {
                log.warn("文档内容为空，跳过处理");
                return ProcessingResult.builder()
                    .success(false)
                    .message("文档内容为空")
                    .segmentCount(0)
                    .build();
            }
            
            // 2. 选择合适的分割器
            DocumentSplitter splitter = selectSplitter(documentType, 
                metadata != null ? (String) metadata.get("original_filename") : null);
            String splitterType = splitterFactory.getSplitterType(splitter);
            log.debug("选择分割器: {} for 文档类型: {}", splitterType, documentType);
            
            // 3. 创建LangChain4j文档对象
            Document document = createDocument(content, metadata);
            
            // 4. 分割文档
            List<TextSegment> segments = splitter.split(document);
            log.debug("文档分割完成，生成 {} 个原始片段", segments.size());
            
            // 5. 过滤和增强片段
            List<TextSegment> filteredSegments = filterAndEnhanceSegments(segments, documentType, splitterType);
            log.debug("过滤后保留 {} 个有效片段", filteredSegments.size());
            
            if (filteredSegments.isEmpty()) {
                log.warn("分割后无有效片段");
                return ProcessingResult.builder()
                    .success(false)
                    .message("分割后无有效片段")
                    .segmentCount(0)
                    .build();
            }
            
            // 6. 向量化和存储
            int storedCount = vectorizeAndStore(filteredSegments);
            
            long duration = System.currentTimeMillis() - startTime;
            log.info("文档处理完成 - 片段数: {}, 存储数: {}, 耗时: {}ms", 
                    filteredSegments.size(), storedCount, duration);
            
            return ProcessingResult.builder()
                .success(true)
                .message("文档处理成功")
                .segmentCount(storedCount)
                .splitterType(splitterType)
                .durationMs(duration)
                .build();
            
        } catch (Exception e) {
            long duration = System.currentTimeMillis() - startTime;
            log.error("文档处理失败 - 类型: {}", documentType, e);
            return ProcessingResult.builder()
                .success(false)
                .message("文档处理失败: " + e.getMessage())
                .segmentCount(0)
                .durationMs(duration)
                .build();
        }
    }
    
    /**
     * 处理知识库文档实体
     * 
     * @param doc 知识库文档实体
     * @return 处理结果
     */
    public ProcessingResult processKnowledgeDocument(KnowledgeDocument doc) {
        log.info("处理知识库文档: {} (ID: {})", doc.getTitle(), doc.getId());
        
        try {
            if (doc.getContent() == null || doc.getContent().trim().isEmpty()) {
                log.warn("文档内容为空，跳过: {}", doc.getTitle());
                return ProcessingResult.builder()
                    .success(false)
                    .message("文档内容为空")
                    .segmentCount(0)
                    .build();
            }
            
            // 准备元数据
            Map<String, Object> metadata = new HashMap<>();
            metadata.put("doc_id", doc.getId().toString());
            metadata.put("original_filename", doc.getTitle());
            metadata.put("document_type", doc.getDocumentType());
            metadata.put("source_file", doc.getSourceFile());
            metadata.put("file_hash", doc.getFileHash());
            metadata.put("source_type", "knowledge_base");
            metadata.put("indexed_at", LocalDateTime.now().toString());
            
            // 如果文档有自己的元数据，合并进来
            if (doc.getMetadata() != null) {
                metadata.putAll(doc.getMetadata());
            }
            
            // 处理文档
            return processDocument(doc.getContent(), metadata, doc.getDocumentType());
            
        } catch (Exception e) {
            log.error("处理知识库文档失败: {}", doc.getTitle(), e);
            return ProcessingResult.builder()
                .success(false)
                .message("处理失败: " + e.getMessage())
                .segmentCount(0)
                .build();
        }
    }
    
    /**
     * 批量处理文档
     * 
     * @param documents 文档列表
     * @return 批量处理结果
     */
    public BatchProcessingResult batchProcessDocuments(List<KnowledgeDocument> documents) {
        log.info("开始批量处理 {} 个文档", documents.size());
        long startTime = System.currentTimeMillis();
        
        int successCount = 0;
        int failedCount = 0;
        int totalSegments = 0;
        List<String> failedDocuments = new ArrayList<>();
        
        for (KnowledgeDocument doc : documents) {
            try {
                ProcessingResult result = processKnowledgeDocument(doc);
                
                if (result.isSuccess()) {
                    successCount++;
                    totalSegments += result.getSegmentCount();
                } else {
                    failedCount++;
                    failedDocuments.add(doc.getTitle() + ": " + result.getMessage());
                }
                
            } catch (Exception e) {
                failedCount++;
                failedDocuments.add(doc.getTitle() + ": " + e.getMessage());
                log.error("批量处理文档失败: {}", doc.getTitle(), e);
            }
        }
        
        long duration = System.currentTimeMillis() - startTime;
        log.info("批量处理完成 - 总计: {}, 成功: {}, 失败: {}, 总片段: {}, 耗时: {}ms",
                documents.size(), successCount, failedCount, totalSegments, duration);
        
        return BatchProcessingResult.builder()
            .totalDocuments(documents.size())
            .successCount(successCount)
            .failedCount(failedCount)
            .totalSegments(totalSegments)
            .failedDocuments(failedDocuments)
            .durationMs(duration)
            .build();
    }
    
    /**
     * 选择合适的分割器
     */
    private DocumentSplitter selectSplitter(String documentType, String fileName) {
        // 优先根据文档类型选择
        if (documentType != null && !documentType.trim().isEmpty()) {
            return splitterFactory.getSplitterByDocumentType(documentType);
        }
        
        // 其次根据文件名选择
        if (fileName != null && !fileName.trim().isEmpty()) {
            return splitterFactory.getSplitterByFileName(fileName);
        }
        
        // 默认使用递归分割器
        log.debug("使用默认递归分割器");
        return splitterFactory.getRecursiveSplitter();
    }
    
    /**
     * 创建LangChain4j文档对象
     */
    private Document createDocument(String content, Map<String, Object> metadata) {
        Map<String, Object> cleanMetadata = cleanMetadata(metadata);
        return Document.from(content, Metadata.from(cleanMetadata));
    }
    
    /**
     * 清理元数据，移除null值
     */
    private Map<String, Object> cleanMetadata(Map<String, Object> metadata) {
        if (metadata == null) {
            return new HashMap<>();
        }
        
        Map<String, Object> cleaned = new HashMap<>();
        metadata.forEach((key, value) -> {
            if (value != null) {
                cleaned.put(key, value);
            } else {
                log.debug("移除metadata中的null值，key: {}", key);
            }
        });
        
        return cleaned;
    }
    
    /**
     * 过滤和增强片段
     */
    private List<TextSegment> filterAndEnhanceSegments(List<TextSegment> segments, 
                                                       String documentType, 
                                                       String splitterType) {
        // 根据文档类型确定最小长度限制
        // 法律文档不应该过滤短条文，因为即使是简短的法律规定也很重要
        int effectiveMinSize = isLegalDocument(documentType) ? 10 : minChunkSize;
        
        List<TextSegment> filtered = segments.stream()
            // 1. 过滤太短的片段
            .filter(segment -> segment.text().trim().length() >= effectiveMinSize)
            // 2. 过滤空白片段
            .filter(segment -> !segment.text().trim().isEmpty())
            .collect(Collectors.toList());
        
        // 3. 增强元数据
        List<TextSegment> enhanced = new ArrayList<>();
        for (int i = 0; i < filtered.size(); i++) {
            TextSegment segment = filtered.get(i);
            Map<String, Object> metadata = new HashMap<>(segment.metadata().toMap());
            
            // 添加处理信息
            metadata.put("processing_timestamp", LocalDateTime.now().toString());
            metadata.put("splitter_type", splitterType);
            metadata.put("segment_index", i);
            metadata.put("total_segments", filtered.size());
            
            // 添加质量指标
            if (enableQualityFilter) {
                double qualityScore = calculateQualityScore(segment.text(), documentType);
                metadata.put("quality_score", qualityScore);
            }
            
            enhanced.add(TextSegment.from(segment.text(), Metadata.from(metadata)));
        }
        
        return enhanced;
    }
    
    /**
     * 判断是否为法律文档
     * 法律文档需要特殊处理，不应过滤短条文
     */
    private boolean isLegalDocument(String documentType) {
        if (documentType == null) {
            return false;
        }
        String upperType = documentType.toUpperCase();
        return upperType.equals("LAW") || 
               upperType.equals("REGULATION") ||
               upperType.equals("CASE");
    }
    
    /**
     * 计算文本质量分数
     * 
     * 评估标准:
     * - 长度适中: 太短或太长都不好
     * - 标点符号合理: 至少包含一些标点
     * - 不全是数字或特殊字符
     * 
     * 注意：法律文档的短条文不会因为长度短而扣分
     */
    private double calculateQualityScore(String text, String documentType) {
        double score = 1.0;
        
        int length = text.length();
        boolean isLegal = isLegalDocument(documentType);
        
        // 长度评分
        // 法律文档的短条文也很重要，不扣分
        if (!isLegal) {
            if (length < 100) {
                score *= 0.7; // 太短
            } else if (length > 3000) {
                score *= 0.8; // 太长
            }
        } else {
            // 法律文档：只对过长的内容扣分
            if (length > 3000) {
                score *= 0.8;
            }
        }
        
        // 标点符号评分
        long punctuationCount = text.chars()
            .filter(c -> "。！？；，".indexOf(c) >= 0)
            .count();
        
        if (punctuationCount == 0) {
            score *= 0.6; // 没有标点
        }
        
        // 字符多样性评分
        long chineseCharCount = text.chars()
            .filter(c -> c >= 0x4E00 && c <= 0x9FA5)
            .count();
        
        if (chineseCharCount < length * 0.3) {
            score *= 0.7; // 中文字符太少
        }
        
        return Math.max(0.0, Math.min(1.0, score));
    }
    
    /**
     * 向量化和存储
     */
    private int vectorizeAndStore(List<TextSegment> segments) {
        try {
            // 使用EmbeddingStoreIngestor批量处理
            EmbeddingStoreIngestor ingestor = EmbeddingStoreIngestor.builder()
                .embeddingModel(embeddingModel)
                .embeddingStore(embeddingStore)
                .build();
            
            // 转换为Document列表
            List<Document> documents = segments.stream()
                .map(segment -> Document.from(segment.text(), segment.metadata()))
                .collect(Collectors.toList());
            
            // 批量摄取
            ingestor.ingest(documents);
            
            log.debug("成功存储 {} 个向量片段", segments.size());
            return segments.size();
            
        } catch (Exception e) {
            log.error("向量化和存储失败", e);
            throw new RuntimeException("向量化和存储失败: " + e.getMessage(), e);
        }
    }
    
    /**
     * 获取支持的文档类型列表
     */
    public List<String> getSupportedDocumentTypes() {
        return List.of(
            "LAW",              // 法律法规
            "REGULATION",       // 部门规章
            "CONTRACT_TEMPLATE", // 合同模板
            "CASE",             // 案例判决
            "GENERAL"           // 通用文档
        );
    }
    
    /**
     * 获取服务配置信息
     */
    public ConfigInfo getConfigInfo() {
        return new ConfigInfo(
            minChunkSize,
            enableQualityFilter,
            getSupportedDocumentTypes()
        );
    }
    
    // ==================== 结果类定义 ====================
    
    /**
     * 文档处理结果
     */
    @Data
    @Builder
    public static class ProcessingResult {
        private boolean success;
        private String message;
        private int segmentCount;
        private String splitterType;
        private long durationMs;
    }
    
    /**
     * 批量处理结果
     */
    @Data
    @Builder
    public static class BatchProcessingResult {
        private int totalDocuments;
        private int successCount;
        private int failedCount;
        private int totalSegments;
        private List<String> failedDocuments;
        private long durationMs;
    }
    
    /**
     * 配置信息
     */
    public record ConfigInfo(
        int minChunkSize,
        boolean enableQualityFilter,
        List<String> supportedDocumentTypes
    ) {}
}

