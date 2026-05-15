package com.knowledgebase.dto;

/**
 * Embedding 供应商配置响应。
 *
 * @param name 供应商名称
 * @param model 模型名称或路径
 * @param configured 是否已完整配置
 * @param message 配置状态说明
 */
public record EmbeddingProviderResponse(
        String name,
        String model,
        boolean configured,
        String message
) {
}
