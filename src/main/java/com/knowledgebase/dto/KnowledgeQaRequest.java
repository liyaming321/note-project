package com.knowledgebase.dto;

import com.knowledgebase.entity.NoteStatus;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

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

        LocalDate updatedTo
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
}
