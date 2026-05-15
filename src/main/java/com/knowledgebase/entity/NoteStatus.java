package com.knowledgebase.entity;

import java.util.Locale;

/**
 * 笔记发布状态。
 */
public enum NoteStatus {
    DRAFT,
    PUBLISHED;

    /**
     * 从请求值解析状态。
     *
     * @param value 请求值
     * @return 笔记状态
     */
    public static NoteStatus fromValue(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return NoteStatus.valueOf(value.trim().toUpperCase(Locale.ROOT));
    }
}
