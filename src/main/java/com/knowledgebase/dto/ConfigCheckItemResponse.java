package com.knowledgebase.dto;

/**
 * 配置检查项响应。
 *
 * @param key 检查项键
 * @param label 检查项名称
 * @param configured 是否已配置
 * @param status 状态
 * @param detail 详情说明
 */
public record ConfigCheckItemResponse(
        String key,
        String label,
        boolean configured,
        String status,
        String detail
) {
}
