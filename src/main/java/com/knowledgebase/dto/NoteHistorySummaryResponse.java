package com.knowledgebase.dto;

import java.time.LocalDateTime;

/**
 * 笔记历史版本列表项。
 *
 * @param noteId 笔记ID
 * @param version 版本号
 * @param title 标题
 * @param createdAt 创建时间
 */
public record NoteHistorySummaryResponse(
        Long noteId,
        Integer version,
        String title,
        LocalDateTime createdAt
) {
}
