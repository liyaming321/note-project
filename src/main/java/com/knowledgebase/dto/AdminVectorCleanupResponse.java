package com.knowledgebase.dto;

/**
 * 向量索引清理响应。
 *
 * @param removedCount 清理数量
 * @param indexedCount 清理后索引数量
 * @param message 状态说明
 */
public record AdminVectorCleanupResponse(
        int removedCount,
        int indexedCount,
        String message
) {
}
