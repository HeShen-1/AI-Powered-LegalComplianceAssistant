package com.river.LegalAssistant.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 统一的API响应包装类
 * 
 * @param <T> 响应数据类型
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "统一API响应格式")
public class ApiResponse<T> {
    
    /**
     * 请求是否成功
     */
    @Schema(description = "请求是否成功", example = "true")
    private boolean success;
    
    /**
     * 响应消息
     */
    @Schema(description = "响应消息", example = "操作成功")
    private String message;
    
    /**
     * 响应数据
     */
    @Schema(description = "响应数据（具体类型由API决定）")
    private T data;
    
    /**
     * 响应时间戳
     */
    @Builder.Default
    @Schema(description = "响应时间戳", example = "2025-10-02T14:30:00")
    private LocalDateTime timestamp = LocalDateTime.now();
    
    /**
     * 错误代码（可选）
     */
    @Schema(description = "错误代码（仅在失败时返回）", example = "VALIDATION_ERROR")
    private String errorCode;
    
    /**
     * 创建成功响应
     * 
     * @param data 响应数据
     * @param <T> 数据类型
     * @return 成功响应
     */
    public static <T> ApiResponse<T> success(T data) {
        return ApiResponse.<T>builder()
                .success(true)
                .data(data)
                .timestamp(LocalDateTime.now())
                .build();
    }
    
    /**
     * 创建成功响应（带消息）
     * 
     * @param data 响应数据
     * @param message 成功消息
     * @param <T> 数据类型
     * @return 成功响应
     */
    public static <T> ApiResponse<T> success(T data, String message) {
        return ApiResponse.<T>builder()
                .success(true)
                .message(message)
                .data(data)
                .timestamp(LocalDateTime.now())
                .build();
    }
    
    /**
     * 创建成功响应（仅消息）
     * 
     * @param message 成功消息
     * @return 成功响应
     */
    public static ApiResponse<Void> success(String message) {
        return ApiResponse.<Void>builder()
                .success(true)
                .message(message)
                .timestamp(LocalDateTime.now())
                .build();
    }
    
    /**
     * 创建错误响应
     * 
     * @param message 错误消息
     * @return 错误响应
     */
    public static ApiResponse<Void> error(String message) {
        return ApiResponse.<Void>builder()
                .success(false)
                .message(message)
                .timestamp(LocalDateTime.now())
                .build();
    }
    
    /**
     * 创建泛型错误响应（无数据）
     * 
     * @param message 错误消息
     * @param <T> 数据类型
     * @return 错误响应
     */
    public static <T> ApiResponse<T> errorGeneric(String message) {
        return ApiResponse.<T>builder()
                .success(false)
                .message(message)
                .timestamp(LocalDateTime.now())
                .build();
    }
    
    /**
     * 创建错误响应（带错误代码）
     * 
     * @param message 错误消息
     * @param errorCode 错误代码
     * @return 错误响应
     */
    public static ApiResponse<Void> error(String message, String errorCode) {
        return ApiResponse.<Void>builder()
                .success(false)
                .message(message)
                .errorCode(errorCode)
                .timestamp(LocalDateTime.now())
                .build();
    }
    
    /**
     * 创建泛型错误响应（带错误代码，无数据）
     * 
     * @param message 错误消息
     * @param errorCode 错误代码
     * @param <T> 数据类型
     * @return 错误响应
     */
    public static <T> ApiResponse<T> errorWithCode(String message, String errorCode) {
        return ApiResponse.<T>builder()
                .success(false)
                .message(message)
                .errorCode(errorCode)
                .timestamp(LocalDateTime.now())
                .build();
    }
    
    /**
     * 创建错误响应（带数据）
     * 
     * @param message 错误消息
     * @param data 错误数据
     * @param <T> 数据类型
     * @return 错误响应
     */
    public static <T> ApiResponse<T> error(String message, T data) {
        return ApiResponse.<T>builder()
                .success(false)
                .message(message)
                .data(data)
                .timestamp(LocalDateTime.now())
                .build();
    }
    
    /**
     * 创建成功响应（无数据）
     * 
     * @return 成功响应
     */
    public static ApiResponse<Void> successVoid() {
        return ApiResponse.<Void>builder()
                .success(true)
                .timestamp(LocalDateTime.now())
                .build();
    }
    
    /**
     * 创建成功响应（无数据，带消息）
     * 
     * @param message 成功消息
     * @return 成功响应
     */
    public static ApiResponse<Void> successVoid(String message) {
        return ApiResponse.<Void>builder()
                .success(true)
                .message(message)
                .timestamp(LocalDateTime.now())
                .build();
    }
    
    /**
     * 创建错误响应（无数据）
     * 
     * @param message 错误消息
     * @return 错误响应
     */
    public static ApiResponse<Void> errorVoid(String message) {
        return ApiResponse.<Void>builder()
                .success(false)
                .message(message)
                .timestamp(LocalDateTime.now())
                .build();
    }
    
    /**
     * 创建错误响应（无数据，带错误代码）
     * 
     * @param message 错误消息
     * @param errorCode 错误代码
     * @return 错误响应
     */
    public static ApiResponse<Void> errorVoid(String message, String errorCode) {
        return ApiResponse.<Void>builder()
                .success(false)
                .message(message)
                .errorCode(errorCode)
                .timestamp(LocalDateTime.now())
                .build();
    }
}
