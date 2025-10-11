package com.river.LegalAssistant.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 索引统计信息 DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class IndexingStatisticsDto {
    
    /**
     * 总文本片段数
     */
    private Long totalSegments;
    
    /**
     * 最后更新时间
     */
    private String lastUpdate;
    
    /**
     * 存储类型
     */
    private String storeType;
    
    /**
     * 文本块大小
     */
    private Integer chunkSize;
    
    /**
     * 文本块重叠
     */
    private Integer chunkOverlap;
}

