package com.river.LegalAssistant.service.advanced;

import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;
import dev.langchain4j.rag.DefaultRetrievalAugmentor;
import dev.langchain4j.rag.RetrievalAugmentor;
import dev.langchain4j.rag.content.Content;
import dev.langchain4j.rag.query.Query;
import dev.langchain4j.service.AiServices;
import dev.langchain4j.service.Result;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import jakarta.annotation.PostConstruct;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CompletableFuture;

/**
 * 高级法律RAG服务
 * 基于LangChain4j Advanced RAG框架的完整实现
 * 
 * 整合了以下组件：
 * - LegalQueryTransformer: 查询转换和优化
 * - LegalQueryRouter: 智能查询路由
 * - LegalContentRetriever: 多源内容检索
 * - LegalContentAggregator: 内容聚合和重排序
 * - LegalContentInjector: 上下文注入
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AdvancedLegalRagService {

    @Qualifier("langchain4jChatModel")
    private final ChatModel chatModel;
    
    @Qualifier("langchain4jStreamingChatModel")
    private final StreamingChatModel streamingChatModel;
    
    private final LegalQueryTransformer queryTransformer;
    private final LegalQueryRouter queryRouter;
    private final LegalContentRetriever contentRetriever;
    private final LegalContentAggregator contentAggregator;
    private final LegalContentInjector contentInjector;
    
    // 会话记忆管理
    private final Map<String, MessageWindowChatMemory> sessionMemories = new ConcurrentHashMap<>();
    
    // RAG组件
    private RetrievalAugmentor retrievalAugmentor;
    private LegalAssistant legalAssistant;
    
    // 服务状态
    private boolean initialized = false;
    private String initializationError = null;

    /**
     * 高级法律助手接口
     */
    public interface LegalAssistant {
        /**
         * 带源信息的法律咨询
         * @param userMessage 用户问题
         * @return 包含答案和来源的结果
         */
        Result<String> chatWithSources(String userMessage);
        
        /**
         * 基础法律咨询
         * @param userMessage 用户问题
         * @return AI回答
         */
        String chat(String userMessage);
    }

    @PostConstruct
    public void initializeAdvancedRag() {
        log.info("开始初始化 Advanced Legal RAG 服务...");
        
        try {
            // 1. 构建检索增强器
            buildRetrievalAugmentor();
            
            // 2. 构建AI服务
            buildAiService();
            
            initialized = true;
            log.info("Advanced Legal RAG 服务初始化成功");
            
        } catch (Exception e) {
            log.error("Advanced Legal RAG 服务初始化失败", e);
            initializationError = e.getMessage();
            initialized = false;
        }
    }

    /**
     * 高级RAG法律咨询
     */
    public AdvancedRagResult advancedLegalChat(String question, String sessionId) {
        log.info("Advanced RAG 处理法律咨询: {}, 会话: {}", question, sessionId);
        
        if (!initialized) {
            return new AdvancedRagResult(
                "Advanced RAG 服务未正确初始化: " + initializationError,
                false,
                0,
                Collections.emptyList(),
                sessionId,
                null,
                null
            );
        }
        
        if (question == null || question.trim().isEmpty()) {
            return new AdvancedRagResult(
                "请提供具体的法律问题。",
                false,
                0,
                Collections.emptyList(),
                sessionId,
                "EMPTY_QUESTION",
                null
            );
        }
        
        try {
            long startTime = System.currentTimeMillis();
            
            // 获取带源信息的结果
            Result<String> result = legalAssistant.chatWithSources(question);
            
            long duration = System.currentTimeMillis() - startTime;
            
            // 提取源信息详情
            List<SourceDetail> sourceDetails = extractSourceDetails(result.sources());
            
            log.info("Advanced RAG 回答生成完成，耗时: {}ms, 使用源: {}", 
                    duration, sourceDetails.size());
            
            return new AdvancedRagResult(
                result.content(),
                true,
                result.sources().size(),
                sourceDetails,
                sessionId,
                "SUCCESS",
                duration
            );
            
        } catch (Exception e) {
            log.error("Advanced RAG 处理失败", e);
            return new AdvancedRagResult(
                "Advanced RAG 处理您的问题时出现错误: " + e.getMessage(),
                false,
                0,
                Collections.emptyList(),
                sessionId,
                "PROCESSING_ERROR",
                null
            );
        }
    }

    /**
     * 获取服务状态
     */
    public AdvancedRagStatus getStatus() {
        return new AdvancedRagStatus(
            initialized,
            initializationError,
            retrievalAugmentor != null ? "已构建" : "未构建",
            legalAssistant != null ? "已构建" : "未构建",
            sessionMemories.size(),
            getComponentStatus()
        );
    }

    /**
     * 重置会话记忆
     */
    public void resetSessionMemory(String sessionId) {
        sessionMemories.remove(sessionId);
        log.info("重置会话记忆: {}", sessionId);
    }

    /**
     * 重置所有会话记忆
     */
    public void resetAllSessions() {
        int count = sessionMemories.size();
        sessionMemories.clear();
        log.info("重置所有会话记忆，共 {} 个会话", count);
    }

    /**
     * 获取会话统计信息
     */
    public Map<String, Object> getSessionStatistics() {
        return Map.of(
            "totalSessions", sessionMemories.size(),
            "activeSessions", sessionMemories.entrySet().stream()
                .filter(entry -> entry.getValue().messages().size() > 0)
                .count(),
            "lastUpdate", LocalDateTime.now()
        );
    }

    // ==================== 私有方法 ====================

    /**
     * 构建检索增强器
     */
    private void buildRetrievalAugmentor() {
        log.info("构建 Advanced RAG 检索增强器...");
        
        this.retrievalAugmentor = DefaultRetrievalAugmentor.builder()
                .queryTransformer(queryTransformer)
                .queryRouter(queryRouter)
                .contentAggregator(contentAggregator)
                .contentInjector(contentInjector)
                .build();
        
        log.info("检索增强器构建完成");
    }

    /**
     * 构建AI服务
     */
    private void buildAiService() {
        log.info("构建 Advanced RAG AI 服务...");
        
        this.legalAssistant = AiServices.builder(LegalAssistant.class)
                .chatModel(chatModel)
                .retrievalAugmentor(retrievalAugmentor)
                .chatMemoryProvider(sessionId -> 
                    sessionMemories.computeIfAbsent(String.valueOf(sessionId), 
                        k -> MessageWindowChatMemory.withMaxMessages(10)))
                .build();
        
        log.info("Advanced RAG AI 服务构建完成");
    }

    /**
     * 提取源信息详情
     */
    private List<SourceDetail> extractSourceDetails(List<dev.langchain4j.rag.content.Content> sources) {
        if (sources == null || sources.isEmpty()) {
            return Collections.emptyList();
        }
        
        return sources.stream()
            .map(this::convertToSourceDetail)
            .filter(Objects::nonNull)
            .limit(10) // 限制源信息数量
            .toList();
    }

    /**
     * 转换为源信息详情
     */
    private SourceDetail convertToSourceDetail(dev.langchain4j.rag.content.Content content) {
        try {
            String text = content.textSegment().text();
            String source = extractSourceName(text);
            double relevanceScore = calculateRelevanceScore(content);
            String contentType = determineContentType(text);
            
            return new SourceDetail(
                truncateText(text, 200),
                source,
                relevanceScore,
                contentType
            );
            
        } catch (Exception e) {
            log.warn("转换源信息失败", e);
            return null;
        }
    }

    /**
     * 提取源名称
     */
    private String extractSourceName(String text) {
        if (text.contains("民法典")) return "民法典";
        if (text.contains("合同法")) return "合同法";
        if (text.contains("公司法")) return "公司法";
        if (text.contains("案例")) return "法律案例";
        return "法律知识库";
    }

    /**
     * 计算相关性分数
     */
    private double calculateRelevanceScore(dev.langchain4j.rag.content.Content content) {
        // 这里应该从内容的元数据或者聚合器中获取实际的相关性分数
        // 现在返回一个模拟值
        return 0.85;
    }

    /**
     * 确定内容类型
     */
    private String determineContentType(String text) {
        if (text.contains("第") && text.contains("条")) return "法律条文";
        if (text.contains("案例") || text.contains("判决")) return "案例参考";
        if (text.contains("合同") && text.contains("条款")) return "合同条款";
        return "一般资料";
    }

    /**
     * 截断文本
     */
    private String truncateText(String text, int maxLength) {
        if (text.length() <= maxLength) {
            return text;
        }
        return text.substring(0, maxLength) + "...";
    }

    /**
     * 获取组件状态
     */
    private Map<String, String> getComponentStatus() {
        Map<String, String> status = new HashMap<>();
        status.put("queryTransformer", queryTransformer != null ? "正常" : "未初始化");
        status.put("queryRouter", queryRouter != null ? "正常" : "未初始化");
        status.put("contentRetriever", contentRetriever != null ? "正常" : "未初始化");
        status.put("contentAggregator", contentAggregator != null ? "正常" : "未初始化");
        status.put("contentInjector", contentInjector != null ? "正常" : "未初始化");
        return status;
    }

    // ==================== 数据类 ====================

    /**
     * Advanced RAG 查询结果
     */
    public record AdvancedRagResult(
        String answer,
        boolean hasKnowledgeMatch,
        int sourceCount,
        List<SourceDetail> sources,
        String sessionId,
        String status,
        Long duration
    ) {}

    /**
     * 源信息详情
     */
    public record SourceDetail(
        String content,
        String source,
        double relevanceScore,
        String contentType
    ) {}

    /**
     * Advanced RAG 服务状态
     */
    public record AdvancedRagStatus(
        boolean initialized,
        String initializationError,
        String retrievalAugmentorStatus,
        String aiServiceStatus,
        int activeSessionCount,
        Map<String, String> componentStatus
    ) {}

    /**
     * 高级RAG流式法律咨询
     * 使用RAG检索相关知识并流式输出回答
     * 
     * @param question 用户问题
     * @param sessionId 会话ID
     * @param emitter SSE发射器
     * @param responseBuilder 用于累积完整响应的StringBuilder（用于保存历史记录）
     */
    public void advancedLegalChatStream(String question, String sessionId, SseEmitter emitter, StringBuilder responseBuilder) {
        log.info("Advanced RAG 流式处理法律咨询: {}, 会话: {}", question, sessionId);
        
        CompletableFuture.runAsync(() -> {
            try {
                // 1. 检查服务状态
                if (!initialized) {
                    emitter.send(SseEmitter.event()
                        .data(Map.of("type", "error", "error", "Advanced RAG 服务未正确初始化: " + initializationError)));
                    emitter.complete();
                    return;
                }
                
                if (question == null || question.trim().isEmpty()) {
                    emitter.send(SseEmitter.event()
                        .data(Map.of("type", "error", "error", "请提供具体的法律问题")));
                    emitter.complete();
                    return;
                }
                
                // 2. 判断问题类型：是否需要检索法律知识
                String questionLower = question.toLowerCase().trim();
                boolean needsKnowledgeRetrieval = isLegalQuestion(questionLower);
                
                String fullPrompt;
                int sourceCount = 0;
                
                if (needsKnowledgeRetrieval) {
                    // 需要检索法律知识的问题
                    log.info("识别为法律问题，开始检索相关法律知识...");
                    Query query = Query.from(question);
                    
                    // 通过查询路由器获取相关内容
                    List<Content> relevantContents = contentRetriever.retrieve(query);
                    sourceCount = relevantContents.size();
                    log.info("检索到 {} 条相关内容", sourceCount);
                    
                    // 构建包含上下文的提示
                    fullPrompt = buildPromptWithKnowledge(question, relevantContents);
                } else {
                    // 不需要检索的常规问题（问候、系统询问等）
                    log.info("识别为常规问题，无需检索法律知识");
                    fullPrompt = buildPromptWithoutKnowledge(question);
                }
                
                log.debug("构建完整提示，长度: {}", fullPrompt.length());
                
                // 4. 发送开始事件（仅使用data，不设置event name）
                final int finalSourceCount = sourceCount;
                emitter.send(SseEmitter.event()
                    .data(Map.of("type", "start", "sourceCount", finalSourceCount)));
                
                // 5. 使用流式模型生成回答
                StringBuilder fullResponse = new StringBuilder();
                
                streamingChatModel.chat(
                    List.of(UserMessage.from(fullPrompt)),
                    new StreamingChatResponseHandler() {
                        @Override
                        public void onPartialResponse(String partialResponse) {
                            try {
                                fullResponse.append(partialResponse);
                                // 同时累积到外部传入的responseBuilder（用于保存历史记录）
                                if (responseBuilder != null) {
                                    responseBuilder.append(partialResponse);
                                }
                                emitter.send(SseEmitter.event()
                                    .data(Map.of("type", "content", "content", partialResponse)));
                                log.debug("发送内容片段: {}", partialResponse.length() > 50 ? 
                                    partialResponse.substring(0, 50) + "..." : partialResponse);
                            } catch (Exception e) {
                                log.error("发送内容片段失败", e);
                            }
                        }
                        
                        @Override
                        public void onCompleteResponse(ChatResponse response) {
                            try {
                                log.info("Advanced RAG 流式响应完成，总长度: {}", fullResponse.length());
                                
                                // 发送完成事件（仅使用data，不设置event name）
                                emitter.send(SseEmitter.event()
                                    .data(Map.of(
                                        "type", "done",
                                        "sourceCount", finalSourceCount,
                                        "sessionId", sessionId
                                    )));
                                emitter.complete();
                            } catch (Exception e) {
                                log.error("发送完成事件失败", e);
                                emitter.completeWithError(e);
                            }
                        }
                        
                        @Override
                        public void onError(Throwable error) {
                            log.error("Advanced RAG 流式生成失败", error);
                            try {
                                emitter.send(SseEmitter.event()
                                    .data(Map.of("type", "error", "error", "生成回答失败: " + error.getMessage())));
                                emitter.completeWithError(error);
                            } catch (Exception e) {
                                log.error("发送错误事件失败", e);
                            }
                        }
                    }
                );
                
            } catch (Exception e) {
                log.error("Advanced RAG 流式处理失败", e);
                try {
                    emitter.send(SseEmitter.event()
                        .data(Map.of("type", "error", "error", "处理失败: " + e.getMessage())));
                    emitter.completeWithError(e);
                } catch (Exception sendError) {
                    log.error("发送错误事件失败", sendError);
                }
            }
        });
    }

    /**
     * 判断是否是法律相关问题
     * 对于问候语、系统询问等常规问题返回 false
     */
    private boolean isLegalQuestion(String questionLower) {
        // 常规问候和系统询问关键词
        String[] greetings = {"你好", "您好", "hello", "hi", "早上好", "下午好", "晚上好"};
        String[] systemQuestions = {
            "你是谁", "你是什么", "你叫什么", "你的名字",
            "你有什么功能", "你能做什么", "你可以", "你会",
            "如何使用", "怎么用", "使用方法", "帮助"
        };
        
        // 检查是否是纯问候语
        for (String greeting : greetings) {
            if (questionLower.equals(greeting) || questionLower.equals(greeting + "!") || 
                questionLower.equals(greeting + "！") || questionLower.equals(greeting + "。")) {
                return false;
            }
        }
        
        // 检查是否是系统询问
        for (String sysQ : systemQuestions) {
            if (questionLower.contains(sysQ)) {
                return false;
            }
        }
        
        // 法律相关关键词
        String[] legalKeywords = {
            "法律", "合同", "条款", "违约", "赔偿", "诉讼", "仲裁",
            "民法", "刑法", "商法", "劳动法", "公司法",
            "权利", "义务", "责任", "起诉", "上诉", "判决",
            "协议", "条例", "规定", "办法", "通知",
            "甲方", "乙方", "当事人", "原告", "被告",
            "审查", "审核", "分析", "咨询", "建议"
        };
        
        // 如果包含法律关键词，认为是法律问题
        for (String keyword : legalKeywords) {
            if (questionLower.contains(keyword)) {
                return true;
            }
        }
        
        // 问题长度超过10个字，可能是具体问题，进行检索
        if (questionLower.length() > 10) {
            return true;
        }
        
        // 默认对短问题不进行检索
        return false;
    }

    /**
     * 构建包含法律知识的提示词
     */
    private String buildPromptWithKnowledge(String question, List<Content> relevantContents) {
        StringBuilder promptBuilder = new StringBuilder();
        
        promptBuilder.append("你是一位专业的AI法律助手，名字是「法律小助手」。\n\n");
        promptBuilder.append("【你的能力】\n");
        promptBuilder.append("- 提供专业的法律咨询和建议\n");
        promptBuilder.append("- 解读法律法规和合同条款\n");
        promptBuilder.append("- 分析法律风险和问题\n\n");
        
        // 添加检索到的法律知识
        if (!relevantContents.isEmpty()) {
            promptBuilder.append("【参考法律知识】\n");
            int contextCount = Math.min(relevantContents.size(), 5);
            for (int i = 0; i < contextCount; i++) {
                Content content = relevantContents.get(i);
                String text = content.textSegment().text();
                // 限制每条内容的长度，避免上下文过长
                if (text.length() > 500) {
                    text = text.substring(0, 500) + "...";
                }
                promptBuilder.append(i + 1).append(". ").append(text).append("\n\n");
            }
        }
        
        promptBuilder.append("【重要提示】\n");
        promptBuilder.append("- 如果参考知识与问题相关，请基于这些知识提供专业回答\n");
        promptBuilder.append("- 如果参考知识与问题不太相关，请基于你的法律常识回答\n");
        promptBuilder.append("- 回答要准确、专业、易懂，避免过于生硬\n");
        promptBuilder.append("- 不要直接复制粘贴参考知识的原文\n\n");
        
        promptBuilder.append("【用户问题】\n").append(question).append("\n\n");
        promptBuilder.append("请提供专业的回答：");
        
        return promptBuilder.toString();
    }

    /**
     * 构建不包含法律知识的提示词（用于常规对话）
     */
    private String buildPromptWithoutKnowledge(String question) {
        StringBuilder promptBuilder = new StringBuilder();
        
        promptBuilder.append("你是一位专业的AI法律助手，名字是「法律小助手」。\n\n");
        
        promptBuilder.append("【你的身份和能力】\n");
        promptBuilder.append("- 我是一个专业的AI法律助手，可以帮助你解答法律问题\n");
        promptBuilder.append("- 我可以提供法律咨询、合同审查、条款解读等服务\n");
        promptBuilder.append("- 我可以分析法律风险、解释法律概念、提供法律建议\n");
        promptBuilder.append("- 我掌握民法、刑法、合同法、公司法等多个法律领域的知识\n\n");
        
        promptBuilder.append("【使用建议】\n");
        promptBuilder.append("- 你可以直接向我咨询具体的法律问题\n");
        promptBuilder.append("- 你可以上传合同文件让我帮你审查\n");
        promptBuilder.append("- 你可以询问法律条文的解释和应用\n\n");
        
        promptBuilder.append("【用户问题】\n").append(question).append("\n\n");
        promptBuilder.append("请用友好、专业的语气回答：");
        
        return promptBuilder.toString();
    }
}
