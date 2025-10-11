package com.river.LegalAssistant.service;

import com.river.LegalAssistant.dto.QueryIntent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * 混合检索服务
 * 根据查询意图智能选择检索策略，提升精确条款检索的准确率
 * 
 * 核心策略：
 * 1. 精确条款查询 → 元数据精确匹配 + 向量检索兜底
 * 2. 章节级查询 → 元数据过滤 + 向量检索
 * 3. 语义查询 → 纯向量语义检索
 * 4. 复杂查询 → 混合策略（未来扩展）
 * 
 * @author LegalAssistant Team
 * @since 2025-10-11
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class HybridSearchService {
    
    private final VectorStoreService vectorStoreService;
    private final QueryAnalyzer queryAnalyzer;
    
    /**
     * 智能混合检索
     * 
     * @param userQuery 用户查询
     * @param maxResults 最大结果数
     * @return 检索结果列表
     */
    public List<Document> search(String userQuery, int maxResults) {
        // 1. 分析查询意图
        QueryIntent intent = queryAnalyzer.analyze(userQuery);
        log.info("🔍 查询意图: {}", intent.getQueryType());
        
        // 2. 根据意图选择检索策略
        List<Document> results = switch (intent.getQueryType()) {
            case PRECISE_ARTICLE -> preciseArticleSearch(intent, maxResults);
            case CHAPTER_LEVEL -> chapterLevelSearch(intent, maxResults);
            case SEMANTIC -> semanticSearch(intent, maxResults);
            case COMPLEX -> complexSearch(intent, maxResults);
        };
        
        log.info("✅ 混合检索完成，返回 {} 个结果", results.size());
        return results;
    }
    
    /**
     * 精确条款检索（核心方法）
     * 
     * 策略：
     * 1. 优先使用元数据精确匹配（激活已有的searchByArticleNumber方法）
     * 2. 失败则降级到向量检索 + 元数据加权重排序
     * 
     * @param intent 查询意图
     * @param maxResults 最大结果数
     * @return 检索结果
     */
    private List<Document> preciseArticleSearch(QueryIntent intent, int maxResults) {
        log.info("🎯 执行精确条款检索: 法律={}, 条款={}", 
            intent.getLawName(), intent.getArticleNumber());
        
        // 策略1：元数据精确匹配（如果有完整信息）
        if (intent.hasExactMatchInfo()) {
            List<Document> exactMatches = vectorStoreService.searchByArticleNumber(
                intent.getLawName(),
                intent.getArticleNumber()
            );
            
            if (!exactMatches.isEmpty()) {
                log.info("✅ 元数据精确匹配成功，找到 {} 个结果", exactMatches.size());
                return exactMatches.stream()
                    .limit(maxResults)
                    .collect(Collectors.toList());
            } else {
                log.warn("⚠️ 元数据精确匹配失败，可能原因：");
                log.warn("  - 法律名称不匹配：存储时为'{}', 查询时为'{}'", "?", intent.getLawName());
                log.warn("  - 条款编号不匹配：存储时为'{}', 查询时为'{}'", "?", intent.getArticleNumber());
            }
        }
        
        // 策略2：降级到向量检索 + 精确度加权重排序
        log.info("⚡ 降级到混合检索（向量 + 元数据加权）");
        List<Document> vectorResults = vectorStoreService.searchSimilar(
            intent.getOriginalQuery(),
            maxResults * 5  // 检索更多候选，用于重排序
        );
        
        if (vectorResults.isEmpty()) {
            log.warn("❌ 向量检索也未找到结果");
            return List.of();
        }
        
        // 策略3：计算精确度得分并重排序
        log.debug("📊 对 {} 个候选结果进行精确度重排序", vectorResults.size());
        List<Document> rankedResults = vectorResults.stream()
            .peek(doc -> {
                double precisionScore = calculatePrecisionScore(doc, intent);
                doc.getMetadata().put("precision_score", precisionScore);
                
                if (log.isDebugEnabled()) {
                    log.debug("  - 文档[law={}, article={}] 得分={}",
                        doc.getMetadata().get("law_name"),
                        doc.getMetadata().get("article_number"),
                        precisionScore);
                }
            })
            .sorted(Comparator.comparingDouble(
                doc -> -((double) doc.getMetadata().getOrDefault("precision_score", 0.0))
            ))
            .limit(maxResults)
            .collect(Collectors.toList());
        
        // 输出Top结果信息
        if (!rankedResults.isEmpty() && log.isInfoEnabled()) {
            Document topResult = rankedResults.get(0);
            log.info("🏆 Top结果: law={}, article={}, score={}",
                topResult.getMetadata().get("law_name"),
                topResult.getMetadata().get("article_number"),
                topResult.getMetadata().get("precision_score"));
        }
        
        return rankedResults;
    }
    
    /**
     * 计算精确度得分
     * 
     * 评分规则：
     * - 条款编号完全匹配：+100分（最高权重）
     * - 法律名称匹配：+50分
     * - 章节匹配：+20分
     * - 节匹配：+10分
     * 
     * @param doc 文档
     * @param intent 查询意图
     * @return 精确度得分
     */
    private double calculatePrecisionScore(Document doc, QueryIntent intent) {
        double score = 0.0;
        
        // 1. 条款编号完全匹配 +100分（最关键）
        Object docArticle = doc.getMetadata().get("article_number");
        if (docArticle != null && intent.getArticleNumber() != null) {
            if (Objects.equals(docArticle.toString(), intent.getArticleNumber())) {
                score += 100.0;
                log.trace("    ✓ 条款编号匹配: +100");
            }
        }
        
        // 2. 法律名称匹配 +50分
        Object docLaw = doc.getMetadata().get("law_name");
        if (intent.getLawName() != null && docLaw != null) {
            String docLawStr = docLaw.toString();
            String intentLawStr = intent.getLawName();
            
            // 完全匹配
            if (Objects.equals(docLawStr, intentLawStr)) {
                score += 50.0;
                log.trace("    ✓ 法律名称完全匹配: +50");
            }
            // 包含匹配（模糊匹配）
            else if (docLawStr.contains(intentLawStr) || intentLawStr.contains(docLawStr)) {
                score += 25.0;
                log.trace("    ✓ 法律名称部分匹配: +25");
            }
        }
        
        // 3. 章节匹配 +20分
        Object docChapter = doc.getMetadata().get("chapter");
        if (intent.getChapter() != null && docChapter != null &&
            Objects.equals(docChapter.toString(), intent.getChapter())) {
            score += 20.0;
            log.trace("    ✓ 章节匹配: +20");
        }
        
        // 4. 节匹配 +10分
        Object docSection = doc.getMetadata().get("section");
        if (intent.getSection() != null && docSection != null &&
            Objects.equals(docSection.toString(), intent.getSection())) {
            score += 10.0;
            log.trace("    ✓ 节匹配: +10");
        }
        
        return score;
    }
    
    /**
     * 章节级别检索
     * 使用元数据过滤 + 向量检索
     */
    private List<Document> chapterLevelSearch(QueryIntent intent, int maxResults) {
        log.info("📖 执行章节级别检索: 章节={}, 节={}", intent.getChapter(), intent.getSection());
        
        // 先进行向量检索
        List<Document> results = vectorStoreService.searchSimilar(
            intent.getOriginalQuery(),
            maxResults * 3  // 检索更多结果用于过滤
        );
        
        // 按章节/节过滤
        List<Document> filteredResults = results.stream()
            .filter(doc -> {
                // 章节过滤
                if (intent.getChapter() != null) {
                    Object chapter = doc.getMetadata().get("chapter");
                    if (chapter == null || !chapter.toString().equals(intent.getChapter())) {
                        return false;
                    }
                }
                
                // 节过滤
                if (intent.getSection() != null) {
                    Object section = doc.getMetadata().get("section");
                    if (section == null || !section.toString().equals(intent.getSection())) {
                        return false;
                    }
                }
                
                return true;
            })
            .limit(maxResults)
            .collect(Collectors.toList());
        
        log.info("✅ 章节级检索完成，过滤后 {} 个结果", filteredResults.size());
        return filteredResults;
    }
    
    /**
     * 语义检索（默认行为，保持向后兼容）
     */
    private List<Document> semanticSearch(QueryIntent intent, int maxResults) {
        log.info("🔍 执行语义检索（纯向量）");
        return vectorStoreService.searchSimilar(intent.getOriginalQuery(), maxResults);
    }
    
    /**
     * 复杂查询（未来扩展）
     * 当前版本降级到语义检索
     */
    private List<Document> complexSearch(QueryIntent intent, int maxResults) {
        log.info("🧩 执行复杂查询（当前版本降级到语义检索）");
        // TODO: 未来可以实现多条款并行检索、跨法律查询等高级功能
        return semanticSearch(intent, maxResults);
    }
}

