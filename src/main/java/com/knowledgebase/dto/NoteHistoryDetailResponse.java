package com.knowledgebase.dto;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 笔记历史版本详情。
 *
 * @param noteId 笔记ID
 * @param version 版本号
 * @param title 标题
 * @param content 原始内容
 * @param contentText 纯文本内容
 * @param type 类型
 * @param language 语言
 * @param categoryId 分类ID
 * @param categoryName 分类名称
 * @param tags 标签名称列表
 * @param createdAt 创建时间
 */
public record NoteHistoryDetailResponse(
        Long noteId,
        Integer version,
        String title,
        String content,
        String contentText,
        String type,
        String language,
        Long categoryId,
        String categoryName,
        List<String> tags,
        LocalDateTime createdAt
) {
}
