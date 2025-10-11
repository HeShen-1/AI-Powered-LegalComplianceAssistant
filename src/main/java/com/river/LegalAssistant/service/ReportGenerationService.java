package com.river.LegalAssistant.service;

import com.itextpdf.io.font.PdfEncodings;
import com.itextpdf.io.font.constants.StandardFonts;
import com.itextpdf.kernel.colors.ColorConstants;
import com.itextpdf.kernel.colors.DeviceRgb;
import com.itextpdf.kernel.font.PdfFont;
import com.itextpdf.kernel.font.PdfFontFactory;
import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfWriter;
import com.itextpdf.layout.Document;
import com.itextpdf.layout.borders.Border;
import com.itextpdf.layout.element.*;
import com.itextpdf.layout.properties.TextAlignment;
import com.itextpdf.layout.properties.UnitValue;
import com.river.LegalAssistant.dto.EnhancedReportContent;
import com.river.LegalAssistant.dto.report.ExecutiveSummaryDto;
import com.river.LegalAssistant.dto.report.DeepAnalysisDto;
import com.river.LegalAssistant.dto.report.ImprovementSuggestionDto;
import com.river.LegalAssistant.entity.ContractReview;
import com.river.LegalAssistant.entity.RiskClause;
import com.river.LegalAssistant.service.AgentService;
import com.river.LegalAssistant.service.DeepSeekService;
import com.river.LegalAssistant.util.ReportContentValidator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.CompletableFuture;

/**
 * PDF合规报告生成服务
 * 用于生成专业、规范的合同审查报告
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ReportGenerationService {

    private final DeepSeekService deepSeekService;
    private final AgentService agentService;
    private final MarkdownToPdfRenderer markdownRenderer;
    private final ReportContentValidator contentValidator;
    private final StructuredContentGenerator structuredContentGenerator;
    private final ReportTemplateRenderer templateRenderer;
    private final PromptTemplateService promptTemplateService;

    private static final String REPORT_TITLE = "法律合规智能审查报告";
    private static final String SYSTEM_NAME = "法律合规智能审查助手";
    
    // 颜色定义
    private static final DeviceRgb PRIMARY_COLOR = new DeviceRgb(41, 128, 185);      // 蓝色
    private static final DeviceRgb SUCCESS_COLOR = new DeviceRgb(39, 174, 96);       // 绿色
    private static final DeviceRgb WARNING_COLOR = new DeviceRgb(241, 196, 15);      // 黄色
    private static final DeviceRgb DANGER_COLOR = new DeviceRgb(231, 76, 60);        // 红色
    private static final DeviceRgb GRAY_COLOR = new DeviceRgb(149, 165, 166);        // 灰色
    
    // 中文字体支持
    private PdfFont chineseFont;
    private PdfFont chineseBoldFont;

    /**
     * 生成合同审查PDF报告
     *
     * @param contractReview 合同审查记录
     * @return PDF文件的字节数组
     * @throws IOException PDF生成异常
     */
    public byte[] generateContractReviewReport(ContractReview contractReview) throws IOException {
        log.info("开始生成PDF报告，审查ID: {}", contractReview.getId());

        try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            // 初始化中文字体
            initializeChineseFont();
            
            // 创建PDF文档
            PdfWriter writer = new PdfWriter(baos);
            PdfDocument pdf = new PdfDocument(writer);
            Document document = new Document(pdf);
            
            // 设置默认字体
            if (chineseFont != null) {
                document.setFont(chineseFont);
                log.info("✓ PDF文档字体设置成功");
            } else {
                log.warn("⚠ 中文字体未成功加载，将使用默认字体");
            }
            
            // 使用DeepSeek生成增强内容（带超时和降级处理）
            log.info("准备生成增强内容...");
            EnhancedReportContent enhancedContent = generateEnhancedContentWithFallback(contractReview);
            
            if (enhancedContent != null && enhancedContent.isSuccessful()) {
                log.info("✓ 增强内容生成成功");
            } else {
                String errorMsg = enhancedContent != null ? enhancedContent.getErrorMessage() : "未知错误";
                log.warn("⚠ 增强内容生成失败，将使用基础报告模式: {}", errorMsg);
            }
            
            // 添加文档内容
            log.info("开始添加报告各部分内容...");
            
            log.debug("添加报告头部...");
            addReportHeader(document, contractReview, enhancedContent);
            
            log.debug("添加执行摘要...");
            addExecutiveSummary(document, contractReview, enhancedContent);
            
            log.debug("添加风险统计...");
            addRiskStatistics(document, contractReview);
            
            log.debug("添加风险条款详情...");
            addRiskClausesDetails(document, contractReview);
            
            log.debug("添加AI深度分析...");
            addDeepSeekAnalysis(document, contractReview, enhancedContent);
            
            log.debug("添加改进建议...");
            addRecommendations(document, contractReview, enhancedContent);
            
            log.debug("添加免责声明...");
            addDisclaimer(document);
            
            log.debug("添加页脚...");
            addFooter(document, contractReview);
            
            log.info("所有PDF内容添加完成");

            document.close();
            
            log.info("PDF报告生成成功，审查ID: {}, 文件大小: {} bytes", 
                    contractReview.getId(), baos.size());
            
            return baos.toByteArray();
        } catch (Exception e) {
            log.error("PDF报告生成失败，审查ID: {}", contractReview.getId(), e);
            throw new IOException("PDF报告生成失败: " + e.getMessage(), e);
        }
    }

    /**
     * 初始化中文字体 - 优先使用系统自带的宋体
     */
    private void initializeChineseFont() throws IOException {
        log.info("初始化PDF中文字体支持...");
        
        // 策略1: 优先使用系统自带的宋体
        String[] systemFontPaths = {
            "C:/Windows/Fonts/STSONG.TTF",           // 华文宋体（最优先，PDF兼容性最好）
            "C:/Windows/Fonts/simsun.ttc,0",         // 宋体
            "C:/Windows/Fonts/simhei.ttf",           // 黑体
            "C:/Windows/Fonts/msyh.ttc,0",           // 微软雅黑
            // 备选字体文件
            "fonts/FangZhengFangSong-GBK-1.ttf"      // 方正仿宋（备选）
        };
        
        String[] systemBoldFontPaths = {
            "C:/Windows/Fonts/STSONG.TTF",           // 华文宋体（作为粗体，PDF兼容性最好）
            "C:/Windows/Fonts/simhei.ttf",           // 黑体（作为宋体的粗体）
            "C:/Windows/Fonts/simhei.ttf",           // 黑体
            "C:/Windows/Fonts/msyhbd.ttc,0",         // 微软雅黑粗体
            "fonts/FangZhengFangSong-GBK-1.ttf"      // 方正仿宋（备选）
        };
        
        // 尝试加载系统字体
        for (int i = 0; i < systemFontPaths.length; i++) {
            try {
                String fontPath = systemFontPaths[i];
                String boldFontPath = i < systemBoldFontPaths.length ? systemBoldFontPaths[i] : fontPath;
                
                log.debug("尝试加载系统字体: {}", fontPath);
                
                // 优先从系统字体路径加载
                if (fontPath.startsWith("C:/Windows/Fonts/")) {
                    // 从系统字体目录加载字体
                    chineseFont = PdfFontFactory.createFont(fontPath, 
                        PdfEncodings.IDENTITY_H, PdfFontFactory.EmbeddingStrategy.PREFER_EMBEDDED);
                    chineseBoldFont = PdfFontFactory.createFont(boldFontPath, 
                        PdfEncodings.IDENTITY_H, PdfFontFactory.EmbeddingStrategy.PREFER_EMBEDDED);
                        
                    log.info("✓ 成功加载系统字体: {} (华文宋体优先，PDF兼容性最佳)", fontPath);
                    return;
                } else if (fontPath.startsWith("fonts/")) {
                    // 从classpath加载备选字体
                    byte[] fontBytes = loadFontFromResources(fontPath);
                    byte[] boldFontBytes = loadFontFromResources(boldFontPath);
                    
                    if (fontBytes != null && boldFontBytes != null) {
                        chineseFont = PdfFontFactory.createFont(fontBytes, 
                            PdfEncodings.IDENTITY_H, PdfFontFactory.EmbeddingStrategy.PREFER_EMBEDDED);
                        chineseBoldFont = PdfFontFactory.createFont(boldFontBytes, 
                            PdfEncodings.IDENTITY_H, PdfFontFactory.EmbeddingStrategy.PREFER_EMBEDDED);
                            
                        log.info("✓ 成功从resources目录加载备选字体: {}", fontPath);
                        return;
                    }
                }
                
            } catch (Exception e) {
                log.debug("字体加载失败 {}: {}", systemFontPaths[i], e.getMessage());
            }
        }
        
        // 策略2: 最后的回退方案 - 使用标准字体
        log.warn("⚠ 所有中文字体加载失败，使用标准字体作为回退方案");
        try {
            chineseFont = PdfFontFactory.createFont(StandardFonts.HELVETICA);
            chineseBoldFont = PdfFontFactory.createFont(StandardFonts.HELVETICA_BOLD);
            log.warn("✓ 使用Helvetica字体作为最后回退（中文可能无法正确显示）");
            return;
        } catch (Exception e) {
            log.error("连标准字体都加载失败: {}", e.getMessage());
        }
        
        // 如果所有字体都失败，设置为null，后续会有特殊处理
        log.error("⚠ 无法加载任何字体，将使用默认字体处理");
        chineseFont = null;
        chineseBoldFont = null;
    }
    
    /**
     * 从resources目录加载字体文件
     */
    private byte[] loadFontFromResources(String fontPath) {
        try {
            log.debug("从resources目录加载字体: {}", fontPath);
            
            // 使用ClassLoader加载resources目录下的字体文件
            java.io.InputStream fontStream = getClass().getClassLoader().getResourceAsStream(fontPath);
            
            if (fontStream == null) {
                log.debug("字体文件不存在: {}", fontPath);
                return null;
            }
            
            // 读取字体文件的所有字节
            byte[] fontBytes = fontStream.readAllBytes();
            fontStream.close();
            
            log.debug("✓ 成功读取字体文件: {}, 大小: {} bytes", fontPath, fontBytes.length);
            return fontBytes;
            
        } catch (Exception e) {
            log.debug("从resources目录加载字体失败 {}: {}", fontPath, e.getMessage());
            return null;
        }
    }
    
    /**
     * PDF文本清理 - 保留中文字符，只移除真正有问题的控制字符
     * 适用于已加载中文字体的情况
     */
    private String cleanTextForPdf(String text) {
        if (text == null || text.trim().isEmpty()) {
            return "";
        }
        
        if (log.isDebugEnabled()) {
            log.debug("开始PDF文本清理，原始长度: {}", text.length());
        }
        
        StringBuilder result = new StringBuilder();
        
        for (char c : text.toCharArray()) {
            // 保留所有正常字符，包括中文
            if ((c >= 0x20 && c <= 0x7E) ||           // ASCII可打印字符
                (c >= 0x4E00 && c <= 0x9FFF) ||       // 中文汉字（CJK统一汉字）
                (c >= 0x3400 && c <= 0x4DBF) ||       // CJK扩展A
                (c >= 0xFF00 && c <= 0xFFEF) ||       // 全角ASCII、全角标点
                // 常用中文标点（直接保留）
                c == '，' || c == '。' || c == '；' || c == '：' ||
                c == '？' || c == '！' || c == '、' ||  // 保留顿号
                c == '（' || c == '）' || c == '《' || c == '》' ||
                c == '【' || c == '】' || c == '「' || c == '」' ||
                c == '\n' || c == '\r' || c == '\t') { // 换行、回车、制表符
                result.append(c);
            } else {
                // 只处理特殊的控制字符和符号
                switch (c) {
                    // 特殊空白字符统一为空格
                    case '\u00A0', '\u2003', '\u2002', '\u2009' -> result.append(' ');
                    // 其他空白字符
                    default -> {
                        if (Character.isWhitespace(c)) {
                            result.append(' ');
                        }
                        // NULL字符和其他控制字符直接忽略
                        // 不做任何处理，直接跳过
                    }
                }
            }
        }
        
        String cleanedText = result.toString();
        
        // 只清理多余的空格，保留原有格式
        cleanedText = cleanedText.replaceAll("[ \\t]+", " ");  // 合并连续空格
        cleanedText = cleanedText.replaceAll("\n\n\n+", "\n\n");  // 最多保留两个连续换行
        
        if (log.isDebugEnabled()) {
            log.debug("PDF文本清理完成，处理后长度: {}", cleanedText.length());
        }
        
        return cleanedText;
    }
    
    /**
     * 清理文件名用于PDF显示，保留中文字符和常用符号
     */
    private String cleanFilenameForPdfDisplay(String filename) {
        if (filename == null || filename.trim().isEmpty()) {
            return "未知文件";
        }
        
        // 只移除真正有问题的控制字符，保留所有正常的中文字符和标点
        String cleaned = filename
                // 只移除NULL字符和真正有害的控制字符（C0控制字符）
                .replaceAll("[\u0000-\u001F]", "")
                // 移除BOM和其他特殊标记
                .replaceAll("[\uFEFF\uFFFE\uFFFF]", "")
                .trim();
        
        // 如果清理后为空，返回默认值
        return cleaned.isEmpty() ? "未知文件" : cleaned;
    }
    
    /**
     * 安全地创建段落，确保文本内容不会导致字体问题
     */
    private Paragraph createSafeParagraph(String text) {
        if (text == null || text.trim().isEmpty()) {
            return new Paragraph(""); // 空段落
        }
        
        String cleanText = cleanTextForPdf(text);
        // 如果清理后为空但原文本不为空，提供安全的回退策略
        if (cleanText.trim().isEmpty() && !text.trim().isEmpty()) {
            log.warn("文本清理导致内容丢失，使用安全回退策略: {}", text.substring(0, Math.min(text.length(), 50)));
            // 不直接使用原文本，而是进行安全的字符替换
            cleanText = createSafeFallbackText(text);
        }
        
        Paragraph paragraph = new Paragraph(cleanText);
        
        // 安全地设置字体，避免字体为null时的问题
        try {
            if (chineseFont != null) {
                paragraph.setFont(chineseFont);
            }
        } catch (Exception e) {
            log.warn("设置段落字体失败，使用默认字体: {}", e.getMessage());
            // 不抛出异常，使用默认字体
        }
        
        return paragraph;
    }
    
    /**
     * 创建安全的回退文本，将所有不安全字符替换为安全字符
     */
    private String createSafeFallbackText(String text) {
        if (text == null) {
            return "";
        }
        
        StringBuilder result = new StringBuilder();
        for (char c : text.toCharArray()) {
            // 只保留最基本的安全字符
            if ((c >= 0x20 && c <= 0x7E) ||     // 基本ASCII
                (c >= 0x4E00 && c <= 0x9FFF) ||  // 中文汉字
                c == '\n' || c == '\r' || c == '\t' || c == ' ') {  // 基本空白字符
                result.append(c);
            } else if (c >= 0x2500 && c <= 0x257F) {
                // 盒绘制字符替换
                result.append('-');
            } else if (c >= 0x2550 && c <= 0x256C) {
                // 双线盒绘制字符替换
                result.append('=');
            } else {
                // 其他字符替换为安全字符
                result.append(' ');
            }
        }
        
        String fallbackText = result.toString().trim();
        // 确保至少有一些内容
        return fallbackText.isEmpty() ? "[内容包含特殊字符]" : fallbackText;
    }

    /**
     * 使用DeepSeek生成增强报告内容（带降级处理）
     */
    private EnhancedReportContent generateEnhancedContentWithFallback(ContractReview contractReview) {
        log.info("使用DeepSeek生成增强报告内容（带降级处理），审查ID: {}", contractReview.getId());
        
        try {
            // 首先尝试使用DeepSeek生成增强内容
            return generateEnhancedContent(contractReview);
            
        } catch (Exception e) {
            log.warn("DeepSeek服务不可用，使用基础报告模式: {}", e.getMessage());
            
            // 降级处理：生成基础报告内容
            return generateBasicReportContent(contractReview);
        }
    }

    /**
     * 生成基础报告内容（降级处理）
     */
    private EnhancedReportContent generateBasicReportContent(ContractReview contractReview) {
        log.info("生成基础报告内容，审查ID: {}", contractReview.getId());
        
        try {
            StringBuilder basicContent = new StringBuilder();
            
            // 基础执行摘要
            basicContent.append("## 执行摘要\n\n");
            basicContent.append("本报告基于系统基础分析功能生成，对合同进行了风险识别和条款分析。\n\n");
            
            // 风险统计
            if (contractReview.getRiskClauses() != null && !contractReview.getRiskClauses().isEmpty()) {
                long highRiskCount = contractReview.getRiskClauses().stream()
                    .filter(risk -> risk.getRiskLevel() == ContractReview.RiskLevel.HIGH)
                    .count();
                long mediumRiskCount = contractReview.getRiskClauses().stream()
                    .filter(risk -> risk.getRiskLevel() == ContractReview.RiskLevel.MEDIUM)
                    .count();
                long lowRiskCount = contractReview.getRiskClauses().stream()
                    .filter(risk -> risk.getRiskLevel() == ContractReview.RiskLevel.LOW)
                    .count();
                
                basicContent.append("### 风险概况\n\n");
                basicContent.append(String.format("- 高风险项：%d 个\n", highRiskCount));
                basicContent.append(String.format("- 中风险项：%d 个\n", mediumRiskCount));
                basicContent.append(String.format("- 低风险项：%d 个\n", lowRiskCount));
                basicContent.append("\n");
            }
            
            // 基础建议
            basicContent.append("### 基础建议\n\n");
            basicContent.append("1. 请仔细审查所有标识的风险项\n");
            basicContent.append("2. 建议咨询专业法律顾问进行详细评估\n");
            basicContent.append("3. 根据风险等级优先处理高风险项\n");
            basicContent.append("4. 在签署前确保所有风险项得到妥善处理\n\n");
            
            log.info("✓ 基础报告内容生成成功，内容长度: {}", basicContent.length());
            
            return EnhancedReportContent.builder()
                .hasEnhancedContent(false)
                .successful(true)
                .deepAnalysis(basicContent.toString())
                .executiveSummary("基于系统基础分析的合同审查报告")
                .improvementSuggestions("建议咨询专业法律顾问获取更详细的分析")
                .riskAssessment("已识别的风险项需要进一步评估")
                .generatedAt(LocalDateTime.now())
                .processingTime(0L)
                .build();
            
        } catch (Exception e) {
            log.error("基础报告内容生成失败", e);
            return EnhancedReportContent.builder()
                .hasEnhancedContent(false)
                .successful(false)
                .errorMessage("基础报告生成失败: " + e.getMessage())
                .generatedAt(LocalDateTime.now())
                .build();
        }
    }

    /**
     * 使用DeepSeek生成增强的报告内容（重构版：使用结构化内容生成）
     */
    private EnhancedReportContent generateEnhancedContent(ContractReview contractReview) {
        log.info("使用结构化方式生成增强报告内容，审查ID: {}", contractReview.getId());
        
        long startTime = System.currentTimeMillis();
        
        try {
            // 检查合同内容是否存在
            String contractContent = contractReview.getContentText();
            if (contractContent == null || contractContent.trim().isEmpty()) {
                log.warn("合同内容为空或null，审查ID: {}, contentText长度: {}", 
                        contractReview.getId(), 
                        contractContent == null ? "null" : contractContent.length());
                
                return EnhancedReportContent.builder()
                    .hasEnhancedContent(false)
                    .successful(false)
                    .errorMessage("合同内容为空，无法进行AI分析")
                    .generatedAt(LocalDateTime.now())
                    .build();
            }
            
            if (deepSeekService.isAvailable()) {
                log.info("开始使用结构化内容生成器生成报告，合同内容长度: {}", contractContent.length());
                
                // 【重构改进】使用StructuredContentGenerator生成结构化数据
                ExecutiveSummaryDto summaryDto = null;
                DeepAnalysisDto analysisDto = null;
                ImprovementSuggestionDto improvementsDto = null;
                
                try {
                    // 【重构改进】使用StructuredContentGenerator并发生成结构化内容
                    CompletableFuture<ExecutiveSummaryDto> summaryFuture = CompletableFuture.supplyAsync(() -> {
                        try {
                            return structuredContentGenerator.generateExecutiveSummary(contractReview);
                        } catch (Exception e) {
                            log.warn("执行摘要结构化生成失败: {}", e.getMessage());
                            return createFallbackExecutiveSummaryDto(contractReview);
                        }
                    });
                    
                    CompletableFuture<DeepAnalysisDto> analysisFuture = CompletableFuture.supplyAsync(() -> {
                        try {
                            return structuredContentGenerator.generateDeepAnalysis(contractReview);
                        } catch (Exception e) {
                            log.warn("深度分析结构化生成失败: {}", e.getMessage());
                            return createFallbackDeepAnalysisDto();
                        }
                    });
                    
                    CompletableFuture<ImprovementSuggestionDto> improvementsFuture = CompletableFuture.supplyAsync(() -> {
                        try {
                            return structuredContentGenerator.generateImprovementSuggestions(contractReview);
                        } catch (Exception e) {
                            log.warn("改进建议结构化生成失败: {}", e.getMessage());
                            return createFallbackImprovementSuggestionDto();
                        }
                    });
                    
                    // 等待所有任务完成，设置5分钟超时
                    CompletableFuture<Void> allTasks = CompletableFuture.allOf(
                        summaryFuture, analysisFuture, improvementsFuture
                    );
                    
                    allTasks.get(5, TimeUnit.MINUTES);
                    
                    // 获取结构化数据
                    summaryDto = summaryFuture.get();
                    analysisDto = analysisFuture.get();
                    improvementsDto = improvementsFuture.get();
                    
                    log.info("结构化内容生成成功");
                    log.debug("执行摘要: 风险等级={}, 核心风险数={}", 
                        summaryDto != null ? summaryDto.getRiskLevel() : "null",
                        summaryDto != null && summaryDto.getCoreRisks() != null ? summaryDto.getCoreRisks().size() : 0);
                    log.debug("深度分析: 关键条款数={}, 风险评估数={}", 
                        analysisDto != null && analysisDto.getKeyClauses() != null ? analysisDto.getKeyClauses().size() : 0,
                        analysisDto != null && analysisDto.getRiskAssessments() != null ? analysisDto.getRiskAssessments().size() : 0);
                    log.debug("改进建议数: {}", 
                        improvementsDto != null && improvementsDto.getSuggestions() != null ? improvementsDto.getSuggestions().size() : 0);
                } catch (InterruptedException e) {
                    // 正确处理InterruptedException - 恢复中断状态
                    Thread.currentThread().interrupt();
                    // 不输出日志到终端，直接返回失败状态
                    long processingTime = System.currentTimeMillis() - startTime;
                    return EnhancedReportContent.builder()
                        .hasEnhancedContent(false)
                        .successful(false)
                        .errorMessage("AI服务调用被中断")
                        .processingTime(processingTime)
                        .generatedAt(LocalDateTime.now())
                        .build();
                } catch (java.util.concurrent.TimeoutException e) {
                    // 超时异常处理
                    long processingTime = System.currentTimeMillis() - startTime;
                    return EnhancedReportContent.builder()
                        .hasEnhancedContent(false)
                        .successful(false)
                        .errorMessage("AI服务调用超时")
                        .processingTime(processingTime)
                        .generatedAt(LocalDateTime.now())
                        .build();
                } catch (java.util.concurrent.ExecutionException e) {
                    // 执行异常处理
                    long processingTime = System.currentTimeMillis() - startTime;
                    return EnhancedReportContent.builder()
                        .hasEnhancedContent(false)
                        .successful(false)
                        .errorMessage("AI服务调用失败: " + e.getCause().getMessage())
                        .processingTime(processingTime)
                        .generatedAt(LocalDateTime.now())
                        .build();
                }
                
                long processingTime = System.currentTimeMillis() - startTime;
                log.info("结构化内容生成完成，耗时: {}ms", processingTime);
                
                // 【重构改进】将结构化DTO转换为文本内容，用于EnhancedReportContent
                String executiveSummary = convertExecutiveSummaryToText(summaryDto);
                String deepAnalysis = convertDeepAnalysisToText(analysisDto);
                String improvementSuggestions = convertImprovementSuggestionsToText(improvementsDto);
                
                // 【改进原则4: 建立质量校验与容错机制】
                // 验证报告完整性
                ReportContentValidator.ValidationResult validationResult = 
                    contentValidator.validateReportCompleteness(
                        executiveSummary, 
                        deepAnalysis, 
                        contractReview.getTotalRisks()
                    );
                
                EnhancedReportContent content = EnhancedReportContent.builder()
                    .hasEnhancedContent(true)
                    .deepAnalysis(deepAnalysis != null ? deepAnalysis : "分析内容生成失败")
                    .executiveSummary(executiveSummary != null ? executiveSummary : "摘要生成失败")
                    .improvementSuggestions(improvementSuggestions != null ? improvementSuggestions : "建议生成失败")
                    .riskAssessment("") // 风险评估已整合到深度分析中
                    .generatedAt(LocalDateTime.now())
                    .processingTime(processingTime)
                    .successful(validationResult.isValid())
                    .errorMessage(validationResult.isValid() ? null : validationResult.getErrorMessage())
                    // 保存结构化DTO用于模板渲染
                    .summaryDto(summaryDto)
                    .analysisDto(analysisDto)
                    .improvementsDto(improvementsDto)
                    .build();
                
                // 如果验证不通过，记录详细信息
                if (!validationResult.isValid()) {
                    log.warn("【报告质量检查失败】{}", validationResult.getSummary());
                }
                
                // 保存分析结果到JSON文件
                saveAnalysisResultToJson(contractReview.getId(), content);
                
                return content;
            } else {
                log.warn("DeepSeek服务不可用或合同内容为空");
                return EnhancedReportContent.builder()
                    .hasEnhancedContent(false)
                    .successful(false)
                    .errorMessage("DeepSeek服务不可用")
                    .generatedAt(LocalDateTime.now())
                    .build();
            }
        } catch (Exception e) {
            long processingTime = System.currentTimeMillis() - startTime;
            log.error("DeepSeek增强内容生成失败: {}", e.getMessage(), e);
            
            return EnhancedReportContent.builder()
                .hasEnhancedContent(false)
                .successful(false)
                .errorMessage(e.getMessage())
                .processingTime(processingTime)
                .generatedAt(LocalDateTime.now())
                .build();
        }
    }
    
    /**
     * 保存DeepSeek分析结果到JSON文件
     */
    private void saveAnalysisResultToJson(Long reviewId, EnhancedReportContent content) {
        try {
            java.nio.file.Path reportDir = java.nio.file.Paths.get("uploads", "reports");
            java.nio.file.Files.createDirectories(reportDir);
            
            java.nio.file.Path jsonFile = reportDir.resolve(reviewId + "_analysis.json");
            
            // 构建JSON对象
            java.util.Map<String, Object> jsonData = new java.util.LinkedHashMap<>();
            jsonData.put("reviewId", reviewId);
            jsonData.put("generatedAt", content.getGeneratedAt().toString());
            jsonData.put("processingTime", content.getProcessingTime());
            jsonData.put("successful", content.isSuccessful());
            jsonData.put("executiveSummary", content.getExecutiveSummary());
            jsonData.put("deepAnalysis", content.getDeepAnalysis());
            jsonData.put("improvementSuggestions", content.getImprovementSuggestions());
            jsonData.put("riskAssessment", content.getRiskAssessment());
            
            // 使用Jackson写入JSON
            com.fasterxml.jackson.databind.ObjectMapper objectMapper = new com.fasterxml.jackson.databind.ObjectMapper();
            objectMapper.enable(com.fasterxml.jackson.databind.SerializationFeature.INDENT_OUTPUT);
            objectMapper.writeValue(jsonFile.toFile(), jsonData);
            
            log.debug("DeepSeek分析结果已保存到JSON文件: {}", jsonFile);
        } catch (Exception e) {
            log.warn("保存DeepSeek分析结果到JSON失败: {}", e.getMessage());
            // 不抛出异常，不影响报告生成
        }
    }

    /**
     * 构建执行摘要专用提示（使用模板）
     */
    private String buildExecutiveSummaryPrompt(ContractReview contractReview) {
        String contractContent = contractReview.getContentText();
        if (contractContent != null && contractContent.length() > 8000) {
            contractContent = contractContent.substring(0, 8000) + "...[已截取]";
        }
        
        return promptTemplateService.render("executive-summary", Map.of(
            "contractContent", contractContent != null ? contractContent : "",
            "fileName", contractReview.getOriginalFilename() != null ? contractReview.getOriginalFilename() : "未知",
            "totalRisks", contractReview.getTotalRisks() != null ? contractReview.getTotalRisks() : 0
        ));
    }
    
    /**
     * 生成降级方案的执行摘要（当AI生成失败时使用）
     */
    private String generateFallbackExecutiveSummary(ContractReview contractReview) {
        StringBuilder summary = new StringBuilder();
        
        summary.append("## 合同审查执行摘要\n\n");
        
        // 1. 合同性质判断
        summary.append("### 1. 合同基本信息\n");
        if (contractReview.getOriginalFilename() != null) {
            summary.append("- 合同文件：").append(contractReview.getOriginalFilename()).append("\n");
        }
        summary.append("- 审查时间：").append(formatDateTime(LocalDateTime.now())).append("\n\n");
        
        // 2. 风险等级
        summary.append("### 2. 整体风险评估\n");
        if (contractReview.getRiskLevel() != null) {
            summary.append("- 风险等级：**").append(getRiskLevelDisplayName(contractReview.getRiskLevel())).append("**\n");
            summary.append("- 评估依据：").append(getRiskLevelDescription(contractReview.getRiskLevel())).append("\n\n");
        }
        
        // 3. 核心风险点
        if (contractReview.getTotalRisks() != null && contractReview.getTotalRisks() > 0) {
            summary.append("### 3. 核心风险提示\n");
            summary.append("系统共识别 **").append(contractReview.getTotalRisks()).append("** 个潜在风险点，");
            
            // 统计高风险数量
            if (contractReview.getRiskClauses() != null) {
                long highRiskCount = contractReview.getRiskClauses().stream()
                    .filter(risk -> risk.getRiskLevel() == ContractReview.RiskLevel.HIGH)
                    .count();
                if (highRiskCount > 0) {
                    summary.append("其中高风险项 ").append(highRiskCount).append(" 个");
                }
            }
            summary.append("，需要重点关注。\n\n");
        }
        
        // 4. 核心建议
        summary.append("### 4. 核心行动建议\n");
        if (contractReview.getRiskLevel() == ContractReview.RiskLevel.HIGH) {
            summary.append("**建议：** 暂缓签署，立即咨询专业法律顾问，对所有高风险条款进行修订后再签署。\n");
        } else if (contractReview.getRiskLevel() == ContractReview.RiskLevel.MEDIUM) {
            summary.append("**建议：** 可考虑签署，但需先与对方协商修改关键风险条款，并在履约过程中加强监控。\n");
        } else {
            summary.append("**建议：** 合同风险较低，可以签署，但仍需注意合同执行过程中的规范性。\n");
        }
        
        summary.append("\n*注：本摘要为系统基础分析结果，建议获取完整的AI深度分析报告以获取更详细的专业意见。*\n");
        
        return summary.toString();
    }

    /**
     * 构建改进建议专用提示（使用模板）
     */
    private String buildImprovementSuggestionsPrompt(ContractReview contractReview) {
        String contractContent = contractReview.getContentText();
        if (contractContent != null && contractContent.length() > 8000) {
            contractContent = contractContent.substring(0, 8000) + "...[已截取]";
        }
        
        return promptTemplateService.render("improvement-suggestions", Map.of(
            "contractContent", contractContent != null ? contractContent : "",
            "totalRisks", contractReview.getTotalRisks() != null ? contractReview.getTotalRisks() : 0
        ));
    }

    /**
     * 构建风险评估提示（使用模板）
     */
    private String buildRiskAssessmentPrompt(ContractReview contractReview) {
        String contractContent = contractReview.getContentText();
        if (contractContent != null && contractContent.length() > 8000) {
            contractContent = contractContent.substring(0, 8000) + "...[已截取]";
        }
        
        return promptTemplateService.render("risk-assessment", Map.of(
            "contractContent", contractContent != null ? contractContent : ""
        ));
    }

    /**
     * 构建DeepSeek分析提示（使用模板）
     */
    private String buildDeepSeekAnalysisPrompt(ContractReview contractReview) {
        String contractContent = contractReview.getContentText();
        if (contractContent != null && contractContent.length() > 8000) {
            contractContent = contractContent.substring(0, 8000) + "...[已截取]";
        }
        
        return promptTemplateService.render("deep-analysis", Map.of(
            "contractContent", contractContent != null ? contractContent : "",
            "fileName", contractReview.getOriginalFilename() != null ? contractReview.getOriginalFilename() : "未知",
            "riskLevel", contractReview.getRiskLevel() != null ? getRiskLevelDisplayName(contractReview.getRiskLevel()) : "未评估",
            "totalRisks", contractReview.getTotalRisks() != null ? contractReview.getTotalRisks() : 0
        ));
    }

    /**
     * 加载提示词模板
     */
    private String loadPromptTemplate(String templateName) throws IOException {
        try {
            return new String(getClass().getClassLoader()
                    .getResourceAsStream("prompts/" + templateName)
                    .readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new IOException("无法加载提示词模板: " + templateName, e);
        }
    }

    /**
     * 构建默认分析提示（使用模板）
     */
    private String buildDefaultAnalysisPrompt(ContractReview contractReview) {
        return promptTemplateService.render("default-analysis", Map.of(
            "fileName", contractReview.getOriginalFilename() != null ? contractReview.getOriginalFilename() : "未知",
            "riskLevel", contractReview.getRiskLevel() != null ? getRiskLevelDisplayName(contractReview.getRiskLevel()) : "未评估",
            "totalRisks", contractReview.getTotalRisks() != null ? contractReview.getTotalRisks() : 0
        ));
    }

    /**
     * 添加报告头部
     */
    private void addReportHeader(Document document, ContractReview contractReview, EnhancedReportContent enhancedContent) {
        // 标题
        Paragraph title = createSafeParagraph(REPORT_TITLE)
                .setFontSize(24)
                .setBold()
                .setTextAlignment(TextAlignment.CENTER)
                .setFontColor(PRIMARY_COLOR)
                .setMarginBottom(10);
        document.add(title);

        // 副标题
        Paragraph subtitle = createSafeParagraph("Contract Compliance Review Report")
                .setFontSize(12)
                .setItalic()
                .setTextAlignment(TextAlignment.CENTER)
                .setFontColor(GRAY_COLOR)
                .setMarginBottom(20);
        document.add(subtitle);

        // 基本信息表格
        Table infoTable = new Table(UnitValue.createPercentArray(new float[]{1, 2}))
                .setWidth(UnitValue.createPercentValue(100));

        addInfoRow(infoTable, "合同文件名", cleanFilenameForPdfDisplay(contractReview.getOriginalFilename()));
        addInfoRow(infoTable, "审查状态", getStatusDisplayName(contractReview.getReviewStatus()));
        addInfoRow(infoTable, "风险等级", getRiskLevelDisplayName(contractReview.getRiskLevel()));
        addInfoRow(infoTable, "创建时间", formatDateTime(contractReview.getCreatedAt()));
        
        if (contractReview.getCompletedAt() != null) {
            addInfoRow(infoTable, "完成时间", formatDateTime(contractReview.getCompletedAt()));
        }
        
        addInfoRow(infoTable, "报告生成时间", formatDateTime(LocalDateTime.now()));

        document.add(infoTable);
        document.add(createSafeParagraph("\n"));
    }

    /**
     * 添加执行摘要
     */
    private void addExecutiveSummary(Document document, ContractReview contractReview, EnhancedReportContent enhancedContent) {
        // 摘要标题
        addSectionTitle(document, "一、执行摘要", "Executive Summary");

        // 如果有AI增强内容，使用AI生成的摘要（改进原则3：屏蔽内部信息）
        if (enhancedContent.isHasEnhancedContent() && enhancedContent.getExecutiveSummary() != null) {
            String summary = enhancedContent.getExecutiveSummary();
            
            // 验证摘要内容有效性
            if (!summary.contains("生成失败") && !summary.contains("暂时不可用")) {
                // 添加AI摘要标识（不包含具体AI服务名称）
                Paragraph aiHeader = createSafeParagraph("")
                        .setFontSize(11)
                        .setMarginBottom(5);
                aiHeader.add(new Text("【AI智能分析摘要】").setBold().setFontColor(PRIMARY_COLOR));
                document.add(aiHeader);
                
                // 渲染AI摘要内容
                markdownRenderer.renderMarkdownToPdf(document, summary, 
                                                   chineseFont, chineseBoldFont);
                
                // 添加分隔线（使用普通减号，避免字体兼容性问题）
                document.add(createSafeParagraph("-".repeat(80))
                        .setTextAlignment(TextAlignment.CENTER)
                        .setFontColor(GRAY_COLOR)
                        .setMarginBottom(10));
            } else {
                log.warn("执行摘要内容验证失败，包含错误信息，将跳过AI摘要部分");
            }
        }

        // 整体评估
        Paragraph overallAssessment = createSafeParagraph("")
                .setFontSize(11)
                .setMarginBottom(10);

        overallAssessment.add("本次合同审查基于AI智能分析技术，对合同文本进行全面的风险识别和合规性检查。");
        
        if (contractReview.getRiskLevel() != null) {
            String riskLevelText = getRiskLevelDescription(contractReview.getRiskLevel());
            DeviceRgb riskColor = getRiskLevelColor(contractReview.getRiskLevel());
            
            overallAssessment.add("审查结果显示该合同整体风险等级为：")
                    .add(new Text(getRiskLevelDisplayName(contractReview.getRiskLevel()))
                            .setBold()
                            .setFontColor(riskColor))
                    .add("。" + riskLevelText);
        }

        if (contractReview.getTotalRisks() != null && contractReview.getTotalRisks() > 0) {
            overallAssessment.add("共识别出 ")
                    .add(new Text(contractReview.getTotalRisks().toString())
                            .setBold()
                            .setFontColor(PRIMARY_COLOR))
                    .add(" 项潜在风险点，需要重点关注。");
        }

        document.add(overallAssessment);

        // 核心风险提示
        if (contractReview.getReviewResult() != null) {
            Object coreRiskAlerts = contractReview.getReviewResult().get("coreRiskAlerts");
            if (coreRiskAlerts instanceof java.util.List<?> alerts && !alerts.isEmpty()) {
                addSubsectionTitle(document, "核心风险提示");
                addSimpleTextList(document, alerts, DANGER_COLOR);
            }
        }

        document.add(createSafeParagraph("\n"));
    }

    /**
     * 添加风险统计
     */
    private void addRiskStatistics(Document document, ContractReview contractReview) {
        addSectionTitle(document, "二、风险分布统计", "Risk Statistics");

        if (contractReview.getRiskClauses() == null || contractReview.getRiskClauses().isEmpty()) {
            document.add(createSafeParagraph("未发现具体风险条款。")
                    .setFontColor(SUCCESS_COLOR)
                    .setMarginBottom(15));
            document.add(createSafeParagraph("\n"));
            return;
        }

        // 统计各风险等级数量
        Map<ContractReview.RiskLevel, Long> riskLevelCount = contractReview.getRiskClauses()
                .stream()
                .collect(Collectors.groupingBy(RiskClause::getRiskLevel, Collectors.counting()));

        // 创建统计表格
        Table statisticsTable = new Table(UnitValue.createPercentArray(new float[]{2, 1, 1}))
                .setWidth(UnitValue.createPercentValue(100));

        // 表头
        statisticsTable.addHeaderCell(createHeaderCell("风险等级"));
        statisticsTable.addHeaderCell(createHeaderCell("数量"));
        statisticsTable.addHeaderCell(createHeaderCell("占比"));

        // 总数
        int totalRisks = contractReview.getRiskClauses().size();

        // 添加各等级统计
        for (ContractReview.RiskLevel level : ContractReview.RiskLevel.values()) {
            long count = riskLevelCount.getOrDefault(level, 0L);
            if (count > 0) {
                double percentage = (double) count / totalRisks * 100;
                
                statisticsTable.addCell(createRiskLevelCell(level));
                statisticsTable.addCell(createDataCell(String.valueOf(count)));
                statisticsTable.addCell(createDataCell(String.format("%.1f%%", percentage)));
            }
        }

        document.add(statisticsTable);
        document.add(createSafeParagraph("\n"));
    }

    /**
     * 添加风险条款详情
     */
    private void addRiskClausesDetails(Document document, ContractReview contractReview) {
        addSectionTitle(document, "三、风险条款详情", "Risk Clause Details");

        if (contractReview.getRiskClauses() == null || contractReview.getRiskClauses().isEmpty()) {
            document.add(createSafeParagraph("未发现具体风险条款。")
                    .setFontColor(SUCCESS_COLOR)
                    .setMarginBottom(15));
            document.add(createSafeParagraph("\n"));
            return;
        }

        // 按风险等级排序（高风险优先）
        List<RiskClause> sortedRiskClauses = contractReview.getRiskClauses().stream()
                .sorted((r1, r2) -> {
                    int priority1 = getRiskLevelPriority(r1.getRiskLevel());
                    int priority2 = getRiskLevelPriority(r2.getRiskLevel());
                    return Integer.compare(priority2, priority1); // 降序排列
                })
                .toList();

        int index = 1;
        for (RiskClause riskClause : sortedRiskClauses) {
            addRiskClauseDetail(document, riskClause, index++);
        }

        document.add(createSafeParagraph("\n"));
    }

    /**
     * 添加单个风险条款详情
     */
    private void addRiskClauseDetail(Document document, RiskClause riskClause, int index) {
        // 风险条款标题
        Paragraph riskTitle = createSafeParagraph("")
                .setFontSize(12)
                .setBold()
                .setMarginBottom(5);

        riskTitle.add("风险点 " + index + "：")
                .add(new Text(cleanTextForPdf(riskClause.getRiskType()))
                        .setFontColor(PRIMARY_COLOR));

        riskTitle.add("  [")
                .add(new Text(getRiskLevelDisplayName(riskClause.getRiskLevel()))
                        .setFontColor(getRiskLevelColor(riskClause.getRiskLevel()))
                        .setBold())
                .add("]");

        document.add(riskTitle);

        // 风险描述
        if (riskClause.getRiskDescription() != null && !riskClause.getRiskDescription().trim().isEmpty()) {
            Paragraph riskDesc = createSafeParagraph("")
                    .setFontSize(10)
                    .setMarginLeft(15)
                    .setMarginBottom(5);

            riskDesc.add("风险描述：" + cleanTextForPdf(riskClause.getRiskDescription()));
            document.add(riskDesc);
        }

        // 建议措施
        if (riskClause.getSuggestion() != null && !riskClause.getSuggestion().trim().isEmpty()) {
            Paragraph suggestion = createSafeParagraph("")
                    .setFontSize(10)
                    .setMarginLeft(15)
                    .setMarginBottom(10);

            suggestion.add(new Text("建议措施：").setBold().setFontColor(SUCCESS_COLOR))
                    .add(cleanTextForPdf(riskClause.getSuggestion()));
            document.add(suggestion);
        }

        // 分隔线 - 使用安全的字符
        document.add(createSafeParagraph("-".repeat(50))
                .setFontColor(GRAY_COLOR)
                .setFontSize(8)
                .setMarginBottom(10));
    }

    /**
     * 添加DeepSeek深度分析（改进版，符合质量检查原则）
     */
    private void addDeepSeekAnalysis(Document document, ContractReview contractReview, EnhancedReportContent enhancedContent) {
        addSectionTitle(document, "四、AI深度分析", "AI Deep Analysis");
        
        // 【改进原则4: 质量校验与容错机制】检查内容有效性
        if (!enhancedContent.isHasEnhancedContent() || enhancedContent.getDeepAnalysis() == null) {
            // 如果没有AI增强内容，提供基础分析信息
            document.add(createSafeParagraph("【系统提示】AI深度分析服务暂时不可用")
                    .setFontColor(WARNING_COLOR)
                    .setBold()
                    .setMarginBottom(10));
            
            // 添加基础合同信息分析
            if (contractReview.getContentText() != null && !contractReview.getContentText().trim().isEmpty()) {
                document.add(createSafeParagraph("以下提供系统基础分析信息：")
                        .setMarginBottom(10));
                
                int textLength = contractReview.getContentText().length();
                document.add(createSafeParagraph("• 合同文本长度：" + textLength + " 字符")
                        .setMarginLeft(20)
                        .setMarginBottom(5));
                
                if (contractReview.getRiskLevel() != null) {
                    document.add(createSafeParagraph("• 识别风险等级：" + getRiskLevelDisplayName(contractReview.getRiskLevel()))
                            .setMarginLeft(20)
                            .setMarginBottom(5)
                            .setFontColor(getRiskLevelColor(contractReview.getRiskLevel())));
                }
                
                if (contractReview.getTotalRisks() != null) {
                    document.add(createSafeParagraph("• 发现风险点数量：" + contractReview.getTotalRisks() + " 个")
                            .setMarginLeft(20)
                            .setMarginBottom(10));
                }
                
                // 提供降级建议
                document.add(createSafeParagraph("建议：")
                        .setBold()
                        .setMarginTop(10)
                        .setMarginBottom(5));
                document.add(createSafeParagraph("1. 请参考「风险条款详情」章节了解具体风险点")
                        .setMarginLeft(20)
                        .setMarginBottom(3));
                document.add(createSafeParagraph("2. 建议稍后重新生成报告以获取完整的AI深度分析")
                        .setMarginLeft(20)
                        .setMarginBottom(3));
                document.add(createSafeParagraph("3. 如持续出现此问题，请联系技术支持")
                        .setMarginLeft(20)
                        .setMarginBottom(15));
            } else {
                document.add(createSafeParagraph("• 合同内容不可用，无法进行深度分析")
                        .setMarginLeft(20)
                        .setMarginBottom(10)
                        .setFontColor(DANGER_COLOR));
                
                document.add(createSafeParagraph("请确保合同文件已正确上传并提取文本内容。")
                        .setFontColor(GRAY_COLOR)
                        .setItalic()
                        .setMarginBottom(15));
            }
            return;
        }
        
        // 检查分析内容是否有效（不是错误提示）
        String analysis = enhancedContent.getDeepAnalysis();
        if (analysis.contains("生成失败") || analysis.contains("暂时不可用")) {
            document.add(createSafeParagraph("【内容生成异常】AI分析内容生成过程中出现问题")
                    .setFontColor(DANGER_COLOR)
                    .setBold()
                    .setMarginBottom(10));
            
            document.add(createSafeParagraph("错误信息：" + analysis)
                    .setFontColor(GRAY_COLOR)
                    .setMarginBottom(10));
            
            document.add(createSafeParagraph("建议重新生成报告或联系技术支持。")
                    .setFontColor(GRAY_COLOR)
                    .setItalic()
                    .setMarginBottom(15));
            return;
        }

        // AI分析标识（移除了DeepSeek品牌名称，改进原则3）
        Paragraph aiIndicator = createSafeParagraph("")
                .setFontSize(10)
                .setFontColor(GRAY_COLOR)
                .setItalic()
                .setMarginBottom(10);
        
        aiIndicator.add("【本部分内容由AI智能分析生成】生成时间：")
                .add(formatDateTime(enhancedContent.getGeneratedAt()));
        
        document.add(aiIndicator);

        // AI分析内容 - 使用Markdown渲染（限制长度）
        if (analysis != null && !analysis.trim().isEmpty()) {
            // 限制内容长度，避免报告过长
            if (analysis.length() > 3000) {
                log.info("AI分析内容过长({} 字符)，截取摘要部分", analysis.length());
                analysis = analysis.substring(0, 3000) + "\n\n...[内容过长，已省略部分内容，完整内容请查看JSON分析文件]";
            }
            
            log.debug("正在渲染AI深度分析内容，长度: {} 字符", analysis.length());
            markdownRenderer.renderMarkdownToPdf(document, analysis, chineseFont, chineseBoldFont);
        }

        // 法律风险评估（如果有，限制长度）
        if (enhancedContent.getRiskAssessment() != null && !enhancedContent.getRiskAssessment().trim().isEmpty()) {
            String riskAssessment = enhancedContent.getRiskAssessment();
            
            // 验证内容有效性
            if (!riskAssessment.contains("暂时不可用") && !riskAssessment.contains("生成失败")) {
                addSubsectionTitle(document, "法律风险评估");
                
                if (riskAssessment.length() > 2000) {
                    log.info("风险评估内容过长({} 字符)，截取摘要部分", riskAssessment.length());
                    riskAssessment = riskAssessment.substring(0, 2000) + "\n\n...[内容过长，已省略部分内容]";
                }
                
                markdownRenderer.renderMarkdownToPdf(document, riskAssessment, chineseFont, chineseBoldFont);
            }
        }

        document.add(createSafeParagraph("\n"));
    }

    /**
     * 添加优先建议
     */
    private void addRecommendations(Document document, ContractReview contractReview, EnhancedReportContent enhancedContent) {
        addSectionTitle(document, "五、优先建议", "Priority Recommendations");
        
        // 如果有AI增强建议，优先显示（改进原则3：屏蔽内部信息）
        if (enhancedContent.isHasEnhancedContent() && enhancedContent.getImprovementSuggestions() != null) {
            String suggestions = enhancedContent.getImprovementSuggestions();
            
            // 验证建议内容有效性
            if (!suggestions.contains("生成失败") && !suggestions.contains("暂时不可用")) {
                addSubsectionTitle(document, "AI增强改进建议");
                
                // 限制建议内容长度
                if (suggestions.length() > 2000) {
                    log.info("改进建议内容过长({} 字符)，截取摘要部分", suggestions.length());
                    suggestions = suggestions.substring(0, 2000) + "\n\n...[内容过长，已省略部分内容]";
                }
                
                // 添加AI建议标识（不包含具体AI服务名称）
                Paragraph aiHeader = createSafeParagraph("")
                        .setFontSize(11)
                        .setMarginBottom(5);
                aiHeader.add(new Text("【AI智能专业建议】").setBold().setFontColor(SUCCESS_COLOR));
                document.add(aiHeader);
                
                // 渲染AI建议内容（使用截取后的内容）
                markdownRenderer.renderMarkdownToPdf(document, suggestions, 
                                                   chineseFont, chineseBoldFont);
                
                // 添加分隔线（使用普通减号，避免字体兼容性问题）
                document.add(createSafeParagraph("-".repeat(80))
                        .setTextAlignment(TextAlignment.CENTER)
                        .setFontColor(GRAY_COLOR)
                        .setMarginBottom(10));
                
                addSubsectionTitle(document, "基础风险防范建议");
            } else {
                log.warn("改进建议内容验证失败，包含错误信息，将跳过AI建议部分");
            }
        }

        if (contractReview.getReviewResult() != null) {
            Object priorityRecommendations = contractReview.getReviewResult().get("priorityRecommendations");
            if (priorityRecommendations instanceof java.util.List<?> recommendations && !recommendations.isEmpty()) {
                com.itextpdf.layout.element.List recommendationList = createBulletList(recommendations, PRIMARY_COLOR);
                document.add(recommendationList);
            }
        }

        // 通用建议
        addSubsectionTitle(document, "通用合规建议");
        
        com.itextpdf.layout.element.List generalRecommendations = new com.itextpdf.layout.element.List()
                .setSymbolIndent(12)
                .setListSymbol("• ");
        
        generalRecommendations.add(new ListItem("建议聘请专业法律顾问对高风险条款进行进一步审核"));
        generalRecommendations.add(new ListItem("在合同签署前，确保所有风险点都已得到妥善处理"));
        generalRecommendations.add(new ListItem("定期更新合同条款，以符合最新的法律法规要求"));
        generalRecommendations.add(new ListItem("建立合同履行监督机制，及时发现和处理执行过程中的问题"));
        
        document.add(generalRecommendations);
        document.add(createSafeParagraph("\n"));
    }

    /**
     * 添加免责声明
     */
    private void addDisclaimer(Document document) {
        addSectionTitle(document, "六、免责声明", "Disclaimer");

        Paragraph disclaimer = createSafeParagraph("")
                .setFontSize(9)
                .setFontColor(GRAY_COLOR)
                .setTextAlignment(TextAlignment.JUSTIFIED)
                .setMarginBottom(15);

        disclaimer.add("本报告由" + SYSTEM_NAME + "基于AI技术自动生成，仅供参考。")
                .add("报告内容基于对合同文本的自动化分析，可能存在理解偏差或遗漏。")
                .add("建议在做出重要决策前，咨询专业法律顾问的意见。")
                .add("本系统及报告生成方对基于本报告做出的任何决策不承担法律责任。")
                .add("使用本报告即表示您已理解并同意上述免责条款。");

        document.add(disclaimer);
        document.add(createSafeParagraph("\n"));
    }

    /**
     * 添加页脚
     */
    private void addFooter(Document document, ContractReview contractReview) {
        // 分隔线 - 使用安全的字符
        document.add(createSafeParagraph("=".repeat(80))
                .setFontColor(PRIMARY_COLOR)
                .setFontSize(8)
                .setTextAlignment(TextAlignment.CENTER)
                .setMarginBottom(10));

        // 页脚信息
        Paragraph footer = createSafeParagraph("")
                .setFontSize(8)
                .setFontColor(GRAY_COLOR)
                .setTextAlignment(TextAlignment.CENTER);

        footer.add(SYSTEM_NAME + " | ")
                .add("报告ID: " + contractReview.getId() + " | ")
                .add("生成时间: " + formatDateTime(LocalDateTime.now()));

        document.add(footer);
    }

    // ==================== 辅助方法 ====================

    /**
     * 添加章节标题
     */
    private void addSectionTitle(Document document, String chineseTitle, String englishTitle) {
        Paragraph title = createSafeParagraph("")
                .setFontSize(14)
                .setBold()
                .setFontColor(PRIMARY_COLOR)
                .setMarginTop(15)
                .setMarginBottom(10);

        title.add(cleanTextForPdf(chineseTitle));
        if (englishTitle != null && !englishTitle.isEmpty()) {
            title.add(new Text(" (" + cleanTextForPdf(englishTitle) + ")")
                    .setFontSize(10)
                    .setItalic()
                    .setFontColor(GRAY_COLOR));
        }

        document.add(title);
    }

    /**
     * 添加子章节标题
     */
    private void addSubsectionTitle(Document document, String title) {
        document.add(createSafeParagraph(title)
                .setFontSize(12)
                .setBold()
                .setFontColor(PRIMARY_COLOR)
                .setMarginTop(10)
                .setMarginBottom(5));
    }

    /**
     * 添加信息行到表格
     */
    private void addInfoRow(Table table, String label, String value) {
        Cell labelCell = new Cell()
                .add(createSafeParagraph(label).setBold())
                .setBorder(Border.NO_BORDER)
                .setPadding(5)
                .setBackgroundColor(new DeviceRgb(248, 249, 250));

        Cell valueCell = new Cell()
                .add(createSafeParagraph(value != null ? value : "未知"))
                .setBorder(Border.NO_BORDER)
                .setPadding(5);

        table.addCell(labelCell);
        table.addCell(valueCell);
    }

    /**
     * 创建表头单元格
     */
    private Cell createHeaderCell(String content) {
        return new Cell()
                .add(createSafeParagraph(content).setBold().setFontColor(ColorConstants.WHITE))
                .setBackgroundColor(PRIMARY_COLOR)
                .setTextAlignment(TextAlignment.CENTER)
                .setPadding(8);
    }

    /**
     * 创建数据单元格
     */
    private Cell createDataCell(String content) {
        return new Cell()
                .add(createSafeParagraph(content))
                .setTextAlignment(TextAlignment.CENTER)
                .setPadding(6);
    }

    /**
     * 创建风险等级单元格
     */
    private Cell createRiskLevelCell(ContractReview.RiskLevel riskLevel) {
        return new Cell()
                .add(createSafeParagraph(getRiskLevelDisplayName(riskLevel))
                        .setBold()
                        .setFontColor(getRiskLevelColor(riskLevel)))
                .setPadding(6);
    }

    /**
     * 获取状态显示名称
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
     * 获取风险等级显示名称
     */
    private String getRiskLevelDisplayName(ContractReview.RiskLevel riskLevel) {
        if (riskLevel == null) return "未知";
        return switch (riskLevel) {
            case HIGH -> "高风险";
            case MEDIUM -> "中等风险";
            case LOW -> "低风险";
        };
    }

    /**
     * 获取风险等级描述
     */
    private String getRiskLevelDescription(ContractReview.RiskLevel riskLevel) {
        if (riskLevel == null) return "";
        return switch (riskLevel) {
            case HIGH -> "建议谨慎处理，必要时寻求专业法律意见。";
            case MEDIUM -> "存在一定风险，建议关注相关条款。";
            case LOW -> "风险较低，但仍需注意合同执行。";
        };
    }

    /**
     * 获取风险等级对应颜色
     */
    private DeviceRgb getRiskLevelColor(ContractReview.RiskLevel riskLevel) {
        if (riskLevel == null) return GRAY_COLOR;
        return switch (riskLevel) {
            case HIGH -> DANGER_COLOR;
            case MEDIUM -> WARNING_COLOR;
            case LOW -> SUCCESS_COLOR;
        };
    }

    /**
     * 获取风险等级优先级（用于排序）
     */
    private int getRiskLevelPriority(ContractReview.RiskLevel riskLevel) {
        if (riskLevel == null) return 0;
        return switch (riskLevel) {
            case HIGH -> 3;
            case MEDIUM -> 2;
            case LOW -> 1;
        };
    }

    /**
     * 格式化日期时间
     */
    private String formatDateTime(LocalDateTime dateTime) {
        if (dateTime == null) return "未知";
        return dateTime.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
    }

    /**
     * 创建带项目符号的列表（改进版，避免符号显示问题）
     * 
     * @param items 列表项内容
     * @param color 列表项颜色
     * @return 格式化的列表元素
     */
    private com.itextpdf.layout.element.List createBulletList(java.util.List<?> items, DeviceRgb color) {
        com.itextpdf.layout.element.List bulletList = new com.itextpdf.layout.element.List()
                .setSymbolIndent(12)
                .setListSymbol("◦ "); // 使用简单的圆圈符号
        
        for (Object item : items) {
            String itemText = cleanTextForPdf(item.toString());
            if (!itemText.isEmpty()) {
                ListItem listItem = new ListItem();
                listItem.setFontColor(color);
                listItem.setMarginBottom(5);
                
                // 创建段落并设置字体
                Paragraph itemParagraph = createSafeParagraph(itemText)
                        .setFontSize(11)
                        .setMarginBottom(0);
                listItem.add(itemParagraph);
                
                bulletList.add(listItem);
            }
        }
        
        return bulletList;
    }
    
    /**
     * 创建简单的文本列表（不使用符号，避免显示问题）
     * 
     * @param items 列表项内容
     * @param color 列表项颜色
     * @return 格式化的段落列表
     */
    private void addSimpleTextList(Document document, java.util.List<?> items, DeviceRgb color) {
        if (items == null || items.isEmpty()) {
            return;
        }
        
        int index = 1;
        for (Object item : items) {
            String itemText = cleanTextForPdf(item.toString());
            if (!itemText.isEmpty()) {
                Paragraph listItem = createSafeParagraph("")
                        .setFontSize(11)
                        .setMarginLeft(15)
                        .setMarginBottom(6);
                
                // 使用数字编号而不是符号
                listItem.add(new Text(index + ". ").setBold().setFontColor(color))
                        .add(new Text(itemText).setFontColor(color));
                        
                document.add(listItem);
                index++;
            }
        }
    }

    /**
     * 生成合同审查Markdown报告（重构版 - 使用结构化生成和模板渲染）
     *
     * @param contractReview 合同审查记录
     * @return Markdown格式的报告内容
     */
    public String generateMarkdownReport(ContractReview contractReview) {
        log.info("开始生成Markdown报告（结构化版本），审查ID: {}", contractReview.getId());

        try {
            // 1. 生成结构化内容（带降级处理）
            ExecutiveSummaryDto summaryDto = generateStructuredSummaryWithFallback(contractReview);
            DeepAnalysisDto analysisDto = generateStructuredAnalysisWithFallback(contractReview);
            ImprovementSuggestionDto improvementsDto = generateStructuredImprovementsWithFallback(contractReview);
            
            // 2. 使用模板引擎渲染报告
            String renderedReport = templateRenderer.renderContractReviewReport(
                    contractReview, summaryDto, analysisDto, improvementsDto);
            
            log.info("Markdown报告生成成功（结构化版本），审查ID: {}, 文件大小: {} 字符", 
                    contractReview.getId(), renderedReport.length());
            
            return renderedReport;
        } catch (Exception e) {
            log.error("Markdown报告生成失败，审查ID: {}", contractReview.getId(), e);
            throw new RuntimeException("Markdown报告生成失败: " + e.getMessage(), e);
        }
    }
    
    /**
     * 生成执行摘要结构化内容（带降级处理）
     */
    private ExecutiveSummaryDto generateStructuredSummaryWithFallback(ContractReview contractReview) {
        try {
            log.info("尝试生成执行摘要结构化内容...");
            ExecutiveSummaryDto summaryDto = structuredContentGenerator.generateExecutiveSummary(contractReview);
            
            // 校验内容
            if (contentValidator.validateExecutiveSummary(summaryDto)) {
                log.info("✓ 执行摘要生成并校验成功");
                return summaryDto;
            } else {
                log.warn("执行摘要校验失败，使用降级方案");
                return createFallbackExecutiveSummary(contractReview);
            }
        } catch (Exception e) {
            log.warn("执行摘要生成失败: {}, 使用降级方案", e.getMessage());
            return createFallbackExecutiveSummary(contractReview);
        }
    }
    
    /**
     * 生成深度分析结构化内容（带降级处理）
     */
    private DeepAnalysisDto generateStructuredAnalysisWithFallback(ContractReview contractReview) {
        try {
            log.info("尝试生成深度分析结构化内容...");
            DeepAnalysisDto analysisDto = structuredContentGenerator.generateDeepAnalysis(contractReview);
            
            // 校验内容
            if (contentValidator.validateDeepAnalysis(analysisDto)) {
                log.info("✓ 深度分析生成并校验成功");
                return analysisDto;
            } else {
                log.warn("深度分析校验失败，使用降级方案");
                return createFallbackDeepAnalysis(contractReview);
            }
        } catch (Exception e) {
            log.warn("深度分析生成失败: {}, 使用降级方案", e.getMessage());
            return createFallbackDeepAnalysis(contractReview);
        }
    }
    
    /**
     * 生成改进建议结构化内容（带降级处理）
     */
    private ImprovementSuggestionDto generateStructuredImprovementsWithFallback(ContractReview contractReview) {
        try {
            log.info("尝试生成改进建议结构化内容...");
            ImprovementSuggestionDto improvementsDto = structuredContentGenerator.generateImprovementSuggestions(contractReview);
            
            // 校验内容
            if (contentValidator.validateImprovementSuggestions(improvementsDto)) {
                log.info("✓ 改进建议生成并校验成功");
                return improvementsDto;
            } else {
                log.warn("改进建议校验失败，使用降级方案");
                return createFallbackImprovementSuggestions(contractReview);
            }
        } catch (Exception e) {
            log.warn("改进建议生成失败: {}, 使用降级方案", e.getMessage());
            return createFallbackImprovementSuggestions(contractReview);
        }
    }
    
    /**
     * 创建降级版执行摘要
     */
    private ExecutiveSummaryDto createFallbackExecutiveSummary(ContractReview contractReview) {
        log.info("创建降级版执行摘要，审查ID: {}", contractReview.getId());
        
        String riskLevel = contractReview.getRiskLevel() != null ? 
                getRiskLevelDisplayName(contractReview.getRiskLevel()) : "未评估";
        
        List<String> coreRisks = new ArrayList<>();
        if (contractReview.getTotalRisks() != null && contractReview.getTotalRisks() > 0) {
            coreRisks.add("系统共识别 " + contractReview.getTotalRisks() + " 个潜在风险点");
            
            if (contractReview.getRiskClauses() != null && !contractReview.getRiskClauses().isEmpty()) {
                long highRiskCount = contractReview.getRiskClauses().stream()
                        .filter(risk -> risk.getRiskLevel() == ContractReview.RiskLevel.HIGH)
                        .count();
                if (highRiskCount > 0) {
                    coreRisks.add("其中高风险项 " + highRiskCount + " 个，需重点关注");
                }
            }
        }
        
        List<String> actionSuggestions = new ArrayList<>();
        if (contractReview.getRiskLevel() == ContractReview.RiskLevel.HIGH) {
            actionSuggestions.add("建议暂缓签署，立即咨询专业法律顾问");
            actionSuggestions.add("对所有高风险条款进行修订后再签署");
        } else if (contractReview.getRiskLevel() == ContractReview.RiskLevel.MEDIUM) {
            actionSuggestions.add("建议与对方协商修改关键风险条款");
            actionSuggestions.add("在履约过程中加强监控");
        } else {
            actionSuggestions.add("合同风险较低，可考虑签署");
            actionSuggestions.add("仍需注意合同执行过程中的规范性");
        }
        
        return ExecutiveSummaryDto.builder()
                .contractType("合同类型分析暂时不可用")
                .riskLevel(riskLevel)
                .riskReason("基于系统基础分析的风险评估结果")
                .coreRisks(coreRisks)
                .actionSuggestions(actionSuggestions)
                .build();
    }
    
    /**
     * 创建降级版深度分析
     */
    private DeepAnalysisDto createFallbackDeepAnalysis(ContractReview contractReview) {
        log.info("创建降级版深度分析，审查ID: {}", contractReview.getId());
        
        // 返回null，模板会显示"AI深度分析服务暂时不可用"
        return null;
    }
    
    /**
     * 创建降级版改进建议
     */
    private ImprovementSuggestionDto createFallbackImprovementSuggestions(ContractReview contractReview) {
        log.info("创建降级版改进建议，审查ID: {}", contractReview.getId());
        
        List<ImprovementSuggestionDto.Suggestion> suggestions = new ArrayList<>();
        
        suggestions.add(ImprovementSuggestionDto.Suggestion.builder()
                .priority("高")
                .problemDescription("建议获取完整的AI深度分析")
                .suggestedModification("请稍后重新生成报告以获取完整的AI深度分析和改进建议")
                .expectedEffect("获得更专业、详细的合同分析和改进建议")
                .build());
        
        suggestions.add(ImprovementSuggestionDto.Suggestion.builder()
                .priority("高")
                .problemDescription("建议咨询专业法律顾问")
                .suggestedModification("针对识别的风险点，建议聘请专业法律顾问进行详细评估")
                .expectedEffect("确保合同的法律有效性和风险可控性")
                .build());
        
        return ImprovementSuggestionDto.builder()
                .suggestions(suggestions)
                .build();
    }

    /**
     * 添加Markdown报告头部
     */
    private void addMarkdownHeader(StringBuilder md, ContractReview contractReview) {
        md.append("# ").append(REPORT_TITLE).append("\n\n");
        md.append("*Contract Compliance Review Report*\n\n");
        md.append("---\n\n");
        
        // 基本信息表格
        md.append("## 基本信息\n\n");
        md.append("| 项目 | 内容 |\n");
        md.append("|------|------|\n");
        md.append("| 合同文件名 | ").append(contractReview.getOriginalFilename()).append(" |\n");
        md.append("| 审查状态 | ").append(getStatusDisplayName(contractReview.getReviewStatus())).append(" |\n");
        md.append("| 风险等级 | **").append(getRiskLevelDisplayName(contractReview.getRiskLevel())).append("** |\n");
        md.append("| 创建时间 | ").append(formatDateTime(contractReview.getCreatedAt())).append(" |\n");
        
        if (contractReview.getCompletedAt() != null) {
            md.append("| 完成时间 | ").append(formatDateTime(contractReview.getCompletedAt())).append(" |\n");
        }
        
        md.append("| 报告生成时间 | ").append(formatDateTime(LocalDateTime.now())).append(" |\n");
        md.append("\n---\n\n");
    }

    /**
     * 添加Markdown执行摘要
     */
    private void addMarkdownExecutiveSummary(StringBuilder md, ContractReview contractReview, 
                                            EnhancedReportContent enhancedContent) {
        md.append("## 一、执行摘要\n\n");
        md.append("*Executive Summary*\n\n");

        // 如果有DeepSeek增强内容，使用AI生成的摘要
        if (enhancedContent.isHasEnhancedContent() && enhancedContent.getExecutiveSummary() != null) {
            md.append("### 【AI深度分析摘要】\n\n");
            md.append(enhancedContent.getExecutiveSummary()).append("\n\n");
            md.append("---\n\n");
        }

        // 整体评估
        md.append("本次合同审查基于AI智能分析技术，对合同文本进行全面的风险识别和合规性检查。");
        
        if (contractReview.getRiskLevel() != null) {
            String riskLevelText = getRiskLevelDescription(contractReview.getRiskLevel());
            md.append("审查结果显示该合同整体风险等级为：**")
              .append(getRiskLevelDisplayName(contractReview.getRiskLevel()))
              .append("**。").append(riskLevelText);
        }

        if (contractReview.getTotalRisks() != null && contractReview.getTotalRisks() > 0) {
            md.append("共识别出 **")
              .append(contractReview.getTotalRisks())
              .append("** 项潜在风险点，需要重点关注。");
        }
        
        md.append("\n\n");

        // 核心风险提示
        if (contractReview.getReviewResult() != null) {
            Object coreRiskAlerts = contractReview.getReviewResult().get("coreRiskAlerts");
            if (coreRiskAlerts instanceof java.util.List<?> alerts && !alerts.isEmpty()) {
                md.append("### 核心风险提示\n\n");
                for (Object alert : alerts) {
                    md.append("- ⚠️ ").append(alert.toString()).append("\n");
                }
                md.append("\n");
            }
        }
    }

    /**
     * 添加Markdown风险统计
     */
    private void addMarkdownRiskStatistics(StringBuilder md, ContractReview contractReview) {
        md.append("## 二、风险分布统计\n\n");
        md.append("*Risk Statistics*\n\n");

        if (contractReview.getRiskClauses() == null || contractReview.getRiskClauses().isEmpty()) {
            md.append("✅ 未发现具体风险条款。\n\n");
            return;
        }

        // 统计各风险等级数量
        Map<ContractReview.RiskLevel, Long> riskLevelCount = contractReview.getRiskClauses()
                .stream()
                .collect(Collectors.groupingBy(RiskClause::getRiskLevel, Collectors.counting()));

        // 创建统计表格
        md.append("| 风险等级 | 数量 | 占比 |\n");
        md.append("|---------|------|------|\n");

        // 总数
        int totalRisks = contractReview.getRiskClauses().size();

        // 添加各等级统计
        for (ContractReview.RiskLevel level : ContractReview.RiskLevel.values()) {
            long count = riskLevelCount.getOrDefault(level, 0L);
            if (count > 0) {
                double percentage = (double) count / totalRisks * 100;
                md.append("| **").append(getRiskLevelDisplayName(level)).append("** | ")
                  .append(count).append(" | ")
                  .append(String.format("%.1f%%", percentage)).append(" |\n");
            }
        }
        
        md.append("\n");
    }

    /**
     * 添加Markdown风险条款详情
     */
    private void addMarkdownRiskClausesDetails(StringBuilder md, ContractReview contractReview) {
        md.append("## 三、风险条款详情\n\n");
        md.append("*Risk Clause Details*\n\n");

        if (contractReview.getRiskClauses() == null || contractReview.getRiskClauses().isEmpty()) {
            md.append("✅ 未发现具体风险条款。\n\n");
            return;
        }

        // 按风险等级排序（高风险优先）
        List<RiskClause> sortedRiskClauses = contractReview.getRiskClauses().stream()
                .sorted((r1, r2) -> {
                    int priority1 = getRiskLevelPriority(r1.getRiskLevel());
                    int priority2 = getRiskLevelPriority(r2.getRiskLevel());
                    return Integer.compare(priority2, priority1);
                })
                .toList();

        int index = 1;
        for (RiskClause riskClause : sortedRiskClauses) {
            addMarkdownRiskClauseDetail(md, riskClause, index++);
        }
    }

    /**
     * 添加单个风险条款详情（Markdown格式）
     */
    private void addMarkdownRiskClauseDetail(StringBuilder md, RiskClause riskClause, int index) {
        String riskIcon;
        if (riskClause.getRiskLevel() == ContractReview.RiskLevel.HIGH) {
            riskIcon = "🔴";
        } else if (riskClause.getRiskLevel() == ContractReview.RiskLevel.MEDIUM) {
            riskIcon = "🟡";
        } else {
            riskIcon = "🟢";
        }
        
        md.append("### ").append(riskIcon).append(" 风险点 ").append(index)
          .append("：").append(riskClause.getRiskType())
          .append(" [**").append(getRiskLevelDisplayName(riskClause.getRiskLevel())).append("**]\n\n");

        // 风险信息表格
        md.append("| 项目 | 内容 |\n");
        md.append("|------|------|\n");
        md.append("| **风险类型** | ").append(riskClause.getRiskType()).append(" |\n");
        md.append("| **风险等级** | **").append(getRiskLevelDisplayName(riskClause.getRiskLevel())).append("** |\n");
        
        if (riskClause.getClauseText() != null && !riskClause.getClauseText().trim().isEmpty()) {
            md.append("| **条款内容** | ").append(riskClause.getClauseText().replace("\n", "<br>")).append(" |\n");
        }
        
        if (riskClause.getRiskDescription() != null && !riskClause.getRiskDescription().trim().isEmpty()) {
            md.append("| **风险描述** | ").append(riskClause.getRiskDescription().replace("\n", "<br>")).append(" |\n");
        }
        
        if (riskClause.getSuggestion() != null && !riskClause.getSuggestion().trim().isEmpty()) {
            md.append("| **改进建议** | ").append(riskClause.getSuggestion().replace("\n", "<br>")).append(" |\n");
        }
        
        md.append("\n---\n\n");
    }

    /**
     * 添加Markdown AI深度分析
     */
    private void addMarkdownDeepSeekAnalysis(StringBuilder md, ContractReview contractReview, 
                                           EnhancedReportContent enhancedContent) {
        md.append("## 四、AI深度分析\n\n");
        md.append("*AI Deep Analysis*\n\n");

        if (enhancedContent.isHasEnhancedContent() && enhancedContent.getDeepAnalysis() != null) {
            md.append(enhancedContent.getDeepAnalysis()).append("\n\n");
        } else {
            md.append("*AI深度分析内容生成中或暂未生成。*\n\n");
        }
    }

    /**
     * 添加Markdown改进建议
     */
    private void addMarkdownRecommendations(StringBuilder md, ContractReview contractReview, 
                                          EnhancedReportContent enhancedContent) {
        md.append("## 五、改进建议\n\n");
        md.append("*Recommendations*\n\n");

        if (enhancedContent.isHasEnhancedContent() && enhancedContent.getImprovementSuggestions() != null) {
            md.append(enhancedContent.getImprovementSuggestions()).append("\n\n");
        } else if (contractReview.getRiskClauses() != null && !contractReview.getRiskClauses().isEmpty()) {
            md.append("### 基于风险分析的建议\n\n");
            
            // 收集所有建议
            List<String> suggestions = contractReview.getRiskClauses().stream()
                    .filter(rc -> rc.getSuggestion() != null && !rc.getSuggestion().trim().isEmpty())
                    .map(RiskClause::getSuggestion)
                    .distinct()
                    .toList();
            
            if (!suggestions.isEmpty()) {
                int suggestionIndex = 1;
                for (String suggestion : suggestions) {
                    md.append(suggestionIndex++).append(". ").append(suggestion).append("\n");
                }
            } else {
                md.append("暂无具体改进建议。\n");
            }
            
            md.append("\n");
        } else {
            md.append("*暂无改进建议。*\n\n");
        }
    }

    /**
     * 添加Markdown免责声明
     */
    private void addMarkdownDisclaimer(StringBuilder md) {
        md.append("## 免责声明\n\n");
        md.append("*Disclaimer*\n\n");
        md.append("> **重要提示**：\n");
        md.append(">\n");
        md.append("> 本报告由AI智能系统自动生成，仅供参考使用。报告内容基于算法分析，可能存在误差或遗漏。\n");
        md.append(">\n");
        md.append("> 在做出任何法律决策前，强烈建议咨询专业法律顾问。本系统及其运营方对因使用本报告产生的任何后果不承担法律责任。\n");
        md.append(">\n");
        md.append("> 本报告中的所有分析和建议仅代表系统观点，不构成正式法律意见。\n\n");
    }

    /**
     * 添加Markdown页脚
     */
    private void addMarkdownFooter(StringBuilder md, ContractReview contractReview) {
        md.append("---\n\n");
        md.append("**报告生成系统**：").append(SYSTEM_NAME).append("\n\n");
        md.append("**生成时间**：").append(formatDateTime(LocalDateTime.now())).append("\n\n");
        md.append("**审查ID**：").append(contractReview.getId()).append("\n\n");
        md.append("**版本**：v1.0\n\n");
        md.append("---\n\n");
        md.append("*本报告为自动生成，保留所有权利。*\n");
    }

    // ==================== 重构新增：结构化内容转换方法 ====================
    
    /**
     * 将ExecutiveSummaryDto转换为文本格式
     */
    private String convertExecutiveSummaryToText(ExecutiveSummaryDto dto) {
        if (dto == null) {
            return "执行摘要生成失败";
        }
        
        StringBuilder text = new StringBuilder();
        text.append("### 合同性质\n").append(dto.getContractType()).append("\n\n");
        text.append("### 风险等级\n**").append(dto.getRiskLevel()).append("**\n\n");
        text.append(dto.getRiskReason()).append("\n\n");
        
        if (dto.getCoreRisks() != null && !dto.getCoreRisks().isEmpty()) {
            text.append("### 核心风险点\n");
            for (String risk : dto.getCoreRisks()) {
                text.append("- ").append(risk).append("\n");
            }
            text.append("\n");
        }
        
        if (dto.getActionSuggestions() != null && !dto.getActionSuggestions().isEmpty()) {
            text.append("### 行动建议\n");
            for (String suggestion : dto.getActionSuggestions()) {
                text.append("- ").append(suggestion).append("\n");
            }
            text.append("\n");
        }
        
        return text.toString();
    }
    
    /**
     * 将DeepAnalysisDto转换为文本格式
     */
    private String convertDeepAnalysisToText(DeepAnalysisDto dto) {
        if (dto == null) {
            return "深度分析生成失败";
        }
        
        StringBuilder text = new StringBuilder();
        
        // 法律性质分析
        if (dto.getLegalNature() != null) {
            text.append("### 法律性质分析\n");
            text.append("- **合同类型**: ").append(removeDuplicateContent(dto.getLegalNature().getContractType())).append("\n");
            text.append("- **适用法规**: ").append(removeDuplicateContent(dto.getLegalNature().getGoverningLaws())).append("\n");
            text.append("- **法律关系**: ").append(removeDuplicateContent(dto.getLegalNature().getLegalRelationship())).append("\n\n");
        }
        
        // 关键条款解读
        if (dto.getKeyClauses() != null && !dto.getKeyClauses().isEmpty()) {
            text.append("### 关键条款解读\n\n");
            for (DeepAnalysisDto.KeyClauseAnalysis clause : dto.getKeyClauses()) {
                text.append("#### ").append(removeDuplicateContent(clause.getClauseName())).append("\n\n");
                text.append("**条款解读**: ").append(removeDuplicateContent(clause.getInterpretation())).append("\n\n");
                text.append("**风险说明**: ").append(removeDuplicateContent(clause.getRisk())).append("\n\n");
            }
        }
        
        // 法律风险深度评估
        if (dto.getRiskAssessments() != null && !dto.getRiskAssessments().isEmpty()) {
            text.append("### 法律风险评估\n\n");
            for (DeepAnalysisDto.RiskAssessment risk : dto.getRiskAssessments()) {
                text.append("#### ").append(removeDuplicateContent(risk.getRiskCategory())).append(" (").append(removeDuplicateContent(risk.getLevel())).append(")\n\n");
                text.append("**风险描述**: ").append(removeDuplicateContent(risk.getDescription())).append("\n\n");
                text.append("**防范措施**: ").append(removeDuplicateContent(risk.getPrevention())).append("\n\n");
            }
        }
        
        // 合规性检查
        if (dto.getComplianceCheck() != null) {
            text.append("### 合规性检查\n");
            text.append("- **适用法规**: ").append(removeDuplicateContent(dto.getComplianceCheck().getRegulation())).append("\n");
            text.append("- **符合性评估**: ").append(removeDuplicateContent(dto.getComplianceCheck().getConformity())).append("\n");
            text.append("- **合规差距**: ").append(removeDuplicateContent(dto.getComplianceCheck().getGaps())).append("\n\n");
        }
        
        // 商业影响分析
        if (dto.getBusinessImpact() != null) {
            text.append("### 商业影响分析\n");
            text.append("- **受影响方**: ").append(removeDuplicateContent(dto.getBusinessImpact().getParty())).append("\n");
            text.append("- **影响描述**: ").append(removeDuplicateContent(dto.getBusinessImpact().getImpact())).append("\n");
            text.append("- **财务影响**: ").append(removeDuplicateContent(dto.getBusinessImpact().getFinancialImpact())).append("\n\n");
        }
        
        return text.toString();
    }
    
    /**
     * 移除文本中的重复内容
     * 检测并移除AI模型可能生成的重复文本片段
     */
    private String removeDuplicateContent(String content) {
        if (content == null || content.trim().isEmpty()) {
            return content;
        }
        
        // 使用ReportContentValidator的去重方法
        return contentValidator.removeDuplicateContent(content);
    }
    
    /**
     * 将ImprovementSuggestionDto转换为文本格式
     */
    private String convertImprovementSuggestionsToText(ImprovementSuggestionDto dto) {
        if (dto == null || dto.getSuggestions() == null || dto.getSuggestions().isEmpty()) {
            return "改进建议生成失败";
        }
        
        StringBuilder text = new StringBuilder();
        int index = 1;
        
        for (ImprovementSuggestionDto.Suggestion suggestion : dto.getSuggestions()) {
            text.append("### ").append(index++).append(". ").append(removeDuplicateContent(suggestion.getProblemDescription()));
            text.append(" (优先级: ").append(suggestion.getPriority()).append(")\n\n");
            
            text.append("**问题描述**: ").append(removeDuplicateContent(suggestion.getProblemDescription())).append("\n\n");
            text.append("**修改建议**: ").append(removeDuplicateContent(suggestion.getSuggestedModification())).append("\n\n");
            text.append("**预期效果**: ").append(removeDuplicateContent(suggestion.getExpectedEffect())).append("\n\n");
        }
        
        return text.toString();
    }
    
    /**
     * 创建降级的ExecutiveSummaryDto
     */
    private ExecutiveSummaryDto createFallbackExecutiveSummaryDto(ContractReview contractReview) {
        return ExecutiveSummaryDto.builder()
            .contractType("合同类型识别失败")
            .riskLevel(contractReview.getRiskLevel() != null ? getRiskLevelText(contractReview.getRiskLevel()) : "未知")
            .riskReason("AI分析服务暂时不可用，请稍后重试")
            .coreRisks(java.util.List.of("系统已识别" + (contractReview.getTotalRisks() != null ? contractReview.getTotalRisks() : 0) + "个风险点"))
            .actionSuggestions(java.util.List.of("建议咨询专业法律顾问进行详细评估"))
            .build();
    }
    
    /**
     * 创建降级的DeepAnalysisDto
     */
    private DeepAnalysisDto createFallbackDeepAnalysisDto() {
        return DeepAnalysisDto.builder()
            .legalNature(DeepAnalysisDto.LegalNatureAnalysis.builder()
                .contractType("未识别")
                .governingLaws("相关法律法规")
                .legalRelationship("需要人工审查")
                .build())
            .keyClauses(java.util.List.of())
            .riskAssessments(java.util.List.of())
            .complianceCheck(DeepAnalysisDto.ComplianceCheck.builder()
                .regulation("相关法律法规")
                .conformity("需要专业评估")
                .gaps("AI分析服务暂时不可用")
                .build())
            .businessImpact(DeepAnalysisDto.BusinessImpactAnalysis.builder()
                .party("各相关方")
                .impact("需要详细评估")
                .financialImpact("需要专业财务分析")
                .build())
            .build();
    }
    
    /**
     * 创建降级的ImprovementSuggestionDto
     */
    private ImprovementSuggestionDto createFallbackImprovementSuggestionDto() {
        return ImprovementSuggestionDto.builder()
            .suggestions(java.util.List.of(
                ImprovementSuggestionDto.Suggestion.builder()
                    .priority("高")
                    .problemDescription("AI分析服务暂时不可用")
                    .suggestedModification("建议咨询专业法律顾问获取详细的改进建议")
                    .expectedEffect("确保合同的合法性和完整性")
                    .build()
            ))
            .build();
    }
    
    /**
     * 获取风险等级文本
     */
    private String getRiskLevelText(ContractReview.RiskLevel riskLevel) {
        return switch (riskLevel) {
            case HIGH -> "高";
            case MEDIUM -> "中";
            case LOW -> "低";
        };
    }
}
