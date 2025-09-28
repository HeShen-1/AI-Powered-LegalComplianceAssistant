package com.river.LegalAssistant.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "合同风险分析请求")
public class AnalyzeContractRequest {

    @Schema(description = "需要分析的合同全文内容", requiredMode = Schema.RequiredMode.REQUIRED)
    private String content;
}
