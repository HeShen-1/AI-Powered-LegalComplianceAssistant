package com.river.LegalAssistant.service.advanced;

import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.rag.query.Query;
import dev.langchain4j.rag.query.transformer.QueryTransformer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.regex.Pattern;

/**
 * 法律领域专用查询转换器
 * 基于LangChain4j Advanced RAG框架实现
 * 
 * 功能：
 * 1. 法律术语标准化
 * 2. 生成相关子问题
 * 3. 查询扩展和重写
 * 4. 口语化问题转换为专业法律查询
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class LegalQueryTransformer implements QueryTransformer {

    @Qualifier("langchain4jChatModel")
    private final ChatModel chatModel;
    
    // 法律术语映射表
    private static final Map<String, String> LEGAL_TERM_MAPPINGS = Map.of(
        "赔偿", "损害赔偿",
        "合同", "合同协议",
        "违约", "违约责任",
        "责任", "法律责任",
        "权利", "合法权益",
        "义务", "法定义务",
        "纠纷", "法律争议",
        "起诉", "提起诉讼"
    );
    
    // 法律关键词模式
    private static final Pattern LEGAL_KEYWORDS_PATTERN = Pattern.compile(
        "法律|条款|合同|违约|责任|权利|义务|损害|赔偿|诉讼|仲裁|法规|条例|民法典|刑法|商法"
    );

    @Override
    public Collection<Query> transform(Query originalQuery) {
        log.info("转换法律查询: {}", originalQuery.text());
        
        List<Query> transformedQueries = new ArrayList<>();
        String originalText = originalQuery.text();
        
        try {
            // 1. 包含原始查询
            transformedQueries.add(originalQuery);
            
            // 2. 法律术语标准化查询
            String standardizedQuery = standardizeLegalTerms(originalText);
            if (!standardizedQuery.equals(originalText)) {
                transformedQueries.add(createQuery(standardizedQuery, originalQuery.metadata()));
                log.debug("标准化查询: {}", standardizedQuery);
            }
            
            // 3. 生成专业法律查询
            String professionalQuery = generateProfessionalQuery(originalText);
            if (professionalQuery != null && !containsSimilarQuery(transformedQueries, professionalQuery)) {
                transformedQueries.add(createQuery(professionalQuery, originalQuery.metadata()));
                log.debug("专业化查询: {}", professionalQuery);
            }
            
            // 4. 生成相关子问题
            List<String> subQuestions = generateSubQuestions(originalText);
            for (String subQuestion : subQuestions) {
                if (!containsSimilarQuery(transformedQueries, subQuestion)) {
                    transformedQueries.add(createQuery(subQuestion, originalQuery.metadata()));
                    log.debug("子问题: {}", subQuestion);
                }
            }
            
            // 5. 关键词提取查询
            String keywordQuery = extractKeywordQuery(originalText);
            if (keywordQuery != null && !containsSimilarQuery(transformedQueries, keywordQuery)) {
                transformedQueries.add(createQuery(keywordQuery, originalQuery.metadata()));
                log.debug("关键词查询: {}", keywordQuery);
            }
            
        } catch (Exception e) {
            log.error("查询转换失败，使用原始查询", e);
            // 如果转换失败，至少返回原始查询
            return List.of(originalQuery);
        }
        
        log.info("查询转换完成，原始1个查询扩展为{}个查询", transformedQueries.size());
        return transformedQueries;
    }

    /**
     * 标准化法律术语
     */
    private String standardizeLegalTerms(String query) {
        String result = query;
        
        // 应用法律术语映射
        for (Map.Entry<String, String> entry : LEGAL_TERM_MAPPINGS.entrySet()) {
            result = result.replace(entry.getKey(), entry.getValue());
        }
        
        // 移除口语化表达
        result = result
            .replace("怎么办", "如何处理")
            .replace("能不能", "是否可以")
            .replace("要是", "如果")
            .replace("咋样", "如何")
            .replace("搞", "处理");
            
        return result.trim();
    }

    /**
     * 生成专业法律查询
     */
    private String generateProfessionalQuery(String query) {
        try {
            String prompt = String.format("""
                请将以下用户问题转换为专业的法律查询语句，要求：
                1. 使用准确的法律术语
                2. 明确法律关系和争议焦点
                3. 保持简洁清晰
                4. 如果原问题已经很专业，返回'ALREADY_PROFESSIONAL'
                
                用户问题：%s
                
                专业法律查询：
                """, query);
            
            String response = chatModel.chat(prompt).trim();
            
            if ("ALREADY_PROFESSIONAL".equals(response) || response.length() > query.length() * 2) {
                return null;
            }
            
            return response;
            
        } catch (Exception e) {
            log.warn("生成专业查询失败: {}", e.getMessage());
            return null;
        }
    }

    /**
     * 生成相关子问题
     */
    private List<String> generateSubQuestions(String query) {
        try {
            String prompt = String.format("""
                基于以下法律问题，生成2-3个相关的具体子问题，用于更全面的检索：
                
                原问题：%s
                
                要求：
                1. 子问题应该更具体、更聚焦
                2. 涵盖不同的法律角度
                3. 每个子问题单独一行
                4. 不要添加序号或标点
                
                子问题：
                """, query);
            
            String response = chatModel.chat(prompt);
            return parseSubQuestions(response);
            
        } catch (Exception e) {
            log.warn("生成子问题失败: {}", e.getMessage());
            return Collections.emptyList();
        }
    }

    /**
     * 提取关键词查询
     */
    private String extractKeywordQuery(String query) {
        // 使用正则表达式提取法律关键词
        java.util.regex.Matcher matcher = LEGAL_KEYWORDS_PATTERN.matcher(query);
        Set<String> keywords = new HashSet<>();
        
        while (matcher.find()) {
            keywords.add(matcher.group());
        }
        
        // 添加重要的非法律词汇
        String[] importantWords = {"风险", "问题", "处理", "分析", "建议", "注意", "避免"};
        for (String word : importantWords) {
            if (query.contains(word)) {
                keywords.add(word);
            }
        }
        
        if (keywords.size() >= 2) {
            return String.join(" ", keywords);
        }
        
        return null;
    }

    /**
     * 解析子问题字符串
     */
    private List<String> parseSubQuestions(String response) {
        if (response == null || response.trim().isEmpty()) {
            return Collections.emptyList();
        }
        
        List<String> questions = new ArrayList<>();
        String[] lines = response.split("\n");
        
        for (String line : lines) {
            String trimmed = line.trim()
                .replaceAll("^[0-9]+[、.]\\s*", "") // 移除序号
                .replaceAll("^[-*]\\s*", "")        // 移除列表符号
                .trim();
                
            if (trimmed.length() > 5 && !trimmed.startsWith("子问题") && !trimmed.startsWith("问题")) {
                questions.add(trimmed);
            }
        }
        
        // 限制子问题数量
        return questions.size() > 3 ? questions.subList(0, 3) : questions;
    }

    /**
     * 检查是否包含相似的查询
     */
    private boolean containsSimilarQuery(List<Query> queries, String newQuery) {
        return queries.stream()
            .anyMatch(q -> calculateSimilarity(q.text(), newQuery) > 0.8);
    }

    /**
     * 计算字符串相似度（简单实现）
     */
    private double calculateSimilarity(String s1, String s2) {
        if (s1.equals(s2)) return 1.0;
        
        int maxLength = Math.max(s1.length(), s2.length());
        if (maxLength == 0) return 1.0;
        
        return (maxLength - levenshteinDistance(s1, s2)) / (double) maxLength;
    }

    /**
     * 计算编辑距离
     */
    private int levenshteinDistance(String s1, String s2) {
        int[][] dp = new int[s1.length() + 1][s2.length() + 1];
        
        for (int i = 0; i <= s1.length(); i++) {
            dp[i][0] = i;
        }
        for (int j = 0; j <= s2.length(); j++) {
            dp[0][j] = j;
        }
        
        for (int i = 1; i <= s1.length(); i++) {
            for (int j = 1; j <= s2.length(); j++) {
                int cost = s1.charAt(i - 1) == s2.charAt(j - 1) ? 0 : 1;
                dp[i][j] = Math.min(
                    Math.min(dp[i - 1][j] + 1, dp[i][j - 1] + 1),
                    dp[i - 1][j - 1] + cost
                );
            }
        }
        
        return dp[s1.length()][s2.length()];
    }

    /**
     * 创建查询对象
     */
    private Query createQuery(String text, Object metadata) {
        return Query.from(text);
    }
}
