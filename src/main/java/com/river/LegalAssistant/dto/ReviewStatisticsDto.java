package com.river.LegalAssistant.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 审查统计响应 DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReviewStatisticsDto {
    
    /**
     * 用户ID
     */
    private Long userId;
    
    /**
     * 用户名（仅在查询自己统计时返回）
     */
    private String username;
    
    /**
     * 审查总数
     */
    private Long totalReviews;
}
