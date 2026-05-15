package com.knowledgebase.dto;

/**
 * 手动重建向量索引响应。
 *
 * @param indexedCount 重建后的索引笔记数量
 * @param vectorIndexPath 向量索引目录
 * @param provider Embedding 供应商
 * @param model 模型名称或路径
 * @param dimension 向量维度
 */
public record AdminVectorReindexResponse(
        int indexedCount,
        String vectorIndexPath,
        String provider,
        String model,
        int dimension
) {
}
