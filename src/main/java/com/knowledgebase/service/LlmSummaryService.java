package com.knowledgebase.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.knowledgebase.config.KnowledgeBaseProperties;
import com.knowledgebase.config.KnowledgeBaseProperties.ProviderProperties;
import com.knowledgebase.dto.LlmProviderResponse;
import com.knowledgebase.dto.LlmSummaryRequest;
import com.knowledgebase.dto.LlmSummaryResponse;
import com.knowledgebase.entity.Category;
import com.knowledgebase.entity.LlmProvider;
import com.knowledgebase.exception.BusinessException;
import com.knowledgebase.repository.CategoryRepository;
import com.knowledgebase.util.MarkdownTextExtractor;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.springframework.stereotype.Service;

/**
 * LLM 笔记总结服务。
 */
@Service
public class LlmSummaryService {

    private static final int MAX_CONTENT_LENGTH = 12_000;
    private static final int MAX_TAG_COUNT = 8;
    private static final int MAX_TAG_LENGTH = 24;
    private static final int MAX_SUMMARY_LENGTH = 500;
    private static final double TEMPERATURE = 0.2D;

    private final KnowledgeBaseProperties properties;
    private final CategoryRepository categoryRepository;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    /**
     * 创建 LLM 笔记总结服务。
     *
     * @param properties 知识库配置
     * @param categoryRepository 分类仓库
     * @param objectMapper JSON 工具
     */
    public LlmSummaryService(
            KnowledgeBaseProperties properties,
            CategoryRepository categoryRepository,
            ObjectMapper objectMapper
    ) {
        this.properties = properties;
        this.categoryRepository = categoryRepository;
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(timeoutSeconds()))
                .build();
    }

    /**
     * 获取 LLM 供应商配置状态。
     *
     * @return 供应商列表
     */
    public List<LlmProviderResponse> providers() {
        return List.of(
                toProviderResponse(LlmProvider.BAILIAN, providerProperties(LlmProvider.BAILIAN)),
                toProviderResponse(LlmProvider.DEEPSEEK, providerProperties(LlmProvider.DEEPSEEK))
        );
    }

    /**
     * 生成笔记总结建议。
     *
     * @param request 总结请求
     * @return 总结结果
     */
    public LlmSummaryResponse summarize(LlmSummaryRequest request) {
        LlmProvider provider = resolveProvider(request.provider());
        ProviderProperties providerProperties = providerProperties(provider);
        validateProvider(provider, providerProperties);
        try {
            String responseContent = callChatCompletions(providerProperties, buildPrompt(request));
            LlmSuggestion suggestion = parseSuggestion(responseContent);
            Category matchedCategory = matchCategory(suggestion.categoryName());
            return new LlmSummaryResponse(
                    provider.name().toLowerCase(Locale.ROOT),
                    providerProperties.getModel(),
                    suggestion.title(),
                    suggestion.summary(),
                    suggestion.tags(),
                    suggestion.categoryName(),
                    matchedCategory == null ? null : matchedCategory.getId()
            );
        } catch (IOException ex) {
            throw new BusinessException("LLM 总结请求失败：" + ex.getMessage());
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new BusinessException("LLM 总结请求被中断");
        }
    }

    /**
     * 调用 OpenAI 兼容 Chat Completions 接口。
     *
     * @param providerProperties 供应商配置
     * @param prompt 提示词
     * @return 模型响应内容
     * @throws IOException 网络异常
     * @throws InterruptedException 中断异常
     */
    private String callChatCompletions(ProviderProperties providerProperties, String prompt)
            throws IOException, InterruptedException {
        Map<String, Object> requestBody = Map.of(
                "model", providerProperties.getModel(),
                "temperature", TEMPERATURE,
                "messages", List.of(
                        Map.of("role", "system", "content", "你是个人知识库的信息整理助手，只输出合法 JSON。"),
                        Map.of("role", "user", "content", prompt)
                )
        );
        HttpRequest httpRequest = HttpRequest.newBuilder()
                .uri(chatCompletionsUri(providerProperties))
                .timeout(Duration.ofSeconds(timeoutSeconds()))
                .header("Authorization", "Bearer " + providerProperties.getApiKey().trim())
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(requestBody)))
                .build();
        HttpResponse<String> response = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new BusinessException("LLM 总结失败，HTTP " + response.statusCode() + "：" + response.body());
        }
        JsonNode rootNode = objectMapper.readTree(response.body());
        JsonNode contentNode = rootNode.path("choices").path(0).path("message").path("content");
        if (contentNode.isMissingNode() || contentNode.asText().isBlank()) {
            throw new BusinessException("LLM 总结失败：模型响应为空");
        }
        return contentNode.asText();
    }

    /**
     * 构建总结提示词。
     *
     * @param request 总结请求
     * @return 提示词
     */
    private String buildPrompt(LlmSummaryRequest request) {
        String contentText = request.type() == null
                ? request.content()
                : MarkdownTextExtractor.extract(request.content());
        String safeContent = limitText(contentText == null || contentText.isBlank() ? request.content() : contentText,
                MAX_CONTENT_LENGTH);
        String categories = availableCategoryNames(request.categoryNames());
        return """
                请根据下面的知识库笔记内容生成整理建议。
                要求：
                1. 只返回 JSON，不要 Markdown 代码块，不要解释。
                2. JSON 字段固定为 title、summary、tags、categoryName。
                3. summary 用中文，80 到 200 字，概括核心信息。
                4. tags 返回 3 到 6 个中文短标签，不要包含 #。
                5. categoryName 优先从候选分类中选择最合适的一项；如果没有合适项，可返回新的分类名称。

                候选分类：%s
                原标题：%s
                笔记类型：%s
                代码语言：%s

                笔记内容：
                %s
                """.formatted(
                categories,
                safeText(request.title()),
                request.type(),
                safeText(request.language()),
                safeContent
        );
    }

    /**
     * 解析模型输出。
     *
     * @param responseContent 模型输出
     * @return 建议结果
     */
    private LlmSuggestion parseSuggestion(String responseContent) {
        try {
            JsonNode rootNode = objectMapper.readTree(extractJson(responseContent));
            String title = limitText(rootNode.path("title").asText("").trim(), 160);
            String summary = limitText(rootNode.path("summary").asText("").trim(), MAX_SUMMARY_LENGTH);
            String categoryName = limitText(rootNode.path("categoryName").asText("").trim(), 80);
            List<String> tags = normalizeTags(rootNode.path("tags"));
            if (summary.isBlank()) {
                throw new BusinessException("LLM 总结失败：模型未返回摘要");
            }
            return new LlmSuggestion(title, summary, tags, categoryName);
        } catch (JsonProcessingException ex) {
            throw new BusinessException("LLM 总结失败：模型未返回合法 JSON");
        }
    }

    /**
     * 标准化标签列表。
     *
     * @param tagsNode 标签节点
     * @return 标签名称列表
     */
    private List<String> normalizeTags(JsonNode tagsNode) {
        Set<String> tags = new LinkedHashSet<>();
        if (tagsNode != null && tagsNode.isArray()) {
            for (JsonNode tagNode : tagsNode) {
                String tag = tagNode.asText("")
                        .replace("#", "")
                        .replace("，", ",")
                        .trim();
                if (!tag.isBlank()) {
                    tags.add(limitText(tag, MAX_TAG_LENGTH));
                }
                if (tags.size() >= MAX_TAG_COUNT) {
                    break;
                }
            }
        }
        return new ArrayList<>(tags);
    }

    /**
     * 按分类名称匹配已有分类。
     *
     * @param categoryName 分类名称
     * @return 已匹配分类
     */
    private Category matchCategory(String categoryName) {
        if (categoryName == null || categoryName.isBlank()) {
            return null;
        }
        Optional<Category> directCategory = categoryRepository.findByName(categoryName.trim());
        if (directCategory.isPresent()) {
            return directCategory.get();
        }
        String normalizedName = categoryName.trim().toLowerCase(Locale.ROOT);
        return categoryRepository.findAll()
                .stream()
                .filter(category -> category.getName().trim().toLowerCase(Locale.ROOT).equals(normalizedName))
                .findFirst()
                .orElse(null);
    }

    /**
     * 解析供应商。
     *
     * @param providerValue 供应商文本
     * @return 供应商
     */
    private LlmProvider resolveProvider(String providerValue) {
        if (providerValue == null || providerValue.isBlank()) {
            return LlmProvider.fromValue(properties.getLlm().getDefaultProvider());
        }
        try {
            return LlmProvider.fromValue(providerValue);
        } catch (IllegalArgumentException ex) {
            throw new BusinessException(ex.getMessage());
        }
    }

    /**
     * 获取供应商配置。
     *
     * @param provider 供应商
     * @return 供应商配置
     */
    private ProviderProperties providerProperties(LlmProvider provider) {
        return switch (provider) {
            case BAILIAN -> properties.getLlm().getBailian();
            case DEEPSEEK -> properties.getLlm().getDeepseek();
        };
    }

    /**
     * 校验供应商配置。
     *
     * @param provider 供应商
     * @param providerProperties 供应商配置
     */
    private void validateProvider(LlmProvider provider, ProviderProperties providerProperties) {
        if (providerProperties == null || safeText(providerProperties.getApiKey()).isBlank()) {
            throw new BusinessException("请先配置 " + provider.name().toLowerCase(Locale.ROOT) + " 的 API Key");
        }
        if (safeText(providerProperties.getBaseUrl()).isBlank()) {
            throw new BusinessException("请先配置 LLM API 地址");
        }
        if (safeText(providerProperties.getModel()).isBlank()) {
            throw new BusinessException("请先配置 LLM 模型名称");
        }
    }

    /**
     * 转换供应商配置响应。
     *
     * @param provider 供应商
     * @param providerProperties 供应商配置
     * @return 供应商配置响应
     */
    private LlmProviderResponse toProviderResponse(LlmProvider provider, ProviderProperties providerProperties) {
        return new LlmProviderResponse(
                provider.name().toLowerCase(Locale.ROOT),
                providerProperties == null ? "" : safeText(providerProperties.getModel()),
                providerProperties != null && !safeText(providerProperties.getApiKey()).isBlank()
        );
    }

    /**
     * 构建 Chat Completions 地址。
     *
     * @param providerProperties 供应商配置
     * @return Chat Completions 地址
     */
    private URI chatCompletionsUri(ProviderProperties providerProperties) {
        String baseUrl = providerProperties.getBaseUrl().trim();
        String normalizedBaseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        if (normalizedBaseUrl.endsWith("/chat/completions")) {
            return URI.create(normalizedBaseUrl);
        }
        return URI.create(normalizedBaseUrl + "/chat/completions");
    }

    /**
     * 获取候选分类名称。
     *
     * @param requestCategoryNames 请求中的候选分类
     * @return 候选分类描述
     */
    private String availableCategoryNames(List<String> requestCategoryNames) {
        List<String> categoryNames = requestCategoryNames == null || requestCategoryNames.isEmpty()
                ? categoryRepository.findAll().stream().map(Category::getName).toList()
                : requestCategoryNames;
        return categoryNames.stream()
                .map(this::safeText)
                .filter(value -> !value.isBlank())
                .distinct()
                .limit(30)
                .reduce((left, right) -> left + "、" + right)
                .orElse("无");
    }

    /**
     * 从模型输出中提取 JSON。
     *
     * @param value 原始输出
     * @return JSON 文本
     */
    private String extractJson(String value) {
        String trimmedValue = safeText(value);
        int startIndex = trimmedValue.indexOf('{');
        int endIndex = trimmedValue.lastIndexOf('}');
        if (startIndex < 0 || endIndex <= startIndex) {
            return trimmedValue;
        }
        return trimmedValue.substring(startIndex, endIndex + 1);
    }

    /**
     * 截断文本。
     *
     * @param value 原始文本
     * @param maxLength 最大长度
     * @return 截断后文本
     */
    private String limitText(String value, int maxLength) {
        String safeValue = safeText(value);
        return safeValue.length() <= maxLength ? safeValue : safeValue.substring(0, maxLength);
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
     * 获取请求超时时间。
     *
     * @return 请求超时时间
     */
    private int timeoutSeconds() {
        return Math.max(properties.getLlm().getTimeoutSeconds(), 1);
    }

    /**
     * LLM 建议结果。
     *
     * @param title 建议标题
     * @param summary 摘要
     * @param tags 标签
     * @param categoryName 分类名称
     */
    private record LlmSuggestion(
            String title,
            String summary,
            List<String> tags,
            String categoryName
    ) {
    }
}
