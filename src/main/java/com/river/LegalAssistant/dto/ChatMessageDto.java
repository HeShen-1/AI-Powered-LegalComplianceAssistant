package com.river.LegalAssistant.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * 聊天消息DTO
 * 用于前端展示消息内容
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "聊天消息信息")
public class ChatMessageDto {

    @Schema(description = "消息ID", example = "1")
    private Long id;

    @Schema(description = "角色", example = "user", allowableValues = {"user", "assistant"})
    private String role;

    @Schema(description = "消息内容", example = "请问劳动合同的试用期最长是多久？")
    private String content;

    @Schema(description = "元数据（如RAG来源等）", example = "{\"model\": \"qwen\", \"sourceCount\": 3}")
    private Map<String, Object> metadata;

    @Schema(description = "创建时间", example = "2025-10-09T14:30:00")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createdAt;
}

