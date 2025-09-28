package com.river.LegalAssistant.service;

import com.itextpdf.kernel.colors.ColorConstants;
import com.itextpdf.kernel.colors.DeviceRgb;
import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfWriter;
import com.itextpdf.layout.Document;
import com.itextpdf.layout.borders.Border;
import com.itextpdf.layout.element.*;
import com.itextpdf.layout.properties.TextAlignment;
import com.itextpdf.layout.properties.UnitValue;
import com.river.LegalAssistant.entity.ContractReview;
import com.river.LegalAssistant.entity.RiskClause;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * PDF合规报告生成服务
 * 用于生成专业、规范的合同审查报告
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ReportGenerationService {

    private static final String REPORT_TITLE = "法律合规智能审查报告";
    private static final String SYSTEM_NAME = "法律合规智能审查助手";
    
    // 颜色定义
    private static final DeviceRgb PRIMARY_COLOR = new DeviceRgb(41, 128, 185);      // 蓝色
    private static final DeviceRgb SUCCESS_COLOR = new DeviceRgb(39, 174, 96);       // 绿色
    private static final DeviceRgb WARNING_COLOR = new DeviceRgb(241, 196, 15);      // 黄色
    private static final DeviceRgb DANGER_COLOR = new DeviceRgb(231, 76, 60);        // 红色
    private static final DeviceRgb GRAY_COLOR = new DeviceRgb(149, 165, 166);        // 灰色

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
            // 创建PDF文档
            PdfWriter writer = new PdfWriter(baos);
            PdfDocument pdf = new PdfDocument(writer);
            Document document = new Document(pdf);
            
            // 添加文档内容
            addReportHeader(document, contractReview);
            addExecutiveSummary(document, contractReview);
            addRiskStatistics(document, contractReview);
            addRiskClausesDetails(document, contractReview);
            addRecommendations(document, contractReview);
            addDisclaimer(document);
            addFooter(document, contractReview);

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
     * 添加报告头部
     */
    private void addReportHeader(Document document, ContractReview contractReview) {
        // 标题
        Paragraph title = new Paragraph(REPORT_TITLE)
                .setFontSize(24)
                .setBold()
                .setTextAlignment(TextAlignment.CENTER)
                .setFontColor(PRIMARY_COLOR)
                .setMarginBottom(10);
        document.add(title);

        // 副标题
        Paragraph subtitle = new Paragraph("Contract Compliance Review Report")
                .setFontSize(12)
                .setItalic()
                .setTextAlignment(TextAlignment.CENTER)
                .setFontColor(GRAY_COLOR)
                .setMarginBottom(20);
        document.add(subtitle);

        // 基本信息表格
        Table infoTable = new Table(UnitValue.createPercentArray(new float[]{1, 2}))
                .setWidth(UnitValue.createPercentValue(100));

        addInfoRow(infoTable, "合同文件名", contractReview.getOriginalFilename());
        addInfoRow(infoTable, "审查状态", getStatusDisplayName(contractReview.getReviewStatus()));
        addInfoRow(infoTable, "风险等级", getRiskLevelDisplayName(contractReview.getRiskLevel()));
        addInfoRow(infoTable, "创建时间", formatDateTime(contractReview.getCreatedAt()));
        
        if (contractReview.getCompletedAt() != null) {
            addInfoRow(infoTable, "完成时间", formatDateTime(contractReview.getCompletedAt()));
        }
        
        addInfoRow(infoTable, "报告生成时间", formatDateTime(LocalDateTime.now()));

        document.add(infoTable);
        document.add(new Paragraph("\n"));
    }

    /**
     * 添加执行摘要
     */
    private void addExecutiveSummary(Document document, ContractReview contractReview) {
        // 摘要标题
        addSectionTitle(document, "一、执行摘要", "Executive Summary");

        // 整体评估
        Paragraph overallAssessment = new Paragraph()
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
                com.itextpdf.layout.element.List coreAlerts = createBulletList(alerts, DANGER_COLOR);
                document.add(coreAlerts);
            }
        }

        document.add(new Paragraph("\n"));
    }

    /**
     * 添加风险统计
     */
    private void addRiskStatistics(Document document, ContractReview contractReview) {
        addSectionTitle(document, "二、风险分布统计", "Risk Statistics");

        if (contractReview.getRiskClauses() == null || contractReview.getRiskClauses().isEmpty()) {
            document.add(new Paragraph("未发现具体风险条款。")
                    .setFontColor(SUCCESS_COLOR)
                    .setMarginBottom(15));
            document.add(new Paragraph("\n"));
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
        document.add(new Paragraph("\n"));
    }

    /**
     * 添加风险条款详情
     */
    private void addRiskClausesDetails(Document document, ContractReview contractReview) {
        addSectionTitle(document, "三、风险条款详情", "Risk Clause Details");

        if (contractReview.getRiskClauses() == null || contractReview.getRiskClauses().isEmpty()) {
            document.add(new Paragraph("未发现具体风险条款。")
                    .setFontColor(SUCCESS_COLOR)
                    .setMarginBottom(15));
            document.add(new Paragraph("\n"));
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

        document.add(new Paragraph("\n"));
    }

    /**
     * 添加单个风险条款详情
     */
    private void addRiskClauseDetail(Document document, RiskClause riskClause, int index) {
        // 风险条款标题
        Paragraph riskTitle = new Paragraph()
                .setFontSize(12)
                .setBold()
                .setMarginBottom(5);

        riskTitle.add("风险点 " + index + "：")
                .add(new Text(riskClause.getRiskType())
                        .setFontColor(PRIMARY_COLOR));

        riskTitle.add("  [")
                .add(new Text(getRiskLevelDisplayName(riskClause.getRiskLevel()))
                        .setFontColor(getRiskLevelColor(riskClause.getRiskLevel()))
                        .setBold())
                .add("]");

        document.add(riskTitle);

        // 风险描述
        if (riskClause.getRiskDescription() != null && !riskClause.getRiskDescription().trim().isEmpty()) {
            Paragraph riskDesc = new Paragraph()
                    .setFontSize(10)
                    .setMarginLeft(15)
                    .setMarginBottom(5);

            riskDesc.add("风险描述：" + riskClause.getRiskDescription());
            document.add(riskDesc);
        }

        // 建议措施
        if (riskClause.getSuggestion() != null && !riskClause.getSuggestion().trim().isEmpty()) {
            Paragraph suggestion = new Paragraph()
                    .setFontSize(10)
                    .setMarginLeft(15)
                    .setMarginBottom(10);

            suggestion.add(new Text("建议措施：").setBold().setFontColor(SUCCESS_COLOR))
                    .add(riskClause.getSuggestion());
            document.add(suggestion);
        }

        // 分隔线
        document.add(new Paragraph("─".repeat(50))
                .setFontColor(GRAY_COLOR)
                .setFontSize(8)
                .setMarginBottom(10));
    }

    /**
     * 添加优先建议
     */
    private void addRecommendations(Document document, ContractReview contractReview) {
        addSectionTitle(document, "四、优先建议", "Priority Recommendations");

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
        document.add(new Paragraph("\n"));
    }

    /**
     * 添加免责声明
     */
    private void addDisclaimer(Document document) {
        addSectionTitle(document, "五、免责声明", "Disclaimer");

        Paragraph disclaimer = new Paragraph()
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
        document.add(new Paragraph("\n"));
    }

    /**
     * 添加页脚
     */
    private void addFooter(Document document, ContractReview contractReview) {
        // 分隔线
        document.add(new Paragraph("═".repeat(80))
                .setFontColor(PRIMARY_COLOR)
                .setFontSize(8)
                .setTextAlignment(TextAlignment.CENTER)
                .setMarginBottom(10));

        // 页脚信息
        Paragraph footer = new Paragraph()
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
        Paragraph title = new Paragraph()
                .setFontSize(14)
                .setBold()
                .setFontColor(PRIMARY_COLOR)
                .setMarginTop(15)
                .setMarginBottom(10);

        title.add(chineseTitle);
        if (englishTitle != null && !englishTitle.isEmpty()) {
            title.add(new Text(" (" + englishTitle + ")")
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
        document.add(new Paragraph(title)
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
                .add(new Paragraph(label).setBold())
                .setBorder(Border.NO_BORDER)
                .setPadding(5)
                .setBackgroundColor(new DeviceRgb(248, 249, 250));

        Cell valueCell = new Cell()
                .add(new Paragraph(value != null ? value : "未知"))
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
                .add(new Paragraph(content).setBold().setFontColor(ColorConstants.WHITE))
                .setBackgroundColor(PRIMARY_COLOR)
                .setTextAlignment(TextAlignment.CENTER)
                .setPadding(8);
    }

    /**
     * 创建数据单元格
     */
    private Cell createDataCell(String content) {
        return new Cell()
                .add(new Paragraph(content))
                .setTextAlignment(TextAlignment.CENTER)
                .setPadding(6);
    }

    /**
     * 创建风险等级单元格
     */
    private Cell createRiskLevelCell(ContractReview.RiskLevel riskLevel) {
        return new Cell()
                .add(new Paragraph(getRiskLevelDisplayName(riskLevel))
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
     * 创建带项目符号的列表（抽取重复代码）
     * 
     * @param items 列表项内容
     * @param color 列表项颜色
     * @return 格式化的列表元素
     */
    private com.itextpdf.layout.element.List createBulletList(java.util.List<?> items, DeviceRgb color) {
        com.itextpdf.layout.element.List bulletList = new com.itextpdf.layout.element.List()
                .setSymbolIndent(12)
                .setListSymbol("• ");
        
        for (Object item : items) {
            ListItem listItem = new ListItem(item.toString());
            listItem.setFontColor(color);
            bulletList.add(listItem);
        }
        
        return bulletList;
    }
}
