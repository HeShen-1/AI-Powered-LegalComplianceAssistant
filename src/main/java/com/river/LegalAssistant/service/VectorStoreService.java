package com.river.LegalAssistant.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 向量存储服务
 * 
 * 职责:
 * - 封装对VectorStore的所有操作
 * - 提供向量存储的统一入口
 * - 支持多向量存储的回退策略
 * - 处理文档的添加、删除、搜索
 * 
 * 从KnowledgeBaseService中提取,使其成为专门的向量存储层
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class VectorStoreService {

    private final VectorStore vectorStore;
    private final TextProcessingService textProcessingService;
    
    // LangChain4j向量存储(用于回退)
    private final dev.langchain4j.store.embedding.EmbeddingStore<dev.langchain4j.data.segment.TextSegment> langchain4jEmbeddingStore;
    private final dev.langchain4j.model.embedding.EmbeddingModel langchain4jEmbeddingModel;
    
    @Value("${app.rag.similarity-threshold:0.5}")
    private double similarityThreshold;

    /**
     * 添加单个文档到向量存储
     */
    public void add(String content, Map<String, Object> metadata) {
        log.info("添加文档到向量存储,内容长度: {} 字符", content.length());
        
        if (content.trim().isEmpty()) {
            log.warn("文档内容为空,跳过添加");
            return;
        }
        
        try {
            // 检查是否需要分块
            if (textProcessingService.needsChunking(content)) {
                log.info("文档内容较长,进行分块处理");
                addWithChunking(content, metadata);
            } else {
                // 内容较短,直接添加
                Document document = new Document(content, metadata);
                vectorStore.add(List.of(document));
                log.info("文档成功添加到向量存储(单块)");
            }
        } catch (Exception e) {
            log.error("文档添加失败", e);
            throw new RuntimeException("向量存储服务暂时不可用", e);
        }
    }

    /**
     * 批量添加文档
     */
    public void addBatch(List<Document> documents) {
        log.info("批量添加 {} 个文档到向量存储", documents.size());
        
        try {
            vectorStore.add(documents);
            log.info("批量添加完成");
        } catch (Exception e) {
            log.error("批量添加文档失败", e);
            throw new RuntimeException("向量存储服务暂时不可用", e);
        }
    }

    /**
     * 在特定法律文档中搜索相似内容
     * 
     * @param query 搜索查询
     * @param lawName 法律名称（如"环境保护法"）
     * @param maxResults 最大结果数
     * @return 过滤后的文档列表
     */
    public List<Document> searchSimilarInLaw(String query, String lawName, int maxResults) {
        log.info("在特定法律文档中搜索: {}, 法律: {}", 
                query.length() > 30 ? query.substring(0, 30) + "..." : query, lawName);
        
        try {
            // 1. 先搜索所有相关文档（扩大搜索范围以确保足够结果）
            List<Document> allResults = searchSimilar(query, maxResults * 3);
            
            // 2. 过滤出特定法律的结果
            List<Document> filteredResults = allResults.stream()
                .filter(doc -> {
                    // 检查metadata中的law_name字段
                    Object lawNameObj = doc.getMetadata().get("law_name");
                    if (lawNameObj != null && lawNameObj.toString().equals(lawName)) {
                        return true;
                    }
                    
                    // 降级：检查original_filename字段
                    Object filenameObj = doc.getMetadata().get("original_filename");
                    if (filenameObj != null) {
                        String filename = filenameObj.toString();
                        return filename.contains(lawName) || filename.startsWith(lawName);
                    }
                    
                    return false;
                })
                .limit(maxResults)
                .collect(Collectors.toList());
            
            log.info("在法律'{}'中找到 {} 个相关片段", lawName, filteredResults.size());
            return filteredResults;
            
        } catch (Exception e) {
            log.error("按法律名称搜索失败: {}", lawName, e);
            throw new RuntimeException("按法律名称搜索失败: " + e.getMessage(), e);
        }
    }
    
    /**
     * 按法律分类搜索
     * 
     * @param query 搜索查询
     * @param category 法律分类（如"环境保护"、"劳动社保"等）
     * @param maxResults 最大结果数
     * @return 过滤后的文档列表
     */
    public List<Document> searchSimilarByCategory(String query, String category, int maxResults) {
        log.info("按法律分类搜索: {}, 分类: {}", 
                query.length() > 30 ? query.substring(0, 30) + "..." : query, category);
        
        try {
            List<Document> allResults = searchSimilar(query, maxResults * 3);
            
            List<Document> filteredResults = allResults.stream()
                .filter(doc -> {
                    Object categoryObj = doc.getMetadata().get("law_category");
                    return categoryObj != null && categoryObj.toString().equals(category);
                })
                .limit(maxResults)
                .collect(Collectors.toList());
            
            log.info("在分类'{}'中找到 {} 个相关片段", category, filteredResults.size());
            return filteredResults;
            
        } catch (Exception e) {
            log.error("按法律分类搜索失败: {}", category, e);
            throw new RuntimeException("按法律分类搜索失败: " + e.getMessage(), e);
        }
    }
    
    /**
     * 按条文编号搜索（精确匹配）
     * 
     * @param lawName 法律名称
     * @param articleNumber 条文编号（如"第一条"）
     * @return 匹配的文档列表
     */
    public List<Document> searchByArticleNumber(String lawName, String articleNumber) {
        log.info("搜索特定条文: {} {}", lawName, articleNumber);
        
        try {
            // 使用条文编号作为查询词
            List<Document> allResults = searchSimilar(articleNumber, 50);
            
            return allResults.stream()
                .filter(doc -> {
                    // 同时匹配法律名称和条文编号
                    Object lawNameObj = doc.getMetadata().get("law_name");
                    Object articleObj = doc.getMetadata().get("article_number");
                    
                    boolean lawMatch = lawNameObj != null && lawNameObj.toString().equals(lawName);
                    boolean articleMatch = articleObj != null && articleObj.toString().equals(articleNumber);
                    
                    return lawMatch && articleMatch;
                })
                .collect(Collectors.toList());
                
        } catch (Exception e) {
            log.error("按条文编号搜索失败: {} {}", lawName, articleNumber, e);
            throw new RuntimeException("按条文编号搜索失败: " + e.getMessage(), e);
        }
    }
    
    /**
     * 搜索相似文档 - 支持多向量存储回退策略
     */
    public List<Document> searchSimilar(String query, int maxResults) {
        // 只输出查询摘要，避免泄露敏感内容
        String queryPreview = query != null && query.length() > 50 
            ? query.substring(0, 50) + "..." 
            : query;
        
        if (log.isDebugEnabled()) {
            log.debug("搜索相似文档，查询摘要: {}, 最大结果数: {}", queryPreview, maxResults);
        }
        
        try {
            // 1. 优先使用Spring AI向量存储
            List<Document> springAiResults = searchFromSpringAi(query, maxResults);
            
            // 2. 如果Spring AI结果不足,尝试从LangChain4j向量存储获取
            List<Document> allResults = new ArrayList<>(springAiResults);
            
            if (springAiResults.size() < maxResults) {
                if (log.isDebugEnabled()) {
                    log.debug("Spring AI向量存储结果不足({}个),尝试LangChain4j向量存储作为补充", 
                            springAiResults.size());
                }
                List<Document> langchain4jResults = searchFromLangChain4j(
                        query, maxResults - springAiResults.size());
                allResults.addAll(langchain4jResults);
                
                if (log.isDebugEnabled()) {
                    log.debug("从LangChain4j向量存储补充 {} 个结果", langchain4jResults.size());
                }
            }
            
            // 3. 过滤和最终处理
            List<Document> finalResults = allResults.stream()
                .filter(doc -> doc.getText() != null && !doc.getText().trim().isEmpty())
                .filter(doc -> doc.getText().trim().length() > 20)
                .limit(maxResults)
                .collect(Collectors.toList());
            
            if (log.isDebugEnabled()) {
                log.debug("最终返回 {} 个相似文档", finalResults.size());
            }
            
            return finalResults;
            
        } catch (Exception e) {
            log.error("相似文档搜索失败", e);
            throw new RuntimeException("向量搜索服务暂时不可用", e);
        }
    }

    /**
     * 删除文档
     * 注意:需要根据具体的VectorStore实现来定制
     */
    public void delete(String docId) {
        log.warn("删除文档功能需要根据具体的VectorStore实现来完成: {}", docId);
        // 实际实现需要根据使用的向量数据库来定制
    }

    /**
     * 分块处理长文档
     */
    private void addWithChunking(String content, Map<String, Object> metadata) {
        List<String> chunks = textProcessingService.splitIntoChunks(content);
        List<Document> documents = new ArrayList<>();
        
        for (int i = 0; i < chunks.size(); i++) {
            String chunk = chunks.get(i);
            
            // 为每个块创建元数据
            Map<String, Object> chunkMetadata = new HashMap<>(metadata);
            chunkMetadata.put("chunk_index", i);
            chunkMetadata.put("total_chunks", chunks.size());
            chunkMetadata.put("chunk_size", chunk.length());
            
            Document document = new Document(chunk, chunkMetadata);
            documents.add(document);
        }
        
        // 批量添加所有块
        vectorStore.add(documents);
        log.info("文档成功分块并添加到向量存储,共 {} 个块", documents.size());
    }

    /**
     * 从Spring AI向量存储搜索
     */
    private List<Document> searchFromSpringAi(String query, int maxResults) {
        try {
            List<Document> results = vectorStore.similaritySearch(query);
            return results.stream()
                .filter(doc -> doc.getText() != null && !doc.getText().trim().isEmpty())
                .filter(doc -> doc.getText().trim().length() > 20)
                .limit(maxResults)
                .collect(Collectors.toList());
        } catch (Exception e) {
            log.warn("Spring AI向量存储搜索失败: {}", e.getMessage());
            return new ArrayList<>();
        }
    }

    /**
     * 从LangChain4j向量存储搜索(作为回退策略)
     */
    private List<Document> searchFromLangChain4j(String query, int maxResults) {
        try {
            // 1. 使用LangChain4j的嵌入模型向量化查询
            dev.langchain4j.data.embedding.Embedding queryEmbedding = 
                    langchain4jEmbeddingModel.embed(query).content();
            
            // 2. 构建搜索请求
            dev.langchain4j.store.embedding.EmbeddingSearchRequest searchRequest = 
                dev.langchain4j.store.embedding.EmbeddingSearchRequest.builder()
                    .queryEmbedding(queryEmbedding)
                    .maxResults(maxResults)
                    .minScore(similarityThreshold)
                    .build();
            
            // 3. 搜索相似的文档片段
            dev.langchain4j.store.embedding.EmbeddingSearchResult<dev.langchain4j.data.segment.TextSegment> searchResult = 
                langchain4jEmbeddingStore.search(searchRequest);
            
            // 4. 转换为Spring AI的Document格式
            return searchResult.matches().stream()
                .map(match -> {
                    dev.langchain4j.data.segment.TextSegment segment = match.embedded();
                    
                    // 构建元数据
                    Map<String, Object> metadata = new HashMap<>();
                    metadata.put("similarity_score", match.score());
                    metadata.put("source_store", "langchain4j");
                    
                    // 尝试从文本内容推断文档类型
                    String text = segment.text();
                    inferDocumentType(text, metadata);
                    
                    return new Document(segment.text(), metadata);
                })
                .collect(Collectors.toList());
                
        } catch (Exception e) {
            log.warn("LangChain4j向量存储搜索失败: {}", e.getMessage());
            return new ArrayList<>();
        }
    }

    /**
     * 从文本内容推断文档类型
     */
    private void inferDocumentType(String text, Map<String, Object> metadata) {
        if (text.contains("刑法") || text.contains("刑事")) {
            metadata.put("original_filename", "刑法");
        } else if (text.contains("民法") || text.contains("民事")) {
            metadata.put("original_filename", "民法典");
        } else if (text.contains("合同") || text.contains("协议")) {
            metadata.put("original_filename", "合同模板");
        } else if (text.contains("劳动法") || text.contains("劳动")) {
            metadata.put("original_filename", "劳动法");
        } else if (text.contains("环境") || text.contains("环保")) {
            metadata.put("original_filename", "环境保护法");
        } else if (text.contains("宪法")) {
            metadata.put("original_filename", "宪法");
        } else {
            metadata.put("original_filename", "法律文档");
        }
    }
}

