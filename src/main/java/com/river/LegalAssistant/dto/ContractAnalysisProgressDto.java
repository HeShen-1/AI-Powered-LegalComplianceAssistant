package com.river.LegalAssistant.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ContractAnalysisProgressDto {
    private Long reviewId;
    private int step; // 0:解析, 1:风险识别, 2:条款分析, 3:生成报告, 4:完成
    private String stage; // "文档解析", "风险识别", "条款分析", "生成报告", "完成"
    private String message;
    private String status; // "processing", "completed", "error"

    public static ContractAnalysisProgressDto of(Long reviewId, int step, String stage, String message) {
        return new ContractAnalysisProgressDto(reviewId, step, stage, message, "processing");
    }
    
    // 可以保留旧的静态方法以兼容
    public static ContractAnalysisProgressDto parsing(Long reviewId, String message) {
        return new ContractAnalysisProgressDto(reviewId, 0, "文档解析", message, "processing");
    }

    public static ContractAnalysisProgressDto analyzing(Long reviewId, String message) {
        return new ContractAnalysisProgressDto(reviewId, 1, "风险识别", message, "processing");
    }

    public static ContractAnalysisProgressDto generatingReport(Long reviewId, String message) {
        return new ContractAnalysisProgressDto(reviewId, 3, "生成报告", message, "processing");
    }
    
    public static ContractAnalysisProgressDto completed(Long reviewId, String message) {
        return new ContractAnalysisProgressDto(reviewId, 4, "完成", message, "completed");
    }

    public static ContractAnalysisProgressDto error(Long reviewId, String message) {
        return new ContractAnalysisProgressDto(reviewId, -1, "错误", message, "error");
    }
}
