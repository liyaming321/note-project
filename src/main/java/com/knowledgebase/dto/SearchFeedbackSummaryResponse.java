package com.knowledgebase.dto;

import java.util.List;

/**
 * 搜索反馈汇总响应。
 *
 * @param totalCount 总反馈数
 * @param usefulCount 有用反馈数
 * @param irrelevantCount 不相关反馈数
 * @param recentItems 最近反馈明细
 */
public record SearchFeedbackSummaryResponse(
        int totalCount,
        int usefulCount,
        int irrelevantCount,
        List<SearchFeedbackItemResponse> recentItems
) {
}
