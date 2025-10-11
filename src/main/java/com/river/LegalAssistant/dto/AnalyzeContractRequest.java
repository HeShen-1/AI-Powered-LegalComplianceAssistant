package com.river.LegalAssistant.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
@Schema(description = "合同风险分析请求")
public class AnalyzeContractRequest {

    @NotBlank(message = "合同内容不能为空")
    @Size(min = 10, max = 50000, message = "合同内容长度必须在10到50000个字符之间")
    @Schema(description = "需要分析的合同全文内容", requiredMode = Schema.RequiredMode.REQUIRED)
    private String content;
}
