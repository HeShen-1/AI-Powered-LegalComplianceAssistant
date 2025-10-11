package com.river.LegalAssistant.controller;

import com.river.LegalAssistant.service.AgentService;
import com.river.LegalAssistant.service.DeepSeekService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.sql.DataSource;
import java.sql.Connection;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * 健康检查控制器
 */
@RestController
@RequestMapping("/health")
@Slf4j
@Tag(name = "健康检查", description = "系统健康检查相关接口")
public class HealthController {

    private final DataSource dataSource;
    private final ChatClient chatClient;
    private final EmbeddingModel embeddingModel;
    private final AgentService agentService;
    private final DeepSeekService deepSeekService;
    
    public HealthController(DataSource dataSource, 
                          ChatClient chatClient, 
                          @Qualifier("ollamaEmbeddingModel") EmbeddingModel embeddingModel,
                          AgentService agentService,
                          DeepSeekService deepSeekService) {
        this.dataSource = dataSource;
        this.chatClient = chatClient;
        this.embeddingModel = embeddingModel;
        this.agentService = agentService;
        this.deepSeekService = deepSeekService;
    }

    /**
     * 基础健康检查
     */
    @GetMapping
    @Operation(summary = "基础健康检查", description = "检查应用服务是否成功启动并正在运行。返回'UP'状态表示服务正常。")
    @ApiResponse(responseCode = "200", description = "服务运行正常", content = @Content(mediaType = "application/json", schema = @Schema(example = "{\"status\":\"UP\",\"timestamp\":\"2023-10-27T12:00:00\",\"service\":\"Legal Assistant\",\"version\":\"1.0.0\"}")))
    public ResponseEntity<Map<String, Object>> health() {
        Map<String, Object> health = new HashMap<>();
        health.put("status", "UP");
        health.put("timestamp", LocalDateTime.now());
        health.put("service", "Legal Assistant");
        health.put("version", "1.0.0");
        
        return ResponseEntity.ok(health);
    }

    /**
     * 详细健康检查
     */
    @GetMapping("/detailed")
    @Operation(summary = "详细健康检查", description = "检查应用及其依赖的核心组件（数据库、AI服务）的健康状态。")
    @ApiResponse(responseCode = "200", description = "检查完成，返回各组件状态", content = @Content(mediaType = "application/json"))
    public ResponseEntity<Map<String, Object>> detailedHealth() {
        Map<String, Object> health = new HashMap<>();
        health.put("timestamp", LocalDateTime.now());
        health.put("service", "Legal Assistant");
        
        // 检查数据库连接
        Map<String, Object> database = checkDatabase();
        health.put("database", database);
        
        // 检查 AI 服务
        Map<String, Object> aiService = checkAiService();
        health.put("ai", aiService);
        
        // 整体状态
        boolean allHealthy = (boolean) database.get("healthy") && (boolean) aiService.get("healthy");
        health.put("status", allHealthy ? "UP" : "DOWN");
        
        return ResponseEntity.ok(health);
    }

    /**
     * AI 服务测试端点
     */
    @GetMapping("/ai/test")
    @Operation(summary = "AI 服务功能测试", description = "对配置的AI聊天模型和嵌入模型进行一次简单的功能调用测试，以验证AI服务是否可用。")
    @ApiResponse(responseCode = "200", description = "AI服务测试完成，返回测试结果")
    public ResponseEntity<Map<String, Object>> testAi() {
        Map<String, Object> result = new HashMap<>();
        result.put("timestamp", LocalDateTime.now());
        
        try {
            // 测试聊天功能
            String testQuestion = "你好，请简单介绍一下你的功能。";
            String response = chatClient.prompt()
                    .user(testQuestion)
                    .call()
                    .content();
            
            // 处理response可能为null的情况
            String safeResponse = (response != null) ? response : "AI服务响应为空";
            
            result.put("chat_test", Map.of(
                "question", testQuestion,
                "response", safeResponse,
                "status", (response != null) ? "SUCCESS" : "PARTIAL_SUCCESS"
            ));
            
            // 测试嵌入功能
            String testText = "这是一个测试文本";
            var embeddingResponse = embeddingModel.embed(testText);
            // Spring AI 1.0.2 中返回类型变化，直接获取向量
            
            result.put("embedding_test", Map.of(
                "text", testText,
                "embedding_dimension", embeddingResponse.length,
                "status", "SUCCESS"
            ));
            
            result.put("overall_status", "SUCCESS");
            
        } catch (Exception e) {
            log.error("AI 服务测试失败", e);
            result.put("overall_status", "FAILED");
            result.put("error", e.getMessage());
        }
        
        return ResponseEntity.ok(result);
    }

    /**
     * 检查数据库连接
     */
    private Map<String, Object> checkDatabase() {
        Map<String, Object> dbHealth = new HashMap<>();
        try (Connection connection = dataSource.getConnection()) {
            boolean isValid = connection.isValid(5); // 5秒超时
            dbHealth.put("healthy", isValid);
            dbHealth.put("status", isValid ? "UP" : "DOWN");
            if (isValid) {
                dbHealth.put("database", connection.getMetaData().getDatabaseProductName());
                dbHealth.put("version", connection.getMetaData().getDatabaseProductVersion());
            }
        } catch (Exception e) {
            log.error("数据库健康检查失败", e);
            dbHealth.put("healthy", false);
            dbHealth.put("status", "DOWN");
            dbHealth.put("error", e.getMessage());
        }
        return dbHealth;
    }

    /**
     * 检查 AI 服务
     */
    private Map<String, Object> checkAiService() {
        Map<String, Object> aiHealth = new HashMap<>();
        try {
            // 简单测试AI服务是否可用
            String testResponse = chatClient.prompt("测试").call().content();
            boolean isHealthy = testResponse != null && !testResponse.isEmpty();
            
            aiHealth.put("healthy", isHealthy);
            aiHealth.put("status", isHealthy ? "UP" : "DOWN");
            aiHealth.put("chat_model", "ollama-auto-configured");
            aiHealth.put("embedding_model", "ollama-auto-configured");
            aiHealth.put("test_response_available", isHealthy);
        } catch (Exception e) {
            log.error("AI 服务健康检查失败", e);
            aiHealth.put("healthy", false);
            aiHealth.put("status", "DOWN");
            aiHealth.put("error", e.getMessage());
        }
        return aiHealth;
    }

    /**
     * 系统信息
     */
    @GetMapping("/info")
    @Operation(summary = "系统信息", description = "获取应用的版本、运行环境（Java、OS）等基本信息。")
    @ApiResponse(responseCode = "200", description = "获取信息成功")
    public ResponseEntity<Map<String, Object>> info() {
        Map<String, Object> info = new HashMap<>();
        
        // 应用信息
        info.put("application", Map.of(
            "name", "Legal Assistant",
            "version", "1.0.0",
            "description", "Legal Compliance Intelligent Review Assistant"
        ));
        
        // Java 信息
        info.put("java", Map.of(
            "version", System.getProperty("java.version"),
            "vendor", System.getProperty("java.vendor"),
            "runtime", System.getProperty("java.runtime.name")
        ));
        
        // 系统信息
        info.put("system", Map.of(
            "os", System.getProperty("os.name"),
            "arch", System.getProperty("os.arch"),
            "processors", Runtime.getRuntime().availableProcessors()
        ));
        
        return ResponseEntity.ok(info);
    }

    /**
     * Agent服务健康检查
     */
    @GetMapping("/agent")
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
     * DeepSeek服务健康检查
     */
    @GetMapping("/deepseek")
    @Operation(summary = "DeepSeek服务健康检查", description = "检查DeepSeek模型服务是否可用。")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "检查成功", content = @Content(mediaType = "application/json", schema = @Schema(example = "{\"healthy\":true,\"status\":\"DeepSeek服务运行正常\",\"timestamp\":\"2023-10-27T11:10:00\"}"))),
        @ApiResponse(responseCode = "503", description = "服务不可用", content = @Content(mediaType = "application/json", schema = @Schema(example = "{\"healthy\":false,\"status\":\"DeepSeek服务不可用\",\"timestamp\":\"2023-10-27T11:10:00\"}")))
    })
    public ResponseEntity<Map<String, Object>> checkDeepSeekHealth() {
        try {
            boolean isHealthy = deepSeekService.isAvailable();
            
            Map<String, Object> result = new HashMap<>();
            result.put("healthy", isHealthy);
            result.put("status", isHealthy ? "DeepSeek服务运行正常" : "DeepSeek服务不可用");
            result.put("timestamp", LocalDateTime.now());
            
            return isHealthy ? 
                ResponseEntity.ok(result) : 
                ResponseEntity.status(503).body(result);
        } catch (Exception e) {
            log.error("DeepSeek健康检查失败", e);
            return ResponseEntity.status(503)
                    .body(Map.of(
                        "healthy", false,
                        "status", "DeepSeek健康检查失败",
                        "error", e.getMessage(),
                        "timestamp", LocalDateTime.now()
                    ));
        }
    }
}
