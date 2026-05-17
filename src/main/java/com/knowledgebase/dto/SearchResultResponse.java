package com.knowledgebase.dto;

import com.knowledgebase.entity.Note;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 搜索结果响应。
 *
 * @param id 笔记ID
 * @param title 标题
 * @param highlight 高亮片段
 * @param hitFields 命中字段
 * @param type 内容格式
 * @param status 发布状态
 * @param language 代码语言
 * @param noteKind 笔记用途类型
 * @param category 分类
 * @param tags 标签列表
 * @param pinned 是否置顶
 * @param favorite 是否收藏
 * @param sortOrder 自定义排序值
 * @param deleted 是否删除
 * @param createdAt 创建时间
 * @param updatedAt 更新时间
 */
public record SearchResultResponse(
        Long id,
        String title,
        String highlight,
        List<String> hitFields,
        String type,
        String status,
        String language,
        NoteKindResponse noteKind,
        SimpleCategoryResponse category,
        List<TagResponse> tags,
        boolean pinned,
        boolean favorite,
        Long sortOrder,
        boolean archived,
        boolean deleted,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {

    /**
     * 从笔记实体构建搜索结果。
     *
     * @param note 笔记实体
     * @param highlight 高亮片段
     * @param hitFields 命中字段
     * @return 搜索结果响应
     */
    public static SearchResultResponse from(Note note, String highlight, List<String> hitFields) {
        return new SearchResultResponse(
                note.getId(),
                note.getTitle(),
                highlight,
                hitFields,
                note.getType().name(),
                note.getStatus().name(),
                note.getLanguage(),
                NoteKindResponse.from(note.getNoteKind()),
                SimpleCategoryResponse.fromNullable(note.getCategory()),
                note.getTags().stream().map(TagResponse::from).toList(),
                note.isPinned(),
                note.isFavorite(),
                note.getSortOrder(),
                note.isArchived(),
                note.isDeleted(),
                note.getCreatedAt(),
                note.getUpdatedAt()
        );
    }
}
