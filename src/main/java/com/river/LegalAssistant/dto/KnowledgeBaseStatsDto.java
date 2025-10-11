package com.river.LegalAssistant.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * 知识库统计信息 DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "知识库统计信息")
public class KnowledgeBaseStatsDto {
    
    /**
     * 文档总数
     */
    @Schema(description = "文档总数", example = "150")
    private Long totalDocuments;
    
    /**
     * 文本块总数
     */
    @Schema(description = "文本块总数", example = "12000")
    private Long totalChunks;
    
    /**
     * 分类统计（分类名 -> 文档数量）
     */
    @Schema(description = "分类统计，key为分类名称，value为该分类下的文档数量", 
            example = "{\"法律法规\": 50, \"合同模板\": 30, \"案例判决\": 70}")
    private Map<String, Long> categoryCounts;
}

