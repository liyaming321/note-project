package com.knowledgebase.dto;

import com.knowledgebase.entity.Category;

/**
 * 简化分类响应。
 *
 * @param id 分类ID
 * @param name 分类名称
 * @param parentId 父分类ID
 */
public record SimpleCategoryResponse(Long id, String name, Long parentId) {

    /**
     * 从实体转换为简化响应。
     *
     * @param category 分类实体
     * @return 简化分类响应
     */
    public static SimpleCategoryResponse from(Category category) {
        return new SimpleCategoryResponse(category.getId(), category.getName(), category.getParentId());
    }

    /**
     * 从可空实体转换为简化响应。
     *
     * @param category 分类实体
     * @return 简化分类响应
     */
    public static SimpleCategoryResponse fromNullable(Category category) {
        return category == null ? null : from(category);
    }
}
