package com.river.LegalAssistant.exception;

/**
 * 向量数据库异常
 * 当向量存储操作失败时抛出
 */
public class VectorStoreException extends BusinessException {
    
    public VectorStoreException(String message) {
        super(message, "VECTOR_STORE_ERROR");
    }
    
    public VectorStoreException(String message, Throwable cause) {
        super(message, "VECTOR_STORE_ERROR", cause);
    }
}

