package com.river.LegalAssistant.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
@Schema(description = "RAG增强聊天请求")
public class RagChatRequest {

    @NotBlank(message = "问题内容不能为空")
    @Size(max = 5000, message = "问题内容不能超过5000个字符")
    @Schema(description = "用户提出的问题", requiredMode = Schema.RequiredMode.REQUIRED, example = "民法典中关于合同诈骗是怎么规定的？")
    private String question;

    @Positive(message = "最大结果数必须为正整数")
    @Min(value = 1, message = "最大结果数至少为1")
    @Max(value = 100, message = "最大结果数不能超过100")
    @Schema(description = "检索的最大相关文档数", defaultValue = "5", example = "5")
    private Integer maxResults = 5;
}
