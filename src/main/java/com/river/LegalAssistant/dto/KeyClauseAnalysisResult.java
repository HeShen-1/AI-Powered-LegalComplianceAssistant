package com.river.LegalAssistant.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * 关键条款分析结果
 * 用于存储合同关键条款的结构化分析数据
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class KeyClauseAnalysisResult {
    
    /**
     * 关键条款列表
     */
    @JsonProperty("keyClauses")
    @Builder.Default
    private List<KeyClause> keyClauses = new ArrayList<>();
    
    /**
     * 原始分析文本（可选）
     */
    @JsonProperty("originalAnalysis")
    private String originalAnalysis;
    
    /**
     * 条款完整性评分（0-100）
     */
    @JsonProperty("completenessScore")
    private Integer completenessScore;
    
    /**
     * 总体评价
     */
    @JsonProperty("overallAssessment")
    private String overallAssessment;
    
    /**
     * 单个关键条款信息
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class KeyClause {
        /**
         * 条款标题/类型
         * 例如: "合同标的", "履行期限", "价款支付", "违约责任"
         */
        @JsonProperty("title")
        private String title;
        
        /**
         * 条款内容
         */
        @JsonProperty("content")
        private String content;
        
        /**
         * 条款分析
         */
        @JsonProperty("analysis")
        private String analysis;
        
        /**
         * 重要性级别
         * HIGH - 高重要性
         * MEDIUM - 中重要性
         * LOW - 低重要性
         */
        @JsonProperty("importance")
        private String importance;
        
        /**
         * 章节位置（可选）
         */
        @JsonProperty("section")
        private String section;
        
        /**
         * 是否完整
         */
        @JsonProperty("isComplete")
        private Boolean isComplete;
        
        /**
         * 改进建议
         */
        @JsonProperty("suggestion")
        private String suggestion;
    }
}

