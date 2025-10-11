package com.river.LegalAssistant.exception;

import com.river.LegalAssistant.dto.ApiResponse;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.BindException;
import org.springframework.validation.FieldError;
import org.springframework.validation.ObjectError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.context.request.async.AsyncRequestTimeoutException;

import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 全局异常处理器
 * 统一处理各种异常并返回标准化的ApiResponse
 */
@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    /**
     * 处理业务异常
     */
    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiResponse<Void>> handleBusinessException(BusinessException ex) {
        log.warn("业务异常: {}", ex.getMessage());
        return ResponseEntity.badRequest()
                .body(ApiResponse.error(ex.getMessage(), ex.getErrorCode()));
    }

    /**
     * 处理资源不存在异常
     */
    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleResourceNotFoundException(ResourceNotFoundException ex) {
        log.warn("资源不存在: {}", ex.getMessage());
        return ResponseEntity.notFound()
                .build();
    }

    /**
     * 处理访问拒绝异常
     */
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiResponse<Void>> handleAccessDeniedException(AccessDeniedException ex) {
        log.warn("访问被拒绝: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(ApiResponse.error("访问被拒绝：您没有足够的权限执行此操作", "ACCESS_DENIED"));
    }

    /**
     * 处理 @Valid 参数校验异常 (用于 @RequestBody)
     * 当请求体中的字段不符合校验规则时触发
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Map<String, String>>> handleValidationException(MethodArgumentNotValidException ex) {
        Map<String, String> errors = new HashMap<>();
        
        // 收集所有字段错误
        ex.getBindingResult().getAllErrors().forEach((error) -> {
            if (error instanceof FieldError) {
                String fieldName = ((FieldError) error).getField();
                String errorMessage = error.getDefaultMessage();
                errors.put(fieldName, errorMessage);
            } else {
                // 对象级别的错误
                String objectName = error.getObjectName();
                String errorMessage = error.getDefaultMessage();
                errors.put(objectName, errorMessage);
            }
        });
        
        // 生成友好的错误摘要
        String summary = errors.entrySet().stream()
                .limit(3)  // 只显示前3个错误
                .map(entry -> entry.getKey() + ": " + entry.getValue())
                .collect(Collectors.joining("; "));
        
        log.warn("请求参数校验失败 - {} 个错误: {}", errors.size(), summary);
        
        return ResponseEntity.badRequest()
                .body(ApiResponse.error("请求参数校验失败：" + summary, errors));
    }

    /**
     * 处理绑定异常 (用于 @ModelAttribute 和表单数据)
     */
    @ExceptionHandler(BindException.class)
    public ResponseEntity<ApiResponse<Map<String, String>>> handleBindException(BindException ex) {
        Map<String, String> errors = new HashMap<>();
        
        ex.getBindingResult().getAllErrors().forEach((error) -> {
            if (error instanceof FieldError) {
                String fieldName = ((FieldError) error).getField();
                String errorMessage = error.getDefaultMessage();
                errors.put(fieldName, errorMessage);
            } else {
                String objectName = error.getObjectName();
                String errorMessage = error.getDefaultMessage();
                errors.put(objectName, errorMessage);
            }
        });
        
        log.warn("数据绑定失败 - {} 个错误: {}", errors.size(), errors);
        return ResponseEntity.badRequest()
                .body(ApiResponse.error("数据绑定失败，请检查请求参数", errors));
    }
    
    /**
     * 处理 @Validated 方法参数校验异常 (用于 @RequestParam, @PathVariable)
     * 当单个方法参数（而非对象）不符合校验规则时触发
     */
    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiResponse<Map<String, String>>> handleConstraintViolationException(ConstraintViolationException ex) {
        Map<String, String> errors = new HashMap<>();
        
        for (ConstraintViolation<?> violation : ex.getConstraintViolations()) {
            // 提取参数名称（去掉方法路径前缀）
            String propertyPath = violation.getPropertyPath().toString();
            String fieldName = propertyPath.substring(propertyPath.lastIndexOf('.') + 1);
            String errorMessage = violation.getMessage();
            errors.put(fieldName, errorMessage);
        }
        
        log.warn("约束校验失败 - {} 个错误: {}", errors.size(), errors);
        return ResponseEntity.badRequest()
                .body(ApiResponse.error("参数约束校验失败", errors));
    }

    /**
     * 处理文件上传大小超限异常
     */
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ApiResponse<Void>> handleMaxUploadSizeExceededException(MaxUploadSizeExceededException ex) {
        log.warn("文件上传大小超限: {}", ex.getMessage());
        return ResponseEntity.badRequest()
                .body(ApiResponse.error("文件大小超出限制，请上传较小的文件", "FILE_SIZE_EXCEEDED"));
    }

    /**
     * 处理非法参数异常
     */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiResponse<Void>> handleIllegalArgumentException(IllegalArgumentException ex) {
        log.warn("非法参数: {}", ex.getMessage());
        return ResponseEntity.badRequest()
                .body(ApiResponse.error(ex.getMessage(), "INVALID_ARGUMENT"));
    }

    /**
     * 处理SSE异步请求超时异常
     * 注意：此异常通常发生在SSE连接中，此时响应头已设置为text/event-stream，
     * 不能返回JSON格式的ApiResponse，否则会导致序列化错误
     */
    @ExceptionHandler(AsyncRequestTimeoutException.class)
    public ResponseEntity<Void> handleAsyncRequestTimeoutException(AsyncRequestTimeoutException ex) {
        log.warn("SSE异步请求超时: {}", ex.getMessage());
        // 对于SSE超时，不返回响应体，避免Content-Type冲突
        // SSE连接会自动关闭，前端EventSource会收到error事件
        return ResponseEntity.status(HttpStatus.REQUEST_TIMEOUT).build();
    }

    /**
     * 处理通用运行时异常
     */
    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<ApiResponse<Void>> handleRuntimeException(RuntimeException ex) {
        log.error("运行时异常", ex);
        return ResponseEntity.internalServerError()
                .body(ApiResponse.error("系统内部错误：" + ex.getMessage(), "RUNTIME_ERROR"));
    }

    /**
     * 处理其他所有异常
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleGeneralException(Exception ex) {
        log.error("未处理的异常", ex);
        return ResponseEntity.internalServerError()
                .body(ApiResponse.error("系统内部错误，请稍后重试", "INTERNAL_SERVER_ERROR"));
    }
}