package com.river.LegalAssistant.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 文档上传结果 DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class DocumentUploadResultDto {
    
    /**
     * 上传模式（单个上传/批量上传）
     */
    private String uploadMode;
    
    /**
     * 文件名（单个上传时）
     */
    private String fileName;
    
    /**
     * 文档ID（单个上传时）
     */
    private String docId;
    
    /**
     * 文本块数量（单个上传时）
     */
    private Integer chunkCount;
    
    /**
     * 文档分类
     */
    private String category;
    
    /**
     * 上传时间
     */
    private LocalDateTime uploadTime;
    
    /**
     * 总文件数
     */
    private Integer totalFiles;
    
    /**
     * 成功上传数量
     */
    private Integer successCount;
    
    /**
     * 失败上传数量
     */
    private Integer failedCount;
    
    /**
     * 成功上传的文件名列表
     */
    private List<String> successFiles;
    
    /**
     * 失败上传的文件名列表
     */
    private List<String> failedFiles;
    
    /**
     * 详细信息列表
     */
    private List<DocumentUploadDetail> details;
    
    /**
     * 文档上传详细信息
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class DocumentUploadDetail {
        private String fileName;
        private Boolean success;
        private String docId;
        private Integer chunkCount;
        private String message;
    }
}

