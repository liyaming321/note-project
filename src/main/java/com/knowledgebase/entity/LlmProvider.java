package com.knowledgebase.entity;

import java.util.Locale;

/**
 * LLM 服务供应商。
 */
public enum LlmProvider {

    BAILIAN,
    DEEPSEEK;

    /**
     * 从文本解析供应商。
     *
     * @param value 供应商文本
     * @return 供应商
     */
    public static LlmProvider fromValue(String value) {
        if (value == null || value.isBlank()) {
            return BAILIAN;
        }
        return switch (value.trim().toLowerCase(Locale.ROOT)) {
            case "deepseek" -> DEEPSEEK;
            case "bailian", "aliyun", "dashscope" -> BAILIAN;
            default -> throw new IllegalArgumentException("不支持的 LLM 供应商：" + value);
        };
    }
}
