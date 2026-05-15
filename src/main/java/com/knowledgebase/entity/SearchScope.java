package com.knowledgebase.entity;

import com.knowledgebase.exception.BusinessException;
import com.knowledgebase.util.SearchIndexFields;
import java.util.Locale;
import java.util.Map;

/**
 * 搜索范围枚举。
 */
public enum SearchScope {

    ALL("all", new String[]{
            SearchIndexFields.TITLE,
            SearchIndexFields.CONTENT_PLAIN,
            SearchIndexFields.CONTENT_CODE,
            SearchIndexFields.CATEGORY
    }),
    TITLE("title", new String[]{SearchIndexFields.TITLE}),
    CODE("code", new String[]{SearchIndexFields.CONTENT_CODE});

    private static final Map<String, SearchScope> VALUE_MAP = Map.of(
            ALL.value, ALL,
            TITLE.value, TITLE,
            CODE.value, CODE
    );

    private final String value;
    private final String[] fields;

    SearchScope(String value, String[] fields) {
        this.value = value;
        this.fields = fields;
    }

    /**
     * 根据请求参数解析搜索范围。
     *
     * @param value 请求参数
     * @return 搜索范围
     */
    public static SearchScope fromValue(String value) {
        if (value == null || value.isBlank()) {
            return ALL;
        }
        SearchScope scope = VALUE_MAP.get(value.trim().toLowerCase(Locale.ROOT));
        if (scope == null) {
            throw new BusinessException("不支持的搜索范围：" + value);
        }
        return scope;
    }

    /**
     * 获取请求参数值。
     *
     * @return 请求参数值
     */
    public String getValue() {
        return value;
    }

    /**
     * 获取参与搜索的索引字段。
     *
     * @return 索引字段数组
     */
    public String[] getFields() {
        return fields.clone();
    }
}
