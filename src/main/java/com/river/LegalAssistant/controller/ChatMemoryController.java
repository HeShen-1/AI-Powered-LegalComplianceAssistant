package com.river.LegalAssistant.controller;

import com.river.LegalAssistant.service.AiService;
import com.river.LegalAssistant.service.ChatMemoryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 聊天记忆控制器
 * 
 * 基于SpringAI官方文档实现的聊天记忆功能：
 * - 每个AI模型使用独立的PostgreSQL数据库表管理聊天记忆
 * - 支持Ollama和DeepSeek两种模型的独立记忆存储
 * - 提供完整的聊天记忆管理接口
 * 
 * 参考: https://docs.spring.io/spring-ai/reference/api/chat-memory.html
 */
@RestController
@RequestMapping("/ai/memory")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "聊天记忆服务", description = "基于SpringAI ChatMemory的对话记忆管理，支持多模型独立存储")
public class ChatMemoryController {

    private final AiService aiService;
    private final ChatMemoryService chatMemoryService;

    /**
     * 带聊天记忆的本地知识库聊天接口
     * 基于SpringAI官方文档实现，支持对话上下文记忆
     */
    @PostMapping(value = "/chat", consumes = {"application/json", "application/x-www-form-urlencoded"})
    @Operation(summary = "带聊天记忆的本地知识库聊天", 
               description = "基于SpringAI ChatMemory实现的带对话记忆功能的知识库聊天，支持Ollama和DeepSeek两种模型，每个模型维护独立的聊天记忆")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "对话成功", 
                    content = @Content(mediaType = "application/json", 
                    schema = @Schema(example = "{\"question\":\"什么是合同违约？\",\"answer\":\"根据之前的对话和法律知识...\",\"conversationId\":\"chat-123\",\"modelType\":\"OLLAMA\",\"hasKnowledgeMatch\":true,\"sourceCount\":3}"))),
        @ApiResponse(responseCode = "400", description = "请求参数错误"),
        @ApiResponse(responseCode = "500", description = "服务器内部错误")
    })
    public ResponseEntity<Map<String, Object>> chatWithMemory(
            @Parameter(description = "聊天消息", required = true)
            @RequestParam String message,
            @Parameter(description = "会话ID，如果为空将自动生成")
            @RequestParam(required = false) String conversationId,
            @Parameter(description = "AI模型类型", schema = @Schema(allowableValues = {"OLLAMA", "DEEPSEEK"}))
            @RequestParam(defaultValue = "OLLAMA") String modelType) {
        
        log.info("带聊天记忆的对话请求: message={}, conversationId={}, modelType={}", 
                message, conversationId, modelType);
        
        try {
            // 参数验证
            if (message == null || message.trim().isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of("error", "消息不能为空"));
            }
            
            // 解析模型类型
            ChatMemoryService.ModelType modelTypeEnum;
            try {
                modelTypeEnum = ChatMemoryService.ModelType.valueOf(modelType.toUpperCase());
            } catch (IllegalArgumentException e) {
                return ResponseEntity.badRequest().body(Map.of(
                    "error", "不支持的模型类型: " + modelType + "，支持的类型: OLLAMA, DEEPSEEK"));
            }
            
            // 如果没有提供conversationId，生成一个新的
            if (conversationId == null || conversationId.trim().isEmpty()) {
                conversationId = chatMemoryService.generateConversationId();
                log.info("生成新的会话ID: {}", conversationId);
            }
            
            // 调用带聊天记忆的RAG聊天
            AiService.LocalRagResult ragResult = aiService.chatWithMemory(message, conversationId, modelTypeEnum);
            
            Map<String, Object> result = new HashMap<>();
            result.put("question", message);
            result.put("answer", ragResult.answer());
            result.put("conversationId", conversationId);
            result.put("modelType", modelType);
            result.put("hasKnowledgeMatch", ragResult.hasKnowledgeMatch());
            result.put("sourceCount", ragResult.sourceCount());
            result.put("timestamp", LocalDateTime.now());
            result.put("type", "memory_enabled_chat");
            
            return ResponseEntity.ok(result);
            
        } catch (Exception e) {
            log.error("带聊天记忆的对话失败", e);
            return ResponseEntity.internalServerError()
                    .body(Map.of("error", "聊天记忆服务暂时不可用: " + e.getMessage()));
        }
    }

    /**
     * 简单聊天（带记忆，不使用RAG）
     */
    @PostMapping(value = "/simple-chat", consumes = {"application/json", "application/x-www-form-urlencoded"})
    @Operation(summary = "简单聊天（带记忆）", 
               description = "不使用知识库检索的简单聊天，但保留对话记忆功能")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "对话成功"),
        @ApiResponse(responseCode = "400", description = "请求参数错误"),
        @ApiResponse(responseCode = "500", description = "服务器内部错误")
    })
    public ResponseEntity<Map<String, Object>> simpleChatWithMemory(
            @Parameter(description = "聊天消息", required = true)
            @RequestParam String message,
            @Parameter(description = "会话ID，如果为空将自动生成")
            @RequestParam(required = false) String conversationId,
            @Parameter(description = "AI模型类型", schema = @Schema(allowableValues = {"OLLAMA", "DEEPSEEK"}))
            @RequestParam(defaultValue = "OLLAMA") String modelType) {
        
        log.info("简单聊天（带记忆）请求: message={}, conversationId={}, modelType={}", 
                message, conversationId, modelType);
        
        try {
            // 参数验证
            if (message == null || message.trim().isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of("error", "消息不能为空"));
            }
            
            // 解析模型类型
            ChatMemoryService.ModelType modelTypeEnum;
            try {
                modelTypeEnum = ChatMemoryService.ModelType.valueOf(modelType.toUpperCase());
            } catch (IllegalArgumentException e) {
                return ResponseEntity.badRequest().body(Map.of(
                    "error", "不支持的模型类型: " + modelType + "，支持的类型: OLLAMA, DEEPSEEK"));
            }
            
            // 如果没有提供conversationId，生成一个新的
            if (conversationId == null || conversationId.trim().isEmpty()) {
                conversationId = chatMemoryService.generateConversationId();
                log.info("生成新的会话ID: {}", conversationId);
            }
            
            // 调用简单聊天（带记忆）
            String response = aiService.simpleChatWithMemory(message, conversationId, modelTypeEnum);
            
            Map<String, Object> result = new HashMap<>();
            result.put("question", message);
            result.put("answer", response);
            result.put("conversationId", conversationId);
            result.put("modelType", modelType);
            result.put("timestamp", LocalDateTime.now());
            result.put("type", "simple_memory_chat");
            
            return ResponseEntity.ok(result);
            
        } catch (Exception e) {
            log.error("简单聊天（带记忆）失败", e);
            return ResponseEntity.internalServerError()
                    .body(Map.of("error", "简单聊天服务暂时不可用: " + e.getMessage()));
        }
    }

    /**
     * 获取聊天记忆历史
     */
    @GetMapping("/history")
    @Operation(summary = "获取聊天记忆历史", 
               description = "获取指定会话和模型的聊天历史记录")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "获取成功"),
        @ApiResponse(responseCode = "400", description = "请求参数错误"),
        @ApiResponse(responseCode = "500", description = "服务器内部错误")
    })
    public ResponseEntity<Map<String, Object>> getChatHistory(
            @Parameter(description = "会话ID", required = true)
            @RequestParam String conversationId,
            @Parameter(description = "AI模型类型", schema = @Schema(allowableValues = {"OLLAMA", "DEEPSEEK"}))
            @RequestParam(defaultValue = "OLLAMA") String modelType) {
        
        log.info("获取聊天历史请求: conversationId={}, modelType={}", conversationId, modelType);
        
        try {
            // 参数验证
            if (conversationId == null || conversationId.trim().isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of("error", "会话ID不能为空"));
            }
            
            // 解析模型类型
            ChatMemoryService.ModelType modelTypeEnum;
            try {
                modelTypeEnum = ChatMemoryService.ModelType.valueOf(modelType.toUpperCase());
            } catch (IllegalArgumentException e) {
                return ResponseEntity.badRequest().body(Map.of(
                    "error", "不支持的模型类型: " + modelType + "，支持的类型: OLLAMA, DEEPSEEK"));
            }
            
            // 获取聊天历史
            List<org.springframework.ai.chat.messages.Message> messages = 
                chatMemoryService.getChatHistory(conversationId, modelTypeEnum);
            
            // 转换为简单格式便于前端显示
            List<Map<String, Object>> historyList = messages.stream()
                .map(message -> {
                    Map<String, Object> msgMap = new HashMap<>();
                    msgMap.put("type", message.getMessageType().toString());
                    msgMap.put("content", message.getText()); // 使用getText()而不是getContent()
                    msgMap.put("metadata", message.getMetadata());
                    return msgMap;
                })
                .collect(java.util.stream.Collectors.toList());
            
            Map<String, Object> result = new HashMap<>();
            result.put("conversationId", conversationId);
            result.put("modelType", modelType);
            result.put("messageCount", messages.size());
            result.put("messages", historyList);
            result.put("timestamp", LocalDateTime.now());
            
            return ResponseEntity.ok(result);
            
        } catch (Exception e) {
            log.error("获取聊天历史失败", e);
            return ResponseEntity.internalServerError()
                    .body(Map.of("error", "获取聊天历史失败: " + e.getMessage()));
        }
    }

    /**
     * 清除聊天记忆
     */
    @DeleteMapping("/clear")
    @Operation(summary = "清除聊天记忆", 
               description = "清除指定会话和模型的聊天记忆，或清除所有模型的指定会话记忆")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "清除成功"),
        @ApiResponse(responseCode = "400", description = "请求参数错误"),
        @ApiResponse(responseCode = "500", description = "服务器内部错误")
    })
    public ResponseEntity<Map<String, Object>> clearChatMemory(
            @Parameter(description = "会话ID", required = true)
            @RequestParam String conversationId,
            @Parameter(description = "AI模型类型，留空则清除所有模型的记忆", schema = @Schema(allowableValues = {"OLLAMA", "DEEPSEEK"}))
            @RequestParam(required = false) String modelType) {
        
        log.info("清除聊天记忆请求: conversationId={}, modelType={}", conversationId, modelType);
        
        try {
            // 参数验证
            if (conversationId == null || conversationId.trim().isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of("error", "会话ID不能为空"));
            }
            
            if (modelType == null || modelType.trim().isEmpty()) {
                // 清除所有模型的聊天记忆
                chatMemoryService.clearAllModelsChatMemory(conversationId);
                
                Map<String, Object> result = new HashMap<>();
                result.put("conversationId", conversationId);
                result.put("message", "已清除所有模型的聊天记忆");
                result.put("timestamp", LocalDateTime.now());
                
                return ResponseEntity.ok(result);
            } else {
                // 清除指定模型的聊天记忆
                ChatMemoryService.ModelType modelTypeEnum;
                try {
                    modelTypeEnum = ChatMemoryService.ModelType.valueOf(modelType.toUpperCase());
                } catch (IllegalArgumentException e) {
                    return ResponseEntity.badRequest().body(Map.of(
                        "error", "不支持的模型类型: " + modelType + "，支持的类型: OLLAMA, DEEPSEEK"));
                }
                
                chatMemoryService.clearChatMemory(conversationId, modelTypeEnum);
                
                Map<String, Object> result = new HashMap<>();
                result.put("conversationId", conversationId);
                result.put("modelType", modelType);
                result.put("message", "已清除指定模型的聊天记忆");
                result.put("timestamp", LocalDateTime.now());
                
                return ResponseEntity.ok(result);
            }
            
        } catch (Exception e) {
            log.error("清除聊天记忆失败", e);
            return ResponseEntity.internalServerError()
                    .body(Map.of("error", "清除聊天记忆失败: " + e.getMessage()));
        }
    }

    /**
     * 获取聊天记忆服务健康状态
     */
    @GetMapping("/health")
    @Operation(summary = "聊天记忆服务健康检查", 
               description = "检查聊天记忆服务的运行状态，包括各个模型的数据库连接状态")
    @ApiResponse(responseCode = "200", description = "健康检查完成")
    public ResponseEntity<Map<String, Object>> getChatMemoryHealth() {
        
        try {
            ChatMemoryService.ChatMemoryHealthInfo healthInfo = chatMemoryService.getHealthInfo();
            
            Map<String, Object> result = new HashMap<>();
            result.put("overallHealthy", healthInfo.isOverallHealthy());
            result.put("ollamaMemoryHealthy", healthInfo.isOllamaMemoryHealthy());
            result.put("deepSeekMemoryHealthy", healthInfo.isDeepSeekMemoryHealthy());
            result.put("ollamaMemoryError", healthInfo.getOllamaMemoryError());
            result.put("deepSeekMemoryError", healthInfo.getDeepSeekMemoryError());
            result.put("timestamp", LocalDateTime.now());
            result.put("service", "chat_memory");
            
            // 根据健康状态返回不同的HTTP状态码
            if (healthInfo.isOverallHealthy()) {
                return ResponseEntity.ok(result);
            } else {
                return ResponseEntity.status(503).body(result); // Service Unavailable
            }
            
        } catch (Exception e) {
            log.error("聊天记忆健康检查失败", e);
            return ResponseEntity.internalServerError()
                    .body(Map.of(
                        "overallHealthy", false,
                        "error", "健康检查失败: " + e.getMessage(),
                        "timestamp", LocalDateTime.now()
                    ));
        }
    }

    /**
     * 生成新的会话ID
     */
    @PostMapping("/new-conversation")
    @Operation(summary = "生成新会话ID", 
               description = "为新的对话会话生成唯一的会话ID")
    @ApiResponse(responseCode = "200", description = "生成成功")
    public ResponseEntity<Map<String, Object>> generateNewConversation(
            @Parameter(description = "用户ID（可选）")
            @RequestParam(required = false) String userId) {
        
        try {
            String conversationId;
            if (userId != null && !userId.trim().isEmpty()) {
                conversationId = chatMemoryService.generateConversationId(userId);
            } else {
                conversationId = chatMemoryService.generateConversationId();
            }
            
            Map<String, Object> result = new HashMap<>();
            result.put("conversationId", conversationId);
            result.put("userId", userId);
            result.put("timestamp", LocalDateTime.now());
            result.put("message", "新会话ID生成成功");
            
            return ResponseEntity.ok(result);
            
        } catch (Exception e) {
            log.error("生成新会话ID失败", e);
            return ResponseEntity.internalServerError()
                    .body(Map.of("error", "生成会话ID失败: " + e.getMessage()));
        }
    }

    /**
     * 检查会话是否存在记忆
     */
    @GetMapping("/exists")
    @Operation(summary = "检查会话记忆存在性", 
               description = "检查指定会话和模型是否存在聊天记忆")
    @ApiResponse(responseCode = "200", description = "检查完成")
    public ResponseEntity<Map<String, Object>> checkMemoryExists(
            @Parameter(description = "会话ID", required = true)
            @RequestParam String conversationId,
            @Parameter(description = "AI模型类型", schema = @Schema(allowableValues = {"OLLAMA", "DEEPSEEK"}))
            @RequestParam(defaultValue = "OLLAMA") String modelType) {
        
        try {
            // 参数验证
            if (conversationId == null || conversationId.trim().isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of("error", "会话ID不能为空"));
            }
            
            // 解析模型类型
            ChatMemoryService.ModelType modelTypeEnum;
            try {
                modelTypeEnum = ChatMemoryService.ModelType.valueOf(modelType.toUpperCase());
            } catch (IllegalArgumentException e) {
                return ResponseEntity.badRequest().body(Map.of(
                    "error", "不支持的模型类型: " + modelType + "，支持的类型: OLLAMA, DEEPSEEK"));
            }
            
            // 检查记忆存在性
            boolean hasMemory = chatMemoryService.hasMemory(conversationId, modelTypeEnum);
            int messageCount = chatMemoryService.getMessageCount(conversationId, modelTypeEnum);
            
            Map<String, Object> result = new HashMap<>();
            result.put("conversationId", conversationId);
            result.put("modelType", modelType);
            result.put("hasMemory", hasMemory);
            result.put("messageCount", messageCount);
            result.put("timestamp", LocalDateTime.now());
            
            return ResponseEntity.ok(result);
            
        } catch (Exception e) {
            log.error("检查会话记忆存在性失败", e);
            return ResponseEntity.internalServerError()
                    .body(Map.of("error", "检查失败: " + e.getMessage()));
        }
    }
}
