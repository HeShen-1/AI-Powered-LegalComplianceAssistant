package com.river.LegalAssistant.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 查询意图数据模型
 * 用于表示从用户查询中提取的结构化信息
 * 
 * @author LegalAssistant Team
 * @since 2025-10-11
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class QueryIntent {
    
    /**
     * 原始查询
     */
    private String originalQuery;
    
    /**
     * 法律名称（如"环境保护法"、"民法典"）
     */
    private String lawName;
    
    /**
     * 条款编号（如"第三十条"、"第一千一百九十八条"）
     */
    private String articleNumber;
    
    /**
     * 章节（如"第三章"）
     */
    private String chapter;
    
    /**
     * 节（如"第一节"）
     */
    private String section;
    
    /**
     * 查询类型
     */
    private QueryType queryType;
    
    /**
     * 查询类型枚举
     */
    public enum QueryType {
        /**
         * 精确条款查询：明确指定了条款编号
         * 示例："环境保护法第30条"
         */
        PRECISE_ARTICLE,
        
        /**
         * 章节级别查询：指定了章节范围
         * 示例："民法典第三章"
         */
        CHAPTER_LEVEL,
        
        /**
         * 语义查询：概念性或描述性查询
         * 示例："什么是违约责任"
         */
        SEMANTIC,
        
        /**
         * 复杂查询：涉及多个条款或跨法律
         * 示例："民法典第1198条和环境保护法第30条"
         */
        COMPLEX
    }
    
    /**
     * 是否是精确查询（需要使用元数据过滤）
     */
    public boolean isPreciseQuery() {
        return queryType == QueryType.PRECISE_ARTICLE || 
               queryType == QueryType.CHAPTER_LEVEL;
    }
    
    /**
     * 是否包含足够的精确匹配信息
     */
    public boolean hasExactMatchInfo() {
        return lawName != null && articleNumber != null;
    }
}

