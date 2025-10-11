package com.river.LegalAssistant.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.river.LegalAssistant.entity.ContractReview;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 合同审查摘要DTO - 用于列表展示
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ContractReviewSummaryDto {
    
    /**
     * 审查ID
     */
    private Long id;
    
    /**
     * 原始文件名
     */
    private String originalFilename;
    
    /**
     * 审查状态
     */
    private String reviewStatus;
    
    /**
     * 风险等级
     */
    private String riskLevel;
    
    /**
     * 总风险数
     */
    private Integer totalRisks;
    
    /**
     * 创建时间
     */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createdAt;
    
    /**
     * 完成时间
     */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime completedAt;
    
    /**
     * 从Entity转换为DTO
     */
    public static ContractReviewSummaryDto fromEntity(ContractReview review) {
        return ContractReviewSummaryDto.builder()
                .id(review.getId())
                .originalFilename(review.getOriginalFilename())
                .reviewStatus(review.getReviewStatus() != null ? review.getReviewStatus().name() : null)
                .riskLevel(review.getRiskLevel() != null ? review.getRiskLevel().name() : null)
                .totalRisks(review.getTotalRisks())
                .createdAt(review.getCreatedAt())
                .completedAt(review.getCompletedAt())
                .build();
    }
}

