package com.river.LegalAssistant.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 统一聊天请求DTO
 * 用于整合所有聊天功能的统一入口
 */
@Data
@Schema(description = "统一聊天请求")
public class UnifiedChatRequest {
    
    @NotBlank(message = "消息内容不能为空")
    @Size(max = 8000, message = "消息内容不能超过8000个字符")
    @Schema(description = "用户消息内容", required = true, example = "什么是合同违约？")
    private String message;
    
    @Size(max = 100, message = "会话ID不能超过100个字符")
    @Schema(description = "会话ID，用于启用对话记忆。如果不提供，则不启用记忆功能", example = "user123_session1")
    private String conversationId;
    
    @Schema(description = "是否使用知识库检索（RAG）", defaultValue = "true")
    private boolean useKnowledgeBase = true;
    
    @NotNull(message = "模型类型不能为空")
    @Schema(description = "AI模型类型", defaultValue = "UNIFIED",
            allowableValues = {"BASIC", "ADVANCED", "ADVANCED_RAG", "UNIFIED"})
    private ModelType modelType = ModelType.UNIFIED;
    
    @NotBlank(message = "模型名称不能为空")
    @Schema(description = "AI模型名称（用于记忆存储）", defaultValue = "OLLAMA",
            allowableValues = {"OLLAMA", "DEEPSEEK"})
    private String modelName = "OLLAMA";
    
    @Schema(description = "是否使用流式响应", defaultValue = "false")
    private boolean stream = false;
    
    /**
     * 模型类型枚举
     */
    public enum ModelType {
        /**
         * 基础模型 - 使用本地Ollama + RAG
         */
        BASIC,
        
        /**
         * 高级模型 - 使用DeepSeek Agent + 工具调用
         */
        ADVANCED,
        
        /**
         * Advanced RAG - 使用LangChain4j高级RAG框架
         */
        ADVANCED_RAG,

        /**
         * 统一智能模型 - 后端自动选择最优策略
         */
        UNIFIED
    }
}

