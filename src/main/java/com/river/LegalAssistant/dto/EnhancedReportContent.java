package com.river.LegalAssistant.dto;

import com.river.LegalAssistant.dto.report.DeepAnalysisDto;
import com.river.LegalAssistant.dto.report.ExecutiveSummaryDto;
import com.river.LegalAssistant.dto.report.ImprovementSuggestionDto;
import lombok.Builder;
import lombok.Data;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 增强报告内容DTO
 * 包含DeepSeek生成的深度分析内容
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class EnhancedReportContent {
    
    /**
     * 是否包含增强内容
     */
    private boolean hasEnhancedContent;
    
    /**
     * DeepSeek深度分析结果（文本格式，用于向后兼容）
     */
    private String deepAnalysis;
    
    /**
     * 执行摘要（AI增强版，文本格式，用于向后兼容）
     */
    private String executiveSummary;
    
    /**
     * 改进建议（文本格式，用于向后兼容）
     */
    private String improvementSuggestions;
    
    /**
     * 法律风险评估
     */
    private String riskAssessment;
    
    /**
     * 合规建议
     */
    private String complianceAdvice;
    
    /**
     * 商业影响分析
     */
    private String businessImpactAnalysis;
    
    /**
     * 内容生成时间
     */
    private LocalDateTime generatedAt;
    
    /**
     * 生成用时（毫秒）
     */
    private Long processingTime;
    
    /**
     * 是否生成成功
     */
    private boolean successful;
    
    /**
     * 错误信息（如果生成失败）
     */
    private String errorMessage;
    
    // ===== 结构化DTO字段（重构新增） =====
    
    /**
     * 执行摘要结构化DTO
     */
    private ExecutiveSummaryDto summaryDto;
    
    /**
     * 深度分析结构化DTO
     */
    private DeepAnalysisDto analysisDto;
    
    /**
     * 改进建议结构化DTO
     */
    private ImprovementSuggestionDto improvementsDto;
}
