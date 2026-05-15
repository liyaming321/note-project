package com.knowledgebase.entity;

import com.knowledgebase.exception.BusinessException;
import java.util.Locale;
import java.util.Map;

/**
 * 搜索模式枚举。
 */
public enum SearchMode {

    EXACT("exact"),
    SEMANTIC("semantic"),
    HYBRID("hybrid");

    private static final Map<String, SearchMode> VALUE_MAP = Map.of(
            EXACT.value, EXACT,
            "keyword", EXACT,
            "fulltext", EXACT,
            SEMANTIC.value, SEMANTIC,
            HYBRID.value, HYBRID
    );

    private final String value;

    SearchMode(String value) {
        this.value = value;
    }

    /**
     * 根据请求参数解析搜索模式。
     *
     * @param value 请求参数
     * @return 搜索模式
     */
    public static SearchMode fromValue(String value) {
        if (value == null || value.isBlank()) {
            return EXACT;
        }
        SearchMode mode = VALUE_MAP.get(value.trim().toLowerCase(Locale.ROOT));
        if (mode == null) {
            throw new BusinessException("不支持的搜索模式：" + value);
        }
        return mode;
    }

    /**
     * 获取请求参数值。
     *
     * @return 请求参数值
     */
    public String getValue() {
        return value;
    }
}
