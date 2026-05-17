package com.knowledgebase.service;

import com.knowledgebase.config.KnowledgeBaseProperties;
import com.knowledgebase.dto.AdminConfigurationChecklistResponse;
import com.knowledgebase.dto.AdminVectorIndexInfoResponse;
import com.knowledgebase.dto.ConfigCheckItemResponse;
import com.knowledgebase.dto.LlmProviderResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Service;

/**
 * 本地配置中心检查服务。
 */
@Service
public class ConfigurationCenterService {

    private final KnowledgeBaseProperties properties;
    private final LlmChatService llmChatService;
    private final VectorIndexService vectorIndexService;

    /**
     * 创建配置中心服务。
     *
     * @param properties 知识库配置
     * @param llmChatService LLM 对话服务
     * @param vectorIndexService 向量索引服务
     */
    public ConfigurationCenterService(
            KnowledgeBaseProperties properties,
            LlmChatService llmChatService,
            VectorIndexService vectorIndexService
    ) {
        this.properties = properties;
        this.llmChatService = llmChatService;
        this.vectorIndexService = vectorIndexService;
    }

    /**
     * 构建配置检查清单。
     *
     * @return 配置检查清单
     */
    public AdminConfigurationChecklistResponse checklist() {
        List<ConfigCheckItemResponse> items = new ArrayList<>();
        addPathItem(items, "data-path", "数据目录", properties.getDataPath());
        addPathItem(items, "index-path", "全文索引目录", properties.getIndexPath());
        addPathItem(items, "vector-index-path", "向量索引目录", properties.getVectorIndexPath());
        addPathItem(items, "images-path", "图片目录", properties.getImagesPath());

        List<LlmProviderResponse> providers = llmChatService.providers();
        boolean llmReady = providers.stream().anyMatch(LlmProviderResponse::configured);
        items.add(new ConfigCheckItemResponse(
                "llm",
                "LLM Provider",
                llmReady,
                llmReady ? "可用" : "未配置",
                llmReady ? configuredLlmDetail(providers) : "未配置 API Key，问答和链接智能整理会降级或不可用"
        ));

        AdminVectorIndexInfoResponse vectorInfo = vectorIndexService.info();
        items.add(new ConfigCheckItemResponse(
                "embedding",
                "本地 Embedding",
                vectorInfo.configured(),
                vectorInfo.configured() ? "已配置" : "未配置",
                vectorInfo.message()
        ));

        boolean offlineReady = hasText(properties.getDataPath()) && hasText(properties.getIndexPath());
        String message = llmReady || vectorInfo.configured()
                ? "增强能力已部分启用，可按需继续补全模型配置"
                : "纯离线笔记、全文搜索和备份可用，语义搜索与问答需要额外配置模型";
        return new AdminConfigurationChecklistResponse(
                items,
                offlineReady,
                llmReady,
                vectorInfo.configured(),
                message
        );
    }

    /**
     * 追加路径检查项。
     *
     * @param items 检查项集合
     * @param key 检查项键
     * @param label 检查项名称
     * @param pathValue 路径值
     */
    private void addPathItem(List<ConfigCheckItemResponse> items, String key, String label, String pathValue) {
        boolean configured = hasText(pathValue);
        Path path = configured ? Paths.get(pathValue).toAbsolutePath().normalize() : null;
        boolean exists = path != null && Files.exists(path);
        items.add(new ConfigCheckItemResponse(
                key,
                label,
                configured,
                exists ? "已创建" : configured ? "待创建" : "未配置",
                path == null ? "未设置路径" : path.toString()
        ));
    }

    /**
     * 构建已配置 LLM 说明。
     *
     * @param providers 供应商配置
     * @return 说明文本
     */
    private String configuredLlmDetail(List<LlmProviderResponse> providers) {
        return providers.stream()
                .filter(LlmProviderResponse::configured)
                .map(provider -> provider.name() + " / " + provider.model())
                .findFirst()
                .orElse("已配置");
    }

    /**
     * 判断文本是否有效。
     *
     * @param value 原始文本
     * @return 是否有效
     */
    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
