package com.knowledgebase.dto;

import com.knowledgebase.entity.Note;

/**
 * 知识库问答引用来源。
 *
 * @param noteId 笔记ID
 * @param title 标题
 * @param snippet 命中片段
 * @param url 详情页链接
 */
public record KnowledgeQaCitationResponse(
        Long noteId,
        String title,
        String snippet,
        String url
) {

    /**
     * 从笔记构建引用来源。
     *
     * @param note 笔记
     * @param snippet 片段
     * @return 引用来源
     */
    public static KnowledgeQaCitationResponse from(Note note, String snippet) {
        return new KnowledgeQaCitationResponse(
                note.getId(),
                note.getTitle(),
                snippet,
                "/notes/" + note.getId()
        );
    }
}
