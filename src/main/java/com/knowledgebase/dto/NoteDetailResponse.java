package com.knowledgebase.dto;

import com.knowledgebase.entity.Note;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 笔记详情响应。
 *
 * @param id 笔记ID
 * @param title 标题
 * @param content 原始内容
 * @param contentText 纯文本内容
 * @param summary 摘要
 * @param type 类型
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
public record NoteDetailResponse(
        Long id,
        String title,
        String content,
        String contentText,
        String summary,
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
     * 从实体转换为详情响应。
     *
     * @param note 笔记实体
     * @return 详情响应
     */
    public static NoteDetailResponse from(Note note) {
        return new NoteDetailResponse(
                note.getId(),
                note.getTitle(),
                note.getContent(),
                note.getContentText(),
                note.getSummary(),
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
