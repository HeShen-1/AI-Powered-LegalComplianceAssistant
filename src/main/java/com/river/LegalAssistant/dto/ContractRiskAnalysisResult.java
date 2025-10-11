package com.river.LegalAssistant.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * 合同风险分析结果 DTO
 * 用于接收 AI 返回的结构化 JSON 数据
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ContractRiskAnalysisResult {
    
    /**
     * 整体风险等级
     */
    private String overallRiskLevel;
    
    /**
     * 核心风险提示
     */
    private List<String> coreRiskAlerts;
    
    /**
     * 优先改进建议
     */
    private List<String> priorityRecommendations;
    
    /**
     * 风险维度分析
     */
    private List<RiskDimensionAnalysis> riskDimensions;
    
    /**
     * 合规评分（0-100）
     */
    private Integer complianceScore;
    
    /**
     * 原始分析文本（用于兼容性）
     */
    private String originalAnalysis;
    
    /**
     * 获取风险条款列表（向后兼容）
     * @deprecated 请使用 getRiskDimensions()
     */
    @Deprecated
    public List<RiskClauseInfo> getRiskClauses() {
        if (riskDimensions == null) {
            return new ArrayList<>();
        }
        
        return riskDimensions.stream()
                .map(dimension -> RiskClauseInfo.builder()
                        .riskType(dimension.getDimensionName())
                        .riskLevel(dimension.getRiskLevel())
                        .riskDescription(dimension.getDescription())
                        .suggestion(dimension.getImprovements() != null && !dimension.getImprovements().isEmpty() 
                                ? String.join("; ", dimension.getImprovements()) : "")
                        .build())
                .toList();
    }
    
    /**
     * 设置风险条款列表（向后兼容）
     * @deprecated 请使用 setRiskDimensions()
     */
    @Deprecated
    public void setRiskClauses(List<RiskClauseInfo> riskClauses) {
        if (riskClauses == null) {
            this.riskDimensions = null;
            return;
        }
        
        this.riskDimensions = riskClauses.stream()
                .map(riskClause -> RiskDimensionAnalysis.builder()
                        .dimensionName(riskClause.getRiskType())
                        .riskLevel(riskClause.getRiskLevel())
                        .description(riskClause.getRiskDescription())
                        .improvements(riskClause.getSuggestion() != null && !riskClause.getSuggestion().isEmpty()
                                ? List.of(riskClause.getSuggestion()) : new ArrayList<>())
                        .build())
                .toList();
    }
    
    /**
     * 风险维度分析
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RiskDimensionAnalysis {
        
        /**
         * 风险维度名称
         */
        private String dimensionName;
        
        /**
         * 风险等级：HIGH/MEDIUM/LOW
         */
        private String riskLevel;
        
        /**
         * 具体风险点
         */
        private List<String> riskPoints;
        
        /**
         * 风险描述
         */
        private String description;
        
        /**
         * 法律依据
         */
        private String legalBasis;
        
        /**
         * 改进建议
         */
        private List<String> improvements;
    }
    
    /**
     * 风险条款信息类（向后兼容）
     * @deprecated 请使用 RiskDimensionAnalysis
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @Deprecated
    public static class RiskClauseInfo {
        
        /**
         * 风险类型
         */
        private String riskType;
        
        /**
         * 风险等级：HIGH/MEDIUM/LOW
         */
        private String riskLevel;
        
        /**
         * 风险描述
         */
        private String riskDescription;
        
        /**
         * 建议
         */
        private String suggestion;
    }
}
