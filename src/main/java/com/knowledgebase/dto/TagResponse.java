package com.knowledgebase.dto;

import com.knowledgebase.entity.Tag;
import java.time.LocalDateTime;

/**
 * 标签响应。
 *
 * @param id 标签ID
 * @param name 标签名称
 * @param createdAt 创建时间
 * @param updatedAt 更新时间
 */
public record TagResponse(
        Long id,
        String name,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {

    /**
     * 从实体转换为响应。
     *
     * @param tag 标签实体
     * @return 标签响应
     */
    public static TagResponse from(Tag tag) {
        return new TagResponse(tag.getId(), tag.getName(), tag.getCreatedAt(), tag.getUpdatedAt());
    }
}
