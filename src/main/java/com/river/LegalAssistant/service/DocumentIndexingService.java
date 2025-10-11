package com.river.LegalAssistant.service;

import com.river.LegalAssistant.entity.KnowledgeDocument;
import com.river.LegalAssistant.repository.KnowledgeDocumentRepository;
import com.river.LegalAssistant.service.splitter.LegalDocumentSplitter;
import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.DocumentParser;
import dev.langchain4j.data.document.DocumentSplitter;
import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.document.loader.FileSystemDocumentLoader;
import dev.langchain4j.data.document.parser.apache.tika.ApacheTikaDocumentParser;
import dev.langchain4j.data.document.splitter.DocumentSplitters;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.EmbeddingStoreIngestor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.FileInputStream;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 文档索引服务
 * 
 * 使用LangChain4j进行文档处理和向量化存储
 * 存储到独立的langchain4j_embeddings表
 * 
 * 系统向量存储架构：
 * 1. vector_store表 - 用于Spring AI (KnowledgeBaseService上传功能)
 * 2. langchain4j_embeddings表 - 用于LangChain4j (DocumentIndexingService索引功能和Advanced RAG)
 * 
 * 功能：
 * 1. 文档加载和解析
 * 2. 文档分割（chunking）- 支持法律文档专用分割器
 * 3. 向量化和存储到LangChain4j专用表
 * 4. 批量索引处理
 */
@Service
@Slf4j
public class DocumentIndexingService {

    private final EmbeddingModel embeddingModel;
    private final EmbeddingStore<TextSegment> embeddingStore;
    private final KnowledgeDocumentRepository knowledgeDocumentRepository;
    private final LegalDocumentSplitter legalDocumentSplitter;
    private final JdbcTemplate jdbcTemplate;
    
    @Value("${app.etl.chunk-size:800}")
    private int chunkSize;
    
    @Value("${app.etl.chunk-overlap:80}")
    private int chunkOverlap;
    
    @Value("${app.etl.min-chunk-size:10}")
    private int minChunkSize;
    
    /**
     * 构造函数
     */
    public DocumentIndexingService(
            @Qualifier("langchain4jEmbeddingModel") EmbeddingModel embeddingModel,
            @Qualifier("langchain4jEmbeddingStore") EmbeddingStore<TextSegment> embeddingStore,
            KnowledgeDocumentRepository knowledgeDocumentRepository,
            LegalDocumentSplitter legalDocumentSplitter,
            JdbcTemplate jdbcTemplate) {
        this.embeddingModel = embeddingModel;
        this.embeddingStore = embeddingStore;
        this.knowledgeDocumentRepository = knowledgeDocumentRepository;
        this.legalDocumentSplitter = legalDocumentSplitter;
        this.jdbcTemplate = jdbcTemplate;
        log.info("DocumentIndexingService 初始化完成,已启用法律文档分割器");
    }

    /**
     * 索引单个文档
     */
    public IndexingResult indexDocument(String filePath) {
        return indexDocument(filePath, false);
    }
    
    /**
     * 索引单个文档（支持强制重建）
     * 
     * @param filePath 文件路径
     * @param forceRebuild 是否强制重建（删除已存在的旧数据）
     */
    public IndexingResult indexDocument(String filePath, boolean forceRebuild) {
        log.info("开始索引文档: {}, 强制重建: {}", filePath, forceRebuild);
        
        try {
            long startTime = System.currentTimeMillis();
            
            // 1. 检查文档是否已存在
            if (knowledgeDocumentRepository.existsBySourceFile(filePath)) {
                if (!forceRebuild) {
                    log.info("文档已存在于数据库中，跳过索引: {}", filePath);
                    return new IndexingResult(
                        true,
                        filePath,
                        0,
                        0L,
                        "文档已存在，跳过索引",
                        LocalDateTime.now()
                    );
                } else {
                    log.info("文档已存在，但强制重建，先删除旧数据: {}", filePath);
                    deleteExistingDocument(filePath);
                }
            }
            
            // 2. 检查文件是否存在
            Path path = Paths.get(filePath);
            if (!path.toFile().exists()) {
                throw new IllegalArgumentException("文件不存在: " + filePath);
            }
            
            // 3. 计算文件哈希
            String fileHash = calculateFileHash(path.toFile());
            
            // 4. 检查哈希是否已存在
            if (knowledgeDocumentRepository.existsByFileHash(fileHash)) {
                log.info("相同文件已存在（基于哈希值），跳过索引: {}", filePath);
                return new IndexingResult(
                    true,
                    filePath,
                    0,
                    0L,
                    "相同文件已存在，跳过索引",
                    LocalDateTime.now()
                );
            }
            
            // 5. 处理和存储文档
            int segmentCount = processAndStoreDocument(filePath);
            
            // 6. 保存文档记录到数据库
            saveDocumentRecord(filePath, fileHash, segmentCount);
            
            long duration = System.currentTimeMillis() - startTime;
            
            log.info("文档索引完成: {}, 片段数: {}, 耗时: {}ms", filePath, segmentCount, duration);
            
            return new IndexingResult(
                true,
                filePath,
                segmentCount,
                duration,
                null,
                LocalDateTime.now()
            );
            
        } catch (Exception e) {
            log.error("文档索引失败: {}", filePath, e);
            return new IndexingResult(
                false,
                filePath,
                0,
                0L,
                e.getMessage(),
                LocalDateTime.now()
            );
        }
    }

    /**
     * 批量索引目录
     */
    public BatchIndexingResult indexDirectory(String directoryPath, boolean recursive) {
        log.info("开始批量索引目录: {}, 递归: {}", directoryPath, recursive);
        
        long startTime = System.currentTimeMillis();
        
        // 1. 发现目录中的文档
        List<String> documentPaths = discoverDocuments(directoryPath, recursive);
        
        if (documentPaths.isEmpty()) {
            log.warn("目录中没有发现支持的文档: {}", directoryPath);
            return new BatchIndexingResult(
                true,
                directoryPath,
                0,
                0,
                0,
                0L,
                null,
                Collections.emptyList(),
                LocalDateTime.now()
            );
        }
        
        log.info("发现 {} 个文档待索引", documentPaths.size());
        
        // 2. 批量处理文档
        List<IndexingResult> results = new ArrayList<>();
        int totalSegments = 0;
        int successfulDocuments = 0;
        
        for (String documentPath : documentPaths) {
            try {
                IndexingResult result = indexDocument(documentPath);
                results.add(result);
                
                if (result.success()) {
                    successfulDocuments++;
                    totalSegments += result.segmentCount();
                }
            } catch (Exception e) {
                log.error("索引文档失败: {}", documentPath, e);
                results.add(new IndexingResult(
                    false,
                    documentPath,
                    0,
                    0L,
                    e.getMessage(),
                    LocalDateTime.now()
                ));
            }
        }
        
        long duration = System.currentTimeMillis() - startTime;
        
        log.info("批量索引完成: 总计 {} 个文档, 成功 {} 个, 生成 {} 个片段, 耗时 {}ms", 
                documentPaths.size(), successfulDocuments, totalSegments, duration);
        
        boolean success = successfulDocuments > 0;
        String error = successfulDocuments == 0 ? "所有文档索引失败" : 
                      successfulDocuments < documentPaths.size() ? "部分文档索引失败" : null;
        
        return new BatchIndexingResult(
            success,
            directoryPath,
            documentPaths.size(),
            successfulDocuments,
            totalSegments,
            duration,
            error,
            results,
            LocalDateTime.now()
        );
    }

    /**
     * 使用LangChain4j处理和存储文档
     */
    private int processAndStoreDocument(String originalPath) {
        log.debug("开始处理和存储文档: {}", originalPath);
        
        try {
            // 1. 加载文档
            Path path = Paths.get(originalPath);
            Document document = loadDocument(path);
            
            // 2. 确定文档类型并选择合适的分割器
            String fileName = path.getFileName().toString();
            DocumentSplitter splitter = selectSplitter(fileName);
            
            List<TextSegment> segments = splitter.split(document);
            log.debug("文档 {} 分割为 {} 个片段", originalPath, segments.size());
            
            // 3. 过滤太短的片段
            List<TextSegment> filteredSegments = segments.stream()
                .filter(segment -> segment.text().trim().length() >= minChunkSize)
                .collect(Collectors.toList());
            
            log.debug("过滤后保留 {} 个有效片段", filteredSegments.size());
            
            // 4. 使用EmbeddingStoreIngestor批量存储
            EmbeddingStoreIngestor ingestor = EmbeddingStoreIngestor.builder()
                .embeddingModel(embeddingModel)
                .embeddingStore(embeddingStore)
                .build();
            
            // 5. 重新创建Document对象用于ingest,保留分割器生成的元数据
            List<Document> documentsToIngest = filteredSegments.stream()
                .map(segment -> {
                    Map<String, Object> metadata = new HashMap<>();
                    
                    // 先保留法律文档分割器生成的元数据（章节、条文等结构化信息）
                    if (segment.metadata() != null) {
                        metadata.putAll(segment.metadata().toMap());
                    }
                    
                    // 再添加索引相关的元数据（避免覆盖分割器的重要元数据）
                    // 只添加不存在的字段，避免覆盖
                    metadata.putIfAbsent("source", originalPath);
                    metadata.putIfAbsent("indexed_at", LocalDateTime.now().toString());
                    
                    return Document.from(segment.text(), Metadata.from(metadata));
                })
                .collect(Collectors.toList());
            
            // 6. 执行向量化和存储
            ingestor.ingest(documentsToIngest);
            
            return filteredSegments.size();
            
        } catch (Exception e) {
            log.error("处理和存储文档失败: {}", originalPath, e);
            throw new RuntimeException("处理和存储文档失败: " + e.getMessage(), e);
        }
    }
    
    /**
     * 根据文件名选择合适的文档分割器
     */
    private DocumentSplitter selectSplitter(String fileName) {
        String lowerName = fileName.toLowerCase();
        
        // 判断是否是法律文档
        boolean isLegalDocument = lowerName.contains("法") || 
                                 lowerName.contains("law") || 
                                 lowerName.contains("法律") || 
                                 lowerName.contains("法规") ||
                                 lowerName.contains("条例") ||
                                 lowerName.contains("规定") ||
                                 lowerName.contains("民法") ||
                                 lowerName.contains("刑法") ||
                                 lowerName.contains("宪法") ||
                                 lowerName.contains("诉讼法");
        
        if (isLegalDocument && legalDocumentSplitter != null) {
            log.debug("文档 {} 识别为法律文档,使用法律文档分割器", fileName);
            return legalDocumentSplitter;
        } else {
            log.debug("文档 {} 使用通用递归分割器", fileName);
            return DocumentSplitters.recursive(chunkSize, chunkOverlap);
        }
    }

    /**
     * 加载文档
     */
    private Document loadDocument(Path filePath) {
        try {
            // 使用Apache Tika解析器
            DocumentParser parser = new ApacheTikaDocumentParser();
            
            // 加载文档
            Document document = FileSystemDocumentLoader.loadDocument(filePath, parser);
            
            // 创建带元数据的文档
            Metadata metadata = Metadata.from(Map.of(
                "source", filePath.toString(),
                "file_name", filePath.getFileName().toString(),
                "loaded_at", LocalDateTime.now().toString()
            ));
            
            // 创建带元数据的文档
            return Document.from(document.text(), metadata);
            
        } catch (Exception e) {
            log.error("文档加载失败: {}", filePath, e);
            throw new RuntimeException("文档加载失败: " + e.getMessage(), e);
        }
    }

    /**
     * 发现目录中的文档文件
     */
    private List<String> discoverDocuments(String directoryPath, boolean recursive) {
        List<String> documentPaths = new ArrayList<>();
        
        File directory = new File(directoryPath);
        if (!directory.exists() || !directory.isDirectory()) {
            log.warn("目录不存在或不是目录: {}", directoryPath);
            return documentPaths;
        }
        
        // 支持的文档格式
        Set<String> supportedExtensions = Set.of(
            ".pdf", ".docx", ".doc", ".txt", ".md", ".rtf", ".odt"
        );
        
        discoverDocumentsRecursive(directory, supportedExtensions, recursive, documentPaths);
        
        return documentPaths;
    }

    /**
     * 递归发现文档
     */
    private void discoverDocumentsRecursive(File directory, Set<String> supportedExtensions, 
                                          boolean recursive, List<String> documentPaths) {
        File[] files = directory.listFiles();
        if (files == null) {
            return;
        }
        
        for (File file : files) {
            if (file.isDirectory() && recursive) {
                discoverDocumentsRecursive(file, supportedExtensions, true, documentPaths);
            } else if (file.isFile()) {
                String fileName = file.getName().toLowerCase();
                boolean isSupported = supportedExtensions.stream()
                    .anyMatch(fileName::endsWith);
                
                if (isSupported) {
                    documentPaths.add(file.getAbsolutePath());
                }
            }
        }
    }

    /**
     * 保存文档记录到数据库
     */
    private void saveDocumentRecord(String filePath, String fileHash, int segmentCount) {
        try {
            File file = new File(filePath);
            String fileName = file.getName();
            String documentType = determineDocumentType(fileName);
            
            // 读取文档内容用于预览
            String content = "";
            try {
                // 使用LangChain4j的文档加载器来读取内容
                Path path = Paths.get(filePath);
                Document document = loadDocument(path);
                content = document.text();
                
                // 限制内容长度用于预览
                if (content.length() > 2000) {
                    content = content.substring(0, 2000) + "...";
                }
            } catch (Exception e) {
                log.warn("读取文档内容失败，将保存空内容: {}", filePath, e);
            }
            
            KnowledgeDocument document = new KnowledgeDocument();
            document.setTitle(fileName);
            document.setContent(content);
            document.setSourceFile(filePath);
            document.setFileHash(fileHash);
            document.setDocumentType(documentType);
            
            Map<String, Object> metadata = new HashMap<>();
            metadata.put("segment_count", segmentCount);
            metadata.put("indexed_at", LocalDateTime.now().toString());
            metadata.put("file_size", file.length());
            metadata.put("source_type", "langchain4j_indexing");
            document.setMetadata(metadata);
            
            knowledgeDocumentRepository.save(document);
            log.debug("文档记录保存成功: {}", fileName);
            
        } catch (Exception e) {
            log.error("保存文档记录失败: {}", filePath, e);
            // 不抛出异常，允许索引继续
        }
    }

    /**
     * 根据文件名确定文档类型
     */
    private String determineDocumentType(String fileName) {
        String lowerName = fileName.toLowerCase();
        
        if (lowerName.contains("合同") || lowerName.contains("contract")) {
            return "CONTRACT_TEMPLATE";
        } else if (lowerName.contains("法") || lowerName.contains("law") || 
                   lowerName.contains("法律") || lowerName.contains("法规")) {
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
     * 删除已存在的文档及其向量数据
     */
    private void deleteExistingDocument(String filePath) {
        try {
            // 1. 查找文档记录
            Optional<KnowledgeDocument> existingDoc = knowledgeDocumentRepository.findBySourceFile(filePath);
            if (existingDoc.isEmpty()) {
                log.warn("未找到源文件为 {} 的文档记录", filePath);
                return;
            }
            
            KnowledgeDocument doc = existingDoc.get();
            String fileHash = doc.getFileHash();
            
            // 2. 删除LangChain4j嵌入表中的数据（通过metadata过滤）
            try {
                String sql = "DELETE FROM langchain4j_embeddings WHERE metadata->>'source' = ?";
                int deletedLangchain = jdbcTemplate.update(sql, filePath);
                log.info("删除LangChain4j嵌入表中的 {} 条记录", deletedLangchain);
            } catch (Exception e) {
                log.warn("删除LangChain4j嵌入表数据失败: {}", e.getMessage());
            }
            
            // 3. 删除数据库文档记录（这会级联删除关联的向量数据，如果有外键的话）
            knowledgeDocumentRepository.delete(doc);
            log.info("删除文档记录: {} (ID: {})", doc.getTitle(), doc.getId());
            
        } catch (Exception e) {
            log.error("删除已存在文档失败: {}", filePath, e);
            throw new RuntimeException("删除已存在文档失败: " + e.getMessage(), e);
        }
    }
    
    /**
     * 计算文件哈希值
     */
    private String calculateFileHash(File file) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            
            try (FileInputStream fis = new FileInputStream(file)) {
                byte[] buffer = new byte[8192];
                int bytesRead;
                while ((bytesRead = fis.read(buffer)) != -1) {
                    digest.update(buffer, 0, bytesRead);
                }
            }
            
            byte[] hashBytes = digest.digest();
            StringBuilder hexString = new StringBuilder();
            for (byte b : hashBytes) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) {
                    hexString.append('0');
                }
                hexString.append(hex);
            }
            return hexString.toString();
            
        } catch (Exception e) {
            log.error("计算文件哈希失败: {}", file.getAbsolutePath(), e);
            // 返回基于文件名和大小的简单哈希
            return String.valueOf((file.getName() + file.length()).hashCode());
        }
    }

    /**
     * 获取索引统计信息
     */
    public IndexingStatistics getIndexingStatistics() {
        try {
            // 查询向量存储中的总片段数
            // 注意：Spring AI的VectorStore接口可能不直接提供计数方法
            // 这里使用数据库查询作为近似值
            long totalSegments = knowledgeDocumentRepository.count();
            
            // 获取最后更新时间
            LocalDateTime lastUpdate = knowledgeDocumentRepository.findTopByOrderByUpdatedAtDesc()
                .map(KnowledgeDocument::getUpdatedAt)
                .orElse(null);
            
            return new IndexingStatistics(
                totalSegments,
                lastUpdate,
                "Spring AI PgVectorStore",
                chunkSize,
                chunkOverlap
            );
            
        } catch (Exception e) {
            log.error("获取索引统计信息失败", e);
            return new IndexingStatistics(
                0L,
                null,
                "Spring AI PgVectorStore",
                chunkSize,
                chunkOverlap
            );
        }
    }

    // 结果类定义
    public record IndexingResult(
        boolean success,
        String filePath,
        int segmentCount,
        long duration,
        String error,
        LocalDateTime timestamp
    ) {}

    public record BatchIndexingResult(
        boolean success,
        String directoryPath,
        int totalDocuments,
        int successfulDocuments,
        int totalSegments,
        long duration,
        String error,
        List<IndexingResult> results,
        LocalDateTime timestamp
    ) {}

    public record IndexingStatistics(
        long totalSegments,
        LocalDateTime lastUpdate,
        String storeType,
        int chunkSize,
        int chunkOverlap
    ) {}
}