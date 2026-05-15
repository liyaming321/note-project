package com.knowledgebase.dto;

import java.util.List;

/**
 * 浏览器书签导入响应。
 *
 * @param importedCount 成功导入数量
 * @param failedCount 导入失败数量
 * @param items 单条导入结果
 */
public record BookmarkImportResponse(
        int importedCount,
        int failedCount,
        List<BookmarkImportItemResponse> items
) {
}
