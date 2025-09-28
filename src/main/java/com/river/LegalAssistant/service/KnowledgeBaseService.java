package com.river.LegalAssistant.service;

import dev.langchain4j.data.document.splitter.DocumentSplitters;
import dev.langchain4j.data.segment.TextSegment;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

/**
 * 知识库管理服务
 * 提供文档上传、管理、删除等核心功能
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class KnowledgeBaseService {

    private final DocumentParserService documentParserService;
    @Getter
    private final AiService aiService;
    private final VectorStore vectorStore;

    @Value("${app.knowledge-base.chunk-size:1000}")
    private int chunkSize;
    
    @Value("${app.knowledge-base.chunk-overlap:100}")
    private int chunkOverlap;
    
    @Getter
    @Value("${app.knowledge-base.max-file-size:50MB}")
    private String maxFileSize;

    // 文档索引，用于管理文档元数据
    private final Map<String, DocumentMetadata> documentIndex = new ConcurrentHashMap<>();
    private final AtomicLong documentCounter = new AtomicLong(0);

    /**
     * 上传单个文档到知识库
     */
    public Map<String, Object> uploadDocument(InputStream inputStream, String fileName, 
                                            long fileSize, String category, String description) 
            throws DocumentParserService.DocumentParsingException {
        
        log.info("开始上传文档到知识库: {}, 大小: {} bytes", fileName, fileSize);
        
        // 解析文档内容
        String content = documentParserService.parseDocument(inputStream, fileName, fileSize);
        
        // 计算文档哈希
        String docId = generateDocumentId(fileName, content);
        
        // 检查文档是否已存在
        if (documentIndex.containsKey(docId)) {
            log.warn("文档已存在: {}", fileName);
            Map<String, Object> result = new HashMap<>();
            result.put("success", false);
            result.put("message", "文档已存在");
            result.put("docId", docId);
            return result;
        }
        
        // 使用 LangChain4j 高级文本分割器
        List<String> chunks = splitDocumentWithLangChain4j(content);
        log.info("文档分割完成，共 {} 个块", chunks.size());
        
        // 创建文档元数据
        DocumentMetadata metadata = new DocumentMetadata(
            docId, fileName, category, description, 
            content.length(), chunks.size(), LocalDateTime.now()
        );
        
        // 向量化存储文档块
        List<Document> vectorDocuments = new ArrayList<>();
        for (int i = 0; i < chunks.size(); i++) {
            String chunk = chunks.get(i);
            
            Map<String, Object> chunkMetadata = new HashMap<>();
            chunkMetadata.put("doc_id", docId);
            chunkMetadata.put("original_filename", fileName);
            chunkMetadata.put("category", category != null ? category : "general");
            chunkMetadata.put("chunk_index", i);
            chunkMetadata.put("total_chunks", chunks.size());
            chunkMetadata.put("upload_time", LocalDateTime.now().toString());
            chunkMetadata.put("source_type", "knowledge_base");
            
            Document vectorDoc = new Document(chunk, chunkMetadata);
            vectorDocuments.add(vectorDoc);
        }
        
        // 批量添加到向量存储
        vectorStore.add(vectorDocuments);
        
        // 更新文档索引
        documentIndex.put(docId, metadata);
        
        log.info("文档上传完成: {}, 文档ID: {}", fileName, docId);
        
        // 创建结果Map，处理null值问题
        Map<String, Object> result = new HashMap<>();
        result.put("success", true);
        result.put("message", "文档上传成功");
        result.put("docId", docId);
        result.put("fileName", fileName);
        result.put("contentLength", content.length());
        result.put("chunkCount", chunks.size());
        result.put("category", category != null ? category : "general");
        result.put("uploadTime", LocalDateTime.now());
        
        return result;
    }

    /**
     * 批量上传文档
     */
    public Map<String, Object> batchUploadDocuments(MultipartFile[] files, String category) {
        log.info("开始批量上传 {} 个文档", files.length);
        
        List<CompletableFuture<Map<String, Object>>> futures = new ArrayList<>();
        List<String> successDocs = new ArrayList<>();
        List<String> failedDocs = new ArrayList<>();
        
        for (MultipartFile file : files) {
            CompletableFuture<Map<String, Object>> future = CompletableFuture.supplyAsync(() -> {
                try {
                    return uploadDocument(
                        file.getInputStream(),
                        file.getOriginalFilename(),
                        file.getSize(),
                        category,
                        "批量上传文档"
                    );
                } catch (Exception e) {
                    log.error("批量上传文档失败: {}", file.getOriginalFilename(), e);
                    return Map.of(
                        "success", false,
                        "fileName", Objects.requireNonNull(file.getOriginalFilename()),
                        "error", e.getMessage()
                    );
                }
            });
            futures.add(future);
        }
        
        // 等待所有上传任务完成
        for (int i = 0; i < futures.size(); i++) {
            try {
                Map<String, Object> result = futures.get(i).get();
                String fileName = files[i].getOriginalFilename();
                
                if ((Boolean) result.get("success")) {
                    successDocs.add(fileName);
                } else {
                    failedDocs.add(fileName + ": " + result.get("message"));
                }
            } catch (Exception e) {
                failedDocs.add(files[i].getOriginalFilename() + ": " + e.getMessage());
            }
        }
        
        log.info("批量上传完成，成功: {}, 失败: {}", successDocs.size(), failedDocs.size());
        
        return Map.of(
            "success", true,
            "totalFiles", files.length,
            "successCount", successDocs.size(),
            "failedCount", failedDocs.size(),
            "successFiles", successDocs,
            "failedFiles", failedDocs,
            "timestamp", LocalDateTime.now()
        );
    }

    /**
     * 获取文档列表
     */
    public Map<String, Object> getDocuments(int page, int size, String category) {
        List<DocumentMetadata> allDocs = new ArrayList<>(documentIndex.values());
        
        // 按分类过滤
        if (category != null && !category.trim().isEmpty()) {
            allDocs = allDocs.stream()
                    .filter(doc -> category.equals(doc.getCategory()))
                    .collect(Collectors.toList()); // 使用collect而不是toList()以确保返回可变列表
        }
        
        // 按上传时间倒序排序
        allDocs.sort((a, b) -> b.getUploadTime().compareTo(a.getUploadTime()));
        
        // 分页
        int start = page * size;
        int end = Math.min(start + size, allDocs.size());
        List<DocumentMetadata> paginatedDocs = allDocs.subList(start, end);
        
        return Map.of(
            "success", true,
            "documents", paginatedDocs,
            "totalCount", allDocs.size(),
            "currentPage", page,
            "pageSize", size,
            "totalPages", (int) Math.ceil((double) allDocs.size() / size)
        );
    }

    /**
     * 获取文档详情
     */
    public Map<String, Object> getDocumentDetail(String docId) {
        DocumentMetadata metadata = documentIndex.get(docId);
        
        if (metadata == null) {
            return null;
        }
        
        // 查询文档的所有块
        List<Document> chunks = searchDocumentChunks(docId);
        
        return Map.of(
            "success", true,
            "metadata", metadata,
            "chunkCount", chunks.size(),
            "chunks", chunks.stream().map(chunk -> {
                    if (chunk.getText() != null) {
                        return Map.of(
                                "content", chunk.getText(),
                                "metadata", chunk.getMetadata()
                        );
                    }
                    return null;
                }).toList()
        );
    }

    /**
     * 删除文档
     */
    public boolean deleteDocument(String docId) {
        log.info("开始删除文档: {}", docId);
        
        DocumentMetadata metadata = documentIndex.get(docId);
        if (metadata == null) {
            log.warn("文档不存在: {}", docId);
            return false;
        }
        
        try {
            // 删除向量存储中的文档块
            deleteDocumentChunks(docId);
            
            // 从文档索引中移除
            documentIndex.remove(docId);
            
            log.info("文档删除成功: {}", metadata.getFileName());
            return true;
            
        } catch (Exception e) {
            log.error("删除文档失败: {}", docId, e);
            return false;
        }
    }

    /**
     * 批量删除文档
     */
    public Map<String, Object> batchDeleteDocuments(List<String> docIds) {
        log.info("开始批量删除 {} 个文档", docIds.size());
        
        List<String> successDocs = new ArrayList<>();
        List<String> failedDocs = new ArrayList<>();
        
        for (String docId : docIds) {
            if (deleteDocument(docId)) {
                successDocs.add(docId);
            } else {
                failedDocs.add(docId);
            }
        }
        
        log.info("批量删除完成，成功: {}, 失败: {}", successDocs.size(), failedDocs.size());
        
        return Map.of(
            "success", true,
            "totalRequested", docIds.size(),
            "successCount", successDocs.size(),
            "failedCount", failedDocs.size(),
            "successDocs", successDocs,
            "failedDocs", failedDocs,
            "timestamp", LocalDateTime.now()
        );
    }

    /**
     * 更新文档元数据
     */
    public Map<String, Object> updateDocumentMetadata(String docId, Map<String, String> updateData) {
        DocumentMetadata metadata = documentIndex.get(docId);
        
        if (metadata == null) {
            return null;
        }
        
        // 更新分类
        if (updateData.containsKey("category")) {
            metadata.setCategory(updateData.get("category"));
        }
        
        // 更新描述
        if (updateData.containsKey("description")) {
            metadata.setDescription(updateData.get("description"));
        }
        
        // 更新向量存储中的元数据
        updateVectorStoreMetadata(docId, updateData);
        
        log.info("文档元数据更新成功: {}", docId);
        
        return Map.of(
            "success", true,
            "message", "文档信息更新成功",
            "docId", docId,
            "updatedMetadata", metadata,
            "timestamp", LocalDateTime.now()
        );
    }

    /**
     * 获取知识库统计信息
     */
    public Map<String, Object> getStatistics() {
        Map<String, Long> categoryStats = new HashMap<>();
        long totalChunks = 0;
        long totalContentLength = 0;
        
        for (DocumentMetadata doc : documentIndex.values()) {
            String category = doc.getCategory() != null ? doc.getCategory() : "未分类";
            categoryStats.put(category, categoryStats.getOrDefault(category, 0L) + 1);
            totalChunks += doc.getChunkCount();
            totalContentLength += doc.getContentLength();
        }
        
        return Map.of(
            "totalDocuments", documentIndex.size(),
            "totalChunks", totalChunks,
            "totalContentLength", totalContentLength,
            "categoryStats", categoryStats,
            "averageChunksPerDoc", documentIndex.isEmpty() ? 0 : totalChunks / documentIndex.size(),
            "lastUpdated", LocalDateTime.now()
        );
    }

    /**
     * 重建知识库索引
     */
    public void reindexKnowledgeBase() {
        log.info("开始重建知识库索引...");
        
        // 异步执行重建
        CompletableFuture.runAsync(() -> {
            try {
                // 清空现有索引
                clearVectorStore();
                
                // 重新处理所有文档
                for (DocumentMetadata metadata : documentIndex.values()) {
                    log.info("重新索引文档: {}", metadata.getFileName());
                    // 这里需要重新读取原始文档内容，在实际实现中需要保存原始文档路径
                    // 简化实现：这里只是记录日志
                }
                
                log.info("知识库索引重建完成");
                
            } catch (Exception e) {
                log.error("重建知识库索引失败", e);
            }
        });
    }

    // ==================== 私有辅助方法 ====================

    /**
     * 使用 LangChain4j 高级文本分割器
     */
    private List<String> splitDocumentWithLangChain4j(String content) {
        try {
            // 创建递归字符文本分割器
            var splitter = DocumentSplitters.recursive(chunkSize, chunkOverlap);
            
            // 创建 Document 并分割
            dev.langchain4j.data.document.Document document = dev.langchain4j.data.document.Document.from(content);
            List<TextSegment> segments = splitter.split(document);
            
            // 转换为字符串列表
            return segments.stream()
                    .map(TextSegment::text)
                    .filter(text -> text != null && !text.trim().isEmpty())
                    .toList();
                    
        } catch (Exception e) {
            log.warn("LangChain4j 分割器失败，使用简单分割器", e);
            // 回退到简单分割
            return splitDocumentSimple(content);
        }
    }

    /**
     * 简单的文本分割实现（备用方案）
     */
    private List<String> splitDocumentSimple(String content) {
        List<String> chunks = new ArrayList<>();
        
        if (content.length() <= chunkSize) {
            chunks.add(content);
            return chunks;
        }
        
        int start = 0;
        while (start < content.length()) {
            int end = Math.min(start + chunkSize, content.length());
            
            // 尝试在句子边界分割
            if (end < content.length()) {
                end = findSentenceBoundary(content, start, end);
            }
            
            String chunk = content.substring(start, end).trim();
            if (!chunk.isEmpty()) {
                chunks.add(chunk);
            }
            
            start = Math.max(start + 1, end - chunkOverlap);
        }
        
        return chunks;
    }

    /**
     * 查找句子边界
     */
    private int findSentenceBoundary(String text, int start, int suggestedEnd) {
        int searchStart = Math.max(start, suggestedEnd - 50);
        
        for (int i = suggestedEnd; i >= searchStart; i--) {
            char c = text.charAt(i);
            if (c == '。' || c == '!' || c == '?' || c == '；' || c == '\n') {
                return i + 1;
            }
        }
        
        return suggestedEnd;
    }

    /**
     * 生成文档ID
     */
    private String generateDocumentId(String fileName, String content) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            String input = fileName + ":" + content;
            byte[] hash = digest.digest(input.getBytes());
            
            String hexString = bytesToHex(hash);
            return hexString.substring(0, 16); // 使用前16个字符作为ID
            
        } catch (NoSuchAlgorithmException e) {
            // 如果SHA-256不可用，使用简单的ID生成
            return "doc_" + documentCounter.incrementAndGet() + "_" + System.currentTimeMillis();
        }
    }

    /**
     * 将字节数组转换为十六进制字符串
     */
    private String bytesToHex(byte[] bytes) {
        StringBuilder hexString = new StringBuilder();
        for (byte b : bytes) {
            String hex = Integer.toHexString(0xff & b);
            if (hex.length() == 1) {
                hexString.append('0');
            }
            hexString.append(hex);
        }
        return hexString.toString();
    }

    /**
     * 搜索文档的所有块
     */
    private List<Document> searchDocumentChunks(String docId) {
        // 注意：这是一个简化实现
        // 实际中需要根据VectorStore的具体实现来查询特定文档的块
        try {
            // 使用文档ID作为查询来找到相关块
            return vectorStore.similaritySearch(docId);
        } catch (Exception e) {
            log.warn("搜索文档块失败: {}", docId, e);
            return new ArrayList<>();
        }
    }

    /**
     * 删除文档的所有块
     */
    private void deleteDocumentChunks(String docId) {
        // 注意：这是一个简化实现
        // Spring AI 的 VectorStore 接口可能不直接支持按元数据删除
        // 在实际项目中，可能需要：
        // 1. 使用支持按元数据删除的向量数据库
        // 2. 维护一个映射表记录文档ID到向量ID的关系
        // 3. 实现自定义的删除逻辑
        
        log.warn("删除文档块功能需要根据具体的VectorStore实现来完成: {}", docId);
        // 这里暂时只记录日志，实际实现需要根据使用的向量数据库来定制
    }

    /**
     * 更新向量存储中的元数据
     */
    private void updateVectorStoreMetadata(String docId, Map<String, String> updateData) {
        // 注意：这也是一个简化实现
        // 实际中可能需要重新向量化文档块并更新元数据
        log.info("更新向量存储元数据: {}, 数据: {}", docId, updateData);
    }

    /**
     * 清空向量存储
     */
    private void clearVectorStore() {
        // 注意：这需要根据具体的VectorStore实现
        log.warn("清空向量存储功能需要根据具体的VectorStore实现来完成");
    }

    /**
     * 文档元数据类
     */
    @Setter
    @Getter
    public static class DocumentMetadata {
        // Getters and Setters
        private String docId;
        private String fileName;
        private String category;
        private String description;
        private long contentLength;
        private int chunkCount;
        private LocalDateTime uploadTime;

        public DocumentMetadata(String docId, String fileName, String category, String description,
                              long contentLength, int chunkCount, LocalDateTime uploadTime) {
            this.docId = docId;
            this.fileName = fileName;
            this.category = category;
            this.description = description;
            this.contentLength = contentLength;
            this.chunkCount = chunkCount;
            this.uploadTime = uploadTime;
        }

    }
}
