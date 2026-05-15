package com.knowledgebase.exception;

/**
 * 资源不存在异常。
 */
public class ResourceNotFoundException extends RuntimeException {

    /**
     * 创建资源不存在异常。
     *
     * @param message 异常消息
     */
    public ResourceNotFoundException(String message) {
        super(message);
    }
}
