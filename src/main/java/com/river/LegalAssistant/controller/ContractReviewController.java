package com.river.LegalAssistant.controller;

import com.river.LegalAssistant.dto.ContractReviewResultDto;
import com.river.LegalAssistant.entity.ContractReview;
import com.river.LegalAssistant.entity.User;
import com.river.LegalAssistant.service.ContractReviewService;
import com.river.LegalAssistant.service.DocumentParserService;
import com.river.LegalAssistant.service.ReportGenerationService;
import com.river.LegalAssistant.service.UserService;
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
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 合同审查控制器
 */
@RestController
@RequestMapping("/api/contracts")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "合同审查", description = "合同上传、审查、结果查询等接口")
@SecurityRequirement(name = "bearerAuth")
public class ContractReviewController {

    private final ContractReviewService contractReviewService;
    private final UserService userService;
    private final DocumentParserService documentParserService;
    private final ReportGenerationService reportGenerationService;

    /**
     * 上传合同文件并创建审查任务
     */
    @PostMapping("/upload")
    @Operation(summary = "上传合同文件", description = "上传合同文件（支持.docx, .pdf, .txt），系统将自动解析文件内容并创建审查任务。")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "文件上传成功，审查任务已创建",
            content = @Content(mediaType = "application/json", schema = @Schema(example = "{\"success\":true,\"message\":\"文件上传成功\",\"reviewId\":1,\"status\":\"PENDING\",\"supportedAnalysis\":true}"))),
        @ApiResponse(responseCode = "400", description = "请求无效（如文件为空、文件名为空、文件类型不支持）", content = @Content(schema = @Schema(example = "{\"success\":false,\"message\":\"不支持的文件类型...\"}"))),
        @ApiResponse(responseCode = "401", description = "用户未认证"),
        @ApiResponse(responseCode = "500", description = "服务器内部错误，文件处理失败")
    })
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Map<String, Object>> uploadContract(
            @Parameter(description = "待审查的合同文件。支持的格式：.docx, .pdf, .txt", required = true)
            @RequestParam("file") MultipartFile file,
            Authentication authentication) {
        try {
            // 获取当前用户
            User currentUser = userService.findByUsername(authentication.getName())
                    .orElseThrow(() -> new IllegalArgumentException("用户不存在"));

            // 验证文件
            if (file.isEmpty()) {
                return getResponseEntity();
            }

            // 验证文件类型
            String fileName = file.getOriginalFilename();
            if (fileName == null || fileName.trim().isEmpty()) {
                return getBody();
            }

            String fileExtension = getFileExtension(fileName);
            if (documentParserService.isFileTypeSupported(fileExtension)) {
                return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "message", "不支持的文件类型，支持的类型: " + 
                              String.join(", ", documentParserService.getSupportedFileTypes())
                ));
            }

            // 保存文件
            String fileHash = calculateFileHash(file.getBytes());
            Path filePath = saveFile(file, fileHash);

            // 使用DocumentParserService创建审查记录
            ContractReview review = contractReviewService.createContractReviewFromFile(
                currentUser,
                fileName,
                filePath.toString(),
                file.getSize(),
                fileHash,
                file.getInputStream()
            );

            return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "文件上传成功",
                "reviewId", review.getId(),
                "status", review.getReviewStatus(),
                "supportedAnalysis", true
            ));

        } catch (Exception e) {
            log.error("文件上传失败", e);
            return ResponseEntity.internalServerError().body(Map.of(
                "success", false,
                "message", "文件上传失败: " + e.getMessage()
            ));
        }
    }

    private static ResponseEntity<Map<String, Object>> getBody() {
        return ResponseEntity.badRequest().body(Map.of(
                "success", false,
                "message", "文件名不能为空"
        ));
    }

    private static ResponseEntity<Map<String, Object>> getResponseEntity() {
        return ResponseEntity.badRequest().body(Map.of(
                "success", false,
                "message", "文件不能为空"
        ));
    }

    /**
     * 异步合同审查（SSE推送进度）
     */
    @PostMapping("/{reviewId}/analyze-async")
    @Operation(summary = "异步合同审查", description = "对已上传的合同启动异步AI审查。服务器将通过Server-Sent Events (SSE)实时推送分析进度和结果。")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "分析任务已启动，开始推送SSE事件流",
            content = @Content(mediaType = "text/event-stream", schema = @Schema(example = "event: progress\ndata: { \"stage\": \"文本提取\", \"progress\": 20, \"message\": \"正在提取合同文本...\" }\n\nevent: result\ndata: { ...审查结果... }\n\nevent: complete\ndata: { \"message\": \"分析完成\" }\n"))),
        @ApiResponse(responseCode = "401", description = "用户未认证"),
        @ApiResponse(responseCode = "404", description = "指定的审查记录不存在"),
        @ApiResponse(responseCode = "500", description = "启动分析任务失败")
    })
    @PreAuthorize("isAuthenticated()")
    public SseEmitter analyzeContractAsync(
            @Parameter(description = "审查任务的唯一ID", required = true, example = "1")
            @PathVariable Long reviewId) {
        
        log.info("开始异步合同分析，审查ID: {}", reviewId);
        
        // 创建SSE发射器，设置超时时间为5分钟
        SseEmitter emitter = new SseEmitter(5 * 60 * 1000L);
        
        // 设置完成和超时回调
        emitter.onCompletion(() -> log.info("SSE连接完成: reviewId={}", reviewId));
        emitter.onTimeout(() -> {
            log.warn("SSE连接超时: reviewId={}", reviewId);
            emitter.completeWithError(new RuntimeException("分析超时"));
        });
        emitter.onError((ex) -> {
            log.error("SSE连接错误: reviewId={}", reviewId, ex);
            emitter.completeWithError(ex);
        });
        
        try {
            // 异步执行分析
            contractReviewService.analyzeContractAsync(reviewId, emitter);
        } catch (Exception e) {
            log.error("启动异步分析失败", e);
            emitter.completeWithError(e);
        }
        
        return emitter;
    }

    /**
     * 开始合同审查（同步版本，保持向后兼容）
     */
    @PostMapping("/{reviewId}/analyze")
    @Operation(summary = "同步合同审查", description = "对已上传的合同文件进行AI审查（同步阻塞）。【注意】此接口为保持向后兼容性而保留，对于较大文件可能导致长时间等待，推荐使用异步接口 `/analyze-async`。")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "审查完成",
            content = @Content(mediaType = "application/json", schema = @Schema(example = "{\"success\":true,\"message\":\"合同审查完成\",\"reviewId\":1,\"status\":\"COMPLETED\",\"riskLevel\":\"HIGH\",\"result\":{...}}"))),
        @ApiResponse(responseCode = "401", description = "用户未认证"),
        @ApiResponse(responseCode = "404", description = "指定的审查记录不存在"),
        @ApiResponse(responseCode = "500", description = "服务器内部错误")
    })
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Map<String, Object>> analyzeContract(
            @Parameter(description = "审查任务的唯一ID", required = true, example = "1")
            @PathVariable Long reviewId) {
        try {
            ContractReview review = contractReviewService.analyzeContract(reviewId);
            
            return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "合同审查完成",
                "reviewId", review.getId(),
                "status", review.getReviewStatus(),
                "riskLevel", review.getRiskLevel(),
                "result", review.getReviewResult()
            ));

        } catch (Exception e) {
            log.error("合同审查失败", e);
            return ResponseEntity.internalServerError().body(Map.of(
                "success", false,
                "message", "合同审查失败: " + e.getMessage()
            ));
        }
    }

    /**
     * 查询用户的审查记录
     */
    @GetMapping("/my-reviews")
    @Operation(summary = "查询我的审查记录", description = "分页查询当前认证用户的合同审查历史记录。")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "查询成功", content = @Content(mediaType = "application/json")),
        @ApiResponse(responseCode = "401", description = "用户未认证")
    })
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Map<String, Object>> getMyReviews(
            @Parameter(description = "页码，从0开始", example = "0")
            @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "每页记录数", example = "10")
            @RequestParam(defaultValue = "10") int size,
            Authentication authentication) {
        
        User currentUser = userService.findByUsername(authentication.getName())
                .orElseThrow(() -> new IllegalArgumentException("用户不存在"));

        Pageable pageable = PageRequest.of(page, size);
        Page<ContractReview> reviews = contractReviewService.getReviewsByUser(currentUser.getId(), pageable);
        
        return ResponseEntity.ok(Map.of(
            "success", true,
            "data", reviews.getContent(),
            "totalElements", reviews.getTotalElements(),
            "totalPages", reviews.getTotalPages(),
            "currentPage", reviews.getNumber()
        ));
    }

    /**
     * 获取审查详情
     */
    @GetMapping("/{reviewId}")
    @Operation(summary = "获取审查详情", description = "根据ID获取单次合同审查的详细结果，包括风险条款、修改建议和审查摘要。")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "获取成功",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ContractReviewResultDto.class))),
        @ApiResponse(responseCode = "401", description = "用户未认证"),
        @ApiResponse(responseCode = "403", description = "无权访问此审查记录"),
        @ApiResponse(responseCode = "404", description = "指定的审查记录不存在")
    })
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Map<String, Object>> getReviewDetail(
            @Parameter(description = "审查任务的唯一ID", required = true, example = "1")
            @PathVariable Long reviewId) {
        
        Optional<ContractReview> reviewOpt = contractReviewService.findById(reviewId);
        
        if (reviewOpt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        
        ContractReview review = reviewOpt.get();
        
        // 转换为详细的DTO
        ContractReviewResultDto resultDto = ContractReviewResultDto.fromEntity(review);
        
        // 构建审查摘要
        if (review.getReviewResult() != null && review.getReviewStatus() == ContractReview.ReviewStatus.COMPLETED) {
            ContractReviewResultDto.ReviewSummaryDto summary = buildReviewSummary(review);
            resultDto.setSummary(summary);
        }
        
        return ResponseEntity.ok(Map.of(
            "success", true,
            "data", resultDto
        ));
    }

    /**
     * 获取审查详情（简化版本）
     */
    @GetMapping("/{reviewId}/summary")
    @Operation(summary = "获取审查摘要", description = "获取指定合同审查的核心信息摘要，用于在列表等场景快速展示。")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "获取成功", content = @Content(mediaType = "application/json", schema = @Schema(example = "{\"success\":true,\"data\":{\"id\":1,\"originalFilename\":\"合作协议.docx\",\"reviewStatus\":\"COMPLETED\",\"riskLevel\":\"MEDIUM\",\"totalRisks\":5,\"createdAt\":\"...\",\"completedAt\":\"...\"}}"))),
        @ApiResponse(responseCode = "401", description = "用户未认证"),
        @ApiResponse(responseCode = "404", description = "指定的审查记录不存在")
    })
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Map<String, Object>> getReviewSummary(
            @Parameter(description = "审查任务的唯一ID", required = true, example = "1")
            @PathVariable Long reviewId) {
        
        Optional<ContractReview> reviewOpt = contractReviewService.findById(reviewId);
        
        if (reviewOpt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        
        ContractReview review = reviewOpt.get();
        
        return ResponseEntity.ok(Map.of(
            "success", true,
            "data", Map.of(
                "id", review.getId(),
                "originalFilename", review.getOriginalFilename(),
                "reviewStatus", review.getReviewStatus(),
                "riskLevel", review.getRiskLevel(),
                "totalRisks", review.getTotalRisks(),
                "createdAt", review.getCreatedAt(),
                "completedAt", review.getCompletedAt()
            )
        ));
    }

    /**
     * 管理员查询所有审查记录
     */
    @GetMapping("/admin/all")
    @Operation(summary = "查询所有审查记录（管理员）", description = "管理员根据状态或日期范围查询系统内所有的合同审查记录。")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "查询成功"),
        @ApiResponse(responseCode = "401", description = "用户未认证"),
        @ApiResponse(responseCode = "403", description = "当前用户无管理员权限")
    })
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Map<String, Object>> getAllReviews(
            @Parameter(description = "根据审查状态过滤", example = "COMPLETED", schema = @Schema(implementation = ContractReview.ReviewStatus.class))
            @RequestParam(required = false) String status,
            @Parameter(description = "查询起始日期时间（ISO格式：YYYY-MM-DDTHH:mm:ss）", example = "2023-10-01T00:00:00")
            @RequestParam(required = false) String startDate,
            @Parameter(description = "查询结束日期时间（ISO格式：YYYY-MM-DDTHH:mm:ss）", example = "2023-10-31T23:59:59")
            @RequestParam(required = false) String endDate) {
        
        try {
            List<ContractReview> reviews;
            
            if (status != null) {
                ContractReview.ReviewStatus reviewStatus = ContractReview.ReviewStatus.valueOf(status.toUpperCase());
                reviews = contractReviewService.getReviewsByStatus(reviewStatus);
            } else if (startDate != null && endDate != null) {
                LocalDateTime start = LocalDateTime.parse(startDate, DateTimeFormatter.ISO_LOCAL_DATE_TIME);
                LocalDateTime end = LocalDateTime.parse(endDate, DateTimeFormatter.ISO_LOCAL_DATE_TIME);
                reviews = contractReviewService.getReviewsByDateRange(start, end);
            } else {
                // 默认查询最近的记录
                LocalDateTime start = LocalDateTime.now().minusDays(30);
                LocalDateTime end = LocalDateTime.now();
                reviews = contractReviewService.getReviewsByDateRange(start, end);
            }
            
            return ResponseEntity.ok(Map.of(
                "success", true,
                "data", reviews
            ));
            
        } catch (Exception e) {
            log.error("查询审查记录失败", e);
            return ResponseEntity.internalServerError().body(Map.of(
                "success", false,
                "message", "查询失败: " + e.getMessage()
            ));
        }
    }

    /**
     * 获取风险等级统计
     */
    @GetMapping("/statistics/risk-levels")
    @Operation(summary = "风险等级统计（管理员）", description = "管理员获取系统中所有已完成审查的合同风险等级分布统计。")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "获取成功", content = @Content(mediaType = "application/json", schema = @Schema(example = "{\"success\":true,\"data\":{\"HIGH\":10,\"MEDIUM\":25,\"LOW\":15}}"))),
        @ApiResponse(responseCode = "401", description = "用户未认证"),
        @ApiResponse(responseCode = "403", description = "当前用户无管理员权限")
    })
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Map<String, Object>> getRiskLevelStatistics() {
        Map<String, Long> statistics = contractReviewService.getRiskLevelStatistics();
        
        return ResponseEntity.ok(Map.of(
            "success", true,
            "data", statistics
        ));
    }

    /**
     * 获取用户审查统计
     */
    @GetMapping("/statistics/user/{userId}")
    @Operation(summary = "用户审查统计（管理员）", description = "管理员获取指定用户的合同审查总数统计。")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "获取成功", content = @Content(mediaType = "application/json", schema = @Schema(example = "{\"success\":true,\"data\":{\"userId\":123,\"totalReviews\":50}}"))),
        @ApiResponse(responseCode = "401", description = "用户未认证"),
        @ApiResponse(responseCode = "403", description = "当前用户无管理员权限")
    })
    @PreAuthorize("hasRole('ADMIN') or authentication.principal.id == #userId")
    public ResponseEntity<Map<String, Object>> getUserReviewStatistics(@Parameter(description = "要查询的用户ID", required = true, example = "1") @PathVariable Long userId) {
        try {
            long reviewCount = contractReviewService.countReviewsByUser(userId);
            
            return ResponseEntity.ok(Map.of(
                "success", true,
                "data", Map.of(
                    "userId", userId,
                    "totalReviews", reviewCount
                )
            ));
        } catch (Exception e) {
            log.error("获取用户审查统计失败", e);
            return ResponseEntity.internalServerError().body(Map.of(
                "success", false,
                "message", "获取统计失败: " + e.getMessage()
            ));
        }
    }

    /**
     * 获取我的审查统计
     */
    @GetMapping("/my-statistics")
    @Operation(summary = "我的审查统计", description = "获取当前认证用户的个人合同审查总数统计。")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "获取成功", content = @Content(mediaType = "application/json", schema = @Schema(example = "{\"success\":true,\"data\":{\"userId\":1,\"username\":\"testuser\",\"totalReviews\":15}}"))),
        @ApiResponse(responseCode = "401", description = "用户未认证")
    })
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Map<String, Object>> getMyReviewStatistics(Authentication authentication) {
        try {
            User currentUser = userService.findByUsername(authentication.getName())
                    .orElseThrow(() -> new IllegalArgumentException("用户不存在"));
            
            long reviewCount = contractReviewService.countReviewsByUser(currentUser.getId());
            
            return ResponseEntity.ok(Map.of(
                "success", true,
                "data", Map.of(
                    "userId", currentUser.getId(),
                    "username", currentUser.getUsername(),
                    "totalReviews", reviewCount
                )
            ));
        } catch (Exception e) {
            log.error("获取我的审查统计失败", e);
            return ResponseEntity.internalServerError().body(Map.of(
                "success", false,
                "message", "获取统计失败: " + e.getMessage()
            ));
        }
    }

    /**
     * 生成合同审查PDF报告
     */
    @GetMapping("/{reviewId}/report")
    @Operation(summary = "生成PDF审查报告", description = "为指定的已完成审查任务生成并下载详细的PDF格式审查报告。")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "PDF报告生成成功",
            content = @Content(mediaType = "application/pdf")),
        @ApiResponse(responseCode = "400", description = "审查任务未完成，无法生成报告", content = @Content(mediaType = "application/json", schema = @Schema(example = "{\"success\":false,\"message\":\"审查未完成，无法生成报告。当前状态: 处理中\"}"))),
        @ApiResponse(responseCode = "401", description = "用户未认证"),
        @ApiResponse(responseCode = "403", description = "无权访问此审查记录的报告"),
        @ApiResponse(responseCode = "404", description = "指定的审查记录不存在"),
        @ApiResponse(responseCode = "500", description = "PDF报告生成失败")
    })
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<byte[]> generateReport(
            @Parameter(description = "审查任务的唯一ID", required = true, example = "1")
            @PathVariable Long reviewId,
            Authentication authentication) {
        
        log.info("用户 {} 请求生成PDF报告，审查ID: {}", authentication.getName(), reviewId);
        
        try {
            // 获取审查记录
            Optional<ContractReview> reviewOpt = contractReviewService.findById(reviewId);
            
            if (reviewOpt.isEmpty()) {
                return ResponseEntity.notFound().build();
            }
            
            ContractReview review = reviewOpt.get();
            
            // 权限检查：只有审查记录的所有者或管理员可以生成报告
            User currentUser = userService.findByUsername(authentication.getName())
                    .orElseThrow(() -> new IllegalArgumentException("用户不存在"));
            
            if (!review.getUser().getId().equals(currentUser.getId()) && 
                !currentUser.getRole().name().equals("ADMIN")) {
                return ResponseEntity.status(403).build();
            }
            
            // 检查审查状态
            if (review.getReviewStatus() != ContractReview.ReviewStatus.COMPLETED) {
                return ResponseEntity.badRequest()
                        .header("Content-Type", "application/json")
                        .body(("{\"success\": false, \"message\": \"审查未完成，无法生成报告。当前状态: " + 
                               getStatusDisplayName(review.getReviewStatus()) + "\"}").getBytes());
            }
            
            // 生成PDF报告
            byte[] pdfBytes = reportGenerationService.generateContractReviewReport(review);
            
            // 生成文件名
            String fileName = generateReportFileName(review);
            
            log.info("PDF报告生成成功，审查ID: {}, 文件大小: {} bytes", reviewId, pdfBytes.length);
            
            return ResponseEntity.ok()
                    .header("Content-Type", "application/pdf")
                    .header("Content-Disposition", "attachment; filename=\"" + fileName + "\"")
                    .header("Cache-Control", "no-cache")
                    .body(pdfBytes);
                    
        } catch (Exception e) {
            log.error("生成PDF报告失败，审查ID: {}", reviewId, e);
            return ResponseEntity.internalServerError()
                    .header("Content-Type", "application/json")
                    .body(("{\"success\": false, \"message\": \"PDF报告生成失败: " + e.getMessage() + "\"}").getBytes());
        }
    }

    /**
     * 计算文件哈希值
     */
    private String calculateFileHash(byte[] fileBytes) throws NoSuchAlgorithmException {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        byte[] hashBytes = digest.digest(fileBytes);
        StringBuilder hexString = new StringBuilder();
        
        for (byte b : hashBytes) {
            String hex = Integer.toHexString(0xff & b);
            if (hex.length() == 1) {
                hexString.append('0');
            }
            hexString.append(hex);
        }
        
        return hexString.toString();
    }

    /**
     * 保存文件到本地
     */
    private Path saveFile(MultipartFile file, String fileHash) throws IOException {
        String uploadDir = "uploads/contracts";
        Path uploadPath = Paths.get(uploadDir);
        
        if (!Files.exists(uploadPath)) {
            Files.createDirectories(uploadPath);
        }
        
        String fileName = fileHash + "_" + file.getOriginalFilename();
        Path filePath = uploadPath.resolve(fileName);
        Files.write(filePath, file.getBytes());
        
        return filePath;
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

    /**
     * 构建审查摘要
     */
    private ContractReviewResultDto.ReviewSummaryDto buildReviewSummary(ContractReview review) {
        ContractReviewResultDto.ReviewSummaryDto.ReviewSummaryDtoBuilder summaryBuilder = 
            ContractReviewResultDto.ReviewSummaryDto.builder()
                .overallRiskLevel(review.getRiskLevel() != null ? review.getRiskLevel().name() : "UNKNOWN");

        // 统计各风险等级数量
        if (review.getRiskClauses() != null && !review.getRiskClauses().isEmpty()) {
            int highRiskCount = 0;
            int mediumRiskCount = 0;
            int lowRiskCount = 0;

            for (var riskClause : review.getRiskClauses()) {
                switch (riskClause.getRiskLevel()) {
                    case HIGH -> highRiskCount++;
                    case MEDIUM -> mediumRiskCount++;
                    case LOW -> lowRiskCount++;
                }
            }

            summaryBuilder
                .highRiskCount(highRiskCount)
                .mediumRiskCount(mediumRiskCount)
                .lowRiskCount(lowRiskCount);
        }

        // 从分析结果中提取核心信息
        if (review.getReviewResult() != null) {
            Object coreRiskAlerts = review.getReviewResult().get("coreRiskAlerts");
            if (coreRiskAlerts instanceof List<?> alertsList) {
                summaryBuilder.coreRiskAlerts(alertsList.stream()
                    .map(Object::toString)
                    .toList());
            }

            Object priorityRecommendations = review.getReviewResult().get("priorityRecommendations");
            if (priorityRecommendations instanceof List<?> recommendationsList) {
                summaryBuilder.priorityRecommendations(recommendationsList.stream()
                    .map(Object::toString)
                    .toList());
            }

            Object complianceScore = review.getReviewResult().get("complianceScore");
            if (complianceScore instanceof Number score) {
                summaryBuilder.complianceScore(score.intValue());
            }
        }

        // 计算合同完整性评分（简化版本）
        int completenessScore = calculateCompletenessScore(review);
        summaryBuilder.completenessScore(completenessScore);

        return summaryBuilder.build();
    }

    /**
     * 计算合同完整性评分
     */
    private int calculateCompletenessScore(ContractReview review) {
        int score = 100;
        
        // 基于风险数量调整评分
        if (review.getTotalRisks() != null) {
            score -= Math.min(review.getTotalRisks() * 5, 50); // 最多扣50分
        }
        
        // 基于风险等级调整评分
        if (review.getRiskLevel() == ContractReview.RiskLevel.HIGH) {
            score -= 20;
        } else if (review.getRiskLevel() == ContractReview.RiskLevel.MEDIUM) {
            score -= 10;
        }
        
        return Math.max(score, 20); // 最低20分
    }

    /**
     * 获取状态显示名称（用于PDF报告）
     */
    private String getStatusDisplayName(ContractReview.ReviewStatus status) {
        return switch (status) {
            case PENDING -> "待处理";
            case PROCESSING -> "处理中";
            case COMPLETED -> "已完成";
            case FAILED -> "处理失败";
        };
    }

    /**
     * 生成报告文件名
     */
    private String generateReportFileName(ContractReview review) {
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
        String originalFilename = review.getOriginalFilename();
        
        // 移除原文件的扩展名
        if (originalFilename != null && originalFilename.contains(".")) {
            originalFilename = originalFilename.substring(0, originalFilename.lastIndexOf("."));
        }
        
        // 清理文件名中的特殊字符
        if (originalFilename != null) {
            originalFilename = originalFilename.replaceAll("[\\\\/:*?\"<>|]", "_");
            // 限制长度
            if (originalFilename.length() > 50) {
                originalFilename = originalFilename.substring(0, 50);
            }
        } else {
            originalFilename = "合同审查报告";
        }
        
        return String.format("%s_审查报告_%s.pdf", originalFilename, timestamp);
    }
}
