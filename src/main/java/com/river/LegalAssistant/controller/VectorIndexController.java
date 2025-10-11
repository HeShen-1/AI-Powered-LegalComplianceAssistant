package com.river.LegalAssistant.controller;

import com.river.LegalAssistant.dto.DocumentIndexResultDto;
import com.river.LegalAssistant.dto.IndexingStatisticsDto;
import com.river.LegalAssistant.service.DocumentIndexingService;
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

import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 向量索引管理控制器
 * 
 * 负责向量数据库的索引构建、重建和统计查询等运维操作。
 * 与 KnowledgeBaseController 分离，实现单一职责原则：
 * - KnowledgeBaseController: 文档元数据的CRUD管理
 * - VectorIndexController: AI向量索引的运维操作
 * 
 * 仅管理员可访问
 */
@RestController
@RequestMapping("/vector-index")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "向量索引管理", description = "向量数据库索引的构建、重建和统计查询等运维操作")
@SecurityRequirement(name = "bearerAuth")
@PreAuthorize("hasRole('ADMIN')")
public class VectorIndexController {

    private final KnowledgeBaseService knowledgeBaseService;
    private final DocumentIndexingService documentIndexingService;

    /**
     * 重建知识库索引
     */
    @PostMapping("/rebuild")
    @Operation(summary = "重建索引", 
               description = "（高阶运维操作）触发对整个知识库的向量索引进行重建。当底层嵌入模型更换或索引结构需要优化时使用。这是一个耗时操作，将在后台异步执行。")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "重建任务已启动"),
        @ApiResponse(responseCode = "401", description = "用户未认证"),
        @ApiResponse(responseCode = "403", description = "权限不足，非管理员用户")
    })
    public ResponseEntity<com.river.LegalAssistant.dto.ApiResponse<Void>> rebuildIndex() {
        log.info("管理员请求重建知识库索引");
        
        try {
            knowledgeBaseService.reindexKnowledgeBase();
            
            return ResponseEntity.ok(
                com.river.LegalAssistant.dto.ApiResponse.<String>success("知识库索引重建已开始，请稍后查看结果"));
        } catch (Exception e) {
            log.error("重建知识库索引失败", e);
            return ResponseEntity.internalServerError()
                .body(com.river.LegalAssistant.dto.ApiResponse.errorWithCode(
                    "重建索引失败: " + e.getMessage(),
                    "REINDEX_FAILED"));
        }
    }

    /**
     * 索引法律文档（统一接口）
     */
    @PostMapping("/documents")
    @Operation(summary = "索引法律文档", 
               description = """
                   统一的文档索引接口，支持索引单个文档或批量索引目录中的所有支持格式的文档到LangChain4j PGVector向量数据库中。
                   
                   **支持的索引模式**：
                   1. 单个文档索引：提供filePath参数
                   2. 目录批量索引：提供directoryPaths参数（支持多个目录）
                   3. 预设目录索引：不提供参数，默认索引uploads/law和uploads/contracts目录
                   
                   **支持的文档格式**：PDF、DOCX、DOC、TXT、MD
                   
                   **注意事项**：
                   - 此接口仅负责向量化和索引构建，不管理文档元数据
                   - 文档元数据管理请使用 /knowledge-base 相关接口
                   """)
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "索引成功", 
            content = @Content(mediaType = "application/json",
                schema = @Schema(example = """
                    {
                      "success": true,
                      "indexingMode": "批量索引",
                      "totalDirectories": 2,
                      "totalDocuments": 15,
                      "successfulDocuments": 14,
                      "failedDocuments": 1,
                      "totalSegments": 1200,
                      "duration": "45.2s",
                      "results": [...],
                      "message": "法律文档索引完成，可以开始使用Advanced RAG进行法律咨询",
                      "timestamp": "2025-09-30T18:00:00"
                    }
                    """))),
        @ApiResponse(responseCode = "400", description = "请求参数错误"),
        @ApiResponse(responseCode = "401", description = "用户未认证"),
        @ApiResponse(responseCode = "403", description = "权限不足，非管理员用户"),
        @ApiResponse(responseCode = "500", description = "服务器内部错误")
    })
    public ResponseEntity<com.river.LegalAssistant.dto.ApiResponse<DocumentIndexResultDto>> indexDocuments(
            @Parameter(description = """
                单个文档文件路径。与directoryPaths参数互斥。
                
                **示例**: `uploads/law/民法典.pdf`
                """)
            @RequestParam(required = false) String filePath,
            
            @Parameter(description = """
                要批量索引的目录路径列表，用逗号分隔。与filePath参数互斥。
                
                **示例**: `uploads/law,uploads/contracts,documents/regulations`
                **注意**: 如果不提供此参数且filePath也为空，将默认索引uploads/law和uploads/contracts目录
                """)
            @RequestParam(required = false) String directoryPaths,
            
            @Parameter(description = "是否递归搜索子目录（仅在目录索引模式下有效）", example = "true")
            @RequestParam(defaultValue = "false") boolean recursive,
            
            @Parameter(description = """
                文档分类标签，用于标识本次索引的文档类型。
                
                **常用分类**: 法律法规、合同模板、案例判决、部门规章
                """)
            @RequestParam(defaultValue = "法律文档") String category) {
        
        log.info("统一文档索引请求 - 文件路径: {}, 目录路径: {}, 递归: {}, 分类: {}", 
                filePath, directoryPaths, recursive, category);
        
        try {
            // 单个文档索引模式
            if (filePath != null && !filePath.trim().isEmpty()) {
                if (directoryPaths != null && !directoryPaths.trim().isEmpty()) {
                    return ResponseEntity.badRequest()
                        .body(com.river.LegalAssistant.dto.ApiResponse.errorWithCode(
                            "filePath 和 directoryPaths 参数不能同时提供",
                            "CONFLICTING_PARAMETERS"));
                }
                
                log.info("执行单个文档索引: {}", filePath);
                DocumentIndexingService.IndexingResult result = documentIndexingService.indexDocument(filePath);
                
                DocumentIndexResultDto resultDto = DocumentIndexResultDto.builder()
                    .indexingMode("单个文档")
                    .filePath(result.filePath())
                    .segmentCount(result.segmentCount())
                    .duration(result.duration() + "ms")
                    .category(category)
                    .error(result.error())
                    .build();
                
                String message = result.success() ? "文档索引完成，已添加到向量数据库" : "文档索引失败";
                return ResponseEntity.ok(com.river.LegalAssistant.dto.ApiResponse.<DocumentIndexResultDto>success(resultDto, message));
            }
            
            // 批量目录索引模式
            List<String> directories = new ArrayList<>();
            
            if (directoryPaths != null && !directoryPaths.trim().isEmpty()) {
                // 使用提供的目录路径
                directories.addAll(Arrays.asList(directoryPaths.split(",")));
                directories = directories.stream()
                        .map(String::trim)
                        .filter(dir -> !dir.isEmpty())
                        .collect(Collectors.toList());
            } else {
                // 使用默认目录
                directories.add("uploads/law");
                directories.add("uploads/contracts");
                log.info("未提供目录参数，使用默认目录: {}", directories);
            }
            
            if (directories.isEmpty()) {
                return ResponseEntity.badRequest()
                    .body(com.river.LegalAssistant.dto.ApiResponse.errorWithCode(
                        "至少需要提供一个有效的目录路径",
                        "NO_VALID_DIRECTORY"));
            }
            
            log.info("执行批量目录索引: {}, 递归: {}", directories, recursive);
            
            // 执行批量索引
            List<DocumentIndexingService.BatchIndexingResult> allResults = new ArrayList<>();
            int totalDocuments = 0;
            int successfulDocuments = 0;
            int totalSegments = 0;
            long totalDuration = 0;
            List<String> errors = new ArrayList<>();
            
            for (String directory : directories) {
                try {
                    DocumentIndexingService.BatchIndexingResult result = 
                        documentIndexingService.indexDirectory(directory.trim(), recursive);
                    
                    allResults.add(result);
                    totalDocuments += result.totalDocuments();
                    successfulDocuments += result.successfulDocuments();
                    totalSegments += result.totalSegments();
                    totalDuration += result.duration();
                    
                    if (!result.success() && result.error() != null) {
                        errors.add(directory + ": " + result.error());
                    }
                } catch (Exception e) {
                    log.error("索引目录失败: {}", directory, e);
                    errors.add(directory + ": " + e.getMessage());
                }
            }
            
            // 转换详细结果
            List<DocumentIndexResultDto.DirectoryIndexResult> detailResults = allResults.stream()
                .map(result -> DocumentIndexResultDto.DirectoryIndexResult.builder()
                    .directory(result.directoryPath())
                    .success(result.success())
                    .documents(result.totalDocuments())
                    .segments(result.totalSegments())
                    .duration(result.duration() + "ms")
                    .error(result.error() != null ? result.error() : "")
                    .build())
                .collect(Collectors.toList());
            
            // 构建响应DTO
            DocumentIndexResultDto resultDto = DocumentIndexResultDto.builder()
                .indexingMode("批量索引")
                .totalDirectories(directories.size())
                .directories(directories)
                .recursive(recursive)
                .category(category)
                .totalDocuments(totalDocuments)
                .successfulDocuments(successfulDocuments)
                .failedDocuments(totalDocuments - successfulDocuments)
                .totalSegments(totalSegments)
                .duration(totalDuration + "ms")
                .errors(errors.isEmpty() ? null : errors)
                .results(detailResults)
                .build();
            
            // 成功消息
            String message;
            if (successfulDocuments > 0) {
                if (totalDocuments == successfulDocuments) {
                    message = String.format(
                        "所有法律文档索引完成！共处理 %d 个文档，生成 %d 个文本片段，可以开始使用Advanced RAG进行法律咨询", 
                        successfulDocuments, totalSegments);
                } else {
                    message = String.format(
                        "部分法律文档索引完成！成功处理 %d/%d 个文档，生成 %d 个文本片段", 
                        successfulDocuments, totalDocuments, totalSegments);
                }
            } else {
                message = "没有成功索引任何文档，请检查目录路径和文档格式";
            }
            
            return ResponseEntity.ok(com.river.LegalAssistant.dto.ApiResponse.<DocumentIndexResultDto>success(resultDto, message));
            
        } catch (Exception e) {
            log.error("统一文档索引失败", e);
            return ResponseEntity.status(500)
                .body(com.river.LegalAssistant.dto.ApiResponse.errorWithCode(
                    "文档索引失败: " + e.getMessage(),
                    "INDEX_FAILED"));
        }
    }

    /**
     * 获取索引统计信息
     */
    @GetMapping("/statistics")
    @Operation(summary = "获取索引统计信息", 
               description = "获取向量数据库中的文档索引统计信息，包括总片段数、最后更新时间、存储类型等")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "统计获取成功", 
            content = @Content(mediaType = "application/json")),
        @ApiResponse(responseCode = "401", description = "用户未认证"),
        @ApiResponse(responseCode = "403", description = "权限不足，非管理员用户")
    })
    public ResponseEntity<com.river.LegalAssistant.dto.ApiResponse<IndexingStatisticsDto>> getIndexingStatistics() {
        
        try {
            DocumentIndexingService.IndexingStatistics stats = documentIndexingService.getIndexingStatistics();
            
            IndexingStatisticsDto statsDto = IndexingStatisticsDto.builder()
                .totalSegments(stats.totalSegments())
                .lastUpdate(stats.lastUpdate() != null ? stats.lastUpdate().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME) : null)
                .storeType(stats.storeType())
                .chunkSize(stats.chunkSize())
                .chunkOverlap(stats.chunkOverlap())
                .build();
            
            return ResponseEntity.ok(com.river.LegalAssistant.dto.ApiResponse.<IndexingStatisticsDto>success(statsDto));
            
        } catch (Exception e) {
            log.error("获取索引统计失败", e);
            return ResponseEntity.internalServerError()
                .body(com.river.LegalAssistant.dto.ApiResponse.errorWithCode(
                    "获取统计信息失败: " + e.getMessage(),
                    "STATISTICS_FAILED"));
        }
    }
}

