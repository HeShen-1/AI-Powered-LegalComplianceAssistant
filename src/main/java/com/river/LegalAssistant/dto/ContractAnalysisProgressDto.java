package com.river.LegalAssistant.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.*;

/**
 * 合同分析进度DTO
 * 用于SSE推送分析进度信息
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "合同分析进度信息")
public class ContractAnalysisProgressDto {

    @Schema(description = "分析阶段", examples = {"PARSING", "ANALYZING", "GENERATING_REPORT", "COMPLETED", "ERROR"})
    private String stage;

    @Schema(description = "进度百分比", example = "75")
    private Integer progress;

    @Schema(description = "当前步骤描述", example = "正在进行风险分析...")
    private String message;

    @Schema(description = "分析结果ID", example = "123")
    private Long reviewId;

    @Schema(description = "错误信息（如有）", example = "文件解析失败")
    private String error;

    @Schema(description = "时间戳", example = "2024-01-01T12:00:00")
    private String timestamp;

    @Schema(description = "是否完成", example = "false")
    private Boolean completed;

    /**
     * 分析阶段枚举
     */
    @Getter
    public enum Stage {
        PARSING("文件解析中"),
        ANALYZING("风险分析中"), 
        GENERATING_REPORT("生成报告中"),
        COMPLETED("分析完成"),
        ERROR("分析失败");

        private final String description;

        Stage(String description) {
            this.description = description;
        }

    }

    /**
     * 创建解析阶段的进度
     */
    public static ContractAnalysisProgressDto parsing(Long reviewId, String message) {
        return ContractAnalysisProgressDto.builder()
            .stage(Stage.PARSING.name())
            .progress(20)
            .message(message)
            .reviewId(reviewId)
            .completed(false)
            .timestamp(java.time.LocalDateTime.now().toString())
            .build();
    }

    /**
     * 创建分析阶段的进度
     */
    public static ContractAnalysisProgressDto analyzing(Long reviewId, String message) {
        return ContractAnalysisProgressDto.builder()
            .stage(Stage.ANALYZING.name())
            .progress(60)
            .message(message)
            .reviewId(reviewId)
            .completed(false)
            .timestamp(java.time.LocalDateTime.now().toString())
            .build();
    }

    /**
     * 创建报告生成阶段的进度
     */
    public static ContractAnalysisProgressDto generatingReport(Long reviewId, String message) {
        return ContractAnalysisProgressDto.builder()
            .stage(Stage.GENERATING_REPORT.name())
            .progress(90)
            .message(message)
            .reviewId(reviewId)
            .completed(false)
            .timestamp(java.time.LocalDateTime.now().toString())
            .build();
    }

    /**
     * 创建完成状态的进度
     */
    public static ContractAnalysisProgressDto completed(Long reviewId, String message) {
        return ContractAnalysisProgressDto.builder()
            .stage(Stage.COMPLETED.name())
            .progress(100)
            .message(message)
            .reviewId(reviewId)
            .completed(true)
            .timestamp(java.time.LocalDateTime.now().toString())
            .build();
    }

    /**
     * 创建错误状态的进度
     */
    public static ContractAnalysisProgressDto error(Long reviewId, String errorMessage) {
        return ContractAnalysisProgressDto.builder()
            .stage(Stage.ERROR.name())
            .progress(0)
            .message("分析过程中发生错误")
            .reviewId(reviewId)
            .error(errorMessage)
            .completed(true)
            .timestamp(java.time.LocalDateTime.now().toString())
            .build();
    }
}
