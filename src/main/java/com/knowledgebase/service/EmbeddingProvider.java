package com.knowledgebase.service;

import java.util.List;

/**
 * Embedding 向量生成供应商。
 */
public interface EmbeddingProvider {

    /**
     * 获取供应商名称。
     *
     * @return 供应商名称
     */
    String name();

    /**
     * 获取模型名称或路径。
     *
     * @return 模型名称或路径
     */
    String model();

    /**
     * 获取池化方式。
     *
     * @return 池化方式
     */
    String pooling();

    /**
     * 是否对向量做归一化。
     *
     * @return 是否归一化
     */
    boolean normalize();

    /**
     * 判断供应商是否已配置。
     *
     * @return 是否已配置
     */
    boolean configured();

    /**
     * 获取配置状态说明。
     *
     * @return 配置状态说明
     */
    String statusMessage();

    /**
     * 生成文本向量。
     *
     * @param text 文本
     * @return 向量
     */
    float[] embed(String text);

    /**
     * 批量生成文本向量。
     *
     * @param texts 文本列表
     * @return 向量列表
     */
    default List<float[]> embedAll(List<String> texts) {
        return texts.stream().map(this::embed).toList();
    }

    /**
     * 获取批量生成向量的建议批次大小。
     *
     * @return 批次大小
     */
    default int batchSize() {
        return 1;
    }
}
