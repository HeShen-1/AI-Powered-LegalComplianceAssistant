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
    public static ContractReviewResultDto fromEntity(ContractReview entity) {
        ContractReviewResultDtoBuilder builder = ContractReviewResultDto.builder()
            .id(entity.getId())
            .originalFilename(entity.getOriginalFilename())
            .reviewStatus(entity.getReviewStatus().name())
            .riskLevel(entity.getRiskLevel() != null ? entity.getRiskLevel().name() : null)
            .totalRisks(entity.getTotalRisks())
            .createdAt(entity.getCreatedAt())
            .completedAt(entity.getCompletedAt())
            .detailedAnalysis(entity.getReviewResult());

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
}
