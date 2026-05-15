package com.knowledgebase.dto;

/**
 * 浏览器书签单条导入结果。
 *
 * @param title 书签标题
 * @param url 书签地址
 * @param noteId 导入成功后的笔记ID
 * @param success 是否成功
 * @param message 结果消息
 */
public record BookmarkImportItemResponse(
        String title,
        String url,
        Long noteId,
        boolean success,
        String message
) {
}
