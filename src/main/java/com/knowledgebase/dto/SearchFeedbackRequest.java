package com.knowledgebase.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * 搜索结果反馈请求。
 *
 * @param noteId 笔记ID
 * @param keyword 搜索关键词
 * @param mode 搜索模式
 * @param useful 是否有用
 * @param reason 反馈原因
 */
public record SearchFeedbackRequest(
        @NotNull(message = "笔记ID不能为空")
        Long noteId,

        @Size(max = 500, message = "搜索关键词不能超过500个字符")
        String keyword,

        @Size(max = 40, message = "搜索模式不能超过40个字符")
        String mode,

        @NotNull(message = "反馈类型不能为空")
        Boolean useful,

        @Size(max = 500, message = "反馈原因不能超过500个字符")
        String reason
) {
}
