package com.knowledgebase.dto;

import java.util.List;

/**
 * LLM 笔记总结响应。
 *
 * @param provider LLM 供应商
 * @param model 模型名称
 * @param title 建议标题
 * @param summary 摘要
 * @param tags 建议标签
 * @param categoryName 建议分类名称
 * @param categoryId 已匹配分类ID
 */
public record LlmSummaryResponse(
        String provider,
        String model,
        String title,
        String summary,
        List<String> tags,
        String categoryName,
        Long categoryId
) {
}
