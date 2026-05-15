package com.knowledgebase.dto;

import com.knowledgebase.entity.Category;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 分类响应。
 *
 * @param id 分类ID
 * @param name 分类名称
 * @param parentId 父分类ID
 * @param children 子分类列表
 * @param createdAt 创建时间
 * @param updatedAt 更新时间
 */
public record CategoryResponse(
        Long id,
        String name,
        Long parentId,
        List<CategoryResponse> children,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {

    /**
     * 从实体转换为响应。
     *
     * @param category 分类实体
     * @param children 子分类列表
     * @return 分类响应
     */
    public static CategoryResponse from(Category category, List<CategoryResponse> children) {
        return new CategoryResponse(
                category.getId(),
                category.getName(),
                category.getParentId(),
                children,
                category.getCreatedAt(),
                category.getUpdatedAt()
        );
    }
}
