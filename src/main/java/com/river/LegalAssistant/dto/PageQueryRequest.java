package com.river.LegalAssistant.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

/**
 * 统一的分页查询请求
 */
@Data
@Schema(description = "分页查询请求")
public class PageQueryRequest {
    
    @Min(value = 0, message = "页码不能小于0")
    @Schema(description = "页码，从0开始", example = "0")
    private int page = 0;
    
    @Min(value = 1, message = "每页大小至少为1")
    @Max(value = 100, message = "每页大小不能超过100")
    @Schema(description = "每页大小，最大100", example = "20")
    private int size = 20;
    
    @NotBlank(message = "排序字段不能为空")
    @Schema(description = "排序字段", example = "createdAt")
    private String sortBy = "createdAt";
    
    @Pattern(regexp = "^(asc|desc|ASC|DESC)$", message = "排序方向必须为 asc 或 desc")
    @Schema(description = "排序方向（asc/desc）", example = "desc")
    private String sortDirection = "desc";
    
    /**
     * 转换为Spring Data的Pageable
     */
    public Pageable toPageable() {
        Sort.Direction direction = "asc".equalsIgnoreCase(sortDirection) 
            ? Sort.Direction.ASC 
            : Sort.Direction.DESC;
        return PageRequest.of(page, size, Sort.by(direction, sortBy));
    }
    
    /**
     * 使用默认排序字段创建Pageable
     */
    public static Pageable defaultPageable() {
        return PageRequest.of(0, 20, Sort.by(Sort.Direction.DESC, "createdAt"));
    }
}

