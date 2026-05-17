package com.knowledgebase.dto;

import com.knowledgebase.entity.NoteKind;
import java.time.LocalDateTime;

/**
 * 笔记用途响应。
 *
 * @param id 用途ID
 * @param name 用途名称
 * @param sortOrder 排序值
 * @param builtIn 是否默认内置
 * @param createdAt 创建时间
 * @param updatedAt 更新时间
 */
public record NoteKindResponse(
        Long id,
        String name,
        Long sortOrder,
        boolean builtIn,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {

    /**
     * 从实体转换为响应。
     *
     * @param noteKind 用途实体
     * @return 用途响应
     */
    public static NoteKindResponse from(NoteKind noteKind) {
        if (noteKind == null) {
            return null;
        }
        return new NoteKindResponse(
                noteKind.getId(),
                noteKind.getName(),
                noteKind.getSortOrder(),
                noteKind.isBuiltIn(),
                noteKind.getCreatedAt(),
                noteKind.getUpdatedAt()
        );
    }
}
