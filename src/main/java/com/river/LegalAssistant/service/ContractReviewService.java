package com.river.LegalAssistant.service;

import com.river.LegalAssistant.dto.ContractAnalysisProgressDto;
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

    public ContractReviewService(ContractReviewRepository contractReviewRepository,
                               AiService aiService,
                               DocumentParserService documentParserService) {
        this.contractReviewRepository = contractReviewRepository;
        this.aiService = aiService;
        this.documentParserService = documentParserService;
    }

    /**
     * 创建合同审查记录 - 从文件流创建
     */
    @Transactional
    public ContractReview createContractReviewFromFile(User user, String originalFilename, 
                                                      String filePath, Long fileSize, String fileHash,
                                                      InputStream fileInputStream) {
        log.info("从文件流创建合同审查记录，用户: {}, 文件: {}", user.getUsername(), originalFilename);
        
        try {
            // 使用DocumentParserService解析文件内容
            String contentText = documentParserService.parseDocument(fileInputStream, originalFilename, fileSize);
            
            return createContractReview(user, originalFilename, filePath, fileSize, fileHash, contentText);
        } catch (DocumentParserService.DocumentParsingException e) {
            log.error("文档解析失败: {}", e.getMessage());
            throw new RuntimeException("文档解析失败: " + e.getMessage(), e);
        }
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

        // 将文档内容异步添加到AI知识库（向量存储）
        try {
            Map<String, Object> metadata = Map.of(
                "review_id", savedReview.getId().toString(),
                "original_filename", savedReview.getOriginalFilename(),
                "user_id", savedReview.getUser().getId().toString()
            );
            aiService.addDocument(sanitizedContent, metadata);
            log.info("已成功将合同内容添加到AI知识库，审查ID: {}", savedReview.getId());
        } catch (Exception e) {
            log.error("将合同内容添加到AI知识库失败，审查ID: {}", savedReview.getId(), e);
            // 注意：这里可以选择是否因为AI侧的失败而回滚事务，当前策略为不影响主业务
        }

        return savedReview;
    }

    /**
     * 异步执行合同风险分析，并通过SSE推送进度
     */
    @Async
    public CompletableFuture<ContractReview> analyzeContractAsync(Long reviewId, SseEmitter emitter) {
        log.info("开始异步分析合同，审查ID: {}", reviewId);
        
        ContractReview review = contractReviewRepository.findById(reviewId)
                .orElseThrow(() -> new IllegalArgumentException("审查记录不存在: " + reviewId));
        
        try {
            // 1. 发送解析进度
            sendProgress(emitter, ContractAnalysisProgressDto.parsing(reviewId, "开始解析合同内容..."));
            Thread.sleep(500); // 模拟处理时间
            
            // 2. 更新状态为处理中
            review.setReviewStatus(ContractReview.ReviewStatus.PROCESSING);
            contractReviewRepository.save(review);
            
            // 3. 发送分析进度
            sendProgress(emitter, ContractAnalysisProgressDto.analyzing(reviewId, "正在进行AI风险分析..."));
            
            // 4. 使用增强的AI服务进行结构化风险分析
            AiService.ContractRiskAnalysisResult analysisResult = 
                aiService.analyzeContractRiskStructured(review.getContentText());
            
            // 5. 发送生成报告进度
            sendProgress(emitter, ContractAnalysisProgressDto.generatingReport(reviewId, "生成分析报告..."));
            Thread.sleep(300);
            
            // 6. 保存分析结果
            review = saveAnalysisResult(review, analysisResult);
            
            // 7. 发送完成状态
            sendProgress(emitter, ContractAnalysisProgressDto.completed(reviewId, "分析完成"));
            
            log.info("合同异步分析完成，风险等级: {}", review.getRiskLevel());
            return CompletableFuture.completedFuture(review);
            
        } catch (Exception e) {
            log.error("合同异步分析失败", e);
            
            // 更新状态为失败
            review.setReviewStatus(ContractReview.ReviewStatus.FAILED);
            review.setReviewResult(Map.of(
                "error", e.getMessage(),
                "timestamp", LocalDateTime.now().toString()
            ));
            review = contractReviewRepository.save(review);
            
            // 发送错误状态
            sendProgress(emitter, ContractAnalysisProgressDto.error(reviewId, e.getMessage()));
            
            return CompletableFuture.completedFuture(review);
        } finally {
            // 完成SSE连接
            try {
                emitter.complete();
            } catch (Exception e) {
                log.warn("完成SSE连接时出错", e);
            }
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
            AiService.ContractRiskAnalysisResult analysisResult = 
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
     * 发送SSE进度消息
     */
    private void sendProgress(SseEmitter emitter, ContractAnalysisProgressDto progress) {
        try {
            emitter.send(SseEmitter.event()
                .name("progress")
                .data(progress));
        } catch (IOException e) {
            log.warn("发送SSE进度消息失败", e);
            emitter.completeWithError(e);
        }
    }

    /**
     * 保存分析结果到数据库
     * 注意：此方法总是在事务性上下文中调用，因此不需要额外的 @Transactional 注解
     */
    protected ContractReview saveAnalysisResult(ContractReview review, AiService.ContractRiskAnalysisResult analysisResult) {
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
        for (AiService.RiskClauseInfo riskInfo : analysisResult.getRiskClauses()) {
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

    private RiskClause getRiskClause(ContractReview review, AiService.RiskClauseInfo riskInfo) {
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

}
