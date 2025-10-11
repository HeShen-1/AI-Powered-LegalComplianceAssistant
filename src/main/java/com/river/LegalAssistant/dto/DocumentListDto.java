package com.river.LegalAssistant.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 文档列表响应 DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "文档列表响应")
public class DocumentListDto {
    
    /**
     * 文档列表（前端期望的字段名为content）
     */
    @Schema(description = "文档信息列表")
    private List<DocumentInfo> content;
    
    /**
     * 总记录数
     */
    @Schema(description = "文档总数", example = "150")
    private Long totalElements;
    
    /**
     * 总页数
     */
    @Schema(description = "总页数", example = "8")
    private Integer totalPages;
    
    /**
     * 当前页码
     */
    @Schema(description = "当前页码（从0开始）", example = "0")
    private Integer currentPage;
    
    /**
     * 每页大小
     */
    @Schema(description = "每页文档数量", example = "20")
    private Integer pageSize;
    
    /**
     * 文档信息
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    @Schema(description = "单个文档信息")
    public static class DocumentInfo {
        @Schema(description = "文档唯一标识", example = "123")
        private String id;
        
        @Schema(description = "文件名", example = "民法典.pdf")
        private String filename;
        
        @Schema(description = "文档分类", example = "法律法规")
        private String category;
        
        @Schema(description = "文档描述", example = "2021年1月1日施行的民法典全文")
        private String description;
        
        @Schema(description = "文件大小（字节）", example = "1048576")
        private Long size;
        
        @Schema(description = "文本块数量", example = "120")
        private Integer chunksCount;
        
        @Schema(description = "上传时间", example = "2025-10-02T14:30:00")
        private String uploadedAt;
    }
}

