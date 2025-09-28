package com.river.LegalAssistant.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.Map;

@Data
@Schema(description = "文档向量化请求")
public class EmbedDocumentRequest {

    @Schema(description = "需要向量化的文档内容", requiredMode = Schema.RequiredMode.REQUIRED)
    private String content;

    @Schema(description = "附加的元数据，以键值对形式提供", example = "{\"source\": \"manual_upload\", \"author\": \"admin\"}")
    private Map<String, Object> metadata;
}
