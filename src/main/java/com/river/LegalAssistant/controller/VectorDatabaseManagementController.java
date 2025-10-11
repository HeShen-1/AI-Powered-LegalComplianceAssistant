package com.river.LegalAssistant.controller;

import com.river.LegalAssistant.service.VectorDatabaseManagementService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * 向量数据库管理控制器
 * 提供向量数据库的重建、清理和统计功能（仅管理员可用）
 */
@RestController
@RequestMapping("/admin/vector-db")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "向量数据库管理", description = "管理员专用的向量数据库管理功能，包括重建、清理和统计")
@SecurityRequirement(name = "bearerAuth")
public class VectorDatabaseManagementController {

    private final VectorDatabaseManagementService vectorDbManagementService;

    /**
     * 获取向量数据库统计信息
     */
    @GetMapping("/stats")
    @Operation(summary = "获取向量数据库统计信息", 
               description = "获取Spring AI和LangChain4j向量数据库的详细统计信息，包括记录数量、配置参数等。")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "获取成功", 
            content = @Content(mediaType = "application/json", 
                schema = @Schema(example = "{\"success\":true,\"data\":{\"springAiVectorCount\":150,\"langchain4jEmbeddingCount\":180,\"sourceDocumentsCount\":6,\"chunkSize\":2000,\"chunkOverlap\":400,\"minChunkSize\":50}}"))),
        @ApiResponse(responseCode = "401", description = "用户未认证"),
        @ApiResponse(responseCode = "403", description = "当前用户无管理员权限")
    })
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Map<String, Object>> getVectorDatabaseStats() {
        try {
            log.info("管理员请求向量数据库统计信息");
            
            VectorDatabaseManagementService.VectorDatabaseStats stats = 
                vectorDbManagementService.getVectorDatabaseStats();
            
            return ResponseEntity.ok(Map.of(
                "success", true,
                "data", stats,
                "message", "向量数据库统计信息获取成功"
            ));
        } catch (Exception e) {
            log.error("获取向量数据库统计信息失败", e);
            return ResponseEntity.internalServerError().body(Map.of(
                "success", false,
                "message", "获取统计信息失败: " + e.getMessage()
            ));
        }
    }

    /**
     * 重建向量数据库
     */
    @PostMapping("/rebuild")
    @Operation(summary = "重建向量数据库", 
               description = "清理所有现有向量数据并使用最新的分块配置重新索引所有文档。" +
                           "此操作可能需要几分钟时间，请耐心等待。建议在系统使用较少时执行。")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "重建任务已启动", 
            content = @Content(mediaType = "application/json", 
                schema = @Schema(example = "{\"success\":true,\"message\":\"向量数据库重建任务已启动\",\"taskId\":\"rebuild_20231001_123456\"}"))),
        @ApiResponse(responseCode = "401", description = "用户未认证"),
        @ApiResponse(responseCode = "403", description = "当前用户无管理员权限"),
        @ApiResponse(responseCode = "500", description = "启动重建任务失败")
    })
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Map<String, Object>> rebuildVectorDatabase() {
        try {
            log.warn("管理员启动向量数据库重建操作");
            
            // 异步启动重建任务
            CompletableFuture<VectorDatabaseManagementService.VectorRebuildResult> rebuildTask = 
                vectorDbManagementService.rebuildAllVectorDatabases();
            
            // 生成任务ID用于跟踪
            String taskId = "rebuild_" + System.currentTimeMillis();
            
            // 异步处理结果（可选：存储到Redis或内存中用于状态查询）
            rebuildTask.whenComplete((result, throwable) -> {
                if (throwable != null) {
                    log.error("向量数据库重建任务失败: {}", taskId, throwable);
                } else {
                    log.info("向量数据库重建任务完成: {} - 成功: {}, 处理文档: {}, 总块数: {}, 耗时: {}ms",
                            taskId, result.isSuccess(), result.getProcessedDocuments(), 
                            result.getTotalChunks(), result.getDurationMs());
                }
            });
            
            return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "向量数据库重建任务已启动",
                "taskId", taskId,
                "note", "重建操作将在后台异步执行，请稍后查看日志或重新获取统计信息确认完成状态"
            ));
            
        } catch (Exception e) {
            log.error("启动向量数据库重建失败", e);
            return ResponseEntity.internalServerError().body(Map.of(
                "success", false,
                "message", "启动重建任务失败: " + e.getMessage()
            ));
        }
    }

    /**
     * 同步重建向量数据库（阻塞版本）
     */
    @PostMapping("/rebuild-sync")
    @Operation(summary = "同步重建向量数据库", 
               description = "清理所有现有向量数据并使用最新的分块配置重新索引所有文档。" +
                           "此为同步版本，会阻塞直到完成，可能需要较长时间。建议使用异步版本。")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "重建完成", 
            content = @Content(mediaType = "application/json", 
                schema = @Schema(example = "{\"success\":true,\"data\":{\"processedDocuments\":6,\"totalChunks\":120,\"durationMs\":45000},\"message\":\"向量数据库重建完成\"}"))),
        @ApiResponse(responseCode = "401", description = "用户未认证"),
        @ApiResponse(responseCode = "403", description = "当前用户无管理员权限"),
        @ApiResponse(responseCode = "500", description = "重建过程中发生错误")
    })
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Map<String, Object>> rebuildVectorDatabaseSync() {
        try {
            log.warn("管理员启动同步向量数据库重建操作");
            
            // 同步执行重建
            VectorDatabaseManagementService.VectorRebuildResult result = 
                vectorDbManagementService.rebuildAllVectorDatabases().get();
            
            if (result.isSuccess()) {
                log.info("向量数据库同步重建完成 - 处理文档: {}, 总块数: {}, 耗时: {}ms",
                        result.getProcessedDocuments(), result.getTotalChunks(), result.getDurationMs());
                
                return ResponseEntity.ok(Map.of(
                    "success", true,
                    "data", result,
                    "message", "向量数据库重建完成"
                ));
            } else {
                log.error("向量数据库同步重建失败: {}", result.getMessage());
                return ResponseEntity.internalServerError().body(Map.of(
                    "success", false,
                    "data", result,
                    "message", "向量数据库重建失败: " + result.getMessage()
                ));
            }
            
        } catch (Exception e) {
            log.error("同步重建向量数据库失败", e);
            return ResponseEntity.internalServerError().body(Map.of(
                "success", false,
                "message", "重建失败: " + e.getMessage()
            ));
        }
    }

    /**
     * 健康检查
     */
    @GetMapping("/health")
    @Operation(summary = "向量数据库健康检查", 
               description = "检查向量数据库的连接状态和基本功能是否正常。")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "健康检查成功"),
        @ApiResponse(responseCode = "401", description = "用户未认证"),
        @ApiResponse(responseCode = "403", description = "当前用户无管理员权限")
    })
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Map<String, Object>> healthCheck() {
        try {
            // 获取统计信息作为健康检查
            VectorDatabaseManagementService.VectorDatabaseStats stats = 
                vectorDbManagementService.getVectorDatabaseStats();
            
            boolean isHealthy = stats.getSpringAiVectorCount() >= 0 && 
                               stats.getLangchain4jEmbeddingCount() >= 0;
            
            return ResponseEntity.ok(Map.of(
                "success", true,
                "healthy", isHealthy,
                "stats", stats,
                "message", isHealthy ? "向量数据库运行正常" : "向量数据库可能存在问题"
            ));
        } catch (Exception e) {
            log.error("向量数据库健康检查失败", e);
            return ResponseEntity.ok(Map.of(
                "success", false,
                "healthy", false,
                "message", "健康检查失败: " + e.getMessage()
            ));
        }
    }
}
