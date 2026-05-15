package com.knowledgebase.dto;

import jakarta.validation.constraints.NotEmpty;
import java.util.List;

/**
 * 笔记批量操作请求。
 *
 * @param noteIds 笔记ID列表
 */
public record NoteBatchRequest(
        @NotEmpty(message = "笔记ID列表不能为空")
        List<Long> noteIds
) {
}
