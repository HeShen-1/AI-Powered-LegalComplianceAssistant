package com.river.LegalAssistant.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * 审查分析响应 DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ContractAnalysisDto {
    
    /**
     * 审查ID
     */
    private Long reviewId;
    
    /**
     * 审查状态
     */
    private String status;
    
    /**
     * 风险等级
     */
    private String riskLevel;
    
    /**
     * 审查结果
     */
    private Map<String, Object> result;
}
