package com.knowledgebase.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.knowledgebase.config.KnowledgeBaseProperties;
import com.knowledgebase.dto.SearchFeedbackItemResponse;
import com.knowledgebase.dto.SearchFeedbackRequest;
import com.knowledgebase.dto.SearchFeedbackResponse;
import com.knowledgebase.dto.SearchFeedbackSummaryResponse;
import com.knowledgebase.dto.SearchTuningSettingsRequest;
import com.knowledgebase.dto.SearchTuningSettingsResponse;
import com.knowledgebase.entity.Note;
import com.knowledgebase.exception.BusinessException;
import com.knowledgebase.exception.ResourceNotFoundException;
import com.knowledgebase.repository.NoteRepository;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import org.springframework.stereotype.Service;

/**
 * 搜索调优配置与反馈记录服务。
 */
@Service
public class SearchTuningService {

    private static final double DEFAULT_KEYWORD_WEIGHT = 0.55D;
    private static final double DEFAULT_SEMANTIC_WEIGHT = 0.45D;
    private static final double DEFAULT_TITLE_HIT_BOOST = 0.08D;
    private static final double DEFAULT_TAG_HIT_BOOST = 0.06D;
    private static final double DEFAULT_PINNED_BOOST = 0.04D;
    private static final double DEFAULT_FAVORITE_BOOST = 0.03D;
    private static final double DEFAULT_RECENT_SEVEN_DAYS_BOOST = 0.03D;
    private static final double DEFAULT_RECENT_THIRTY_DAYS_BOOST = 0.015D;
    private static final int MAX_FEEDBACK_ITEMS = 300;
    private static final int RECENT_FEEDBACK_SIZE = 20;

    private final ObjectMapper objectMapper;
    private final NoteRepository noteRepository;
    private final Path settingsPath;
    private final Path feedbackPath;

    /**
     * 创建搜索调优服务。
     *
     * @param properties 知识库配置
     * @param objectMapper JSON 工具
     * @param noteRepository 笔记仓库
     */
    public SearchTuningService(
            KnowledgeBaseProperties properties,
            ObjectMapper objectMapper,
            NoteRepository noteRepository
    ) {
        this.objectMapper = objectMapper;
        this.noteRepository = noteRepository;
        Path dataDirectory = resolveDataDirectory(properties.getDataPath());
        this.settingsPath = dataDirectory.resolve("search-tuning.json");
        this.feedbackPath = dataDirectory.resolve("search-feedback.json");
    }

    /**
     * 获取当前搜索调优设置。
     *
     * @return 搜索调优设置
     */
    public synchronized SearchTuningSettingsResponse currentSettings() {
        return toResponse(readSettings());
    }

    /**
     * 更新搜索调优设置。
     *
     * @param request 设置请求
     * @return 更新后的设置
     */
    public synchronized SearchTuningSettingsResponse updateSettings(SearchTuningSettingsRequest request) {
        SearchTuningSettings settings = sanitizeSettings(request);
        writeSettings(settings);
        return toResponse(settings);
    }

    /**
     * 记录搜索结果反馈。
     *
     * @param request 反馈请求
     * @return 记录结果
     */
    public synchronized SearchFeedbackResponse recordFeedback(SearchFeedbackRequest request) {
        Note note = noteRepository.findById(request.noteId())
                .orElseThrow(() -> new ResourceNotFoundException("笔记不存在：" + request.noteId()));
        SearchFeedbackStore store = readFeedbackStore();
        List<SearchFeedbackItem> items = new ArrayList<>(store.items());
        LocalDateTime now = LocalDateTime.now();
        items.add(new SearchFeedbackItem(
                note.getId(),
                note.getTitle(),
                safeText(request.keyword()),
                normalizeMode(request.mode()),
                Boolean.TRUE.equals(request.useful()),
                safeText(request.reason()),
                now
        ));
        items = items.stream()
                .sorted(Comparator.comparing(SearchFeedbackItem::createdAt).reversed())
                .limit(MAX_FEEDBACK_ITEMS)
                .toList();
        writeFeedbackStore(new SearchFeedbackStore(items));
        SearchFeedbackCounts counts = countFeedback(items);
        return new SearchFeedbackResponse(
                counts.totalCount(),
                counts.usefulCount(),
                counts.irrelevantCount(),
                request.useful() ? "已记录：这个结果有用" : "已记录：这个结果不相关",
                now
        );
    }

    /**
     * 获取搜索反馈汇总。
     *
     * @return 搜索反馈汇总
     */
    public synchronized SearchFeedbackSummaryResponse feedbackSummary() {
        List<SearchFeedbackItem> items = readFeedbackStore().items();
        SearchFeedbackCounts counts = countFeedback(items);
        List<SearchFeedbackItemResponse> recentItems = items.stream()
                .sorted(Comparator.comparing(SearchFeedbackItem::createdAt).reversed())
                .limit(RECENT_FEEDBACK_SIZE)
                .map(this::toFeedbackResponse)
                .toList();
        return new SearchFeedbackSummaryResponse(
                counts.totalCount(),
                counts.usefulCount(),
                counts.irrelevantCount(),
                recentItems
        );
    }

    /**
     * 读取搜索调优设置值对象。
     *
     * @return 搜索调优设置
     */
    public synchronized SearchTuningSettings settingsValue() {
        return readSettings();
    }

    /**
     * 读取设置文件。
     *
     * @return 搜索调优设置
     */
    private SearchTuningSettings readSettings() {
        if (Files.notExists(settingsPath)) {
            return defaultSettings();
        }
        try {
            SearchTuningSettings settings = objectMapper.readValue(settingsPath.toFile(), SearchTuningSettings.class);
            return sanitizeSettings(settings);
        } catch (IOException ex) {
            throw new BusinessException("读取搜索调优配置失败：" + ex.getMessage());
        }
    }

    /**
     * 写入设置文件。
     *
     * @param settings 搜索调优设置
     */
    private void writeSettings(SearchTuningSettings settings) {
        try {
            Files.createDirectories(settingsPath.getParent());
            objectMapper.writerWithDefaultPrettyPrinter().writeValue(settingsPath.toFile(), settings);
        } catch (IOException ex) {
            throw new BusinessException("保存搜索调优配置失败：" + ex.getMessage());
        }
    }

    /**
     * 读取反馈文件。
     *
     * @return 反馈存储
     */
    private SearchFeedbackStore readFeedbackStore() {
        if (Files.notExists(feedbackPath)) {
            return new SearchFeedbackStore(List.of());
        }
        try {
            SearchFeedbackStore store = objectMapper.readValue(feedbackPath.toFile(), SearchFeedbackStore.class);
            return store.items() == null ? new SearchFeedbackStore(List.of()) : store;
        } catch (IOException ex) {
            throw new BusinessException("读取搜索反馈失败：" + ex.getMessage());
        }
    }

    /**
     * 写入反馈文件。
     *
     * @param store 反馈存储
     */
    private void writeFeedbackStore(SearchFeedbackStore store) {
        try {
            Files.createDirectories(feedbackPath.getParent());
            objectMapper.writerWithDefaultPrettyPrinter().writeValue(feedbackPath.toFile(), store);
        } catch (IOException ex) {
            throw new BusinessException("保存搜索反馈失败：" + ex.getMessage());
        }
    }

    /**
     * 根据请求构建安全设置。
     *
     * @param request 设置请求
     * @return 安全设置
     */
    private SearchTuningSettings sanitizeSettings(SearchTuningSettingsRequest request) {
        return sanitizeSettings(new SearchTuningSettings(
                valueOrDefault(request.keywordWeight(), DEFAULT_KEYWORD_WEIGHT),
                valueOrDefault(request.semanticWeight(), DEFAULT_SEMANTIC_WEIGHT),
                valueOrDefault(request.titleHitBoost(), DEFAULT_TITLE_HIT_BOOST),
                valueOrDefault(request.tagHitBoost(), DEFAULT_TAG_HIT_BOOST),
                valueOrDefault(request.pinnedBoost(), DEFAULT_PINNED_BOOST),
                valueOrDefault(request.favoriteBoost(), DEFAULT_FAVORITE_BOOST),
                valueOrDefault(request.recentSevenDaysBoost(), DEFAULT_RECENT_SEVEN_DAYS_BOOST),
                valueOrDefault(request.recentThirtyDaysBoost(), DEFAULT_RECENT_THIRTY_DAYS_BOOST),
                LocalDateTime.now()
        ));
    }

    /**
     * 清洗设置值对象。
     *
     * @param settings 原始设置
     * @return 安全设置
     */
    private SearchTuningSettings sanitizeSettings(SearchTuningSettings settings) {
        double keywordWeight = clamp(settings.keywordWeight(), 0.0D, 1.0D, DEFAULT_KEYWORD_WEIGHT);
        double semanticWeight = clamp(settings.semanticWeight(), 0.0D, 1.0D, DEFAULT_SEMANTIC_WEIGHT);
        if (keywordWeight + semanticWeight <= 0.0D) {
            keywordWeight = DEFAULT_KEYWORD_WEIGHT;
            semanticWeight = DEFAULT_SEMANTIC_WEIGHT;
        }
        return new SearchTuningSettings(
                keywordWeight,
                semanticWeight,
                clamp(settings.titleHitBoost(), 0.0D, 0.5D, DEFAULT_TITLE_HIT_BOOST),
                clamp(settings.tagHitBoost(), 0.0D, 0.5D, DEFAULT_TAG_HIT_BOOST),
                clamp(settings.pinnedBoost(), 0.0D, 0.5D, DEFAULT_PINNED_BOOST),
                clamp(settings.favoriteBoost(), 0.0D, 0.5D, DEFAULT_FAVORITE_BOOST),
                clamp(settings.recentSevenDaysBoost(), 0.0D, 0.5D, DEFAULT_RECENT_SEVEN_DAYS_BOOST),
                clamp(settings.recentThirtyDaysBoost(), 0.0D, 0.5D, DEFAULT_RECENT_THIRTY_DAYS_BOOST),
                settings.updatedAt() == null ? LocalDateTime.now() : settings.updatedAt()
        );
    }

    /**
     * 创建默认设置。
     *
     * @return 默认设置
     */
    private SearchTuningSettings defaultSettings() {
        return new SearchTuningSettings(
                DEFAULT_KEYWORD_WEIGHT,
                DEFAULT_SEMANTIC_WEIGHT,
                DEFAULT_TITLE_HIT_BOOST,
                DEFAULT_TAG_HIT_BOOST,
                DEFAULT_PINNED_BOOST,
                DEFAULT_FAVORITE_BOOST,
                DEFAULT_RECENT_SEVEN_DAYS_BOOST,
                DEFAULT_RECENT_THIRTY_DAYS_BOOST,
                LocalDateTime.now()
        );
    }

    /**
     * 转换为设置响应。
     *
     * @param settings 搜索调优设置
     * @return 设置响应
     */
    private SearchTuningSettingsResponse toResponse(SearchTuningSettings settings) {
        return new SearchTuningSettingsResponse(
                settings.keywordWeight(),
                settings.semanticWeight(),
                settings.titleHitBoost(),
                settings.tagHitBoost(),
                settings.pinnedBoost(),
                settings.favoriteBoost(),
                settings.recentSevenDaysBoost(),
                settings.recentThirtyDaysBoost(),
                settings.updatedAt(),
                settingsPath.toAbsolutePath().normalize().toString()
        );
    }

    /**
     * 转换反馈明细响应。
     *
     * @param item 反馈项
     * @return 反馈响应
     */
    private SearchFeedbackItemResponse toFeedbackResponse(SearchFeedbackItem item) {
        return new SearchFeedbackItemResponse(
                item.noteId(),
                item.noteTitle(),
                item.keyword(),
                item.mode(),
                item.useful(),
                item.reason(),
                item.createdAt()
        );
    }

    /**
     * 统计反馈数量。
     *
     * @param items 反馈项
     * @return 反馈统计
     */
    private SearchFeedbackCounts countFeedback(List<SearchFeedbackItem> items) {
        int usefulCount = (int) items.stream().filter(SearchFeedbackItem::useful).count();
        int totalCount = items.size();
        return new SearchFeedbackCounts(totalCount, usefulCount, totalCount - usefulCount);
    }

    /**
     * 推导 H2 数据文件所在目录。
     *
     * @param configuredDataPath 配置的数据路径
     * @return 数据目录
     */
    private Path resolveDataDirectory(String configuredDataPath) {
        String dataPathValue = configuredDataPath;
        if (dataPathValue == null || dataPathValue.isBlank()) {
            dataPathValue = Paths.get(System.getProperty("user.home"), ".knowledge-base", "data", "knowledge-base").toString();
        }
        return Paths.get(dataPathValue).toAbsolutePath().normalize().getParent();
    }

    /**
     * 归一化搜索模式。
     *
     * @param mode 原始模式
     * @return 搜索模式
     */
    private String normalizeMode(String mode) {
        String normalizedMode = safeText(mode).toLowerCase(Locale.ROOT);
        return normalizedMode.isBlank() ? "unknown" : normalizedMode;
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
     * 获取默认值。
     *
     * @param value 原始值
     * @param defaultValue 默认值
     * @return 安全值
     */
    private double valueOrDefault(Double value, double defaultValue) {
        return value == null ? defaultValue : value;
    }

    /**
     * 限制数值范围。
     *
     * @param value 原始值
     * @param min 最小值
     * @param max 最大值
     * @param defaultValue 默认值
     * @return 安全值
     */
    private double clamp(double value, double min, double max, double defaultValue) {
        if (Double.isNaN(value) || Double.isInfinite(value)) {
            return defaultValue;
        }
        return Math.max(min, Math.min(max, value));
    }

    /**
     * 搜索调优设置值对象。
     *
     * @param keywordWeight 关键词权重
     * @param semanticWeight 语义权重
     * @param titleHitBoost 标题命中加权
     * @param tagHitBoost 标签命中加权
     * @param pinnedBoost 置顶加权
     * @param favoriteBoost 收藏加权
     * @param recentSevenDaysBoost 近 7 天更新加权
     * @param recentThirtyDaysBoost 近 30 天更新加权
     * @param updatedAt 更新时间
     */
    public record SearchTuningSettings(
            double keywordWeight,
            double semanticWeight,
            double titleHitBoost,
            double tagHitBoost,
            double pinnedBoost,
            double favoriteBoost,
            double recentSevenDaysBoost,
            double recentThirtyDaysBoost,
            LocalDateTime updatedAt
    ) {
    }

    /**
     * 搜索反馈存储结构。
     *
     * @param items 反馈项
     */
    private record SearchFeedbackStore(List<SearchFeedbackItem> items) {
    }

    /**
     * 搜索反馈项。
     *
     * @param noteId 笔记ID
     * @param noteTitle 笔记标题
     * @param keyword 关键词
     * @param mode 搜索模式
     * @param useful 是否有用
     * @param reason 原因
     * @param createdAt 创建时间
     */
    private record SearchFeedbackItem(
            Long noteId,
            String noteTitle,
            String keyword,
            String mode,
            boolean useful,
            String reason,
            LocalDateTime createdAt
    ) {
    }

    /**
     * 搜索反馈统计。
     *
     * @param totalCount 总数
     * @param usefulCount 有用数量
     * @param irrelevantCount 不相关数量
     */
    private record SearchFeedbackCounts(int totalCount, int usefulCount, int irrelevantCount) {
    }
}
