package com.river.LegalAssistant.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 文件上传响应 DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UploadResponseDto {
    
    /**
     * 审查任务ID
     */
    private Long reviewId;
    
    /**
     * 审查状态
     */
    private String status;
    
    /**
     * 是否支持分析
     */
    private Boolean supportedAnalysis;
    
    /**
     * 文件哈希值
     */
    private String fileHash;
    
    /**
     * 原始文件名
     */
    private String originalFilename;
    
    /**
     * 文件大小（字节）
     */
    private Long fileSize;
}
