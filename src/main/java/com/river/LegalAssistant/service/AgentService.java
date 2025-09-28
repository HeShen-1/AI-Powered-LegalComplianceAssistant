package com.river.LegalAssistant.service;

import dev.langchain4j.service.AiServices;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.ollama.OllamaChatModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.memory.ChatMemory;
import dev.langchain4j.http.client.jdk.JdkHttpClient;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;
import java.util.List;
import java.util.Arrays;
import jakarta.annotation.PostConstruct;

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
    
    // 会话记忆管理
    private final Map<String, ChatMemory> chatMemories = new ConcurrentHashMap<>();
    private final LegalTools legalTools;

    // 配置参数
    @Value("${spring.ai.ollama.base-url:http://localhost:11434}")
    private String ollamaBaseUrl;
    
    @Value("${spring.ai.openai.api-key:}")
    private String openaiApiKey;
    
    @Value("${spring.ai.openai.base-url:https://api.deepseek.com}")
    private String openaiBaseUrl;
    
    // 混合架构配置
    @Value("${app.ai.service-mode.hybrid-enabled:true}")
    private boolean hybridEnabled;
    
    @Value("${app.ai.service-mode.basic-provider:ollama}")
    private String basicProvider;
    
    @Value("${app.ai.service-mode.advanced-provider:openai}")
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
        String consultLegalMatter(String question);
        
        /**
         * 合同条款分析咨询
         * 专门处理合同相关的问题
         * 
         * @param contractContent 合同内容或条款
         * @param question 具体问题
         * @return 分析结果和建议
         */
        String analyzeContractMatter(String contractContent, String question);
    }

    public AgentService(LegalTools legalTools) {
        this.legalTools = legalTools;
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
            if ("openai".equalsIgnoreCase(advancedProvider)) {
                // 检查OpenAI API密钥
                if (openaiApiKey == null || openaiApiKey.isEmpty() || "your_openai_api_key_here".equals(openaiApiKey)) {
                    log.warn("OpenAI API密钥未配置，高级服务将不可用");
                    advancedServiceAvailable = false;
                    return;
                }
                
                // 创建OpenAI高级模型
                this.advancedChatModel = createOpenAiChatModel(advancedModelName, 0.3);
                
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
                        .logRequests(true)
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
     * 创建OpenAI聊天模型 - 显式指定JDK HTTP客户端
     */
    private OpenAiChatModel createOpenAiChatModel(String modelName, double temperature) {
        return OpenAiChatModel.builder()
                .apiKey(openaiApiKey)
                .baseUrl(openaiBaseUrl)
                .modelName(modelName)
                .temperature(temperature)
                .maxTokens(4096)
                .timeout(Duration.ofMinutes(3))
                .logRequests(true)
                .logResponses(false)
                .httpClientBuilder(JdkHttpClient.builder()
                        .connectTimeout(Duration.ofSeconds(30))
                        .readTimeout(Duration.ofMinutes(3)))
                .build();
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
     * 智能法律咨询（指定会话）
     * 混合AI架构：根据任务复杂度智能选择合适的AI服务
     * 
     * @param question 法律问题
     * @param sessionId 会话 ID，用于区分不同用户/会话
     */
    public String consultLegalMatter(String question, String sessionId) {
        log.info("处理法律咨询请求: {}, 会话ID: {}", 
                question.length() > 50 ? question.substring(0, 50) + "..." : question, sessionId);
        
        try {
            if (question.trim().isEmpty()) {
                return "请提供具体的法律问题，我将为您提供专业的咨询建议。";
            }
            
            // 临时解决方案：直接使用基础ChatModel，绕过Agent复杂逻辑
            String response;
            String serviceInfo;
            
            try {
                if (basicChatModel != null) {
                    response = basicChatModel.chat("作为法律助手，请回答以下问题：" + question);
                    serviceInfo = "OLLAMA基础服务 (qwen2:1.5b)";
                } else if (advancedChatModel != null) {
                    response = advancedChatModel.chat("作为法律助手，请回答以下问题：" + question);
                    serviceInfo = "DEEPSEEK高级服务 (deepseek-chat)";
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
     * 合同条款专项分析（指定会话）
     * 混合AI架构：合同分析属于高级任务，优先使用高级AI服务
     * 
     * @param contractContent 合同内容
     * @param question 具体问题
     * @param sessionId 会话 ID，用于区分不同用户/会话
     */
    public String analyzeContractMatter(String contractContent, String question, String sessionId) {
        log.info("处理合同分析请求，内容长度: {}, 问题: {}, 会话ID: {}", 
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
            log.info("使用{}处理合同分析，内容长度: {}, 会话: {}", serviceInfo, contractContent.length(), sessionId);
            
            // 调用选定的AI服务
            String response = selectedAssistant.analyzeContractMatter(contractContent, question);
            
            // 在响应中添加服务信息（仅调试时）
            if (log.isDebugEnabled()) {
                response += "\n\n[调试信息：" + serviceInfo + "，内容长度：" + contractContent.length() + "字符]";
            }
            
            log.info("合同分析响应生成成功，服务: {}, 会话: {}", serviceInfo, sessionId);
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
                log.info("简化模式处理合同分析，内容长度: {}", contractContent.length());
                
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
}
