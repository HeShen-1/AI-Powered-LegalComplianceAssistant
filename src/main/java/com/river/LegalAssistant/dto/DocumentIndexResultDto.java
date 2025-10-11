package com.river.LegalAssistant.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 文档索引结果 DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class DocumentIndexResultDto {
    
    /**
     * 索引模式（单个文档/批量索引）
     */
    private String indexingMode;
    
    /**
     * 文件路径（单个文档索引时）
     */
    private String filePath;
    
    /**
     * 文本片段数量（单个文档索引时）
     */
    private Integer segmentCount;
    
    /**
     * 耗时（毫秒）
     */
    private String duration;
    
    /**
     * 分类
     */
    private String category;
    
    /**
     * 错误信息
     */
    private String error;
    
    /**
     * 总目录数（批量索引时）
     */
    private Integer totalDirectories;
    
    /**
     * 目录列表（批量索引时）
     */
    private List<String> directories;
    
    /**
     * 是否递归（批量索引时）
     */
    private Boolean recursive;
    
    /**
     * 总文档数（批量索引时）
     */
    private Integer totalDocuments;
    
    /**
     * 成功索引文档数（批量索引时）
     */
    private Integer successfulDocuments;
    
    /**
     * 失败索引文档数（批量索引时）
     */
    private Integer failedDocuments;
    
    /**
     * 总文本片段数（批量索引时）
     */
    private Integer totalSegments;
    
    /**
     * 错误列表（批量索引时）
     */
    private List<String> errors;
    
    /**
     * 详细结果列表（批量索引时）
     */
    private List<DirectoryIndexResult> results;
    
    /**
     * 目录索引结果
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class DirectoryIndexResult {
        private String directory;
        private Boolean success;
        private Integer documents;
        private Integer segments;
        private String duration;
        private String error;
    }
}

