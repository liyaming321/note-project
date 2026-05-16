package com.knowledgebase.dto;

/**
 * 批量链接导入单项结果。
 *
 * @param url 原始链接
 * @param success 是否成功
 * @param message 结果消息
 * @param preview 导入预览
 */
public record BatchLinkImportItemResponse(
        String url,
        boolean success,
        String message,
        LinkImportPreviewResponse preview
) {
}
