package com.knowledgebase.dto;

import java.util.List;

/**
 * 批量整理应用响应。
 *
 * @param updatedCount 更新数量
 * @param notes 更新后的笔记
 * @param message 结果说明
 */
public record OrganizeApplyResponse(
        int updatedCount,
        List<NoteListResponse> notes,
        String message
) {
}
