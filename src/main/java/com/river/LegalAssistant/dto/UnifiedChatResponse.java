package com.river.LegalAssistant.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 统一聊天响应DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "统一聊天响应")
public class UnifiedChatResponse {
    
    @Schema(description = "用户的原始问题")
    private String question;
    
    @Schema(description = "AI生成的回答")
    private String answer;
    
    @Schema(description = "会话ID")
    private String conversationId;
    
    @Schema(description = "使用的模型类型")
    private String modelType;
    
    @Schema(description = "使用的具体模型名称")
    private String modelName;
    
    @Schema(description = "是否使用了知识库")
    private boolean usedKnowledgeBase;
    
    @Schema(description = "是否找到了相关知识")
    private boolean hasKnowledgeMatch;
    
    @Schema(description = "知识来源数量")
    private int sourceCount;
    
    @Schema(description = "知识来源列表")
    private List<String> sources;
    
    @Schema(description = "是否启用了对话记忆")
    private boolean memoryEnabled;
    
    @Schema(description = "响应类型")
    private String responseType;
    
    @Schema(description = "响应时间戳")
    private LocalDateTime timestamp;
    
    @Schema(description = "处理耗时（毫秒）")
    private Long duration;
    
    @Schema(description = "额外的元数据")
    private Map<String, Object> metadata;
}

