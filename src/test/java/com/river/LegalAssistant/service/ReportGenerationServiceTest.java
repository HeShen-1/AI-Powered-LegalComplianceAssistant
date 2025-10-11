package com.river.LegalAssistant.service;

import com.river.LegalAssistant.dto.report.ExecutiveSummaryDto;
import com.river.LegalAssistant.dto.report.DeepAnalysisDto;
import com.river.LegalAssistant.dto.report.ImprovementSuggestionDto;
import com.river.LegalAssistant.entity.ContractReview;
import com.river.LegalAssistant.util.ReportContentValidator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * 报告生成服务测试 - 重点测试降级场景
 */
@ExtendWith(MockitoExtension.class)
class ReportGenerationServiceTest {
    
    @Mock
    private StructuredContentGenerator structuredContentGenerator;
    
    @Mock
    private ReportTemplateRenderer templateRenderer;
    
    @Mock
    private ReportContentValidator contentValidator;
    
    @Mock
    private DeepSeekService deepSeekService;
    
    @Mock
    private AgentService agentService;
    
    @Mock
    private MarkdownToPdfRenderer markdownRenderer;
    
    @InjectMocks
    private ReportGenerationService reportGenerationService;
    
    private ContractReview contractReview;
    
    @BeforeEach
    void setUp() {
        contractReview = new ContractReview();
        contractReview.setId(1L);
        contractReview.setOriginalFilename("测试合同.pdf");
        contractReview.setContentText("这是一份测试合同的内容...");
        contractReview.setRiskLevel(ContractReview.RiskLevel.MEDIUM);
        contractReview.setTotalRisks(3);
    }
    
    @Test
    void testGenerateMarkdownReport_AllSuccess() {
        // 准备成功的DTO
        ExecutiveSummaryDto summaryDto = ExecutiveSummaryDto.builder()
                .contractType("服务合同")
                .riskLevel("中")
                .riskReason("有效的风险理由")
                .coreRisks(List.of("风险1"))
                .actionSuggestions(List.of("建议1"))
                .build();
        
        DeepAnalysisDto analysisDto = DeepAnalysisDto.builder()
                .legalNature(DeepAnalysisDto.LegalNatureAnalysis.builder()
                        .contractType("服务合同")
                        .build())
                .build();
        
        ImprovementSuggestionDto improvementsDto = ImprovementSuggestionDto.builder()
                .suggestions(List.of(ImprovementSuggestionDto.Suggestion.builder()
                        .problemDescription("问题")
                        .suggestedModification("建议")
                        .build()))
                .build();
        
        // 模拟生成和验证成功
        when(structuredContentGenerator.generateExecutiveSummary(any())).thenReturn(summaryDto);
        when(structuredContentGenerator.generateDeepAnalysis(any())).thenReturn(analysisDto);
        when(structuredContentGenerator.generateImprovementSuggestions(any())).thenReturn(improvementsDto);
        
        when(contentValidator.validateExecutiveSummary(any())).thenReturn(true);
        when(contentValidator.validateDeepAnalysis(any())).thenReturn(true);
        when(contentValidator.validateImprovementSuggestions(any())).thenReturn(true);
        
        when(templateRenderer.renderContractReviewReport(any(), any(), any(), any()))
                .thenReturn("# 测试报告");
        
        // 执行测试
        String report = reportGenerationService.generateMarkdownReport(contractReview);
        
        // 验证结果
        assertNotNull(report);
        assertEquals("# 测试报告", report);
        verify(structuredContentGenerator, times(1)).generateExecutiveSummary(any());
        verify(structuredContentGenerator, times(1)).generateDeepAnalysis(any());
        verify(structuredContentGenerator, times(1)).generateImprovementSuggestions(any());
    }
    
    @Test
    void testGenerateMarkdownReport_SummaryValidationFails_UsesFallback() {
        // 模拟生成成功但验证失败
        ExecutiveSummaryDto invalidDto = ExecutiveSummaryDto.builder().build();
        
        when(structuredContentGenerator.generateExecutiveSummary(any())).thenReturn(invalidDto);
        when(contentValidator.validateExecutiveSummary(any())).thenReturn(false);
        
        // 其他部分成功
        when(structuredContentGenerator.generateDeepAnalysis(any())).thenReturn(null);
        when(structuredContentGenerator.generateImprovementSuggestions(any()))
                .thenReturn(ImprovementSuggestionDto.builder()
                        .suggestions(List.of())
                        .build());
        when(contentValidator.validateDeepAnalysis(any())).thenReturn(false);
        when(contentValidator.validateImprovementSuggestions(any())).thenReturn(false);
        
        when(templateRenderer.renderContractReviewReport(any(), any(), any(), any()))
                .thenReturn("# 降级报告");
        
        // 执行测试
        String report = reportGenerationService.generateMarkdownReport(contractReview);
        
        // 验证结果 - 应该使用降级的DTO
        assertNotNull(report);
        verify(contentValidator, times(1)).validateExecutiveSummary(any());
        verify(templateRenderer, times(1)).renderContractReviewReport(any(), any(), any(), any());
    }
    
    @Test
    void testGenerateMarkdownReport_GeneratorThrowsException_UsesFallback() {
        // 模拟生成器抛出异常
        when(structuredContentGenerator.generateExecutiveSummary(any()))
                .thenThrow(new RuntimeException("AI服务不可用"));
        
        // 其他部分也失败
        when(structuredContentGenerator.generateDeepAnalysis(any()))
                .thenThrow(new RuntimeException("AI服务不可用"));
        when(structuredContentGenerator.generateImprovementSuggestions(any()))
                .thenThrow(new RuntimeException("AI服务不可用"));
        
        when(templateRenderer.renderContractReviewReport(any(), any(), any(), any()))
                .thenReturn("# 完全降级报告");
        
        // 执行测试
        String report = reportGenerationService.generateMarkdownReport(contractReview);
        
        // 验证结果 - 应该捕获异常并使用降级方案
        assertNotNull(report);
        assertEquals("# 完全降级报告", report);
        verify(templateRenderer, times(1)).renderContractReviewReport(any(), any(), any(), any());
    }
    
    @Test
    void testGenerateMarkdownReport_HighRiskContract_ProperFallback() {
        // 测试高风险合同的降级方案
        contractReview.setRiskLevel(ContractReview.RiskLevel.HIGH);
        contractReview.setTotalRisks(10);
        
        // 模拟所有生成失败
        when(structuredContentGenerator.generateExecutiveSummary(any()))
                .thenThrow(new RuntimeException("生成失败"));
        when(structuredContentGenerator.generateDeepAnalysis(any()))
                .thenThrow(new RuntimeException("生成失败"));
        when(structuredContentGenerator.generateImprovementSuggestions(any()))
                .thenThrow(new RuntimeException("生成失败"));
        
        when(templateRenderer.renderContractReviewReport(any(), any(), any(), any()))
                .thenAnswer(invocation -> {
                    ExecutiveSummaryDto summary = invocation.getArgument(1);
                    // 验证降级的摘要包含正确的行动建议（高风险）
                    assertTrue(summary.getActionSuggestions().stream()
                            .anyMatch(s -> s.contains("暂缓签署")));
                    return "# 高风险降级报告";
                });
        
        // 执行测试
        String report = reportGenerationService.generateMarkdownReport(contractReview);
        
        // 验证结果
        assertNotNull(report);
        assertEquals("# 高风险降级报告", report);
    }
    
    @Test
    void testGenerateMarkdownReport_LowRiskContract_ProperFallback() {
        // 测试低风险合同的降级方案
        contractReview.setRiskLevel(ContractReview.RiskLevel.LOW);
        contractReview.setTotalRisks(1);
        
        // 模拟所有生成失败
        when(structuredContentGenerator.generateExecutiveSummary(any()))
                .thenThrow(new RuntimeException("生成失败"));
        when(structuredContentGenerator.generateDeepAnalysis(any()))
                .thenThrow(new RuntimeException("生成失败"));
        when(structuredContentGenerator.generateImprovementSuggestions(any()))
                .thenThrow(new RuntimeException("生成失败"));
        
        when(templateRenderer.renderContractReviewReport(any(), any(), any(), any()))
                .thenAnswer(invocation -> {
                    ExecutiveSummaryDto summary = invocation.getArgument(1);
                    // 验证降级的摘要包含正确的行动建议（低风险）
                    assertTrue(summary.getActionSuggestions().stream()
                            .anyMatch(s -> s.contains("风险较低")));
                    return "# 低风险降级报告";
                });
        
        // 执行测试
        String report = reportGenerationService.generateMarkdownReport(contractReview);
        
        // 验证结果
        assertNotNull(report);
        assertEquals("# 低风险降级报告", report);
    }
}

