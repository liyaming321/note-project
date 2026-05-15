package com.knowledgebase.exception;

/**
 * 业务异常。
 */
public class BusinessException extends RuntimeException {

    /**
     * 创建业务异常。
     *
     * @param message 异常消息
     */
    public BusinessException(String message) {
        super(message);
    }
}
