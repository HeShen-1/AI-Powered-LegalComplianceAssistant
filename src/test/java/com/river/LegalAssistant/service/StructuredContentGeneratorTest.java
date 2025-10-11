package com.river.LegalAssistant.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.river.LegalAssistant.dto.report.ExecutiveSummaryDto;
import com.river.LegalAssistant.dto.report.DeepAnalysisDto;
import com.river.LegalAssistant.dto.report.ImprovementSuggestionDto;
import com.river.LegalAssistant.entity.ContractReview;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * 结构化内容生成器测试
 */
@ExtendWith(MockitoExtension.class)
class StructuredContentGeneratorTest {
    
    @Mock
    private AgentService agentService;
    
    @Mock
    private ObjectMapper objectMapper;
    
    @InjectMocks
    private StructuredContentGenerator structuredContentGenerator;
    
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
    void testGenerateExecutiveSummary_Success() throws Exception {
        // 准备AI返回的JSON响应
        String jsonResponse = """
            {
              "contractType": "服务合同",
              "riskLevel": "中",
              "riskReason": "合同存在部分条款不够明确，可能导致履约风险",
              "coreRisks": ["付款条款不明确", "违约责任较重", "争议解决机制不完善"],
              "actionSuggestions": ["建议明确付款时间节点", "协商修改违约条款"]
            }
            """;
        
        ExecutiveSummaryDto expectedDto = ExecutiveSummaryDto.builder()
                .contractType("服务合同")
                .riskLevel("中")
                .riskReason("合同存在部分条款不够明确，可能导致履约风险")
                .coreRisks(List.of("付款条款不明确", "违约责任较重", "争议解决机制不完善"))
                .actionSuggestions(List.of("建议明确付款时间节点", "协商修改违约条款"))
                .build();
        
        when(agentService.directChatForReport(anyString())).thenReturn(jsonResponse);
        when(objectMapper.readValue(anyString(), ExecutiveSummaryDto.class))
                .thenReturn(expectedDto);
        
        // 执行测试
        ExecutiveSummaryDto result = structuredContentGenerator.generateExecutiveSummary(contractReview);
        
        // 验证结果
        assertNotNull(result);
        assertEquals("服务合同", result.getContractType());
        assertEquals("中", result.getRiskLevel());
        assertEquals(3, result.getCoreRisks().size());
        assertEquals(2, result.getActionSuggestions().size());
    }
    
    @Test
    void testGenerateExecutiveSummary_WithCodeBlockMarkers() throws Exception {
        // 测试AI返回带```json```标记的情况
        String jsonResponse = """
            ```json
            {
              "contractType": "服务合同",
              "riskLevel": "中",
              "riskReason": "合同存在部分条款不够明确",
              "coreRisks": ["付款条款不明确"],
              "actionSuggestions": ["建议明确付款时间节点"]
            }
            ```
            """;
        
        // 模拟清理后的JSON解析成功
        ExecutiveSummaryDto expectedDto = ExecutiveSummaryDto.builder()
                .contractType("服务合同")
                .riskLevel("中")
                .riskReason("合同存在部分条款不够明确")
                .coreRisks(List.of("付款条款不明确"))
                .actionSuggestions(List.of("建议明确付款时间节点"))
                .build();
        
        when(agentService.directChatForReport(anyString())).thenReturn(jsonResponse);
        when(objectMapper.readValue(anyString(), ExecutiveSummaryDto.class))
                .thenReturn(expectedDto);
        
        // 执行测试
        ExecutiveSummaryDto result = structuredContentGenerator.generateExecutiveSummary(contractReview);
        
        // 验证结果
        assertNotNull(result);
        assertEquals("服务合同", result.getContractType());
    }
    
    @Test
    void testGenerateExecutiveSummary_JsonParseFailure() throws Exception {
        // 测试JSON解析失败的情况
        String invalidJson = "这不是有效的JSON";
        
        when(agentService.directChatForReport(anyString())).thenReturn(invalidJson);
        when(objectMapper.readValue(anyString(), ExecutiveSummaryDto.class))
                .thenThrow(new com.fasterxml.jackson.core.JsonProcessingException("Parse error") {});
        
        // 执行测试并验证异常
        assertThrows(RuntimeException.class, () -> {
            structuredContentGenerator.generateExecutiveSummary(contractReview);
        });
    }
}

