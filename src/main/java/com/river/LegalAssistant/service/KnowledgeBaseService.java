package com.river.LegalAssistant.service;

import com.river.LegalAssistant.dto.*;
import com.river.LegalAssistant.repository.KnowledgeDocumentRepository;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.core.io.InputStreamResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

/**
 * 知识库管理服务
 * 提供文档上传、管理、删除等核心功能
 * 已优化使用Spring AI ETL组件进行文档处理
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class KnowledgeBaseService {

    private final DocumentParserService documentParserService;
    @Getter
    private final AiService aiService;
    private final VectorStore vectorStore;
    private final EtlService etlService; // 保留用于向后兼容
    private final DocumentProcessingService documentProcessingService; // 新的统一处理服务
    private final KnowledgeDocumentRepository knowledgeDocumentRepository;
    private final org.springframework.jdbc.core.JdbcTemplate jdbcTemplate; // 用于执行原生SQL

    @Value("${app.knowledge-base.chunk-size:1000}")
    private int chunkSize;
    
    @Value("${app.knowledge-base.chunk-overlap:100}")
    private int chunkOverlap;
    
    @Getter
    @Value("${app.knowledge-base.max-file-size:50MB}")
    private String maxFileSize;

    /**
     * 上传单个文档到知识库 - 使用优化的EtlService
     */
    @CacheEvict(value = "knowledgeStats", allEntries = true)  // 上传后清除统计缓存
    public DocumentUploadResultDto uploadDocument(InputStream inputStream, String fileName, 
                                            long fileSize, String category, String description) 
            throws DocumentParserService.DocumentParsingException {
        
        log.info("开始上传文档到知识库 (使用EtlService): {}, 大小: {} bytes", fileName, fileSize);
        
        try {
            // 统一处理：先解析为文本，再使用EtlService处理
            String content = documentParserService.parseDocument(inputStream, fileName, fileSize);
            
            // 计算文档哈希
            String fileHash = generateDocumentId(fileName, content);
            
            // 检查文档是否已存在（基于数据库）
            Optional<com.river.LegalAssistant.entity.KnowledgeDocument> existingDoc = 
                knowledgeDocumentRepository.findByFileHash(fileHash);
            
            if (existingDoc.isPresent()) {
                log.info("检测到已存在的文档: {}, 将进行更新", fileName);
                
                // 删除旧的向量数据
                deleteVectorDataByFileHash(fileHash);
                
                // 删除旧的数据库记录
                knowledgeDocumentRepository.deleteById(existingDoc.get().getId());
                knowledgeDocumentRepository.flush();
                
                log.info("已删除旧文档数据，准备重新上传: {}", fileName);
            }
            
            // 准备元数据（确保没有null值）
            Map<String, Object> metadata = new HashMap<>();
            metadata.put("original_filename", fileName != null ? fileName : "unknown");
            metadata.put("category", category != null ? category : "general");
            metadata.put("description", description != null ? description : "");
            metadata.put("upload_time", LocalDateTime.now().toString());
            metadata.put("source_type", "knowledge_base");
            metadata.put("file_hash", fileHash != null ? fileHash : "");
            
            // 确定文档类型
            String documentType = determineDocumentType(fileName, category);
            
            // 使用新的统一文档处理服务
            DocumentProcessingService.ProcessingResult result = 
                documentProcessingService.processDocument(content, metadata, documentType);
            
            if (!result.isSuccess()) {
                log.error("文档处理失败: {}", result.getMessage());
                return DocumentUploadResultDto.builder()
                    .uploadMode("单个上传")
                    .fileName(fileName)
                    .category(category)
                    .totalFiles(1)
                    .successCount(0)
                    .failedCount(1)
                    .failedFiles(List.of(fileName + ": " + result.getMessage()))
                    .build();
            }
            
            // 保存文档记录到数据库
            com.river.LegalAssistant.entity.KnowledgeDocument savedDoc = 
                saveUploadedDocumentToDatabase(fileName, content, category, description, result.getSegmentCount(), metadata);
            
            log.info("文档上传完成 (DocumentProcessingService): {}, 文档ID: {}, 块数: {}, 分割器: {}", 
                    fileName, savedDoc.getId(), result.getSegmentCount(), result.getSplitterType());
            
            // 返回成功结果DTO
            return DocumentUploadResultDto.builder()
                .uploadMode("单个上传")
                .fileName(fileName)
                .docId(savedDoc.getId().toString())
                .chunkCount(result.getSegmentCount())
                .category(category != null ? category : "general")
                .uploadTime(LocalDateTime.now())
                .totalFiles(1)
                .successCount(1)
                .failedCount(0)
                .successFiles(List.of(fileName))
                .build();
            
        } catch (Exception e) {
            log.error("文档上传失败: {}", fileName, e);
            return DocumentUploadResultDto.builder()
                .uploadMode("单个上传")
                .fileName(fileName)
                .category(category)
                .totalFiles(1)
                .successCount(0)
                .failedCount(1)
                .failedFiles(List.of(fileName + ": " + e.getMessage()))
                .build();
        }
    }

    /**
     * 批量上传文档
     */
    public DocumentUploadResultDto batchUploadDocuments(MultipartFile[] files, String category) {
        log.info("开始批量上传 {} 个文档", files.length);
        
        List<CompletableFuture<DocumentUploadResultDto>> futures = new ArrayList<>();
        List<String> successDocs = new ArrayList<>();
        List<String> failedDocs = new ArrayList<>();
        
        for (MultipartFile file : files) {
            CompletableFuture<DocumentUploadResultDto> future = CompletableFuture.supplyAsync(() -> {
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
                    return DocumentUploadResultDto.builder()
                        .uploadMode("单个上传")
                        .fileName(Objects.requireNonNull(file.getOriginalFilename()))
                        .totalFiles(1)
                        .successCount(0)
                        .failedCount(1)
                        .failedFiles(List.of(file.getOriginalFilename() + ": " + e.getMessage()))
                        .build();
                }
            });
            futures.add(future);
        }
        
        // 等待所有上传任务完成
        for (int i = 0; i < futures.size(); i++) {
            try {
                DocumentUploadResultDto result = futures.get(i).get();
                String fileName = files[i].getOriginalFilename();
                
                if (result.getSuccessCount() != null && result.getSuccessCount() > 0) {
                    successDocs.add(fileName);
                } else {
                    String errorMsg = result.getFailedFiles() != null && !result.getFailedFiles().isEmpty() 
                        ? result.getFailedFiles().get(0) 
                        : fileName + ": 未知错误";
                    failedDocs.add(errorMsg);
                }
            } catch (Exception e) {
                failedDocs.add(files[i].getOriginalFilename() + ": " + e.getMessage());
            }
        }
        
        log.info("批量上传完成，成功: {}, 失败: {}", successDocs.size(), failedDocs.size());
        
        return DocumentUploadResultDto.builder()
            .uploadMode("批量上传")
            .totalFiles(files.length)
            .successCount(successDocs.size())
            .failedCount(failedDocs.size())
            .successFiles(successDocs)
            .failedFiles(failedDocs)
            .uploadTime(LocalDateTime.now())
            .build();
    }

    /**
     * 获取文档列表
     * 从数据库获取已索引的文档，确保包含所有通过自动索引器添加的文档
     */
    public DocumentListDto getDocuments(int page, int size, String category) {
        try {
            org.springframework.data.domain.Pageable pageable = 
                org.springframework.data.domain.PageRequest.of(page, size, 
                    org.springframework.data.domain.Sort.by(
                        org.springframework.data.domain.Sort.Direction.DESC, "createdAt"));
            
            org.springframework.data.domain.Page<com.river.LegalAssistant.entity.KnowledgeDocument> documentsPage;
            
            // 按分类过滤 - 将前端分类值转换为数据库分类值
            if (category != null && !category.trim().isEmpty()) {
                String dbCategory = convertFrontendCategoryToDbCategory(category);
                log.info("分类筛选: 前端值={}, 数据库值={}", category, dbCategory);
                documentsPage = knowledgeDocumentRepository.findByDocumentType(dbCategory, pageable);
            } else {
                documentsPage = knowledgeDocumentRepository.findAll(pageable);
            }
            
            // 转换数据库实体为DTO
            List<DocumentListDto.DocumentInfo> documents = documentsPage.getContent().stream()
                .map(doc -> {
                    Integer chunkCount = 0;
                    String description = "";
                    
                    // 从元数据中获取片段数量和描述
                    if (doc.getMetadata() != null) {
                        // 获取片段数量
                        if (doc.getMetadata().containsKey("segment_count")) {
                            Object segmentCountObj = doc.getMetadata().get("segment_count");
                            if (segmentCountObj instanceof Number) {
                                chunkCount = ((Number) segmentCountObj).intValue();
                            }
                        }
                        
                        // 获取描述
                        if (doc.getMetadata().containsKey("description")) {
                            Object descObj = doc.getMetadata().get("description");
                            if (descObj != null && !descObj.toString().trim().isEmpty()) {
                                description = descObj.toString();
                            }
                        }
                    }
                    
                    // 如果没有描述，使用默认描述
                    if (description.isEmpty()) {
                        description = getDisplayNameForDocumentType(doc.getDocumentType());
                    }
                    
                    return DocumentListDto.DocumentInfo.builder()
                        .id(doc.getId().toString())
                        .filename(doc.getTitle())
                        .category(convertDbCategoryToFrontendCategory(doc.getDocumentType()))
                        .description(description)
                        .size((long) (doc.getContent() != null ? doc.getContent().length() : 0))
                        .chunksCount(chunkCount)
                        .uploadedAt(doc.getCreatedAt() != null ? doc.getCreatedAt().toString() : null)
                        .build();
                })
                .collect(Collectors.toList());
            
            log.info("从数据库获取文档列表: 页码={}, 每页={}, 分类={}, 总数={}", 
                    page, size, category, documentsPage.getTotalElements());
            
            return DocumentListDto.builder()
                .content(documents)
                .totalElements(documentsPage.getTotalElements())
                .totalPages(documentsPage.getTotalPages())
                .currentPage(page)
                .pageSize(size)
                .build();
            
        } catch (Exception e) {
            log.error("获取文档列表失败", e);
            return DocumentListDto.builder()
                .content(Collections.emptyList())
                .totalElements(0L)
                .totalPages(0)
                .currentPage(page)
                .pageSize(size)
                .build();
        }
    }

    /**
     * 获取文档详情
     * 从数据库获取文档详情
     */
    public DocumentDetailDto getDocumentDetail(String docId) {
        try {
            // 将 docId 解析为数据库主键
            Long id = Long.parseLong(docId);
            
            // 从数据库查找
            Optional<com.river.LegalAssistant.entity.KnowledgeDocument> docOpt = 
                knowledgeDocumentRepository.findById(id);
            
            if (docOpt.isPresent()) {
                com.river.LegalAssistant.entity.KnowledgeDocument doc = docOpt.get();
                
                Integer chunkCount = 0;
                String description = "";
                
                // 从元数据中获取片段数量和描述
                if (doc.getMetadata() != null) {
                    // 获取片段数量
                    if (doc.getMetadata().containsKey("segment_count")) {
                        Object segmentCountObj = doc.getMetadata().get("segment_count");
                        if (segmentCountObj instanceof Number) {
                            chunkCount = ((Number) segmentCountObj).intValue();
                        }
                    }
                    
                    // 获取描述
                    if (doc.getMetadata().containsKey("description")) {
                        Object descObj = doc.getMetadata().get("description");
                        if (descObj != null && !descObj.toString().trim().isEmpty()) {
                            description = descObj.toString();
                        }
                    }
                }
                
                // 如果没有描述，使用默认描述
                if (description.isEmpty()) {
                    description = getDisplayNameForDocumentType(doc.getDocumentType());
                }
                
                return DocumentDetailDto.builder()
                    .docId(doc.getId().toString())
                    .fileName(doc.getTitle())
                    .category(getDisplayNameForDocumentType(doc.getDocumentType()))
                    .description(description)
                    .fileSize((long) (doc.getContent() != null ? doc.getContent().length() : 0))
                    .chunkCount(chunkCount)
                    .uploadTime(doc.getCreatedAt() != null ? doc.getCreatedAt().toString() : null)
                    .fileHash(doc.getFileHash())
                    .build();
            }
            
            return null; // 文档不存在
            
        } catch (NumberFormatException e) {
            log.error("文档ID格式无效: {}", docId, e);
            return null;
        } catch (Exception e) {
            log.error("获取文档详情失败: {}", docId, e);
            return null;
        }
    }

    /**
     * 删除文档
     * 从数据库删除文档
     */
    @Transactional
    public boolean deleteDocument(String docId) {
        log.info("开始删除文档: {}", docId);
        
        try {
            // 将 docId 解析为数据库主键
            Long id = Long.parseLong(docId);
            
            // 从数据库查找文档
            Optional<com.river.LegalAssistant.entity.KnowledgeDocument> docOpt = 
                knowledgeDocumentRepository.findById(id);
            
            if (docOpt.isEmpty()) {
                log.warn("文档不存在: {}", docId);
                return false;
            }
            
            com.river.LegalAssistant.entity.KnowledgeDocument doc = docOpt.get();
            String docTitle = doc.getTitle();
            String sourceFile = doc.getSourceFile();
            
            try {
                // 先删除向量存储中的文档块（如果失败不影响数据库删除）
                try {
                    deleteDocumentChunks(sourceFile);
                } catch (Exception e) {
                    log.warn("删除向量存储失败，继续删除数据库记录: {}", e.getMessage());
                }
                
                // 删除数据库记录
                knowledgeDocumentRepository.deleteById(id);
                knowledgeDocumentRepository.flush(); // 立即刷新到数据库
                
                log.info("文档删除成功: {} (ID: {})", docTitle, id);
                return true;
                
            } catch (Exception e) {
                log.error("删除文档失败: {} (ID: {}), 错误: {}", docTitle, id, e.getMessage(), e);
                throw new RuntimeException("删除文档失败: " + e.getMessage(), e);
            }
            
        } catch (NumberFormatException e) {
            log.error("文档ID格式无效: {}", docId, e);
            throw new IllegalArgumentException("文档ID格式无效: " + docId, e);
        } catch (Exception e) {
            log.error("删除文档时发生异常: {}", docId, e);
            throw new RuntimeException("删除文档失败: " + e.getMessage(), e);
        }
    }

    /**
     * 批量删除文档
     */
    public DocumentDeleteResultDto batchDeleteDocuments(List<String> docIds) {
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
        
        return DocumentDeleteResultDto.builder()
            .deletionMode("批量删除")
            .totalRequested(docIds.size())
            .successCount(successDocs.size())
            .failedCount(failedDocs.size())
            .successDocs(successDocs)
            .failedDocs(failedDocs)
            .build();
    }

    /**
     * 更新文档元数据
     */
    public DocumentDetailDto updateDocumentMetadata(String docId, Map<String, String> updateData) {
        try {
            // 将 docId 解析为数据库主键
            Long id = Long.parseLong(docId);
            
            // 从数据库查找
            Optional<com.river.LegalAssistant.entity.KnowledgeDocument> docOpt = 
                knowledgeDocumentRepository.findById(id);
            
            if (!docOpt.isPresent()) {
                log.warn("文档不存在: {}", docId);
                return null;
            }
            
            com.river.LegalAssistant.entity.KnowledgeDocument doc = docOpt.get();
            
            // 更新元数据
            Map<String, Object> metadata = doc.getMetadata();
            if (metadata == null) {
                metadata = new HashMap<>();
                doc.setMetadata(metadata);
            }
            
            // 更新分类
            if (updateData.containsKey("category")) {
                String category = updateData.get("category");
                metadata.put("category", category);
                // 同时更新文档类型
                doc.setDocumentType(determineDocumentType(doc.getTitle(), category));
            }
            
            // 更新描述
            if (updateData.containsKey("description")) {
                metadata.put("description", updateData.get("description"));
            }
            
            // 保存更新
            knowledgeDocumentRepository.save(doc);
            
            // 更新向量存储中的元数据
            updateVectorStoreMetadata(docId, updateData);
            
            log.info("文档元数据更新成功: {}", docId);
            
            // 获取片段数量
            Integer chunkCount = 0;
            if (metadata.containsKey("segment_count")) {
                Object segmentCountObj = metadata.get("segment_count");
                if (segmentCountObj instanceof Number) {
                    chunkCount = ((Number) segmentCountObj).intValue();
                }
            }
            
            return DocumentDetailDto.builder()
                .docId(doc.getId().toString())
                .fileName(doc.getTitle())
                .category(getDisplayNameForDocumentType(doc.getDocumentType()))
                .description(metadata.get("description") != null ? metadata.get("description").toString() : "")
                .fileSize((long) (doc.getContent() != null ? doc.getContent().length() : 0))
                .chunkCount(chunkCount)
                .uploadTime(doc.getCreatedAt() != null ? doc.getCreatedAt().toString() : null)
                .build();
                
        } catch (NumberFormatException e) {
            log.error("文档ID格式无效: {}", docId, e);
            return null;
        } catch (Exception e) {
            log.error("更新文档元数据失败: {}", docId, e);
            return null;
        }
    }

    /**
     * 获取知识库统计信息
     * 优先从数据库获取准确的统计信息，确保包含所有已索引的文档
     * 使用缓存以减少数据库查询压力（每10分钟过期）
     */
    @Cacheable(value = "knowledgeStats", key = "'statistics'")
    public KnowledgeBaseStatsDto getStatistics() {
        try {
            // 优先从数据库获取准确的统计信息
            Long totalDocuments = knowledgeDocumentRepository.getTotalDocumentCount();
            List<Object[]> categoryStats = knowledgeDocumentRepository.countByDocumentType();
            
            // 转换分类统计为Map
            Map<String, Long> categoryStatsMap = new HashMap<>();
            long estimatedTotalChunks = 0;
            
            for (Object[] stat : categoryStats) {
                String docType = (String) stat[0];
                Long count = (Long) stat[1];
                
                // 转换文档类型为更友好的显示名称
                String displayName = getDisplayNameForDocumentType(docType);
                categoryStatsMap.put(displayName, count);
                
                // 估算片段数量（每个文档平均产生约80个片段）
                estimatedTotalChunks += count * 80;
            }
            
            // 如果数据库中有记录，返回数据库统计信息
            if (totalDocuments > 0) {
                log.info("从数据库获取知识库统计信息: 总文档数={}, 分类数={}", totalDocuments, categoryStatsMap.size());
                
                return KnowledgeBaseStatsDto.builder()
                    .totalDocuments(totalDocuments)
                    .totalChunks(estimatedTotalChunks)
                    .categoryCounts(categoryStatsMap)
                    .build();
            }
            
            // 最后，尝试从向量存储估算统计信息
            try {
                List<org.springframework.ai.document.Document> sampleResults = vectorStore.similaritySearch("法");
                if (!sampleResults.isEmpty()) {
                    log.info("向量存储中发现文档片段，尝试估算统计信息");
                    
                    Set<String> uniqueSources = new HashSet<>();
                    List<org.springframework.ai.document.Document> moreResults = vectorStore.similaritySearch("法");
                    
                    for (org.springframework.ai.document.Document doc : moreResults) {
                        Object sourceObj = doc.getMetadata().get("source");
                        String source = sourceObj != null ? sourceObj.toString() : null;
                        if (source != null) {
                            uniqueSources.add(source);
                        }
                    }
                    
                    Map<String, Long> vectorCategoryStats = new HashMap<>();
                    vectorCategoryStats.put("法律文档", (long) uniqueSources.size());
                    
                    return KnowledgeBaseStatsDto.builder()
                        .totalDocuments((long) uniqueSources.size())
                        .totalChunks((long) moreResults.size())
                        .categoryCounts(vectorCategoryStats)
                        .build();
                }
            } catch (Exception e) {
                log.warn("查询向量存储统计信息失败: {}", e.getMessage());
            }
            
            // 如果所有方式都没有获取到统计信息，返回空统计
            return KnowledgeBaseStatsDto.builder()
                .totalDocuments(0L)
                .totalChunks(0L)
                .categoryCounts(new HashMap<>())
                .build();
            
        } catch (Exception e) {
            log.error("获取知识库统计信息失败", e);
            return KnowledgeBaseStatsDto.builder()
                .totalDocuments(0L)
                .totalChunks(0L)
                .categoryCounts(new HashMap<>())
                .build();
        }
    }

    /**
     * 将文档类型转换为更友好的显示名称
     */
    private String getDisplayNameForDocumentType(String documentType) {
        switch (documentType) {
            case "LAW":
                return "法律法规";
            case "REGULATION":
                return "部门规章";
            case "CASE":
                return "案例判决";
            case "CONTRACT_TEMPLATE":
                return "合同模板";
            default:
                return documentType;
        }
    }

    /**
     * 重建知识库索引 - 使用法律文档分割器
     */
    public void reindexKnowledgeBase() {
        log.info("开始重建知识库索引（使用法律文档分割器）...");
        
        // 异步执行重建
        CompletableFuture.runAsync(() -> {
            try {
                // 清空现有索引
                clearVectorStore();
                
                // 从数据库获取所有文档并重新处理
                List<com.river.LegalAssistant.entity.KnowledgeDocument> allDocs = 
                    knowledgeDocumentRepository.findAll();
                
                int successCount = 0;
                int failedCount = 0;
                
                for (com.river.LegalAssistant.entity.KnowledgeDocument doc : allDocs) {
                    try {
                        log.info("重新索引文档: {} (ID: {})", doc.getTitle(), doc.getId());
                        
                        // 注意：由于已经调用了clearVectorStore()清空了所有向量数据，
                        // 这里不需要再删除单个文档的旧数据，避免重复操作
                        
                        // 使用DocumentProcessingService重新处理文档
                        // 这会自动使用法律文档分割器来分割法律文档
                        DocumentProcessingService.ProcessingResult result = 
                            documentProcessingService.processKnowledgeDocument(doc);
                        
                        if (result.isSuccess()) {
                            successCount++;
                            
                            // 更新文档元数据
                            Map<String, Object> docMetadata = doc.getMetadata();
                            if (docMetadata == null) {
                                docMetadata = new HashMap<>();
                            }
                            docMetadata.put("segment_count", result.getSegmentCount());
                            docMetadata.put("last_reindex_time", LocalDateTime.now().toString());
                            docMetadata.put("splitter_type", result.getSplitterType());
                            doc.setMetadata(docMetadata);
                            doc.setUpdatedAt(LocalDateTime.now());
                            
                            knowledgeDocumentRepository.save(doc);
                            
                            log.info("文档索引成功: {}, 片段数: {}, 分割器: {}", 
                                    doc.getTitle(), result.getSegmentCount(), result.getSplitterType());
                        } else {
                            failedCount++;
                            log.error("文档索引失败: {}, 原因: {}", doc.getTitle(), result.getMessage());
                        }
                        
                    } catch (Exception e) {
                        failedCount++;
                        log.error("重新索引文档失败: {} (ID: {})", doc.getTitle(), doc.getId(), e);
                    }
                }
                
                log.info("知识库索引重建完成 - 总数: {}, 成功: {}, 失败: {}", 
                        allDocs.size(), successCount, failedCount);
                
            } catch (Exception e) {
                log.error("重建知识库索引失败", e);
            }
        });
    }

    /**
     * 获取文档的向量块信息
     * 从向量存储中获取实际的向量块数据，而不是重新计算
     */
    public List<DocumentChunkDto> getDocumentChunks(String docId) {
        log.info("获取文档向量块信息: {}", docId);
        
        try {
            // 将 docId 解析为数据库主键
            Long id = Long.parseLong(docId);
            
            // 从数据库查找文档
            Optional<com.river.LegalAssistant.entity.KnowledgeDocument> docOpt = 
                knowledgeDocumentRepository.findById(id);
            
            if (docOpt.isEmpty()) {
                log.warn("文档不存在: {}", docId);
                return null;
            }
            
            com.river.LegalAssistant.entity.KnowledgeDocument doc = docOpt.get();
            
            // 从向量存储中获取实际的向量块数据
            List<DocumentChunkDto> chunkDtos = getVectorChunksFromStore(docId, doc);
            
            if (chunkDtos.isEmpty()) {
                log.warn("未找到文档的向量块数据，尝试从数据库内容重新计算: {}", docId);
                // 如果向量存储中没有数据，则使用文档内容重新计算（作为备选方案）
                chunkDtos = getChunksFromDocumentContent(doc);
            }
            
            log.info("获取到 {} 个向量块", chunkDtos.size());
            return chunkDtos;
            
        } catch (NumberFormatException e) {
            log.error("文档ID格式无效: {}", docId, e);
            return null;
        } catch (Exception e) {
            log.error("获取文档向量块失败: {}", docId, e);
            return Collections.emptyList();
        }
    }
    
    /**
     * 从向量存储中获取文档的向量块数据
     */
    private List<DocumentChunkDto> getVectorChunksFromStore(String docId, com.river.LegalAssistant.entity.KnowledgeDocument doc) {
        try {
            log.info("从向量存储中获取文档向量块: {}", docId);
            
            // 方法1：通过doc_id查询向量存储
            List<DocumentChunkDto> chunks = getVectorChunksByDocId(docId);
            if (!chunks.isEmpty()) {
                log.info("通过doc_id获取到 {} 个向量块", chunks.size());
                return chunks;
            }
            
            // 方法2：通过文件名查询向量存储
            chunks = getVectorChunksByFilename(doc.getTitle());
            if (!chunks.isEmpty()) {
                log.info("通过文件名获取到 {} 个向量块", chunks.size());
                return chunks;
            }
            
            // 方法3：通过sourceFile查询向量存储
            if (doc.getSourceFile() != null && !doc.getSourceFile().isEmpty()) {
                chunks = getVectorChunksBySourceFile(doc.getSourceFile());
                if (!chunks.isEmpty()) {
                    log.info("通过sourceFile获取到 {} 个向量块", chunks.size());
                    return chunks;
                }
            }
            
            log.warn("向量存储中未找到文档的向量块数据: {}", docId);
            return Collections.emptyList();
            
        } catch (Exception e) {
            log.error("从向量存储获取向量块失败: {}", docId, e);
            return Collections.emptyList();
        }
    }
    
    /**
     * 通过doc_id从向量存储中获取向量块
     */
    private List<DocumentChunkDto> getVectorChunksByDocId(String docId) {
        try {
            // 查询langchain4j_embeddings表 - 注意字段名是text而不是content
            String sql = """
                SELECT text, metadata, embedding 
                FROM langchain4j_embeddings 
                WHERE metadata->>'doc_id' = ?
                ORDER BY CAST(metadata->>'segment_index' AS INTEGER)
                """;
            
            List<Map<String, Object>> results = jdbcTemplate.queryForList(sql, docId);
            
            List<DocumentChunkDto> chunks = new ArrayList<>();
            for (Map<String, Object> row : results) {
                String content = (String) row.get("text");  // 使用text字段
                
                // 处理PostgreSQL JSONB字段的类型转换
                Object metadataObj = row.get("metadata");
                String metadataJson = null;
                if (metadataObj instanceof String) {
                    metadataJson = (String) metadataObj;
                } else if (metadataObj != null) {
                    // 使用toString()方法处理PGobject和其他类型
                    metadataJson = metadataObj.toString();
                }
                
                // 解析元数据
                Map<String, Object> metadata = parseMetadata(metadataJson);
                
                // 获取块索引 - 使用segment_index字段
                int chunkIndex = 0;
                if (metadata.containsKey("segment_index")) {
                    Object indexObj = metadata.get("segment_index");
                    if (indexObj instanceof Number) {
                        chunkIndex = ((Number) indexObj).intValue();
                    } else if (indexObj instanceof String) {
                        try {
                            chunkIndex = Integer.parseInt((String) indexObj);
                        } catch (NumberFormatException e) {
                            log.warn("无法解析segment_index: {}", indexObj);
                        }
                    }
                }
                
                DocumentChunkDto chunkDto = DocumentChunkDto.builder()
                    .index(chunkIndex)
                    .content(content)
                    .contentLength(content != null ? content.length() : 0)
                    .tokens(estimateTokens(content))
                    .similarity(1.0) // 从向量存储获取的块相似度设为1.0
                    .metadata(metadata)
                    .build();
                
                chunks.add(chunkDto);
            }
            
            return chunks;
            
        } catch (Exception e) {
            log.warn("通过doc_id查询向量块失败: {}", e.getMessage());
            return Collections.emptyList();
        }
    }
    
    /**
     * 通过文件名从向量存储中获取向量块
     */
    private List<DocumentChunkDto> getVectorChunksByFilename(String filename) {
        try {
            String sql = """
                SELECT text, metadata, embedding 
                FROM langchain4j_embeddings 
                WHERE metadata->>'original_filename' = ?
                ORDER BY CAST(metadata->>'segment_index' AS INTEGER)
                """;
            
            List<Map<String, Object>> results = jdbcTemplate.queryForList(sql, filename);
            
            List<DocumentChunkDto> chunks = new ArrayList<>();
            for (Map<String, Object> row : results) {
                String content = (String) row.get("text");  // 使用text字段
                
                // 处理PostgreSQL JSONB字段的类型转换
                Object metadataObj = row.get("metadata");
                String metadataJson = null;
                if (metadataObj instanceof String) {
                    metadataJson = (String) metadataObj;
                } else if (metadataObj != null) {
                    // 使用toString()方法处理PGobject和其他类型
                    metadataJson = metadataObj.toString();
                }
                
                Map<String, Object> metadata = parseMetadata(metadataJson);
                
                int chunkIndex = 0;
                if (metadata.containsKey("segment_index")) {  // 使用segment_index字段
                    Object indexObj = metadata.get("segment_index");
                    if (indexObj instanceof Number) {
                        chunkIndex = ((Number) indexObj).intValue();
                    } else if (indexObj instanceof String) {
                        try {
                            chunkIndex = Integer.parseInt((String) indexObj);
                        } catch (NumberFormatException e) {
                            log.warn("无法解析segment_index: {}", indexObj);
                        }
                    }
                }
                
                DocumentChunkDto chunkDto = DocumentChunkDto.builder()
                    .index(chunkIndex)
                    .content(content)
                    .contentLength(content != null ? content.length() : 0)
                    .tokens(estimateTokens(content))
                    .similarity(1.0)
                    .metadata(metadata)
                    .build();
                
                chunks.add(chunkDto);
            }
            
            return chunks;
            
        } catch (Exception e) {
            log.warn("通过文件名查询向量块失败: {}", e.getMessage());
            return Collections.emptyList();
        }
    }
    
    /**
     * 通过sourceFile从向量存储中获取向量块
     */
    private List<DocumentChunkDto> getVectorChunksBySourceFile(String sourceFile) {
        try {
            String sql = """
                SELECT text, metadata, embedding 
                FROM langchain4j_embeddings 
                WHERE metadata->>'source_file' = ?
                ORDER BY CAST(metadata->>'segment_index' AS INTEGER)
                """;
            
            List<Map<String, Object>> results = jdbcTemplate.queryForList(sql, sourceFile);
            
            List<DocumentChunkDto> chunks = new ArrayList<>();
            for (Map<String, Object> row : results) {
                String content = (String) row.get("text");  // 使用text字段
                
                // 处理PostgreSQL JSONB字段的类型转换
                Object metadataObj = row.get("metadata");
                String metadataJson = null;
                if (metadataObj instanceof String) {
                    metadataJson = (String) metadataObj;
                } else if (metadataObj != null) {
                    // 使用toString()方法处理PGobject和其他类型
                    metadataJson = metadataObj.toString();
                }
                
                Map<String, Object> metadata = parseMetadata(metadataJson);
                
                int chunkIndex = 0;
                if (metadata.containsKey("segment_index")) {  // 使用segment_index字段
                    Object indexObj = metadata.get("segment_index");
                    if (indexObj instanceof Number) {
                        chunkIndex = ((Number) indexObj).intValue();
                    } else if (indexObj instanceof String) {
                        try {
                            chunkIndex = Integer.parseInt((String) indexObj);
                        } catch (NumberFormatException e) {
                            log.warn("无法解析segment_index: {}", indexObj);
                        }
                    }
                }
                
                DocumentChunkDto chunkDto = DocumentChunkDto.builder()
                    .index(chunkIndex)
                    .content(content)
                    .contentLength(content != null ? content.length() : 0)
                    .tokens(estimateTokens(content))
                    .similarity(1.0)
                    .metadata(metadata)
                    .build();
                
                chunks.add(chunkDto);
            }
            
            return chunks;
            
        } catch (Exception e) {
            log.warn("通过sourceFile查询向量块失败: {}", e.getMessage());
            return Collections.emptyList();
        }
    }
    
    /**
     * 从文档内容重新计算向量块（备选方案）
     */
    private List<DocumentChunkDto> getChunksFromDocumentContent(com.river.LegalAssistant.entity.KnowledgeDocument doc) {
        try {
            String content = doc.getContent();
            if (content == null || content.isEmpty()) {
                log.warn("文档内容为空: {}", doc.getId());
                return Collections.emptyList();
            }
            
            // 使用相同的分块策略重新分块（用于展示）
            List<String> chunks = splitDocumentSimple(content);
            
            // 转换为DTO
            List<DocumentChunkDto> chunkDtos = new ArrayList<>();
            for (int i = 0; i < chunks.size(); i++) {
                String chunk = chunks.get(i);
                
                // 估算token数
                int estimatedTokens = estimateTokens(chunk);
                
                Map<String, Object> chunkMetadata = new HashMap<>();
                chunkMetadata.put("chunk_index", i);
                chunkMetadata.put("total_chunks", chunks.size());
                chunkMetadata.put("source", doc.getTitle());
                chunkMetadata.put("doc_id", doc.getId().toString());
                chunkMetadata.put("source_file", doc.getSourceFile());
                
                DocumentChunkDto chunkDto = DocumentChunkDto.builder()
                    .index(i)
                    .content(chunk)
                    .contentLength(chunk.length())
                    .tokens(estimatedTokens)
                    .similarity(1.0) // 默认相似度为1.0
                    .metadata(chunkMetadata)
                    .build();
                
                chunkDtos.add(chunkDto);
            }
            
            log.info("从文档内容重新计算得到 {} 个向量块", chunkDtos.size());
            return chunkDtos;
            
        } catch (Exception e) {
            log.error("从文档内容计算向量块失败: {}", doc.getId(), e);
            return Collections.emptyList();
        }
    }
    
    /**
     * 解析JSON元数据字符串为Map
     */
    private Map<String, Object> parseMetadata(String metadataJson) {
        try {
            if (metadataJson == null || metadataJson.trim().isEmpty()) {
                return new HashMap<>();
            }
            
            // 使用Spring Boot内置的JSON处理能力
            try {
                // 尝试使用Jackson ObjectMapper解析JSON
                com.fasterxml.jackson.databind.ObjectMapper objectMapper = 
                    new com.fasterxml.jackson.databind.ObjectMapper();
                com.fasterxml.jackson.core.type.TypeReference<Map<String, Object>> typeRef = 
                    new com.fasterxml.jackson.core.type.TypeReference<Map<String, Object>>() {};
                
                Map<String, Object> metadata = objectMapper.readValue(metadataJson, typeRef);
                log.debug("成功解析JSON元数据: {} 个字段", metadata.size());
                return metadata;
                
            } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
                log.debug("JSON解析失败，尝试简单字符串解析: {}", e.getMessage());
                
                // 如果JSON解析失败，尝试简单的字符串解析
                return parseMetadataSimple(metadataJson);
            }
            
        } catch (Exception e) {
            log.warn("解析元数据失败: {}", e.getMessage());
            return new HashMap<>();
        }
    }
    
    /**
     * 简单的元数据解析（备选方案）
     */
    private Map<String, Object> parseMetadataSimple(String metadataJson) {
        Map<String, Object> metadata = new HashMap<>();
        
        try {
            // 简单的键值对解析
            if (metadataJson.contains("doc_id")) {
                // 提取doc_id
                String docIdPattern = "\"doc_id\"\\s*:\\s*\"([^\"]+)\"";
                java.util.regex.Pattern pattern = java.util.regex.Pattern.compile(docIdPattern);
                java.util.regex.Matcher matcher = pattern.matcher(metadataJson);
                if (matcher.find()) {
                    metadata.put("doc_id", matcher.group(1));
                }
            }
            
            if (metadataJson.contains("chunk_index")) {
                // 提取chunk_index
                String chunkIndexPattern = "\"chunk_index\"\\s*:\\s*(\\d+)";
                java.util.regex.Pattern pattern = java.util.regex.Pattern.compile(chunkIndexPattern);
                java.util.regex.Matcher matcher = pattern.matcher(metadataJson);
                if (matcher.find()) {
                    metadata.put("chunk_index", Integer.parseInt(matcher.group(1)));
                }
            }
            
            if (metadataJson.contains("original_filename")) {
                // 提取original_filename
                String filenamePattern = "\"original_filename\"\\s*:\\s*\"([^\"]+)\"";
                java.util.regex.Pattern pattern = java.util.regex.Pattern.compile(filenamePattern);
                java.util.regex.Matcher matcher = pattern.matcher(metadataJson);
                if (matcher.find()) {
                    metadata.put("original_filename", matcher.group(1));
                }
            }
            
            if (metadataJson.contains("source_file")) {
                // 提取source_file
                String sourceFilePattern = "\"source_file\"\\s*:\\s*\"([^\"]+)\"";
                java.util.regex.Pattern pattern = java.util.regex.Pattern.compile(sourceFilePattern);
                java.util.regex.Matcher matcher = pattern.matcher(metadataJson);
                if (matcher.find()) {
                    metadata.put("source_file", matcher.group(1));
                }
            }
            
            log.debug("简单解析得到 {} 个元数据字段", metadata.size());
            return metadata;
            
        } catch (Exception e) {
            log.warn("简单元数据解析失败: {}", e.getMessage());
            return new HashMap<>();
        }
    }

    /**
     * 估算文本的token数量
     */
    private int estimateTokens(String text) {
        if (text == null || text.isEmpty()) {
            return 0;
        }
        
        // 简单估算：中文字符按1个token计算，英文单词按1个token计算
        int chineseChars = 0;
        int englishWords = 0;
        
        for (char c : text.toCharArray()) {
            if (c >= 0x4E00 && c <= 0x9FA5) {
                chineseChars++;
            }
        }
        
        // 英文单词数估算（按空格分割）
        String[] words = text.split("\\s+");
        for (String word : words) {
            if (word.matches(".*[a-zA-Z].*")) {
                englishWords++;
            }
        }
        
        // 总token数 = 中文字符数 + 英文单词数
        return chineseChars + englishWords;
    }

    /**
     * 重新处理单个文档
     */
    public void reprocessDocument(String docId) {
        log.info("开始重新处理文档: {}", docId);
        
        // 异步执行重新处理
        CompletableFuture.runAsync(() -> {
            try {
                // 将字符串ID转换为Long类型
                Long documentId;
                try {
                    documentId = Long.parseLong(docId);
                } catch (NumberFormatException e) {
                    log.error("文档ID格式错误: {}", docId);
                    throw new IllegalArgumentException("文档ID格式错误: " + docId);
                }
                
                // 查找文档
                com.river.LegalAssistant.entity.KnowledgeDocument doc = 
                    knowledgeDocumentRepository.findById(documentId)
                        .orElseThrow(() -> new IllegalArgumentException("文档不存在: " + docId));
                
                log.info("找到文档: {}, 开始重新处理", doc.getTitle());
                
                String content = doc.getContent();
                if (content == null || content.isEmpty()) {
                    log.warn("文档内容为空，无法重新处理: {}", docId);
                    return;
                }
                
                // 1. 删除该文档的现有向量数据 - 使用doc_id确保准确删除
                try {
                    // 优先使用doc_id删除，这是最准确的方式
                    int deletedByDocId = deleteVectorDataByDocId(documentId);
                    
                    // 如果按doc_id删除失败，尝试按sourceFile删除
                    if (deletedByDocId == 0) {
                        String sourceFile = doc.getSourceFile();
                        if (sourceFile != null && !sourceFile.isEmpty()) {
                            int deletedBySourceFile = deleteDocumentChunks(sourceFile);
                            if (deletedBySourceFile > 0) {
                                log.info("按sourceFile删除文档旧的向量数据成功: {} 条记录", deletedBySourceFile);
                            } else {
                                log.warn("按sourceFile删除文档旧的向量数据失败: {}", sourceFile);
                            }
                        }
                    } else {
                        log.info("按doc_id删除文档旧的向量数据成功: {} 条记录", deletedByDocId);
                    }
                } catch (Exception e) {
                    log.error("删除旧向量数据失败，继续处理: {}", e.getMessage());
                }
                
                // 2. 重新处理文档内容
                try {
                    // 使用DocumentProcessingService重新处理文档
                    // 这会自动使用法律文档分割器来分割法律文档，并准备完整的元数据
                    DocumentProcessingService.ProcessingResult result = 
                        documentProcessingService.processKnowledgeDocument(doc);
                    
                    if (result.isSuccess()) {
                        // 更新文档元数据
                        Map<String, Object> docMetadata = doc.getMetadata();
                        if (docMetadata == null) {
                            docMetadata = new HashMap<>();
                        }
                        docMetadata.put("segment_count", result.getSegmentCount());
                        docMetadata.put("last_reprocess_time", LocalDateTime.now().toString());
                        docMetadata.put("splitter_type", result.getSplitterType());
                        doc.setMetadata(docMetadata);
                        doc.setUpdatedAt(LocalDateTime.now());
                        
                        knowledgeDocumentRepository.save(doc);
                        
                        log.info("文档重新处理成功: {}, 新的块数: {}, 分割器: {}", 
                                doc.getTitle(), result.getSegmentCount(), result.getSplitterType());
                    } else {
                        log.error("文档重新处理失败: {}, 错误: {}", doc.getTitle(), result.getMessage());
                    }
                    
                } catch (Exception e) {
                    log.error("重新处理文档内容失败: {}", doc.getTitle(), e);
                }
                
            } catch (IllegalArgumentException e) {
                log.error("文档不存在: {}", docId);
                throw e;
            } catch (Exception e) {
                log.error("重新处理文档失败: {}", docId, e);
            }
        });
    }

    // ==================== 私有辅助方法 ====================

    /**
     * 将前端分类值转换为数据库分类值
     * 前端: law, contract, case, other
     * 数据库: LAW, CONTRACT_TEMPLATE, CASE, REGULATION
     */
    private String convertFrontendCategoryToDbCategory(String frontendCategory) {
        if (frontendCategory == null) {
            return null;
        }
        
        return switch (frontendCategory.toLowerCase()) {
            case "law" -> "LAW";
            case "contract" -> "CONTRACT_TEMPLATE";
            case "case" -> "CASE";
            case "other" -> "REGULATION";
            default -> frontendCategory.toUpperCase();
        };
    }
    
    /**
     * 将数据库分类值转换为前端分类值
     * 数据库: LAW, CONTRACT_TEMPLATE, CASE, REGULATION
     * 前端: law, contract, case, other
     */
    private String convertDbCategoryToFrontendCategory(String dbCategory) {
        if (dbCategory == null) {
            return null;
        }
        
        return switch (dbCategory.toUpperCase()) {
            case "LAW" -> "law";
            case "CONTRACT_TEMPLATE" -> "contract";
            case "CASE" -> "case";
            case "REGULATION" -> "other";
            default -> dbCategory.toLowerCase();
        };
    }

    /**
     * 使用简化的文本分割器（LangChain4j 在当前环境中可能不可用）
     */
    private List<String> splitDocumentWithLangChain4j(String content) {
        // 由于LangChain4j相关类可能不可用，直接使用简单分割器
        log.info("使用简化分割器处理文档");
        return splitDocumentSimple(content);
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
            // 如果SHA-256不可用，使用基于时间戳和文件名的简单ID生成
            return "doc_" + fileName.hashCode() + "_" + System.currentTimeMillis();
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
     * 删除文档的所有向量块（真实实现）
     * 
     * 支持两种删除方式：
     * 1. 按sourceFile删除（推荐）
     * 2. 按fileHash删除（备选）
     * 
     * @return 删除的记录数
     */
    private int deleteDocumentChunks(String sourceFile) {
        if (sourceFile == null || sourceFile.trim().isEmpty()) {
            log.warn("sourceFile为空，无法删除向量数据");
            return 0;
        }
        
        try {
            // 方案1：从langchain4j_embeddings表删除（优先）
            int deletedFromLangchain4j = deleteVectorDataFromLangchain4j(sourceFile);
            
            // 方案2：从vector_store表删除（如果存在）
            int deletedFromVectorStore = deleteVectorDataFromSpringAi(sourceFile);
            
            int totalDeleted = deletedFromLangchain4j + deletedFromVectorStore;
            
            if (totalDeleted > 0) {
                log.info("成功删除向量数据: {}, 共删除 {} 条记录 (LangChain4j: {}, SpringAI: {})", 
                        sourceFile, totalDeleted, deletedFromLangchain4j, deletedFromVectorStore);
            } else {
                log.warn("未找到需要删除的向量数据: {}", sourceFile);
            }
            
            return totalDeleted;
            
        } catch (Exception e) {
            log.error("删除向量数据失败: {}", sourceFile, e);
            return 0;
        }
    }
    
    /**
     * 从LangChain4j向量表删除数据 - 支持按doc_id删除
     */
    private int deleteVectorDataFromLangchain4j(String sourceFile) {
        try {
            // 使用JSONB查询删除匹配的记录
            // 支持按 original_filename、source_file 删除
            String sql = """
                DELETE FROM langchain4j_embeddings 
                WHERE metadata->>'original_filename' = ? 
                   OR metadata->>'source_file' = ?
                """;
            
            int deleted = jdbcTemplate.update(sql, sourceFile, sourceFile);
            
            if (deleted > 0) {
                log.debug("从langchain4j_embeddings删除 {} 条记录", deleted);
            }
            
            return deleted;
            
        } catch (Exception e) {
            log.warn("从langchain4j_embeddings删除失败: {}", e.getMessage());
            return 0;
        }
    }
    
    /**
     * 从LangChain4j向量表按doc_id删除数据
     */
    private int deleteVectorDataByDocId(Long docId) {
        try {
            // 先查询一下是否有匹配的数据，用于调试
            String checkSql = """
                SELECT COUNT(*) FROM langchain4j_embeddings 
                WHERE metadata->>'doc_id' = ?
                """;
            
            Integer count = jdbcTemplate.queryForObject(checkSql, Integer.class, docId.toString());
            log.info("检查doc_id {} 的向量数据数量: {}", docId, count);
            
            // 如果找到数据，执行删除
            if (count != null && count > 0) {
                String sql = """
                    DELETE FROM langchain4j_embeddings 
                    WHERE metadata->>'doc_id' = ?
                    """;
                
                int deleted = jdbcTemplate.update(sql, docId.toString());
                log.info("从langchain4j_embeddings按doc_id删除 {} 条记录", deleted);
                return deleted;
            } else {
                log.warn("按doc_id {} 未找到需要删除的向量数据", docId);
                
                // 尝试按original_filename删除作为备选方案
                String filenameSql = """
                    SELECT COUNT(*) FROM langchain4j_embeddings 
                    WHERE metadata->>'original_filename' LIKE ?
                    """;
                Integer filenameCount = jdbcTemplate.queryForObject(filenameSql, Integer.class, "%环境保护法%");
                log.info("按文件名匹配的向量数据数量: {}", filenameCount);
                
                return 0;
            }
            
        } catch (Exception e) {
            log.error("从langchain4j_embeddings按doc_id删除失败: {}", e.getMessage());
            return 0;
        }
    }
    
    /**
     * 从Spring AI向量表删除数据
     */
    private int deleteVectorDataFromSpringAi(String sourceFile) {
        try {
            // Spring AI的vector_store表结构可能不同
            String sql = """
                DELETE FROM vector_store 
                WHERE metadata->>'original_filename' = ? 
                   OR metadata->>'source_file' = ?
                """;
            
            int deleted = jdbcTemplate.update(sql, sourceFile, sourceFile);
            
            if (deleted > 0) {
                log.debug("从vector_store删除 {} 条记录", deleted);
            }
            
            return deleted;
            
        } catch (Exception e) {
            log.debug("从vector_store删除失败（表可能不存在）: {}", e.getMessage());
            return 0;
        }
    }
    
    /**
     * 按文件哈希删除向量数据
     * 用于支持文档更新功能
     */
    private void deleteVectorDataByFileHash(String fileHash) {
        if (fileHash == null || fileHash.trim().isEmpty()) {
            log.warn("fileHash为空，无法删除向量数据");
            return;
        }
        
        try {
            // 从langchain4j_embeddings表删除
            String sql1 = "DELETE FROM langchain4j_embeddings WHERE metadata->>'file_hash' = ?";
            int deleted1 = jdbcTemplate.update(sql1, fileHash);
            
            // 从vector_store表删除
            String sql2 = "DELETE FROM vector_store WHERE metadata->>'file_hash' = ?";
            int deleted2 = 0;
            try {
                deleted2 = jdbcTemplate.update(sql2, fileHash);
            } catch (Exception e) {
                log.debug("从vector_store删除失败: {}", e.getMessage());
            }
            
            int totalDeleted = deleted1 + deleted2;
            log.info("按fileHash删除向量数据: {}, 共删除 {} 条记录", fileHash, totalDeleted);
            
        } catch (Exception e) {
            log.error("按fileHash删除向量数据失败: {}", fileHash, e);
        }
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
     * 将上传的文档保存到数据库
     */
    private com.river.LegalAssistant.entity.KnowledgeDocument saveUploadedDocumentToDatabase(
            String fileName, String content, String category, 
            String description, int chunkCount, Map<String, Object> metadata) {
        try {
            // 计算文件哈希
            String fileHash = generateDocumentId(fileName, content);
            
            // 检查是否已存在
            if (knowledgeDocumentRepository.existsByFileHash(fileHash)) {
                log.info("文档已存在于数据库中：{}", fileName);
                // 返回已存在的文档
                return knowledgeDocumentRepository.findByFileHash(fileHash).orElse(null);
            }
            
            // 创建数据库实体
            com.river.LegalAssistant.entity.KnowledgeDocument document = 
                new com.river.LegalAssistant.entity.KnowledgeDocument();
            
            document.setTitle(fileName);
            // 存储完整内容，不进行截断，以确保重新处理时能够正确识别文档结构
            document.setContent(content);
            document.setSourceFile("uploads/" + fileName);
            document.setFileHash(fileHash);
            document.setDocumentType(determineDocumentType(fileName, category));
            
            // 设置元数据
            Map<String, Object> dbMetadata = new HashMap<>();
            dbMetadata.put("segment_count", chunkCount);
            dbMetadata.put("category", category != null ? category : "general");
            dbMetadata.put("description", description);
            dbMetadata.put("upload_source", "knowledge_base_service");
            dbMetadata.put("upload_time", LocalDateTime.now().toString());
            if (metadata != null) {
                dbMetadata.putAll(metadata);
            }
            document.setMetadata(dbMetadata);
            
            // 保存到数据库并返回
            com.river.LegalAssistant.entity.KnowledgeDocument savedDoc = 
                knowledgeDocumentRepository.save(document);
            log.info("文档保存到数据库成功：{}", fileName);
            
            return savedDoc;
            
        } catch (Exception e) {
            log.error("保存文档到数据库失败：{}", fileName, e);
            // 返回 null，允许主流程继续
            return null;
        }
    }
    
    /**
     * 根据文件名和分类确定文档类型
     */
    private String determineDocumentType(String fileName, String category) {
        String lowerName = fileName.toLowerCase();
        
        // 优先使用用户提供的分类，使用转换函数
        if (category != null && !category.trim().isEmpty()) {
            String dbCategory = convertFrontendCategoryToDbCategory(category);
            if (dbCategory != null) {
                return dbCategory;
            }
            
            // 兼容旧的中文分类
            switch (category.toLowerCase()) {
                case "法律", "法规" -> { return "LAW"; }
                case "合同" -> { return "CONTRACT_TEMPLATE"; }
                case "案例" -> { return "CASE"; }
                case "规章" -> { return "REGULATION"; }
            }
        }
        
        // 根据文件名判断
        if (lowerName.contains("合同") || lowerName.contains("contract")) {
            return "CONTRACT_TEMPLATE";
        } else if (lowerName.contains("法") || lowerName.contains("law") || 
                   lowerName.contains("法律")) {
            return "LAW";
        } else if (lowerName.contains("规") || lowerName.contains("regulation")) {
            return "REGULATION";
        } else if (lowerName.contains("案例") || lowerName.contains("case")) {
            return "CASE";
        } else {
            return "LAW"; // 默认为法律文档
        }
    }
    
    /**
     * 清空向量存储 - 实际实现
     */
    private void clearVectorStore() {
        try {
            log.info("开始清空向量存储...");
            
            // 清空LangChain4j向量表
            int deletedFromLangchain4j = jdbcTemplate.update("DELETE FROM langchain4j_embeddings");
            log.info("从langchain4j_embeddings删除 {} 条记录", deletedFromLangchain4j);
            
            // 清空Spring AI向量表（如果存在）
            int deletedFromVectorStore = 0;
            try {
                deletedFromVectorStore = jdbcTemplate.update("DELETE FROM vector_store");
                log.info("从vector_store删除 {} 条记录", deletedFromVectorStore);
            } catch (Exception e) {
                log.debug("vector_store表不存在或删除失败: {}", e.getMessage());
            }
            
            int totalDeleted = deletedFromLangchain4j + deletedFromVectorStore;
            log.info("向量存储清空完成，共删除 {} 条记录", totalDeleted);
            
        } catch (Exception e) {
            log.error("清空向量存储失败", e);
            throw new RuntimeException("清空向量存储失败: " + e.getMessage(), e);
        }
    }
}
