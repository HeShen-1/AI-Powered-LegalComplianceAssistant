package com.river.LegalAssistant.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 提示词模板管理服务
 * 
 * 集中管理所有AI提示词模板，支持：
 * - 模板加载和缓存
 * - 动态变量填充
 * - 模板版本管理
 * - 模板热重载（可选）
 * 
 * 从多个服务中提取提示词管理逻辑，遵循DRY原则
 * 为未来的高级Prompt工程（如Prompt链、Few-shot学习）打下基础
 * 
 * @author LegalAssistant Team
 * @since 2025-10-02
 */
@Service
@Slf4j
public class PromptTemplateService {
    
    // 模板缓存
    private final Map<String, String> templateCache = new ConcurrentHashMap<>();
    
    // 模板变量占位符的正则表达式: {variableName}
    // 只匹配简单标识符（字母、数字、下划线），避免匹配JSON示例中的复杂内容
    private static final Pattern VARIABLE_PATTERN = Pattern.compile("\\{([a-zA-Z_][a-zA-Z0-9_]*)\\}");
    
    // 从配置文件加载的模板
    @Value("classpath:prompts/legal-qa.st")
    private Resource legalQaTemplateResource;
    
    @Value("classpath:prompts/contract-risk-analysis.st")
    private Resource contractRiskTemplateResource;
    
    @Value("classpath:prompts/contract-risk-analysis-structured.st")
    private Resource contractRiskStructuredTemplateResource;
    
    @Value("classpath:prompts/pdf-report-generation.st")
    private Resource pdfReportTemplateResource;
    
    @Value("classpath:prompts/executive-summary.st")
    private Resource executiveSummaryTemplateResource;
    
    @Value("classpath:prompts/deep-analysis.st")
    private Resource deepAnalysisTemplateResource;
    
    @Value("classpath:prompts/improvement-suggestions.st")
    private Resource improvementSuggestionsTemplateResource;
    
    @Value("classpath:prompts/key-clauses-analysis.st")
    private Resource keyClausesAnalysisTemplateResource;
    
    @Value("classpath:prompts/risk-assessment.st")
    private Resource riskAssessmentTemplateResource;
    
    @Value("classpath:prompts/default-analysis.st")
    private Resource defaultAnalysisTemplateResource;
    
    @Value("classpath:prompts/system-legal-basic.st")
    private Resource systemLegalBasicTemplateResource;
    
    @Value("classpath:prompts/system-legal-advanced.st")
    private Resource systemLegalAdvancedTemplateResource;
    
    @Value("classpath:prompts/rag-with-context.st")
    private Resource ragWithContextTemplateResource;
    
    @Value("classpath:prompts/contract-review-simple.st")
    private Resource contractReviewSimpleTemplateResource;
    
    /**
     * 初始化：加载所有模板到缓存
     */
    @PostConstruct
    public void init() {
        log.info("开始初始化提示词模板服务...");
        
        try {
            // 加载内置模板
            loadTemplate("legal-qa", legalQaTemplateResource);
            loadTemplate("contract-risk-analysis", contractRiskTemplateResource);
            loadTemplate("contract-risk-analysis-structured", contractRiskStructuredTemplateResource);
            loadTemplate("pdf-report-generation", pdfReportTemplateResource);
            loadTemplate("executive-summary", executiveSummaryTemplateResource);
            loadTemplate("deep-analysis", deepAnalysisTemplateResource);
            loadTemplate("improvement-suggestions", improvementSuggestionsTemplateResource);
            loadTemplate("key-clauses-analysis", keyClausesAnalysisTemplateResource);
            loadTemplate("risk-assessment", riskAssessmentTemplateResource);
            loadTemplate("default-analysis", defaultAnalysisTemplateResource);
            loadTemplate("system-legal-basic", systemLegalBasicTemplateResource);
            loadTemplate("system-legal-advanced", systemLegalAdvancedTemplateResource);
            loadTemplate("rag-with-context", ragWithContextTemplateResource);
            loadTemplate("contract-review-simple", contractReviewSimpleTemplateResource);
            
            log.info("提示词模板服务初始化完成，共加载 {} 个模板", templateCache.size());
            
        } catch (Exception e) {
            log.error("提示词模板服务初始化失败", e);
        }
    }
    
    /**
     * 加载默认的系统提示语（已废弃，提示词已迁移到模板文件）
     */
    @Deprecated
    private void loadDefaultSystemPrompts() {
        // 提示词已迁移到模板文件，此方法保留用于向后兼容
        log.info("系统提示语已从模板文件加载，默认提示语方法已废弃");
    }
    
    /**
     * 从资源文件加载模板
     */
    private void loadTemplate(String templateName, Resource resource) {
        try {
            if (resource == null || !resource.exists()) {
                log.warn("模板资源不存在: {}", templateName);
                return;
            }
            
            // 使用流式处理避免大文件内存问题
            StringBuilder contentBuilder = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(resource.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    contentBuilder.append(line).append("\n");
                }
            }
            String content = contentBuilder.toString();
            
            templateCache.put(templateName, content);
            log.debug("模板加载成功: {} ({}字符)", templateName, content.length());
            
        } catch (IOException e) {
            log.error("加载模板失败: {}", templateName, e);
        }
    }
    
    /**
     * 获取模板原始内容
     * 
     * @param templateName 模板名称
     * @return 模板内容，如果不存在返回null
     */
    public String getTemplate(String templateName) {
        String template = templateCache.get(templateName);
        
        if (template == null) {
            log.warn("请求的模板不存在: {}", templateName);
        }
        
        return template;
    }
    
    /**
     * 渲染模板：将变量填充到模板中
     * 
     * @param templateName 模板名称
     * @param variables 变量Map，key为变量名，value为变量值
     * @return 填充后的内容
     */
    public String render(String templateName, Map<String, Object> variables) {
        String template = getTemplate(templateName);
        
        if (template == null) {
            log.error("无法渲染模板，模板不存在: {}", templateName);
            return "";
        }
        
        return fillVariables(template, variables);
    }
    
    /**
     * 使用变量填充模板内容
     * 支持 {variableName} 格式的占位符
     * 
     * @param template 模板内容
     * @param variables 变量Map
     * @return 填充后的内容
     */
    private String fillVariables(String template, Map<String, Object> variables) {
        if (variables == null || variables.isEmpty()) {
            return template;
        }
        
        Matcher matcher = VARIABLE_PATTERN.matcher(template);
        StringBuffer result = new StringBuffer();
        
        while (matcher.find()) {
            String varName = matcher.group(1);
            Object value = variables.get(varName);
            
            if (value != null) {
                // 转义特殊字符以防止正则表达式问题
                String replacement = Matcher.quoteReplacement(value.toString());
                matcher.appendReplacement(result, replacement);
            } else {
                log.warn("模板变量未提供: {}", varName);
                // 保持原样
                matcher.appendReplacement(result, Matcher.quoteReplacement(matcher.group(0)));
            }
        }
        
        matcher.appendTail(result);
        return result.toString();
    }
    
    /**
     * 注册新模板（运行时动态添加）
     * 
     * @param templateName 模板名称
     * @param templateContent 模板内容
     */
    public void registerTemplate(String templateName, String templateContent) {
        if (templateName == null || templateName.trim().isEmpty()) {
            throw new IllegalArgumentException("模板名称不能为空");
        }
        
        if (templateContent == null || templateContent.trim().isEmpty()) {
            throw new IllegalArgumentException("模板内容不能为空");
        }
        
        templateCache.put(templateName, templateContent);
        log.info("动态注册模板: {}", templateName);
    }
    
    /**
     * 检查模板是否存在
     */
    public boolean hasTemplate(String templateName) {
        return templateCache.containsKey(templateName);
    }
    
    /**
     * 获取所有已加载的模板名称
     */
    public Set<String> getTemplateNames() {
        return new HashSet<>(templateCache.keySet());
    }
    
    /**
     * 重新加载指定模板（热重载）
     */
    public void reloadTemplate(String templateName) {
        // 这里可以实现从文件系统或数据库重新加载模板
        log.info("重新加载模板: {}", templateName);
        
        // TODO: 根据实际需求实现热重载逻辑
    }
    
    /**
     * 清空模板缓存
     */
    public void clearCache() {
        templateCache.clear();
        log.info("模板缓存已清空");
    }
    
    /**
     * 获取模板统计信息
     */
    public Map<String, Object> getStatistics() {
        Map<String, Object> stats = new HashMap<>();
        stats.put("totalTemplates", templateCache.size());
        stats.put("templateNames", getTemplateNames());
        stats.put("timestamp", java.time.LocalDateTime.now());
        return stats;
    }
    
    // ==================== 便捷方法：常用模板的快速访问 ====================
    
    /**
     * 获取基础法律助手系统提示语
     */
    public String getBasicLegalSystemPrompt() {
        return getTemplate("system-legal-basic");
    }
    
    /**
     * 获取高级法律助手系统提示语
     */
    public String getAdvancedLegalSystemPrompt() {
        return getTemplate("system-legal-advanced");
    }
    
    /**
     * 渲染RAG提示语
     * 
     * @param context 知识库上下文
     * @param question 用户问题
     * @return 渲染后的提示语
     */
    public String renderRagPrompt(String context, String question) {
        return render("rag-with-context", Map.of(
            "context", context,
            "question", question
        ));
    }
    
    /**
     * 渲染合同审查提示语
     * 
     * @param contractContent 合同内容
     * @return 渲染后的提示语
     */
    public String renderContractReviewPrompt(String contractContent) {
        return render("contract-review-simple", Map.of(
            "contractContent", contractContent
        ));
    }
    
    /**
     * 构建带上下文的法律问答提示语
     * 
     * @param context 法律知识上下文
     * @param question 用户问题
     * @return 完整的提示语
     */
    public String buildLegalQAPrompt(String context, String question) {
        // 如果存在legal-qa模板，使用它
        if (hasTemplate("legal-qa")) {
            return render("legal-qa", Map.of(
                "context", context,
                "question", question
            ));
        }
        
        // 否则使用默认的RAG模板
        return renderRagPrompt(context, question);
    }
}

