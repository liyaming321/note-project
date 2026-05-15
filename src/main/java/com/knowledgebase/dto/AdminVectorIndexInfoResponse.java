package com.knowledgebase.dto;

/**
 * 向量索引维护信息响应。
 *
 * @param vectorIndexPath 向量索引目录
 * @param provider Embedding 供应商
 * @param model 模型名称或路径
 * @param dimension 向量维度
 * @param pooling 池化方式
 * @param normalize 是否归一化
 * @param indexedCount 已索引笔记数量
 * @param configured 是否已配置可用供应商
 * @param available 索引是否可用
 * @param lastRebuiltAt 最近重建时间
 * @param message 状态说明
 */
public record AdminVectorIndexInfoResponse(
        String vectorIndexPath,
        String provider,
        String model,
        Integer dimension,
        String pooling,
        boolean normalize,
        int indexedCount,
        boolean configured,
        boolean available,
        String lastRebuiltAt,
        String message
) {
}
