package com.river.LegalAssistant.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "文本向量化请求")
public class EmbedTextRequest {

    @Schema(description = "需要向量化的文本", requiredMode = Schema.RequiredMode.REQUIRED, example = "这是一段测试文本。")
    private String text;
}
