package com.knowledgebase.dto;

import com.knowledgebase.entity.NoteType;
import com.knowledgebase.entity.NoteStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * 笔记保存请求。
 *
 * @param title 标题
 * @param content 内容
 * @param summary 摘要
 * @param type 内容格式
 * @param status 发布状态
 * @param language 代码语言
 * @param noteKindId 笔记用途类型ID
 * @param categoryId 分类ID
 * @param tags 标签名称集合
 * @param pinned 是否置顶
 * @param favorite 是否收藏
 */
public record NoteRequest(
        @NotBlank(message = "笔记标题不能为空")
        @Size(max = 160, message = "笔记标题不能超过160个字符")
        String title,

        @NotBlank(message = "笔记内容不能为空")
        String content,

        @Size(max = 500, message = "摘要不能超过500个字符")
        String summary,

        @NotNull(message = "内容格式不能为空")
        NoteType type,

        NoteStatus status,

        @Size(max = 40, message = "语言名称不能超过40个字符")
        String language,

        Long noteKindId,

        Long categoryId,

        Set<@Size(max = 60, message = "标签名称不能超过60个字符") String> tags,

        Boolean pinned,

        Boolean favorite
) {

    /**
     * 获取安全的标签集合。
     *
     * @return 标签集合
     */
    public Set<String> safeTags() {
        return tags == null ? new LinkedHashSet<>() : tags;
    }
}
