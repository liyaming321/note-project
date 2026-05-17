package com.knowledgebase.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 笔记用途保存请求。
 *
 * @param name 用途名称
 * @param sortOrder 排序值
 */
public record NoteKindRequest(
        @NotBlank(message = "用途名称不能为空")
        @Size(max = 80, message = "用途名称不能超过80个字符")
        String name,

        Long sortOrder
) {
}
