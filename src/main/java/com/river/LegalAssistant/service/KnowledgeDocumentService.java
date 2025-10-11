package com.river.LegalAssistant.service;

import com.river.LegalAssistant.entity.KnowledgeDocument;
import com.river.LegalAssistant.repository.KnowledgeDocumentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 知识文档管理服务
 * 
 * 专门负责知识文档的元数据CRUD操作，与KnowledgeDocumentRepository交互
 * 从KnowledgeBaseService中拆分出来，遵循单一职责原则
 * 
 * 职责：
 * - 文档元数据的创建、查询、更新、删除
 * - 文档列表的分页查询和过滤
 * - 文档统计信息的聚合
 * - 文档存在性和重复性检查
 * 
 * @author LegalAssistant Team
 * @since 2025-10-02
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class KnowledgeDocumentService {
    
    private final KnowledgeDocumentRepository knowledgeDocumentRepository;
    
    /**
     * 保存文档到数据库
     * 
     * @param title 文档标题
     * @param content 文档内容
     * @param sourceFile 源文件路径
     * @param fileHash 文件哈希值
     * @param documentType 文档类型
     * @param metadata 文档元数据
     * @return 保存的文档实体，如果已存在则返回null
     */
    @Transactional
    public KnowledgeDocument saveDocument(String title, String content, String sourceFile, 
                                         String fileHash, String documentType, 
                                         Map<String, Object> metadata) {
        try {
            // 检查是否已存在
            if (knowledgeDocumentRepository.existsByFileHash(fileHash)) {
                log.info("文档已存在于数据库中：{}", title);
                return null;
            }
            
            // 创建数据库实体
            KnowledgeDocument document = new KnowledgeDocument();
            document.setTitle(title);
            
            // 内容太长时进行截断（避免数据库字段溢出）
            if (content != null && content.length() > 2000) {
                document.setContent(content.substring(0, 2000) + "...[内容已截断]");
            } else {
                document.setContent(content);
            }
            
            document.setSourceFile(sourceFile);
            document.setFileHash(fileHash);
            document.setDocumentType(documentType);
            document.setMetadata(metadata);
            
            // 保存到数据库
            KnowledgeDocument saved = knowledgeDocumentRepository.save(document);
            log.info("文档保存到数据库成功：{} (ID: {})", title, saved.getId());
            
            return saved;
            
        } catch (Exception e) {
            log.error("保存文档到数据库失败：{}", title, e);
            return null;
        }
    }
    
    /**
     * 根据ID查询文档
     */
    public Optional<KnowledgeDocument> findById(Long id) {
        return knowledgeDocumentRepository.findById(id);
    }
    
    /**
     * 根据文件哈希查询文档
     */
    public Optional<KnowledgeDocument> findByFileHash(String fileHash) {
        return knowledgeDocumentRepository.findByFileHash(fileHash);
    }
    
    /**
     * 分页查询所有文档
     * 
     * @param page 页码（从0开始）
     * @param size 每页大小
     * @return 分页结果
     */
    public Page<KnowledgeDocument> findAll(int page, int size) {
        Pageable pageable = PageRequest.of(page, size, 
            Sort.by(Sort.Direction.DESC, "createdAt"));
        return knowledgeDocumentRepository.findAll(pageable);
    }
    
    /**
     * 按文档类型分页查询
     * 
     * @param documentType 文档类型
     * @param page 页码
     * @param size 每页大小
     * @return 分页结果
     */
    public Page<KnowledgeDocument> findByDocumentType(String documentType, int page, int size) {
        Pageable pageable = PageRequest.of(page, size, 
            Sort.by(Sort.Direction.DESC, "createdAt"));
        return knowledgeDocumentRepository.findByDocumentType(documentType, pageable);
    }
    
    /**
     * 删除文档
     * 
     * @param id 文档ID
     * @return 是否删除成功
     */
    @Transactional
    public boolean deleteById(Long id) {
        try {
            if (!knowledgeDocumentRepository.existsById(id)) {
                log.warn("要删除的文档不存在: {}", id);
                return false;
            }
            
            knowledgeDocumentRepository.deleteById(id);
            log.info("文档删除成功: {}", id);
            return true;
            
        } catch (Exception e) {
            log.error("删除文档失败: {}", id, e);
            return false;
        }
    }
    
    /**
     * 批量删除文档
     * 
     * @param ids 文档ID列表
     * @return 删除结果统计
     */
    @Transactional
    public Map<String, Object> batchDelete(List<Long> ids) {
        List<Long> successIds = new ArrayList<>();
        List<Long> failedIds = new ArrayList<>();
        
        for (Long id : ids) {
            if (deleteById(id)) {
                successIds.add(id);
            } else {
                failedIds.add(id);
            }
        }
        
        return Map.of(
            "totalRequested", ids.size(),
            "successCount", successIds.size(),
            "failedCount", failedIds.size(),
            "successIds", successIds,
            "failedIds", failedIds,
            "timestamp", LocalDateTime.now()
        );
    }
    
    /**
     * 更新文档元数据
     * 
     * @param id 文档ID
     * @param metadata 新的元数据
     * @return 更新后的文档
     */
    @Transactional
    public Optional<KnowledgeDocument> updateMetadata(Long id, Map<String, Object> metadata) {
        Optional<KnowledgeDocument> docOpt = knowledgeDocumentRepository.findById(id);
        
        if (docOpt.isEmpty()) {
            log.warn("要更新的文档不存在: {}", id);
            return Optional.empty();
        }
        
        KnowledgeDocument document = docOpt.get();
        
        // 合并元数据
        Map<String, Object> existingMetadata = document.getMetadata();
        if (existingMetadata == null) {
            existingMetadata = new HashMap<>();
        }
        existingMetadata.putAll(metadata);
        
        document.setMetadata(existingMetadata);
        document.setUpdatedAt(LocalDateTime.now());
        
        KnowledgeDocument updated = knowledgeDocumentRepository.save(document);
        log.info("文档元数据更新成功: {}", id);
        
        return Optional.of(updated);
    }
    
    /**
     * 检查文档是否存在
     * 
     * @param fileHash 文件哈希值
     * @return 是否存在
     */
    public boolean existsByFileHash(String fileHash) {
        return knowledgeDocumentRepository.existsByFileHash(fileHash);
    }
    
    /**
     * 获取文档总数
     */
    public long getTotalCount() {
        return knowledgeDocumentRepository.getTotalDocumentCount();
    }
    
    /**
     * 获取按文档类型的统计信息
     * 
     * @return Map<文档类型, 数量>
     */
    public Map<String, Long> getCountByDocumentType() {
        List<Object[]> results = knowledgeDocumentRepository.countByDocumentType();
        
        return results.stream()
            .collect(Collectors.toMap(
                arr -> (String) arr[0],
                arr -> (Long) arr[1]
            ));
    }
    
    /**
     * 获取完整的统计信息
     */
    public Map<String, Object> getStatistics() {
        try {
            Long totalDocuments = getTotalCount();
            Map<String, Long> categoryStats = getCountByDocumentType();
            
            // 转换文档类型为更友好的显示名称
            Map<String, Long> friendlyCategoryStats = new HashMap<>();
            for (Map.Entry<String, Long> entry : categoryStats.entrySet()) {
                String displayName = getDisplayNameForDocumentType(entry.getKey());
                friendlyCategoryStats.put(displayName, entry.getValue());
            }
            
            // 估算片段数量（每个文档平均产生约80个片段）
            long estimatedTotalChunks = totalDocuments * 80;
            
            return Map.of(
                "totalDocuments", totalDocuments,
                "totalChunks", estimatedTotalChunks,
                "totalContentLength", estimatedTotalChunks * 500, // 估算内容长度
                "categoryStats", friendlyCategoryStats,
                "averageChunksPerDoc", totalDocuments > 0 ? estimatedTotalChunks / totalDocuments : 0,
                "lastUpdated", LocalDateTime.now(),
                "source", "数据库（已索引文档）"
            );
            
        } catch (Exception e) {
            log.error("获取文档统计信息失败", e);
            return Map.of(
                "totalDocuments", 0,
                "totalChunks", 0,
                "totalContentLength", 0,
                "categoryStats", new HashMap<String, Long>(),
                "averageChunksPerDoc", 0,
                "lastUpdated", LocalDateTime.now(),
                "source", "错误",
                "error", e.getMessage()
            );
        }
    }
    
    /**
     * 转换文档列表为前端展示格式
     * 
     * @param documents 文档实体列表
     * @return 转换后的Map列表
     */
    public List<Map<String, Object>> convertToDisplayFormat(List<KnowledgeDocument> documents) {
        return documents.stream()
            .map(this::convertToDisplayFormat)
            .collect(Collectors.toList());
    }
    
    /**
     * 转换单个文档为前端展示格式
     */
    public Map<String, Object> convertToDisplayFormat(KnowledgeDocument doc) {
        Map<String, Object> docMap = new HashMap<>();
        docMap.put("docId", doc.getId().toString());
        docMap.put("title", doc.getTitle());
        docMap.put("fileName", doc.getTitle());
        docMap.put("category", getDisplayNameForDocumentType(doc.getDocumentType()));
        docMap.put("documentType", doc.getDocumentType());
        docMap.put("sourceFile", doc.getSourceFile());
        docMap.put("fileHash", doc.getFileHash());
        docMap.put("contentLength", doc.getContent() != null ? doc.getContent().length() : 0);
        docMap.put("uploadTime", doc.getCreatedAt());
        docMap.put("updatedTime", doc.getUpdatedAt());
        docMap.put("description", "自动索引的" + getDisplayNameForDocumentType(doc.getDocumentType()));
        
        // 从元数据中获取片段数量
        if (doc.getMetadata() != null && doc.getMetadata().containsKey("segment_count")) {
            docMap.put("chunkCount", doc.getMetadata().get("segment_count"));
        } else {
            docMap.put("chunkCount", 0);
        }
        
        return docMap;
    }
    
    /**
     * 将文档类型转换为更友好的显示名称
     */
    private String getDisplayNameForDocumentType(String documentType) {
        return switch (documentType) {
            case "LAW" -> "法律法规";
            case "REGULATION" -> "部门规章";
            case "CASE" -> "案例判决";
            case "CONTRACT_TEMPLATE" -> "合同模板";
            default -> documentType;
        };
    }
    
    /**
     * 根据文件名和分类确定文档类型
     * 
     * @param fileName 文件名
     * @param category 用户指定的分类（可选）
     * @return 文档类型
     */
    public String determineDocumentType(String fileName, String category) {
        String lowerName = fileName.toLowerCase();
        
        // 优先使用用户提供的分类
        if (category != null) {
            switch (category.toLowerCase()) {
                case "law", "legal", "法律", "法规" -> { return "LAW"; }
                case "contract", "合同" -> { return "CONTRACT_TEMPLATE"; }
                case "case", "案例" -> { return "CASE"; }
                case "regulation", "规章" -> { return "REGULATION"; }
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
}

