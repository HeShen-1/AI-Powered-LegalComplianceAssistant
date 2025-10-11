package com.river.LegalAssistant.controller;

import com.river.LegalAssistant.dto.*;
import com.river.LegalAssistant.service.AiService;
import com.river.LegalAssistant.service.DocumentIndexingService;
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
import org.springframework.http.MediaType;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 知识库管理控制器
 * 提供法律文档上传、管理、删除等功能
 * 仅管理员可访问
 */
@RestController
@RequestMapping("/knowledge-base")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "知识库管理", description = "法律文档知识库的后台管理功能")
@SecurityRequirement(name = "bearerAuth")
@PreAuthorize("hasRole('ADMIN')")
public class KnowledgeBaseController {

    private final KnowledgeBaseService knowledgeBaseService;
    private final DocumentParserService documentParserService;
    private final AiService aiService;
    private final DocumentIndexingService documentIndexingService;


    /**
     * 上传单个法律文档
     */
    @PostMapping("/documents/upload-single")
    @Operation(summary = "上传单个法律文档", 
               description = """
                   上传单个法律文档到知识库。系统将自动解析、分割、向量化并存入知识库。仅限管理员访问。
                   
                   **支持的文件格式**：.docx, .pdf, .txt, .doc, .md
                   **文件大小限制**：最大50MB
                   """)
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "文档上传并处理成功"),
        @ApiResponse(responseCode = "400", description = "请求无效（如文件为空、文件类型不支持）"),
        @ApiResponse(responseCode = "401", description = "用户未认证"),
        @ApiResponse(responseCode = "403", description = "权限不足，非管理员用户"),
        @ApiResponse(responseCode = "500", description = "服务器内部错误，文档处理失败")
    })
    public ResponseEntity<com.river.LegalAssistant.dto.ApiResponse<DocumentUploadResultDto>> uploadSingleDocument(
            @Parameter(description = "待上传的法律文档文件", required = true)
            @RequestParam("file") MultipartFile file,
            
            @Parameter(description = "为文档指定一个分类，便于管理和检索", example = "劳动法")
            @RequestParam(value = "category", required = false) String category,
            
            @Parameter(description = "对文档内容的简要描述", example = "2023年最新劳动法全文")
            @RequestParam(value = "description", required = false) String description) {
        
        log.info("管理员上传单个法律文档: {}", file.getOriginalFilename());
        
        try {
            // 验证文件
            if (file.isEmpty()) {
                return ResponseEntity.badRequest()
                    .body(com.river.LegalAssistant.dto.ApiResponse.error(
                        "文件不能为空", (DocumentUploadResultDto) null));
            }
            
            String fileName = file.getOriginalFilename();
            if (fileName == null || fileName.trim().isEmpty()) {
                return ResponseEntity.badRequest()
                    .body(com.river.LegalAssistant.dto.ApiResponse.error(
                        "文件名不能为空", (DocumentUploadResultDto) null));
            }

            // 验证文件类型
            String fileExtension = getFileExtension(fileName);
            if (!documentParserService.isFileTypeSupported(fileExtension)) {
                return ResponseEntity.badRequest()
                    .body(com.river.LegalAssistant.dto.ApiResponse.error(
                        "不支持的文件类型，支持的类型: " + 
                            String.join(", ", documentParserService.getSupportedFileTypes()), (DocumentUploadResultDto) null));
            }

            // 执行上传，Service层直接返回DTO
            DocumentUploadResultDto resultDto = knowledgeBaseService.uploadDocument(
                file.getInputStream(), 
                fileName, 
                file.getSize(), 
                category, 
                description
            );

            // 直接返回Service层的结果
            String message = (resultDto.getSuccessCount() != null && resultDto.getSuccessCount() > 0) 
                ? "文档上传成功" 
                : "文档上传失败";
            return ResponseEntity.ok(com.river.LegalAssistant.dto.ApiResponse.<DocumentUploadResultDto>success(resultDto, message));

        } catch (Exception e) {
            log.error("单个文档上传失败", e);
            return ResponseEntity.internalServerError()
                .body(com.river.LegalAssistant.dto.ApiResponse.error(
                    "文档上传失败: " + e.getMessage(), (DocumentUploadResultDto) null));
        }
    }

    /**
     * 批量上传法律文档
     */
    @PostMapping("/documents/upload-batch")
    @Operation(summary = "批量上传法律文档", 
               description = """
                   批量上传多个法律文档到知识库。系统将自动解析、分割、向量化并存入知识库。仅限管理员访问。
                   
                   **支持的文件格式**：.docx, .pdf, .txt, .doc, .md
                   **数量限制**：最多50个文件
                   **总大小限制**：最大200MB
                   """)
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "批量上传完成（部分或全部成功）"),
        @ApiResponse(responseCode = "400", description = "请求无效"),
        @ApiResponse(responseCode = "401", description = "用户未认证"),
        @ApiResponse(responseCode = "403", description = "权限不足"),
        @ApiResponse(responseCode = "500", description = "服务器内部错误")
    })
    public ResponseEntity<com.river.LegalAssistant.dto.ApiResponse<DocumentUploadResultDto>> uploadBatchDocuments(
            @Parameter(description = "待上传的多个法律文档文件", required = true)
            @RequestParam("files") MultipartFile[] files,
            
            @Parameter(description = "为所有文档指定统一分类", example = "劳动法")
            @RequestParam(value = "category", required = false) String category) {
        
        log.info("管理员批量上传 {} 个法律文档", files.length);
        
        try {
            // 验证文件数量
            if (files.length > 50) {
                return ResponseEntity.badRequest()
                    .body(com.river.LegalAssistant.dto.ApiResponse.error(
                        "一次最多只能上传50个文件，当前上传 " + files.length + " 个文件", (DocumentUploadResultDto) null));
            }
            
            // 验证是否有有效文件
            boolean hasValidFile = false;
            for (MultipartFile f : files) {
                if (f != null && !f.isEmpty()) {
                    hasValidFile = true;
                    break;
                }
            }
            
            if (!hasValidFile) {
                return ResponseEntity.badRequest()
                    .body(com.river.LegalAssistant.dto.ApiResponse.error(
                        "至少需要上传一个有效文件", (DocumentUploadResultDto) null));
            }
            
            // 执行批量上传，Service层直接返回DTO
            DocumentUploadResultDto resultDto = knowledgeBaseService.batchUploadDocuments(files, category);
            
            return ResponseEntity.ok(com.river.LegalAssistant.dto.ApiResponse.<DocumentUploadResultDto>success(resultDto, "批量上传完成"));

        } catch (Exception e) {
            log.error("批量文档上传失败", e);
            return ResponseEntity.internalServerError()
                .body(com.river.LegalAssistant.dto.ApiResponse.error(
                    "批量上传失败: " + e.getMessage(), (DocumentUploadResultDto) null));
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
    public ResponseEntity<com.river.LegalAssistant.dto.ApiResponse<DocumentListDto>> getDocuments(
            @Parameter(description = "页码，从0开始", example = "0")
            @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "每页记录数", example = "20")
            @RequestParam(defaultValue = "20") int size,
            @Parameter(description = "根据文档分类进行筛选", example = "劳动法")
            @RequestParam(required = false) String category) {
        
        try {
            // Service层直接返回DTO
            DocumentListDto listDto = knowledgeBaseService.getDocuments(page, size, category);
            
            return ResponseEntity.ok(com.river.LegalAssistant.dto.ApiResponse.<DocumentListDto>success(listDto));
        } catch (Exception e) {
            log.error("获取文档列表失败", e);
            return ResponseEntity.internalServerError()
                .body(com.river.LegalAssistant.dto.ApiResponse.errorWithCode(
                    "获取文档列表失败: " + e.getMessage(),
                    "FETCH_FAILED"));
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
    public ResponseEntity<com.river.LegalAssistant.dto.ApiResponse<DocumentDetailDto>> getDocumentDetail(
            @Parameter(description = "文档的唯一ID", required = true, example = "doc_4a5c6d...") 
            @PathVariable String docId) {
        try {
            // Service层直接返回DTO
            DocumentDetailDto detailDto = knowledgeBaseService.getDocumentDetail(docId);
            
            if (detailDto == null) {
                return ResponseEntity.status(404)
                    .body(com.river.LegalAssistant.dto.ApiResponse.errorWithCode(
                        "文档不存在",
                        "DOCUMENT_NOT_FOUND"));
            }
            
            return ResponseEntity.ok(com.river.LegalAssistant.dto.ApiResponse.<DocumentDetailDto>success(detailDto));
        } catch (Exception e) {
            log.error("获取文档详情失败", e);
            return ResponseEntity.internalServerError()
                .body(com.river.LegalAssistant.dto.ApiResponse.errorWithCode(
                    "获取文档详情失败: " + e.getMessage(),
                    "FETCH_FAILED"));
        }
    }


    /**
     * 删除单个文档
     */
    @DeleteMapping("/documents/{docId}")
    @Operation(summary = "删除单个文档", 
               description = """
                   删除指定ID的单个文档。此操作将从数据库和向量存储中完全移除文档及其所有相关数据，操作不可恢复。请谨慎使用。
                   """)
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "删除成功"),
        @ApiResponse(responseCode = "401", description = "用户未认证"),
        @ApiResponse(responseCode = "403", description = "权限不足"),
        @ApiResponse(responseCode = "404", description = "文档不存在"),
        @ApiResponse(responseCode = "500", description = "删除失败")
    })
    public ResponseEntity<com.river.LegalAssistant.dto.ApiResponse<DocumentDeleteResultDto>> deleteSingleDocument(
            @Parameter(description = "待删除文档的唯一ID", required = true, example = "doc_4a5c6d...")
            @PathVariable String docId) {
        
        log.info("管理员删除单个文档: {}", docId);
        
        try {
            boolean deleted = knowledgeBaseService.deleteDocument(docId);
            
            DocumentDeleteResultDto resultDto = DocumentDeleteResultDto.builder()
                .deletionMode("单个删除")
                .docId(docId)
                .totalRequested(1)
                .successCount(deleted ? 1 : 0)
                .failedCount(deleted ? 0 : 1)
                .successDocs(deleted ? List.of(docId) : Collections.emptyList())
                .failedDocs(deleted ? Collections.emptyList() : List.of(docId))
                .build();
            
            String message = deleted ? "文档删除成功" : "文档删除失败，文档可能不存在";
            return ResponseEntity.ok(com.river.LegalAssistant.dto.ApiResponse.<DocumentDeleteResultDto>success(resultDto, message));
            
        } catch (Exception e) {
            log.error("单个文档删除失败: {}", docId, e);
            return ResponseEntity.internalServerError()
                .body(com.river.LegalAssistant.dto.ApiResponse.errorWithCode(
                    "文档删除失败: " + e.getMessage(),
                    "DELETE_FAILED"));
        }
    }

    /**
     * 批量删除文档
     */
    @PostMapping("/documents/batch-delete")
    @Operation(summary = "批量删除文档", 
               description = """
                   批量删除多个文档。此操作将从数据库和向量存储中完全移除文档及其所有相关数据，操作不可恢复。请谨慎使用。
                   
                   **注意事项**：
                   - 支持数字ID和字符串哈希ID
                   - 如果某个ID不存在，该ID会被记录在failedDocs中，但不会影响其他文档的删除
                   - 最大支持一次删除100个文档
                   """)
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "批量删除完成"),
        @ApiResponse(responseCode = "400", description = "请求参数错误"),
        @ApiResponse(responseCode = "401", description = "用户未认证"),
        @ApiResponse(responseCode = "403", description = "权限不足"),
        @ApiResponse(responseCode = "500", description = "删除操作失败")
    })
    public ResponseEntity<com.river.LegalAssistant.dto.ApiResponse<DocumentDeleteResultDto>> deleteBatchDocuments(
            @Parameter(description = "待删除的文档ID列表", required = true, 
                       schema = @Schema(example = "[\"1\", \"2\", \"3\", \"doc_abc123\"]"))
            @RequestBody List<String> docIds) {
        
        log.info("管理员批量删除 {} 个文档", docIds.size());
        
        try {
            // 验证文档ID列表
            if (docIds.isEmpty()) {
                return ResponseEntity.badRequest()
                    .body(com.river.LegalAssistant.dto.ApiResponse.errorWithCode(
                        "文档ID列表不能为空",
                        "EMPTY_DOC_ID_LIST"));
            }
            
            // 限制一次删除的文档数量
            if (docIds.size() > 100) {
                return ResponseEntity.badRequest()
                    .body(com.river.LegalAssistant.dto.ApiResponse.errorWithCode(
                        "一次最多只能删除100个文档，当前请求删除 " + docIds.size() + " 个文档",
                        "TOO_MANY_DOCUMENTS"));
            }
            
            // 执行批量删除，Service层直接返回DTO
            DocumentDeleteResultDto resultDto = knowledgeBaseService.batchDeleteDocuments(docIds);
            
            return ResponseEntity.ok(com.river.LegalAssistant.dto.ApiResponse.<DocumentDeleteResultDto>success(resultDto, "批量删除完成"));
            
        } catch (Exception e) {
            log.error("批量文档删除失败", e);
            return ResponseEntity.internalServerError()
                .body(com.river.LegalAssistant.dto.ApiResponse.errorWithCode(
                    "批量删除失败: " + e.getMessage(),
                    "BATCH_DELETE_FAILED"));
        }
    }

    /**
     * 获取文档的向量块信息
     */
    @GetMapping("/documents/{docId}/chunks")
    @Operation(summary = "获取文档向量块", 
               description = """
                   获取指定文档的所有向量块信息，包括内容预览、序号、长度等。
                   用于查看文档的分块和向量化结果。
                   """)
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "获取成功"),
        @ApiResponse(responseCode = "401", description = "用户未认证"),
        @ApiResponse(responseCode = "403", description = "权限不足"),
        @ApiResponse(responseCode = "404", description = "文档不存在"),
        @ApiResponse(responseCode = "500", description = "获取失败")
    })
    public ResponseEntity<com.river.LegalAssistant.dto.ApiResponse<List<DocumentChunkDto>>> getDocumentChunks(
            @Parameter(description = "文档的唯一ID", required = true)
            @PathVariable String docId) {
        
        log.info("管理员获取文档向量块: {}", docId);
        
        try {
            List<DocumentChunkDto> chunks = knowledgeBaseService.getDocumentChunks(docId);
            
            if (chunks == null) {
                return ResponseEntity.status(404)
                    .body(com.river.LegalAssistant.dto.ApiResponse.errorWithCode(
                        "文档不存在",
                        "DOCUMENT_NOT_FOUND"));
            }
            
            return ResponseEntity.ok(
                com.river.LegalAssistant.dto.ApiResponse.<List<DocumentChunkDto>>success(
                    chunks, "获取向量块成功"));
        } catch (Exception e) {
            log.error("获取文档向量块失败: {}", docId, e);
            return ResponseEntity.internalServerError()
                .body(com.river.LegalAssistant.dto.ApiResponse.errorWithCode(
                    "获取向量块失败: " + e.getMessage(),
                    "FETCH_CHUNKS_FAILED"));
        }
    }

    /**
     * 重新处理单个文档
     */
    @PostMapping("/documents/{docId}/reprocess")
    @Operation(summary = "重新处理文档", 
               description = """
                   重新处理指定的文档，包括重新解析、分块、向量化等操作。
                   当文档内容或处理参数发生变化时可使用此功能。
                   此操作将在后台异步执行。
                   """)
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "重新处理任务已启动"),
        @ApiResponse(responseCode = "401", description = "用户未认证"),
        @ApiResponse(responseCode = "403", description = "权限不足"),
        @ApiResponse(responseCode = "404", description = "文档不存在"),
        @ApiResponse(responseCode = "500", description = "启动重新处理任务失败")
    })
    public ResponseEntity<com.river.LegalAssistant.dto.ApiResponse<String>> reprocessDocument(
            @Parameter(description = "待重新处理文档的唯一ID", required = true)
            @PathVariable String docId) {
        
        log.info("管理员请求重新处理文档: {}", docId);
        
        try {
            // 异步执行重新处理
            knowledgeBaseService.reprocessDocument(docId);
            
            return ResponseEntity.ok(
                com.river.LegalAssistant.dto.ApiResponse.success(
                    "文档重新处理任务已启动，请稍后查看结果", 
                    "重新处理任务已启动"));
        } catch (IllegalArgumentException e) {
            log.error("重新处理文档失败: 文档不存在 - {}", docId);
            return ResponseEntity.status(404)
                .body(com.river.LegalAssistant.dto.ApiResponse.errorWithCode(
                    "文档不存在: " + e.getMessage(),
                    "DOCUMENT_NOT_FOUND"));
        } catch (Exception e) {
            log.error("重新处理文档失败: {}", docId, e);
            return ResponseEntity.internalServerError()
                .body(com.river.LegalAssistant.dto.ApiResponse.errorWithCode(
                    "重新处理文档失败: " + e.getMessage(),
                    "REPROCESS_FAILED"));
        }
    }

    /**
     * 更新文档元数据 - 支持JSON格式
     */
    @PutMapping("/documents/{docId}")
    @Operation(summary = "更新文档信息", description = "更新知识库中已存在文档的元数据，如分类和描述。支持JSON格式的请求体。")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "更新成功"),
        @ApiResponse(responseCode = "401", description = "用户未认证"),
        @ApiResponse(responseCode = "403", description = "权限不足"),
        @ApiResponse(responseCode = "404", description = "指定的文档ID不存在")
    })
    public ResponseEntity<com.river.LegalAssistant.dto.ApiResponse<DocumentDetailDto>> updateDocumentMetadata(
            @Parameter(description = "待更新文档的唯一ID", required = true, example = "doc_4a5c6d...")
            @PathVariable String docId,
            @Parameter(description = "包含待更新字段的JSON对象。目前支持 `category` 和 `description`。", required = true, schema = @Schema(example = "{\"category\":\"劳动合同法\",\"description\":\"2023年最新版\"}"))
            @RequestBody Map<String, String> updateData) {
        
        log.info("管理员更新文档元数据 (JSON): {}", docId);
        
        try {
            // Service层直接返回DTO
            DocumentDetailDto detailDto = knowledgeBaseService.updateDocumentMetadata(docId, updateData);
            
            if (detailDto == null) {
                return ResponseEntity.status(404)
                    .body(com.river.LegalAssistant.dto.ApiResponse.errorWithCode(
                        "文档不存在",
                        "DOCUMENT_NOT_FOUND"));
            }
            
            return ResponseEntity.ok(com.river.LegalAssistant.dto.ApiResponse.<DocumentDetailDto>success(detailDto, "文档信息更新成功"));
        } catch (Exception e) {
            log.error("更新文档元数据失败", e);
            return ResponseEntity.internalServerError()
                .body(com.river.LegalAssistant.dto.ApiResponse.errorWithCode(
                    "更新文档信息失败: " + e.getMessage(),
                    "UPDATE_FAILED"));
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
    public ResponseEntity<com.river.LegalAssistant.dto.ApiResponse<KnowledgeBaseStatsDto>> getStatistics() {
        try {
            // Service层直接返回DTO
            KnowledgeBaseStatsDto statsDto = knowledgeBaseService.getStatistics();
            
            return ResponseEntity.ok(com.river.LegalAssistant.dto.ApiResponse.<KnowledgeBaseStatsDto>success(statsDto));
        } catch (Exception e) {
            log.error("获取知识库统计失败", e);
            return ResponseEntity.internalServerError()
                .body(com.river.LegalAssistant.dto.ApiResponse.errorWithCode(
                    "获取统计信息失败: " + e.getMessage(),
                    "FETCH_STATS_FAILED"));
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
