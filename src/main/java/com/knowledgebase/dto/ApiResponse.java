package com.knowledgebase.dto;

import java.time.LocalDateTime;

/**
 * API 统一响应结构。
 *
 * @param success 是否成功
 * @param message 响应消息
 * @param data 响应数据
 * @param timestamp 响应时间
 * @param <T> 数据类型
 */
public record ApiResponse<T>(
        boolean success,
        String message,
        T data,
        LocalDateTime timestamp
) {

    /**
     * 构建成功响应。
     *
     * @param data 响应数据
     * @param <T> 数据类型
     * @return 成功响应
     */
    public static <T> ApiResponse<T> success(T data) {
        return new ApiResponse<>(true, "操作成功", data, LocalDateTime.now());
    }

    /**
     * 构建成功响应。
     *
     * @param message 响应消息
     * @param data 响应数据
     * @param <T> 数据类型
     * @return 成功响应
     */
    public static <T> ApiResponse<T> success(String message, T data) {
        return new ApiResponse<>(true, message, data, LocalDateTime.now());
    }

    /**
     * 构建失败响应。
     *
     * @param message 错误消息
     * @param <T> 数据类型
     * @return 失败响应
     */
    public static <T> ApiResponse<T> error(String message) {
        return new ApiResponse<>(false, message, null, LocalDateTime.now());
    }
}
