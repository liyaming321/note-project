package com.knowledgebase.dto;

import com.knowledgebase.entity.NoteStatus;
import jakarta.validation.constraints.NotNull;

/**
 * 笔记发布状态更新请求。
 *
 * @param status 发布状态
 */
public record NotePublishStatusRequest(
        @NotNull(message = "发布状态不能为空")
        NoteStatus status
) {
}
