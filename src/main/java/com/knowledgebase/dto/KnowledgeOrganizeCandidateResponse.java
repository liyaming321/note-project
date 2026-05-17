package com.knowledgebase.dto;

import java.util.List;

/**
 * 知识整理候选响应。
 *
 * @param note 笔记列表信息
 * @param reasons 待整理原因
 * @param suggestedTags 建议标签
 * @param suggestedCategory 建议分类
 * @param suggestedSummary 建议摘要
 */
public record KnowledgeOrganizeCandidateResponse(
        NoteListResponse note,
        List<String> reasons,
        List<String> suggestedTags,
        String suggestedCategory,
        String suggestedSummary
) {
}
