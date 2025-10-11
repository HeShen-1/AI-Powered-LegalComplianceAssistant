package com.river.LegalAssistant.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 用户统计数据 DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class UserStatsDto {
    
    /**
     * 合同审查总数
     */
    private Integer totalReviews;
    
    /**
     * 已完成审查数
     */
    private Integer completedReviews;
    
    /**
     * 处理中审查数
     */
    private Integer processingReviews;
    
    /**
     * 高风险发现数
     */
    private Integer highRiskCount;
    
    /**
     * AI问答总数
     */
    private Integer totalQuestions;
    
    /**
     * 本月问答数
     */
    private Integer monthlyQuestions;
    
    /**
     * 平均响应时间（秒）
     */
    private Double avgResponseTime;
    
    /**
     * 满意度（百分比）
     */
    private Integer satisfaction;
    
    /**
     * 加入天数
     */
    private Integer joinDays;
}

