package com.knowledgebase.dto;

import java.util.List;

/**
 * Markdown 批量导入响应。
 *
 * @param importedCount 成功导入数量
 * @param failedCount 导入失败数量
 * @param items 单文件导入结果
 */
public record MarkdownImportResponse(
        int importedCount,
        int failedCount,
        List<MarkdownImportItemResponse> items
) {
}
