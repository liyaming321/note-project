package com.knowledgebase.dto;

import java.util.List;

/**
 * 链接导入预览响应。
 *
 * @param sourceUrl 原始链接
 * @param sourceTitle 原始网页标题
 * @param provider LLM 供应商
 * @param model LLM 模型
 * @param title 建议标题
 * @param summary 摘要
 * @param tags 标签
 * @param categoryName 建议分类名称
 * @param categoryId 已匹配分类ID
 * @param content 预览正文
 */
public record LinkImportPreviewResponse(
        String sourceUrl,
        String sourceTitle,
        String provider,
        String model,
        String title,
        String summary,
        List<String> tags,
        String categoryName,
        Long categoryId,
        String content
) {
}
