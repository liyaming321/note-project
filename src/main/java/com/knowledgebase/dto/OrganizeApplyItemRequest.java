package com.knowledgebase.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

/**
 * 单篇笔记整理应用请求。
 *
 * @param noteId 笔记ID
 * @param summary 摘要
 * @param tags 标签名称
 * @param categoryId 分类ID
 */
public record OrganizeApplyItemRequest(
        @NotNull(message = "笔记ID不能为空")
        Long noteId,

        @Size(max = 500, message = "摘要不能超过500个字符")
        String summary,

        List<@Size(max = 40, message = "标签名称不能超过40个字符") String> tags,

        Long categoryId
) {
}
