package com.river.LegalAssistant.dto;

import com.river.LegalAssistant.entity.ContractReview;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 合同审查结果DTO
 * 用于返回完整的合同审查结果
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "合同审查结果详情")
public class ContractReviewResultDto {

    @Schema(description = "审查ID", example = "123")
    private Long id;

    @Schema(description = "原始文件名", example = "服务合同.docx")
    private String originalFilename;

    @Schema(description = "审查状态", example = "COMPLETED")
    private String reviewStatus;

    @Schema(description = "风险等级", example = "MEDIUM")
    private String riskLevel;

    @Schema(description = "风险总数", example = "5")
    private Integer totalRisks;

    @Schema(description = "风险条款列表")
    private List<RiskClauseDto> riskClauses;

    @Schema(description = "审查摘要")
    private ReviewSummaryDto summary;

    @Schema(description = "详细分析结果")
    private Map<String, Object> detailedAnalysis;

    @Schema(description = "创建时间")
    private LocalDateTime createdAt;

    @Schema(description = "完成时间") 
    private LocalDateTime completedAt;

    @Schema(description = "用户信息")
    private UserInfoDto userInfo;

    /**
     * 风险条款DTO
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @Schema(description = "风险条款信息")
    public static class RiskClauseDto {

        @Schema(description = "条款ID", example = "1")
        private Long id;

        @Schema(description = "条款文本")
        private String clauseText;

        @Schema(description = "风险类型", example = "违约责任风险")
        private String riskType;

        @Schema(description = "风险等级", example = "HIGH")
        private String riskLevel;

        @Schema(description = "风险描述")
        private String riskDescription;

        @Schema(description = "改进建议")
        private String suggestion;

        @Schema(description = "法律依据")
        private String legalBasis;

        @Schema(description = "条款位置起始", example = "100")
        private Integer positionStart;

        @Schema(description = "条款位置结束", example = "200")
        private Integer positionEnd;
    }

    /**
     * 审查摘要DTO
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @Schema(description = "审查摘要信息")
    public static class ReviewSummaryDto {

        @Schema(description = "整体风险等级", example = "MEDIUM")
        private String overallRiskLevel;

        @Schema(description = "高风险条款数", example = "2")
        private Integer highRiskCount;

        @Schema(description = "中风险条款数", example = "3")
        private Integer mediumRiskCount;

        @Schema(description = "低风险条款数", example = "1")
        private Integer lowRiskCount;

        @Schema(description = "核心风险提示")
        private List<String> coreRiskAlerts;

        @Schema(description = "优先改进建议")
        private List<String> priorityRecommendations;

        @Schema(description = "合规性评分", example = "75")
        private Integer complianceScore;

        @Schema(description = "合同完整性评分", example = "80")
        private Integer completenessScore;
        
        @Schema(description = "评分细则说明")
        private ScoringRulesDto scoringRules;
    }
    
    /**
     * 评分细则DTO
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @Schema(description = "评分细则说明")
    public static class ScoringRulesDto {
        
        @Schema(description = "评分方法", example = "基于风险数量和风险等级的综合评分")
        private String method;
        
        @Schema(description = "评分规则列表")
        private List<String> rules;
        
        @Schema(description = "评分说明", example = "满分100分，分数越高表示合同质量越好")
        private String description;
    }

    /**
     * 用户信息DTO
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @Schema(description = "用户基本信息")
    public static class UserInfoDto {

        @Schema(description = "用户ID", example = "1")
        private Long id;

        @Schema(description = "用户名", example = "admin")
        private String username;

        @Schema(description = "用户角色", example = "ADMIN")
        private String role;
    }

    /**
     * 从ContractReview实体转换为DTO
     */
    @SuppressWarnings("unchecked")
    public static ContractReviewResultDto fromEntity(ContractReview entity) {
        Map<String, Object> reviewResult = entity.getReviewResult();
        
        // 提取详细分析数据（解决嵌套问题）
        Map<String, Object> detailedAnalysisData = null;
        if (reviewResult != null && reviewResult.containsKey("detailedAnalysis")) {
            Object detailedAnalysisObj = reviewResult.get("detailedAnalysis");
            if (detailedAnalysisObj instanceof Map) {
                detailedAnalysisData = (Map<String, Object>) detailedAnalysisObj;
            }
        }
        
        // 构建summary对象
        ReviewSummaryDto summaryDto = buildSummary(entity, reviewResult, detailedAnalysisData);
        
        ContractReviewResultDtoBuilder builder = ContractReviewResultDto.builder()
            .id(entity.getId())
            .originalFilename(entity.getOriginalFilename())
            .reviewStatus(entity.getReviewStatus().name())
            .riskLevel(entity.getRiskLevel() != null ? entity.getRiskLevel().name() : null)
            .totalRisks(entity.getTotalRisks())
            .createdAt(entity.getCreatedAt())
            .completedAt(entity.getCompletedAt())
            .summary(summaryDto)
            .detailedAnalysis(detailedAnalysisData != null ? detailedAnalysisData : reviewResult);

        // 转换风险条款
        if (entity.getRiskClauses() != null && !entity.getRiskClauses().isEmpty()) {
            List<RiskClauseDto> riskClauseDtos = entity.getRiskClauses().stream()
                .map(riskClause -> RiskClauseDto.builder()
                    .id(riskClause.getId())
                    .clauseText(riskClause.getClauseText())
                    .riskType(riskClause.getRiskType())
                    .riskLevel(riskClause.getRiskLevel().name())
                    .riskDescription(riskClause.getRiskDescription())
                    .suggestion(riskClause.getSuggestion())
                    .legalBasis(riskClause.getLegalBasis())
                    .positionStart(riskClause.getPositionStart())
                    .positionEnd(riskClause.getPositionEnd())
                    .build())
                .toList();
            builder.riskClauses(riskClauseDtos);
        }

        // 转换用户信息
        if (entity.getUser() != null) {
            UserInfoDto userInfoDto = UserInfoDto.builder()
                .id(entity.getUser().getId())
                .username(entity.getUser().getUsername())
                .role(entity.getUser().getRole().name())
                .build();
            builder.userInfo(userInfoDto);
        }

        return builder.build();
    }
    
    /**
     * 构建审查摘要DTO
     */
    @SuppressWarnings("unchecked")
    private static ReviewSummaryDto buildSummary(ContractReview entity, 
                                                  Map<String, Object> reviewResult,
                                                  Map<String, Object> detailedAnalysis) {
        ReviewSummaryDto.ReviewSummaryDtoBuilder summaryBuilder = ReviewSummaryDto.builder();
        
        // 设置整体风险等级
        if (entity.getRiskLevel() != null) {
            summaryBuilder.overallRiskLevel(entity.getRiskLevel().name());
        } else if (reviewResult != null && reviewResult.containsKey("overallRiskLevel")) {
            summaryBuilder.overallRiskLevel(String.valueOf(reviewResult.get("overallRiskLevel")));
        }
        
        // 统计各级别风险数量
        int highRiskCount = 0;
        int mediumRiskCount = 0;
        int lowRiskCount = 0;
        
        if (entity.getRiskClauses() != null) {
            for (var clause : entity.getRiskClauses()) {
                switch (clause.getRiskLevel()) {
                    case HIGH -> highRiskCount++;
                    case MEDIUM -> mediumRiskCount++;
                    case LOW -> lowRiskCount++;
                }
            }
        }
        
        summaryBuilder
            .highRiskCount(highRiskCount)
            .mediumRiskCount(mediumRiskCount)
            .lowRiskCount(lowRiskCount);
        
        // 提取核心风险提示
        if (reviewResult != null && reviewResult.containsKey("coreRiskAlerts")) {
            Object coreRiskAlerts = reviewResult.get("coreRiskAlerts");
            if (coreRiskAlerts instanceof List) {
                summaryBuilder.coreRiskAlerts((List<String>) coreRiskAlerts);
            }
        }
        
        // 提取优先改进建议
        if (reviewResult != null && reviewResult.containsKey("priorityRecommendations")) {
            Object priorityRecommendations = reviewResult.get("priorityRecommendations");
            if (priorityRecommendations instanceof List) {
                summaryBuilder.priorityRecommendations((List<String>) priorityRecommendations);
            }
        }
        
        // 提取合规性评分
        if (reviewResult != null && reviewResult.containsKey("complianceScore")) {
            Object complianceScore = reviewResult.get("complianceScore");
            if (complianceScore instanceof Number) {
                summaryBuilder.complianceScore(((Number) complianceScore).intValue());
            }
        }
        
        // 提取完整性评分（从详细分析中）
        if (detailedAnalysis != null && detailedAnalysis.containsKey("completenessScore")) {
            Object completenessScore = detailedAnalysis.get("completenessScore");
            if (completenessScore instanceof Number) {
                summaryBuilder.completenessScore(((Number) completenessScore).intValue());
            }
        }
        
        // 构建评分细则说明
        ScoringRulesDto scoringRules = buildScoringRules();
        summaryBuilder.scoringRules(scoringRules);
        
        return summaryBuilder.build();
    }
    
    /**
     * 构建评分细则说明
     */
    private static ScoringRulesDto buildScoringRules() {
        List<String> rules = List.of(
            "基础分100分：合同初始为满分状态",
            "风险数量扣分：每个风险项扣除5分（最多扣50分）",
            "风险等级加权扣分：高风险额外扣除20分，中等风险额外扣除10分",
            "保底分数：最低20分（即使存在多个高风险也不低于20分）",
            "评分区间：20-100分，分数越高表示合同质量越好，风险越低",
            "评分等级：90+分（优秀）、70-89分（良好）、50-69分（一般）、<50分（需重点关注）"
        );
        
        return ScoringRulesDto.builder()
            .method("综合评分法（风险数量 + 风险等级加权）")
            .rules(rules)
            .description("满分100分。系统采用智能评分算法，综合考虑风险数量和风险等级：" +
                        "基础分100分，每个风险扣5分，高风险额外扣20分，中等风险额外扣10分。" +
                        "分数越高表示合同质量越好、风险越低。" +
                        "建议：90分以上为优秀，70-89分为良好，50-69分为一般，50分以下需要重点关注和修改。")
            .build();
    }
}
