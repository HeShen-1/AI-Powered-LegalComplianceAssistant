package com.river.LegalAssistant.controller;

import com.river.LegalAssistant.dto.UnifiedChatRequest;
import com.river.LegalAssistant.dto.UnifiedChatResponse;
import com.river.LegalAssistant.service.*;
import com.river.LegalAssistant.service.advanced.AdvancedLegalRagService;
import com.river.LegalAssistant.util.JwtTokenUtil;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 统一聊天控制器
 * 
 * 整合了所有聊天功能的统一入口，包括：
 * - 本地知识库聊天（Ollama + RAG）
 * - 高级法律顾问（DeepSeek Agent）
 * - Advanced RAG（LangChain4j）
 * - 对话记忆功能
 * - 流式响应
 * 
 * @author LegalAssistant Team
 * @since 2025-10-02
 */
@RestController
@RequestMapping("/chat")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "统一聊天服务", description = "整合所有AI聊天功能的统一API入口")
public class UnifiedChatController {
    
    private final AiService aiService;
    private final AgentService agentService;
    private final DeepSeekService deepSeekService;
    private final AdvancedLegalRagService advancedRagService;
    private final ChatMemoryService chatMemoryService;
    private final ChatHistoryService chatHistoryService;
    private final JwtTokenUtil jwtTokenUtil;
    private final UserDetailsService userDetailsService;
    
    /**
     * 统一聊天接口
     * 根据请求参数自动选择合适的AI服务和处理流程
     */
    @PostMapping
    @Operation(summary = "统一聊天入口", 
               description = "统一的AI聊天接口，通过参数控制不同的聊天模式：\n" +
                            "- BASIC: 本地Ollama + RAG\n" +
                            "- ADVANCED: DeepSeek Agent + 工具调用\n" +
                            "- ADVANCED_RAG: LangChain4j高级RAG\n" +
                            "支持对话记忆、知识库检索等功能")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "对话成功", 
                    content = @Content(mediaType = "application/json", 
                                     schema = @Schema(implementation = UnifiedChatResponse.class))),
        @ApiResponse(responseCode = "400", description = "请求参数错误"),
        @ApiResponse(responseCode = "503", description = "服务暂时不可用"),
        @ApiResponse(responseCode = "500", description = "服务器内部错误")
    })
    public ResponseEntity<UnifiedChatResponse> chat(
            @Parameter(description = "统一聊天请求", required = true)
            @Valid @RequestBody UnifiedChatRequest request) {
        
        long startTime = System.currentTimeMillis();
        
        log.info("收到统一聊天请求: message={}, modelType={}, conversationId={}, useKnowledgeBase={}", 
                request.getMessage(), request.getModelType(), request.getConversationId(), 
                request.isUseKnowledgeBase());
        
        try {
            // 参数验证
            if (request.getMessage() == null || request.getMessage().trim().isEmpty()) {
                return ResponseEntity.badRequest()
                        .body(UnifiedChatResponse.builder()
                                .answer("错误：消息内容不能为空")
                                .timestamp(LocalDateTime.now())
                                .build());
            }
            
            // 确保会话存在并保存用户消息（如果提供了conversationId）
            String sessionId = request.getConversationId();
            if (sessionId != null && !sessionId.trim().isEmpty()) {
                try {
                    sessionId = chatHistoryService.ensureSessionExistsForCurrentUser(
                        sessionId, request.getMessage());
                    
                    // 获取当前用户ID
                    Long userId = getCurrentUserId();
                    chatHistoryService.saveUserMessage(sessionId, userId, request.getMessage());
                    log.debug("已保存用户消息到会话: {}", sessionId);
                } catch (Exception e) {
                    log.warn("保存用户消息失败，但继续处理请求", e);
                }
            }
            
            // 根据模型类型分发请求
            UnifiedChatResponse response = switch (request.getModelType()) {
                case ADVANCED -> handleAdvancedChat(request);
                case ADVANCED_RAG -> handleAdvancedRagChat(request);
                case UNIFIED -> handleUnifiedChat(request);
                default -> handleBasicChat(request);
            };
            
            // 保存AI助手的回复（如果提供了conversationId）
            if (sessionId != null && !sessionId.trim().isEmpty() && response.getAnswer() != null) {
                try {
                    Map<String, Object> metadata = new HashMap<>();
                    metadata.put("modelType", request.getModelType().toString());
                    
                    // ✅ 优化：记录实际使用的模型，而不是请求参数中的模型
                    String actualModel = extractActualModelFromResponse(response);
                    metadata.put("modelName", actualModel);
                    metadata.put("requestedModel", request.getModelName()); // 保留原始请求信息
                    
                    if (response.getMetadata() != null) {
                        metadata.putAll(response.getMetadata());
                    }
                    
                    chatHistoryService.saveAssistantMessage(sessionId, response.getAnswer(), metadata);
                    log.debug("已保存助手回复到会话: {}, 使用模型: {}", sessionId, actualModel);
                } catch (Exception e) {
                    log.warn("保存助手回复失败", e);
                }
            }
            
            // 设置处理耗时
            long duration = System.currentTimeMillis() - startTime;
            response.setDuration(duration);
            
            log.info("统一聊天请求处理完成，耗时: {} ms", duration);
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            log.error("统一聊天请求处理失败", e);
            return ResponseEntity.internalServerError()
                    .body(UnifiedChatResponse.builder()
                            .question(request.getMessage())
                            .answer("抱歉，服务暂时不可用：" + e.getMessage())
                            .timestamp(LocalDateTime.now())
                            .build());
        }
    }
    
    /**
     * 统一流式聊天接口
     */
    @PostMapping("/stream")
    @Operation(summary = "统一流式聊天", 
               description = "通过Server-Sent Events(SSE)实时流式推送AI响应内容")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "流式响应启动成功", 
                    content = @Content(mediaType = "text/event-stream")),
        @ApiResponse(responseCode = "400", description = "请求参数错误"),
        @ApiResponse(responseCode = "503", description = "服务暂时不可用")
    })
    public SseEmitter chatStream(
            @Parameter(description = "统一聊天请求", required = true)
            @Valid @RequestBody UnifiedChatRequest request,
            HttpServletRequest httpRequest) {
        
        log.info("收到统一流式聊天请求: message={}, modelType={}", 
                request.getMessage(), request.getModelType());
        
        // 创建SSE发射器，设置超时时间为3分钟
        SseEmitter emitter = new SseEmitter(3 * 60 * 1000L);
        
        // 手动验证JWT Token
        String authHeader = httpRequest.getHeader("Authorization");
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            try {
                emitter.send(SseEmitter.event()
                    .name("error")
                    .data(Map.of("error", "未提供有效的认证Token")));
                emitter.complete();
            } catch (Exception e) {
                log.error("发送认证错误事件失败", e);
            }
            return emitter;
        }
        
        String jwtToken = authHeader.substring(7);
        try {
            // 优化JWT验证：只验证Token本身，不查询数据库
            String username = jwtTokenUtil.getUsernameFromToken(jwtToken);
            if (username == null) {
                throw new RuntimeException("无效的Token");
            }
            
            // 验证Token签名和有效期（不需要数据库查询）
            if (jwtTokenUtil.isTokenExpired(jwtToken)) {
                throw new RuntimeException("Token已过期");
            }
            
            // JWT是自包含的，只要签名验证通过就可以信任其内容
            // 避免不必要的数据库查询，提升响应速度
            log.info("用户 {} 通过JWT验证，开始处理流式聊天", username);
        } catch (Exception e) {
            log.error("JWT验证失败: {}", e.getMessage());
            try {
                emitter.send(SseEmitter.event()
                    .name("error")
                    .data(Map.of("error", "认证失败: " + e.getMessage())));
                emitter.complete();
            } catch (Exception sendError) {
                log.error("发送认证错误事件失败", sendError);
            }
            return emitter;
        }
        
        // 用于存储流式响应的完整内容（用于保存历史记录）
        final StringBuilder fullResponseBuilder = new StringBuilder();
        final String[] finalSessionId = {request.getConversationId()};
        
        // 确保会话存在并保存用户消息（如果提供了conversationId）
        if (finalSessionId[0] != null && !finalSessionId[0].trim().isEmpty()) {
            try {
                Long userId = getCurrentUserId();
                finalSessionId[0] = chatHistoryService.ensureSessionExists(
                    finalSessionId[0], userId, request.getMessage());
                chatHistoryService.saveUserMessage(finalSessionId[0], userId, request.getMessage());
                log.debug("已保存用户消息到会话: {}", finalSessionId[0]);
            } catch (Exception e) {
                log.warn("保存用户消息失败，但继续处理流式请求", e);
            }
        }
        
        // 设置回调
        emitter.onCompletion(() -> {
            log.info("流式聊天完成");
            // 保存助手的完整回复（如果有会话ID）
            if (finalSessionId[0] != null && !finalSessionId[0].trim().isEmpty() 
                    && fullResponseBuilder.length() > 0) {
                try {
                    Map<String, Object> metadata = new HashMap<>();
                    metadata.put("modelType", request.getModelType().toString());
                    
                    // ✅ 优化：推断实际使用的模型，而不是使用请求参数
                    String actualModel = inferActualModelFromStreamRequest(request);
                    metadata.put("modelName", actualModel);
                    metadata.put("requestedModel", request.getModelName()); // 保留原始请求信息
                    metadata.put("streaming", true);
                    
                    chatHistoryService.saveAssistantMessage(
                        finalSessionId[0], fullResponseBuilder.toString(), metadata);
                    log.debug("已保存流式助手回复到会话: {}, 使用模型: {}", finalSessionId[0], actualModel);
                } catch (Exception e) {
                    log.warn("保存流式助手回复失败", e);
                }
            }
        });
        emitter.onTimeout(() -> {
            log.warn("流式聊天超时");
            emitter.completeWithError(new RuntimeException("聊天超时"));
        });
        emitter.onError((ex) -> {
            log.error("流式聊天错误", ex);
            emitter.completeWithError(ex);
        });
        
        // 参数验证
        if (request.getMessage() == null || request.getMessage().trim().isEmpty()) {
            try {
                emitter.send(SseEmitter.event()
                    .name("error")
                    .data(Map.of("error", "消息内容不能为空")));
                emitter.complete();
            } catch (Exception e) {
                log.error("发送错误事件失败", e);
            }
            return emitter;
        }
        
        // 根据模型类型选择流式处理
        switch (request.getModelType()) {
            case BASIC -> {
                // 使用Agent的基础流式处理
                log.info("使用基础模式进行流式处理");
                agentService.consultLegalMatterStream(request.getMessage(), emitter, fullResponseBuilder);
            }
            case ADVANCED -> {
                // 使用Agent的高级流式处理
                log.info("使用高级模式进行流式处理");
                agentService.consultLegalMatterStream(request.getMessage(), emitter, fullResponseBuilder);
            }
            case UNIFIED -> {
                // 统一模式根据关键词智能选择流式处理方式
                log.info("使用统一模式进行流式处理");
                handleUnifiedStreamChat(request, emitter, fullResponseBuilder);
            }
            case ADVANCED_RAG -> {
                // Advanced RAG 流式处理
                log.info("使用Advanced RAG模式进行流式处理");
                String sessionId = request.getConversationId() != null ? request.getConversationId() : "default";
                advancedRagService.advancedLegalChatStream(request.getMessage(), sessionId, emitter, fullResponseBuilder);
            }
            default -> {
                // 未知模式
                try {
                    emitter.send(SseEmitter.event()
                        .name("error")
                        .data(Map.of("error", "未知的模型类型，请选择BASIC、ADVANCED或UNIFIED模式")));
                    emitter.complete();
                } catch (Exception e) {
                    log.error("发送错误事件失败", e);
                }
            }
        }
        
        return emitter;
    }
    
    /**
     * 重置会话记忆
     */
    @DeleteMapping("/session/{conversationId}")
    @Operation(summary = "重置会话记忆", description = "清除指定会话的对话记忆")
    @ApiResponse(responseCode = "200", description = "重置成功")
    public ResponseEntity<Map<String, Object>> resetSession(
            @Parameter(description = "会话ID", required = true)
            @PathVariable String conversationId,
            @Parameter(description = "模型名称（可选，留空则清除所有模型的记忆）")
            @RequestParam(required = false) String modelName) {
        
        try {
            if (modelName == null || modelName.trim().isEmpty()) {
                // 清除所有模型的聊天记忆
                chatMemoryService.clearAllModelsChatMemory(conversationId);
                
                return ResponseEntity.ok(Map.of(
                    "success", true,
                    "message", "已清除所有模型的聊天记忆",
                    "conversationId", conversationId,
                    "timestamp", LocalDateTime.now()
                ));
            } else {
                // 清除指定模型的聊天记忆
                ChatMemoryService.ModelType modelType = 
                    ChatMemoryService.ModelType.valueOf(modelName.toUpperCase());
                chatMemoryService.clearChatMemory(conversationId, modelType);
                
                return ResponseEntity.ok(Map.of(
                    "success", true,
                    "message", "已清除指定模型的聊天记忆",
                    "conversationId", conversationId,
                    "modelName", modelName,
                    "timestamp", LocalDateTime.now()
                ));
            }
        } catch (Exception e) {
            log.error("重置会话记忆失败", e);
            return ResponseEntity.internalServerError()
                    .body(Map.of(
                        "success", false,
                        "error", "重置会话记忆失败: " + e.getMessage(),
                        "timestamp", LocalDateTime.now()
                    ));
        }
    }
    
    /**
     * 获取会话统计信息
     */
    @GetMapping("/stats")
    @Operation(summary = "获取聊天统计信息", description = "获取各个AI服务的统计信息")
    @ApiResponse(responseCode = "200", description = "获取成功")
    public ResponseEntity<Map<String, Object>> getStats() {
        try {
            Map<String, Object> stats = new HashMap<>();
            
            // 获取Advanced RAG统计
            try {
                Map<String, Object> advancedRagStats = advancedRagService.getSessionStatistics();
                stats.put("advancedRag", advancedRagStats);
            } catch (Exception e) {
                log.warn("获取Advanced RAG统计失败", e);
            }
            
            // 获取聊天记忆健康状态
            try {
                ChatMemoryService.ChatMemoryHealthInfo healthInfo = chatMemoryService.getHealthInfo();
                stats.put("chatMemory", Map.of(
                    "healthy", healthInfo.isOverallHealthy(),
                    "ollamaHealthy", healthInfo.isOllamaMemoryHealthy(),
                    "deepSeekHealthy", healthInfo.isDeepSeekMemoryHealthy()
                ));
            } catch (Exception e) {
                log.warn("获取聊天记忆健康状态失败", e);
            }
            
            // 获取DeepSeek服务状态
            stats.put("deepSeek", Map.of(
                "available", deepSeekService.isAvailable()
            ));
            
            stats.put("timestamp", LocalDateTime.now());
            
            return ResponseEntity.ok(stats);
            
        } catch (Exception e) {
            log.error("获取统计信息失败", e);
            return ResponseEntity.internalServerError()
                    .body(Map.of(
                        "error", "获取统计信息失败: " + e.getMessage(),
                        "timestamp", LocalDateTime.now()
                    ));
        }
    }
    
    // ==================== 聊天历史管理 API ====================
    
    /**
     * 获取当前用户的所有聊天会话
     */
    @GetMapping("/sessions")
    @Operation(summary = "获取聊天会话列表", description = "获取当前用户的所有聊天会话，按更新时间倒序排列")
    @ApiResponse(responseCode = "200", description = "获取成功")
    public ResponseEntity<?> getSessions() {
        try {
            List<com.river.LegalAssistant.dto.ChatSessionDto> sessions = chatHistoryService.getSessionsForCurrentUser();
            return ResponseEntity.ok(sessions);
        } catch (Exception e) {
            log.error("获取会话列表失败", e);
            return ResponseEntity.internalServerError()
                    .body(Map.of(
                        "error", "获取会话列表失败: " + e.getMessage(),
                        "timestamp", LocalDateTime.now()
                    ));
        }
    }
    
    /**
     * 获取指定会话的所有消息
     */
    @GetMapping("/sessions/{sessionId}")
    @Operation(summary = "获取会话消息", description = "获取指定会话的所有消息")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "获取成功"),
        @ApiResponse(responseCode = "403", description = "无权访问该会话"),
        @ApiResponse(responseCode = "404", description = "会话不存在")
    })
    public ResponseEntity<?> getSessionMessages(
            @Parameter(description = "会话ID", required = true)
            @PathVariable String sessionId) {
        try {
            List<com.river.LegalAssistant.dto.ChatMessageDto> messages = 
                chatHistoryService.getMessagesForSession(sessionId);
            return ResponseEntity.ok(messages);
        } catch (org.springframework.security.access.AccessDeniedException e) {
            log.warn("用户无权访问会话: {}", sessionId);
            return ResponseEntity.status(403)
                    .body(Map.of(
                        "error", "您没有权限访问该会话",
                        "timestamp", LocalDateTime.now()
                    ));
        } catch (Exception e) {
            log.error("获取会话消息失败", e);
            return ResponseEntity.internalServerError()
                    .body(Map.of(
                        "error", "获取会话消息失败: " + e.getMessage(),
                        "timestamp", LocalDateTime.now()
                    ));
        }
    }
    
    /**
     * 删除指定的聊天会话
     */
    @DeleteMapping("/sessions/{sessionId}")
    @Operation(summary = "删除聊天会话", description = "删除指定的聊天会话及其所有消息")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "删除成功"),
        @ApiResponse(responseCode = "403", description = "无权删除该会话"),
        @ApiResponse(responseCode = "404", description = "会话不存在")
    })
    public ResponseEntity<?> deleteSession(
            @Parameter(description = "会话ID", required = true)
            @PathVariable String sessionId) {
        try {
            chatHistoryService.deleteSession(sessionId);
            return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "会话已删除",
                "sessionId", sessionId,
                "timestamp", LocalDateTime.now()
            ));
        } catch (org.springframework.security.access.AccessDeniedException e) {
            log.warn("用户无权删除会话: {}", sessionId);
            return ResponseEntity.status(403)
                    .body(Map.of(
                        "error", "您没有权限删除该会话",
                        "timestamp", LocalDateTime.now()
                    ));
        } catch (Exception e) {
            log.error("删除会话失败", e);
            return ResponseEntity.internalServerError()
                    .body(Map.of(
                        "error", "删除会话失败: " + e.getMessage(),
                        "timestamp", LocalDateTime.now()
                    ));
        }
    }
    
    // ==================== 私有辅助方法 ====================
    
    /**
     * 获取当前登录用户的ID
     */
    private Long getCurrentUserId() {
        Object principal = org.springframework.security.core.context.SecurityContextHolder
                .getContext().getAuthentication().getPrincipal();
        
        if (principal instanceof com.river.LegalAssistant.entity.User) {
            return ((com.river.LegalAssistant.entity.User) principal).getId();
        }
        
        throw new IllegalStateException("当前用户未认证或无法获取用户ID");
    }
    
    /**
     * 处理基础聊天请求（BASIC模式）
     */
    private UnifiedChatResponse handleBasicChat(UnifiedChatRequest request) {
        String question = request.getMessage();
        String conversationId = request.getConversationId();
        boolean useMemory = conversationId != null && !conversationId.trim().isEmpty();
        
        // 如果没有提供conversationId但请求使用记忆，自动生成一个
        if (useMemory && conversationId.trim().isEmpty()) {
            conversationId = chatMemoryService.generateConversationId();
            log.info("自动生成会话ID: {}", conversationId);
        }
        
        AiService.LocalRagResult ragResult;
        
        if (useMemory) {
            // 使用带记忆的RAG聊天
            ChatMemoryService.ModelType modelType = 
                ChatMemoryService.ModelType.valueOf(request.getModelName().toUpperCase());
            ragResult = aiService.chatWithMemory(question, conversationId, modelType);
        } else {
            // 使用无记忆的RAG聊天
            ragResult = aiService.localKnowledgeChat(question);
        }
        
        return UnifiedChatResponse.builder()
                .question(question)
                .answer(ragResult.answer())
                .conversationId(useMemory ? conversationId : null)
                .modelType("BASIC")
                .modelName(request.getModelName())
                .usedKnowledgeBase(request.isUseKnowledgeBase())
                .hasKnowledgeMatch(ragResult.hasKnowledgeMatch())
                .sourceCount(ragResult.sourceCount())
                .memoryEnabled(useMemory)
                .responseType("basic_rag_chat")
                .timestamp(LocalDateTime.now())
                .build();
    }
    
    /**
     * 处理高级聊天请求（ADVANCED模式）
     */
    private UnifiedChatResponse handleAdvancedChat(UnifiedChatRequest request) {
        String question = request.getMessage();
        
        // 检查DeepSeek服务是否可用
        if (!deepSeekService.isAvailable()) {
            return UnifiedChatResponse.builder()
                    .question(question)
                    .answer("抱歉，高级法律顾问服务暂时不可用，请稍后重试或使用基础模式")
                    .modelType("ADVANCED")
                    .modelName("DEEPSEEK")
                    .timestamp(LocalDateTime.now())
                    .build();
        }
        
        // 使用Agent进行法律咨询
        AgentService.ConsultationResult consultationResult = 
            agentService.consultLegalMatterWithDetails(question);
        
        return UnifiedChatResponse.builder()
                .question(question)
                .answer(consultationResult.answer())
                .conversationId(null) // Agent暂不支持会话记忆
                .modelType("ADVANCED")
                .modelName(consultationResult.modelUsed())
                .usedKnowledgeBase(true) // Agent会自动使用知识库
                .hasKnowledgeMatch(true)
                .sourceCount(0) // Agent不返回来源信息
                .memoryEnabled(false)
                .responseType("advanced_legal_consultation")
                .timestamp(LocalDateTime.now())
                .metadata(Map.of(
                    "service", consultationResult.serviceUsed(),
                    "isDeepSeek", consultationResult.isDeepSeekUsed()
                ))
                .build();
    }
    
    /**
     * 处理Advanced RAG聊天请求
     */
    private UnifiedChatResponse handleAdvancedRagChat(UnifiedChatRequest request) {
        String question = request.getMessage();
        String sessionId = request.getConversationId();
        
        if (sessionId == null || sessionId.trim().isEmpty()) {
            sessionId = "default";
        }
        
        AdvancedLegalRagService.AdvancedRagResult result = 
            advancedRagService.advancedLegalChat(question, sessionId);
        
        // 转换SourceDetail列表为字符串列表
        List<String> sourceNames = result.sources().stream()
                .map(sourceDetail -> sourceDetail.source() + " (相关度: " + 
                     String.format("%.2f", sourceDetail.relevanceScore()) + ")")
                .collect(java.util.stream.Collectors.toList());
        
        return UnifiedChatResponse.builder()
                .question(question)
                .answer(result.answer())
                .conversationId(sessionId)
                .modelType("ADVANCED_RAG")
                .modelName("LangChain4j")
                .usedKnowledgeBase(true)
                .hasKnowledgeMatch(result.hasKnowledgeMatch())
                .sourceCount(result.sourceCount())
                .sources(sourceNames)
                .memoryEnabled(true) // Advanced RAG内置记忆功能
                .responseType("advanced_rag_chat")
                .timestamp(LocalDateTime.now())
                .metadata(Map.of(
                    "status", result.status(),
                    "sessionId", result.sessionId()
                ))
                .build();
    }

    /**
     * 处理统一智能聊天请求（UNIFIED模式）
     * 根据用户问题智能选择最优处理策略
     */
    private UnifiedChatResponse handleUnifiedChat(UnifiedChatRequest request) {
        String question = request.getMessage().toLowerCase();
        UnifiedChatResponse response;

        // 判断问题复杂度和类型（与流式模式使用相同的逻辑）
        boolean isComplexAnalysis = isComplexLegalAnalysis(question);
        boolean isSimpleQuery = isSimpleLegalQuery(question);
        
        if (isSimpleQuery) {
            // 简单查询：使用RAG快速响应
            log.info("统一模式路由到 -> Advanced RAG (简单查询)");
            response = handleAdvancedRagChat(request);
            response.setResponseType("unified_to_advanced_rag_simple");
        } else if (isComplexAnalysis) {
            // 复杂分析：使用Agent进行深度推理
            log.info("统一模式路由到 -> Advanced Agent (复杂分析)");
            response = handleAdvancedChat(request);
            response.setResponseType("unified_to_advanced_agent_complex");
        } else {
            // 默认使用Agent
            log.info("统一模式路由到 -> Advanced Agent (默认)");
            response = handleAdvancedChat(request);
            response.setResponseType("unified_to_advanced_agent_default");
        }
        
        // 在元数据中标记这是由统一模式处理的
        Map<String, Object> metadata = new HashMap<>(response.getMetadata() != null ? response.getMetadata() : Map.of());
        metadata.put("unified_mode_routed", true);
        metadata.put("routing_reason", isSimpleQuery ? "simple_query" : (isComplexAnalysis ? "complex_analysis" : "default"));
        response.setMetadata(metadata);
        
        return response;
    }

    /**
     * 处理统一模式的流式聊天请求
     * 根据用户问题智能选择最优流式处理策略
     * 
     * 路由策略：
     * - Agent模式：适合需要复杂推理、多步分析、案例分析的场景
     * - RAG模式：适合简单的法律条文查询、概念解释
     */
    private void handleUnifiedStreamChat(UnifiedChatRequest request, SseEmitter emitter, StringBuilder responseBuilder) {
        String question = request.getMessage().toLowerCase();
        
        // 判断问题复杂度和类型
        boolean isComplexAnalysis = isComplexLegalAnalysis(question);
        boolean isSimpleQuery = isSimpleLegalQuery(question);
        
        if (isSimpleQuery) {
            // 简单查询：使用RAG快速响应
            log.info("统一流式模式路由到 -> Advanced RAG (简单查询)");
            String sessionId = request.getConversationId() != null ? request.getConversationId() : "default";
            advancedRagService.advancedLegalChatStream(request.getMessage(), sessionId, emitter, responseBuilder);
        } else if (isComplexAnalysis) {
            // 复杂分析：使用Agent进行深度推理
            log.info("统一流式模式路由到 -> Advanced Agent (复杂分析)");
            agentService.consultLegalMatterStream(request.getMessage(), emitter, responseBuilder);
        } else {
            // 默认使用Agent（Agent可以根据需要调用RAG工具）
            log.info("统一流式模式路由到 -> Advanced Agent (默认)");
            agentService.consultLegalMatterStream(request.getMessage(), emitter, responseBuilder);
        }
    }
    
    /**
     * 判断是否是复杂的法律分析问题
     * ✅ 优化版：降低阈值，扩展关键词覆盖
     */
    private boolean isComplexLegalAnalysis(String questionLower) {
        // ✅ 扩展案例分析特征
        boolean isCaseAnalysis = questionLower.contains("案例") || 
                                 questionLower.contains("案情") || 
                                 questionLower.contains("核心法律问题") ||
                                 questionLower.contains("如何认定") ||
                                 questionLower.contains("是否构成") ||
                                 questionLower.contains("案件") ||
                                 questionLower.contains("纠纷");
        
        // ✅ 扩展多步推理特征
        boolean needsReasoning = questionLower.contains("分析") || 
                                 questionLower.contains("判断") || 
                                 questionLower.contains("评估") ||
                                 questionLower.contains("应当如何") ||
                                 questionLower.contains("如何处理") ||
                                 questionLower.contains("怎么办") ||
                                 questionLower.contains("怎样处理") ||
                                 questionLower.contains("建议") ||
                                 questionLower.contains("对策");
        
        // ✅ 扩展文档生成特征
        boolean needsGeneration = questionLower.contains("起草") || 
                                  questionLower.contains("撰写") || 
                                  questionLower.contains("生成") ||
                                  questionLower.contains("制作") ||
                                  questionLower.contains("拟定");
        
        // ✅ 扩展审查分析特征
        boolean needsReview = questionLower.contains("审查") || 
                             questionLower.contains("审核") || 
                             questionLower.contains("检查") ||
                             questionLower.contains("风险") ||
                             questionLower.contains("隐患") ||
                             questionLower.contains("问题");
        
        // ✅ 新增：责任认定和法律后果
        boolean needsLiability = questionLower.contains("责任") ||
                                questionLower.contains("赔偿") ||
                                questionLower.contains("承担") ||
                                questionLower.contains("后果") ||
                                questionLower.contains("处罚");
        
        // ✅ 降低长问题阈值到70字符
        boolean isLongQuestion = questionLower.length() > 70;
        
        // ✅ 新增：包含多个关键法律概念（表明问题复杂）
        int conceptCount = 0;
        String[] legalConcepts = {"合同", "违约", "侵权", "赔偿", "诉讼", "仲裁", "协议"};
        for (String concept : legalConcepts) {
            if (questionLower.contains(concept)) {
                conceptCount++;
            }
        }
        boolean hasMultipleConcepts = conceptCount >= 2;
        
        return isCaseAnalysis || needsReasoning || needsGeneration || 
               needsReview || needsLiability || isLongQuestion || hasMultipleConcepts;
    }
    
    /**
     * ✅ 新增：从响应中提取实际使用的模型
     * 分析响应的metadata，确定实际使用了哪个AI模型
     */
    private String extractActualModelFromResponse(UnifiedChatResponse response) {
        if (response == null) {
            return "unknown";
        }
        
        // 1. 优先从响应的modelName字段获取
        if (response.getModelName() != null && !response.getModelName().isEmpty()) {
            return response.getModelName();
        }
        
        // 2. 从metadata中提取服务信息
        if (response.getMetadata() != null) {
            // 检查是否有service字段
            Object service = response.getMetadata().get("service");
            if (service != null) {
                String serviceStr = service.toString().toLowerCase();
                if (serviceStr.contains("deepseek")) {
                    return "deepseek-chat";
                }
            }
            
            // 检查是否有modelUsed字段
            Object modelUsed = response.getMetadata().get("modelUsed");
            if (modelUsed != null) {
                return modelUsed.toString();
            }
            
            // 检查是否有isDeepSeekUsed标记
            Object isDeepSeek = response.getMetadata().get("isDeepSeek");
            if (isDeepSeek instanceof Boolean && (Boolean) isDeepSeek) {
                return "deepseek-chat";
            }
        }
        
        // 3. 根据responseType推断
        String responseType = response.getResponseType();
        if (responseType != null) {
            if (responseType.contains("agent") || responseType.contains("advanced_agent")) {
                // Agent通常使用Deepseek，除非降级
                return "deepseek-chat (inferred)";
            } else if (responseType.contains("rag") || responseType.contains("advanced_rag")) {
                // RAG使用Ollama
                return "qwen2:1.5b (ollama)";
            }
        }
        
        // 4. 默认返回未知
        return "unknown";
    }
    
    /**
     * ✅ 新增：推断流式请求实际使用的模型
     * 根据请求的modelType和消息内容推断实际使用的模型
     * 
     * @param request 统一聊天请求
     * @return 实际使用的模型标识
     */
    private String inferActualModelFromStreamRequest(UnifiedChatRequest request) {
        String questionLower = request.getMessage().toLowerCase();
        
        switch (request.getModelType()) {
            case BASIC:
                // 基础模式使用Ollama
                return "qwen2:1.5b (ollama)";
                
            case ADVANCED:
                // 高级模式使用DeepSeek Agent
                return "deepseek-chat";
                
            case ADVANCED_RAG:
                // Advanced RAG使用LangChain4j配置的模型（通常是Ollama）
                return "qwen2:1.5b (langchain4j)";
                
            case UNIFIED:
                // 统一模式需要根据路由逻辑推断
                boolean isComplexAnalysis = isComplexLegalAnalysis(questionLower);
                boolean isSimpleQuery = isSimpleLegalQuery(questionLower);
                
                if (isSimpleQuery) {
                    // 简单查询路由到Advanced RAG
                    log.debug("流式模型推断: 简单查询 -> Advanced RAG");
                    return "qwen2:1.5b (langchain4j)";
                } else if (isComplexAnalysis) {
                    // 复杂分析路由到Agent (DeepSeek)
                    log.debug("流式模型推断: 复杂分析 -> DeepSeek Agent");
                    return "deepseek-chat";
                } else {
                    // 默认路由到Agent (DeepSeek)
                    log.debug("流式模型推断: 默认 -> DeepSeek Agent");
                    return "deepseek-chat";
                }
                
            default:
                log.warn("未知的模型类型: {}, 使用请求参数中的模型名称", request.getModelType());
                return request.getModelName();
        }
    }
    
    /**
     * 判断是否是简单的法律查询
     * ✅ 优化版：放宽识别标准，增加关键词覆盖
     */
    private boolean isSimpleLegalQuery(String questionLower) {
        // ✅ 扩展简单查询关键词
        String[] queryPatterns = {
            // 定义类查询
            "什么是", "如何定义", "解释一下", "含义", "是什么意思",
            "什么意思", "怎么理解", "具体是指",
            // 列举类查询
            "包括哪些", "有哪些", "都有什么", "包含什么",
            // 查询类操作
            "查询", "查找", "搜索", "找一下", "帮我查",
            // 简单法条查询
            "第几条", "哪一条", "相关规定"
        };
        
        // 检查是否包含简单查询关键词
        for (String pattern : queryPatterns) {
            if (questionLower.contains(pattern)) {
                // ✅ 放宽长度限制到80字符
                if (questionLower.length() < 80) {
                    return true;
                }
            }
        }
        
        // ✅ 新增：如果问题非常短（<20字）且包含疑问词，可能是简单查询
        if (questionLower.length() < 20) {
            String[] questionWords = {"吗", "呢", "么", "？", "?"};
            for (String word : questionWords) {
                if (questionLower.contains(word)) {
                    return true;
                }
            }
        }
        
        return false;
    }
}

