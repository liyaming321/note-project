package com.knowledgebase.service;

import com.knowledgebase.dto.LinkImportPreviewResponse;
import com.knowledgebase.dto.LinkImportRequest;
import com.knowledgebase.dto.LlmSummaryRequest;
import com.knowledgebase.dto.LlmSummaryResponse;
import com.knowledgebase.entity.NoteType;
import com.knowledgebase.exception.BusinessException;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Locale;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.springframework.stereotype.Service;

/**
 * 网页链接导入预览服务。
 */
@Service
public class LinkImportService {

    private static final String USER_AGENT = "PeopleWiki-LinkImport/1.0";
    private static final int FETCH_TIMEOUT_MILLIS = 15_000;
    private static final int MAX_BODY_SIZE_BYTES = 3 * 1024 * 1024;
    private static final int MAX_EXTRACTED_TEXT_LENGTH = 12_000;
    private static final int MAX_PREVIEW_TEXT_LENGTH = 8_000;

    private final LlmSummaryService llmSummaryService;

    /**
     * 创建链接导入预览服务。
     *
     * @param llmSummaryService LLM 总结服务
     */
    public LinkImportService(LlmSummaryService llmSummaryService) {
        this.llmSummaryService = llmSummaryService;
    }

    /**
     * 抓取网页并生成新建笔记预览。
     *
     * @param request 链接导入请求
     * @return 导入预览
     */
    public LinkImportPreviewResponse preview(LinkImportRequest request) {
        URI sourceUri = validateHttpUrl(request.url());
        ExtractedWebPage webPage = fetchWebPage(sourceUri);
        LlmSummaryResponse suggestion = llmSummaryService.summarize(new LlmSummaryRequest(
                request.provider(),
                webPage.title(),
                webPage.text(),
                NoteType.MARKDOWN,
                "markdown",
                null
        ));
        String title = firstNotBlank(suggestion.title(), webPage.title(), sourceUri.getHost());
        return new LinkImportPreviewResponse(
                sourceUri.toString(),
                webPage.title(),
                suggestion.provider(),
                suggestion.model(),
                title,
                suggestion.summary(),
                suggestion.tags(),
                suggestion.categoryName(),
                suggestion.categoryId(),
                buildPreviewContent(sourceUri, webPage, title, suggestion)
        );
    }

    /**
     * 校验网页链接。
     *
     * @param rawUrl 原始链接
     * @return 标准 URI
     */
    private URI validateHttpUrl(String rawUrl) {
        String url = rawUrl == null ? "" : rawUrl.trim();
        if (url.isBlank()) {
            throw new BusinessException("链接不能为空");
        }
        try {
            URI uri = new URI(url);
            String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
            if (!scheme.equals("http") && !scheme.equals("https")) {
                throw new BusinessException("仅支持 http 或 https 链接");
            }
            if (uri.getHost() == null || uri.getHost().isBlank()) {
                throw new BusinessException("链接缺少有效域名");
            }
            return uri;
        } catch (URISyntaxException ex) {
            throw new BusinessException("链接格式不正确");
        }
    }

    /**
     * 抓取并提取网页正文。
     *
     * @param uri 网页 URI
     * @return 网页内容
     */
    private ExtractedWebPage fetchWebPage(URI uri) {
        try {
            Document document = Jsoup.connect(uri.toString())
                    .userAgent(USER_AGENT)
                    .timeout(FETCH_TIMEOUT_MILLIS)
                    .maxBodySize(MAX_BODY_SIZE_BYTES)
                    .followRedirects(true)
                    .get();
            document.select("script,style,noscript,svg,canvas,iframe,form,nav,header,footer,aside").remove();
            String title = firstNotBlank(
                    selectMetaContent(document, "meta[property=og:title]"),
                    document.title(),
                    selectElementText(document, "h1")
            );
            String text = firstNotBlank(
                    selectElementText(document, "article"),
                    selectElementText(document, "main"),
                    document.body() == null ? "" : document.body().text()
            );
            String normalizedText = limitText(normalizeText(text), MAX_EXTRACTED_TEXT_LENGTH);
            if (normalizedText.isBlank()) {
                throw new BusinessException("未能从网页中提取到正文内容");
            }
            return new ExtractedWebPage(title, normalizedText);
        } catch (IOException ex) {
            throw new BusinessException("读取网页失败：" + ex.getMessage());
        }
    }

    /**
     * 构建预览 Markdown 正文。
     *
     * @param uri 原始链接
     * @param webPage 网页内容
     * @param title 建议标题
     * @param suggestion LLM 建议
     * @return Markdown 正文
     */
    private String buildPreviewContent(
            URI uri,
            ExtractedWebPage webPage,
            String title,
            LlmSummaryResponse suggestion
    ) {
        StringBuilder builder = new StringBuilder();
        builder.append("# ").append(title).append("\n\n");
        builder.append("> 来源链接：[").append(uri).append("](").append(uri).append(")\n");
        if (!webPage.title().isBlank()) {
            builder.append("> 原始标题：").append(webPage.title()).append("\n");
        }
        builder.append("> 整理模型：").append(suggestion.provider()).append(" / ").append(suggestion.model()).append("\n\n");
        builder.append("## 摘要\n\n").append(suggestion.summary()).append("\n\n");
        builder.append("## 网页正文摘录\n\n").append(limitText(webPage.text(), MAX_PREVIEW_TEXT_LENGTH)).append("\n");
        return builder.toString();
    }

    /**
     * 读取 meta content。
     *
     * @param document HTML 文档
     * @param selector 选择器
     * @return content 内容
     */
    private String selectMetaContent(Document document, String selector) {
        Element element = document.selectFirst(selector);
        return element == null ? "" : safeText(element.attr("content"));
    }

    /**
     * 读取元素文本。
     *
     * @param document HTML 文档
     * @param selector 选择器
     * @return 元素文本
     */
    private String selectElementText(Document document, String selector) {
        Element element = document.selectFirst(selector);
        return element == null ? "" : safeText(element.text());
    }

    /**
     * 返回第一个非空文本。
     *
     * @param values 候选文本
     * @return 非空文本
     */
    private String firstNotBlank(String... values) {
        for (String value : values) {
            String safeValue = safeText(value);
            if (!safeValue.isBlank()) {
                return safeValue;
            }
        }
        return "";
    }

    /**
     * 标准化正文文本。
     *
     * @param value 原始文本
     * @return 标准文本
     */
    private String normalizeText(String value) {
        return safeText(value).replaceAll("\\s+", " ");
    }

    /**
     * 截断文本。
     *
     * @param value 原始文本
     * @param maxLength 最大长度
     * @return 截断文本
     */
    private String limitText(String value, int maxLength) {
        String safeValue = safeText(value);
        return safeValue.length() <= maxLength ? safeValue : safeValue.substring(0, maxLength) + "...";
    }

    /**
     * 获取安全文本。
     *
     * @param value 原始文本
     * @return 安全文本
     */
    private String safeText(String value) {
        return value == null ? "" : value.trim();
    }

    /**
     * 已提取网页内容。
     *
     * @param title 标题
     * @param text 正文
     */
    private record ExtractedWebPage(String title, String text) {
    }
}
