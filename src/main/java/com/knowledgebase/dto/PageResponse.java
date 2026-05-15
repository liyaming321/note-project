package com.knowledgebase.dto;

import java.util.List;
import org.springframework.data.domain.Page;

/**
 * 分页响应结构。
 *
 * @param items 当前页数据
 * @param page 当前页码
 * @param size 每页数量
 * @param totalElements 总记录数
 * @param totalPages 总页数
 * @param first 是否第一页
 * @param last 是否最后一页
 * @param <T> 数据类型
 */
public record PageResponse<T>(
        List<T> items,
        int page,
        int size,
        long totalElements,
        int totalPages,
        boolean first,
        boolean last
) {

    /**
     * 将 Spring Data 分页对象转换为统一分页响应。
     *
     * @param pageData 分页对象
     * @param <T> 数据类型
     * @return 分页响应
     */
    public static <T> PageResponse<T> from(Page<T> pageData) {
        return new PageResponse<>(
                pageData.getContent(),
                pageData.getNumber(),
                pageData.getSize(),
                pageData.getTotalElements(),
                pageData.getTotalPages(),
                pageData.isFirst(),
                pageData.isLast()
        );
    }
}
