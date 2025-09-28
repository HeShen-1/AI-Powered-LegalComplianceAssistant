package com.river.LegalAssistant.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "RAG增强聊天请求")
public class RagChatRequest {

    @Schema(description = "用户提出的问题", requiredMode = Schema.RequiredMode.REQUIRED, example = "民法典中关于合同诈骗是怎么规定的？")
    private String question;

    @Schema(description = "检索的最大相关文档数", defaultValue = "5", example = "5")
    private Integer maxResults = 5;
}
