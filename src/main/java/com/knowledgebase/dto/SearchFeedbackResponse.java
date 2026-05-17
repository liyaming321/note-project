package com.knowledgebase.dto;

import java.time.LocalDateTime;

/**
 * 搜索反馈写入响应。
 *
 * @param totalCount 总反馈数
 * @param usefulCount 有用反馈数
 * @param irrelevantCount 不相关反馈数
 * @param message 结果说明
 * @param recordedAt 记录时间
 */
public record SearchFeedbackResponse(
        int totalCount,
        int usefulCount,
        int irrelevantCount,
        String message,
        LocalDateTime recordedAt
) {
}
