package com.knowledgebase.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.util.List;

/**
 * Markdown ZIP 批量导出请求。
 *
 * @param noteIds 笔记ID列表
 */
public record ExportZipRequest(
        @NotEmpty(message = "导出笔记列表不能为空")
        List<@NotNull(message = "笔记ID不能为空") Long> noteIds
) {
}
