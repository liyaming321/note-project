package com.knowledgebase.dto;

import com.knowledgebase.entity.NoteType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

/**
 * LLM 笔记总结请求。
 *
 * @param provider LLM 供应商
 * @param title 笔记标题
 * @param content 笔记内容
 * @param type 内容格式
 * @param language 代码语言
 * @param categoryNames 可选分类名称列表
 */
public record LlmSummaryRequest(
        String provider,

        @Size(max = 160, message = "笔记标题不能超过160个字符")
        String title,

        @NotBlank(message = "笔记内容不能为空")
        String content,

        @NotNull(message = "内容格式不能为空")
        NoteType type,

        @Size(max = 40, message = "语言名称不能超过40个字符")
        String language,

        List<@Size(max = 80, message = "分类名称不能超过80个字符") String> categoryNames
) {
}
