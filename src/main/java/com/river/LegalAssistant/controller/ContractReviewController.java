package com.river.LegalAssistant.controller;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.river.LegalAssistant.dto.ContractAnalysisDto;
import com.river.LegalAssistant.dto.ContractReviewResultDto;
import com.river.LegalAssistant.dto.ContractReviewSummaryDto;
import com.river.LegalAssistant.dto.PageResponseDto;
import com.river.LegalAssistant.dto.ReviewStatisticsDto;
import com.river.LegalAssistant.dto.RiskLevelStatisticsDto;
import com.river.LegalAssistant.dto.UploadResponseDto;
import com.river.LegalAssistant.dto.UserReviewStatisticsDto;
import com.river.LegalAssistant.entity.ContractReview;
import com.river.LegalAssistant.entity.User;
import com.river.LegalAssistant.exception.ResourceNotFoundException;
import com.river.LegalAssistant.service.ContractReviewService;
import com.river.LegalAssistant.service.DocumentParserService;
import com.river.LegalAssistant.service.FileStorageService;
import com.river.LegalAssistant.service.ReportGenerationService;
import com.river.LegalAssistant.service.UserService;
import com.river.LegalAssistant.util.JwtTokenUtil;
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

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 合同审查控制器
 */
@RestController
@RequestMapping("/contracts")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "合同审查", description = "合同上传、审查、结果查询等接口")
@SecurityRequirement(name = "bearerAuth")
public class ContractReviewController {

    private final ContractReviewService contractReviewService;
    private final UserService userService;
    private final DocumentParserService documentParserService;
    private final ReportGenerationService reportGenerationService;
    private final JwtTokenUtil jwtTokenUtil;
    private final FileStorageService fileStorageService;
    private final ObjectMapper objectMapper;

    /**
     * 上传合同文件并创建审查任务（异步处理）
     * 
     * 优化说明：
     * - 快速响应：文件保存后立即返回 202 Accepted，避免同步阻塞
     * - 异步处理：文件解析和向量化操作在后台线程中执行
     * - 状态追踪：客户端可通过 reviewId 查询处理状态
     */
    @PostMapping("/upload")
    @Operation(summary = "上传合同文件", description = "上传合同文件（支持.docx, .pdf, .txt），系统将异步解析文件内容并创建审查任务。文件处理在后台进行，可通过返回的reviewId查询处理状态。")
    @ApiResponses({
        @ApiResponse(responseCode = "202", description = "文件已接收，正在后台处理",
            content = @Content(mediaType = "application/json", schema = @Schema(example = "{\"success\":true,\"message\":\"文件已接收，正在处理中\",\"reviewId\":1,\"status\":\"PENDING\",\"supportedAnalysis\":true}"))),
        @ApiResponse(responseCode = "400", description = "请求无效（如文件为空、文件名为空、文件类型不支持）", content = @Content(schema = @Schema(example = "{\"success\":false,\"message\":\"不支持的文件类型...\"}"))),
        @ApiResponse(responseCode = "401", description = "用户未认证"),
        @ApiResponse(responseCode = "500", description = "服务器内部错误，文件保存失败")
    })
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<com.river.LegalAssistant.dto.ApiResponse<UploadResponseDto>> uploadContract(
            @Parameter(description = "待审查的合同文件。支持的格式：.docx, .pdf, .txt", required = true)
            @RequestParam("file") MultipartFile file,
            Authentication authentication) {
        
        try {
            // 获取当前用户
            User currentUser = userService.findByUsername(authentication.getName())
                    .orElseThrow(() -> new IllegalArgumentException("用户不存在"));

            // 验证文件
            if (file.isEmpty()) {
                throw new IllegalArgumentException("文件不能为空");
            }

            // 验证文件类型
            String fileName = file.getOriginalFilename();
            if (fileName == null || fileName.trim().isEmpty()) {
                throw new IllegalArgumentException("文件名不能为空");
            }

            String fileExtension = fileStorageService.getFileExtension(fileName);
            if (!documentParserService.isFileTypeSupported(fileExtension)) {
                throw new IllegalArgumentException("不支持的文件类型，支持的类型: " + 
                              String.join(", ", documentParserService.getSupportedFileTypes()));
            }

            // 使用FileStorageService保存文件（快速操作）
            FileStorageService.FileStorageResult storageResult = 
                fileStorageService.saveFile(file, "contracts");

            // 快速创建PENDING状态的审查记录（不解析文件内容）
            ContractReview review = contractReviewService.createPendingReview(
                currentUser,
                fileName,
                storageResult.getAbsolutePath(),
                file.getSize(),
                storageResult.getFileHash()
            );

            // 触发异步处理（文件解析和向量化）
            contractReviewService.processUploadedContractAsync(review.getId());

            // 构建响应DTO
            UploadResponseDto responseDto = UploadResponseDto.builder()
                .reviewId(review.getId())
                .status(review.getReviewStatus().name())
                .supportedAnalysis(true)
                .fileHash(storageResult.getFileHash())
                .originalFilename(fileName)
                .fileSize(file.getSize())
                .build();

            // 返回 202 Accepted，表示已接收任务并在后台处理
            return ResponseEntity.accepted()
                    .body(com.river.LegalAssistant.dto.ApiResponse.<UploadResponseDto>success(
                        responseDto, 
                        "文件已接收，正在处理中"));

        } catch (Exception e) {
            log.error("文件上传失败", e);
            throw new RuntimeException("文件上传失败: " + e.getMessage(), e);
        }
    }


    /**
     * 异步合同审查（SSE推送进度）
     */
    @GetMapping(value = "/{reviewId}/analyze-async", produces = "text/event-stream;charset=UTF-8")
    @Operation(summary = "异步合同审查", description = "对已上传的合同启动异步AI审查。服务器将通过Server-Sent Events (SSE)实时推送分析进度和结果。")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "分析任务已启动，开始推送SSE事件流",
            content = @Content(mediaType = "text/event-stream", schema = @Schema(example = "event: progress\ndata: { \"stage\": \"文本提取\", \"progress\": 20, \"message\": \"正在提取合同文本...\" }\n\nevent: result\ndata: { ...审查结果... }\n\nevent: complete\ndata: { \"message\": \"分析完成\" }\n"))),
        @ApiResponse(responseCode = "401", description = "用户未认证"),
        @ApiResponse(responseCode = "404", description = "指定的审查记录不存在"),
        @ApiResponse(responseCode = "500", description = "启动分析任务失败")
    })
    public SseEmitter analyzeContractAsync(
            @Parameter(description = "审查任务的唯一ID", required = true, example = "1")
            @PathVariable Long reviewId,
            HttpServletRequest httpRequest,
            HttpServletResponse httpResponse) {
        
        log.info("开始异步合同分析，审查ID: {}", reviewId);
        
        // 设置SSE响应头，确保中文正确显示
        httpResponse.setContentType("text/event-stream;charset=UTF-8");
        httpResponse.setCharacterEncoding("UTF-8");
        httpResponse.setHeader("Cache-Control", "no-cache");
        httpResponse.setHeader("Connection", "keep-alive");
        httpResponse.setHeader("X-Accel-Buffering", "no"); // 禁用Nginx缓冲
        httpResponse.setHeader("Access-Control-Allow-Origin", "http://localhost:3000");
        httpResponse.setHeader("Access-Control-Allow-Credentials", "true");
        httpResponse.setHeader("Access-Control-Allow-Headers", "Authorization, Content-Type");
        httpResponse.setHeader("Access-Control-Allow-Methods", "GET, POST, OPTIONS");
        
        // 创建SSE发射器，设置超时时间为20分钟（考虑到AI分析可能需要较长时间）
        SseEmitter emitter = new SseEmitter(20 * 60 * 1000L);
        
        // 手动验证JWT Token - 支持Header和查询参数两种方式
        String jwtToken = null;
        String authHeader = httpRequest.getHeader("Authorization");
        
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            jwtToken = authHeader.substring(7);
        } else {
            // 尝试从查询参数获取token（用于EventSource）
            jwtToken = httpRequest.getParameter("token");
        }
        
        if (jwtToken == null || jwtToken.trim().isEmpty()) {
            try {
                String errorJsonData = serializeToJsonWithUtf8(Map.of("error", "未提供有效的认证Token"));
                emitter.send(SseEmitter.event()
                    .name("error")
                    .data(errorJsonData));
                emitter.complete();
            } catch (Exception e) {
                log.error("发送认证错误事件失败", e);
            }
            return emitter;
        }
        try {
            // 验证JWT Token
            String username = jwtTokenUtil.getUsernameFromToken(jwtToken);
            if (username == null) {
                throw new RuntimeException("无效的Token");
            }
            
            // 验证Token签名和有效期
            if (jwtTokenUtil.isTokenExpired(jwtToken)) {
                throw new RuntimeException("Token已过期");
            }
            
            log.info("用户 {} 通过JWT验证，开始处理合同分析，reviewId: {}", username, reviewId);
        } catch (Exception e) {
            log.error("JWT验证失败: {}", e.getMessage());
            try {
                String errorJsonData = serializeToJsonWithUtf8(Map.of("error", "认证失败: " + e.getMessage()));
                emitter.send(SseEmitter.event()
                    .name("error")
                    .data(errorJsonData));
                emitter.complete();
            } catch (Exception sendError) {
                log.error("发送认证错误事件失败", sendError);
            }
            return emitter;
        }
        
        // 设置完成和超时回调
        emitter.onCompletion(() -> log.info("SSE连接完成: reviewId={}", reviewId));
        emitter.onTimeout(() -> {
            log.warn("SSE连接超时，标记任务应停止，但分析将继续在后台进行: reviewId={}", reviewId);
            try {
                // 检查emitter状态，避免在已完成的emitter上发送消息
                emitter.send(SseEmitter.event()
                    .name("timeout")
                    .data(Map.of("message", "连接超时，请刷新页面查看结果", "reviewId", reviewId)));
                emitter.complete();
            } catch (IllegalStateException e) {
                log.warn("SSE连接已关闭，无法发送超时消息: reviewId={}", reviewId);
            } catch (Exception e) {
                log.error("发送超时消息失败", e);
            }
        });
        emitter.onError((ex) -> {
            log.error("SSE连接错误: reviewId={}", reviewId, ex);
            emitter.completeWithError(ex);
        });
        
        try {
            // 记录连接成功（仅控制台日志，不发送到前端）
            log.info("SSE连接已建立: reviewId={}", reviewId);
            
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
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ContractReviewResultDto.class))),
        @ApiResponse(responseCode = "401", description = "用户未认证"),
        @ApiResponse(responseCode = "404", description = "指定的审查记录不存在"),
        @ApiResponse(responseCode = "500", description = "服务器内部错误")
    })
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<com.river.LegalAssistant.dto.ApiResponse<ContractReviewResultDto>> analyzeContract(
            @Parameter(description = "审查任务的唯一ID", required = true, example = "1")
            @PathVariable Long reviewId) {
        try {
            ContractReview review = contractReviewService.analyzeContract(reviewId);
            ContractReviewResultDto resultDto = ContractReviewResultDto.fromEntity(review);
            
            return ResponseEntity.ok(
                com.river.LegalAssistant.dto.ApiResponse.<ContractReviewResultDto>success(resultDto, "合同审查完成")
            );

        } catch (Exception e) {
            log.error("合同审查失败", e);
            return ResponseEntity.internalServerError()
                    .body(com.river.LegalAssistant.dto.ApiResponse.error("合同审查失败: " + e.getMessage(), (ContractReviewResultDto) null));
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
    public ResponseEntity<com.river.LegalAssistant.dto.ApiResponse<PageResponseDto<com.river.LegalAssistant.dto.ContractReviewSummaryDto>>> getMyReviews(
            @Parameter(description = "页码，从0开始", example = "0")
            @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "每页记录数", example = "10")
            @RequestParam(defaultValue = "10") int size,
            Authentication authentication) {
        
        User currentUser = userService.findByUsername(authentication.getName())
                .orElseThrow(() -> new IllegalArgumentException("用户不存在"));

        Pageable pageable = PageRequest.of(page, size);
        Page<ContractReview> reviews = contractReviewService.getReviewsByUser(currentUser.getId(), pageable);
        
        // 转换为DTO
        List<com.river.LegalAssistant.dto.ContractReviewSummaryDto> summaryList = reviews.getContent().stream()
                .map(com.river.LegalAssistant.dto.ContractReviewSummaryDto::fromEntity)
                .toList();
        
        PageResponseDto<com.river.LegalAssistant.dto.ContractReviewSummaryDto> pageResponse = PageResponseDto.<com.river.LegalAssistant.dto.ContractReviewSummaryDto>builder()
                .content(summaryList)
                .totalElements(reviews.getTotalElements())
                .totalPages(reviews.getTotalPages())
                .currentPage(reviews.getNumber())
                .pageSize(reviews.getSize())
                .build();
        
        return ResponseEntity.ok(com.river.LegalAssistant.dto.ApiResponse.success(pageResponse));
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
    public ResponseEntity<com.river.LegalAssistant.dto.ApiResponse<ContractReviewResultDto>> getReviewDetail(
            @Parameter(description = "审查任务的唯一ID", required = true, example = "1")
            @PathVariable Long reviewId) {
        
        try {
            // 委托给Service层处理业务逻辑
            ContractReviewResultDto resultDto = contractReviewService.getReviewDetailsWithSummary(reviewId);
            return ResponseEntity.ok(com.river.LegalAssistant.dto.ApiResponse.<ContractReviewResultDto>success(resultDto));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(404)
                    .body(com.river.LegalAssistant.dto.ApiResponse.error(e.getMessage(), (ContractReviewResultDto) null));
        }
    }

    /**
     * 获取审查详情（简化版本）
     */
    @GetMapping("/{reviewId}/summary")
    @Operation(summary = "获取审查摘要", description = "获取指定合同审查的核心信息摘要，用于在列表等场景快速展示。")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "获取成功", 
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = com.river.LegalAssistant.dto.ContractReviewSummaryDto.class))),
        @ApiResponse(responseCode = "401", description = "用户未认证"),
        @ApiResponse(responseCode = "404", description = "指定的审查记录不存在")
    })
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<com.river.LegalAssistant.dto.ApiResponse<com.river.LegalAssistant.dto.ContractReviewSummaryDto>> getReviewSummary(
            @Parameter(description = "审查任务的唯一ID", required = true, example = "1")
            @PathVariable Long reviewId) {
        
        Optional<ContractReview> reviewOpt = contractReviewService.findById(reviewId);
        
        if (reviewOpt.isEmpty()) {
            return ResponseEntity.status(404)
                    .body(com.river.LegalAssistant.dto.ApiResponse.error("审查记录不存在", (com.river.LegalAssistant.dto.ContractReviewSummaryDto) null));
        }
        
        ContractReview review = reviewOpt.get();
        com.river.LegalAssistant.dto.ContractReviewSummaryDto summaryDto = 
                com.river.LegalAssistant.dto.ContractReviewSummaryDto.fromEntity(review);
        
        return ResponseEntity.ok(com.river.LegalAssistant.dto.ApiResponse.<ContractReviewSummaryDto>success(summaryDto));
    }

    /**
     * 删除审查记录
     */
    @DeleteMapping("/{reviewId}")
    @Operation(summary = "删除审查记录", description = "删除指定的合同审查记录。用户只能删除自己的审查记录。")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "删除成功"),
        @ApiResponse(responseCode = "401", description = "用户未认证"),
        @ApiResponse(responseCode = "403", description = "无权删除此审查记录"),
        @ApiResponse(responseCode = "404", description = "审查记录不存在")
    })
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<com.river.LegalAssistant.dto.ApiResponse<Void>> deleteReview(
            @Parameter(description = "审查记录ID", required = true, example = "1")
            @PathVariable Long reviewId,
            Authentication authentication) {
        
        try {
            User currentUser = userService.findByUsername(authentication.getName())
                    .orElseThrow(() -> new IllegalArgumentException("用户不存在"));
            
            // 查找审查记录
            Optional<ContractReview> reviewOpt = contractReviewService.findById(reviewId);
            if (reviewOpt.isEmpty()) {
                return ResponseEntity.status(404)
                        .body(com.river.LegalAssistant.dto.ApiResponse.error("审查记录不存在", (Void) null));
            }
            
            ContractReview review = reviewOpt.get();
            
            // 验证权限：只能删除自己的审查记录
            if (!review.getUser().getId().equals(currentUser.getId())) {
                return ResponseEntity.status(403)
                        .body(com.river.LegalAssistant.dto.ApiResponse.error("您没有权限删除此审查记录", (Void) null));
            }
            
            // 执行删除
            contractReviewService.deleteReview(reviewId);
            
            log.info("用户 {} 删除了审查记录 {}", currentUser.getUsername(), reviewId);
            return ResponseEntity.ok(com.river.LegalAssistant.dto.ApiResponse.<Void>success(null, "删除成功"));
            
        } catch (Exception e) {
            log.error("删除审查记录失败", e);
            return ResponseEntity.internalServerError()
                    .body(com.river.LegalAssistant.dto.ApiResponse.error("删除失败: " + e.getMessage(), (Void) null));
        }
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
    public ResponseEntity<com.river.LegalAssistant.dto.ApiResponse<List<com.river.LegalAssistant.dto.ContractReviewSummaryDto>>> getAllReviews(
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
            
            // 转换为DTO
            List<com.river.LegalAssistant.dto.ContractReviewSummaryDto> summaryList = reviews.stream()
                    .map(com.river.LegalAssistant.dto.ContractReviewSummaryDto::fromEntity)
                    .toList();
            
            return ResponseEntity.ok(com.river.LegalAssistant.dto.ApiResponse.<List<ContractReviewSummaryDto>>success(summaryList));
            
        } catch (Exception e) {
            log.error("查询审查记录失败", e);
            return ResponseEntity.internalServerError()
                    .body(com.river.LegalAssistant.dto.ApiResponse.error("查询失败: " + e.getMessage(), (java.util.List<com.river.LegalAssistant.dto.ContractReviewSummaryDto>) null));
        }
    }

    /**
     * 获取风险等级统计
     */
    @GetMapping("/statistics/risk-levels")
    @Operation(summary = "风险等级统计（管理员）", description = "管理员获取系统中所有已完成审查的合同风险等级分布统计。")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "获取成功", 
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = com.river.LegalAssistant.dto.RiskLevelStatisticsDto.class))),
        @ApiResponse(responseCode = "401", description = "用户未认证"),
        @ApiResponse(responseCode = "403", description = "当前用户无管理员权限")
    })
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<com.river.LegalAssistant.dto.ApiResponse<com.river.LegalAssistant.dto.RiskLevelStatisticsDto>> getRiskLevelStatistics() {
        Map<String, Long> statistics = contractReviewService.getRiskLevelStatistics();
        com.river.LegalAssistant.dto.RiskLevelStatisticsDto statisticsDto = 
                com.river.LegalAssistant.dto.RiskLevelStatisticsDto.fromStatisticsMap(statistics);
        
        return ResponseEntity.ok(com.river.LegalAssistant.dto.ApiResponse.<RiskLevelStatisticsDto>success(statisticsDto));
    }

    /**
     * 获取用户审查统计
     */
    @GetMapping("/statistics/user/{userId}")
    @Operation(summary = "用户审查统计（管理员）", description = "管理员获取指定用户的合同审查总数统计。")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "获取成功", 
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = com.river.LegalAssistant.dto.UserReviewStatisticsDto.class))),
        @ApiResponse(responseCode = "401", description = "用户未认证"),
        @ApiResponse(responseCode = "403", description = "当前用户无管理员权限")
    })
    @PreAuthorize("hasRole('ADMIN') or authentication.principal.id == #userId")
    public ResponseEntity<com.river.LegalAssistant.dto.ApiResponse<com.river.LegalAssistant.dto.UserReviewStatisticsDto>> getUserReviewStatistics(
            @Parameter(description = "要查询的用户ID", required = true, example = "1") 
            @PathVariable Long userId) {
        try {
            long reviewCount = contractReviewService.countReviewsByUser(userId);
            
            com.river.LegalAssistant.dto.UserReviewStatisticsDto statisticsDto = 
                    com.river.LegalAssistant.dto.UserReviewStatisticsDto.builder()
                            .userId(userId)
                            .totalReviews(reviewCount)
                            .build();
            
            return ResponseEntity.ok(com.river.LegalAssistant.dto.ApiResponse.<UserReviewStatisticsDto>success(statisticsDto));
        } catch (Exception e) {
            log.error("获取用户审查统计失败", e);
            return ResponseEntity.internalServerError()
                    .body(com.river.LegalAssistant.dto.ApiResponse.error("获取统计失败: " + e.getMessage(), (UserReviewStatisticsDto) null));
        }
    }

    /**
     * 获取我的审查统计
     */
    @GetMapping("/my-statistics")
    @Operation(summary = "我的审查统计", description = "获取当前认证用户的个人合同审查总数统计。")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "获取成功", 
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = com.river.LegalAssistant.dto.UserReviewStatisticsDto.class))),
        @ApiResponse(responseCode = "401", description = "用户未认证")
    })
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<com.river.LegalAssistant.dto.ApiResponse<com.river.LegalAssistant.dto.UserReviewStatisticsDto>> getMyReviewStatistics(
            Authentication authentication) {
        try {
            User currentUser = userService.findByUsername(authentication.getName())
                    .orElseThrow(() -> new IllegalArgumentException("用户不存在"));
            
            long reviewCount = contractReviewService.countReviewsByUser(currentUser.getId());
            
            com.river.LegalAssistant.dto.UserReviewStatisticsDto statisticsDto = 
                    com.river.LegalAssistant.dto.UserReviewStatisticsDto.builder()
                            .userId(currentUser.getId())
                            .username(currentUser.getUsername())
                            .totalReviews(reviewCount)
                            .build();
            
            return ResponseEntity.ok(com.river.LegalAssistant.dto.ApiResponse.<UserReviewStatisticsDto>success(statisticsDto));
        } catch (Exception e) {
            log.error("获取我的审查统计失败", e);
            return ResponseEntity.internalServerError()
                    .body(com.river.LegalAssistant.dto.ApiResponse.error("获取统计失败: " + e.getMessage(), (UserReviewStatisticsDto) null));
        }
    }

    /**
     * 下载合同审查PDF报告
     */
    @GetMapping("/{reviewId}/report")
    @Operation(summary = "下载PDF审查报告", description = "下载指定审查任务的PDF格式审查报告。如果报告已存在则直接返回，不存在则自动生成。")
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
    public ResponseEntity<?> generateReport(
            @Parameter(description = "审查任务的唯一ID", required = true, example = "1")
            @PathVariable Long reviewId,
            Authentication authentication) {
        
        log.info("用户 {} 请求下载PDF报告，审查ID: {}", authentication.getName(), reviewId);
        
        try {
            // 在独立只读事务中获取审查记录（避免连接泄漏）
            ContractReview review = contractReviewService.getReviewForReportGeneration(reviewId);
            
            if (review == null) {
                return ResponseEntity.notFound().build();
            }
            
            // 权限检查：只有审查记录的所有者或管理员可以生成报告
            User currentUser = userService.findByUsername(authentication.getName())
                    .orElseThrow(() -> new IllegalArgumentException("用户不存在"));
            
            if (!review.getUser().getId().equals(currentUser.getId()) && 
                !currentUser.getRole().name().equals("ADMIN")) {
                return ResponseEntity.status(403).build();
            }
            
            // 检查审查状态 - 直接返回 ApiResponse 对象，Spring 会自动序列化为 JSON
            if (review.getReviewStatus() != ContractReview.ReviewStatus.COMPLETED) {
                String message = "审查未完成，无法生成报告。当前状态: " + getStatusDisplayName(review.getReviewStatus());
                return ResponseEntity.badRequest()
                        .body(com.river.LegalAssistant.dto.ApiResponse.errorWithCode(message, "REVIEW_NOT_COMPLETED"));
            }
            
            // 【修复】优先从文件系统读取已生成的报告，避免重复生成
            byte[] pdfBytes = null;
            boolean isFromCache = false;
            
            // 检查报告文件是否已存在
            java.nio.file.Path reportPath = java.nio.file.Paths.get("uploads", "reports", reviewId + "_report.pdf");
            if (java.nio.file.Files.exists(reportPath)) {
                try {
                    pdfBytes = java.nio.file.Files.readAllBytes(reportPath);
                    isFromCache = true;
                    log.info("从缓存读取PDF报告，审查ID: {}, 文件大小: {} bytes", reviewId, pdfBytes.length);
                } catch (java.io.IOException e) {
                    log.warn("读取缓存的PDF报告失败，将重新生成: {}", e.getMessage());
                    pdfBytes = null; // 读取失败，触发重新生成
                }
            }
            
            // 如果缓存中没有报告，则生成新报告
            if (pdfBytes == null) {
                log.info("报告文件不存在，开始生成新报告，审查ID: {}", reviewId);
                pdfBytes = reportGenerationService.generateContractReviewReport(review);
                log.info("PDF报告生成成功，审查ID: {}, 文件大小: {} bytes", reviewId, pdfBytes.length);
            }
            
            // 生成文件名（处理中文编码）
            String fileName = generateReportFileName(review);
            String encodedFileName = encodeFileName(fileName);
            
            log.info("PDF报告准备下载 [{}]，审查ID: {}, 文件大小: {} bytes, 文件名: {}", 
                isFromCache ? "缓存" : "新生成", reviewId, pdfBytes.length, fileName);
            
            return ResponseEntity.ok()
                    .header("Content-Type", "application/pdf; charset=UTF-8")
                    .header("Content-Disposition", encodedFileName)
                    .header("Cache-Control", "no-cache")
                    .body(pdfBytes);
                    
        } catch (Exception e) {
            log.error("生成PDF报告失败，审查ID: {}", reviewId, e);
            
            // 根据异常类型提供更具体的错误信息
            String errorMessage;
            String errorCode;
            
            if (e.getCause() instanceof java.util.concurrent.TimeoutException) {
                errorMessage = "报告生成超时，请稍后重试。如果问题持续存在，请联系系统管理员。";
                errorCode = "REPORT_GENERATION_TIMEOUT";
            } else if (e.getMessage() != null && e.getMessage().contains("InterruptedException")) {
                errorMessage = "报告生成被中断，可能是由于网络问题。请稍后重试。";
                errorCode = "REPORT_GENERATION_INTERRUPTED";
            } else if (e.getMessage() != null && e.getMessage().contains("AI服务")) {
                errorMessage = "AI服务暂时不可用，系统已生成基础报告。请稍后重试获取完整分析。";
                errorCode = "AI_SERVICE_UNAVAILABLE";
            } else {
                errorMessage = "PDF报告生成失败，请稍后重试。如果问题持续存在，请联系系统管理员。";
                errorCode = "REPORT_GENERATION_FAILED";
            }
            
            // 直接返回 ApiResponse 对象，Spring 会自动序列化为 JSON
            return ResponseEntity.internalServerError()
                    .body(com.river.LegalAssistant.dto.ApiResponse.errorWithCode(
                        errorMessage, 
                        errorCode));
        }
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
     * 生成报告文件名（改进版，保留更多中文字符）
     */
    private String generateReportFileName(ContractReview review) {
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
        String originalFilename = review.getOriginalFilename();
        
        log.debug("生成报告文件名，原始文件名: {}", originalFilename);
        
        // 移除原文件的扩展名
        if (originalFilename != null && originalFilename.contains(".")) {
            originalFilename = originalFilename.substring(0, originalFilename.lastIndexOf("."));
        }
        
        // 更智能的文件名清理，最大程度保留有意义的字符
        String cleanedName = "合同分析报告";  // 默认值
        
        if (originalFilename != null && !originalFilename.trim().isEmpty()) {
            // 只移除Windows/Linux文件系统真正不允许的字符，保留中文字符和常见符号
            String tempName = originalFilename
                .replaceAll("[\\\\/:*?\"<>|]", "")  // 移除真正的非法字符
                .replaceAll("\\s+", " ")  // 合并多个空白字符为单个空格
                .trim();  // 移除首尾空白
            
            // 如果清理后的文件名有效，使用它
            if (!tempName.isEmpty() && tempName.length() >= 2) {
                cleanedName = tempName;
            } else {
                // 尝试提取有意义的部分
                String extractedName = extractMeaningfulName(review.getOriginalFilename());
                if (extractedName != null && !extractedName.trim().isEmpty()) {
                    cleanedName = extractedName;
                }
            }
            
            // 限制长度，保留核心信息
            if (cleanedName.length() > 30) {
                // 智能截取，尽量保留有意义的部分
                if (cleanedName.contains("合同")) {
                    // 如果包含"合同"，尽量保留包含该词的部分
                    int contractIndex = cleanedName.indexOf("合同");
                    int startIndex = Math.max(0, contractIndex - 10);
                    int endIndex = Math.min(cleanedName.length(), contractIndex + 20);
                    cleanedName = cleanedName.substring(startIndex, endIndex);
                } else {
                    cleanedName = cleanedName.substring(0, 30);
                }
            }
            
            log.debug("文件名清理后: {}", cleanedName);
        }
        
        // 简化文件名格式：原文件名 + "分析报告"，不再添加时间戳（因为时间戳会在HTTP响应头中）
        String finalFileName = String.format("%s分析报告.pdf", cleanedName);
        log.info("生成最终报告文件名: {}", finalFileName);
        
        return finalFileName;
    }
    
    /**
     * 从文件名中提取有意义的部分（改进版，保留更多中文字符）
     */
    private String extractMeaningfulName(String filename) {
        if (filename == null || filename.trim().isEmpty()) {
            return null;
        }
        
        // 移除文件扩展名
        String nameWithoutExt = filename.contains(".") ? 
            filename.substring(0, filename.lastIndexOf(".")) : filename;
        
        // 只移除文件系统真正不允许的字符，保留中文和常用符号
        String cleaned = nameWithoutExt
                .replaceAll("[\\\\/:*?\"<>|]", "")  // 移除文件系统非法字符
                .replaceAll("\\s+", "")  // 移除所有空白字符
                .trim();
        
        // 如果清理后有效，直接返回（限制长度）
        if (!cleaned.isEmpty() && cleaned.length() >= 2) {
            // 智能截取，优先保留包含"合同"等关键词的部分
            if (cleaned.length() > 25) {
                String[] keywords = {"合同", "协议", "租赁", "劳动", "服务", "采购", "销售"};
                for (String keyword : keywords) {
                    if (cleaned.contains(keyword)) {
                        int index = cleaned.indexOf(keyword);
                        int start = Math.max(0, index - 5);
                        int end = Math.min(cleaned.length(), index + 20);
                        return cleaned.substring(start, end);
                    }
                }
                // 如果没有关键词，简单截取前25个字符
                return cleaned.substring(0, 25);
            }
            return cleaned;
        }
        
        return null;
    }
    
    /**
     * 对文件名进行编码，支持中文文件名
     * 使用RFC 6266标准，同时提供filename和filename*两种格式以确保最大兼容性
     * 
     * @param fileName 原始文件名
     * @return 编码后的Content-Disposition头部值
     */
    private String encodeFileName(String fileName) {
        try {
            // 使用UTF-8进行URL编码
            String encodedFileName = java.net.URLEncoder.encode(fileName, "UTF-8")
                .replace("+", "%20"); // 空格用%20而不是+
            
            // 使用RFC 6266标准，同时提供两种格式：
            // 1. filename="..." - 用于不支持RFC 6266的旧浏览器（使用ASCII安全的回退名称）
            // 2. filename*=UTF-8''... - 用于支持UTF-8的现代浏览器
            // 现代浏览器会优先使用filename*参数
            
            // 创建一个ASCII安全的回退文件名（只保留基本字符）
            String asciiFallback = fileName.replaceAll("[^a-zA-Z0-9._-]", "_");
            
            log.debug("原始文件名: {}, UTF-8编码后: {}, ASCII回退: {}", 
                fileName, encodedFileName, asciiFallback);
            
            // 返回双重编码格式，确保最大兼容性
            return String.format("attachment; filename=\"%s\"; filename*=UTF-8''%s", 
                asciiFallback, encodedFileName);
            
        } catch (java.io.UnsupportedEncodingException e) {
            log.error("文件名编码失败，使用简化文件名", e);
            // 如果编码失败，使用简化的文件名
            String simpleFileName = "审查报告.pdf";
            return String.format("attachment; filename=\"%s\"", simpleFileName);
        }
    }
    
    /**
     * 手动序列化对象为JSON字符串，确保UTF-8编码
     */
    private String serializeToJsonWithUtf8(Object obj) {
        try {
            // 创建临时ObjectMapper，确保禁用ASCII转义，启用UTF-8支持
            ObjectMapper tempMapper = objectMapper.copy();
            tempMapper.getFactory().configure(JsonGenerator.Feature.ESCAPE_NON_ASCII, false);
            // 确保使用UTF-8编码
            tempMapper.configure(com.fasterxml.jackson.core.JsonGenerator.Feature.WRITE_BIGDECIMAL_AS_PLAIN, true);
            String jsonString = tempMapper.writeValueAsString(obj);
            log.debug("JSON序列化完成，内容长度: {}, 前50字符: {}", 
                jsonString.length(), 
                jsonString.length() > 50 ? jsonString.substring(0, 50) + "..." : jsonString);
            return jsonString;
        } catch (Exception e) {
            log.error("JSON序列化失败", e);
            return "{\"error\": \"序列化失败\"}";
        }
    }
}

