package com.river.LegalAssistant.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 统一错误响应格式
 */
@Data
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "统一错误响应格式")
public class ErrorResponse {
    
    @Schema(description = "错误码", example = "AI_SERVICE_ERROR")
    private String errorCode;
    
    @Schema(description = "错误消息", example = "AI服务暂时不可用")
    private String message;
    
    @Schema(description = "详细错误信息（可选）")
    private Object details;
    
    @Schema(description = "时间戳", example = "2025-10-02T10:30:45")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime timestamp;
    
    @Schema(description = "请求路径", example = "/api/v1/chat")
    private String path;
    
    @Schema(description = "追踪ID", example = "a1b2c3d4")
    private String traceId;
}

