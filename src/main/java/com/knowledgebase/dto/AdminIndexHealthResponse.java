package com.knowledgebase.dto;

/**
 * 索引健康检查响应。
 *
 * @param databaseActiveCount 数据库有效笔记数量
 * @param searchIndexedCount 全文索引文档数量
 * @param vectorIndexedCount 向量索引文档数量
 * @param searchHealthy 全文索引是否健康
 * @param vectorHealthy 向量索引是否健康
 * @param message 状态说明
 */
public record AdminIndexHealthResponse(
        long databaseActiveCount,
        int searchIndexedCount,
        int vectorIndexedCount,
        boolean searchHealthy,
        boolean vectorHealthy,
        String message
) {
}
