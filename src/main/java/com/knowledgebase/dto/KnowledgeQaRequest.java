package com.knowledgebase.dto;

import com.knowledgebase.entity.NoteStatus;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.List;

/**
 * 知识库问答请求。
 *
 * @param question 问题
 * @param provider LLM 供应商
 * @param topK 引用数量
 * @param tag 标签筛选
 * @param category 分类筛选
 * @param language 语言筛选
 * @param status 发布状态筛选
 * @param updatedFrom 更新时间开始日期
 * @param updatedTo 更新时间结束日期
 * @param conversationContext 追问上下文
 * @param citationNoteIds 指定引用笔记ID
 * @param strictMode 是否仅基于引用严格回答
 */
public record KnowledgeQaRequest(
        @NotBlank(message = "问题不能为空")
        @Size(max = 500, message = "问题不能超过500个字符")
        String question,

        String provider,

        @Min(value = 1, message = "引用数量不能小于1")
        @Max(value = 8, message = "引用数量不能超过8")
        Integer topK,

        String tag,

        String category,

        String language,

        NoteStatus status,

        LocalDate updatedFrom,

        LocalDate updatedTo,

        @Size(max = 6, message = "追问上下文最多保留6条")
        List<@Size(max = 500, message = "单条追问上下文不能超过500个字符") String> conversationContext,

        @Size(max = 8, message = "引用筛选最多选择8篇笔记")
        List<Long> citationNoteIds,

        Boolean strictMode
) {

    /**
     * 获取安全引用数量。
     *
     * @return 引用数量
     */
    public int safeTopK() {
        if (topK == null) {
            return 5;
        }
        return Math.max(1, Math.min(topK, 8));
    }

    /**
     * 获取安全追问上下文。
     *
     * @return 追问上下文
     */
    public List<String> safeConversationContext() {
        if (conversationContext == null) {
            return List.of();
        }
        return conversationContext.stream()
                .filter(item -> item != null && !item.isBlank())
                .map(String::trim)
                .limit(6)
                .toList();
    }

    /**
     * 获取安全引用笔记ID。
     *
     * @return 引用笔记ID
     */
    public List<Long> safeCitationNoteIds() {
        if (citationNoteIds == null) {
            return List.of();
        }
        return citationNoteIds.stream()
                .filter(java.util.Objects::nonNull)
                .distinct()
                .limit(8)
                .toList();
    }

    /**
     * 是否启用严格模式。
     *
     * @return 是否严格模式
     */
    public boolean strictModeEnabled() {
        return Boolean.TRUE.equals(strictMode);
    }
}
