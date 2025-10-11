package com.river.LegalAssistant.service;

import com.river.LegalAssistant.dto.ContractRiskAnalysisResult;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.ai.converter.BeanOutputConverter;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.ObjectFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * AI 服务层
 */
@Service
@Slf4j
public class AiService {

    private final ChatClient chatClient;
    private final EmbeddingModel embeddingModel;
    
    // 添加聊天记忆服务
    private final ChatMemoryService chatMemoryService;
    
    // 使用 ObjectFactory 获取 prototype 作用域的 ChatClient.Builder
    private final ObjectFactory<ChatClient.Builder> chatClientBuilderFactory;
    
    // 新增的专门服务
    private final RAGService ragService;
    private final VectorStoreService vectorStoreService;
    private final TextProcessingService textProcessingService;

    @Value("${app.rag.max-results:10}")
    private int maxResults;
    
    @Value("${app.ai.prompts.legal-qa}")
    private Resource legalQaPromptResource;
    
    @Value("${app.ai.prompts.contract-risk-analysis}")
    private Resource contractRiskAnalysisPromptResource;
    
    @Value("${app.ai.prompts.contract-risk-analysis-structured}")
    private Resource contractRiskAnalysisStructuredPromptResource;
    
    public AiService(ChatClient chatClient, 
                    @Qualifier("ollamaEmbeddingModel") EmbeddingModel embeddingModel,
                    ChatMemoryService chatMemoryService,
                    ObjectFactory<ChatClient.Builder> chatClientBuilderFactory,
                    RAGService ragService,
                    VectorStoreService vectorStoreService,
                    TextProcessingService textProcessingService) {
        this.chatClient = chatClient;
        this.embeddingModel = embeddingModel;
        this.chatMemoryService = chatMemoryService;
        this.chatClientBuilderFactory = chatClientBuilderFactory;
        this.ragService = ragService;
        this.vectorStoreService = vectorStoreService;
        this.textProcessingService = textProcessingService;
    }


    /**
     * 带聊天记忆的本地知识库聊天
     * 基于SpringAI官方文档实现的聊天记忆功能
     * 
     * @param question 用户问题
     * @param conversationId 会话ID
     * @param modelType 模型类型（OLLAMA或DEEPSEEK）
     * @return 聊天结果，包括答案和是否有知识匹配
     */
    @CircuitBreaker(name = "aiService", fallbackMethod = "chatWithMemoryFallback")
    @Retry(name = "aiService")
    public LocalRagResult chatWithMemory(String question, String conversationId, ChatMemoryService.ModelType modelType) {
        log.info("处理带聊天记忆的本地知识库聊天: conversationId={}, modelType={}, question={}", 
                conversationId, modelType, question);
        
        try {
            // 1. 获取对应模型的聊天记忆
            ChatMemory chatMemory = chatMemoryService.getChatMemory(modelType);
            
            // 2. 使用 Spring 管理的 Builder 工厂创建带聊天记忆的ChatClient
            String systemPrompt = (modelType == ChatMemoryService.ModelType.OLLAMA) 
                ? "你是一个专业的法律合规智能助手，专门帮助用户进行合同审查和法律咨询。请始终提供准确、专业的法律建议，并在适当时引用相关法律条文。"
                : "你是一个专业的法律AI助手，拥有深度推理和工具调用能力。你可以进行复杂的法律分析、风险评估和决策支持。请提供专业、准确的法律建议。";
            
            ChatClient memoryEnabledChatClient = chatClientBuilderFactory.getObject()
                    .defaultAdvisors(MessageChatMemoryAdvisor.builder(chatMemory).build())
                    .defaultSystem(systemPrompt)
                    .build();
            
            // 3. 使用RAGService执行RAG流程,但使用带记忆的ChatClient
            RAGService.RagResult ragResult = ragService.performRAGWithClient(question, maxResults, memoryEnabledChatClient);
            
            log.info("带聊天记忆的本地知识库聊天响应生成成功: conversationId={}, modelType={}, 使用了 {} 个文档片段", 
                    conversationId, modelType, ragResult.sourceCount());
            
            return new LocalRagResult(ragResult.answer(), ragResult.hasKnowledgeMatch(), ragResult.sourceCount());
            
        } catch (Exception e) {
            log.error("带聊天记忆的本地知识库聊天处理失败: conversationId={}, modelType={}", 
                    conversationId, modelType, e);
            throw new RuntimeException("聊天记忆服务暂时不可用", e);
        }
    }

    /**
     * 简单的聊天（不使用RAG，但保留聊天记忆）
     * 
     * @param message 用户消息
     * @param conversationId 会话ID
     * @param modelType 模型类型
     * @return 聊天响应
     */
    @CircuitBreaker(name = "aiService", fallbackMethod = "simpleChatWithMemoryFallback")
    @Retry(name = "aiService")
    public String simpleChatWithMemory(String message, String conversationId, ChatMemoryService.ModelType modelType) {
        log.info("处理简单聊天（带记忆）: conversationId={}, modelType={}, message={}", 
                conversationId, modelType, message);
        
        try {
            // 获取对应模型的聊天记忆
            ChatMemory chatMemory = chatMemoryService.getChatMemory(modelType);
            
            // 使用 Spring 管理的 Builder 工厂创建带聊天记忆的ChatClient
            ChatClient memoryEnabledChatClient = chatClientBuilderFactory.getObject()
                    .defaultAdvisors(MessageChatMemoryAdvisor.builder(chatMemory).build())
                    .defaultSystem(modelType == ChatMemoryService.ModelType.OLLAMA 
                        ? "你是一个专业的法律合规智能助手。"
                        : "你是一个专业的法律AI助手，拥有深度推理能力。")
                    .build();
            
            // 进行对话
            String response = memoryEnabledChatClient.prompt()
                    .user(message)
                    .advisors(advisor -> advisor.param(ChatMemory.CONVERSATION_ID, conversationId))
                    .call()
                    .content();
            
            log.info("简单聊天（带记忆）响应生成成功: conversationId={}, modelType={}", 
                    conversationId, modelType);
            
            return response;
            
        } catch (Exception e) {
            log.error("简单聊天（带记忆）处理失败: conversationId={}, modelType={}", 
                    conversationId, modelType, e);
            throw new RuntimeException("聊天记忆服务暂时不可用", e);
        }
    }


    /**
     * 本地知识库聊天 - 标准RAG实现
     * 使用传统的检索-增强-生成流程
     * 
     * 重构后的版本:委托给RAGService处理
     */
    public LocalRagResult localKnowledgeChat(String question) {
        log.info("处理本地知识库聊天请求: {}", question);
        try {
            // 委托给RAGService执行完整的RAG流程
            RAGService.RagResult ragResult = ragService.performRAG(question, maxResults);
            
            // 转换为LocalRagResult格式(保持API兼容性)
            return new LocalRagResult(
                ragResult.answer(),
                ragResult.hasKnowledgeMatch(),
                ragResult.sourceCount()
            );
            
        } catch (Exception e) {
            log.error("本地知识库聊天处理失败", e);
            throw new RuntimeException("本地知识库聊天服务暂时不可用", e);
        }
    }



    /**
     * 保留原有的RAG方法用于兼容性（废弃）
     * @deprecated 请使用 localKnowledgeChat 方法
     */
    @Deprecated
    public String chatWithRag(String question) {
        LocalRagResult result = localKnowledgeChat(question);
        return result.answer();
    }

    /**
     * 文档向量化并存储（支持自动分块）
     * 重构后的版本:委托给VectorStoreService处理
     */
    public void addDocument(String content, Map<String, Object> metadata) {
        log.info("添加文档到向量存储，内容长度: {} 字符", content.length());
        
        if (content.trim().isEmpty()) {
            log.warn("文档内容为空，跳过添加");
            return;
        }
        
        try {
            // 委托给VectorStoreService处理
            vectorStoreService.add(content, metadata);
        } catch (Exception e) {
            log.error("文档添加失败", e);
            throw new RuntimeException("文档存储服务暂时不可用", e);
        }
    }
    

    /**
     * 测试文档分块功能（用于调试）
     * 重构后的版本:委托给TextProcessingService处理
     */
    public List<Map<String, Object>> testDocumentChunking(String content) {
        log.info("测试文档分块，内容长度: {} 字符", content.length());
        
        List<Map<String, Object>> chunkInfo = new ArrayList<>();
        
        if (textProcessingService.needsChunking(content)) {
            List<String> chunks = textProcessingService.splitIntoChunks(content);
            
            for (int i = 0; i < chunks.size(); i++) {
                String chunk = chunks.get(i);
                int estimatedTokens = textProcessingService.estimateTokenCount(chunk);
                
                Map<String, Object> info = new HashMap<>();
                info.put("index", i);
                info.put("length", chunk.length());
                info.put("estimatedTokens", estimatedTokens);
                info.put("preview", chunk.length() > 100 ? chunk.substring(0, 100) + "..." : chunk);
                info.put("withinTokenLimit", estimatedTokens <= 500);
                
                chunkInfo.add(info);
            }
        } else {
            Map<String, Object> info = new HashMap<>();
            info.put("index", 0);
            info.put("length", content.length());
            info.put("estimatedTokens", textProcessingService.estimateTokenCount(content));
            info.put("preview", content.length() > 100 ? content.substring(0, 100) + "..." : content);
            info.put("withinTokenLimit", true);
            info.put("needsChunking", false);
            
            chunkInfo.add(info);
        }
        
        log.info("分块测试完成，共生成 {} 个块", chunkInfo.size());
        return chunkInfo;
    }

    /**
     * 文本向量化
     */
    public List<Double> embed(String text) {
        log.info("对文本进行向量化，长度: {}", text.length());
        try {
            List<Double> embedding = new ArrayList<>();
            // Spring AI 在部分版本中会返回 float[]，这里做兼容处理
            float[] floatEmbedding = embeddingModel.embed(text);
            for (float f : floatEmbedding) {
                embedding.add((double) f);
            }
            log.info("文本向量化成功，维度: {}", embedding.size());
            return embedding;
        } catch (Exception e) {
            log.error("文本向量化失败", e);
            throw new RuntimeException("向量化服务暂时不可用", e);
        }
    }


    /**
     * 合同风险分析 - 使用专业提示词模板
     */
    @CircuitBreaker(name = "aiService", fallbackMethod = "analyzeContractRiskFallback")
    @Retry(name = "aiService")
    public String analyzeContractRisk(String contractContent) {
        log.info("分析合同风险，内容长度: {}", contractContent.length());
        try {
            // 使用专业的风险分析提示词模板
            PromptTemplate promptTemplate = new PromptTemplate(contractRiskAnalysisPromptResource);
            
            Map<String, Object> promptValues = Map.of(
                "contractContent", contractContent
            );
            
            String response = chatClient.prompt(promptTemplate.create(promptValues))
                    .call()
                    .content();

            if (response != null) {
                log.info("合同风险分析完成，分析结果长度: {} 字符", response.length());
            }
            return response;
        } catch (Exception e) {
            log.error("合同风险分析失败", e);
            throw new RuntimeException("合同分析服务暂时不可用", e);
        }
    }

    /**
     * 分析合同并提取结构化风险信息
     * 使用 BeanOutputConverter 直接解析 AI 的 JSON 输出
     * 
     * @param contractContent 合同内容
     * @return 结构化的风险分析结果
     * @throws RuntimeException 如果结构化解析失败
     */
    @CircuitBreaker(name = "aiService", fallbackMethod = "analyzeContractRiskStructuredFallback")
    @Retry(name = "aiService")
    public ContractRiskAnalysisResult analyzeContractRiskStructured(String contractContent) {
        log.info("进行结构化合同风险分析，内容长度: {}", contractContent.length());
        
        try {
            // 创建 BeanOutputConverter
            var outputConverter = new BeanOutputConverter<>(ContractRiskAnalysisResult.class);
            
            // 获取期望的JSON格式描述
            String format = outputConverter.getFormat();
            
            // 使用新的结构化提示词模板
            PromptTemplate promptTemplate = new PromptTemplate(contractRiskAnalysisStructuredPromptResource);
            
            Map<String, Object> promptValues = Map.of(
                "contractContent", contractContent,
                "format", format
            );
            
            // 调用AI并直接获取解析后的Bean
            String rawResponse = chatClient.prompt(promptTemplate.create(promptValues))
                    .call()
                    .content();
            
            // 解析结构化结果
            ContractRiskAnalysisResult result = outputConverter.convert(rawResponse);
            
            // 使用AI的原始响应作为originalAnalysis，避免重复调用
            result.setOriginalAnalysis(rawResponse);
            
            log.info("结构化风险分析完成，识别出 {} 个风险维度", 
                    result.getRiskDimensions() != null ? result.getRiskDimensions().size() : 0);
            
            return result;
            
        } catch (Exception e) {
            log.error("结构化合同风险分析失败，请检查AI模型响应格式或优化Prompt", e);
            
            // 返回一个包含错误信息的基础结果，而不是使用脆弱的字符串解析
            return ContractRiskAnalysisResult.builder()
                    .overallRiskLevel("UNKNOWN")
                    .coreRiskAlerts(List.of(
                        "AI结构化分析暂时不可用，请稍后重试",
                        "建议人工审查合同内容"
                    ))
                    .priorityRecommendations(List.of(
                        "联系技术支持优化AI模型配置",
                        "建议咨询专业法律人士进行人工审查"
                    ))
                    .complianceScore(50)
                    .originalAnalysis("结构化分析失败: " + e.getMessage())
                    .build();
        }
    }

    /**
     * 本地RAG聊天结果记录类
     */
    public record LocalRagResult(String answer, boolean hasKnowledgeMatch, int sourceCount) {
    }
    
    // ==================== Resilience4j 降级方法 ====================
    
    /**
     * chatWithMemory 的降级方法
     * 当 AI 服务不可用时提供友好的降级响应
     */
    private LocalRagResult chatWithMemoryFallback(String question, String conversationId, 
                                                   ChatMemoryService.ModelType modelType, Throwable t) {
        log.warn("AI聊天服务暂时不可用，触发降级处理: conversationId={}, modelType={}, 原因: {}", 
                conversationId, modelType, t.getMessage());
        
        String fallbackAnswer = "抱歉，AI法律助手服务当前繁忙，请稍后再试。\n\n" +
                "如您有紧急法律咨询需求，建议：\n" +
                "1. 联系专业律师进行人工咨询\n" +
                "2. 稍后重试本系统\n" +
                "3. 查阅相关法律文档获取基础信息";
        
        return new LocalRagResult(fallbackAnswer, false, 0);
    }
    
    /**
     * simpleChatWithMemory 的降级方法
     */
    private String simpleChatWithMemoryFallback(String message, String conversationId, 
                                                 ChatMemoryService.ModelType modelType, Throwable t) {
        log.warn("AI简单聊天服务暂时不可用，触发降级处理: conversationId={}, modelType={}, 原因: {}", 
                conversationId, modelType, t.getMessage());
        
        return "抱歉，AI服务当前繁忙，无法处理您的消息。请稍后重试，或联系系统管理员。";
    }
    
    /**
     * analyzeContractRisk 的降级方法
     */
    private String analyzeContractRiskFallback(String contractContent, Throwable t) {
        log.warn("合同风险分析服务暂时不可用，触发降级处理，合同长度: {}, 原因: {}", 
                contractContent.length(), t.getMessage());
        
        return "### 合同分析服务暂时不可用\n\n" +
                "由于AI分析服务当前繁忙，暂时无法完成合同风险分析。\n\n" +
                "**建议措施：**\n" +
                "1. **稍后重试**：系统正在恢复中，请在几分钟后重新提交分析请求\n" +
                "2. **人工审查**：建议将合同提交给专业法律顾问进行详细审查\n" +
                "3. **基础检查**：您可以先自行核对合同的基本要素（双方信息、标的、价款、期限等）\n\n" +
                "**合同基本信息：**\n" +
                "- 合同内容长度：" + contractContent.length() + " 字符\n" +
                "- 分析状态：服务降级中\n\n" +
                "如需紧急处理，请联系专业律师或技术支持团队。";
    }
    
    /**
     * analyzeContractRiskStructured 的降级方法
     */
    private ContractRiskAnalysisResult analyzeContractRiskStructuredFallback(String contractContent, Throwable t) {
        log.warn("结构化合同风险分析服务暂时不可用，触发降级处理，合同长度: {}, 原因: {}", 
                contractContent.length(), t.getMessage());
        
        return ContractRiskAnalysisResult.builder()
                .overallRiskLevel("UNKNOWN")
                .coreRiskAlerts(List.of(
                    "AI分析服务当前繁忙，暂时无法提供智能风险分析",
                    "建议您稍后重试或寻求专业法律人士的帮助",
                    "系统已记录您的请求，我们正在努力恢复服务"
                ))
                .priorityRecommendations(List.of(
                    "请稍后（5-10分钟）重新提交分析请求",
                    "建议将合同提交给专业律师进行人工审查",
                    "可先行核对合同的基本要素和关键条款",
                    "如有紧急需求，请联系技术支持团队"
                ))
                .complianceScore(0)
                .riskDimensions(List.of())
                .originalAnalysis(analyzeContractRiskFallback(contractContent, t))
                .build();
    }
}
