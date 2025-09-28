package com.river.LegalAssistant.controller;

import com.river.LegalAssistant.dto.*;
import com.river.LegalAssistant.service.AiService;
import com.river.LegalAssistant.service.AgentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
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
@RequestMapping("/api/ai")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "AI服务", description = "AI聊天、RAG检索、向量化等功能")
public class AiController {

    private final AiService aiService;
    private final AgentService agentService;

    /**
     * 基础聊天接口
     */
    @PostMapping(value = "/chat", consumes = {"application/json", "application/x-www-form-urlencoded"})
    @Operation(summary = "基础聊天", description = "与AI大模型进行基础对话，不依赖任何外部知识库或工具。")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "对话成功", content = @Content(mediaType = "application/json", schema = @Schema(example = "{\"question\":\"你好\",\"answer\":\"你好！有什么可以帮助你的吗？\",\"timestamp\":\"2023-10-27T10:00:00\",\"type\":\"basic_chat\"}"))),
        @ApiResponse(responseCode = "400", description = "请求参数错误，例如消息内容为空", content = @Content(mediaType = "application/json", schema = @Schema(example = "{\"error\":\"消息不能为空\"}"))),
        @ApiResponse(responseCode = "500", description = "服务器内部错误", content = @Content(mediaType = "application/json", schema = @Schema(example = "{\"error\":\"聊天服务暂时不可用: [错误详情]\"}")))
    })
    public ResponseEntity<Map<String, Object>> chat(
            @Parameter(description = "包含聊天消息的JSON对象", schema = @Schema(implementation = ChatRequest.class))
            @RequestBody(required = false) ChatRequest request,
            @Parameter(description = "聊天消息内容（当Content-Type为x-www-form-urlencoded时使用）")
            @RequestParam(required = false) String message) {
        
        // 优先使用JSON参数，如果不存在则使用form参数
        String finalMessage;
        if (request != null) {
            finalMessage = request.getMessage();
        } else {
            finalMessage = message;
        }
        if (finalMessage == null || finalMessage.trim().isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "消息不能为空"));
        }

        try {
            String response = aiService.chat(finalMessage);
            
            Map<String, Object> result = new HashMap<>();
            result.put("question", finalMessage);
            result.put("answer", response);
            result.put("timestamp", LocalDateTime.now());
            result.put("type", "basic_chat");
            
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            log.error("聊天请求处理失败", e);
            return ResponseEntity.internalServerError()
                    .body(Map.of("error", "聊天服务暂时不可用: " + e.getMessage()));
        }
    }

    /**
     * RAG增强聊天接口
     */
    @PostMapping("/chat/rag")
    @Operation(summary = "RAG增强聊天", description = "基于知识库进行检索增强生成（RAG）对话，回答更具专业性和准确性。")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "对话成功", content = @Content(mediaType = "application/json", schema = @Schema(example = "{\"question\":\"合同中的违约责任怎么规定？\",\"answer\":\"根据《民法典》第五百七十七条...\",\"timestamp\":\"2023-10-27T10:05:00\",\"type\":\"rag_chat\",\"maxResults\":5}"))),
        @ApiResponse(responseCode = "400", description = "请求参数错误，例如问题为空", content = @Content(mediaType = "application/json", schema = @Schema(example = "{\"error\":\"问题不能为空\"}"))),
        @ApiResponse(responseCode = "500", description = "服务器内部错误", content = @Content(mediaType = "application/json", schema = @Schema(example = "{\"error\":\"RAG聊天服务暂时不可用: [错误详情]\"}")))
    })
    public ResponseEntity<Map<String, Object>> chatWithRag(@RequestBody RagChatRequest request) {
        String question = request.getQuestion();
        Integer maxResults = request.getMaxResults();
        
        if (question == null || question.trim().isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "问题不能为空"));
        }

        try {
            String response = aiService.chatWithRag(question);
            
            Map<String, Object> result = new HashMap<>();
            result.put("question", question);
            result.put("answer", response);
            result.put("timestamp", LocalDateTime.now());
            result.put("type", "rag_chat");
            result.put("maxResults", maxResults);
            
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            log.error("RAG聊天请求处理失败", e);
            return ResponseEntity.internalServerError()
                    .body(Map.of("error", "RAG聊天服务暂时不可用: " + e.getMessage()));
        }
    }

    /**
     * 文档向量化接口
     */
    @PostMapping("/embed/document")
    @Operation(summary = "文档向量化", description = "将非结构化文档内容（如法律条文、合同范本）进行向量化，并存入知识库以供RAG检索。")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "文档向量化成功", content = @Content(mediaType = "application/json", schema = @Schema(example = "{\"status\":\"success\",\"message\":\"文档已成功向量化并存储\",\"contentLength\":1234,\"timestamp\":\"2023-10-27T10:10:00\"}"))),
        @ApiResponse(responseCode = "400", description = "请求参数错误，例如文档内容为空", content = @Content(mediaType = "application/json", schema = @Schema(example = "{\"error\":\"文档内容不能为空\"}"))),
        @ApiResponse(responseCode = "500", description = "服务器内部错误", content = @Content(mediaType = "application/json", schema = @Schema(example = "{\"error\":\"文档向量化服务暂时不可用: [错误详情]\"}")))
    })
    public ResponseEntity<Map<String, Object>> embedDocument(@RequestBody EmbedDocumentRequest request) {
        String content = request.getContent();
        Map<String, Object> metadata = request.getMetadata() != null ? request.getMetadata() : new HashMap<>();
        
        if (content == null || content.trim().isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "文档内容不能为空"));
        }

        try {
            // 添加默认元数据
            metadata.put("timestamp", LocalDateTime.now().toString());
            metadata.put("source", "api");
            
            aiService.addDocument(content, metadata);
            
            Map<String, Object> result = new HashMap<>();
            result.put("status", "success");
            result.put("message", "文档已成功向量化并存储");
            result.put("contentLength", content.length());
            result.put("timestamp", LocalDateTime.now());
            
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            log.error("文档向量化失败", e);
            return ResponseEntity.internalServerError()
                    .body(Map.of("error", "文档向量化服务暂时不可用: " + e.getMessage()));
        }
    }

    /**
     * 文本向量化接口
     */
    @PostMapping("/embed/text")
    @Operation(summary = "文本向量化", description = "将任意文本转换为向量表示（Embedding），可用于计算文本相似度等场景。")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "文本向量化成功", content = @Content(mediaType = "application/json", schema = @Schema(example = "{\"text\":\"测试文本\",\"embedding\":[0.1, 0.2, ...],\"dimension\":768,\"timestamp\":\"2023-10-27T10:15:00\"}"))),
        @ApiResponse(responseCode = "400", description = "请求参数错误，例如文本为空", content = @Content(mediaType = "application/json", schema = @Schema(example = "{\"error\":\"文本不能为空\"}"))),
        @ApiResponse(responseCode = "500", description = "服务器内部错误", content = @Content(mediaType = "application/json", schema = @Schema(example = "{\"error\":\"文本向量化服务暂时不可用: [错误详情]\"}")))
    })
    public ResponseEntity<Map<String, Object>> embedText(@RequestBody EmbedTextRequest request) {
        String text = request.getText();
        if (text == null || text.trim().isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "文本不能为空"));
        }

        try {
            List<Double> embedding = aiService.embed(text);
            
            Map<String, Object> result = new HashMap<>();
            result.put("text", text);
            result.put("embedding", embedding);
            result.put("dimension", embedding.size());
            result.put("timestamp", LocalDateTime.now());
            
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            log.error("文本向量化失败", e);
            return ResponseEntity.internalServerError()
                    .body(Map.of("error", "文本向量化服务暂时不可用: " + e.getMessage()));
        }
    }

    /**
     * 相似文档搜索接口
     */
    @PostMapping("/search")
    @Operation(summary = "相似文档搜索", description = "在知识库中根据输入文本进行向量相似度搜索，返回最相关的文档片段。")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "搜索成功", content = @Content(mediaType = "application/json", schema = @Schema(example = "{\"query\":\"违约责任\",\"documents\":[{\"content\":\"...\",\"metadata\":{...}}],\"totalResults\":1,\"timestamp\":\"2023-10-27T10:20:00\"}"))),
        @ApiResponse(responseCode = "400", description = "请求参数错误，例如查询内容为空", content = @Content(mediaType = "application/json", schema = @Schema(example = "{\"error\":\"查询内容不能为空\"}"))),
        @ApiResponse(responseCode = "500", description = "服务器内部错误", content = @Content(mediaType = "application/json", schema = @Schema(example = "{\"error\":\"文档搜索服务暂时不可用: [错误详情]\"}")))
    })
    public ResponseEntity<Map<String, Object>> searchSimilarDocuments(@RequestBody SearchRequest request) {
        String query = request.getQuery();
        Integer maxResults = request.getMaxResults();
        
        if (query == null || query.trim().isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "查询内容不能为空"));
        }

        try {
            List<Document> documents = aiService.searchSimilarDocuments(query, maxResults);
            
            Map<String, Object> result = new HashMap<>();
            result.put("query", query);
            result.put("documents", documents.stream().map(doc -> {
                if (doc.getText() != null) {
                    return Map.of(
                            "content", doc.getText(),
                            "metadata", doc.getMetadata()
                    );
                }
                return null;
            }).toList());
            result.put("totalResults", documents.size());
            result.put("timestamp", LocalDateTime.now());
            
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            log.error("文档搜索失败", e);
            return ResponseEntity.internalServerError()
                    .body(Map.of("error", "文档搜索服务暂时不可用: " + e.getMessage()));
        }
    }

    /**
     * 文档分块测试接口
     */
    @PostMapping("/test/chunking")
    @Operation(summary = "测试文档分块", description = "输入一段长文本，测试文本分割（Chunking）策略的效果，返回分割后的文本块列表及其信息。该接口用于调试，不会将结果存入数据库。")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "测试成功", content = @Content(mediaType = "application/json", schema = @Schema(example = "{\"originalLength\":5000,\"totalChunks\":5,\"chunks\":[{\"chunkNumber\":1,\"content\":\"...\",\"length\":1000}],\"timestamp\":\"2023-10-27T10:25:00\"}"))),
        @ApiResponse(responseCode = "400", description = "请求参数错误，例如内容为空", content = @Content(mediaType = "application/json", schema = @Schema(example = "{\"error\":\"内容不能为空\"}"))),
        @ApiResponse(responseCode = "500", description = "服务器内部错误", content = @Content(mediaType = "application/json", schema = @Schema(example = "{\"error\":\"分块测试失败: [错误详情]\"}")))
    })
    public ResponseEntity<Map<String, Object>> testDocumentChunking(@RequestBody Map<String, String> request) {
        String content = request.get("content");
        if (content == null || content.trim().isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "内容不能为空"));
        }

        try {
            List<Map<String, Object>> chunkInfo = aiService.testDocumentChunking(content);
            
            Map<String, Object> result = new HashMap<>();
            result.put("originalLength", content.length());
            result.put("totalChunks", chunkInfo.size());
            result.put("chunks", chunkInfo);
            result.put("timestamp", LocalDateTime.now());
            
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            log.error("分块测试失败", e);
            return ResponseEntity.internalServerError()
                    .body(Map.of("error", "分块测试失败: " + e.getMessage()));
        }
    }

    /**
     * 合同风险分析接口
     */
    @PostMapping("/analyze/contract")
    @Operation(summary = "合同风险分析（基础版）", description = "使用预设的Prompt模板，对合同文本进行初步的风险分析。")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "分析成功", content = @Content(mediaType = "application/json", schema = @Schema(example = "{\"contentLength\":3000,\"analysis\":\"该合同主要存在以下风险...\",\"timestamp\":\"2023-10-27T10:30:00\",\"type\":\"contract_risk_analysis\"}"))),
        @ApiResponse(responseCode = "400", description = "请求参数错误，例如合同内容为空", content = @Content(mediaType = "application/json", schema = @Schema(example = "{\"error\":\"合同内容不能为空\"}"))),
        @ApiResponse(responseCode = "500", description = "服务器内部错误", content = @Content(mediaType = "application/json", schema = @Schema(example = "{\"error\":\"合同分析服务暂时不可用: [错误详情]\"}")))
    })
    public ResponseEntity<Map<String, Object>> analyzeContract(@RequestBody AnalyzeContractRequest request) {
        String contractContent = request.getContent();
        if (contractContent == null || contractContent.trim().isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "合同内容不能为空"));
        }

        try {
            String analysis = aiService.analyzeContractRisk(contractContent);
            
            Map<String, Object> result = new HashMap<>();
            result.put("contentLength", contractContent.length());
            result.put("analysis", analysis);
            result.put("timestamp", LocalDateTime.now());
            result.put("type", "contract_risk_analysis");
            
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            log.error("合同分析失败", e);
            return ResponseEntity.internalServerError()
                    .body(Map.of("error", "合同分析服务暂时不可用: " + e.getMessage()));
        }
    }

    // ==================== Agent 相关接口 ====================

    /**
     * 智能法律顾问咨询接口
     * 使用 ReAct Agent，具备工具调用能力
     */
    @PostMapping(value = "/agent/consult", consumes = {"application/json", "application/x-www-form-urlencoded"})
    @Operation(summary = "智能法律顾问", description = "与基于ReAct Agent的智能法律顾问进行对话。Agent能够根据问题自主选择并调用工具（如网络搜索、数据库查询）以提供更全面、准确的解答。")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "咨询成功", content = @Content(mediaType = "application/json", schema = @Schema(example = "{\"question\":\"最新的《公司法》有什么变化？\",\"answer\":\"最新的《公司法》修订主要体现在...（信息来源于网络搜索）\",\"timestamp\":\"2023-10-27T10:35:00\",\"type\":\"agent_legal_consultation\"}"))),
        @ApiResponse(responseCode = "400", description = "请求参数错误，例如问题为空", content = @Content(mediaType = "application/json", schema = @Schema(example = "{\"error\":\"咨询问题不能为空\"}"))),
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
            String response = agentService.consultLegalMatter(question);
            
            Map<String, Object> result = new HashMap<>();
            result.put("question", question);
            result.put("answer", response);
            result.put("timestamp", LocalDateTime.now());
            result.put("type", "agent_legal_consultation");
            result.put("agent_info", agentService.getModelInfo());
            
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            log.error("Agent法律咨询失败", e);
            return ResponseEntity.internalServerError()
                    .body(Map.of("error", "智能法律顾问暂时不可用: " + e.getMessage()));
        }
    }

    /**
     * 智能合同分析咨询接口
     * 专门处理合同相关问题，使用Agent的工具调用能力
     * 支持JSON和form-encoded两种数据格式
     */
    @PostMapping(value = "/agent/analyze-contract", consumes = {"application/json", "application/x-www-form-urlencoded"})
    @Operation(summary = "智能合同分析（Agent版）", description = "通过Agent对合同进行深度分析。Agent可以调用专业工具（如条款比对、案例查询）来识别普通模型难以发现的深层风险。")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "分析成功", content = @Content(mediaType = "application/json", schema = @Schema(example = "{\"contractLength\":5000,\"question\":\"审查此租赁合同\",\"analysis\":\"...（分析结果）\",\"timestamp\":\"2023-10-27T10:40:00\",\"type\":\"agent_contract_analysis\"}"))),
        @ApiResponse(responseCode = "400", description = "请求参数错误，例如合同内容为空", content = @Content(mediaType = "application/json", schema = @Schema(example = "{\"error\":\"合同内容不能为空\"}"))),
        @ApiResponse(responseCode = "500", description = "服务器内部错误", content = @Content(mediaType = "application/json", schema = @Schema(example = "{\"error\":\"智能合同分析服务暂时不可用: [错误详情]\"}")))
    })
    public ResponseEntity<Map<String, Object>> analyzeContractWithAgent(
            @Parameter(description = "要分析的合同文本内容")
            @RequestParam(required = false) String contractContent,
            @Parameter(description = "针对合同的具体问题或审查方向")
            @RequestParam(required = false) String question,
            @Parameter(description = "包含 `contractContent` 和 `question` 的JSON对象")
            @RequestBody(required = false) Map<String, String> jsonRequest) {
        
        // 优先使用JSON参数，如果不存在则使用form参数
        if (jsonRequest != null && !jsonRequest.isEmpty()) {
            contractContent = jsonRequest.get("contractContent");
            question = jsonRequest.get("question");
        }
        
        if (contractContent == null || contractContent.trim().isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "合同内容不能为空"));
        }

        try {
            String response = agentService.analyzeContractMatter(contractContent, question);
            
            Map<String, Object> result = new HashMap<>();
            result.put("contractLength", contractContent.length());
            result.put("question", question != null ? question : "通用合同分析");
            result.put("analysis", response);
            result.put("timestamp", LocalDateTime.now());
            result.put("type", "agent_contract_analysis");
            result.put("agent_info", agentService.getModelInfo());
            
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            log.error("Agent合同分析失败", e);
            return ResponseEntity.internalServerError()
                    .body(Map.of("error", "智能合同分析服务暂时不可用: " + e.getMessage()));
        }
    }

    /**
     * Agent服务状态检查
     */
    @GetMapping("/agent/health")
    @Operation(summary = "Agent健康检查", description = "检查Agent服务及其依赖的底层模型是否正常运行。")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "检查成功", content = @Content(mediaType = "application/json", schema = @Schema(example = "{\"healthy\":true,\"status\":\"运行正常\",\"modelInfo\":\"qwen2:7b\",\"timestamp\":\"2023-10-27T10:45:00\"}"))),
        @ApiResponse(responseCode = "500", description = "服务器内部错误", content = @Content(mediaType = "application/json", schema = @Schema(example = "{\"healthy\":false,\"status\":\"健康检查失败\",\"error\":\"[错误详情]\",\"timestamp\":\"2023-10-27T10:45:00\"}")))
    })
    public ResponseEntity<Map<String, Object>> checkAgentHealth() {
        try {
            boolean isHealthy = agentService.isServiceHealthy();
            String modelInfo = agentService.getModelInfo();
            
            Map<String, Object> result = new HashMap<>();
            result.put("healthy", isHealthy);
            result.put("status", isHealthy ? "运行正常" : "服务异常");
            result.put("modelInfo", modelInfo);
            result.put("timestamp", LocalDateTime.now());
            
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            log.error("Agent健康检查失败", e);
            return ResponseEntity.internalServerError()
                    .body(Map.of(
                        "healthy", false,
                        "status", "健康检查失败",
                        "error", e.getMessage(),
                        "timestamp", LocalDateTime.now()
                    ));
        }
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

    /**
     * 直接与模型对话（不使用Agent工具）
     * 用于简单对话或调试
     */
    @PostMapping("/agent/direct-chat")
    @Operation(summary = "直接模型对话", description = "绕过Agent框架，直接与Agent底层的大模型进行对话。该接口主要用于调试模型本身的能力。")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "对话成功", content = @Content(mediaType = "application/json", schema = @Schema(example = "{\"message\":\"你好\",\"response\":\"你好！\",\"timestamp\":\"2023-10-27T10:55:00\",\"type\":\"direct_model_chat\"}"))),
        @ApiResponse(responseCode = "400", description = "请求参数错误，例如消息为空", content = @Content(mediaType = "application/json", schema = @Schema(example = "{\"error\":\"消息不能为空\"}"))),
        @ApiResponse(responseCode = "500", description = "服务器内部错误", content = @Content(mediaType = "application/json", schema = @Schema(example = "{\"error\":\"直接对话服务暂时不可用: [错误详情]\"}")))
    })
    public ResponseEntity<Map<String, Object>> directChat(@RequestBody ChatRequest request) {
        String message = request.getMessage();
        
        if (message == null || message.trim().isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "消息不能为空"));
        }

        try {
            String response = agentService.directChat(message);
            
            Map<String, Object> result = new HashMap<>();
            result.put("message", message);
            result.put("response", response);
            result.put("timestamp", LocalDateTime.now());
            result.put("type", "direct_model_chat");
            result.put("model_info", agentService.getModelInfo());
            
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            log.error("直接模型对话失败", e);
            return ResponseEntity.internalServerError()
                    .body(Map.of("error", "直接对话服务暂时不可用: " + e.getMessage()));
        }
    }
}
