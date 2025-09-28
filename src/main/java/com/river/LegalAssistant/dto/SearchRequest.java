package com.river.LegalAssistant.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "相似度搜索请求")
public class SearchRequest {

    @Schema(description = "用于搜索的查询文本", requiredMode = Schema.RequiredMode.REQUIRED, example = "合同中的违约责任")
    private String query;

    @Schema(description = "返回的最大结果数", defaultValue = "5", example = "5")
    private Integer maxResults = 5;
}
