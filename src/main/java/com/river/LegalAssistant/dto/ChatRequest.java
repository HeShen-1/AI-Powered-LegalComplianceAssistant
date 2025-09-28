package com.river.LegalAssistant.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "基础聊天请求")
public class ChatRequest {

    @Schema(description = "用户发送的消息", requiredMode = Schema.RequiredMode.REQUIRED, example = "你好，请介绍一下民法典。")
    private String message;
}
