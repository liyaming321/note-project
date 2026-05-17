package com.knowledgebase.dto;

import java.time.LocalDateTime;

/**
 * LLM 连接测试响应。
 *
 * @param provider 供应商
 * @param model 模型
 * @param success 是否成功
 * @param message 测试结果
 * @param testedAt 测试时间
 */
public record LlmProviderTestResponse(
        String provider,
        String model,
        boolean success,
        String message,
        LocalDateTime testedAt
) {
}
