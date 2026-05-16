package com.knowledgebase.dto;

import java.util.List;

/**
 * 知识库问答响应。
 *
 * @param answer 回答
 * @param provider LLM 供应商
 * @param model 模型
 * @param citations 引用来源
 */
public record KnowledgeQaResponse(
        String answer,
        String provider,
        String model,
        List<KnowledgeQaCitationResponse> citations
) {
}
