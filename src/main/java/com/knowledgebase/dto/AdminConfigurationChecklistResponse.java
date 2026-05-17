package com.knowledgebase.dto;

import java.util.List;

/**
 * 配置中心检查清单响应。
 *
 * @param items 检查项
 * @param offlineReady 纯离线能力是否可用
 * @param llmReady LLM 是否可用
 * @param embeddingReady Embedding 是否可用
 * @param message 状态说明
 */
public record AdminConfigurationChecklistResponse(
        List<ConfigCheckItemResponse> items,
        boolean offlineReady,
        boolean llmReady,
        boolean embeddingReady,
        String message
) {
}
