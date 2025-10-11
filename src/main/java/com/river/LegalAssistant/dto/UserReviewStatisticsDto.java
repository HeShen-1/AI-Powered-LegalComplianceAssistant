package com.river.LegalAssistant.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 用户审查统计DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class UserReviewStatisticsDto {
    
    /**
     * 用户ID
     */
    private Long userId;
    
    /**
     * 用户名（可选）
     */
    private String username;
    
    /**
     * 总审查数
     */
    private Long totalReviews;
}

