package com.river.LegalAssistant.service;

import com.river.LegalAssistant.dto.QueryIntent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 查询分析器服务
 * 从用户查询中提取结构化信息，识别查询意图
 * 
 * 核心功能：
 * 1. 提取法律名称
 * 2. 提取条款编号（支持多种格式）
 * 3. 提取章节/节信息
 * 4. 判断查询类型
 * 
 * @author LegalAssistant Team
 * @since 2025-10-11
 */
@Service
@Slf4j
public class QueryAnalyzer {
    
    // 法律名称模式（支持带书名号和不带书名号）
    private static final Pattern LAW_NAME_PATTERN = Pattern.compile(
        "《?([^《》]+?(?:法|条例|规定|办法|准则|细则))》?"
    );
    
    // 条款编号模式（支持多种格式）
    private static final Pattern[] ARTICLE_PATTERNS = {
        // 中文格式：第一条、第三十条、第一千一百九十八条
        Pattern.compile("第([一二三四五六七八九十百千万零]+)条"),
        // 数字格式：第1条、第30条、第1198条
        Pattern.compile("第(\\d+)条"),
        // 简化格式：1条、30条
        Pattern.compile("(\\d+)条"),
        // 款项格式
        Pattern.compile("第([一二三四五六七八九十百千万零]+)款"),
        Pattern.compile("第(\\d+)款")
    };
    
    // 章节模式
    private static final Pattern CHAPTER_PATTERN = Pattern.compile(
        "第([一二三四五六七八九十百千万零\\d]+)章"
    );
    
    // 节模式
    private static final Pattern SECTION_PATTERN = Pattern.compile(
        "第([一二三四五六七八九十百千万零\\d]+)节"
    );
    
    /**
     * 分析用户查询，提取结构化信息
     * 
     * @param userQuery 用户原始查询
     * @return 查询意图对象
     */
    public QueryIntent analyze(String userQuery) {
        if (userQuery == null || userQuery.trim().isEmpty()) {
            log.warn("用户查询为空");
            return QueryIntent.builder()
                .originalQuery(userQuery)
                .queryType(QueryIntent.QueryType.SEMANTIC)
                .build();
        }
        
        log.debug("分析用户查询: {}", userQuery);
        
        QueryIntent.QueryIntentBuilder builder = QueryIntent.builder()
            .originalQuery(userQuery);
        
        // 1. 提取法律名称
        String lawName = extractLawName(userQuery);
        builder.lawName(lawName);
        if (lawName != null) {
            log.debug("✓ 识别法律名称: {}", lawName);
        }
        
        // 2. 提取条款编号
        String articleNumber = extractArticleNumber(userQuery);
        builder.articleNumber(articleNumber);
        if (articleNumber != null) {
            log.debug("✓ 识别条款编号: {}", articleNumber);
        }
        
        // 3. 提取章节
        String chapter = extractChapter(userQuery);
        builder.chapter(chapter);
        if (chapter != null) {
            log.debug("✓ 识别章节: {}", chapter);
        }
        
        // 4. 提取节
        String section = extractSection(userQuery);
        builder.section(section);
        if (section != null) {
            log.debug("✓ 识别节: {}", section);
        }
        
        // 5. 判断查询类型
        QueryIntent.QueryType queryType = determineQueryType(
            articleNumber, chapter, section, userQuery
        );
        builder.queryType(queryType);
        log.info("查询类型识别: {} (法律:{}, 条款:{})", 
            queryType, lawName, articleNumber);
        
        return builder.build();
    }
    
    /**
     * 提取法律名称
     * 支持格式：《环境保护法》、环境保护法、《中华人民共和国民法典》等
     */
    private String extractLawName(String query) {
        Matcher matcher = LAW_NAME_PATTERN.matcher(query);
        if (matcher.find()) {
            String lawName = matcher.group(1);
            // 去除书名号
            lawName = lawName.replaceAll("[《》]", "");
            // 简化法律名称：去除"中华人民共和国"前缀
            lawName = lawName.replaceFirst("^中华人民共和国", "");
            return lawName;
        }
        return null;
    }
    
    /**
     * 提取条款编号（支持多种格式）
     * 支持：第一条、第30条、30条、第三十条等
     */
    private String extractArticleNumber(String query) {
        for (int i = 0; i < ARTICLE_PATTERNS.length; i++) {
            Pattern pattern = ARTICLE_PATTERNS[i];
            Matcher matcher = pattern.matcher(query);
            if (matcher.find()) {
                String rawNumber = matcher.group(1);
                // 标准化为"第X条"格式
                return normalizeArticleNumber(rawNumber, i >= 2);  // i>=2表示是简化格式
            }
        }
        return null;
    }
    
    /**
     * 提取章节
     */
    private String extractChapter(String query) {
        Matcher matcher = CHAPTER_PATTERN.matcher(query);
        if (matcher.find()) {
            String chapterNum = matcher.group(1);
            return "第" + chapterNum + "章";
        }
        return null;
    }
    
    /**
     * 提取节
     */
    private String extractSection(String query) {
        Matcher matcher = SECTION_PATTERN.matcher(query);
        if (matcher.find()) {
            String sectionNum = matcher.group(1);
            return "第" + sectionNum + "节";
        }
        return null;
    }
    
    /**
     * 判断查询类型
     */
    private QueryIntent.QueryType determineQueryType(
            String articleNumber, String chapter, String section, String query) {
        
        // 1. 如果明确指定了条款编号，则为精确条款查询
        if (articleNumber != null) {
            return QueryIntent.QueryType.PRECISE_ARTICLE;
        }
        
        // 2. 如果指定了章节，则为章节级查询
        if (chapter != null || section != null) {
            return QueryIntent.QueryType.CHAPTER_LEVEL;
        }
        
        // 3. 检查是否是复杂查询（包含"和"、"以及"等连接词）
        if (query.matches(".*([和及以及或者还有]|、).*第.*")) {
            return QueryIntent.QueryType.COMPLEX;
        }
        
        // 4. 默认为语义查询
        return QueryIntent.QueryType.SEMANTIC;
    }
    
    /**
     * 标准化条款编号
     * 将各种格式统一为"第X条"格式，并转换为中文数字
     * 
     * @param rawNumber 原始编号
     * @param isSimplified 是否是简化格式（不带"第"）
     * @return 标准化后的条款编号
     */
    private String normalizeArticleNumber(String rawNumber, boolean isSimplified) {
        // 如果是数字，转换为中文
        if (rawNumber.matches("\\d+")) {
            String chineseNumber = convertToChineseNumber(Integer.parseInt(rawNumber));
            return "第" + chineseNumber + "条";
        }
        
        // 如果已经是中文，确保格式正确
        String result = rawNumber;
        if (isSimplified || !result.startsWith("第")) {
            result = "第" + result;
        }
        if (!result.endsWith("条")) {
            result = result + "条";
        }
        
        return result;
    }
    
    /**
     * 阿拉伯数字转中文数字
     * 支持1-9999的转换
     * 
     * @param number 阿拉伯数字
     * @return 中文数字
     */
    private String convertToChineseNumber(int number) {
        if (number < 0 || number > 9999) {
            log.warn("数字超出支持范围: {}", number);
            return String.valueOf(number);
        }
        
        if (number == 0) return "零";
        
        String[] digits = {"零", "一", "二", "三", "四", "五", "六", "七", "八", "九"};
        String[] units = {"", "十", "百", "千"};
        
        StringBuilder result = new StringBuilder();
        String numStr = String.valueOf(number);
        int len = numStr.length();
        
        for (int i = 0; i < len; i++) {
            int digit = numStr.charAt(i) - '0';
            int unitIndex = len - i - 1;
            
            if (digit == 0) {
                // 处理零：如果结果还是空的，或者最后一位已经是零，则不添加
                if (result.length() > 0 && !result.toString().endsWith("零")) {
                    result.append("零");
                }
            } else {
                // 特殊处理：10-19之间的数字，"一十"简化为"十"
                if (digit == 1 && unitIndex == 1 && i == 0) {
                    result.append(units[unitIndex]);
                } else {
                    result.append(digits[digit]).append(units[unitIndex]);
                }
            }
        }
        
        // 去除末尾的零
        String resultStr = result.toString();
        if (resultStr.endsWith("零")) {
            resultStr = resultStr.substring(0, resultStr.length() - 1);
        }
        
        return resultStr;
    }
}

