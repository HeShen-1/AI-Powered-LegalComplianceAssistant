package com.river.LegalAssistant.dto.report;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * AI深度分析数据模型
 * 用于结构化接收AI生成的深度分析JSON数据
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DeepAnalysisDto {
    
    /**
     * 法律性质分析
     */
    @JsonProperty("legalNature")
    private LegalNatureAnalysis legalNature;
    
    /**
     * 关键条款解读列表
     */
    @JsonProperty("keyClauses")
    private List<KeyClauseAnalysis> keyClauses;
    
    /**
     * 法律风险深度评估列表
     */
    @JsonProperty("riskAssessments")
    private List<RiskAssessment> riskAssessments;
    
    /**
     * 合规性检查
     */
    @JsonProperty("complianceCheck")
    private ComplianceCheck complianceCheck;
    
    /**
     * 商业影响分析
     */
    @JsonProperty("businessImpact")
    private BusinessImpactAnalysis businessImpact;
    
    /**
     * 法律性质分析子模型
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class LegalNatureAnalysis {
        /**
         * 合同类型（如买卖、租赁、服务等）
         */
        @JsonProperty("contractType")
        private String contractType;
        
        /**
         * 适用法律法规条款
         */
        @JsonProperty("governingLaws")
        private String governingLaws;
        
        /**
         * 法律关系认定
         */
        @JsonProperty("legalRelationship")
        private String legalRelationship;
    }
    
    /**
     * 关键条款分析子模型
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class KeyClauseAnalysis {
        /**
         * 条款名称
         */
        @JsonProperty("clauseName")
        private String clauseName;
        
        /**
         * 条款解读
         */
        @JsonProperty("interpretation")
        private String interpretation;
        
        /**
         * 风险说明
         */
        @JsonProperty("risk")
        private String risk;
    }
    
    /**
     * 风险评估子模型
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RiskAssessment {
        /**
         * 风险类别（法律合规/履约/财务/诉讼/监管）
         */
        @JsonProperty("riskCategory")
        private String riskCategory;
        
        /**
         * 等级（高/中/低）
         */
        @JsonProperty("level")
        private String level;
        
        /**
         * 风险描述
         */
        @JsonProperty("description")
        private String description;
        
        /**
         * 防范措施
         */
        @JsonProperty("prevention")
        private String prevention;
    }
    
    /**
     * 合规性检查子模型
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ComplianceCheck {
        /**
         * 适用法规
         */
        @JsonProperty("regulation")
        private String regulation;
        
        /**
         * 符合性评估
         */
        @JsonProperty("conformity")
        private String conformity;
        
        /**
         * 合规差距
         */
        @JsonProperty("gaps")
        private String gaps;
    }
    
    /**
     * 商业影响分析子模型
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class BusinessImpactAnalysis {
        /**
         * 受影响方
         */
        @JsonProperty("party")
        private String party;
        
        /**
         * 影响描述
         */
        @JsonProperty("impact")
        private String impact;
        
        /**
         * 财务影响
         */
        @JsonProperty("financialImpact")
        private String financialImpact;
    }
}

