package com.river.LegalAssistant.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
@Schema(description = "文本向量化请求")
public class EmbedTextRequest {

    @NotBlank(message = "文本内容不能为空")
    @Size(max = 10000, message = "文本内容不能超过10000个字符")
    @Schema(description = "需要向量化的文本", requiredMode = Schema.RequiredMode.REQUIRED, example = "这是一段测试文本。")
    private String text;
}
