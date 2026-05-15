package com.knowledgebase.dto;

/**
 * 手动重建索引响应。
 *
 * @param indexedCount 重建后的索引笔记数量
 * @param indexPath 索引目录
 */
public record AdminReindexResponse(
        int indexedCount,
        String indexPath
) {
}
