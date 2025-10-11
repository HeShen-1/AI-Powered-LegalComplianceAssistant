package com.river.LegalAssistant.dto.report;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 执行摘要数据模型
 * 用于结构化接收AI生成的执行摘要JSON数据
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ExecutiveSummaryDto {
    
    /**
     * 合同性质和类型
     */
    @JsonProperty("contractType")
    private String contractType;
    
    /**
     * 风险等级（高/中/低）
     */
    @JsonProperty("riskLevel")
    private String riskLevel;
    
    /**
     * 风险等级判定理由
     */
    @JsonProperty("riskReason")
    private String riskReason;
    
    /**
     * 核心风险点列表（2-3个）
     */
    @JsonProperty("coreRisks")
    private List<String> coreRisks;
    
    /**
     * 行动建议列表
     */
    @JsonProperty("actionSuggestions")
    private List<String> actionSuggestions;
}

