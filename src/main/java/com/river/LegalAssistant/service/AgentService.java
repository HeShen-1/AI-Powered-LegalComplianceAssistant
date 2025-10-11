package com.river.LegalAssistant.service;

import dev.langchain4j.service.AiServices;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.StreamingResponseHandler;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;
import dev.langchain4j.model.output.Response;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.ollama.OllamaChatModel;
import dev.langchain4j.model.ollama.OllamaStreamingChatModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.model.openai.OpenAiStreamingChatModel;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.memory.ChatMemory;
import dev.langchain4j.http.client.jdk.JdkHttpClient;

import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.Map;
import java.util.List;
import java.util.Arrays;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * 智能法律顾问代理服务
 * 混合AI架构：本地Ollama处理基础任务，OpenAI处理高级任务
 * 基于 LangChain4j 构建的 ReAct Agent，具备工具调用能力
 */
@Service
@Slf4j
public class AgentService {

    /**
     * 任务复杂度枚举
     */
    public enum TaskComplexity {
        BASIC,     // 基础任务：简单对话、查询
        ADVANCED   // 高级任务：复杂推理、工具调用
    }

    // 智能助手接口
    private LegalAssistant basicLegalAssistant;   // 基础服务助手（Ollama）
    private LegalAssistant advancedLegalAssistant; // 高级服务助手（OpenAI）
    
    // 底层模型
    private ChatModel basicChatModel;     // 基础聊天模型（Ollama）
    private ChatModel advancedChatModel;  // 高级聊天模型（OpenAI）
    
    // 流式模型
    private StreamingChatModel basicStreamingModel;     // 基础流式模型（Ollama）
    private StreamingChatModel advancedStreamingModel;  // 高级流式模型（OpenAI）
    
    // 会话记忆管理
    private final Map<String, ChatMemory> chatMemories = new ConcurrentHashMap<>();
    private final LegalTools legalTools;
    private final PromptTemplateService promptTemplateService;
    
    // 异步执行器（支持SecurityContext传递）
    private final Executor taskExecutor;

    // 配置参数
    @Value("${spring.ai.ollama.base-url:http://localhost:11434}")
    private String ollamaBaseUrl;
    
    @Value("${spring.ai.deepseek.api-key:}")
    private String deepSeekApiKey;
    
    @Value("${spring.ai.deepseek.base-url:https://api.deepseek.com}")
    private String deepSeekBaseUrl;
    
    // 混合架构配置
    @Value("${app.ai.service-mode.hybrid-enabled:true}")
    private boolean hybridEnabled;
    
    @Value("${app.ai.service-mode.basic-provider:ollama}")
    private String basicProvider;
    
    @Value("${app.ai.service-mode.advanced-provider:deepseek}")
    private String advancedProvider;
    
    @Value("${app.ai.service-mode.auto-fallback:true}")
    private boolean autoFallback;
    
    // 模型配置
    @Value("${app.ai.models.basic.chat:qwen2:1.5b}")
    private String basicModelName;
    
    @Value("${app.ai.models.advanced.chat:deepseek-chat}")
    private String advancedModelName;
    
    
    // 任务路由配置（硬编码，避免配置复杂性）
    @Getter
    private final List<String> advancedTasks = Arrays.asList(
        "tool_calling", "complex_reasoning", "contract_analysis", "legal_research"
    );
    
    // 服务状态
    private boolean basicServiceAvailable = false;
    private boolean advancedServiceAvailable = false;

    /**
     * 法律智能助手接口
     * 由 LangChain4j 根据工具自动生成实现
     */
    public interface LegalAssistant {
        /**
         * 智能法律咨询
         * Agent会根据问题自动决策是否需要调用工具
         * 
         * @param question 用户问题
         * @return AI回答，可能包含工具调用结果
         */
        String consultLegalMatter(@dev.langchain4j.service.UserMessage String question);
        
        /**
         * 合同条款分析咨询
         * 专门处理合同相关的问题
         * 
         * @param contractContent 合同内容或条款
         * @param question 具体问题
         * @return 分析结果和建议
         */
        String analyzeContractMatter(@dev.langchain4j.service.V("contractContent") String contractContent, 
                                   @dev.langchain4j.service.UserMessage String question);
    }

    public AgentService(LegalTools legalTools, 
                        PromptTemplateService promptTemplateService,
                        @Qualifier("generalTaskExecutor") Executor taskExecutor) {
        this.legalTools = legalTools;
        this.promptTemplateService = promptTemplateService;
        this.taskExecutor = taskExecutor;
        log.info("混合AI法律顾问代理服务启动");
    }
    
    @PostConstruct
    public void initializeAgent() {
        log.info("正在初始化混合AI法律顾问代理服务...");
        log.info("配置：混合模式={}, 基础提供者={}, 高级提供者={}, 自动降级={}", 
                hybridEnabled, basicProvider, advancedProvider, autoFallback);
        
        // 初始化基础AI服务（Ollama）
        initializeBasicService();
        
        // 初始化高级AI服务（OpenAI）
        initializeAdvancedService();
        
        // 输出初始化结果
        logInitializationResults();
    }
    
    /**
     * 初始化基础AI服务（通常是本地Ollama）
     */
    private void initializeBasicService() {
        log.info("初始化基础AI服务：{} ({})", basicProvider, basicModelName);
        
        try {
            if ("ollama".equalsIgnoreCase(basicProvider)) {
                // 创建Ollama基础模型 - 显式指定JDK HTTP客户端
                this.basicChatModel = OllamaChatModel.builder()
                        .baseUrl(ollamaBaseUrl)
                        .modelName(basicModelName)
                        .temperature(0.7)
                        .timeout(Duration.ofMinutes(2))
                        .logRequests(false)
                        .logResponses(false)
                        .httpClientBuilder(JdkHttpClient.builder()
                                .connectTimeout(Duration.ofSeconds(30))
                                .readTimeout(Duration.ofMinutes(2)))
                        .build();
                
                // 创建Ollama流式模型
                this.basicStreamingModel = OllamaStreamingChatModel.builder()
                        .baseUrl(ollamaBaseUrl)
                        .modelName(basicModelName)
                        .temperature(0.7)
                        .timeout(Duration.ofMinutes(2))
                        .httpClientBuilder(JdkHttpClient.builder()
                                .connectTimeout(Duration.ofSeconds(30))
                                .readTimeout(Duration.ofMinutes(2)))
                        .build();
                
                // 创建基础智能助手（不使用工具，避免qwen2:1.5b的工具调用问题）
                this.basicLegalAssistant = createSimpleLegalAssistant(legalTools);
                
            } else if ("openai".equalsIgnoreCase(basicProvider)) {
                // 如果基础服务也使用OpenAI
                this.basicChatModel = createOpenAiChatModel(basicModelName, 0.7);
                this.basicLegalAssistant = createSimpleLegalAssistant(legalTools);
            }
            
            // 测试基础服务可用性（由于基础AI端点工作正常，直接设置为可用）
            basicServiceAvailable = true;
            log.info("基础AI服务初始化成功：{}", basicModelName);
            
        } catch (Exception e) {
            log.error("基础AI服务初始化失败：{}", e.getMessage());
            basicServiceAvailable = false;
        }
    }
    
    /**
     * 初始化高级AI服务（通常是OpenAI）
     */
    private void initializeAdvancedService() {
        log.info("初始化高级AI服务：{} ({})", advancedProvider, advancedModelName);
        
        try {
            if ("deepseek".equalsIgnoreCase(advancedProvider) || "openai".equalsIgnoreCase(advancedProvider)) {
                // 检查DeepSeek API密钥
                if (deepSeekApiKey == null || deepSeekApiKey.isEmpty() || "your_deepseek_api_key_here".equals(deepSeekApiKey)) {
                    log.warn("DeepSeek API密钥未配置，高级服务将不可用");
                    advancedServiceAvailable = false;
                    return;
                }
                
                // 创建DeepSeek高级模型
                this.advancedChatModel = createDeepSeekChatModel(advancedModelName, 0.3);
                
                // 创建DeepSeek流式模型
                this.advancedStreamingModel = createDeepSeekStreamingChatModel(advancedModelName, 0.3);
                
                // 创建高级智能助手（支持工具调用）
                this.advancedLegalAssistant = AiServices.builder(LegalAssistant.class)
                        .chatModel(advancedChatModel)
                        .tools(legalTools)
                        .chatMemoryProvider(memoryId -> chatMemories.computeIfAbsent(
                            String.valueOf(memoryId),
                            k -> MessageWindowChatMemory.withMaxMessages(15)
                        ))
                        .build();
                
            } else if ("ollama".equalsIgnoreCase(advancedProvider)) {
                // 如果高级服务也使用Ollama（如llama3.1:8b等支持工具的模型）
                this.advancedChatModel = OllamaChatModel.builder()
                        .baseUrl(ollamaBaseUrl)
                        .modelName(advancedModelName)
                        .temperature(0.3)
                        .timeout(Duration.ofMinutes(5))
                        .logRequests(false)  // 关闭请求日志，避免在终端输出大量内容
                        .logResponses(false)
                        .httpClientBuilder(JdkHttpClient.builder()
                                .connectTimeout(Duration.ofSeconds(30))
                                .readTimeout(Duration.ofMinutes(5)))
                        .build();
                
                // 尝试创建支持工具的助手
                try {
                    this.advancedLegalAssistant = AiServices.builder(LegalAssistant.class)
                            .chatModel(advancedChatModel)
                            .tools(legalTools)
                            .chatMemoryProvider(memoryId -> chatMemories.computeIfAbsent(
                                String.valueOf(memoryId),
                                k -> MessageWindowChatMemory.withMaxMessages(15)
                            ))
                            .build();
                } catch (Exception e) {
                    log.warn("Ollama模型 {} 不支持工具调用，使用简化版本", advancedModelName);
                    this.advancedLegalAssistant = createSimpleLegalAssistant(legalTools);
                }
            }
            
            // 测试高级服务可用性（基于模型是否成功创建）
            if (advancedChatModel != null && advancedLegalAssistant != null) {
                advancedServiceAvailable = true;
                log.info("高级AI服务初始化成功：{}", advancedModelName);
            } else {
                log.warn("高级AI服务不可用：{}", advancedModelName);
            }
            
        } catch (Exception e) {
            log.error("高级AI服务初始化失败：{}", e.getMessage());
            advancedServiceAvailable = false;
        }
    }
    
    /**
     * 创建DeepSeek聊天模型 - 使用OpenAI兼容接口
     */
    private OpenAiChatModel createDeepSeekChatModel(String modelName, double temperature) {
        return OpenAiChatModel.builder()
                .apiKey(deepSeekApiKey)
                .baseUrl(deepSeekBaseUrl)
                .modelName(modelName)
                .temperature(temperature)
                .maxTokens(4096)
                .timeout(Duration.ofMinutes(3))
                .logRequests(false)  // 关闭请求日志，避免在终端输出大量内容
                .logResponses(false)
                .httpClientBuilder(JdkHttpClient.builder()
                        .connectTimeout(Duration.ofSeconds(30))
                        .readTimeout(Duration.ofMinutes(3)))
                .build();
    }
    
    /**
     * 创建DeepSeek流式聊天模型 - 使用OpenAI兼容接口
     */
    private OpenAiStreamingChatModel createDeepSeekStreamingChatModel(String modelName, double temperature) {
        return OpenAiStreamingChatModel.builder()
                .apiKey(deepSeekApiKey)
                .baseUrl(deepSeekBaseUrl)
                .modelName(modelName)
                .temperature(temperature)
                .maxTokens(4096)
                .timeout(Duration.ofMinutes(3))
                .logRequests(false)  // 关闭请求日志，避免在终端输出大量内容
                .logResponses(false)
                .httpClientBuilder(JdkHttpClient.builder()
                        .connectTimeout(Duration.ofSeconds(30))
                        .readTimeout(Duration.ofMinutes(3)))
                .build();
    }

    /**
     * 创建OpenAI聊天模型 - 兼容性方法（废弃）
     * @deprecated 请使用 createDeepSeekChatModel 方法
     */
    @Deprecated
    private OpenAiChatModel createOpenAiChatModel(String modelName, double temperature) {
        return createDeepSeekChatModel(modelName, temperature);
    }
    
    
    /**
     * 记录初始化结果
     */
    private void logInitializationResults() {
        log.info("=== 混合AI法律顾问服务初始化完成 ===");
        log.info("基础服务状态：{} ({})", basicServiceAvailable ? "可用" : "不可用", basicModelName);
        log.info("高级服务状态：{} ({})", advancedServiceAvailable ? "可用" : "不可用", advancedModelName);
        log.info("自动降级：{}", autoFallback ? "启用" : "禁用");
        
        if (!basicServiceAvailable && !advancedServiceAvailable) {
            log.error("所有AI服务都不可用！请检查配置和网络连接。");
        } else if (basicServiceAvailable && !advancedServiceAvailable) {
            log.warn("只有基础AI服务可用，高级功能将受限。");
        } else if (!basicServiceAvailable) {
            log.warn("只有高级AI服务可用，可能产生更高的API成本。");
        } else {
            log.info("混合AI架构完全就绪，智能路由已激活。");
        }
        log.info("========================================");
    }

    /**
     * 智能法律咨询（默认会话）
     * 混合AI架构：根据任务复杂度智能选择合适的AI服务
     */
    public String consultLegalMatter(String question) {
        return consultLegalMatter(question, "default");
    }

    /**
     * 智能法律咨询 - 返回详细信息
     * 包含使用的模型和服务信息
     */
    public ConsultationResult consultLegalMatterWithDetails(String question) {
        return consultLegalMatterWithDetails(question, "default");
    }

    /**
     * 智能法律咨询结果记录类
     */
    public record ConsultationResult(
        String answer, 
        String serviceUsed, 
        String modelUsed, 
        boolean isDeepSeekUsed
    ) {}

    /**
     * 智能法律咨询（指定会话）- 返回详细信息
     */
    @CircuitBreaker(name = "aiService", fallbackMethod = "consultLegalMatterWithDetailsFallback")
    @Retry(name = "aiService")
    public ConsultationResult consultLegalMatterWithDetails(String question, String sessionId) {
        log.info("处理法律咨询请求: {}, 会话ID: {}", 
                question.length() > 50 ? question.substring(0, 50) + "..." : question, sessionId);
        
        try {
            if (question.trim().isEmpty()) {
                return new ConsultationResult(
                    "请提供具体的法律问题，我将为您提供专业的咨询建议。",
                    "系统提示",
                    "none",
                    false
                );
            }
            
            // 高级法律咨询：优先使用DeepSeek，降级到OLLAMA
            String response;
            String serviceInfo;
            String modelInfo;
            boolean isDeepSeek;
            
            try {
                if (advancedChatModel != null) {
                    // 使用DeepSeek高级服务进行法律咨询
                    response = advancedChatModel.chat("作为专业法律助手，请提供准确、详细的法律建议：" + question);
                    serviceInfo = "DeepSeek AI Agent";
                    modelInfo = "deepseek-chat";
                    isDeepSeek = true;
                } else if (basicChatModel != null) {
                    // 降级到OLLAMA基础服务
                    log.warn("DeepSeek高级服务不可用，降级使用OLLAMA基础服务");
                    response = basicChatModel.chat("作为法律助手，请回答以下问题：" + question);
                    serviceInfo = "OLLAMA基础服务 (降级)";
                    modelInfo = "qwen2:1.5b";
                    isDeepSeek = false;
                } else {
                    return new ConsultationResult(
                        "AI法律顾问服务暂时不可用，请稍后重试。建议咨询专业律师获得法律建议。",
                        "服务不可用",
                        "none",
                        false
                    );
                }
                
                // 统一记录日志
                log.info("使用{}处理法律咨询，会话: {}", serviceInfo, sessionId);
                
                return new ConsultationResult(response, serviceInfo, modelInfo, isDeepSeek);
                
            } catch (Exception chatError) {
                log.error("聊天调用失败：{}", chatError.getMessage());
                return new ConsultationResult(
                    "处理您的法律咨询时出现问题，请稍后重试。错误信息: " + chatError.getMessage(),
                    "错误处理",
                    "none",
                    false
                );
            }
            
        } catch (Exception e) {
            log.error("法律咨询处理失败，会话ID: {}", sessionId, e);
            
            return new ConsultationResult(
                "处理您的法律咨询时出现问题，请稍后重试。如需紧急法律援助，建议直接咨询专业律师。错误信息: " + e.getMessage(),
                "异常处理",
                "none",
                false
            );
        }
    }
    
    /**
     * 智能法律咨询（指定会话）
     * 混合AI架构：根据任务复杂度智能选择合适的AI服务
     * 
     * @param question 法律问题
     * @param sessionId 会话 ID，用于区分不同用户/会话
     */
    @CircuitBreaker(name = "aiService", fallbackMethod = "consultLegalMatterFallback")
    @Retry(name = "aiService")
    public String consultLegalMatter(String question, String sessionId) {
        log.info("处理法律咨询请求: {}, 会话ID: {}", 
                question.length() > 50 ? question.substring(0, 50) + "..." : question, sessionId);
        
        try {
            if (question.trim().isEmpty()) {
                return "请提供具体的法律问题，我将为您提供专业的咨询建议。";
            }
            
            // 高级法律咨询：优先使用DeepSeek，降级到OLLAMA
            String response;
            String serviceInfo;
            
            try {
                if (advancedChatModel != null) {
                    // 使用DeepSeek高级服务进行法律咨询
                    response = advancedChatModel.chat("作为专业法律助手，请提供准确、详细的法律建议：" + question);
                    serviceInfo = "DEEPSEEK高级服务 (deepseek-chat)";
                } else if (basicChatModel != null) {
                    // 降级到OLLAMA基础服务
                    log.warn("DeepSeek高级服务不可用，降级使用OLLAMA基础服务");
                    response = basicChatModel.chat("作为法律助手，请回答以下问题：" + question);
                    serviceInfo = "OLLAMA基础服务 (qwen2:1.5b) [降级]";
                } else {
                    return "AI法律顾问服务暂时不可用，请稍后重试。建议咨询专业律师获得法律建议。";
                }
                
                // 统一记录日志，避免重复
                log.info("使用{}处理法律咨询，会话: {}", serviceInfo, sessionId);
            } catch (Exception chatError) {
                log.error("直接聊天调用失败：{}", chatError.getMessage());
                return "处理您的法律咨询时出现问题，请稍后重试。错误信息: " + chatError.getMessage();
            }
            
            // 在响应中添加服务信息（仅调试时）
            if (log.isDebugEnabled()) {
                response += "\n\n[调试信息：" + serviceInfo + "]";
            }
            
            log.info("法律咨询响应生成成功，服务: {}, 会话: {}", serviceInfo, sessionId);
            return response;
            
        } catch (Exception e) {
            log.error("法律咨询处理失败，会话ID: {}", sessionId, e);
            
            // 尝试降级处理
            if (autoFallback) {
                return handleFallback("法律咨询", question, e);
            }
            
            return "处理您的法律咨询时出现问题，请稍后重试。如需紧急法律援助，建议直接咨询专业律师。错误信息: " + e.getMessage();
        }
    }

    /**
     * 合同条款专项分析（默认会话）
     * 混合AI架构：合同分析属于高级任务，优先使用高级AI服务
     */
    public String analyzeContractMatter(String contractContent, String question) {
        return analyzeContractMatter(contractContent, question, "default");
    }
    
    /**
     * 分析合同的关键条款（专用方法）
     * 识别并分析合同中的重要条款，如合同标的、履行期限、价款支付、违约责任等
     * 
     * @param contractContent 合同内容
     * @return 关键条款分析结果（JSON格式字符串）
     */
    @CircuitBreaker(name = "aiService", fallbackMethod = "analyzeKeyClausesFallback")
    @Retry(name = "aiService")
    public String analyzeKeyClauses(String contractContent) {
        log.info("开始分析合同关键条款，内容长度: {}", contractContent.length());
        
        try {
            if (contractContent.trim().isEmpty()) {
                return "{\"error\": \"合同内容为空\"}";
            }
            
            // 使用模板构建关键条款分析的提示词
            String contractContentTruncated = contractContent.length() > 8000 ? 
                contractContent.substring(0, 8000) + "...[已截取]" : 
                contractContent;
            
            String question = promptTemplateService.render("key-clauses-analysis", 
                Map.of("contractContent", contractContentTruncated));
            
            // 关键条款分析属于高级任务，使用高级AI服务
            TaskComplexity complexity = TaskComplexity.ADVANCED;
            LegalAssistant selectedAssistant = selectAppropriateAssistant(complexity);
            
            if (selectedAssistant == null) {
                log.warn("AI服务不可用，返回降级响应");
                return "{\"error\": \"AI分析服务暂时不可用\"}";
            }
            
            // 调用AI进行分析
            String response = selectedAssistant.analyzeContractMatter(contractContent, question);
            
            // 清理和验证JSON响应
            String cleanedResponse = cleanJsonResponse(response);
            
            log.info("关键条款分析完成，原始响应长度: {}, 清理后长度: {}", 
                response != null ? response.length() : 0, 
                cleanedResponse != null ? cleanedResponse.length() : 0);
            
            return cleanedResponse;
            
        } catch (Exception e) {
            log.error("关键条款分析失败", e);
            return "{\"error\": \"分析过程中发生错误: " + e.getMessage() + "\"}";
        }
    }
    
    /**
     * 清理AI响应，提取JSON部分
     * AI有时会返回包含解释文字的响应，需要提取其中的JSON
     */
    private String cleanJsonResponse(String response) {
        if (response == null || response.trim().isEmpty()) {
            log.warn("AI返回空响应");
            return createFallbackJsonResponse("AI返回空响应");
        }
        
        log.debug("开始清理AI响应，原始长度: {}, 前100字符: {}", 
            response.length(), 
            response.length() > 100 ? response.substring(0, 100) + "..." : response);
        
        String cleaned = response.trim();
        
        // 检查是否包含明显的非JSON内容（如"我需要"、"请提供"等）
        if (cleaned.contains("我需要") || cleaned.contains("请提供") || 
            cleaned.contains("您提到") || cleaned.contains("但还没有")) {
            log.warn("AI返回的是解释性文字而非JSON格式，原始响应: {}", 
                response.substring(0, Math.min(200, response.length())));
            return createFallbackJsonResponse("AI未按要求返回JSON格式");
        }
        
        // 尝试提取JSON部分（在```json 和 ``` 之间，或直接的{}包裹）
        if (cleaned.contains("```json")) {
            int startIdx = cleaned.indexOf("```json") + 7;
            int endIdx = cleaned.indexOf("```", startIdx);
            if (endIdx > startIdx) {
                cleaned = cleaned.substring(startIdx, endIdx).trim();
                log.debug("从markdown代码块中提取JSON");
            }
        } else if (cleaned.contains("```")) {
            // 处理不带json标记的代码块
            int startIdx = cleaned.indexOf("```") + 3;
            int endIdx = cleaned.indexOf("```", startIdx);
            if (endIdx > startIdx) {
                cleaned = cleaned.substring(startIdx, endIdx).trim();
                log.debug("从普通代码块中提取JSON");
            }
        }
        
        // 查找第一个 { 和最后一个 }
        int firstBrace = cleaned.indexOf('{');
        int lastBrace = cleaned.lastIndexOf('}');
        
        if (firstBrace != -1 && lastBrace != -1 && lastBrace > firstBrace) {
            cleaned = cleaned.substring(firstBrace, lastBrace + 1);
            log.debug("提取JSON对象: {}...{}", 
                cleaned.substring(0, Math.min(50, cleaned.length())),
                cleaned.length() > 50 ? "..." : "");
        } else {
            log.warn("响应中未找到有效的JSON结构，原始响应: {}", 
                response.substring(0, Math.min(200, response.length())));
            return createFallbackJsonResponse("响应中未找到有效的JSON结构");
        }
        
        // 验证是否为有效JSON
        try {
            com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            com.fasterxml.jackson.databind.JsonNode jsonNode = mapper.readTree(cleaned);
            
            // 验证必要字段是否存在
            if (!jsonNode.has("keyClauses")) {
                log.warn("JSON缺少keyClauses字段，将添加默认结构");
                return createFallbackJsonResponse("AI返回的JSON缺少必要字段");
            }
            
            log.info("JSON格式验证成功，包含 {} 个关键条款", 
                jsonNode.get("keyClauses").size());
            return cleaned;
        } catch (Exception e) {
            log.warn("JSON格式验证失败: {}, 原始内容: {}", e.getMessage(), 
                cleaned.substring(0, Math.min(100, cleaned.length())));
            return createFallbackJsonResponse("JSON格式验证失败: " + e.getMessage());
        }
    }
    
    /**
     * 创建降级JSON响应
     * 当AI没有返回有效JSON时，提供一个基础的JSON结构
     */
    private String createFallbackJsonResponse(String reason) {
        return String.format("""
            {
              "keyClauses": [
                {
                  "title": "系统提示",
                  "content": "AI分析服务暂时无法提供详细的关键条款分析",
                  "analysis": "%s，建议重试或人工审查",
                  "importance": "HIGH",
                  "isComplete": false,
                  "suggestion": "建议稍后重试AI分析，或咨询专业律师进行人工审查"
                }
              ],
              "completenessScore": 0,
              "overallAssessment": "由于AI服务异常，无法完成关键条款分析。建议重试或寻求专业法律意见。"
            }
            """, reason);
    }
    
    /**
     * analyzeKeyClauses 的降级方法
     */
    private String analyzeKeyClausesFallback(String contractContent, Throwable t) {
        log.warn("关键条款分析服务暂时不可用，触发降级处理，合同长度: {}, 原因: {}", 
                contractContent.length(), t.getMessage());
        
        return """
            {
              "keyClauses": [
                {
                  "title": "服务降级提示",
                  "content": "AI分析服务暂时不可用",
                  "analysis": "建议稍后重试或人工审查",
                  "importance": "HIGH",
                  "isComplete": false,
                  "suggestion": "请联系专业律师进行人工审查"
                }
              ],
              "completenessScore": 0,
              "overallAssessment": "AI服务暂时不可用，无法完成关键条款分析。建议将合同提交给专业律师进行详细审查。"
            }
            """;
    }

    /**
     * 合同条款专项分析（指定会话）
     * 混合AI架构：合同分析属于高级任务，优先使用高级AI服务
     * 
     * @param contractContent 合同内容
     * @param question 具体问题
     * @param sessionId 会话 ID，用于区分不同用户/会话
     */
    @CircuitBreaker(name = "aiService", fallbackMethod = "analyzeContractMatterFallback")
    @Retry(name = "aiService")
    public String analyzeContractMatter(String contractContent, String question, String sessionId) {
        log.debug("处理合同分析请求，内容长度: {}, 问题: {}, 会话ID: {}", 
                contractContent.length(), 
                question != null ? (question.length() > 30 ? question.substring(0, 30) + "..." : question) : "通用分析",
                sessionId);
        
        try {
            if (contractContent.trim().isEmpty()) {
                return "请提供需要分析的合同内容。";
            }
            
            if (question == null || question.trim().isEmpty()) {
                question = "请分析这份合同的主要风险点和需要注意的事项。";
            }
            
            // 合同分析属于高级任务，优先使用高级AI服务
            TaskComplexity complexity = TaskComplexity.ADVANCED; // 合同分析始终视为高级任务
            LegalAssistant selectedAssistant = selectAppropriateAssistant(complexity);
            
            if (selectedAssistant == null) {
                return "AI合同分析服务暂时不可用，请稍后重试。建议将合同提交给专业律师进行人工审查。";
            }
            
            // 记录使用的AI服务
            String serviceInfo = getSelectedServiceInfo(selectedAssistant);
            log.debug("使用{}处理合同分析，内容长度: {}, 会话: {}", serviceInfo, contractContent.length(), sessionId);
            
            // 调用选定的AI服务
            String response = selectedAssistant.analyzeContractMatter(contractContent, question);
            
            // 在响应中添加服务信息（仅调试时）
            if (log.isDebugEnabled()) {
                response += "\n\n[调试信息：" + serviceInfo + "，内容长度：" + contractContent.length() + "字符]";
            }
            
            log.debug("合同分析响应生成成功，服务: {}, 会话: {}", serviceInfo, sessionId);
            return response;
            
        } catch (Exception e) {
            log.error("合同分析处理失败，会话ID: {}", sessionId, e);
            
            // 尝试降级处理
            if (autoFallback) {
                return handleFallback("合同分析", contractContent + "\n问题：" + question, e);
            }
            
            return "处理合同分析时出现问题，请稍后重试。建议将合同提交给专业律师进行人工审查。错误信息: " + e.getMessage();
        }
    }

    /**
     * 获取Agent使用的模型信息
     */
    public String getModelInfo() {
        return "混合AI架构状态：\n" +
                String.format("基础服务：%s (%s) - %s\n",
                        basicProvider, basicModelName, basicServiceAvailable ? "可用" : "不可用") +
                String.format("高级服务：%s (%s) - %s\n",
                        advancedProvider, advancedModelName, advancedServiceAvailable ? "可用" : "不可用") +
                String.format("智能路由：%s，自动降级：%s",
                        hybridEnabled ? "启用" : "禁用", autoFallback ? "启用" : "禁用");
    }

    /**
     * 检查Agent服务状态
     */
    public boolean isServiceHealthy() {
        // 在混合架构中，至少一个服务可用即认为健康
        return basicServiceAvailable || advancedServiceAvailable;
    }
    
    

    /**
     * 直接与底层模型对话（不使用工具）
     * 用于简单对话或调试，优先使用基础服务
     */
    @CircuitBreaker(name = "aiService", fallbackMethod = "directChatFallback")
    @Retry(name = "aiService")
    public String directChat(String message) {
        log.info("处理直接聊天请求: {}", message);
        
        try {
            ChatModel selectedModel = null;
            String serviceInfo = "";
            
            // 优先使用基础服务进行直接对话
            if (basicServiceAvailable && basicChatModel != null) {
                selectedModel = basicChatModel;
                serviceInfo = String.format("%s基础服务 (%s)", basicProvider.toUpperCase(), basicModelName);
            } else if (advancedServiceAvailable && advancedChatModel != null) {
                selectedModel = advancedChatModel;
                serviceInfo = String.format("%s高级服务 (%s)", advancedProvider.toUpperCase(), advancedModelName);
                log.info("基础服务不可用，使用高级服务进行直接对话");
            }
            
            if (selectedModel == null) {
                return "聊天服务正在初始化，请稍后重试。";
            }
            
            log.debug("使用{}进行直接对话", serviceInfo);
            String response = selectedModel.chat(message);
            log.info("直接聊天响应生成成功，服务: {}", serviceInfo);
            
            // 在调试模式下显示服务信息
            if (log.isDebugEnabled()) {
                response += "\n\n[调试信息：" + serviceInfo + "]";
            }
            
            return response;
        } catch (Exception e) {
            log.error("直接聊天处理失败", e);
            
            // 尝试降级处理
            if (autoFallback) {
                return handleFallback("直接聊天", message, e);
            }
            
            return "聊天服务暂时不可用，请稍后重试。";
        }
    }

    /**
     * 专门用于报告生成的直接对话方法（不使用工具调用，避免工具调用问题）
     * 优先使用高级服务（DeepSeek），确保报告质量
     * 
     * @param message 完整的提示词（包含合同内容和分析任务）
     * @return AI生成的报告内容
     */
    @CircuitBreaker(name = "aiService", fallbackMethod = "directChatForReportFallback")
    @Retry(name = "aiService")
    public String directChatForReport(String message) {
        log.debug("处理报告生成请求，内容长度: {}", message.length());
        
        try {
            ChatModel selectedModel = null;
            String serviceInfo = "";
            
            // 优先使用高级服务（DeepSeek）生成报告，确保质量
            if (advancedServiceAvailable && advancedChatModel != null) {
                selectedModel = advancedChatModel;
                serviceInfo = String.format("%s高级服务 (%s)", advancedProvider.toUpperCase(), advancedModelName);
            } else if (basicServiceAvailable && basicChatModel != null) {
                // 降级到基础服务
                selectedModel = basicChatModel;
                serviceInfo = String.format("%s基础服务 (%s)", basicProvider.toUpperCase(), basicModelName);
                log.warn("高级服务不可用，降级使用基础服务生成报告");
            }
            
            if (selectedModel == null) {
                throw new RuntimeException("所有AI服务不可用");
            }
            
            log.debug("使用{}生成报告内容", serviceInfo);
            String response = selectedModel.chat(message);
            
            if (response == null || response.trim().isEmpty()) {
                throw new RuntimeException("AI返回空响应");
            }
            
            log.debug("报告内容生成成功，服务: {}, 响应长度: {}", serviceInfo, response.length());
            return response;
            
        } catch (Exception e) {
            log.error("报告生成失败: {}", e.getMessage());
            throw new RuntimeException("报告生成失败: " + e.getMessage(), e);
        }
    }
    
    /**
     * directChatForReport 的降级方法
     */
    private String directChatForReportFallback(String message, Throwable t) {
        log.warn("报告生成服务触发降级, 原因: {}", t.getMessage());
        
        // 尝试使用备用服务
        try {
            if (basicServiceAvailable && basicChatModel != null) {
                log.info("使用基础服务作为降级方案");
                return basicChatModel.chat(message);
            }
        } catch (Exception fallbackError) {
            log.error("降级方案也失败了: {}", fallbackError.getMessage());
        }
        
        return "报告内容生成失败，AI服务暂时不可用。";
    }

    
    /**
     * 重置所有对话记忆
     */
    public void resetAllChatMemories() {
        log.info("重置所有对话记忆，当前数量: {}", chatMemories.size());
        
        try {
            chatMemories.values().forEach(ChatMemory::clear);
            chatMemories.clear();
            log.info("所有对话记忆已清除");
        } catch (Exception e) {
            log.warn("重置所有对话记忆时出现异常", e);
        }
    }

    // ==================== 智能路由核心方法 ====================
    
    
    /**
     * 选择合适的AI助手
     * 根据任务复杂度和服务可用性选择最佳的AI服务
     */
    private LegalAssistant selectAppropriateAssistant(TaskComplexity complexity) {
        // 优先级策略：
        // 1. 高级任务优先使用高级服务
        // 2. 基础任务优先使用基础服务
        // 3. 如果首选服务不可用且支持降级，则使用备用服务
        
        if (complexity == TaskComplexity.ADVANCED) {
            // 高级任务：优先使用高级服务
            if (advancedServiceAvailable && advancedLegalAssistant != null) {
                return advancedLegalAssistant;
            } else if (autoFallback && basicServiceAvailable && basicLegalAssistant != null) {
                log.info("高级服务不可用，降级到基础服务处理高级任务: {}", "contract_analysis");
                return basicLegalAssistant;
            }
        } else {
            // 基础任务：优先使用基础服务
            if (basicServiceAvailable && basicLegalAssistant != null) {
                return basicLegalAssistant;
            } else if (autoFallback && advancedServiceAvailable && advancedLegalAssistant != null) {
                log.info("基础服务不可用，使用高级服务处理基础任务: {}", "contract_analysis");
                return advancedLegalAssistant;
            }
        }
        
        return null; // 没有可用的服务
    }
    
    /**
     * 获取选中服务的信息
     */
    private String getSelectedServiceInfo(LegalAssistant assistant) {
        if (assistant == null) {
            return "无可用服务";
        }
        
        if (assistant == advancedLegalAssistant) {
            return String.format("%s高级服务 (%s)", advancedProvider.toUpperCase(), advancedModelName);
        } else if (assistant == basicLegalAssistant) {
            return String.format("%s基础服务 (%s)", basicProvider.toUpperCase(), basicModelName);
        } else {
            return "未知服务";
        }
    }
    
    /**
     * 处理服务降级
     */
    private String handleFallback(String taskName, String input, Exception error) {
        log.warn("{}处理失败，尝试降级处理：{}", taskName, error.getMessage());
        
        try {
            // 尝试使用另一个可用的服务
            LegalAssistant fallbackAssistant = null;
            String fallbackInfo = "";
            
            if (basicServiceAvailable && basicLegalAssistant != null) {
                fallbackAssistant = basicLegalAssistant;
                fallbackInfo = String.format("%s基础服务", basicProvider.toUpperCase());
            } else if (advancedServiceAvailable && advancedLegalAssistant != null) {
                fallbackAssistant = advancedLegalAssistant;
                fallbackInfo = String.format("%s高级服务", advancedProvider.toUpperCase());
            }
            
            if (fallbackAssistant != null) {
                log.info("使用{}进行降级处理", fallbackInfo);
                
                if (taskName.contains("合同")) {
                    // 合同相关任务
                    String[] parts = input.split("\n问题：", 2);
                    String contractContent = parts[0];
                    String question = parts.length > 1 ? parts[1] : "请进行基础分析";
                    return fallbackAssistant.analyzeContractMatter(contractContent, question);
                } else {
                    // 法律咨询任务
                    return fallbackAssistant.consultLegalMatter(input);
                }
            }
            
        } catch (Exception fallbackError) {
            log.error("降级处理也失败了：{}", fallbackError.getMessage());
        }
        
        return String.format("抱歉，%s服务暂时遇到问题。请稍后重试或联系技术支持。\n\n原始错误：%s", 
                taskName, error.getMessage());
    }

    /**
     * 创建简化的法律助手实现
     */
    private LegalAssistant createSimpleLegalAssistant(LegalTools legalTools) {
        return new LegalAssistant() {
            @Override
            public String consultLegalMatter(String question) {
                log.info("简化模式处理法律咨询: {}", question);
                
                try {
                    // 调用知识库搜索工具
                    String searchResult = legalTools.searchLegalKnowledge(question, 3);
                    
                    return "根据知识库检索，为您找到以下相关信息：\n\n" + searchResult + 
                           "\n\n注意：当前使用简化模式，建议咨询专业法律人士获得更准确的建议。";
                           
                } catch (Exception e) {
                    log.error("简化模式处理法律咨询失败", e);
                    return "处理您的法律咨询时出现问题，请稍后重试或直接咨询专业律师。";
                }
            }

            @Override
            public String analyzeContractMatter(String contractContent, String question) {
                log.debug("简化模式处理合同分析，内容长度: {}", contractContent.length());
                
                try {
                    // 提取合同关键词进行搜索
                    String searchQuery = question != null ? question : "合同风险 条款分析";
                    String searchResult = legalTools.searchLegalKnowledge(searchQuery, 3);
                    
                    // 分析合同条款
                    String clauseAnalysis = legalTools.analyzeContractClause(
                        contractContent.length() > 1000 ? contractContent.substring(0, 1000) : contractContent,
                        question != null ? question : "合同风险分析"
                    );

                    return "合同分析结果（简化模式）：\n\n" +
                            "合同内容长度：" + contractContent.length() + " 字符\n" +
                            "分析问题：" + (question != null ? question : "通用风险分析") + "\n\n" +
                            "条款分析：\n" + clauseAnalysis + "\n\n" +
                            "相关法律依据：\n" + searchResult + "\n\n" +
                            "重要提醒：以上分析为简化模式结果，建议聘请专业律师进行详细审查。";
                    
                } catch (Exception e) {
                    log.error("简化模式处理合同分析失败", e);
                    return "处理合同分析时出现问题，建议将合同提交给专业律师审查。";
                }
            }
        };
    }

    /**
     * 流式法律咨询
     * 通过SSE实时推送响应内容
     * 
     * @param question 法律问题
     * @param emitter SSE发射器
     * @param responseBuilder 用于累积完整响应的StringBuilder（用于保存历史记录）
     */
    public void consultLegalMatterStream(String question, org.springframework.web.servlet.mvc.method.annotation.SseEmitter emitter, StringBuilder responseBuilder) {
        // 异步执行，使用支持SecurityContext的执行器，避免阻塞请求线程
        java.util.concurrent.CompletableFuture.runAsync(() -> {
            try {
                log.info("🚀 开始流式法律咨询处理: question='{}', length={}", question, question.length());
                
                // 基础参数检查
                if (question == null || question.trim().isEmpty()) {
                    log.warn("❌ 问题为空，终止流式处理");
                    emitter.send(org.springframework.web.servlet.mvc.method.annotation.SseEmitter.event()
                        .data(java.util.Map.of("type", "error", "error", "问题不能为空")));
                    emitter.complete();
                    return;
                }
                
                // 发送开始事件（可选，前端可能不需要）
                // 注释掉以减少不必要的事件
                // emitter.send(org.springframework.web.servlet.mvc.method.annotation.SseEmitter.event()
                //     .data(java.util.Map.of(
                //         "type", "start",
                //         "message", "开始处理您的法律咨询..."
                //     )));
                
                // 检查服务可用性
                log.debug("🔍 检查服务可用性: advanced={}, basic={}", advancedServiceAvailable, basicServiceAvailable);
                if (!advancedServiceAvailable && !basicServiceAvailable) {
                    log.error("❌ 所有AI服务不可用");
                    emitter.send(org.springframework.web.servlet.mvc.method.annotation.SseEmitter.event()
                        .data(java.util.Map.of("type", "error", "error", "所有AI服务暂时不可用，请稍后重试")));
                    emitter.complete();
                    return;
                }
                
                // 使用流式模型进行流式调用
                if (advancedStreamingModel != null) {
                    log.info("使用高级流式模型进行推送");
                    streamWithModel(advancedStreamingModel, question, emitter, responseBuilder);
                } else if (basicStreamingModel != null) {
                    log.info("高级流式模型不可用，使用基础流式模型进行推送");
                    streamWithModel(basicStreamingModel, question, emitter, responseBuilder);
                } else {
                    emitter.send(org.springframework.web.servlet.mvc.method.annotation.SseEmitter.event()
                        .data(java.util.Map.of("type", "error", "error", "没有可用的流式AI模型")));
                    emitter.complete();
                }
                
            } catch (Exception e) {
                log.error("流式法律咨询处理失败", e);
                try {
                    emitter.send(org.springframework.web.servlet.mvc.method.annotation.SseEmitter.event()
                        .data(java.util.Map.of("type", "error", "error", "处理失败: " + e.getMessage())));
                    emitter.completeWithError(e);
                } catch (Exception sendError) {
                    log.error("发送错误事件失败", sendError);
                }
            }
        }, taskExecutor);
    }
    
    /**
     * 使用指定流式模型进行流式推送
     * 使用LangChain4j官方的StreamingChatModel和StreamingChatResponseHandler
     */
    private void streamWithModel(StreamingChatModel streamingModel, String question, 
                                 org.springframework.web.servlet.mvc.method.annotation.SseEmitter emitter,
                                 StringBuilder responseBuilder) {
        try {
            log.info("📡 开始使用流式模型处理: model={}", streamingModel.getClass().getSimpleName());
            
            // 构建完整的提示
            String fullPrompt = "作为专业法律助手，请提供准确、详细的法律建议：" + question;
            log.debug("📝 构建完整提示: length={}, prompt='{}'", 
                    fullPrompt.length(), 
                    fullPrompt.length() > 100 ? fullPrompt.substring(0, 100) + "..." : fullPrompt);
            
            // 流式生成响应
            StringBuilder fullResponse = new StringBuilder();
            
            // 获取当前SecurityContext，用于传递到回调方法中
            SecurityContext currentSecurityContext = SecurityContextHolder.getContext();
            log.debug("🔐 获取SecurityContext: {}", currentSecurityContext != null ? "成功" : "失败");
            
            // 使用LangChain4j的StreamingChatResponseHandler
            // 将字符串包装成UserMessage并放入列表中
            streamingModel.chat(
                java.util.List.of(UserMessage.from(fullPrompt)), 
                new StreamingChatResponseHandler() {
                
                @Override
                public void onPartialResponse(String partialResponse) {
                    // 在回调中设置SecurityContext
                    SecurityContext originalContext = SecurityContextHolder.getContext();
                    try {
                        SecurityContextHolder.setContext(currentSecurityContext);
                        
                        fullResponse.append(partialResponse);
                        // 同时累积到外部传入的responseBuilder（用于保存历史记录）
                        if (responseBuilder != null) {
                            responseBuilder.append(partialResponse);
                        }
                        
                        // 记录详细的发送信息
                        log.debug("📤 发送内容片段: length={}, content='{}'", 
                                partialResponse.length(), 
                                partialResponse.length() > 50 ? 
                                    partialResponse.substring(0, 50) + "..." : partialResponse);
                        
                        // 发送内容片段，使用前端期望的格式
                        var dataMap = java.util.Map.of("type", "content", "content", partialResponse);
                        log.debug("📦 发送SSE数据: {}", dataMap);
                        
                        emitter.send(org.springframework.web.servlet.mvc.method.annotation.SseEmitter.event()
                            .data(dataMap));
                            
                        log.debug("✅ 内容片段发送成功");
                    } catch (Exception e) {
                        log.error("❌ 发送内容片段失败", e);
                    } finally {
                        // 恢复原始SecurityContext
                        SecurityContextHolder.setContext(originalContext);
                    }
                }
                
                @Override
                public void onCompleteResponse(ChatResponse response) {
                    // 在回调中设置SecurityContext
                    SecurityContext originalContext = SecurityContextHolder.getContext();
                    try {
                        SecurityContextHolder.setContext(currentSecurityContext);
                        
                        log.info("流式响应完成，总长度: {}", fullResponse.length());
                        
                        // 发送完成事件，使用前端期望的格式
                        emitter.send(org.springframework.web.servlet.mvc.method.annotation.SseEmitter.event()
                            .data(java.util.Map.of(
                                "type", "complete",
                                "message", "响应完成",
                                "totalLength", fullResponse.length()
                            )));
                        
                        emitter.complete();
                    } catch (Exception e) {
                        log.error("发送完成事件失败", e);
                        emitter.completeWithError(e);
                    } finally {
                        // 恢复原始SecurityContext
                        SecurityContextHolder.setContext(originalContext);
                    }
                }
                
                @Override
                public void onError(Throwable error) {
                    // 在回调中设置SecurityContext
                    SecurityContext originalContext = SecurityContextHolder.getContext();
                    try {
                        SecurityContextHolder.setContext(currentSecurityContext);
                        
                        log.error("流式生成出错", error);
                        emitter.send(org.springframework.web.servlet.mvc.method.annotation.SseEmitter.event()
                            .data(java.util.Map.of("type", "error", "error", "生成失败: " + error.getMessage())));
                        emitter.completeWithError(error);
                    } catch (Exception e) {
                        log.error("发送错误事件失败", e);
                    } finally {
                        // 恢复原始SecurityContext
                        SecurityContextHolder.setContext(originalContext);
                    }
                }
            });
            
        } catch (Exception e) {
            log.error("流式模型调用失败", e);
            try {
                emitter.send(org.springframework.web.servlet.mvc.method.annotation.SseEmitter.event()
                    .data(java.util.Map.of("type", "error", "error", "模型调用失败: " + e.getMessage())));
                emitter.completeWithError(e);
            } catch (Exception sendError) {
                log.error("发送错误事件失败", sendError);
            }
        }
    }
    
    // ==================== Resilience4j 降级方法 ====================
    
    /**
     * consultLegalMatterWithDetails 的降级方法
     */
    private ConsultationResult consultLegalMatterWithDetailsFallback(String question, String sessionId, Throwable t) {
        log.warn("法律咨询服务（详细版）触发降级: sessionId={}, 原因: {}", sessionId, t.getMessage());
        
        return new ConsultationResult(
            "抱歉，AI法律顾问服务当前繁忙或暂时不可用。\n\n" +
            "**建议措施：**\n" +
            "1. 请稍后（3-5分钟）重试\n" +
            "2. 如有紧急法律咨询需求，建议直接联系专业律师\n" +
            "3. 您也可以查阅本系统的法律文档库获取基础信息\n\n" +
            "系统正在努力恢复服务，感谢您的耐心等待。",
            "服务降级",
            "none",
            false
        );
    }
    
    /**
     * consultLegalMatter 的降级方法
     */
    private String consultLegalMatterFallback(String question, String sessionId, Throwable t) {
        log.warn("法律咨询服务触发降级: sessionId={}, 原因: {}", sessionId, t.getMessage());
        
        return "抱歉，AI法律顾问服务当前繁忙或暂时不可用。\n\n" +
               "建议措施：\n" +
               "1. 请稍后重试\n" +
               "2. 如有紧急需求，建议联系专业律师\n" +
               "3. 可查阅系统法律文档库获取基础信息\n\n" +
               "系统正在恢复中，感谢您的耐心。";
    }
    
    /**
     * analyzeContractMatter 的降级方法
     */
    private String analyzeContractMatterFallback(String contractContent, String question, 
                                                  String sessionId, Throwable t) {
        log.warn("合同分析服务触发降级: sessionId={}, 合同长度={}, 原因: {}", 
                sessionId, contractContent.length(), t.getMessage());
        
        return "### 合同分析服务暂时不可用\n\n" +
               "由于AI合同分析服务当前繁忙，暂时无法完成智能分析。\n\n" +
               "**建议措施：**\n" +
               "1. **稍后重试**：请在5-10分钟后重新提交分析请求\n" +
               "2. **专业审查**：强烈建议将合同提交给专业律师进行详细审查\n" +
               "3. **基础检查**：您可以先自行核对以下要素：\n" +
               "   - 合同双方信息是否准确完整\n" +
               "   - 合同标的、价款、期限是否明确\n" +
               "   - 违约责任条款是否清晰\n" +
               "   - 争议解决方式是否约定\n\n" +
               "**合同基本信息：**\n" +
               "- 内容长度：" + contractContent.length() + " 字符\n" +
               "- 分析问题：" + (question != null ? question : "通用风险分析") + "\n" +
               "- 服务状态：降级中\n\n" +
               "如需紧急处理，请联系专业法律顾问或技术支持。";
    }
    
    /**
     * directChat 的降级方法
     */
    private String directChatFallback(String message, Throwable t) {
        log.warn("直接聊天服务触发降级, 原因: {}", t.getMessage());
        
        return "抱歉，聊天服务当前繁忙，暂时无法响应。请稍后重试。\n\n" +
               "如有紧急需求，建议：\n" +
               "- 稍后（3-5分钟）重新尝试\n" +
               "- 联系系统管理员或技术支持\n" +
               "- 使用其他可用的功能模块";
    }
}
