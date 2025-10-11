package com.river.LegalAssistant.exception;

/**
 * 资源不存在异常
 */
public class ResourceNotFoundException extends RuntimeException {
    
    public ResourceNotFoundException(String message) {
        super(message);
    }
    
    public ResourceNotFoundException(String message, Throwable cause) {
        super(message, cause);
    }
    
    public static ResourceNotFoundException of(String resourceType, Object id) {
        return new ResourceNotFoundException(String.format("%s with id %s not found", resourceType, id));
    }
}