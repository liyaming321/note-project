package com.knowledgebase.dto;

import jakarta.validation.constraints.NotNull;

/**
 * 笔记状态更新请求。
 *
 * @param value 状态值
 */
public record NoteStatusRequest(
        @NotNull(message = "状态值不能为空")
        Boolean value
) {
}
