package com.river.LegalAssistant.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * 风险等级统计DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class RiskLevelStatisticsDto {
    
    /**
     * 高风险数量
     */
    private Long highRiskCount;
    
    /**
     * 中风险数量
     */
    private Long mediumRiskCount;
    
    /**
     * 低风险数量
     */
    private Long lowRiskCount;
    
    /**
     * 总计
     */
    private Long totalCount;
    
    /**
     * 从统计Map转换为DTO
     */
    public static RiskLevelStatisticsDto fromStatisticsMap(Map<String, Long> statistics) {
        Long high = statistics.getOrDefault("HIGH", 0L);
        Long medium = statistics.getOrDefault("MEDIUM", 0L);
        Long low = statistics.getOrDefault("LOW", 0L);
        
        return RiskLevelStatisticsDto.builder()
                .highRiskCount(high)
                .mediumRiskCount(medium)
                .lowRiskCount(low)
                .totalCount(high + medium + low)
                .build();
    }
}

