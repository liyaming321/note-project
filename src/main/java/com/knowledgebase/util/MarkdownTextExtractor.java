package com.knowledgebase.util;

import java.util.regex.Pattern;

/**
 * Markdown 纯文本提取工具。
 */
public final class MarkdownTextExtractor {

    private static final Pattern CODE_FENCE_PATTERN = Pattern.compile("```[\\s\\S]*?```");
    private static final Pattern CODE_FENCE_CONTENT_PATTERN = Pattern.compile("```[^\\r\\n]*\\R([\\s\\S]*?)```");
    private static final Pattern INLINE_CODE_PATTERN = Pattern.compile("`([^`]*)`");
    private static final Pattern MARKDOWN_SYMBOL_PATTERN = Pattern.compile("[#>*_~\\-\\[\\]()!]");
    private static final Pattern LINK_PATTERN = Pattern.compile("\\[([^]]+)]\\([^)]*\\)");
    private static final Pattern MULTI_SPACE_PATTERN = Pattern.compile("\\s+");

    private MarkdownTextExtractor() {
    }

    /**
     * 从 Markdown 或代码内容中提取纯文本。
     *
     * @param content 原始内容
     * @return 纯文本内容
     */
    public static String extract(String content) {
        if (content == null || content.isBlank()) {
            return "";
        }
        String withoutFence = CODE_FENCE_PATTERN.matcher(content).replaceAll(" ");
        String linkText = LINK_PATTERN.matcher(withoutFence).replaceAll("$1");
        String inlineCode = INLINE_CODE_PATTERN.matcher(linkText).replaceAll("$1");
        String plainText = MARKDOWN_SYMBOL_PATTERN.matcher(inlineCode).replaceAll(" ");
        return MULTI_SPACE_PATTERN.matcher(plainText).replaceAll(" ").trim();
    }

    /**
     * 从 Markdown 内容中提取围栏代码块。
     *
     * @param content 原始 Markdown 内容
     * @return 合并后的代码块内容
     */
    public static String extractCodeBlocks(String content) {
        if (content == null || content.isBlank()) {
            return "";
        }
        StringBuilder codeBuilder = new StringBuilder();
        var matcher = CODE_FENCE_CONTENT_PATTERN.matcher(content);
        while (matcher.find()) {
            if (!codeBuilder.isEmpty()) {
                codeBuilder.append('\n');
            }
            codeBuilder.append(matcher.group(1).trim());
        }
        return codeBuilder.toString();
    }
}
