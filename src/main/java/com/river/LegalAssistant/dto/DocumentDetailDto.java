package com.river.LegalAssistant.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 文档详情 DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class DocumentDetailDto {
    
    /**
     * 文档ID
     */
    private String docId;
    
    /**
     * 文件名
     */
    private String fileName;
    
    /**
     * 文档分类
     */
    private String category;
    
    /**
     * 文档描述
     */
    private String description;
    
    /**
     * 文件大小（字节）
     */
    private Long fileSize;
    
    /**
     * 文本块数量
     */
    private Integer chunkCount;
    
    /**
     * 上传时间
     */
    private String uploadTime;
    
    /**
     * 文件哈希值
     */
    private String fileHash;
}

