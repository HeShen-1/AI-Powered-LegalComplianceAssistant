package com.river.LegalAssistant.util;

import com.river.LegalAssistant.dto.report.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;

/**
 * 报告内容验证和清洗工具
 * 
 * 用于确保AI生成的报告内容符合专业标准：
 * 1. 过滤无效内容（提示词模板、错误信息等）
 * 2. 清除冗余和重复文本
 * 3. 屏蔽内部技术信息
 * 4. 验证内容有效性
 */
@Component
@Slf4j
public class ReportContentValidator {

    // ==================== 验证规则配置 ====================
    
    /**
     * 不应出现在正式报告中的关键词（表明内容生成失败）
     */
    private static final List<String> INVALID_CONTENT_KEYWORDS = Arrays.asList(
        "我无法分析",
        "我无法理解",
        "请提供更多信息",
        "请提供",
        "作为AI模型",
        "作为人工智能",
        "作为语言模型",
        "我是一个AI",
        "我是一个大语言模型",
        "抱歉，我不能",
        "很抱歉",
        "无法完成此任务",
        "请重新输入",
        "输入不完整"
    );
    
    /**
     * 需要屏蔽的内部技术信息关键词
     */
    private static final List<String> INTERNAL_INFO_KEYWORDS = Arrays.asList(
        "DeepSeek",
        "OpenAI",
        "GPT-",
        "Claude",
        "prompt:",
        "Prompt:",
        "PROMPT:",
        "提示词:",
        "提示词：",
        "System:",
        "User:",
        "Assistant:",
        "temperature:",
        "max_tokens:",
        "top_p:",
        "API Key",
        "api_key",
        "模型名称:",
        "模型名称：",
        "调用参数"
    );
    
    /**
     * 提示词模板特征（表明返回了原始提示词）
     */
    private static final List<Pattern> PROMPT_TEMPLATE_PATTERNS = Arrays.asList(
        Pattern.compile("请.*生成.*报告", Pattern.CASE_INSENSITIVE),
        Pattern.compile("请.*分析.*合同", Pattern.CASE_INSENSITIVE),
        Pattern.compile("基于以下.*进行", Pattern.CASE_INSENSITIVE),
        Pattern.compile("\\{[^}]+\\}", Pattern.CASE_INSENSITIVE),  // {变量名}
        Pattern.compile("\\$\\{[^}]+\\}", Pattern.CASE_INSENSITIVE), // ${变量名}
        Pattern.compile("你需要.*输出", Pattern.CASE_INSENSITIVE),
        Pattern.compile("要求：\\s*\\d+\\.", Pattern.CASE_INSENSITIVE)
    );
    
    /**
     * 最小有效内容长度（字符数）
     */
    private static final int MIN_VALID_CONTENT_LENGTH = 50;
    
    /**
     * 最大连续重复行数阈值
     */
    private static final int MAX_CONSECUTIVE_DUPLICATES = 3;

    // ==================== 公共验证方法 ====================
    
    /**
     * 验证并清洗AI生成的内容
     * 
     * @param content 原始内容
     * @param contentType 内容类型（用于日志）
     * @return 清洗后的内容，如果内容无效则返回null
     */
    public String validateAndClean(String content, String contentType) {
        if (content == null || content.trim().isEmpty()) {
            log.warn("【内容验证】{} 内容为空", contentType);
            return null;
        }
        
        log.debug("【内容验证】开始验证 {} 内容，原始长度: {}", contentType, content.length());
        
        // 1. 检查是否包含无效内容关键词
        if (containsInvalidKeywords(content)) {
            log.warn("【内容验证失败】{} 包含无效关键词（如'我无法分析'等），判定为生成失败", contentType);
            return null;
        }
        
        // 2. 检查是否是提示词模板
        if (isPromptTemplate(content)) {
            log.warn("【内容验证失败】{} 疑似为提示词模板，判定为生成失败", contentType);
            return null;
        }
        
        // 3. 清洗内容
        String cleanedContent = cleanContent(content);
        
        // 4. 验证清洗后的内容长度
        if (cleanedContent == null || cleanedContent.length() < MIN_VALID_CONTENT_LENGTH) {
            log.warn("【内容验证失败】{} 清洗后内容过短（{}字符），判定为无效", 
                    contentType, cleanedContent != null ? cleanedContent.length() : 0);
            return null;
        }
        
        log.info("【内容验证成功】{} 验证通过，清洗后长度: {}", contentType, cleanedContent.length());
        return cleanedContent;
    }
    
    /**
     * 验证报告内容的完整性
     * 
     * @param executiveSummary 执行摘要
     * @param deepAnalysis AI深度分析
     * @param riskCount 风险点数量
     * @return 验证结果对象
     */
    public ValidationResult validateReportCompleteness(
            String executiveSummary, 
            String deepAnalysis,
            Integer riskCount) {
        
        ValidationResult result = new ValidationResult();
        result.setValid(true);
        
        // 1. 检查执行摘要
        if (executiveSummary == null || executiveSummary.trim().isEmpty()) {
            result.setValid(false);
            result.addError("执行摘要缺失或为空");
        } else if (executiveSummary.length() < 100) {
            result.setValid(false);
            result.addError("执行摘要内容过短（少于100字符），可能生成不完整");
        }
        
        // 2. 检查AI深度分析
        if (deepAnalysis == null || deepAnalysis.trim().isEmpty()) {
            result.addWarning("AI深度分析缺失，将使用基础分析模式");
        } else if (deepAnalysis.length() < 200) {
            result.addWarning("AI深度分析内容过短（少于200字符），可能生成不完整");
        }
        
        // 3. 检查风险点数量逻辑
        if (riskCount == null || riskCount < 0) {
            result.setValid(false);
            result.addError("风险点数量无效");
        }
        
        // 4. 记录验证结果
        if (!result.isValid()) {
            log.error("【报告完整性验证失败】发现 {} 个错误, {} 个警告", 
                    result.getErrors().size(), result.getWarnings().size());
            result.getErrors().forEach(error -> log.error("  - 错误: {}", error));
        } else if (!result.getWarnings().isEmpty()) {
            log.warn("【报告完整性验证通过但有警告】发现 {} 个警告", result.getWarnings().size());
            result.getWarnings().forEach(warning -> log.warn("  - 警告: {}", warning));
        } else {
            log.info("【报告完整性验证通过】所有必要内容齐全");
        }
        
        return result;
    }

    // ==================== 私有验证方法 ====================
    
    /**
     * 检查内容是否包含无效关键词
     */
    private boolean containsInvalidKeywords(String content) {
        String lowerContent = content.toLowerCase();
        for (String keyword : INVALID_CONTENT_KEYWORDS) {
            if (lowerContent.contains(keyword.toLowerCase())) {
                log.debug("发现无效关键词: {}", keyword);
                return true;
            }
        }
        return false;
    }
    
    /**
     * 检查内容是否是提示词模板
     */
    private boolean isPromptTemplate(String content) {
        // 检查是否匹配提示词模板特征
        for (Pattern pattern : PROMPT_TEMPLATE_PATTERNS) {
            if (pattern.matcher(content).find()) {
                log.debug("匹配到提示词模板特征: {}", pattern.pattern());
                return true;
            }
        }
        
        // 检查是否包含过多的变量占位符
        long placeholderCount = content.chars().filter(ch -> ch == '{' || ch == '}').count();
        if (placeholderCount > 6) { // 超过3对大括号
            log.debug("发现过多占位符（{}个），疑似模板", placeholderCount);
            return true;
        }
        
        return false;
    }
    
    /**
     * 清洗内容
     */
    private String cleanContent(String content) {
        if (content == null) {
            return null;
        }
        
        String cleaned = content;
        
        // 1. 移除内部技术信息
        cleaned = removeInternalInfo(cleaned);
        
        // 2. 去除过度冗余的重复内容
        cleaned = removeDuplicateLines(cleaned);
        
        // 3. 清理多余的空白
        cleaned = cleanWhitespace(cleaned);
        
        return cleaned;
    }
    
    /**
     * 移除内部技术信息
     */
    private String removeInternalInfo(String content) {
        String result = content;
        
        // 移除包含内部信息关键词的整行
        String[] lines = result.split("\n");
        StringBuilder sb = new StringBuilder();
        
        for (String line : lines) {
            boolean containsInternalInfo = false;
            String lowerLine = line.toLowerCase();
            
            for (String keyword : INTERNAL_INFO_KEYWORDS) {
                if (lowerLine.contains(keyword.toLowerCase())) {
                    containsInternalInfo = true;
                    log.debug("移除包含内部信息的行: {}", line.substring(0, Math.min(line.length(), 50)));
                    break;
                }
            }
            
            if (!containsInternalInfo) {
                sb.append(line).append("\n");
            }
        }
        
        return sb.toString();
    }
    
    /**
     * 移除重复行（保留最多MAX_CONSECUTIVE_DUPLICATES个连续重复）
     */
    private String removeDuplicateLines(String content) {
        String[] lines = content.split("\n");
        StringBuilder result = new StringBuilder();
        
        String previousLine = null;
        int duplicateCount = 0;
        
        for (String line : lines) {
            String trimmedLine = line.trim();
            
            if (trimmedLine.equals(previousLine)) {
                duplicateCount++;
                // 只保留前MAX_CONSECUTIVE_DUPLICATES个重复
                if (duplicateCount <= MAX_CONSECUTIVE_DUPLICATES) {
                    result.append(line).append("\n");
                } else {
                    log.debug("跳过重复行（第{}次）: {}", duplicateCount, 
                            line.substring(0, Math.min(line.length(), 30)));
                }
            } else {
                duplicateCount = 1;
                result.append(line).append("\n");
                previousLine = trimmedLine;
            }
        }
        
        return result.toString();
    }
    
    /**
     * 清理多余的空白字符
     */
    private String cleanWhitespace(String content) {
        return content
            // 合并多个连续空行为最多2个空行
            .replaceAll("\n{4,}", "\n\n\n")
            // 移除行尾空白
            .replaceAll("[ \t]+\n", "\n")
            // 合并多个空格为一个
            .replaceAll(" {2,}", " ")
            .trim();
    }
    
    /**
     * 移除文本中的重复内容片段（改进版）
     * 检测并移除AI模型可能生成的重复文本片段
     * 支持检测非连续的重复内容
     */
    public String removeDuplicateContent(String content) {
        if (content == null || content.trim().isEmpty()) {
            return content;
        }
        
        // 先按句子分割
        String[] sentences = content.split("[。！？；\\n]+");
        if (sentences.length <= 1) {
            return content;
        }
        
        // 使用Set记录已经出现的句子，用于去重
        java.util.Set<String> seenSentences = new java.util.LinkedHashSet<>();
        StringBuilder result = new StringBuilder();
        int removedCount = 0;
        
        for (String sentence : sentences) {
            String trimmedSentence = sentence.trim();
            
            // 跳过空句子
            if (trimmedSentence.isEmpty() || trimmedSentence.length() < 5) {
                continue;
            }
            
            // 标准化句子（移除多余空格）用于比较
            String normalizedSentence = trimmedSentence.replaceAll("\\s+", " ");
            
            // 如果这个句子已经出现过，跳过（非连续重复检测）
            if (seenSentences.contains(normalizedSentence)) {
                removedCount++;
                if (log.isDebugEnabled() && removedCount <= 5) {
                    log.debug("移除重复句子[{}]: {}", removedCount, 
                            trimmedSentence.substring(0, Math.min(trimmedSentence.length(), 50)));
                }
                continue;
            }
            
            // 添加到已见集合
            seenSentences.add(normalizedSentence);
            
            // 添加到结果
            result.append(trimmedSentence).append("。");
        }
        
        String cleanedContent = result.toString();
        
        // 如果清理后的内容过短（移除了超过70%的内容），可能是误判，返回原始内容
        if (cleanedContent.length() < content.length() * 0.3) {
            log.warn("去重后内容过短，可能存在误判。原始长度: {}, 清理后长度: {}, 移除句子数: {}", 
                    content.length(), cleanedContent.length(), removedCount);
            return content;
        }
        
        if (removedCount > 0) {
            log.info("成功移除 {} 个重复句子，原始长度: {}, 清理后长度: {}", 
                    removedCount, content.length(), cleanedContent.length());
        }
        
        return cleanedContent;
    }
    
    /**
     * 移除文本中的重复内容片段（改进版）
     * 专门处理AI模型生成的重复文本
     */
    public String removeDuplicateContentImproved(String content) {
        if (content == null || content.trim().isEmpty()) {
            return content;
        }
        
        // 检测连续重复的文本片段
        String result = content;
        
        // 查找重复的文本模式（至少重复3次）
        String[] words = content.split("\\s+");
        if (words.length > 10) {
            // 检查是否有重复的词语序列
            for (int i = 0; i < words.length - 5; i++) {
                String segment = words[i];
                int repeatCount = 1;
                
                // 计算这个词语重复的次数
                for (int j = i + 1; j < words.length; j++) {
                    if (words[j].equals(segment)) {
                        repeatCount++;
                    } else {
                        break;
                    }
                }
                
                // 如果重复次数超过3次，移除多余的重复
                if (repeatCount > 3) {
                    String repeatedText = segment;
                    for (int k = 1; k < repeatCount; k++) {
                        repeatedText += segment;
                    }
                    
                    // 只保留一次
                    String replacement = segment;
                    result = result.replace(repeatedText, replacement);
                    log.debug("移除重复文本: {} (重复{}次)", segment, repeatCount);
                }
            }
        }
        
        return result;
    }
    
    // ==================== DTO验证方法 ====================
    
    /**
     * 验证执行摘要DTO
     */
    public boolean validateExecutiveSummary(ExecutiveSummaryDto dto) {
        if (dto == null) {
            log.warn("【DTO验证】执行摘要DTO为null");
            return false;
        }
        
        // 检查核心字段
        if (dto.getContractType() == null || dto.getContractType().trim().isEmpty()) {
            log.warn("【DTO验证失败】执行摘要缺少合同类型");
            return false;
        }
        
        if (dto.getRiskLevel() == null || dto.getRiskLevel().trim().isEmpty()) {
            log.warn("【DTO验证失败】执行摘要缺少风险等级");
            return false;
        }
        
        if (dto.getRiskReason() == null || dto.getRiskReason().trim().isEmpty()) {
            log.warn("【DTO验证失败】执行摘要缺少风险理由");
            return false;
        }
        
        if (dto.getCoreRisks() == null || dto.getCoreRisks().isEmpty()) {
            log.warn("【DTO验证失败】执行摘要缺少核心风险点");
            return false;
        }
        
        if (dto.getActionSuggestions() == null || dto.getActionSuggestions().isEmpty()) {
            log.warn("【DTO验证失败】执行摘要缺少行动建议");
            return false;
        }
        
        // 检查内容质量
        if (dto.getContractType().length() < 4) {
            log.warn("【DTO验证失败】合同类型过短: {}", dto.getContractType());
            return false;
        }
        
        if (dto.getRiskReason().length() < 20) {
            log.warn("【DTO验证失败】风险理由过短（少于20字符）");
            return false;
        }
        
        log.info("【DTO验证成功】执行摘要验证通过");
        return true;
    }
    
    /**
     * 验证深度分析DTO
     */
    public boolean validateDeepAnalysis(DeepAnalysisDto dto) {
        if (dto == null) {
            log.warn("【DTO验证】深度分析DTO为null，将使用降级方案");
            return false;
        }
        
        // 至少需要法律性质分析或关键条款分析之一
        boolean hasLegalNature = dto.getLegalNature() != null && 
                dto.getLegalNature().getContractType() != null &&
                !dto.getLegalNature().getContractType().trim().isEmpty();
        
        boolean hasKeyClauses = dto.getKeyClauses() != null && 
                !dto.getKeyClauses().isEmpty();
        
        if (!hasLegalNature && !hasKeyClauses) {
            log.warn("【DTO验证失败】深度分析缺少核心内容（法律性质或关键条款）");
            return false;
        }
        
        log.info("【DTO验证成功】深度分析验证通过");
        return true;
    }
    
    /**
     * 验证改进建议DTO
     */
    public boolean validateImprovementSuggestions(ImprovementSuggestionDto dto) {
        if (dto == null) {
            log.warn("【DTO验证】改进建议DTO为null");
            return false;
        }
        
        if (dto.getSuggestions() == null || dto.getSuggestions().isEmpty()) {
            log.warn("【DTO验证失败】改进建议列表为空");
            return false;
        }
        
        // 检查每条建议的完整性
        for (ImprovementSuggestionDto.Suggestion suggestion : dto.getSuggestions()) {
            if (suggestion.getProblemDescription() == null || 
                suggestion.getProblemDescription().trim().isEmpty()) {
                log.warn("【DTO验证失败】改进建议缺少问题描述");
                return false;
            }
            
            if (suggestion.getSuggestedModification() == null || 
                suggestion.getSuggestedModification().trim().isEmpty()) {
                log.warn("【DTO验证失败】改进建议缺少修改建议");
                return false;
            }
        }
        
        log.info("【DTO验证成功】改进建议验证通过，包含{}条建议", dto.getSuggestions().size());
        return true;
    }
    
    // ==================== 验证结果类 ====================
    
    /**
     * 验证结果
     */
    public static class ValidationResult {
        private boolean valid;
        private List<String> errors = new java.util.ArrayList<>();
        private List<String> warnings = new java.util.ArrayList<>();
        
        public boolean isValid() {
            return valid;
        }
        
        public void setValid(boolean valid) {
            this.valid = valid;
        }
        
        public List<String> getErrors() {
            return errors;
        }
        
        public List<String> getWarnings() {
            return warnings;
        }
        
        public void addError(String error) {
            this.errors.add(error);
        }
        
        public void addWarning(String warning) {
            this.warnings.add(warning);
        }
        
        public String getErrorMessage() {
            if (errors.isEmpty()) {
                return null;
            }
            return String.join("; ", errors);
        }
        
        public String getSummary() {
            return String.format("验证%s: %d个错误, %d个警告", 
                valid ? "通过" : "失败", errors.size(), warnings.size());
        }
    }
}

