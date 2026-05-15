package com.knowledgebase.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 标签保存请求。
 *
 * @param name 标签名称
 */
public record TagRequest(
        @NotBlank(message = "标签名称不能为空")
        @Size(max = 60, message = "标签名称不能超过60个字符")
        String name
) {
}
