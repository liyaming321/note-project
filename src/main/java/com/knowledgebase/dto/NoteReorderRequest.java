package com.knowledgebase.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.util.List;

/**
 * 笔记拖拽排序请求。
 *
 * @param noteIds 排序后的笔记ID列表
 */
public record NoteReorderRequest(
        @NotEmpty(message = "排序笔记列表不能为空")
        List<@NotNull(message = "笔记ID不能为空") Long> noteIds
) {
}
