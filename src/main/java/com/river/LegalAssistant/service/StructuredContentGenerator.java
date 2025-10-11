package com.river.LegalAssistant.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.river.LegalAssistant.dto.report.*;
import com.river.LegalAssistant.entity.ContractReview;
import com.river.LegalAssistant.util.ReportContentValidator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Map;

/**
 * 结构化内容生成服务
 * 负责构建JSON格式的Prompt并解析AI返回的结构化数据
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class StructuredContentGenerator {
    
    private final AgentService agentService;
    private final ObjectMapper objectMapper;
    private final PromptTemplateService promptTemplateService;
    private final ReportContentValidator contentValidator;
    
    /**
     * 生成执行摘要结构化内容
     */
    public ExecutiveSummaryDto generateExecutiveSummary(ContractReview contractReview) {
        log.info("生成执行摘要结构化内容，审查ID: {}", contractReview.getId());
        
        String prompt = buildExecutiveSummaryPrompt(contractReview);
        String response = agentService.directChatForReport(prompt);
        
        ExecutiveSummaryDto dto = parseJsonResponse(response, ExecutiveSummaryDto.class);
        return cleanExecutiveSummaryDto(dto);
    }
    
    /**
     * 生成深度分析结构化内容
     */
    public DeepAnalysisDto generateDeepAnalysis(ContractReview contractReview) {
        log.info("生成深度分析结构化内容，审查ID: {}", contractReview.getId());
        
        String prompt = buildDeepAnalysisPrompt(contractReview);
        String response = agentService.directChatForReport(prompt);
        
        DeepAnalysisDto dto = parseJsonResponse(response, DeepAnalysisDto.class);
        return cleanDeepAnalysisDto(dto);
    }
    
    /**
     * 生成改进建议结构化内容
     */
    public ImprovementSuggestionDto generateImprovementSuggestions(ContractReview contractReview) {
        log.info("生成改进建议结构化内容，审查ID: {}", contractReview.getId());
        
        String prompt = buildImprovementSuggestionsPrompt(contractReview);
        String response = agentService.directChatForReport(prompt);
        
        ImprovementSuggestionDto dto = parseJsonResponse(response, ImprovementSuggestionDto.class);
        return cleanImprovementSuggestionDto(dto);
    }
    
    /**
     * 构建执行摘要Prompt（JSON格式输出）
     */
    private String buildExecutiveSummaryPrompt(ContractReview contractReview) {
        String contractContent = contractReview.getContentText();
        if (contractContent.length() > 8000) {
            contractContent = contractContent.substring(0, 8000) + "...[已截取]";
        }
        
        return promptTemplateService.render("executive-summary", Map.of(
            "contractContent", contractContent,
            "fileName", contractReview.getOriginalFilename() != null ? contractReview.getOriginalFilename() : "未知",
            "totalRisks", contractReview.getTotalRisks() != null ? contractReview.getTotalRisks() : 0
        ));
    }
    
    /**
     * 构建深度分析Prompt（JSON格式输出）
     */
    private String buildDeepAnalysisPrompt(ContractReview contractReview) {
        String contractContent = contractReview.getContentText();
        if (contractContent.length() > 8000) {
            contractContent = contractContent.substring(0, 8000) + "...[已截取]";
        }
        
        return promptTemplateService.render("deep-analysis", Map.of(
            "contractContent", contractContent,
            "fileName", contractReview.getOriginalFilename() != null ? contractReview.getOriginalFilename() : "未知",
            "riskLevel", contractReview.getRiskLevel() != null ? getRiskLevelDisplayName(contractReview.getRiskLevel()) : "未评估",
            "totalRisks", contractReview.getTotalRisks() != null ? contractReview.getTotalRisks() : 0
        ));
    }
    
    /**
     * 构建改进建议Prompt（JSON格式输出）
     */
    private String buildImprovementSuggestionsPrompt(ContractReview contractReview) {
        String contractContent = contractReview.getContentText();
        if (contractContent.length() > 8000) {
            contractContent = contractContent.substring(0, 8000) + "...[已截取]";
        }
        
        return promptTemplateService.render("improvement-suggestions", Map.of(
            "contractContent", contractContent,
            "totalRisks", contractReview.getTotalRisks() != null ? contractReview.getTotalRisks() : 0
        ));
    }
    
    /**
     * 解析JSON响应
     */
    private <T> T parseJsonResponse(String response, Class<T> targetClass) {
        try {
            // 清理响应文本，移除可能的代码块标记
            String cleanedResponse = response.trim();
            if (cleanedResponse.startsWith("```json")) {
                cleanedResponse = cleanedResponse.substring(7);
            }
            if (cleanedResponse.startsWith("```")) {
                cleanedResponse = cleanedResponse.substring(3);
            }
            if (cleanedResponse.endsWith("```")) {
                cleanedResponse = cleanedResponse.substring(0, cleanedResponse.length() - 3);
            }
            cleanedResponse = cleanedResponse.trim();
            
            log.debug("清理后的JSON响应: {}", cleanedResponse.substring(0, Math.min(200, cleanedResponse.length())));
            
            // 解析JSON
            T result = objectMapper.readValue(cleanedResponse, targetClass);
            
            // 验证解析结果，检查是否有重复内容
            validateParsedContent(result);
            
            return result;
        } catch (JsonProcessingException e) {
            log.error("JSON解析失败，响应内容: {}", response.substring(0, Math.min(500, response.length())), e);
            throw new RuntimeException("Failed to parse AI response as JSON", e);
        }
    }
    
    /**
     * 验证解析后的内容，检查是否有重复内容
     */
    private void validateParsedContent(Object content) {
        if (content == null) {
            return;
        }
        
        // 将对象转换为JSON字符串进行检查
        try {
            String jsonString = objectMapper.writeValueAsString(content);
            
            // 检查是否有明显的重复模式
            if (hasDuplicatePattern(jsonString)) {
                log.warn("检测到解析内容中存在重复模式，可能影响报告质量");
            }
        } catch (JsonProcessingException e) {
            log.warn("验证解析内容时出现异常: {}", e.getMessage());
        }
    }
    
    /**
     * 检查字符串是否包含重复模式
     */
    private boolean hasDuplicatePattern(String content) {
        if (content == null || content.length() < 100) {
            return false;
        }
        
        // 检查是否有连续重复的文本片段
        String[] words = content.split("\\s+");
        if (words.length < 10) {
            return false;
        }
        
        // 检查是否有连续重复的词语
        for (int i = 0; i < words.length - 5; i++) {
            String segment = words[i] + " " + words[i + 1] + " " + words[i + 2];
            int count = 0;
            for (int j = i + 3; j < words.length - 2; j++) {
                String nextSegment = words[j] + " " + words[j + 1] + " " + words[j + 2];
                if (segment.equals(nextSegment)) {
                    count++;
                    if (count > 2) {
                        log.debug("检测到重复模式: {}", segment);
                        return true;
                    }
                }
            }
        }
        
        return false;
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
    
    // ==================== DTO清理方法 ====================
    
    /**
     * 清理执行摘要DTO中的重复内容
     */
    private ExecutiveSummaryDto cleanExecutiveSummaryDto(ExecutiveSummaryDto dto) {
        if (dto == null) {
            return null;
        }
        
        log.debug("开始清理执行摘要DTO中的重复内容");
        
        // 清理各个字段的重复内容
        if (dto.getContractType() != null) {
            dto.setContractType(contentValidator.removeDuplicateContent(dto.getContractType()));
        }
        if (dto.getRiskReason() != null) {
            dto.setRiskReason(contentValidator.removeDuplicateContent(dto.getRiskReason()));
        }
        if (dto.getCoreRisks() != null) {
            dto.setCoreRisks(dto.getCoreRisks().stream()
                    .map(contentValidator::removeDuplicateContent)
                    .toList());
        }
        if (dto.getActionSuggestions() != null) {
            dto.setActionSuggestions(dto.getActionSuggestions().stream()
                    .map(contentValidator::removeDuplicateContent)
                    .toList());
        }
        
        log.debug("执行摘要DTO清理完成");
        return dto;
    }
    
    /**
     * 清理深度分析DTO中的重复内容
     */
    private DeepAnalysisDto cleanDeepAnalysisDto(DeepAnalysisDto dto) {
        if (dto == null) {
            return null;
        }
        
        log.debug("开始清理深度分析DTO中的重复内容");
        
        // 清理法律性质分析
        if (dto.getLegalNature() != null) {
            DeepAnalysisDto.LegalNatureAnalysis nature = dto.getLegalNature();
            if (nature.getContractType() != null) {
                nature.setContractType(contentValidator.removeDuplicateContent(nature.getContractType()));
            }
            if (nature.getGoverningLaws() != null) {
                nature.setGoverningLaws(contentValidator.removeDuplicateContent(nature.getGoverningLaws()));
            }
            if (nature.getLegalRelationship() != null) {
                nature.setLegalRelationship(contentValidator.removeDuplicateContent(nature.getLegalRelationship()));
            }
        }
        
        // 清理关键条款解读
        if (dto.getKeyClauses() != null) {
            for (DeepAnalysisDto.KeyClauseAnalysis clause : dto.getKeyClauses()) {
                if (clause.getClauseName() != null) {
                    clause.setClauseName(contentValidator.removeDuplicateContent(clause.getClauseName()));
                }
                if (clause.getInterpretation() != null) {
                    clause.setInterpretation(contentValidator.removeDuplicateContent(clause.getInterpretation()));
                }
                if (clause.getRisk() != null) {
                    clause.setRisk(contentValidator.removeDuplicateContent(clause.getRisk()));
                }
            }
        }
        
        // 清理风险评估
        if (dto.getRiskAssessments() != null) {
            for (DeepAnalysisDto.RiskAssessment risk : dto.getRiskAssessments()) {
                if (risk.getRiskCategory() != null) {
                    risk.setRiskCategory(contentValidator.removeDuplicateContent(risk.getRiskCategory()));
                }
                if (risk.getDescription() != null) {
                    risk.setDescription(contentValidator.removeDuplicateContent(risk.getDescription()));
                }
                if (risk.getPrevention() != null) {
                    risk.setPrevention(contentValidator.removeDuplicateContent(risk.getPrevention()));
                }
            }
        }
        
        // 清理合规性检查
        if (dto.getComplianceCheck() != null) {
            DeepAnalysisDto.ComplianceCheck check = dto.getComplianceCheck();
            if (check.getRegulation() != null) {
                check.setRegulation(contentValidator.removeDuplicateContent(check.getRegulation()));
            }
            if (check.getConformity() != null) {
                check.setConformity(contentValidator.removeDuplicateContent(check.getConformity()));
            }
            if (check.getGaps() != null) {
                check.setGaps(contentValidator.removeDuplicateContent(check.getGaps()));
            }
        }
        
        // 清理商业影响分析
        if (dto.getBusinessImpact() != null) {
            DeepAnalysisDto.BusinessImpactAnalysis impact = dto.getBusinessImpact();
            if (impact.getParty() != null) {
                impact.setParty(contentValidator.removeDuplicateContent(impact.getParty()));
            }
            if (impact.getImpact() != null) {
                impact.setImpact(contentValidator.removeDuplicateContent(impact.getImpact()));
            }
            if (impact.getFinancialImpact() != null) {
                impact.setFinancialImpact(contentValidator.removeDuplicateContent(impact.getFinancialImpact()));
            }
        }
        
        log.debug("深度分析DTO清理完成");
        return dto;
    }
    
    /**
     * 清理改进建议DTO中的重复内容
     */
    private ImprovementSuggestionDto cleanImprovementSuggestionDto(ImprovementSuggestionDto dto) {
        if (dto == null || dto.getSuggestions() == null) {
            return dto;
        }
        
        log.debug("开始清理改进建议DTO中的重复内容");
        
        // 清理每条建议
        for (ImprovementSuggestionDto.Suggestion suggestion : dto.getSuggestions()) {
            if (suggestion.getProblemDescription() != null) {
                suggestion.setProblemDescription(
                        contentValidator.removeDuplicateContent(suggestion.getProblemDescription()));
            }
            if (suggestion.getSuggestedModification() != null) {
                suggestion.setSuggestedModification(
                        contentValidator.removeDuplicateContent(suggestion.getSuggestedModification()));
            }
            if (suggestion.getExpectedEffect() != null) {
                suggestion.setExpectedEffect(
                        contentValidator.removeDuplicateContent(suggestion.getExpectedEffect()));
            }
        }
        
        log.debug("改进建议DTO清理完成");
        return dto;
    }
}

