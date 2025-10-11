package com.river.LegalAssistant.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 文档向量块 DTO
 * 用于返回文档分块后的向量块信息
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class DocumentChunkDto {
    
    /**
     * 块索引（从0开始）
     */
    private Integer index;
    
    /**
     * 块内容
     */
    private String content;
    
    /**
     * 内容长度（字符数）
     */
    private Integer contentLength;
    
    /**
     * Token数（估算值）
     */
    private Integer tokens;
    
    /**
     * 相似度分数（如果有）
     */
    private Double similarity;
    
    /**
     * 块元数据
     */
    private java.util.Map<String, Object> metadata;
}

