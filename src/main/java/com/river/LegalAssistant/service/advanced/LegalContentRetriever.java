package com.river.LegalAssistant.service.advanced;

import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.rag.content.Content;
import dev.langchain4j.rag.content.retriever.ContentRetriever;
import dev.langchain4j.rag.query.Query;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.EmbeddingSearchRequest;
import dev.langchain4j.store.embedding.EmbeddingSearchResult;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import dev.langchain4j.model.embedding.EmbeddingModel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 法律领域多源内容检索器
 * 基于LangChain4j Advanced RAG框架实现
 * 
 * 支持的检索源：
 * 1. 向量数据库（核心法律文档）
 * 2. 法律条文数据库
 * 3. 案例数据库
 * 4. 实时网络搜索（可选）
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class LegalContentRetriever implements ContentRetriever {

    @Qualifier("langchain4jEmbeddingStore")
    private final EmbeddingStore<TextSegment> embeddingStore;  // LangChain4j PGVector 嵌入存储
    
    @Qualifier("langchain4jEmbeddingModel")
    private final EmbeddingModel embeddingModel;    // LangChain4j 嵌入模型
    
    @Value("${app.langchain4j.retriever.max-results:10}")
    private int maxResults;
    
    @Value("${app.langchain4j.retriever.min-score:0.7}")
    private double minScore;
    
    @Value("${app.langchain4j.retriever.enable-web-search:false}")
    private boolean enableWebSearch;
    
    @Value("${app.langchain4j.retriever.enable-case-search:true}")
    private boolean enableCaseSearch;

    @Override
    public List<Content> retrieve(Query query) {
        log.info("多源检索法律内容: {}", query.text());
        
        List<Content> allContent = new ArrayList<>();
        
        try {
            // 1. 向量数据库检索（主要来源）
            List<Content> vectorContent = retrieveFromVectorStore(query);
            allContent.addAll(vectorContent);
            log.debug("向量检索获得 {} 个结果", vectorContent.size());
            
            // 2. 法律条文检索
            List<Content> lawContent = retrieveFromLawDatabase(query);
            allContent.addAll(lawContent);
            log.debug("法律条文检索获得 {} 个结果", lawContent.size());
            
            // 3. 案例检索（如果启用）
            if (enableCaseSearch) {
                List<Content> caseContent = retrieveFromCaseDatabase(query);
                allContent.addAll(caseContent);
                log.debug("案例检索获得 {} 个结果", caseContent.size());
            }
            
            // 4. 网络检索（如果启用且查询包含时效性关键词）
            if (enableWebSearch && shouldSearchWeb(query)) {
                List<Content> webContent = retrieveFromWeb(query);
                allContent.addAll(webContent);
                log.debug("网络检索获得 {} 个结果", webContent.size());
            }
            
            // 5. 去重和排序
            List<Content> dedupedContent = removeDuplicates(allContent);
            List<Content> rankedContent = rankByRelevance(dedupedContent, query);
            
            // 6. 限制结果数量
            List<Content> finalResults = rankedContent.size() > maxResults 
                ? rankedContent.subList(0, maxResults) 
                : rankedContent;
            
            log.info("多源检索完成，返回 {} 个结果", finalResults.size());
            return finalResults;
            
        } catch (Exception e) {
            log.error("多源内容检索失败", e);
            // 降级：只返回向量检索结果
            return retrieveFromVectorStore(query);
        }
    }

    /**
     * 从向量数据库检索内容（使用LangChain4j PGVector）
     */
    private List<Content> retrieveFromVectorStore(Query query) {
        try {
            // 1. 将查询文本向量化
            Embedding queryEmbedding = embeddingModel.embed(query.text()).content();
            
            // 2. 构建搜索请求
            EmbeddingSearchRequest searchRequest = EmbeddingSearchRequest.builder()
                .queryEmbedding(queryEmbedding)
                .maxResults(maxResults)
                .minScore(minScore)
                .build();
            
            // 3. 执行相似性搜索
            EmbeddingSearchResult<TextSegment> searchResult = embeddingStore.search(searchRequest);
            
            // 4. 转换为Content对象
            return searchResult.matches().stream()
                .map(this::convertEmbeddingMatchToContent)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
                
        } catch (Exception e) {
            log.error("向量数据库检索失败", e);
            return Collections.emptyList();
        }
    }

    /**
     * 从法律条文数据库检索
     */
    private List<Content> retrieveFromLawDatabase(Query query) {
        try {
            // 这里可以实现专门的法律条文检索逻辑
            // 例如：基于法律条文编号、关键词等的精确匹配
            
            List<Content> lawContent = new ArrayList<>();
            
            // 检查是否包含法律条文关键词
            if (containsLawKeywords(query.text())) {
                // 模拟法律条文检索（实际应该连接法律条文数据库）
                String mockLawContent = generateMockLawContent(query.text());
                if (mockLawContent != null) {
                    lawContent.add(createLegalContent(mockLawContent, "法律条文数据库", "LAW_PROVISION"));
                }
            }
            
            return lawContent;
            
        } catch (Exception e) {
            log.error("法律条文数据库检索失败", e);
            return Collections.emptyList();
        }
    }

    /**
     * 从案例数据库检索
     */
    private List<Content> retrieveFromCaseDatabase(Query query) {
        try {
            List<Content> caseContent = new ArrayList<>();
            
            // 检查是否需要案例参考
            if (needsCaseReference(query.text())) {
                // 模拟案例检索（实际应该连接案例数据库）
                String mockCaseContent = generateMockCaseContent(query.text());
                if (mockCaseContent != null) {
                    caseContent.add(createLegalContent(mockCaseContent, "案例数据库", "CASE_REFERENCE"));
                }
            }
            
            return caseContent;
            
        } catch (Exception e) {
            log.error("案例数据库检索失败", e);
            return Collections.emptyList();
        }
    }

    /**
     * 从网络检索内容
     */
    private List<Content> retrieveFromWeb(Query query) {
        try {
            // 这里应该集成网络搜索引擎
            // 目前返回空列表，可以后续集成Google Search API等
            log.info("网络检索功能待实现: {}", query.text());
            return Collections.emptyList();
            
        } catch (Exception e) {
            log.error("网络检索失败", e);
            return Collections.emptyList();
        }
    }

    /**
     * 判断是否需要网络搜索
     */
    private boolean shouldSearchWeb(Query query) {
        String text = query.text().toLowerCase();
        return text.contains("最新") || 
               text.contains("新法规") || 
               text.contains("新政策") ||
               text.contains("最近") ||
               text.contains("2024") ||
               text.contains("2025");
    }

    /**
     * 检查是否包含法律条文关键词
     */
    private boolean containsLawKeywords(String query) {
        String[] lawKeywords = {"条", "款", "项", "法", "条例", "规定", "办法", "民法典", "刑法", "商法"};
        String lowerQuery = query.toLowerCase();
        
        return Arrays.stream(lawKeywords)
            .anyMatch(lowerQuery::contains);
    }

    /**
     * 检查是否需要案例参考
     */
    private boolean needsCaseReference(String query) {
        String[] caseKeywords = {"案例", "判例", "判决", "裁决", "先例", "法院", "审理"};
        String lowerQuery = query.toLowerCase();
        
        return Arrays.stream(caseKeywords)
            .anyMatch(lowerQuery::contains);
    }

    /**
     * 生成模拟法律条文内容
     */
    private String generateMockLawContent(String query) {
        // 这是一个模拟实现，实际应该从法律条文数据库查询
        if (query.contains("合同") || query.contains("违约")) {
            return "根据《民法典》第五百七十七条，当事人一方不履行合同义务或者履行合同义务不符合约定的，应当承担继续履行、采取补救措施或者赔偿损失等违约责任。";
        }
        if (query.contains("损害赔偿")) {
            return "根据《民法典》第一千一百六十五条，行为人因过错侵害他人民事权益造成损害的，应当承担侵权责任。";
        }
        return null;
    }

    /**
     * 生成模拟案例内容
     */
    private String generateMockCaseContent(String query) {
        // 这是一个模拟实现，实际应该从案例数据库查询
        if (query.contains("合同纠纷")) {
            return "参考案例：某公司诉某厂合同纠纷案。法院认为，双方签订的合同条款明确，违约方应承担相应责任。最终判决违约方赔偿经济损失并支付违约金。";
        }
        return null;
    }

    /**
     * 将LangChain4j EmbeddingMatch转换为Content
     */
    private Content convertEmbeddingMatchToContent(EmbeddingMatch<TextSegment> match) {
        try {
            TextSegment textSegment = match.embedded();
            double score = match.score();
            
            log.debug("检索到相关内容，相似度: {:.3f}, 内容预览: {}", 
                    score, truncateText(textSegment.text(), 100));
            
            return Content.from(textSegment);
            
        } catch (Exception e) {
            log.warn("EmbeddingMatch转换失败", e);
            return null;
        }
    }

    /**
     * 创建法律内容对象
     */
    private Content createLegalContent(String text, String source, String type) {
        TextSegment textSegment = TextSegment.from(text);
        return Content.from(textSegment);
    }

    /**
     * 去除重复内容
     */
    private List<Content> removeDuplicates(List<Content> contents) {
        Set<String> seen = new HashSet<>();
        return contents.stream()
            .filter(content -> {
                String text = content.textSegment().text();
                String normalized = normalizeForDeduplication(text);
                return seen.add(normalized);
            })
            .collect(Collectors.toList());
    }

    /**
     * 标准化文本用于去重
     */
    private String normalizeForDeduplication(String text) {
        return text.replaceAll("\\s+", " ")
                  .trim()
                  .toLowerCase();
    }

    /**
     * 根据相关性排序内容
     */
    private List<Content> rankByRelevance(List<Content> contents, Query query) {
        // 简单的相关性排序实现
        return contents.stream()
            .sorted((a, b) -> {
                double scoreA = calculateRelevanceScore(a, query);
                double scoreB = calculateRelevanceScore(b, query);
                return Double.compare(scoreB, scoreA); // 降序排列
            })
            .collect(Collectors.toList());
    }

    /**
     * 计算内容相关性分数
     */
    private double calculateRelevanceScore(Content content, Query query) {
        String text = content.textSegment().text().toLowerCase();
        String queryText = query.text().toLowerCase();
        
        double score = 0.0;
        
        // 关键词匹配分数
        String[] queryWords = queryText.split("\\s+");
        for (String word : queryWords) {
            if (text.contains(word)) {
                score += 1.0;
            }
        }
        
        // 法律关键词加权
        String[] legalKeywords = {"法律", "条款", "合同", "责任", "权利", "义务"};
        for (String keyword : legalKeywords) {
            if (text.contains(keyword) && queryText.contains(keyword)) {
                score += 0.5;
            }
        }
        
        // 长度惩罚（避免过长的无关内容）
        if (text.length() > 1000) {
            score *= 0.8;
        }
        
        return score;
    }

    /**
     * 截断文本到指定长度
     */
    private String truncateText(String text, int maxLength) {
        if (text == null || text.length() <= maxLength) {
            return text;
        }
        return text.substring(0, maxLength) + "...";
    }
}
