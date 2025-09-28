package com.river.LegalAssistant.controller;

import com.river.LegalAssistant.service.AiService;
import com.river.LegalAssistant.service.DocumentParserService;
import com.river.LegalAssistant.service.KnowledgeBaseService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
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
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 知识库管理控制器
 * 提供法律文档上传、管理、删除等功能
 * 仅管理员可访问
 */
@RestController
@RequestMapping("/api/knowledge-base")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "知识库管理", description = "法律文档知识库的后台管理功能")
@SecurityRequirement(name = "bearerAuth")
@PreAuthorize("hasRole('ADMIN')")
public class KnowledgeBaseController {

    private final KnowledgeBaseService knowledgeBaseService;
    private final DocumentParserService documentParserService;
    private final AiService aiService;

    /**
     * 上传法律文档到知识库
     */
    @PostMapping("/documents")
    @Operation(summary = "上传法律文档", description = "上传单个法律文档（支持.docx, .pdf, .txt），系统将自动解析、分割、向量化并存入知识库。仅限管理员访问。")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "文档上传并处理成功",
            content = @Content(mediaType = "application/json", schema = @Schema(example = "{\"success\":true,\"message\":\"文档上传成功\",\"docId\":\"...\",\"chunksAdded\":10}"))),
        @ApiResponse(responseCode = "400", description = "请求无效（如文件为空、文件类型不支持）"),
        @ApiResponse(responseCode = "401", description = "用户未认证"),
        @ApiResponse(responseCode = "403", description = "权限不足，非管理员用户"),
        @ApiResponse(responseCode = "500", description = "服务器内部错误，文档处理失败")
    })
    public ResponseEntity<Map<String, Object>> uploadDocument(
            @Parameter(description = "待上传的法律文档文件。支持格式：.docx, .pdf, .txt", required = true)
            @RequestParam("file") MultipartFile file,
            @Parameter(description = "为文档指定一个分类，便于管理和检索", example = "劳动法")
            @RequestParam(value = "category", required = false) String category,
            @Parameter(description = "对文档内容的简要描述", example = "2023年最新劳动法全文")
            @RequestParam(value = "description", required = false) String description) {
        
        log.info("管理员上传法律文档: {}", file.getOriginalFilename());
        
        try {
            // 验证文件
            if (file.isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "message", "文件不能为空"
                ));
            }

            // 验证文件类型
            String fileName = file.getOriginalFilename();
            if (fileName == null || fileName.trim().isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "message", "文件名不能为空"
                ));
            }

            String fileExtension = getFileExtension(fileName);
            if (documentParserService.isFileTypeSupported(fileExtension)) {
                return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "message", "不支持的文件类型，支持的类型: " + 
                              String.join(", ", documentParserService.getSupportedFileTypes())
                ));
            }

            // 上传文档到知识库
            Map<String, Object> result = knowledgeBaseService.uploadDocument(
                file.getInputStream(), 
                fileName, 
                file.getSize(), 
                category, 
                description
            );

            return ResponseEntity.ok(result);

        } catch (Exception e) {
            log.error("法律文档上传失败", e);
            return ResponseEntity.internalServerError().body(Map.of(
                "success", false,
                "message", "文档上传失败: " + e.getMessage()
            ));
        }
    }

    /**
     * 批量上传文档
     */
    @PostMapping("/documents/batch")
    @Operation(summary = "批量上传文档", description = "一次性上传多个法律文档，提高效率。所有文档将被归入同一个指定的分类（如果提供）。")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "批量上传任务完成",
            content = @Content(mediaType = "application/json", schema = @Schema(example = "{\"success\":true,\"totalFiles\":3,\"successCount\":3,\"failedCount\":0,\"details\":[...]}"))),
        @ApiResponse(responseCode = "400", description = "请求无效（如未上传任何文件）"),
        @ApiResponse(responseCode = "401", description = "用户未认证"),
        @ApiResponse(responseCode = "403", description = "权限不足")
    })
    public ResponseEntity<Map<String, Object>> batchUploadDocuments(
            @Parameter(description = "待上传的文档文件数组", required = true)
            @RequestParam("files") MultipartFile[] files,
            @Parameter(description = "为本次上传的所有文档指定一个统一分类", example = "民法典司法解释")
            @RequestParam(value = "category", required = false) String category) {
        
        log.info("管理员批量上传 {} 个法律文档", files.length);
        
        try {
            if (files.length == 0) {
                return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "message", "至少需要上传一个文件"
                ));
            }

            Map<String, Object> batchResult = knowledgeBaseService.batchUploadDocuments(files, category);
            return ResponseEntity.ok(batchResult);

        } catch (Exception e) {
            log.error("批量文档上传失败", e);
            return ResponseEntity.internalServerError().body(Map.of(
                "success", false,
                "message", "批量上传失败: " + e.getMessage()
            ));
        }
    }

    /**
     * 获取知识库文档列表
     */
    @GetMapping("/documents")
    @Operation(summary = "获取文档列表", description = "分页查询知识库中的文档信息，可根据分类进行筛选。")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "查询成功"),
        @ApiResponse(responseCode = "401", description = "用户未认证"),
        @ApiResponse(responseCode = "403", description = "权限不足")
    })
    public ResponseEntity<Map<String, Object>> getDocuments(
            @Parameter(description = "页码，从0开始", example = "0")
            @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "每页记录数", example = "20")
            @RequestParam(defaultValue = "20") int size,
            @Parameter(description = "根据文档分类进行筛选", example = "劳动法")
            @RequestParam(required = false) String category) {
        
        try {
            Map<String, Object> result = knowledgeBaseService.getDocuments(page, size, category);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            log.error("获取文档列表失败", e);
            return ResponseEntity.internalServerError().body(Map.of(
                "success", false,
                "message", "获取文档列表失败: " + e.getMessage()
            ));
        }
    }

    /**
     * 获取文档详情
     */
    @GetMapping("/documents/{docId}")
    @Operation(summary = "获取文档详情", description = "根据文档的唯一ID，获取其详细信息，包含文件名、分类、描述、大小及存储的文本块数量。")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "获取成功"),
        @ApiResponse(responseCode = "401", description = "用户未认证"),
        @ApiResponse(responseCode = "403", description = "权限不足"),
        @ApiResponse(responseCode = "404", description = "指定的文档ID不存在")
    })
    public ResponseEntity<Map<String, Object>> getDocumentDetail(@Parameter(description = "文档的唯一ID", required = true, example = "doc_4a5c6d...") @PathVariable String docId) {
        try {
            Map<String, Object> result = knowledgeBaseService.getDocumentDetail(docId);
            
            if (result == null) {
                return ResponseEntity.notFound().build();
            }
            
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            log.error("获取文档详情失败", e);
            return ResponseEntity.internalServerError().body(Map.of(
                "success", false,
                "message", "获取文档详情失败: " + e.getMessage()
            ));
        }
    }

    /**
     * 删除知识库文档
     */
    @DeleteMapping("/documents/{docId}")
    @Operation(summary = "删除文档", description = "从知识库中永久删除指定的文档及其所有相关的向量数据。此操作不可恢复。")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "文档删除成功", content = @Content(mediaType = "application/json", schema = @Schema(example = "{\"success\":true,\"message\":\"文档删除成功\",\"docId\":\"doc_4a5c6d...\"}"))),
        @ApiResponse(responseCode = "401", description = "用户未认证"),
        @ApiResponse(responseCode = "403", description = "权限不足"),
        @ApiResponse(responseCode = "404", description = "指定的文档ID不存在"),
        @ApiResponse(responseCode = "500", description = "删除操作失败")
    })
    public ResponseEntity<Map<String, Object>> deleteDocument(@Parameter(description = "待删除文档的唯一ID", required = true, example = "doc_4a5c6d...") @PathVariable String docId) {
        log.info("管理员删除知识库文档: {}", docId);
        
        try {
            boolean deleted = knowledgeBaseService.deleteDocument(docId);
            
            if (deleted) {
                return ResponseEntity.ok(Map.of(
                    "success", true,
                    "message", "文档删除成功",
                    "docId", docId
                ));
            } else {
                return ResponseEntity.notFound().build();
            }
        } catch (Exception e) {
            log.error("删除文档失败", e);
            return ResponseEntity.internalServerError().body(Map.of(
                "success", false,
                "message", "文档删除失败: " + e.getMessage()
            ));
        }
    }

    /**
     * 批量删除文档
     */
    @DeleteMapping("/documents/batch")
    @Operation(summary = "批量删除文档", description = "一次性从知识库中永久删除多个指定的文档。")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "批量删除任务完成"),
        @ApiResponse(responseCode = "400", description = "请求体中的ID列表为空"),
        @ApiResponse(responseCode = "401", description = "用户未认证"),
        @ApiResponse(responseCode = "403", description = "权限不足")
    })
    public ResponseEntity<Map<String, Object>> batchDeleteDocuments(
            @Parameter(description = "包含待删除文档ID的列表", required = true, example = "[\"doc_4a5c6d...\", \"doc_b7e8f9...\"]")
            @RequestBody List<String> docIds) {
        
        log.info("管理员批量删除 {} 个文档", docIds.size());
        
        try {
            if (docIds.isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "message", "文档ID列表不能为空"
                ));
            }

            Map<String, Object> result = knowledgeBaseService.batchDeleteDocuments(docIds);
            return ResponseEntity.ok(result);

        } catch (Exception e) {
            log.error("批量删除文档失败", e);
            return ResponseEntity.internalServerError().body(Map.of(
                "success", false,
                "message", "批量删除失败: " + e.getMessage()
            ));
        }
    }

    /**
     * 更新文档元数据
     */
    @PutMapping("/documents/{docId}")
    @Operation(summary = "更新文档信息", description = "更新知识库中已存在文档的元数据，如分类和描述。")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "更新成功"),
        @ApiResponse(responseCode = "401", description = "用户未认证"),
        @ApiResponse(responseCode = "403", description = "权限不足"),
        @ApiResponse(responseCode = "404", description = "指定的文档ID不存在")
    })
    public ResponseEntity<Map<String, Object>> updateDocumentMetadata(
            @Parameter(description = "待更新文档的唯一ID", required = true, example = "doc_4a5c6d...")
            @PathVariable String docId,
            @Parameter(description = "包含待更新字段的JSON对象。目前支持 `category` 和 `description`。", required = true, schema = @Schema(example = "{\"category\":\"劳动合同法\",\"description\":\"2023年最新版\"}"))
            @RequestBody Map<String, String> updateData) {
        
        log.info("管理员更新文档元数据: {}", docId);
        
        try {
            Map<String, Object> result = knowledgeBaseService.updateDocumentMetadata(docId, updateData);
            
            if (result == null) {
                return ResponseEntity.notFound().build();
            }
            
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            log.error("更新文档元数据失败", e);
            return ResponseEntity.internalServerError().body(Map.of(
                "success", false,
                "message", "更新文档信息失败: " + e.getMessage()
            ));
        }
    }

    /**
     * 获取知识库统计信息
     */
    @GetMapping("/statistics")
    @Operation(summary = "知识库统计", description = "获取关于知识库的整体统计信息，包括文档总数、文本块总数和分类信息。")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "获取成功", content = @Content(mediaType = "application/json", schema = @Schema(example = "{\"success\":true,\"data\":{\"totalDocuments\":50,\"totalChunks\":1500,\"categoryCounts\":{\"劳动法\":20,\"合同法\":30}},\"timestamp\":\"...\"}"))),
        @ApiResponse(responseCode = "401", description = "用户未认证"),
        @ApiResponse(responseCode = "403", description = "权限不足"),
        @ApiResponse(responseCode = "500", description = "获取统计信息失败")
    })
    public ResponseEntity<Map<String, Object>> getStatistics() {
        try {
            Map<String, Object> statistics = knowledgeBaseService.getStatistics();
            return ResponseEntity.ok(Map.of(
                "success", true,
                "data", statistics,
                "timestamp", LocalDateTime.now()
            ));
        } catch (Exception e) {
            log.error("获取知识库统计失败", e);
            return ResponseEntity.internalServerError().body(Map.of(
                "success", false,
                "message", "获取统计信息失败: " + e.getMessage()
            ));
        }
    }

    /**
     * 重建知识库索引
     */
    @PostMapping("/reindex")
    @Operation(summary = "重建索引", description = "（高阶操作）触发对整个知识库的向量索引进行重建。当底层嵌入模型更换或索引结构需要优化时使用。这是一个耗时操作，将在后台异步执行。")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "重建任务已启动"),
        @ApiResponse(responseCode = "401", description = "用户未认证"),
        @ApiResponse(responseCode = "403", description = "权限不足")
    })
    public ResponseEntity<Map<String, Object>> reindexKnowledgeBase() {
        log.info("管理员请求重建知识库索引");
        
        try {
            knowledgeBaseService.reindexKnowledgeBase();
            
            return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "知识库索引重建已开始，请稍后查看结果",
                "timestamp", LocalDateTime.now()
            ));
        } catch (Exception e) {
            log.error("重建知识库索引失败", e);
            return ResponseEntity.internalServerError().body(Map.of(
                "success", false,
                "message", "重建索引失败: " + e.getMessage()
            ));
        }
    }

    /**
     * 测试文档解析
     */
    @PostMapping("/test/parse")
    @Operation(summary = "测试文档解析", description = "上传一个文档进行解析和文本分割（Chunking）测试，返回解析后的文本内容和分块结果。此接口仅用于调试，不会将文档存入知识库。")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "测试成功"),
        @ApiResponse(responseCode = "400", description = "文件为空或无效"),
        @ApiResponse(responseCode = "401", description = "用户未认证"),
        @ApiResponse(responseCode = "403", description = "权限不足")
    })
    public ResponseEntity<Map<String, Object>> testDocumentParsing(
            @Parameter(description = "待测试解析的文档文件", required = true)
            @RequestParam("file") MultipartFile file) {
        
        try {
            if (file.isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "message", "文件不能为空"
                ));
            }

            String content = documentParserService.parseDocument(
                file.getInputStream(), 
                file.getOriginalFilename(), 
                file.getSize()
            );

            // 测试分块效果
            List<Map<String, Object>> chunkInfo = aiService.testDocumentChunking(content);

            Map<String, Object> result = new HashMap<>();
            result.put("success", true);
            result.put("fileName", file.getOriginalFilename());
            result.put("fileSize", file.getSize());
            result.put("contentLength", content.length());
            result.put("contentPreview", content.length() > 500 ? content.substring(0, 500) + "..." : content);
            result.put("chunkInfo", chunkInfo);
            result.put("timestamp", LocalDateTime.now());

            return ResponseEntity.ok(result);

        } catch (Exception e) {
            log.error("测试文档解析失败", e);
            return ResponseEntity.internalServerError().body(Map.of(
                "success", false,
                "message", "文档解析测试失败: " + e.getMessage()
            ));
        }
    }

    /**
     * 获取文件扩展名
     */
    private String getFileExtension(String fileName) {
        if (fileName == null || !fileName.contains(".")) {
            return "";
        }
        return fileName.substring(fileName.lastIndexOf(".") + 1).toLowerCase();
    }
}
