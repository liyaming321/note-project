package com.knowledgebase.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 分类保存请求。
 *
 * @param name 分类名称
 * @param parentId 父分类ID
 */
public record CategoryRequest(
        @NotBlank(message = "分类名称不能为空")
        @Size(max = 80, message = "分类名称不能超过80个字符")
        String name,
        Long parentId
) {
}
