package com.river.LegalAssistant.service;

import com.river.LegalAssistant.dto.report.*;
import com.river.LegalAssistant.entity.ContractReview;
import com.river.LegalAssistant.entity.RiskClause;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 报告模板渲染服务
 * 使用Thymeleaf模板引擎渲染Markdown报告
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class ReportTemplateRenderer {
    
    private final TemplateEngine markdownTemplateEngine;
    
    /**
     * 渲染合同审查报告
     */
    public String renderContractReviewReport(
            ContractReview contractReview,
            ExecutiveSummaryDto summary,
            DeepAnalysisDto analysis,
            ImprovementSuggestionDto improvements) {
        
        log.info("开始渲染Markdown报告，审查ID: {}", contractReview.getId());
        
        Context context = new Context();
        
        // 1. 基本信息
        context.setVariable("review", contractReview);
        context.setVariable("reviewStatus", getStatusDisplayName(contractReview.getReviewStatus()));
        context.setVariable("riskLevelDisplay", getRiskLevelDisplayName(contractReview.getRiskLevel()));
        context.setVariable("riskLevelDescription", getRiskLevelDescription(contractReview.getRiskLevel()));
        context.setVariable("createdAt", formatDateTime(contractReview.getCreatedAt()));
        context.setVariable("completedAt", contractReview.getCompletedAt() != null ? 
                formatDateTime(contractReview.getCompletedAt()) : null);
        context.setVariable("reportGeneratedAt", formatDateTime(LocalDateTime.now()));
        
        // 2. AI生成的结构化内容
        context.setVariable("summary", summary);
        context.setVariable("analysis", analysis);
        context.setVariable("improvements", improvements);
        
        // 3. 风险统计
        context.setVariable("totalRisks", contractReview.getTotalRisks());
        context.setVariable("riskClauses", contractReview.getRiskClauses());
        
        if (contractReview.getRiskClauses() != null && !contractReview.getRiskClauses().isEmpty()) {
            // 计算风险统计数据
            List<RiskStatistic> riskStatistics = calculateRiskStatistics(contractReview.getRiskClauses());
            context.setVariable("riskStatistics", riskStatistics);
            
            // 为每个风险条款添加显示属性
            List<RiskClauseViewModel> riskClauseViewModels = contractReview.getRiskClauses().stream()
                    .map(this::toRiskClauseViewModel)
                    .collect(Collectors.toList());
            context.setVariable("riskClauses", riskClauseViewModels);
        }
        
        // 4. 核心风险提示
        if (contractReview.getReviewResult() != null) {
            Object coreRiskAlerts = contractReview.getReviewResult().get("coreRiskAlerts");
            if (coreRiskAlerts instanceof List<?> alerts) {
                context.setVariable("coreRiskAlerts", alerts);
            }
        }
        
        // 5. 渲染模板
        try {
            String renderedReport = markdownTemplateEngine.process("contract-review-report", context);
            log.info("Markdown报告渲染成功，审查ID: {}, 报告长度: {} 字符", 
                    contractReview.getId(), renderedReport.length());
            return renderedReport;
        } catch (Exception e) {
            log.error("Markdown报告渲染失败，审查ID: {}", contractReview.getId(), e);
            throw new RuntimeException("Failed to render Markdown report", e);
        }
    }
    
    /**
     * 计算风险统计数据
     */
    private List<RiskStatistic> calculateRiskStatistics(List<RiskClause> riskClauses) {
        int totalRisks = riskClauses.size();
        Map<ContractReview.RiskLevel, Long> riskLevelCount = riskClauses.stream()
                .collect(Collectors.groupingBy(RiskClause::getRiskLevel, Collectors.counting()));
        
        List<RiskStatistic> statistics = new ArrayList<>();
        for (ContractReview.RiskLevel level : ContractReview.RiskLevel.values()) {
            long count = riskLevelCount.getOrDefault(level, 0L);
            if (count > 0) {
                double percentage = (double) count / totalRisks * 100;
                statistics.add(new RiskStatistic(
                        getRiskLevelDisplayName(level),
                        count,
                        String.format("%.1f", percentage)
                ));
            }
        }
        return statistics;
    }
    
    /**
     * 转换为风险条款视图模型
     */
    private RiskClauseViewModel toRiskClauseViewModel(RiskClause riskClause) {
        String riskIcon = switch (riskClause.getRiskLevel()) {
            case HIGH -> "🔴";
            case MEDIUM -> "🟡";
            case LOW -> "🟢";
        };
        
        return new RiskClauseViewModel(
                riskClause.getRiskType(),
                riskClause.getRiskLevel(),
                getRiskLevelDisplayName(riskClause.getRiskLevel()),
                riskIcon,
                riskClause.getClauseText(),
                riskClause.getRiskDescription(),
                riskClause.getSuggestion()
        );
    }
    
    /**
     * 获取状态显示名称
     */
    private String getStatusDisplayName(ContractReview.ReviewStatus status) {
        return switch (status) {
            case PENDING -> "待处理";
            case PROCESSING -> "处理中";
            case COMPLETED -> "已完成";
            case FAILED -> "处理失败";
        };
    }
    
    /**
     * 获取风险等级显示名称
     */
    private String getRiskLevelDisplayName(ContractReview.RiskLevel riskLevel) {
        if (riskLevel == null) return "未知";
        return switch (riskLevel) {
            case HIGH -> "高风险";
            case MEDIUM -> "中等风险";
            case LOW -> "低风险";
        };
    }
    
    /**
     * 获取风险等级描述
     */
    private String getRiskLevelDescription(ContractReview.RiskLevel riskLevel) {
        if (riskLevel == null) return "";
        return switch (riskLevel) {
            case HIGH -> "建议谨慎处理，必要时寻求专业法律意见。";
            case MEDIUM -> "存在一定风险，建议关注相关条款。";
            case LOW -> "风险较低，但仍需注意合同执行。";
        };
    }
    
    /**
     * 格式化日期时间
     */
    private String formatDateTime(LocalDateTime dateTime) {
        if (dateTime == null) return "未知";
        return dateTime.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
    }
    
    /**
     * 风险统计视图模型
     */
    public record RiskStatistic(String levelName, long count, String percentage) {}
    
    /**
     * 风险条款视图模型
     */
    public record RiskClauseViewModel(
            String riskType,
            ContractReview.RiskLevel riskLevel,
            String riskLevelDisplay,
            String riskIcon,
            String clauseText,
            String riskDescription,
            String suggestion
    ) {}
}

