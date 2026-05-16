package com.knowledgebase.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import java.util.List;

/**
 * 批量链接导入预览请求。
 *
 * @param urls 网页链接列表
 * @param provider LLM 供应商
 * @param useLlm 是否使用 LLM 整理
 */
public record BatchLinkImportRequest(
        @NotEmpty(message = "链接列表不能为空")
        @Size(max = 20, message = "一次最多导入20个链接")
        List<@Size(max = 1000, message = "链接不能超过1000个字符") String> urls,

        String provider,

        Boolean useLlm
) {
}
