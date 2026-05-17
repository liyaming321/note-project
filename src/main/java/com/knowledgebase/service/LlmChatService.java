package com.knowledgebase.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.knowledgebase.config.KnowledgeBaseProperties;
import com.knowledgebase.config.KnowledgeBaseProperties.ProviderProperties;
import com.knowledgebase.dto.LlmProviderResponse;
import com.knowledgebase.dto.LlmProviderTestResponse;
import com.knowledgebase.entity.LlmProvider;
import com.knowledgebase.exception.BusinessException;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.stereotype.Service;

/**
 * OpenAI 兼容 Chat Completions 调用服务。
 */
@Service
public class LlmChatService {

    private final KnowledgeBaseProperties properties;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    /**
     * 创建 LLM 对话服务。
     *
     * @param properties 知识库配置
     * @param objectMapper JSON 工具
     */
    public LlmChatService(KnowledgeBaseProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(timeoutSeconds()))
                .build();
    }

    /**
     * 获取 LLM 供应商配置状态。
     *
     * @return 供应商配置状态
     */
    public List<LlmProviderResponse> providers() {
        return List.of(
                toProviderResponse(LlmProvider.BAILIAN, providerProperties(LlmProvider.BAILIAN)),
                toProviderResponse(LlmProvider.DEEPSEEK, providerProperties(LlmProvider.DEEPSEEK))
        );
    }

    /**
     * 测试 LLM 供应商连接。
     *
     * @param providerValue 供应商
     * @return 测试结果
     */
    public LlmProviderTestResponse testConnection(String providerValue) {
        LlmProvider provider = resolveProvider(providerValue);
        ProviderProperties providerProperties = providerProperties(provider);
        try {
            LlmChatResult result = chat(
                    provider.name().toLowerCase(Locale.ROOT),
                    "你是连接测试助手。",
                    "请只回复 OK，用于验证连接是否可用。",
                    0.0D
            );
            return new LlmProviderTestResponse(
                    result.provider(),
                    result.model(),
                    true,
                    "连接成功，模型已返回内容",
                    LocalDateTime.now()
            );
        } catch (BusinessException ex) {
            return new LlmProviderTestResponse(
                    provider.name().toLowerCase(Locale.ROOT),
                    providerProperties == null ? "" : safeText(providerProperties.getModel()),
                    false,
                    ex.getMessage(),
                    LocalDateTime.now()
            );
        }
    }

    /**
     * 调用对话模型。
     *
     * @param providerValue 供应商
     * @param systemPrompt 系统提示词
     * @param userPrompt 用户提示词
     * @param temperature 温度
     * @return 模型响应
     */
    public LlmChatResult chat(String providerValue, String systemPrompt, String userPrompt, double temperature) {
        LlmProvider provider = resolveProvider(providerValue);
        ProviderProperties providerProperties = providerProperties(provider);
        validateProvider(provider, providerProperties);
        try {
            Map<String, Object> requestBody = Map.of(
                    "model", providerProperties.getModel(),
                    "temperature", temperature,
                    "messages", List.of(
                            Map.of("role", "system", "content", safeText(systemPrompt)),
                            Map.of("role", "user", "content", safeText(userPrompt))
                    )
            );
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(chatCompletionsUri(providerProperties))
                    .timeout(Duration.ofSeconds(timeoutSeconds()))
                    .header("Authorization", "Bearer " + providerProperties.getApiKey().trim())
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(requestBody)))
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new BusinessException("LLM 请求失败，HTTP " + response.statusCode() + "：" + response.body());
            }
            return new LlmChatResult(
                    provider.name().toLowerCase(Locale.ROOT),
                    providerProperties.getModel(),
                    readContent(response.body())
            );
        } catch (IOException ex) {
            throw new BusinessException("LLM 请求失败：" + ex.getMessage());
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new BusinessException("LLM 请求被中断");
        }
    }

    /**
     * 解析响应内容。
     *
     * @param body 响应体
     * @return 文本内容
     * @throws IOException JSON 解析异常
     */
    private String readContent(String body) throws IOException {
        JsonNode rootNode = objectMapper.readTree(body);
        JsonNode contentNode = rootNode.path("choices").path(0).path("message").path("content");
        if (contentNode.isMissingNode() || contentNode.asText().isBlank()) {
            throw new BusinessException("LLM 请求失败：模型响应为空");
        }
        return contentNode.asText();
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
     * @return 配置
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
     * @param providerProperties 配置
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
     * @param providerProperties 配置
     * @return 响应
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
     * @return 请求地址
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
     * 获取请求超时时间。
     *
     * @return 超时时间
     */
    private int timeoutSeconds() {
        return Math.max(properties.getLlm().getTimeoutSeconds(), 1);
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
     * LLM 对话结果。
     *
     * @param provider 供应商
     * @param model 模型
     * @param content 响应内容
     */
    public record LlmChatResult(String provider, String model, String content) {
    }
}
