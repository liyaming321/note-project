package com.knowledgebase.dto;

import com.knowledgebase.entity.Note;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 语义搜索结果响应。
 *
 * @param id 笔记ID
 * @param title 标题
 * @param highlight 匹配摘要
 * @param hitFields 命中字段
 * @param semanticSimilarity 语义相似度
 * @param matchReason 匹配原因
 * @param type 笔记类型
 * @param status 发布状态
 * @param language 代码语言
 * @param category 分类
 * @param tags 标签列表
 * @param pinned 是否置顶
 * @param favorite 是否收藏
 * @param sortOrder 自定义排序值
 * @param archived 是否归档
 * @param deleted 是否删除
 * @param createdAt 创建时间
 * @param updatedAt 更新时间
 */
public record SemanticSearchResultResponse(
        Long id,
        String title,
        String highlight,
        List<String> hitFields,
        double semanticSimilarity,
        String matchReason,
        String type,
        String status,
        String language,
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
     * 从笔记实体构建语义搜索结果。
     *
     * @param note 笔记实体
     * @param highlight 匹配摘要
     * @param semanticSimilarity 语义相似度
     * @param matchReason 匹配原因
     * @return 语义搜索结果
     */
    public static SemanticSearchResultResponse from(
            Note note,
            String highlight,
            double semanticSimilarity,
            String matchReason
    ) {
        return new SemanticSearchResultResponse(
                note.getId(),
                note.getTitle(),
                highlight,
                List.of("semantic"),
                semanticSimilarity,
                matchReason,
                note.getType().name(),
                note.getStatus().name(),
                note.getLanguage(),
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
