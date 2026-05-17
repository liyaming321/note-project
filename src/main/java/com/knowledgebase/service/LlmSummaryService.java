package com.knowledgebase.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.knowledgebase.config.KnowledgeBaseProperties;
import com.knowledgebase.dto.LlmProviderResponse;
import com.knowledgebase.dto.LlmSummaryRequest;
import com.knowledgebase.dto.LlmSummaryResponse;
import com.knowledgebase.entity.Category;
import com.knowledgebase.entity.NoteType;
import com.knowledgebase.exception.BusinessException;
import com.knowledgebase.repository.CategoryRepository;
import com.knowledgebase.util.MarkdownTextExtractor;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
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
    private final LlmChatService llmChatService;

    /**
     * 创建 LLM 笔记总结服务。
     *
     * @param properties 知识库配置
     * @param categoryRepository 分类仓库
     * @param objectMapper JSON 工具
     * @param llmChatService LLM 对话服务
     */
    public LlmSummaryService(
            KnowledgeBaseProperties properties,
            CategoryRepository categoryRepository,
            ObjectMapper objectMapper,
            LlmChatService llmChatService
    ) {
        this.properties = properties;
        this.categoryRepository = categoryRepository;
        this.objectMapper = objectMapper;
        this.llmChatService = llmChatService;
    }

    /**
     * 获取 LLM 供应商配置状态。
     *
     * @return 供应商列表
     */
    public List<LlmProviderResponse> providers() {
        return llmChatService.providers();
    }

    /**
     * 生成笔记总结建议。
     *
     * @param request 总结请求
     * @return 总结结果
     */
    public LlmSummaryResponse summarize(LlmSummaryRequest request) {
        LlmChatService.LlmChatResult chatResult = llmChatService.chat(
                request.provider(),
                "你是个人知识库的信息整理助手，只输出合法 JSON。",
                buildPrompt(request),
                TEMPERATURE
        );
        LlmSuggestion suggestion = parseSuggestion(chatResult.content());
        Category matchedCategory = matchCategory(suggestion.categoryName());
        return new LlmSummaryResponse(
                chatResult.provider(),
                chatResult.model(),
                suggestion.title(),
                suggestion.summary(),
                suggestion.tags(),
                suggestion.categoryName(),
                matchedCategory == null ? null : matchedCategory.getId()
        );
    }

    /**
     * 构建总结提示词。
     *
     * @param request 总结请求
     * @return 提示词
     */
    private String buildPrompt(LlmSummaryRequest request) {
        String safeContent = limitText(normalizeContentForSummary(request.type(), request.content()), MAX_CONTENT_LENGTH);
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
                内容格式：%s
                代码语言：%s

                笔记内容：
                %s
                """.formatted(
                categories,
                safeText(request.title()),
                resolveContentFormatLabel(request.type()),
                safeText(request.language()),
                safeContent
        );
    }

    /**
     * 根据内容格式生成用于总结的正文文本。
     *
     * @param type 内容格式
     * @param content 原始内容
     * @return 供 LLM 使用的正文
     */
    private String normalizeContentForSummary(NoteType type, String content) {
        String safeContent = safeText(content);
        if (type == NoteType.MARKDOWN) {
            return MarkdownTextExtractor.extract(safeContent);
        }
        return safeContent;
    }

    /**
     * 获取内容格式展示名称。
     *
     * @param type 内容格式
     * @return 展示名称
     */
    private String resolveContentFormatLabel(NoteType type) {
        if (type == NoteType.CODE) {
            return "代码";
        }
        if (type == NoteType.TEXT) {
            return "普通文本";
        }
        return "Markdown";
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
