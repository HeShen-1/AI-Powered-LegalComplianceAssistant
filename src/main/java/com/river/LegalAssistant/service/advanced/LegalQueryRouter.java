package com.river.LegalAssistant.service.advanced;

import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.rag.content.retriever.ContentRetriever;
import dev.langchain4j.rag.query.Query;
import dev.langchain4j.rag.query.router.QueryRouter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import java.util.*;
import java.util.regex.Pattern;

/**
 * 法律查询智能路由器
 * 基于LangChain4j Advanced RAG框架实现
 * 
 * 功能：
 * 1. 分析查询类型和意图
 * 2. 选择最合适的内容检索器组合
 * 3. 支持多种法律查询场景
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class LegalQueryRouter implements QueryRouter {

    @Qualifier("langchain4jChatModel")
    private final ChatModel routingChatModel;
    private final LegalContentRetriever contentRetriever;
    private final Map<String, ContentRetriever> retrievers = new HashMap<>();
    
    // 查询类型模式
    private static final Map<String, Pattern> QUERY_PATTERNS = Map.of(
        "CONTRACT_ANALYSIS", Pattern.compile("合同|协议|条款|签订|履行|违约|解除", Pattern.CASE_INSENSITIVE),
        "LAW_INTERPRETATION", Pattern.compile("法律|法规|条例|民法典|刑法|规定|解释", Pattern.CASE_INSENSITIVE),
        "COMPLIANCE_CHECK", Pattern.compile("合规|违规|规范|标准|要求|审查", Pattern.CASE_INSENSITIVE),
        "DISPUTE_RESOLUTION", Pattern.compile("纠纷|争议|仲裁|诉讼|调解|起诉", Pattern.CASE_INSENSITIVE),
        "RISK_ASSESSMENT", Pattern.compile("风险|问题|隐患|注意|避免|防范", Pattern.CASE_INSENSITIVE),
        "LEGAL_ADVICE", Pattern.compile("建议|意见|咨询|应该|怎么办|如何", Pattern.CASE_INSENSITIVE)
    );
    
    // 复杂度关键词
    private static final Set<String> COMPLEX_KEYWORDS = Set.of(
        "复杂", "多方", "跨境", "国际", "重大", "疑难", "争议", "仲裁", "上诉"
    );
    
    // 紧急度关键词
    private static final Set<String> URGENT_KEYWORDS = Set.of(
        "紧急", "立即", "马上", "尽快", "急", "时限", "截止"
    );

    @PostConstruct
    public void initializeRouters() {
        log.info("初始化法律查询路由器...");
        
        // 使用注入的LegalContentRetriever作为主要检索器
        // LegalContentRetriever内部已经实现了多源检索逻辑
        retrievers.put("general", contentRetriever);
        retrievers.put("contract", contentRetriever);
        retrievers.put("law", contentRetriever);
        retrievers.put("case", contentRetriever);
        retrievers.put("compliance", contentRetriever);
        retrievers.put("web", contentRetriever);
        
        log.info("法律查询路由器初始化完成，配置 {} 个检索器", retrievers.size());
    }

    @Override
    public Collection<ContentRetriever> route(Query query) {
        log.info("路由法律查询: {}", query.text());
        
        try {
            // 1. 分析查询类型
            QueryAnalysis analysis = analyzeQuery(query);
            log.debug("查询分析结果: {}", analysis);
            
            // 2. 选择检索器组合
            List<ContentRetriever> selectedRetrievers = selectRetrievers(analysis);
            log.info("为查询类型 {} 选择了 {} 个检索器", analysis.primaryType(), selectedRetrievers.size());
            
            return selectedRetrievers;
            
        } catch (Exception e) {
            log.error("查询路由失败，使用默认检索器", e);
            return List.of(retrievers.get("general"));
        }
    }

    /**
     * 分析查询类型和特征
     */
    private QueryAnalysis analyzeQuery(Query query) {
        String queryText = query.text();
        
        // 1. 基于模式匹配的初步分类
        String patternBasedType = classifyByPattern(queryText);
        
        // 2. 使用AI模型进行精确分类
        String aiBasedType = classifyByAI(queryText);
        
        // 3. 确定最终类型（AI分类优先，模式分类作为备用）
        String primaryType = aiBasedType != null ? aiBasedType : patternBasedType;
        
        // 4. 分析查询特征
        QueryFeatures features = analyzeQueryFeatures(queryText);
        
        return new QueryAnalysis(primaryType, patternBasedType, features);
    }

    /**
     * 基于模式匹配分类查询
     */
    private String classifyByPattern(String queryText) {
        for (Map.Entry<String, Pattern> entry : QUERY_PATTERNS.entrySet()) {
            if (entry.getValue().matcher(queryText).find()) {
                return entry.getKey();
            }
        }
        return "GENERAL";
    }

    /**
     * 使用AI模型分类查询
     */
    private String classifyByAI(String queryText) {
        try {
            String prompt = String.format("""
                请分析以下法律问题的类型，从下列选项中选择最合适的一个：
                
                选项：
                - CONTRACT_ANALYSIS: 合同分析相关
                - LAW_INTERPRETATION: 法律条文解释
                - COMPLIANCE_CHECK: 合规性检查
                - DISPUTE_RESOLUTION: 纠纷解决
                - RISK_ASSESSMENT: 风险评估
                - LEGAL_ADVICE: 一般法律咨询
                - GENERAL: 通用查询
                
                问题：%s
                
                只返回类型标识（如：CONTRACT_ANALYSIS）：
                """, queryText);
            
            String response = routingChatModel.chat(prompt).trim().toUpperCase();
            
            // 验证响应是否为有效类型
            if (QUERY_PATTERNS.containsKey(response) || "GENERAL".equals(response)) {
                return response;
            }
            
            log.warn("AI分类返回无效类型: {}", response);
            return null;
            
        } catch (Exception e) {
            log.warn("AI查询分类失败: {}", e.getMessage());
            return null;
        }
    }

    /**
     * 分析查询特征
     */
    private QueryFeatures analyzeQueryFeatures(String queryText) {
        String lowerText = queryText.toLowerCase();
        
        // 复杂度分析
        boolean isComplex = COMPLEX_KEYWORDS.stream()
            .anyMatch(lowerText::contains) || queryText.length() > 100;
        
        // 紧急度分析
        boolean isUrgent = URGENT_KEYWORDS.stream()
            .anyMatch(lowerText::contains);
        
        // 时效性分析
        boolean needsLatestInfo = lowerText.contains("最新") || 
                                 lowerText.contains("2024") || 
                                 lowerText.contains("2025") ||
                                 lowerText.contains("新");
        
        // 专业度分析
        boolean isProfessional = containsLegalTerms(queryText);
        
        return new QueryFeatures(isComplex, isUrgent, needsLatestInfo, isProfessional);
    }

    /**
     * 检查是否包含专业法律术语
     */
    private boolean containsLegalTerms(String text) {
        String[] legalTerms = {
            "民法典", "刑法", "商法", "行政法", "诉讼法", "仲裁法",
            "违约责任", "侵权责任", "损害赔偿", "不可抗力", "代理",
            "所有权", "用益物权", "担保物权", "债权", "人格权"
        };
        
        String lowerText = text.toLowerCase();
        return Arrays.stream(legalTerms)
            .anyMatch(term -> lowerText.contains(term.toLowerCase()));
    }

    /**
     * 根据分析结果选择检索器
     */
    private List<ContentRetriever> selectRetrievers(QueryAnalysis analysis) {
        List<ContentRetriever> selected = new ArrayList<>();
        String primaryType = analysis.primaryType();
        QueryFeatures features = analysis.features();
        
        // 根据主要类型选择核心检索器
        switch (primaryType) {
            case "CONTRACT_ANALYSIS" -> {
                selected.add(retrievers.get("contract"));
                selected.add(retrievers.get("general"));
                if (features.isComplex()) {
                    selected.add(retrievers.get("case"));
                }
            }
            case "LAW_INTERPRETATION" -> {
                selected.add(retrievers.get("law"));
                selected.add(retrievers.get("general"));
                if (features.needsLatestInfo()) {
                    selected.add(retrievers.get("web"));
                }
            }
            case "COMPLIANCE_CHECK" -> {
                selected.add(retrievers.get("compliance"));
                selected.add(retrievers.get("law"));
                if (features.needsLatestInfo()) {
                    selected.add(retrievers.get("web"));
                }
            }
            case "DISPUTE_RESOLUTION" -> {
                selected.add(retrievers.get("case"));
                selected.add(retrievers.get("general"));
                selected.add(retrievers.get("law"));
            }
            case "RISK_ASSESSMENT" -> {
                selected.add(retrievers.get("general"));
                selected.add(retrievers.get("case"));
                if (features.isComplex()) {
                    selected.add(retrievers.get("compliance"));
                }
            }
            default -> selected.add(retrievers.get("general"));
        }
        
        // 根据特征添加额外检索器
        if (features.needsLatestInfo() && !selected.contains(retrievers.get("web"))) {
            selected.add(retrievers.get("web"));
        }
        
        if (features.isUrgent() && selected.size() > 2) {
            // 紧急查询限制检索器数量以提高速度
            selected = selected.subList(0, 2);
        }
        
        return selected;
    }


    // ==================== 数据类 ====================

    /**
     * 查询分析结果
     */
    public record QueryAnalysis(
        String primaryType,
        String patternType,
        QueryFeatures features
    ) {}

    /**
     * 查询特征
     */
    public record QueryFeatures(
        boolean isComplex,
        boolean isUrgent,
        boolean needsLatestInfo,
        boolean isProfessional
    ) {}
}
