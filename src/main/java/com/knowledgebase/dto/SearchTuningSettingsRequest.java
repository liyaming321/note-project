package com.knowledgebase.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;

/**
 * 搜索调优设置请求。
 *
 * @param keywordWeight 关键词基础权重
 * @param semanticWeight 语义基础权重
 * @param titleHitBoost 标题命中加权
 * @param tagHitBoost 标签命中加权
 * @param pinnedBoost 置顶加权
 * @param favoriteBoost 收藏加权
 * @param recentSevenDaysBoost 近 7 天更新加权
 * @param recentThirtyDaysBoost 近 30 天更新加权
 */
public record SearchTuningSettingsRequest(
        @DecimalMin(value = "0.0", message = "关键词权重不能小于0")
        @DecimalMax(value = "1.0", message = "关键词权重不能大于1")
        Double keywordWeight,

        @DecimalMin(value = "0.0", message = "语义权重不能小于0")
        @DecimalMax(value = "1.0", message = "语义权重不能大于1")
        Double semanticWeight,

        @DecimalMin(value = "0.0", message = "标题加权不能小于0")
        @DecimalMax(value = "0.5", message = "标题加权不能大于0.5")
        Double titleHitBoost,

        @DecimalMin(value = "0.0", message = "标签加权不能小于0")
        @DecimalMax(value = "0.5", message = "标签加权不能大于0.5")
        Double tagHitBoost,

        @DecimalMin(value = "0.0", message = "置顶加权不能小于0")
        @DecimalMax(value = "0.5", message = "置顶加权不能大于0.5")
        Double pinnedBoost,

        @DecimalMin(value = "0.0", message = "收藏加权不能小于0")
        @DecimalMax(value = "0.5", message = "收藏加权不能大于0.5")
        Double favoriteBoost,

        @DecimalMin(value = "0.0", message = "近7天更新加权不能小于0")
        @DecimalMax(value = "0.5", message = "近7天更新加权不能大于0.5")
        Double recentSevenDaysBoost,

        @DecimalMin(value = "0.0", message = "近30天更新加权不能小于0")
        @DecimalMax(value = "0.5", message = "近30天更新加权不能大于0.5")
        Double recentThirtyDaysBoost
) {
}
