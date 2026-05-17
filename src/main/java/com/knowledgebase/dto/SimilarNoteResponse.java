package com.knowledgebase.dto;

import com.knowledgebase.entity.Note;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 相似笔记响应。
 *
 * @param id 笔记ID
 * @param title 标题
 * @param summary 摘要
 * @param similarityScore 相似度分数
 * @param reason 推荐原因
 * @param source 推荐来源
 * @param type 内容格式
 * @param status 发布状态
 * @param language 代码语言
 * @param noteKind 笔记用途类型
 * @param category 分类
 * @param tags 标签
 * @param pinned 是否置顶
 * @param favorite 是否收藏
 * @param updatedAt 更新时间
 */
public record SimilarNoteResponse(
        Long id,
        String title,
        String summary,
        double similarityScore,
        String reason,
        String source,
        String type,
        String status,
        String language,
        NoteKindResponse noteKind,
        SimpleCategoryResponse category,
        List<TagResponse> tags,
        boolean pinned,
        boolean favorite,
        LocalDateTime updatedAt
) {

    /**
     * 从笔记实体构建响应。
     *
     * @param note 笔记
     * @param score 分数
     * @param reason 原因
     * @param source 来源
     * @return 响应
     */
    public static SimilarNoteResponse from(Note note, double score, String reason, String source) {
        return new SimilarNoteResponse(
                note.getId(),
                note.getTitle(),
                summary(note),
                score,
                reason,
                source,
                note.getType().name(),
                note.getStatus().name(),
                note.getLanguage(),
                NoteKindResponse.from(note.getNoteKind()),
                SimpleCategoryResponse.fromNullable(note.getCategory()),
                note.getTags().stream().map(TagResponse::from).toList(),
                note.isPinned(),
                note.isFavorite(),
                note.getUpdatedAt()
        );
    }

    /**
     * 生成摘要。
     *
     * @param note 笔记
     * @return 摘要
     */
    private static String summary(Note note) {
        String text = note.getSummary() == null || note.getSummary().isBlank()
                ? note.getContentText()
                : note.getSummary();
        if (text == null || text.isBlank()) {
            return "";
        }
        String compactText = text.replaceAll("\\s+", " ").trim();
        return compactText.length() <= 120 ? compactText : compactText.substring(0, 120) + "...";
    }
}
