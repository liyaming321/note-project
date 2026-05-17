package com.knowledgebase.dto;

import java.time.LocalDateTime;

/**
 * 搜索调优设置响应。
 *
 * @param keywordWeight 关键词基础权重
 * @param semanticWeight 语义基础权重
 * @param titleHitBoost 标题命中加权
 * @param tagHitBoost 标签命中加权
 * @param pinnedBoost 置顶加权
 * @param favoriteBoost 收藏加权
 * @param recentSevenDaysBoost 近 7 天更新加权
 * @param recentThirtyDaysBoost 近 30 天更新加权
 * @param updatedAt 最近更新时间
 * @param configPath 配置文件路径
 */
public record SearchTuningSettingsResponse(
        double keywordWeight,
        double semanticWeight,
        double titleHitBoost,
        double tagHitBoost,
        double pinnedBoost,
        double favoriteBoost,
        double recentSevenDaysBoost,
        double recentThirtyDaysBoost,
        LocalDateTime updatedAt,
        String configPath
) {
}
