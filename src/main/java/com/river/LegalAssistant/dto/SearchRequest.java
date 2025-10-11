package com.river.LegalAssistant.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
@Schema(description = "相似度搜索请求")
public class SearchRequest {

    @NotBlank(message = "查询文本不能为空")
    @Size(max = 1000, message = "查询文本不能超过1000个字符")
    @Schema(description = "用于搜索的查询文本", requiredMode = Schema.RequiredMode.REQUIRED, example = "合同中的违约责任")
    private String query;

    @Positive(message = "最大结果数必须为正整数")
    @Min(value = 1, message = "最大结果数至少为1")
    @Max(value = 100, message = "最大结果数不能超过100")
    @Schema(description = "返回的最大结果数", defaultValue = "5", example = "5")
    private Integer maxResults = 5;
}
