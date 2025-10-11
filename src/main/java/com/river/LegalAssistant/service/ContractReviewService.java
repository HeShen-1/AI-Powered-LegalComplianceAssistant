package com.river.LegalAssistant.service;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.river.LegalAssistant.dto.ContractAnalysisProgressDto;
import com.river.LegalAssistant.dto.ContractReviewResultDto;
import com.river.LegalAssistant.dto.ContractRiskAnalysisResult;
import com.river.LegalAssistant.entity.ContractReview;
import com.river.LegalAssistant.entity.RiskClause;
import com.river.LegalAssistant.entity.User;
import com.river.LegalAssistant.repository.ContractReviewRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import org.springframework.aop.framework.AopContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;

import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

/**
 * 合同审查服务层
 */
@Service
@Slf4j
public class ContractReviewService {

    private final ContractReviewRepository contractReviewRepository;
    private final AiService aiService;
    private final DocumentParserService documentParserService;
    private final EtlService etlService;
    private final ObjectMapper objectMapper;
    private final AgentService agentService;
    private final ReportGenerationService reportGenerationService;
    
    // 用于显式detach实体
    @Autowired
    private jakarta.persistence.EntityManager entityManager;
    
    // 自注入，用于调用@Transactional方法
    @Lazy
    @Autowired
    private ContractReviewService self;

    public ContractReviewService(ContractReviewRepository contractReviewRepository,
                                 AiService aiService,
                                 DocumentParserService documentParserService,
                                 EtlService etlService,
                                 ObjectMapper objectMapper,
                                 AgentService agentService,
                                 ReportGenerationService reportGenerationService) {
        this.contractReviewRepository = contractReviewRepository;
        this.aiService = aiService;
        this.documentParserService = documentParserService;
        this.etlService = etlService;
        this.objectMapper = objectMapper;
        this.agentService = agentService;
        this.reportGenerationService = reportGenerationService;
    }

    /**
     * 快速创建待处理的合同审查记录（不解析文件内容）
     * 用于文件上传接口快速响应
     * 
     * @param user 上传用户
     * @param originalFilename 原始文件名
     * @param filePath 文件保存路径
     * @param fileSize 文件大小
     * @param fileHash 文件哈希
     * @return 状态为PENDING的审查记录
     */
    @Transactional
    public ContractReview createPendingReview(User user, String originalFilename, 
                                             String filePath, Long fileSize, String fileHash) {
        log.info("创建待处理审查记录，用户: {}, 文件: {}", user.getUsername(), originalFilename);
        
        // 检查是否已存在相同哈希的文件
        Optional<ContractReview> existingReview = contractReviewRepository.findByFileHash(fileHash);
        if (existingReview.isPresent()) {
            log.warn("文件哈希 {} 已存在，返回现有审查记录", fileHash);
            return existingReview.get();
        }

        ContractReview review = new ContractReview();
        review.setUser(user);
        review.setOriginalFilename(originalFilename);
        review.setFilePath(filePath);
        review.setFileSize(fileSize);
        review.setFileHash(fileHash);
        review.setReviewStatus(ContractReview.ReviewStatus.PENDING);

        ContractReview savedReview = contractReviewRepository.save(review);
        log.info("待处理审查记录已创建 - ID: {}, 文件: {}", savedReview.getId(), originalFilename);

        return savedReview;
    }

    /**
     * 异步处理上传的合同文件（解析内容并向量化）
     * 
     * @param reviewId 审查记录ID
     */
    @Async("generalTaskExecutor")
    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.REQUIRES_NEW)
    public void processUploadedContractAsync(Long reviewId) {
        log.info("开始异步处理合同文件，审查ID: {}", reviewId);
        
        // 添加线程信息用于调试
        log.info("异步处理线程: {}", Thread.currentThread().getName());
        
        try {
            // 加载审查记录
            ContractReview review = contractReviewRepository.findById(reviewId)
                    .orElseThrow(() -> new IllegalArgumentException("审查记录不存在: " + reviewId));
            
            // 更新状态为处理中
            review.setReviewStatus(ContractReview.ReviewStatus.PROCESSING);
            contractReviewRepository.save(review);
            
            // 从文件路径读取并解析文件内容
            log.info("开始解析文件: {}, 路径: {}", review.getOriginalFilename(), review.getFilePath());
            String contentText;
            try (InputStream fileInputStream = new java.io.FileInputStream(review.getFilePath())) {
                contentText = documentParserService.parseDocument(
                    fileInputStream, 
                    review.getOriginalFilename(), 
                    review.getFileSize()
                );
                log.info("文件解析成功: {}, 内容长度: {}", review.getOriginalFilename(), 
                    contentText != null ? contentText.length() : 0);
            } catch (DocumentParserService.DocumentParsingException e) {
                log.error("文档解析失败，审查ID: {}", reviewId, e);
                review.setReviewStatus(ContractReview.ReviewStatus.FAILED);
                review.setReviewResult(Map.of(
                    "error", "文档解析失败: " + e.getMessage(),
                    "timestamp", LocalDateTime.now().toString()
                ));
                contractReviewRepository.save(review);
                return;
            } catch (IOException e) {
                log.error("读取文件失败，审查ID: {}", reviewId, e);
                review.setReviewStatus(ContractReview.ReviewStatus.FAILED);
                review.setReviewResult(Map.of(
                    "error", "文件读取失败: " + e.getMessage(),
                    "timestamp", LocalDateTime.now().toString()
                ));
                contractReviewRepository.save(review);
                return;
            }
            
            // 清理文本并保存
            String sanitizedContent = (contentText != null) ? contentText.replace("\0", "") : "";
            review.setContentText(sanitizedContent);
            
            // 保存文本内容到数据库
            contractReviewRepository.save(review);
            log.info("文档解析完成，文本内容已保存: reviewId={}, 文本长度: {}", reviewId, sanitizedContent.length());
            
            // 将文档内容添加到AI知识库（向量存储）
            try {
                Map<String, Object> metadata = Map.of(
                    "review_id", review.getId().toString(),
                    "original_filename", review.getOriginalFilename(),
                    "user_id", review.getUser().getId().toString(),
                    "source_type", "contract_review"
                );
                
                EtlService.EtlProcessResult result = etlService.processTextContent(sanitizedContent, metadata);
                
                if (result.success()) {
                    log.info("已成功将合同内容添加到AI知识库，审查ID: {}, 块数: {}", 
                            review.getId(), result.chunkCount());
                } else {
                    log.error("AI知识库处理失败，审查ID: {}, 错误: {}", 
                            review.getId(), result.message());
                }
            } catch (Exception e) {
                log.error("将合同内容添加到AI知识库失败，审查ID: {}", review.getId(), e);
                // AI知识库失败不影响主流程
            }
            
            // 更新状态为PENDING（等待用户触发分析）
            review.setReviewStatus(ContractReview.ReviewStatus.PENDING);
            contractReviewRepository.save(review);
            
            log.info("合同文件处理完成，审查ID: {}", reviewId);
            
        } catch (Exception e) {
            log.error("异步处理合同文件失败，审查ID: {}, 异常类型: {}, 错误信息: {}", 
                reviewId, e.getClass().getSimpleName(), e.getMessage(), e);
            // 尝试更新失败状态
            try {
                ContractReview review = contractReviewRepository.findById(reviewId).orElse(null);
                if (review != null) {
                    review.setReviewStatus(ContractReview.ReviewStatus.FAILED);
                    review.setReviewResult(Map.of(
                        "error", "处理失败: " + e.getMessage(),
                        "exception_type", e.getClass().getSimpleName(),
                        "timestamp", LocalDateTime.now().toString()
                    ));
                    contractReviewRepository.save(review);
                    log.info("已更新失败状态到数据库: reviewId={}", reviewId);
                }
            } catch (Exception ex) {
                log.error("更新失败状态时出错", ex);
            }
        }
        
        log.info("异步处理合同文件方法结束，审查ID: {}", reviewId);
    }

    /**
     * 创建合同审查记录 - 从文本内容创建
     */
    @Transactional
    public ContractReview createContractReview(User user, String originalFilename, 
                                             String filePath, Long fileSize, String fileHash, 
                                             String contentText) {
        log.info("创建合同审查记录，用户: {}, 文件: {}", user.getUsername(), originalFilename);
        
        // 检查是否已存在相同哈希的文件
        Optional<ContractReview> existingReview = contractReviewRepository.findByFileHash(fileHash);
        if (existingReview.isPresent()) {
            log.warn("文件哈希 {} 已存在，跳过重复审查", fileHash);
            return existingReview.get();
        }

        ContractReview review = new ContractReview();
        review.setUser(user);
        review.setOriginalFilename(originalFilename);
        review.setFilePath(filePath);
        review.setFileSize(fileSize);
        review.setFileHash(fileHash);
        
        // 清理文本中的空字符 (0x00)，防止数据库报错
        String sanitizedContent = (contentText != null) ? contentText.replace("\0", "") : "";
        review.setContentText(sanitizedContent);
        
        review.setReviewStatus(ContractReview.ReviewStatus.PENDING);

        ContractReview savedReview = contractReviewRepository.save(review);

        // 调试日志：确认审查记录保存情况
        log.info("合同审查记录已保存 - ID: {}, 原始文件名: {}, 文件路径: {}", 
                savedReview.getId(), savedReview.getOriginalFilename(), savedReview.getFilePath());

        // 将文档内容异步添加到AI知识库（向量存储）- 使用优化的EtlService
        try {
            Map<String, Object> metadata = Map.of(
                "review_id", savedReview.getId().toString(),
                "original_filename", savedReview.getOriginalFilename(),
                "user_id", savedReview.getUser().getId().toString(),
                "source_type", "contract_review"
            );
            
            // 使用EtlService进行文档处理
            EtlService.EtlProcessResult result = etlService.processTextContent(sanitizedContent, metadata);
            
            if (result.success()) {
                log.info("已成功使用EtlService将合同内容添加到AI知识库，审查ID: {}, 块数: {}", 
                        savedReview.getId(), result.chunkCount());
            } else {
                log.error("EtlService处理合同内容失败，审查ID: {}, 错误: {}", 
                        savedReview.getId(), result.message());
            }
        } catch (Exception e) {
            log.error("将合同内容添加到AI知识库失败，审查ID: {}", savedReview.getId(), e);
            // 注意：这里可以选择是否因为AI侧的失败而回滚事务，当前策略为不影响主业务
        }

        return savedReview;
    }

    /**
     * 加载完整的审查记录（包含所有关联数据）
     * 用于避免懒加载异常，使用独立只读事务
     */
    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.REQUIRES_NEW, readOnly = true)
    public ContractReview loadCompleteReview(Long reviewId) {
        return contractReviewRepository.findByIdWithRiskClauses(reviewId)
                .orElseThrow(() -> new IllegalArgumentException("审查记录不存在: " + reviewId));
    }

    /**
     * 从数据库刷新审查记录（使用新事务确保读取最新数据）
     * 用于解决事务隔离导致的数据不可见问题
     */
    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.REQUIRES_NEW, readOnly = true)
    public ContractReview refreshReviewFromDatabase(Long reviewId) {
        log.info("刷新审查记录（新事务）: reviewId={}", reviewId);
        ContractReview review = contractReviewRepository.findById(reviewId)
                .orElseThrow(() -> new IllegalArgumentException("审查记录不存在: " + reviewId));
        log.info("刷新完成，文本内容长度: {}", review.getContentText() != null ? review.getContentText().length() : 0);
        return review;
    }

    /**
     * 智能等待文件解析完成
     * 使用独立事务避免事务隔离问题
     */
    private String waitForFileParsingCompletion(Long reviewId, SseEmitter emitter, boolean[] shouldStop) {
        int maxWaitSeconds = 120; // 最多等待2分钟
        int waitedSeconds = 0;
        long startTime = System.currentTimeMillis();
        
        // 发送初始等待消息
        sendProgress(emitter, ContractAnalysisProgressDto.parsing(reviewId, "正在等待文件解析完成..."));
        
        String contractText = null;
        while (waitedSeconds < maxWaitSeconds && !shouldStop[0]) {
            try {
                Thread.sleep(1000); // 等待1秒
                waitedSeconds++;
                
                // 使用新事务刷新数据（通过自注入调用确保事务代理生效）
                ContractReview refreshedReview = self.refreshReviewFromDatabase(reviewId);
                contractText = refreshedReview.getContentText();
                
                // 如果找到了文本内容，立即返回
                if (contractText != null && !contractText.trim().isEmpty()) {
                    long elapsedTime = (System.currentTimeMillis() - startTime) / 1000;
                    log.info("文件解析完成，等待时间: {}秒, reviewId={}", elapsedTime, reviewId);
                    if (elapsedTime > 5) { // 只有等待时间超过5秒才显示耗时信息
                        sendProgress(emitter, ContractAnalysisProgressDto.parsing(reviewId, 
                            String.format("文件解析完成（耗时%d秒），开始AI分析...", elapsedTime)));
                    }
                    return contractText;
                }
                
                // 每30秒输出一次等待日志
                if (waitedSeconds % 30 == 0) {
                    long elapsedTime = (System.currentTimeMillis() - startTime) / 1000;
                    log.info("仍在等待文件解析完成: reviewId={}, 已等待{}秒", reviewId, elapsedTime);
                    sendProgress(emitter, ContractAnalysisProgressDto.parsing(reviewId, 
                        String.format("正在等待文件解析完成... (已等待 %d 秒)", elapsedTime)));
                }
                
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log.info("等待文件解析被中断: reviewId={}", reviewId);
                break;
            }
        }
        
        return contractText; // 可能为null，表示超时
    }

    /**
     * 异步执行合同风险分析，并通过SSE推送进度（重构版）
     * 该方法遵循前端定义的四步流程，分阶段执行并实时反馈。
     */
    @Async("generalTaskExecutor")
    public CompletableFuture<ContractReview> analyzeContractAsync(Long reviewId, SseEmitter emitter) {
        log.info("开始异步分析合同（四步流程版），审查ID: {}", reviewId);

        // 步骤0: 初始化和状态检查
        ContractReview review = self.refreshReviewFromDatabase(reviewId);
        if (!isAnalysisPreconditionMet(review, emitter)) {
            return CompletableFuture.completedFuture(review);
        }

        final boolean[] shouldStop = {false};
        setupSseLifecycle(emitter, reviewId, shouldStop);

        try {
            // 更新状态为处理中
            review = self.updateReviewStatus(reviewId, ContractReview.ReviewStatus.PROCESSING);

            // ====================================================================
            // 步骤 1: 文档解析 (Content Parsing & Validation)
            // ====================================================================
            sendProgress(emitter, ContractAnalysisProgressDto.of(reviewId, 0, "文档解析", "正在提取并验证合同文本..."));
            String contractText = review.getContentText();
            if (contractText == null || contractText.trim().isEmpty()) {
                contractText = waitForFileParsingCompletion(reviewId, emitter, shouldStop);
            }
            if (contractText == null || contractText.trim().isEmpty()) {
                throw new IllegalStateException("合同文本内容为空或解析超时");
            }
            log.info("步骤1完成：文档解析成功, reviewId={}", reviewId);
            if (shouldStop[0]) throw new InterruptedException("任务中断");

            // ====================================================================
            // 步骤 2: 风险识别 (Risk Identification)
            // ====================================================================
            sendProgress(emitter, ContractAnalysisProgressDto.of(reviewId, 1, "风险识别", "AI正在识别合同中的潜在法律风险..."));
            // 假设aiService有专门的风险识别方法
            ContractRiskAnalysisResult riskResult = aiService.analyzeContractRiskStructured(contractText);
            log.info("步骤2完成：风险识别成功, reviewId={}", reviewId);
            if (shouldStop[0]) throw new InterruptedException("任务中断");

            // ====================================================================
            // 步骤 3: 条款分析 (Clause Analysis)
            // ====================================================================
            sendProgress(emitter, ContractAnalysisProgressDto.of(reviewId, 2, "条款分析", "AI正在分析合同中的关键条款..."));
            // 调用AgentService的关键条款分析方法
            String keyClausesAnalysisJson = null;
            Map<String, Object> detailedAnalysis = new HashMap<>();
            
            try {
                // 注入AgentService并调用关键条款分析
                keyClausesAnalysisJson = agentService.analyzeKeyClauses(contractText);
                
                // 解析JSON响应（增强的错误处理）
                if (keyClausesAnalysisJson != null && !keyClausesAnalysisJson.trim().isEmpty()) {
                    com.fasterxml.jackson.databind.ObjectMapper jsonMapper = new com.fasterxml.jackson.databind.ObjectMapper();
                    
                    try {
                        com.fasterxml.jackson.databind.JsonNode clausesNode = jsonMapper.readTree(keyClausesAnalysisJson);
                        
                        // 检查是否包含error字段
                        if (clausesNode.has("error")) {
                            String errorMsg = clausesNode.get("error").asText();
                            log.warn("关键条款分析返回错误: {}", errorMsg);
                            detailedAnalysis.put("key_clauses", riskResult.getRiskClauses());
                            detailedAnalysis.put("keyClauses", new java.util.ArrayList<>());
                            detailedAnalysis.put("completenessScore", 0);
                            detailedAnalysis.put("error", errorMsg);
                        } else {
                            // 正常解析关键条款
                            if (clausesNode.has("keyClauses")) {
                                detailedAnalysis.put("keyClauses", clausesNode.get("keyClauses"));
                                detailedAnalysis.put("key_clauses", clausesNode.get("keyClauses")); // 兼容旧字段名
                                
                                int clauseCount = clausesNode.get("keyClauses").size();
                                log.info("关键条款分析成功，识别出 {} 个关键条款", clauseCount);
                            } else {
                                log.warn("JSON响应中未找到keyClauses字段");
                                detailedAnalysis.put("keyClauses", new java.util.ArrayList<>());
                                detailedAnalysis.put("key_clauses", riskResult.getRiskClauses());
                            }
                            
                            // 提取完整性评分
                            if (clausesNode.has("completenessScore")) {
                                detailedAnalysis.put("completenessScore", clausesNode.get("completenessScore").asInt());
                            } else {
                                detailedAnalysis.put("completenessScore", 0);
                            }
                            
                            // 提取整体评价
                            if (clausesNode.has("overallAssessment")) {
                                detailedAnalysis.put("overallAssessment", clausesNode.get("overallAssessment").asText());
                            }
                        }
                    } catch (com.fasterxml.jackson.core.JsonParseException jpe) {
                        // JSON解析失败的详细日志
                        log.error("JSON解析失败，响应不是有效的JSON格式。响应内容前200字符: {}", 
                            keyClausesAnalysisJson.substring(0, Math.min(200, keyClausesAnalysisJson.length())), jpe);
                        detailedAnalysis.put("key_clauses", riskResult.getRiskClauses());
                        detailedAnalysis.put("keyClauses", new java.util.ArrayList<>());
                        detailedAnalysis.put("completenessScore", 0);
                        detailedAnalysis.put("error", "AI返回格式错误，已使用风险条款作为后备");
                    }
                } else {
                    // 空响应处理
                    log.warn("关键条款分析返回空响应，使用风险条款作为后备数据");
                    detailedAnalysis.put("key_clauses", riskResult.getRiskClauses());
                    detailedAnalysis.put("keyClauses", new java.util.ArrayList<>());
                    detailedAnalysis.put("completenessScore", 0);
                    detailedAnalysis.put("error", "AI返回空响应");
                }
            } catch (Exception e) {
                log.error("关键条款分析失败，使用风险条款作为后备数据", e);
                detailedAnalysis.put("key_clauses", riskResult.getRiskClauses());
                detailedAnalysis.put("keyClauses", new java.util.ArrayList<>());
                detailedAnalysis.put("completenessScore", 0);
                detailedAnalysis.put("error", "关键条款分析异常: " + e.getMessage());
            }
            
            log.info("步骤3完成：条款分析成功, reviewId={}", reviewId);
            if (shouldStop[0]) throw new InterruptedException("任务中断");

            // ====================================================================
            // 步骤 4: 保存分析结果
            // ====================================================================
            sendProgress(emitter, ContractAnalysisProgressDto.of(reviewId, 3, "保存分析结果", "正在保存风险分析结果..."));
            review = self.saveAnalysisResultWithTransaction(reviewId, riskResult, detailedAnalysis);
            log.info("步骤4完成：分析结果保存成功, reviewId={}", reviewId);

            // ====================================================================
            // 步骤 5: 生成PDF报告
            // ====================================================================
            sendProgress(emitter, ContractAnalysisProgressDto.of(reviewId, 4, "生成报告", "正在生成PDF审查报告..."));
            try {
                ContractReview fullReview = self.loadCompleteReview(reviewId);
                byte[] pdfBytes = reportGenerationService.generateContractReviewReport(fullReview);
                
                // 保存PDF报告到文件系统
                self.savePdfReportWithTransaction(reviewId, pdfBytes);
                log.info("步骤5完成：PDF报告生成并保存成功, reviewId={}, 文件大小: {} bytes", reviewId, pdfBytes.length);
            } catch (Exception e) {
                log.error("PDF报告生成失败，但不影响审查结果, reviewId={}", reviewId, e);
                // 报告生成失败不影响审查结果，继续流程
            }

            // ====================================================================
            // 发送最终结果
            // ====================================================================
            ContractReview fullReview = self.loadCompleteReview(reviewId);
            ContractReviewResultDto resultDto = ContractReviewResultDto.fromEntity(fullReview);
            sendSseEvent(emitter, "result", Map.of("result", resultDto));
            sendSseEvent(emitter, "complete", Map.of("message", "合同审查已完成"));

            log.info("合同异步分析流程全部完成, reviewId={}", reviewId);
            return CompletableFuture.completedFuture(fullReview);

        } catch (InterruptedException e) {
            log.warn("分析任务被中断: reviewId={}", reviewId);
            self.updateReviewStatus(reviewId, ContractReview.ReviewStatus.FAILED);
            sendSseEvent(emitter, "error", Map.of("message", "分析任务已取消"));
            Thread.currentThread().interrupt(); // 重新设置中断状态
            return CompletableFuture.completedFuture(null);
        } catch (Exception e) {
            log.error("合同异步分析失败, reviewId={}", reviewId, e);
            self.updateReviewWithResult(reviewId, ContractReview.ReviewStatus.FAILED, Map.of("error", e.getMessage()));
            sendSseEvent(emitter, "error", Map.of("message", "分析失败: " + e.getMessage()));
            return CompletableFuture.failedFuture(e);
        } finally {
            emitter.complete();
        }
    }

    /**
     * 检查分析任务的先决条件
     */
    private boolean isAnalysisPreconditionMet(ContractReview review, SseEmitter emitter) {
        if (review.getReviewStatus() == ContractReview.ReviewStatus.PROCESSING) {
            log.warn("合同正在分析中，忽略重复请求: reviewId={}", review.getId());
            sendSseEvent(emitter, "info", Map.of("message", "合同已在分析中，请勿重复操作"));
            emitter.complete();
            return false;
        }
        if (review.getReviewStatus() == ContractReview.ReviewStatus.COMPLETED) {
            log.info("合同已分析完成，直接返回结果: reviewId={}", review.getId());
            ContractReview fullReview = self.loadCompleteReview(review.getId());
            ContractReviewResultDto resultDto = ContractReviewResultDto.fromEntity(fullReview);
            sendSseEvent(emitter, "result", Map.of("result", resultDto));
            sendSseEvent(emitter, "complete", Map.of("message", "分析已完成"));
            emitter.complete();
            return false;
        }
        return true;
    }

    /**
     * 设置SSE生命周期回调
     */
    private void setupSseLifecycle(SseEmitter emitter, Long reviewId, boolean[] shouldStop) {
        emitter.onCompletion(() -> {
            log.info("SSE连接完成: reviewId={}", reviewId);
            shouldStop[0] = true;
        });
        emitter.onTimeout(() -> {
            log.warn("SSE连接超时: reviewId={}", reviewId);
            shouldStop[0] = true;
            emitter.complete();
        });
        emitter.onError(ex -> {
            log.error("SSE连接错误: reviewId={}", reviewId, ex);
            shouldStop[0] = true;
        });
    }

    /**
     * 通用的SSE事件发送方法
     */
    private void sendSseEvent(SseEmitter emitter, String eventName, Object data) {
        try {
            if (emitter != null) {
                // 注意：前端期望的格式是 { type: 'progress', step: ..., message: ... }
                // 而不是 { type: 'progress', data: { ... } }
                // 因此我们直接序列化 data 对象
                String jsonData = serializeToJsonWithUtf8(data);
                emitter.send(SseEmitter.event().name(eventName).data(jsonData));
            }
        } catch (Exception e) {
            log.warn("发送SSE事件 '{}' 失败: {}", eventName, e.getMessage());
        }
    }
    
    /**
     * 执行合同风险分析（同步版本，保持向后兼容）
     */
    @Transactional
    public ContractReview analyzeContract(Long reviewId) {
        log.info("开始分析合同，审查ID: {}", reviewId);
        
        ContractReview review = contractReviewRepository.findById(reviewId)
                .orElseThrow(() -> new IllegalArgumentException("审查记录不存在: " + reviewId));
        
        try {
            // 更新状态为处理中
            review.setReviewStatus(ContractReview.ReviewStatus.PROCESSING);
            contractReviewRepository.save(review);

            // 使用增强的AI服务进行结构化风险分析
            ContractRiskAnalysisResult analysisResult = 
                aiService.analyzeContractRiskStructured(review.getContentText());
            
            // 保存分析结果
            review = saveAnalysisResult(review, analysisResult);
            
            log.info("合同分析完成，风险等级: {}", review.getRiskLevel());
            return review;
            
        } catch (Exception e) {
            log.error("合同分析失败", e);
            review.setReviewStatus(ContractReview.ReviewStatus.FAILED);
            review.setReviewResult(Map.of(
                "error", e.getMessage(),
                "timestamp", LocalDateTime.now().toString()
            ));
            return contractReviewRepository.save(review);
        }
    }

    /**
     * 根据用户ID分页查询审查记录
     */
    public Page<ContractReview> getReviewsByUser(Long userId, Pageable pageable) {
        return contractReviewRepository.findByUserIdOrderByCreatedAtDesc(userId, pageable);
    }

    /**
     * 根据状态查询审查记录
     */
    public List<ContractReview> getReviewsByStatus(ContractReview.ReviewStatus status) {
        return contractReviewRepository.findByReviewStatus(status);
    }

    /**
     * 查询指定时间范围内的审查记录
     */
    public List<ContractReview> getReviewsByDateRange(LocalDateTime startDate, LocalDateTime endDate) {
        return contractReviewRepository.findByDateRange(startDate, endDate);
    }

    /**
     * 统计用户的审查记录数量
     */
    public long countReviewsByUser(Long userId) {
        return contractReviewRepository.countByUserId(userId);
    }

    /**
     * 统计各风险等级的记录数量
     */
    public Map<String, Long> getRiskLevelStatistics() {
        List<Object[]> results = contractReviewRepository.countByRiskLevel();
        Map<String, Long> statistics = new java.util.HashMap<>();
        
        for (Object[] result : results) {
            ContractReview.RiskLevel riskLevel = (ContractReview.RiskLevel) result[0];
            Long count = (Long) result[1];
            statistics.put(riskLevel.name(), count);
        }
        
        return statistics;
    }

    /**
     * 根据ID查找审查记录
     */
    public Optional<ContractReview> findById(Long id) {
        return contractReviewRepository.findById(id);
    }

    /**
     * 删除审查记录
     * @param reviewId 审查记录ID
     */
    @Transactional
    public void deleteReview(Long reviewId) {
        log.info("删除审查记录: {}", reviewId);
        contractReviewRepository.deleteById(reviewId);
    }

    /**
     * 获取审查记录用于报告生成（在独立只读事务中，避免连接泄漏）
     * 确保所有懒加载的关联都已加载并detach
     */
    @Transactional(readOnly = true, propagation = org.springframework.transaction.annotation.Propagation.REQUIRES_NEW)
    public ContractReview getReviewForReportGeneration(Long reviewId) {
        Optional<ContractReview> reviewOpt = contractReviewRepository.findById(reviewId);
        if (reviewOpt.isEmpty()) {
            return null;
        }
        
        ContractReview review = reviewOpt.get();
        
        // 强制加载所有懒加载的关联，避免后续访问时再次查询数据库
        if (review.getUser() != null) {
            review.getUser().getUsername(); // 触发user加载
        }
        if (review.getRiskClauses() != null) {
            review.getRiskClauses().size(); // 触发riskClauses集合加载
            // 加载每个风险条款的详细信息
            for (var clause : review.getRiskClauses()) {
                clause.getRiskType(); // 确保每个条款都被加载
            }
        }
        
        // 显式detach实体，确保不再受持久化上下文管理
        entityManager.detach(review);
        if (review.getUser() != null) {
            entityManager.detach(review.getUser());
        }
        if (review.getRiskClauses() != null) {
            review.getRiskClauses().forEach(entityManager::detach);
        }
        
        return review;
    }

    /**
     * 获取审查详情并构建摘要（用于API返回）
     * 
     * @param reviewId 审查ID
     * @return 包含摘要的审查结果DTO
     */
    public com.river.LegalAssistant.dto.ContractReviewResultDto getReviewDetailsWithSummary(Long reviewId) {
        ContractReview review = findById(reviewId)
                .orElseThrow(() -> new IllegalArgumentException("审查记录不存在: " + reviewId));
        
        // 转换为详细的DTO
        com.river.LegalAssistant.dto.ContractReviewResultDto resultDto = 
                com.river.LegalAssistant.dto.ContractReviewResultDto.fromEntity(review);
        
        // 构建审查摘要（仅当审查已完成时）
        if (review.getReviewResult() != null && review.getReviewStatus() == ContractReview.ReviewStatus.COMPLETED) {
            com.river.LegalAssistant.dto.ContractReviewResultDto.ReviewSummaryDto summary = buildReviewSummary(review);
            resultDto.setSummary(summary);
        }
        
        return resultDto;
    }

    /**
     * 构建审查摘要
     * 从Controller中迁移的业务逻辑
     * 
     * @param review 审查记录
     * @return 审查摘要DTO
     */
    public com.river.LegalAssistant.dto.ContractReviewResultDto.ReviewSummaryDto buildReviewSummary(ContractReview review) {
        com.river.LegalAssistant.dto.ContractReviewResultDto.ReviewSummaryDto.ReviewSummaryDtoBuilder summaryBuilder = 
            com.river.LegalAssistant.dto.ContractReviewResultDto.ReviewSummaryDto.builder()
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

        // 计算合同完整性评分
        int completenessScore = calculateCompletenessScore(review);
        summaryBuilder.completenessScore(completenessScore);

        return summaryBuilder.build();
    }

    /**
     * 计算合同完整性评分
     * 从Controller中迁移的业务逻辑
     * 
     * @param review 审查记录
     * @return 完整性评分（0-100）
     */
    private int calculateCompletenessScore(ContractReview review) {
        int score = 100; // 基础分100分
        
        // 记录扣分详情用于调试
        StringBuilder scoreDetails = new StringBuilder("评分计算详情: 基础分100");
        
        // 基于风险数量调整评分
        if (review.getTotalRisks() != null && review.getTotalRisks() > 0) {
            int riskDeduction = Math.min(review.getTotalRisks() * 5, 50); // 每个风险扣5分，最多扣50分
            score -= riskDeduction;
            scoreDetails.append(String.format(" - 风险数量扣分(%d个×5分=%d分)", review.getTotalRisks(), riskDeduction));
        }
        
        // 基于风险等级调整评分
        if (review.getRiskLevel() != null) {
            if (review.getRiskLevel() == ContractReview.RiskLevel.HIGH) {
                score -= 20;
                scoreDetails.append(" - 高风险等级扣分(20分)");
            } else if (review.getRiskLevel() == ContractReview.RiskLevel.MEDIUM) {
                score -= 10;
                scoreDetails.append(" - 中等风险等级扣分(10分)");
            }
        }
        
        // 确保最低分数
        int finalScore = Math.max(score, 20); // 最低20分
        if (score < 20) {
            scoreDetails.append(String.format(" = %d分(保底20分)", finalScore));
        } else {
            scoreDetails.append(String.format(" = %d分", finalScore));
        }
        
        log.debug("合同完整性评分计算完成: {}", scoreDetails.toString());
        return finalScore;
    }

    /**
     * 发送SSE进度消息
     */
    private void sendProgress(SseEmitter emitter, ContractAnalysisProgressDto progress) {
        try {
            // 检查emitter是否已经完成，避免在已完成的emitter上发送消息
            if (emitter == null) {
                log.warn("SSE emitter 为空，跳过发送进度消息");
                return;
            }
            
            // 手动序列化JSON以确保中文正确显示
            String jsonData = serializeToJsonWithUtf8(progress);
            
            emitter.send(SseEmitter.event()
                .name("progress")
                .data(jsonData)
                .reconnectTime(1000L)); // 设置重连时间
        } catch (IOException e) {
            log.warn("发送SSE进度消息失败: {}", e.getMessage());
            // 不再调用completeWithError，避免重复完成
        } catch (IllegalStateException e) {
            log.warn("SSE连接已关闭，跳过发送进度消息: {}", e.getMessage());
        } catch (Exception e) {
            log.warn("发送SSE进度消息时发生未知错误: {}", e.getMessage(), e);
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
            log.debug("SSE-JSON序列化完成，内容长度: {}, 包含中文: {}", 
                jsonString.length(), 
                jsonString.matches(".*[\\u4e00-\\u9fa5]+.*"));
            return jsonString;
        } catch (Exception e) {
            log.error("JSON序列化失败", e);
            return "{\"error\": \"序列化失败\"}";
        }
    }

    /**
     * 保存分析结果到数据库
     * 注意：此方法总是在事务性上下文中调用，因此不需要额外的 @Transactional 注解
     */
    protected ContractReview saveAnalysisResult(ContractReview review, ContractRiskAnalysisResult analysisResult) {
        // 设置风险等级
        ContractReview.RiskLevel riskLevel = determineRiskLevelFromStructuredResult(analysisResult.getOverallRiskLevel());
        review.setRiskLevel(riskLevel);
        
        // 设置详细分析结果
        Map<String, Object> detailedResult = new HashMap<>();
        detailedResult.put("originalAnalysis", analysisResult.getOriginalAnalysis());
        detailedResult.put("overallRiskLevel", analysisResult.getOverallRiskLevel());
        detailedResult.put("coreRiskAlerts", analysisResult.getCoreRiskAlerts());
        detailedResult.put("priorityRecommendations", analysisResult.getPriorityRecommendations());
        detailedResult.put("complianceScore", analysisResult.getComplianceScore());
        detailedResult.put("timestamp", LocalDateTime.now().toString());
        detailedResult.put("ai_model", "ollama-qwen2:1.5b");
        
        review.setReviewResult(detailedResult);
        
        // 创建风险条款实体
        List<RiskClause> riskClauses = new ArrayList<>();
        for (ContractRiskAnalysisResult.RiskClauseInfo riskInfo : analysisResult.getRiskClauses()) {
            RiskClause riskClause = getRiskClause(review, riskInfo);
            riskClauses.add(riskClause);
        }
        
        review.setRiskClauses(riskClauses);
        review.setTotalRisks(riskClauses.size());
        
        // 更新状态为完成
        review.setReviewStatus(ContractReview.ReviewStatus.COMPLETED);
        review.setCompletedAt(LocalDateTime.now());
        
        return contractReviewRepository.save(review);
    }

    private RiskClause getRiskClause(ContractReview review, ContractRiskAnalysisResult.RiskClauseInfo riskInfo) {
        RiskClause riskClause = new RiskClause();
        riskClause.setContractReview(review);
        riskClause.setClauseText(""); // 需要从原文本中提取，这里暂时留空
        riskClause.setRiskType(riskInfo.getRiskType());
        riskClause.setRiskLevel(determineRiskLevelFromStructuredResult(riskInfo.getRiskLevel()));
        riskClause.setRiskDescription(riskInfo.getRiskDescription());
        riskClause.setSuggestion(riskInfo.getSuggestion());
        riskClause.setLegalBasis(""); // 需要进一步分析，暂时留空
        return riskClause;
    }

    /**
     * 分块分析长合同文本（支持中断）
     */
    private ContractRiskAnalysisResult analyzeContractInChunksWithInterruption(String contractText, Long reviewId, SseEmitter emitter, boolean[] shouldStop) throws InterruptedException {
        log.info("开始分块分析合同（支持中断），文本长度: {}", contractText.length());
        
        try {
            // 1. 将文本分块（每块约6000字符，减少处理时间）
            List<String> chunks = splitTextIntoChunks(contractText, 6000, 300);
            if (!shouldStop[0]) {
                sendProgress(emitter, ContractAnalysisProgressDto.analyzing(reviewId, 
                    String.format("已分割为%d个块，逐个分析...", chunks.size())));
            }
            
            List<ContractRiskAnalysisResult> chunkResults = new ArrayList<>();
            
            // 2. 逐个分析每个块，支持中断
            for (int i = 0; i < chunks.size(); i++) {
                // 检查是否应该停止
                if (shouldStop[0]) {
                    log.info("分块分析被中断，已处理 {}/{} 块", i, chunks.size());
                    throw new InterruptedException("分块分析被中断");
                }
                
                if (!shouldStop[0]) {
                    sendProgress(emitter, ContractAnalysisProgressDto.analyzing(reviewId, 
                        String.format("正在分析第%d/%d块...", i + 1, chunks.size())));
                }
                
                String chunk = chunks.get(i);
                ContractRiskAnalysisResult chunkResult = aiService.analyzeContractRiskStructured(chunk);
                chunkResults.add(chunkResult);
                
                // 每处理完一块后检查中断
                if (shouldStop[0]) {
                    log.info("分块分析在第{}块后被中断", i + 1);
                    throw new InterruptedException("分块分析被中断");
                }
            }
            
            // 3. 合并分析结果
            if (!shouldStop[0]) {
                sendProgress(emitter, ContractAnalysisProgressDto.analyzing(reviewId, "正在合并分析结果..."));
            }
            
            // 最终检查中断
            if (shouldStop[0]) {
                throw new InterruptedException("分析结果合并被中断");
            }
            
            return mergeAnalysisResults(chunkResults);
            
        } catch (InterruptedException e) {
            log.info("分块分析被中断");
            throw e;
        } catch (Exception e) {
            log.error("分块分析失败", e);
            // 降级处理：截取前6000字符进行分析
            String truncatedText = contractText.length() > 6000 ? 
                contractText.substring(0, 6000) + "...[\u6587本已截取]" : contractText;
            
            if (shouldStop[0]) {
                throw new InterruptedException("分析任务被中断");
            }
            
            return aiService.analyzeContractRiskStructured(truncatedText);
        }
    }

    /**
     * 分块分析长合同文本（原方法，保持向后兼容）
     */
    private ContractRiskAnalysisResult analyzeContractInChunks(String contractText, Long reviewId, SseEmitter emitter) {
        log.info("开始分块分析合同，文本长度: {}", contractText.length());
        
        try {
            // 1. 将文本分块（每块约8000字符）
            List<String> chunks = splitTextIntoChunks(contractText, 8000, 500);
            sendProgress(emitter, ContractAnalysisProgressDto.analyzing(reviewId, 
                String.format("已分割为%d个块，逐个分析...", chunks.size())));
            
            List<ContractRiskAnalysisResult> chunkResults = new ArrayList<>();
            
            // 2. 逐个分析每个块
            for (int i = 0; i < chunks.size(); i++) {
                sendProgress(emitter, ContractAnalysisProgressDto.analyzing(reviewId, 
                    String.format("正在分析第%d/%d块...", i + 1, chunks.size())));
                
                String chunk = chunks.get(i);
                ContractRiskAnalysisResult chunkResult = aiService.analyzeContractRiskStructured(chunk);
                chunkResults.add(chunkResult);
                
                // 移除停顿以提高处理速度
            }
            
            // 3. 合并分析结果
            sendProgress(emitter, ContractAnalysisProgressDto.analyzing(reviewId, "正在合并分析结果..."));
            return mergeAnalysisResults(chunkResults);
            
        } catch (Exception e) {
            log.error("分块分析失败", e);
            // 降级处理：截取前8000字符进行分析
            String truncatedText = contractText.length() > 8000 ? 
                contractText.substring(0, 8000) + "...[\u6587本已截取]" : contractText;
            return aiService.analyzeContractRiskStructured(truncatedText);
        }
    }
    
    /**
     * 将文本分割成块
     */
    private List<String> splitTextIntoChunks(String text, int chunkSize, int overlap) {
        List<String> chunks = new ArrayList<>();
        
        if (text.length() <= chunkSize) {
            chunks.add(text);
            return chunks;
        }
        
        int start = 0;
        while (start < text.length()) {
            int end = Math.min(start + chunkSize, text.length());
            
            // 尝试在句子边界分割
            if (end < text.length()) {
                int lastPeriod = Math.max(
                    Math.max(text.lastIndexOf('。', end), text.lastIndexOf('!', end)),
                    text.lastIndexOf('?', end)
                );
                if (lastPeriod > start + chunkSize * 0.7) {
                    end = lastPeriod + 1;
                }
            }
            
            String chunk = text.substring(start, end).trim();
            if (!chunk.isEmpty()) {
                chunks.add(chunk);
            }
            
            start = Math.max(start + 1, end - overlap);
        }
        
        return chunks;
    }
    
    /**
     * 合并多个分析结果
     */
    private ContractRiskAnalysisResult mergeAnalysisResults(List<ContractRiskAnalysisResult> results) {
        if (results.isEmpty()) {
            return createEmptyAnalysisResult();
        }
        
        if (results.size() == 1) {
            return results.get(0);
        }
        
        // 合并结果
        ContractRiskAnalysisResult merged = new ContractRiskAnalysisResult();
        
        // 合并原始分析文本
        StringBuilder originalAnalysis = new StringBuilder();
        List<String> coreRiskAlerts = new ArrayList<>();
        List<String> priorityRecommendations = new ArrayList<>();
        List<ContractRiskAnalysisResult.RiskClauseInfo> allRiskClauses = new ArrayList<>();
        
        String highestRiskLevel = "LOW";
        int totalComplianceScore = 0;
        
        for (int i = 0; i < results.size(); i++) {
            ContractRiskAnalysisResult result = results.get(i);
            
            originalAnalysis.append("\n=== 第").append(i + 1).append("部分分析 ===\n");
            originalAnalysis.append(result.getOriginalAnalysis());
            
            coreRiskAlerts.addAll(result.getCoreRiskAlerts());
            priorityRecommendations.addAll(result.getPriorityRecommendations());
            allRiskClauses.addAll(result.getRiskClauses());
            
            // 确定最高风险等级
            if ("HIGH".equals(result.getOverallRiskLevel())) {
                highestRiskLevel = "HIGH";
            } else if ("MEDIUM".equals(result.getOverallRiskLevel()) && "LOW".equals(highestRiskLevel)) {
                highestRiskLevel = "MEDIUM";
            }
            
            totalComplianceScore += result.getComplianceScore() != null ? result.getComplianceScore() : 70;
        }
        
        merged.setOriginalAnalysis(originalAnalysis.toString());
        merged.setOverallRiskLevel(highestRiskLevel);
        merged.setCoreRiskAlerts(coreRiskAlerts.stream().distinct().limit(10).toList());
        merged.setPriorityRecommendations(priorityRecommendations.stream().distinct().limit(8).toList());
        merged.setRiskClauses(allRiskClauses);
        merged.setComplianceScore(totalComplianceScore / results.size());
        
        return merged;
    }
    
    /**
     * 创建空的分析结果
     */
    private ContractRiskAnalysisResult createEmptyAnalysisResult() {
        ContractRiskAnalysisResult result = new ContractRiskAnalysisResult();
        result.setOriginalAnalysis("未能完成分析");
        result.setOverallRiskLevel("MEDIUM");
        result.setCoreRiskAlerts(List.of("需要人工审查"));
        result.setPriorityRecommendations(List.of("建议咨询专业律师"));
        result.setRiskClauses(new ArrayList<>());
        result.setComplianceScore(70);
        return result;
    }
    
    /**
     * 根据结构化分析结果确定风险等级
     */
    private ContractReview.RiskLevel determineRiskLevelFromStructuredResult(String riskLevel) {
        if (riskLevel == null) {
            return ContractReview.RiskLevel.MEDIUM;
        }

        return switch (riskLevel.toUpperCase()) {
            case "HIGH" -> ContractReview.RiskLevel.HIGH;
            case "LOW" -> ContractReview.RiskLevel.LOW;
            default -> ContractReview.RiskLevel.MEDIUM;
        };
    }

    /**
     * 更新审查记录状态（独立事务）
     * 用于异步处理中的状态更新，避免长时间占用连接
     */
    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.REQUIRES_NEW)
    public ContractReview updateReviewStatus(Long reviewId, ContractReview.ReviewStatus status) {
        ContractReview review = contractReviewRepository.findById(reviewId)
                .orElseThrow(() -> new IllegalArgumentException("审查记录不存在: " + reviewId));
        review.setReviewStatus(status);
        return contractReviewRepository.save(review);
    }

    /**
     * 更新审查记录状态和结果（独立事务）
     * 用于异步处理中的状态和结果更新，避免长时间占用连接
     */
    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.REQUIRES_NEW)
    public ContractReview updateReviewWithResult(Long reviewId, ContractReview.ReviewStatus status, 
                                                Map<String, Object> result) {
        ContractReview review = contractReviewRepository.findById(reviewId)
                .orElseThrow(() -> new IllegalArgumentException("审查记录不存在: " + reviewId));
        review.setReviewStatus(status);
        review.setReviewResult(result);
        return contractReviewRepository.save(review);
    }

    /**
     * 重载的保存分析结果方法，增加条款分析结果
     */
    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.REQUIRES_NEW)
    public ContractReview saveAnalysisResultWithTransaction(Long reviewId, ContractRiskAnalysisResult riskResult, Map<String, Object> detailedAnalysis) {
        ContractReview review = contractReviewRepository.findById(reviewId)
                .orElseThrow(() -> new IllegalArgumentException("审查记录不存在: " + reviewId));
        
        // 保存风险分析结果
        review = saveAnalysisResult(review, riskResult);

        // 更新并合并详细条款分析结果
        Map<String, Object> existingResult = review.getReviewResult() != null ? new HashMap<>(review.getReviewResult()) : new HashMap<>();
        existingResult.put("detailedAnalysis", detailedAnalysis);
        review.setReviewResult(existingResult);
        
        return contractReviewRepository.save(review);
    }

    /**
     * 保存PDF报告到文件系统（在事务中执行）
     * 
     * @param reviewId 审查ID
     * @param pdfBytes PDF文件字节数组
     */
    @org.springframework.transaction.annotation.Transactional
    public void savePdfReportWithTransaction(Long reviewId, byte[] pdfBytes) {
        try {
            // 创建报告目录
            java.nio.file.Path reportDir = java.nio.file.Paths.get("uploads", "reports");
            java.nio.file.Files.createDirectories(reportDir);
            
            // 保存PDF文件
            java.nio.file.Path pdfFile = reportDir.resolve(reviewId + "_report.pdf");
            java.nio.file.Files.write(pdfFile, pdfBytes);
            
            log.info("PDF报告已保存到文件系统: {}", pdfFile.toAbsolutePath());
            
            // 更新审查记录中的报告路径
            ContractReview review = contractReviewRepository.findById(reviewId)
                    .orElseThrow(() -> new IllegalArgumentException("审查记录不存在: " + reviewId));
            
            Map<String, Object> existingResult = review.getReviewResult() != null ? 
                    new HashMap<>(review.getReviewResult()) : new HashMap<>();
            existingResult.put("pdfReportPath", pdfFile.toString());
            existingResult.put("pdfReportGeneratedAt", LocalDateTime.now().toString());
            review.setReviewResult(existingResult);
            
            contractReviewRepository.save(review);
            
        } catch (IOException e) {
            log.error("保存PDF报告到文件系统失败, reviewId={}", reviewId, e);
            throw new RuntimeException("保存PDF报告失败: " + e.getMessage(), e);
        }
    }

}
