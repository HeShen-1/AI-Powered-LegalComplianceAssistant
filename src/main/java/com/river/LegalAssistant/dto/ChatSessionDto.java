package com.river.LegalAssistant.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 聊天会话DTO
 * 用于前端展示会话列表
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "聊天会话信息")
public class ChatSessionDto {

    @Schema(description = "会话ID", example = "550e8400-e29b-41d4-a716-446655440000")
    private String id;

    @Schema(description = "会话标题", example = "关于劳动合同的咨询")
    private String title;

    @Schema(description = "最后更新时间", example = "2025-10-09T14:30:00")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime updatedAt;

    @Schema(description = "消息数量", example = "10")
    private Integer messageCount;
}

