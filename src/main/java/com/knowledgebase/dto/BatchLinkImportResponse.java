package com.knowledgebase.dto;

import java.util.List;

/**
 * 批量链接导入预览响应。
 *
 * @param totalCount 总数
 * @param successCount 成功数
 * @param failedCount 失败数
 * @param items 单项结果
 */
public record BatchLinkImportResponse(
        int totalCount,
        int successCount,
        int failedCount,
        List<BatchLinkImportItemResponse> items
) {
}
