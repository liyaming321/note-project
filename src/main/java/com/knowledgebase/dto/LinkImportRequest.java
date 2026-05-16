package com.knowledgebase.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 链接导入预览请求。
 *
 * @param url 网页链接
 * @param provider LLM 供应商
 */
public record LinkImportRequest(
        @NotBlank(message = "链接不能为空")
        @Size(max = 1000, message = "链接不能超过1000个字符")
        String url,

        String provider
) {
}
