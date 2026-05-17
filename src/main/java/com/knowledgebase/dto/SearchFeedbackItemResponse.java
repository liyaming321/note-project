package com.knowledgebase.dto;

import java.time.LocalDateTime;

/**
 * 搜索反馈明细响应。
 *
 * @param noteId 笔记ID
 * @param noteTitle 笔记标题
 * @param keyword 搜索关键词
 * @param mode 搜索模式
 * @param useful 是否有用
 * @param reason 反馈原因
 * @param createdAt 创建时间
 */
public record SearchFeedbackItemResponse(
        Long noteId,
        String noteTitle,
        String keyword,
        String mode,
        boolean useful,
        String reason,
        LocalDateTime createdAt
) {
}
