package com.river.LegalAssistant.controller;

import com.river.LegalAssistant.dto.*;
import com.river.LegalAssistant.service.AiService;
import com.river.LegalAssistant.service.AgentService;
import com.river.LegalAssistant.service.ChatMemoryService;
import com.river.LegalAssistant.service.DeepSeekService;
import com.river.LegalAssistant.service.advanced.AdvancedLegalRagService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * AI 服务控制器
 */
@RestController
@RequestMapping("/ai")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "AI服务", description = "核心AI对话功能，包括智能法律问答、合同分析、Advanced RAG等")
public class AiController {

    private final AiService aiService;
    private final AgentService agentService;
    private final DeepSeekService deepSeekService;
    private final AdvancedLegalRagService advancedRagService;
    private final ChatMemoryService chatMemoryService;


    /**
     * 本地知识库聊天接口
     * 使用本地Ollama模型 + 知识库检索，提供基于本地数据的智能问答
     */
    @PostMapping(value = "/chat", consumes = {"application/json", "application/x-www-form-urlencoded"})
    @Operation(summary = "本地知识库聊天", description = "基于本地知识库进行检索增强生成（RAG）对话，使用Ollama本地模型处理。如果找到相关结果则生成回答，未找到则提示没有相关答案。")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "对话成功", content = @Content(mediaType = "application/json", schema = @Schema(example = "{\"question\":\"合同中的违约责任怎么规定？\",\"answer\":\"根据《民法典》第五百七十七条...\",\"timestamp\":\"2023-10-27T10:05:00\",\"type\":\"local_rag_chat\",\"hasKnowledgeMatch\":true}"))),
        @ApiResponse(responseCode = "400", description = "请求参数错误，例如问题为空", content = @Content(mediaType = "application/json", schema = @Schema(example = "{\"error\":\"问题不能为空\"}"))),
        @ApiResponse(responseCode = "500", description = "服务器内部错误", content = @Content(mediaType = "application/json", schema = @Schema(example = "{\"error\":\"本地聊天服务暂时不可用: [错误详情]\"}")))
    })
    public ResponseEntity<Map<String, Object>> localKnowledgeChat(
            @Parameter(description = "包含聊天消息的JSON对象", schema = @Schema(implementation = ChatRequest.class))
            @RequestBody(required = false) ChatRequest request,
            @Parameter(description = "聊天消息内容（当Content-Type为x-www-form-urlencoded时使用）")
            @RequestParam(required = false) String message) {
        
        // 优先使用JSON参数，如果不存在则使用form参数
        String question;
        if (request != null) {
            question = request.getMessage();
        } else {
            question = message;
        }
        
        if (question == null || question.trim().isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "问题不能为空"));
        }

        try {
            // 使用优化后的本地RAG服务
            AiService.LocalRagResult ragResult = aiService.localKnowledgeChat(question);
            
            Map<String, Object> result = new HashMap<>();
            result.put("question", question);
            result.put("answer", ragResult.answer());
            result.put("hasKnowledgeMatch", ragResult.hasKnowledgeMatch());
            result.put("knowledgeSourceCount", ragResult.sourceCount());
            result.put("timestamp", LocalDateTime.now());
            result.put("type", "local_rag_chat");
            result.put("model", "ollama_local");
            
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            log.error("本地知识库聊天请求处理失败", e);
            return ResponseEntity.internalServerError()
                    .body(Map.of("error", "本地聊天服务暂时不可用: " + e.getMessage()));
        }
    }






    // ==================== Agent 相关接口 ====================

    /**
     * 高级法律顾问咨询接口
     * 使用DeepSeek AI + ReAct Agent，具备工具调用能力，专门处理法律相关问题
     */
    @PostMapping(value = "/chat/legal", consumes = {"application/json", "application/x-www-form-urlencoded"})
    @Operation(summary = "高级法律顾问", description = "使用DeepSeek AI驱动的专业法律顾问Agent。具备强大的推理能力和工具调用功能，专门处理复杂的法律问题，提供专业、准确的法律咨询服务。")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "咨询成功", content = @Content(mediaType = "application/json", schema = @Schema(example = "{\"question\":\"最新的《公司法》有什么变化？\",\"answer\":\"最新的《公司法》修订主要体现在...（DeepSeek AI分析）\",\"timestamp\":\"2023-10-27T10:35:00\",\"type\":\"deepseek_legal_consultation\",\"model\":\"deepseek-chat\"}"))),
        @ApiResponse(responseCode = "400", description = "请求参数错误，例如问题为空", content = @Content(mediaType = "application/json", schema = @Schema(example = "{\"error\":\"咨询问题不能为空\"}"))),
        @ApiResponse(responseCode = "503", description = "DeepSeek服务不可用", content = @Content(mediaType = "application/json", schema = @Schema(example = "{\"error\":\"高级法律顾问服务暂时不可用\"}"))),
        @ApiResponse(responseCode = "500", description = "服务器内部错误", content = @Content(mediaType = "application/json", schema = @Schema(example = "{\"error\":\"智能法律顾问暂时不可用: [错误详情]\"}")))
    })
    public ResponseEntity<Map<String, Object>> consultLegalMatter(
            @Parameter(description = "包含咨询问题的JSON对象", schema = @Schema(implementation = ChatRequest.class))
            @RequestBody(required = false) ChatRequest request,
            @Parameter(description = "咨询问题内容（当Content-Type为x-www-form-urlencoded时使用）")
            @RequestParam(required = false) String message) {
        
        // 优先使用JSON参数，如果不存在则使用form参数
        String question;
        if (request != null) {
            question = request.getMessage();
        } else {
            question = message;
        }
        
        if (question == null || question.trim().isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "咨询问题不能为空"));
        }

        try {
            // 检查DeepSeek服务是否可用
            if (!deepSeekService.isAvailable()) {
                return ResponseEntity.status(503)
                        .body(Map.of("error", "高级法律顾问服务暂时不可用，请稍后重试"));
            }

            // 使用Agent进行法律咨询（会自动选择合适的AI服务）
            AgentService.ConsultationResult consultationResult = agentService.consultLegalMatterWithDetails(question);
            
            Map<String, Object> result = new HashMap<>();
            result.put("question", question);
            result.put("answer", consultationResult.answer());
            result.put("timestamp", LocalDateTime.now());
            result.put("type", "advanced_legal_consultation");
            result.put("model", consultationResult.modelUsed());
            result.put("service", consultationResult.serviceUsed());
            result.put("isDeepSeek", consultationResult.isDeepSeekUsed());
            
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            log.error("DeepSeek法律咨询失败", e);
            return ResponseEntity.internalServerError()
                    .body(Map.of("error", "高级法律顾问暂时不可用: " + e.getMessage()));
        }
    }

    /**
     * 高级法律顾问咨询接口（流式输出）
     * 使用DeepSeek AI + ReAct Agent，通过SSE实时流式推送响应
     */
    @PostMapping(value = "/chat/legal/stream", consumes = {"application/json", "application/x-www-form-urlencoded"})
    @Operation(summary = "高级法律顾问（流式）", description = "使用DeepSeek AI驱动的专业法律顾问Agent，通过Server-Sent Events(SSE)实时流式推送响应内容，提供更好的用户体验。")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "流式响应启动成功", content = @Content(mediaType = "text/event-stream")),
        @ApiResponse(responseCode = "400", description = "请求参数错误"),
        @ApiResponse(responseCode = "503", description = "DeepSeek服务不可用")
    })
    public org.springframework.web.servlet.mvc.method.annotation.SseEmitter consultLegalMatterStream(
            @Parameter(description = "包含咨询问题的JSON对象", schema = @Schema(implementation = ChatRequest.class))
            @RequestBody(required = false) ChatRequest request,
            @Parameter(description = "咨询问题内容（当Content-Type为x-www-form-urlencoded时使用）")
            @RequestParam(required = false) String message) {
        
        // 优先使用JSON参数，如果不存在则使用form参数
        String question;
        if (request != null) {
            question = request.getMessage();
        } else {
            question = message;
        }
        
        log.info("开始流式法律咨询，问题: {}", question);
        
        // 创建SSE发射器，设置超时时间为3分钟
        org.springframework.web.servlet.mvc.method.annotation.SseEmitter emitter = 
            new org.springframework.web.servlet.mvc.method.annotation.SseEmitter(3 * 60 * 1000L);
        
        // 设置回调
        emitter.onCompletion(() -> log.info("流式法律咨询完成"));
        emitter.onTimeout(() -> {
            log.warn("流式法律咨询超时");
            emitter.completeWithError(new RuntimeException("咨询超时"));
        });
        emitter.onError((ex) -> {
            log.error("流式法律咨询错误", ex);
            emitter.completeWithError(ex);
        });
        
        // 参数验证
        if (question == null || question.trim().isEmpty()) {
            try {
                emitter.send(org.springframework.web.servlet.mvc.method.annotation.SseEmitter.event()
                    .name("error")
                    .data(Map.of("error", "咨询问题不能为空")));
                emitter.complete();
            } catch (Exception e) {
                log.error("发送错误事件失败", e);
            }
            return emitter;
        }
        
        // 创建StringBuilder用于累积响应（此接口不保存历史，但需要传递参数）
        StringBuilder responseBuilder = new StringBuilder();
        
        // 异步执行流式咨询
        agentService.consultLegalMatterStream(question, emitter, responseBuilder);
        
        return emitter;
    }

    /**
     * 重置Agent对话记忆
     */
    @PostMapping("/agent/reset-memory")
    @Operation(summary = "重置对话记忆", description = "清除Agent在当前会话中的所有对话历史记录，开始一次全新的对话。")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "重置成功", content = @Content(mediaType = "application/json", schema = @Schema(example = "{\"status\":\"success\",\"message\":\"对话记忆已重置\",\"timestamp\":\"2023-10-27T10:50:00\"}"))),
        @ApiResponse(responseCode = "500", description = "服务器内部错误", content = @Content(mediaType = "application/json", schema = @Schema(example = "{\"status\":\"error\",\"message\":\"重置对话记忆失败\",\"error\":\"[错误详情]\",\"timestamp\":\"2023-10-27T10:50:00\"}")))
    })
    public ResponseEntity<Map<String, Object>> resetAgentMemory() {
        try {
            agentService.resetAllChatMemories();
            
            Map<String, Object> result = new HashMap<>();
            result.put("status", "success");
            result.put("message", "对话记忆已重置");
            result.put("timestamp", LocalDateTime.now());
            
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            log.error("重置Agent对话记忆失败", e);
            return ResponseEntity.internalServerError()
                    .body(Map.of(
                        "status", "error",
                        "message", "重置对话记忆失败",
                        "error", e.getMessage(),
                        "timestamp", LocalDateTime.now()
                    ));
        }
    }



    // ==================== DeepSeek 专用接口 ====================

    /**
     * DeepSeek推理模型接口
     * 使用deepseek-reasoner模型进行复杂推理，提供思维链过程
     */
    @PostMapping("/deepseek/reasoning")
    @Operation(summary = "DeepSeek推理模型", description = "使用DeepSeek的推理模型（deepseek-reasoner）进行复杂逻辑推理，可以查看模型的思维链（Chain of Thought）过程。")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "推理成功", content = @Content(mediaType = "application/json", schema = @Schema(example = "{\"question\":\"9.11和9.8哪个更大？\",\"reasoning_process\":\"...\",\"final_answer\":\"9.11更大\",\"timestamp\":\"2023-10-27T11:00:00\",\"type\":\"deepseek_reasoning\"}"))),
        @ApiResponse(responseCode = "400", description = "请求参数错误", content = @Content(mediaType = "application/json", schema = @Schema(example = "{\"error\":\"问题不能为空\"}"))),
        @ApiResponse(responseCode = "503", description = "DeepSeek服务不可用", content = @Content(mediaType = "application/json", schema = @Schema(example = "{\"error\":\"DeepSeek推理服务暂时不可用\"}")))
    })
    public ResponseEntity<Map<String, Object>> deepSeekReasoning(@Valid @RequestBody ChatRequest request) {
        String question = request.getMessage();
        
        if (question == null || question.trim().isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "问题不能为空"));
        }

        try {
            // 检查DeepSeek服务是否可用
            if (!deepSeekService.isAvailable()) {
                return ResponseEntity.status(503)
                        .body(Map.of("error", "DeepSeek推理服务暂时不可用"));
            }

            DeepSeekService.ReasoningResult result = deepSeekService.reasoningChat(question);
            
            Map<String, Object> response = new HashMap<>();
            response.put("question", question);
            response.put("reasoning_process", result.reasoningProcess());
            response.put("final_answer", result.finalAnswer());
            response.put("has_reasoning", result.hasReasoning());
            response.put("timestamp", LocalDateTime.now());
            response.put("type", "deepseek_reasoning");
            
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("DeepSeek推理失败", e);
            return ResponseEntity.internalServerError()
                    .body(Map.of("error", "推理服务暂时不可用: " + e.getMessage()));
        }
    }



    // ==================== LangChain4j Advanced RAG 接口 ====================

    /**
     * LangChain4j Advanced RAG 法律咨询
     * 使用完整的Advanced RAG框架提供最高质量的法律问答
     */
    @PostMapping(value = "/advanced-rag/chat", consumes = {"application/json", "application/x-www-form-urlencoded"})
    @Operation(summary = "Advanced RAG 法律咨询", 
               description = "基于LangChain4j Advanced RAG框架的高级法律智能问答，包含查询转换、多源检索、重排序等完整流程")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "咨询成功", content = @Content(mediaType = "application/json")),
        @ApiResponse(responseCode = "400", description = "请求参数错误"),
        @ApiResponse(responseCode = "500", description = "服务器内部错误")
    })
    public ResponseEntity<Map<String, Object>> advancedRagChat(
            @Parameter(description = "法律问题", required = true, example = "合同违约后的损害赔偿如何计算？")
            @RequestParam String question,
            @Parameter(description = "会话ID", example = "user123_session1")
            @RequestParam(defaultValue = "default") String sessionId) {
        
        log.info("Advanced RAG 法律咨询请求: {}, 会话: {}", question, sessionId);
        
        try {
            if (question == null || question.trim().isEmpty()) {
                return ResponseEntity.badRequest()
                        .body(Map.of(
                            "error", "问题不能为空",
                            "timestamp", LocalDateTime.now()
                        ));
            }
            
            AdvancedLegalRagService.AdvancedRagResult result = 
                advancedRagService.advancedLegalChat(question, sessionId);
            
            Map<String, Object> response = new HashMap<>();
            response.put("question", question);
            response.put("answer", result.answer());
            response.put("success", result.hasKnowledgeMatch());
            response.put("sourceCount", result.sourceCount());
            response.put("sources", result.sources());
            response.put("sessionId", result.sessionId());
            response.put("status", result.status());
            response.put("duration", result.duration());
            response.put("timestamp", LocalDateTime.now());
            response.put("type", "advanced_rag_chat");
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            log.error("Advanced RAG 处理失败", e);
            return ResponseEntity.status(500)
                    .body(Map.of(
                        "error", "Advanced RAG 服务处理失败: " + e.getMessage(),
                        "timestamp", LocalDateTime.now()
                    ));
        }
    }

    /**
     * 获取 Advanced RAG 服务状态
     */
    @GetMapping("/advanced-rag/status")
    @Operation(summary = "Advanced RAG 服务状态", 
               description = "获取LangChain4j Advanced RAG服务的运行状态和组件信息")
    @ApiResponse(responseCode = "200", description = "状态获取成功", content = @Content(mediaType = "application/json"))
    public ResponseEntity<Map<String, Object>> getAdvancedRagStatus() {
        
        try {
            AdvancedLegalRagService.AdvancedRagStatus status = advancedRagService.getStatus();
            
            Map<String, Object> response = new HashMap<>();
            response.put("initialized", status.initialized());
            response.put("initializationError", status.initializationError());
            response.put("retrievalAugmentorStatus", status.retrievalAugmentorStatus());
            response.put("aiServiceStatus", status.aiServiceStatus());
            response.put("activeSessionCount", status.activeSessionCount());
            response.put("componentStatus", status.componentStatus());
            response.put("timestamp", LocalDateTime.now());
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            log.error("获取 Advanced RAG 状态失败", e);
            return ResponseEntity.status(500)
                    .body(Map.of(
                        "error", "状态获取失败: " + e.getMessage(),
                        "timestamp", LocalDateTime.now()
                    ));
        }
    }

    /**
     * 重置 Advanced RAG 会话记忆
     */
    @PostMapping("/advanced-rag/reset-session")
    @Operation(summary = "重置会话记忆", description = "重置指定会话的对话记忆")
    @ApiResponse(responseCode = "200", description = "重置成功", content = @Content(mediaType = "application/json"))
    public ResponseEntity<Map<String, Object>> resetAdvancedRagSession(
            @Parameter(description = "会话ID", required = true)
            @RequestParam String sessionId) {
        
        try {
            advancedRagService.resetSessionMemory(sessionId);
            
            return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "会话记忆重置完成",
                "sessionId", sessionId,
                "timestamp", LocalDateTime.now()
            ));
            
        } catch (Exception e) {
            log.error("Advanced RAG 会话重置失败", e);
            return ResponseEntity.status(500)
                    .body(Map.of(
                        "success", false,
                        "error", "会话重置失败: " + e.getMessage(),
                        "timestamp", LocalDateTime.now()
                    ));
        }
    }

    /**
     * 获取 Advanced RAG 会话统计
     */
    @GetMapping("/advanced-rag/session-stats")
    @Operation(summary = "会话统计信息", description = "获取Advanced RAG的会话统计信息")
    @ApiResponse(responseCode = "200", description = "统计获取成功", content = @Content(mediaType = "application/json"))
    public ResponseEntity<Map<String, Object>> getAdvancedRagSessionStats() {
        
        try {
            Map<String, Object> stats = advancedRagService.getSessionStatistics();
            
            return ResponseEntity.ok(stats);
            
        } catch (Exception e) {
            log.error("获取 Advanced RAG 会话统计失败", e);
            return ResponseEntity.status(500)
                    .body(Map.of(
                        "error", "统计获取失败: " + e.getMessage(),
                        "timestamp", LocalDateTime.now()
                    ));
        }
    }




}
