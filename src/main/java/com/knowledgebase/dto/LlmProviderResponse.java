package com.knowledgebase.dto;

/**
 * LLM 供应商配置响应。
 *
 * @param name 供应商名称
 * @param model 模型名称
 * @param configured 是否已配置 API Key
 */
public record LlmProviderResponse(
        String name,
        String model,
        boolean configured
) {
}
