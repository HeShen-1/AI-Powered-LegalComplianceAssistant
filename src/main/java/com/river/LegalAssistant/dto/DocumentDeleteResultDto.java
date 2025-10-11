package com.river.LegalAssistant.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 文档删除结果 DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class DocumentDeleteResultDto {
    
    /**
     * 删除模式（单个删除/批量删除）
     */
    private String deletionMode;
    
    /**
     * 文档ID（单个删除时）
     */
    private String docId;
    
    /**
     * 总请求删除数量
     */
    private Integer totalRequested;
    
    /**
     * 成功删除数量
     */
    private Integer successCount;
    
    /**
     * 失败删除数量
     */
    private Integer failedCount;
    
    /**
     * 成功删除的文档ID列表
     */
    private List<String> successDocs;
    
    /**
     * 失败删除的文档ID列表
     */
    private List<String> failedDocs;
    
    /**
     * 详细信息列表（批量删除时）
     */
    private List<DocumentDeleteDetail> details;
    
    /**
     * 文档删除详细信息
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class DocumentDeleteDetail {
        private String docId;
        private Boolean success;
        private String message;
    }
}

