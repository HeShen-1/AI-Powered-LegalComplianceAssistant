package com.river.LegalAssistant.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
@Schema(description = "基础聊天请求")
public class ChatRequest {

    @NotBlank(message = "消息内容不能为空")
    @Size(max = 5000, message = "消息内容不能超过5000个字符")
    @Schema(description = "用户发送的消息", requiredMode = Schema.RequiredMode.REQUIRED, example = "你好，请介绍一下民法典。")
    private String message;
}
