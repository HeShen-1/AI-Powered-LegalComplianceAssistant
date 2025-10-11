package com.river.LegalAssistant.service.advanced;

import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.rag.content.Content;
import dev.langchain4j.rag.content.aggregator.ContentAggregator;
import dev.langchain4j.rag.query.Query;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 法律内容聚合器和重排序器
 * 基于LangChain4j Advanced RAG框架实现
 * 
 * 功能：
 * 1. 合并多个检索器的结果
 * 2. 去除重复内容
 * 3. 基于法律相关性重排序
 * 4. 应用Reciprocal Rank Fusion (RRF)算法
 * 5. 法律领域特定的相关性评分
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class LegalContentAggregator implements ContentAggregator {

    @Value("${app.langchain4j.aggregator.max-results:10}")
    private int maxResults;
    
    @Value("${app.langchain4j.aggregator.similarity-threshold:0.85}")
    private double similarityThreshold;
    
    @Value("${app.langchain4j.aggregator.rrf-k:60}")
    private int rrfK; // RRF算法的k参数
    
    // 法律内容类型权重
    private static final Map<String, Double> CONTENT_TYPE_WEIGHTS = Map.of(
        "LAW_PROVISION", 1.0,      // 法律条文
        "CASE_REFERENCE", 0.8,     // 案例参考
        "CONTRACT_CLAUSE", 0.9,    // 合同条款
        "REGULATION", 0.85,        // 法规规定
        "GENERAL", 0.7,            // 一般内容
        "WEB_CONTENT", 0.6         // 网络内容
    );
    
    // 法律关键词权重
    private static final Map<String, Double> LEGAL_KEYWORD_WEIGHTS = Map.of(
        "民法典", 1.0,
        "合同法", 0.9,
        "违约责任", 0.8,
        "损害赔偿", 0.8,
        "法律责任", 0.7,
        "合同条款", 0.7,
        "法律条文", 0.6
    );

    @Override
    public List<Content> aggregate(Map<Query, Collection<List<Content>>> queryToContents) {
        log.info("开始聚合法律内容，共 {} 个查询，总内容数: {}", 
                queryToContents.size(), 
                queryToContents.values().stream()
                    .flatMap(Collection::stream)
                    .mapToInt(List::size).sum());
        
        try {
            // 1. 收集所有内容
            List<ScoredContent> allScoredContent = collectAndScoreContent(queryToContents);
            log.debug("收集到 {} 个评分内容", allScoredContent.size());
            
            // 2. 去重
            List<ScoredContent> dedupedContent = removeDuplicates(allScoredContent);
            log.debug("去重后剩余 {} 个内容", dedupedContent.size());
            
            // 3. 应用Reciprocal Rank Fusion
            List<ScoredContent> rrfContent = applyReciprocalRankFusion(dedupedContent, queryToContents);
            log.debug("RRF处理后 {} 个内容", rrfContent.size());
            
            // 4. 法律相关性重排序
            List<ScoredContent> rerankedContent = reRankByLegalRelevance(rrfContent, queryToContents.keySet());
            log.debug("重排序后 {} 个内容", rerankedContent.size());
            
            // 5. 限制结果数量并返回
            List<Content> finalResults = rerankedContent.stream()
                .limit(maxResults)
                .map(ScoredContent::content)
                .collect(Collectors.toList());
            
            log.info("内容聚合完成，返回 {} 个结果", finalResults.size());
            return finalResults;
            
        } catch (Exception e) {
            log.error("内容聚合失败", e);
            // 降级处理：简单合并所有内容
            return queryToContents.values().stream()
                .flatMap(Collection::stream)
                .flatMap(List::stream)
                .limit(maxResults)
                .collect(Collectors.toList());
        }
    }

    /**
     * 收集并评分所有内容
     */
    private List<ScoredContent> collectAndScoreContent(Map<Query, Collection<List<Content>>> queryToContents) {
        List<ScoredContent> scoredContents = new ArrayList<>();
        
        for (Map.Entry<Query, Collection<List<Content>>> entry : queryToContents.entrySet()) {
            Query query = entry.getKey();
            Collection<List<Content>> contentLists = entry.getValue();
            
            for (List<Content> contents : contentLists) {
                for (int i = 0; i < contents.size(); i++) {
                    Content content = contents.get(i);
                    
                    // 计算基础分数
                    double baseScore = calculateBaseScore(content, query);
                    
                    // 计算位置分数（排名越靠前分数越高）
                    double positionScore = 1.0 / (i + 1);
                    
                    // 综合分数
                    double totalScore = baseScore * 0.7 + positionScore * 0.3;
                    
                    scoredContents.add(new ScoredContent(content, totalScore, query, i));
                }
            }
        }
        
        return scoredContents;
    }

    /**
     * 计算内容的基础分数
     */
    private double calculateBaseScore(Content content, Query query) {
        String contentText = content.textSegment().text().toLowerCase();
        String queryText = query.text().toLowerCase();
        
        double score = 0.0;
        
        // 1. 关键词匹配分数
        score += calculateKeywordMatchScore(contentText, queryText);
        
        // 2. 法律术语匹配分数
        score += calculateLegalTermScore(contentText, queryText);
        
        // 3. 内容类型权重
        score *= getContentTypeWeight(content);
        
        // 4. 内容长度调整
        score *= calculateLengthAdjustment(contentText);
        
        return Math.min(score, 1.0); // 限制最高分为1.0
    }

    /**
     * 计算关键词匹配分数
     */
    private double calculateKeywordMatchScore(String contentText, String queryText) {
        String[] queryWords = queryText.split("\\s+");
        int matches = 0;
        
        for (String word : queryWords) {
            if (word.length() > 1 && contentText.contains(word)) {
                matches++;
            }
        }
        
        return queryWords.length > 0 ? (double) matches / queryWords.length : 0.0;
    }

    /**
     * 计算法律术语匹配分数
     */
    private double calculateLegalTermScore(String contentText, String queryText) {
        double score = 0.0;
        
        for (Map.Entry<String, Double> entry : LEGAL_KEYWORD_WEIGHTS.entrySet()) {
            String keyword = entry.getKey().toLowerCase();
            Double weight = entry.getValue();
            
            if (contentText.contains(keyword) && queryText.contains(keyword)) {
                score += weight * 0.1; // 法律术语匹配加分
            }
        }
        
        return score;
    }

    /**
     * 获取内容类型权重
     */
    private double getContentTypeWeight(Content content) {
        // 尝试从内容元数据获取类型
        String contentType = extractContentType(content);
        return CONTENT_TYPE_WEIGHTS.getOrDefault(contentType, 0.7);
    }

    /**
     * 提取内容类型
     */
    private String extractContentType(Content content) {
        // 简单的内容类型识别
        String text = content.textSegment().text();
        
        if (text.contains("第") && text.contains("条") && (text.contains("法") || text.contains("典"))) {
            return "LAW_PROVISION";
        }
        if (text.contains("案例") || text.contains("判决") || text.contains("法院")) {
            return "CASE_REFERENCE";
        }
        if (text.contains("合同") && text.contains("条款")) {
            return "CONTRACT_CLAUSE";
        }
        if (text.contains("规定") || text.contains("办法") || text.contains("条例")) {
            return "REGULATION";
        }
        
        return "GENERAL";
    }

    /**
     * 计算长度调整因子
     */
    private double calculateLengthAdjustment(String text) {
        int length = text.length();
        
        if (length < 50) {
            return 0.7; // 太短的内容降权
        } else if (length > 2000) {
            return 0.8; // 太长的内容轻微降权
        } else {
            return 1.0; // 适中长度保持原权重
        }
    }

    /**
     * 去除重复内容
     */
    private List<ScoredContent> removeDuplicates(List<ScoredContent> contents) {
        Map<String, ScoredContent> uniqueMap = new LinkedHashMap<>();
        
        for (ScoredContent current : contents) {
            String normalizedText = normalizeTextForDeduplication(current.content().textSegment().text());
            
            // 检查是否存在相似内容
            String duplicateKey = findDuplicateKey(normalizedText, uniqueMap.keySet());
            
            if (duplicateKey != null) {
                // 存在重复，保留分数更高的
                ScoredContent existing = uniqueMap.get(duplicateKey);
                if (current.score() > existing.score()) {
                    uniqueMap.remove(duplicateKey);
                    uniqueMap.put(normalizedText, current);
                }
            } else {
                uniqueMap.put(normalizedText, current);
            }
        }
        
        return new ArrayList<>(uniqueMap.values());
    }
    
    /**
     * 查找重复的键
     */
    private String findDuplicateKey(String normalizedText, Set<String> existingKeys) {
        for (String key : existingKeys) {
            if (calculateTextSimilarity(normalizedText, key) > similarityThreshold) {
                return key;
            }
        }
        return null;
    }
    
    /**
     * 标准化文本用于去重
     */
    private String normalizeTextForDeduplication(String text) {
        return text.replaceAll("\\s+", " ")
                  .replaceAll("[\\p{P}\\p{S}]", "")
                  .toLowerCase()
                  .trim();
    }
    
    /**
     * 计算文本相似度
     */
    private double calculateTextSimilarity(String text1, String text2) {
        if (text1.equals(text2)) {
            return 1.0;
        }
        
        // 使用更严格的相似度计算
        Set<String> words1 = new HashSet<>(Arrays.asList(text1.split("\\s+")));
        Set<String> words2 = new HashSet<>(Arrays.asList(text2.split("\\s+")));
        
        Set<String> intersection = new HashSet<>(words1);
        intersection.retainAll(words2);
        
        Set<String> union = new HashSet<>(words1);
        union.addAll(words2);
        
        return union.isEmpty() ? 0.0 : (double) intersection.size() / union.size();
    }


    /**
     * 应用Reciprocal Rank Fusion算法
     */
    private List<ScoredContent> applyReciprocalRankFusion(List<ScoredContent> contents, 
                                                         Map<Query, Collection<List<Content>>> queryToContents) {
        Map<Content, Double> rrfScores = new HashMap<>();
        
        // 为每个查询计算RRF分数
        for (Map.Entry<Query, Collection<List<Content>>> entry : queryToContents.entrySet()) {
            Collection<List<Content>> contentLists = entry.getValue();
            
            for (List<Content> rankedContents : contentLists) {
                for (int rank = 0; rank < rankedContents.size(); rank++) {
                    Content content = rankedContents.get(rank);
                    double rrfScore = 1.0 / (rrfK + rank + 1);
                    rrfScores.merge(content, rrfScore, Double::sum);
                }
            }
        }
        
        // 更新分数并排序
        return contents.stream()
            .map(sc -> {
                double newScore = sc.score() * 0.5 + rrfScores.getOrDefault(sc.content(), 0.0) * 0.5;
                return new ScoredContent(sc.content(), newScore, sc.query(), sc.originalRank());
            })
            .sorted((a, b) -> Double.compare(b.score(), a.score()))
            .collect(Collectors.toList());
    }

    /**
     * 基于法律相关性重排序
     */
    private List<ScoredContent> reRankByLegalRelevance(List<ScoredContent> contents, Set<Query> queries) {
        String combinedQuery = queries.stream()
            .map(Query::text)
            .collect(Collectors.joining(" "));
        
        return contents.stream()
            .map(sc -> {
                double legalRelevance = calculateLegalRelevance(sc.content(), combinedQuery);
                double adjustedScore = sc.score() * 0.6 + legalRelevance * 0.4;
                return new ScoredContent(sc.content(), adjustedScore, sc.query(), sc.originalRank());
            })
            .sorted((a, b) -> Double.compare(b.score(), a.score()))
            .collect(Collectors.toList());
    }

    /**
     * 计算法律相关性
     */
    private double calculateLegalRelevance(Content content, String queryText) {
        String contentText = content.textSegment().text().toLowerCase();
        String lowerQuery = queryText.toLowerCase();
        
        double relevance = 0.0;
        
        // 1. 法律实体识别
        relevance += identifyLegalEntities(contentText, lowerQuery);
        
        // 2. 法律关系识别
        relevance += identifyLegalRelations(contentText, lowerQuery);
        
        // 3. 专业术语密度
        relevance += calculateLegalTermDensity(contentText);
        
        return Math.min(relevance, 1.0);
    }

    /**
     * 识别法律实体
     */
    private double identifyLegalEntities(String contentText, String queryText) {
        String[] legalEntities = {
            "当事人", "甲方", "乙方", "买方", "卖方", "承租人", "出租人",
            "委托人", "受托人", "债权人", "债务人", "保证人", "担保人"
        };
        
        double score = 0.0;
        for (String entity : legalEntities) {
            if (contentText.contains(entity) && queryText.contains(entity)) {
                score += 0.1;
            }
        }
        
        return score;
    }

    /**
     * 识别法律关系
     */
    private double identifyLegalRelations(String contentText, String queryText) {
        String[] legalRelations = {
            "合同关系", "债权债务", "侵权行为", "违约责任", "损害赔偿",
            "代理关系", "担保责任", "连带责任", "继承关系", "婚姻关系"
        };
        
        double score = 0.0;
        for (String relation : legalRelations) {
            if (contentText.contains(relation) || queryText.contains(relation)) {
                score += 0.05;
            }
        }
        
        return score;
    }

    /**
     * 计算法律术语密度
     */
    private double calculateLegalTermDensity(String text) {
        String[] words = text.split("\\s+");
        if (words.length == 0) return 0.0;
        
        int legalTermCount = 0;
        for (String word : words) {
            if (LEGAL_KEYWORD_WEIGHTS.containsKey(word)) {
                legalTermCount++;
            }
        }
        
        return (double) legalTermCount / words.length;
    }

    // ==================== 数据类 ====================

    /**
     * 评分内容记录
     */
    public record ScoredContent(
        Content content,
        double score,
        Query query,
        int originalRank
    ) {}
}
