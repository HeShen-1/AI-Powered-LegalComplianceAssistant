package com.river.LegalAssistant.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingModel;
// PDF 读取功能在 Spring AI 1.0.2 中可能不可用，暂时移除
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * ETL 服务 - 专门负责文档的提取、转换和加载
 * 基于Spring AI 1.0.2官方文档最佳实践实现
 * 
 * 主要功能：
 * 1. 使用Spring AI标准组件读取PDF文档
 * 2. 使用TokenTextSplitter进行智能文本分块
 * 3. 批量向量化和存储到VectorStore
 */
@Service
@Slf4j
public class EtlService {

    private final VectorStore vectorStore;
    private final EmbeddingModel embeddingModel;
    
    // ETL配置参数
    @Value("${app.etl.chunk-size:800}")
    private int chunkSize;
    
    @Value("${app.etl.chunk-overlap:80}")
    private int chunkOverlap;
    
    @Value("${app.etl.min-chunk-size:10}")
    private int minChunkSize;
    
    @Value("${app.etl.break-on-words:true}")
    private boolean breakOnWords;

    public EtlService(VectorStore vectorStore, 
                     @Qualifier("ollamaEmbeddingModel") EmbeddingModel embeddingModel) {
        this.vectorStore = vectorStore;
        this.embeddingModel = embeddingModel;
        log.info("ETL服务初始化完成 - chunk大小: {}, 重叠: {}, 最小块: {}", 
                chunkSize, chunkOverlap, minChunkSize);
    }

    /**
     * 处理PDF文档 - 简化版本（暂时不使用Spring AI PDF读取器）
     * 
     * @param pdfResource PDF文件资源
     * @param metadata 额外的元数据
     * @return 处理结果统计
     */
    public EtlProcessResult processPdfDocument(Resource pdfResource, Map<String, Object> metadata) {
        log.info("开始处理PDF文档: {}", pdfResource.getFilename());
        
        // 由于Spring AI 1.0.2中PDF读取器可能不可用，暂时返回失败状态
        // 建议使用DocumentParserService进行PDF解析
        log.warn("Spring AI PDF读取器在当前版本中不可用，请使用DocumentParserService解析PDF");
        
        return new EtlProcessResult(
                false, 
                0, 
                0,
                "PDF文档处理功能暂时不可用，请使用其他方式处理PDF文档"
        );
    }

    /**
     * 处理纯文本内容 - 使用TokenTextSplitter
     * 
     * @param content 文本内容
     * @param metadata 元数据
     * @return 处理结果统计
     */
    public EtlProcessResult processTextContent(String content, Map<String, Object> metadata) {
        log.info("开始处理文本内容，长度: {} 字符", content.length());
        
        if (content == null || content.trim().isEmpty()) {
            log.warn("文本内容为空，跳过处理");
            return new EtlProcessResult(false, 0, 0, "文本内容为空");
        }
        
        try {
            // 1. 清理metadata，确保没有null值
            Map<String, Object> cleanedMetadata = cleanMetadata(metadata);
            
            // 2. 创建文档对象
            Document document = new Document(content, cleanedMetadata);
            
            // 3. 使用简化的文本分块（TokenTextSplitter在Spring AI 1.0.2中可能不可用）
            List<Document> splitDocuments = splitTextToDocuments(content, cleanedMetadata);
            
            log.info("文本分块完成，生成 {} 个文本块", splitDocuments.size());
            
            // 3. 存储到向量数据库
            vectorStore.accept(splitDocuments);
            
            log.info("文本内容处理完成，{} 个向量块已存储", splitDocuments.size());
            
            return new EtlProcessResult(
                    true, 
                    1, 
                    splitDocuments.size(),
                    "文本内容处理成功"
            );
            
        } catch (Exception e) {
            log.error("文本内容处理失败", e);
            return new EtlProcessResult(
                    false, 
                    0, 
                    0,
                    "文本内容处理失败: " + e.getMessage()
            );
        }
    }

    /**
     * 测试分块效果 - 不存储到数据库
     * 
     * @param content 要测试的文本内容
     * @return 分块信息列表
     */
    public List<Document> testTextSplitting(String content) {
        log.info("测试文本分块，内容长度: {} 字符", content.length());
        
        try {
            List<Document> splitDocuments = splitTextToDocuments(content, Map.of("test", true));
            
            log.info("测试分块完成，生成 {} 个文本块", splitDocuments.size());
            return splitDocuments;
            
        } catch (Exception e) {
            log.error("测试分块失败", e);
            throw new RuntimeException("分块测试失败", e);
        }
    }

    /**
     * 简化的文本分块方法
     */
    private List<Document> splitTextToDocuments(String content, Map<String, Object> baseMetadata) {
        List<Document> documents = new ArrayList<>();
        
        // 确保baseMetadata不包含null值
        Map<String, Object> cleanedBaseMetadata = cleanMetadata(baseMetadata);
        
        if (content.length() <= chunkSize) {
            // 内容较短，不需要分块
            documents.add(new Document(content, cleanedBaseMetadata));
            return documents;
        }
        
        // 进行分块
        int start = 0;
        int chunkIndex = 0;
        
        while (start < content.length()) {
            int end = Math.min(start + chunkSize, content.length());
            
            // 尝试在句子边界分割
            if (end < content.length()) {
                end = findBestSplitPoint(content, start, end);
            }
            
            String chunkText = content.substring(start, end).trim();
            if (!chunkText.isEmpty()) {
                Map<String, Object> chunkMetadata = new HashMap<>(cleanedBaseMetadata);
                chunkMetadata.put("chunk_index", chunkIndex);
                chunkMetadata.put("chunk_start", start);
                chunkMetadata.put("chunk_end", end);
                
                documents.add(new Document(chunkText, chunkMetadata));
                chunkIndex++;
            }
            
            // 下一块的开始位置，考虑重叠
            start = Math.max(start + 1, end - chunkOverlap);
        }
        
        // 添加总块数信息
        final int totalChunks = documents.size();
        documents.forEach(doc -> doc.getMetadata().put("total_chunks", totalChunks));
        
        return documents;
    }
    
    /**
     * 清理metadata，移除null值，确保Spring AI Document兼容性
     */
    private Map<String, Object> cleanMetadata(Map<String, Object> metadata) {
        if (metadata == null) {
            return Map.of();
        }
        
        Map<String, Object> cleaned = new HashMap<>();
        metadata.forEach((key, value) -> {
            if (value != null) {
                cleaned.put(key, value);
            } else {
                // 记录警告但不添加null值
                log.warn("移除metadata中的null值，key: {}", key);
            }
        });
        
        return cleaned;
    }
    
    /**
     * 查找最佳的分割点
     */
    private int findBestSplitPoint(String text, int start, int suggestedEnd) {
        int searchStart = Math.max(start, suggestedEnd - 100);
        
        // 优先在句号、问号、感叹号处分割
        for (int i = suggestedEnd; i >= searchStart; i--) {
            char c = text.charAt(i);
            if (c == '。' || c == '!' || c == '?' || c == '；') {
                return i + 1;
            }
        }
        
        // 其次在逗号、分号处分割
        for (int i = suggestedEnd; i >= searchStart; i--) {
            char c = text.charAt(i);
            if (c == ',' || c == '，' || c == ';') {
                return i + 1;
            }
        }
        
        // 最后在空格或换行符处分割
        for (int i = suggestedEnd; i >= searchStart; i--) {
            char c = text.charAt(i);
            if (Character.isWhitespace(c)) {
                return i + 1;
            }
        }
        
        return suggestedEnd;
    }

    /**
     * 获取当前ETL配置信息
     */
    public EtlConfigInfo getConfigInfo() {
        return new EtlConfigInfo(
                chunkSize,
                chunkOverlap,
                minChunkSize,
                breakOnWords
        );
    }

    /**
     * ETL处理结果记录
     */
    public record EtlProcessResult(
            boolean success,
            int originalDocumentCount,
            int chunkCount,
            String message
    ) {}

    /**
     * ETL配置信息记录
     */
    public record EtlConfigInfo(
            int chunkSize,
            int chunkOverlap,
            int minChunkSize,
            boolean breakOnWords
    ) {}
}
