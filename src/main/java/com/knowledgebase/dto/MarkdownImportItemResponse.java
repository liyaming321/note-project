package com.knowledgebase.dto;

/**
 * Markdown 单文件导入结果。
 *
 * @param fileName 文件名
 * @param noteId 导入成功后的笔记ID
 * @param title 导入成功后的笔记标题
 * @param success 是否成功
 * @param message 结果消息
 */
public record MarkdownImportItemResponse(
        String fileName,
        Long noteId,
        String title,
        boolean success,
        String message
) {
}
