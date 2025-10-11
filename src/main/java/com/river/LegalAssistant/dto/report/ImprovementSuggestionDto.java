package com.river.LegalAssistant.dto.report;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 改进建议数据模型
 * 用于结构化接收AI生成的改进建议JSON数据
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ImprovementSuggestionDto {
    
    /**
     * 改进建议列表（2-3条核心建议）
     */
    @JsonProperty("suggestions")
    private List<Suggestion> suggestions;
    
    /**
     * 单个改进建议子模型
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Suggestion {
        /**
         * 优先级（高/中/低）
         */
        @JsonProperty("priority")
        private String priority;
        
        /**
         * 问题描述
         */
        @JsonProperty("problemDescription")
        private String problemDescription;
        
        /**
         * 修改建议
         */
        @JsonProperty("suggestedModification")
        private String suggestedModification;
        
        /**
         * 预期效果
         */
        @JsonProperty("expectedEffect")
        private String expectedEffect;
    }
}

