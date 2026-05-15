package com.knowledgebase.dto;

/**
 * 图片上传响应。
 *
 * @param fileName 图片文件名
 * @param url 图片访问地址
 * @param size 文件大小
 */
public record ImageUploadResponse(
        String fileName,
        String url,
        long size
) {
}
