package com.knowledgebase.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import java.util.List;

/**
 * 批量整理应用请求。
 *
 * @param items 待应用的整理项
 */
public record OrganizeApplyRequest(
        @Valid
        @NotEmpty(message = "整理项不能为空")
        @Size(max = 50, message = "单次最多整理50篇笔记")
        List<OrganizeApplyItemRequest> items
) {
}
