package com.knowledgebase.service;

import com.knowledgebase.dto.HybridSearchResultResponse;
import com.knowledgebase.dto.PageResponse;
import com.knowledgebase.entity.Note;
import com.knowledgebase.entity.NoteStatus;
import com.knowledgebase.exception.BusinessException;
import com.knowledgebase.repository.NoteRepository;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 混合搜索服务，融合 Lucene 全文得分与向量相似度。
 */
@Service
public class HybridSearchService {

    private static final int DEFAULT_SIZE = 10;
    private static final int MAX_SIZE = 50;
    private static final int MAX_CANDIDATES = 10_000;

    private final SearchService searchService;
    private final VectorIndexService vectorIndexService;
    private final NoteRepository noteRepository;
    private final SearchTuningService searchTuningService;

    /**
     * 创建混合搜索服务。
     *
     * @param searchService 全文搜索服务
     * @param vectorIndexService 向量索引服务
     * @param noteRepository 笔记仓库
     * @param searchTuningService 搜索调优服务
     */
    public HybridSearchService(
            SearchService searchService,
            VectorIndexService vectorIndexService,
            NoteRepository noteRepository,
            SearchTuningService searchTuningService
    ) {
        this.searchService = searchService;
        this.vectorIndexService = vectorIndexService;
        this.noteRepository = noteRepository;
        this.searchTuningService = searchTuningService;
    }

    /**
     * 执行混合搜索。
     *
     * @param keyword 搜索关键词或自然语言问题
     * @param scope 搜索范围
     * @param tag 标签筛选
     * @param category 分类筛选
     * @param language 语言筛选
     * @param status 发布状态筛选
     * @param updatedFrom 更新时间开始日期
     * @param updatedTo 更新时间结束日期
     * @param page 页码
     * @param size 每页数量
     * @return 混合搜索分页结果
     */
    @Transactional(readOnly = true)
    public PageResponse<HybridSearchResultResponse> search(
            String keyword,
            String scope,
            String tag,
            String category,
            String language,
            NoteStatus status,
            LocalDate updatedFrom,
            LocalDate updatedTo,
            int page,
            int size
    ) {
        int safePage = Math.max(page, 0);
        int safeSize = normalizeSize(size);
        boolean hasTextQuery = !safeText(keyword).isBlank();
        SearchService.KeywordSearchHits keywordHits = searchService.searchKeywordHits(
                keyword,
                scope,
                tag,
                category,
                language,
                status,
                updatedFrom,
                updatedTo,
                MAX_CANDIDATES
        );
        SemanticCandidateResult semanticResult = loadSemanticCandidates(
                keyword,
                tag,
                category,
                language,
                status,
                updatedFrom,
                updatedTo,
                hasTextQuery
        );
        List<HybridCandidate> candidates = mergeCandidates(keywordHits, semanticResult.hits(), hasTextQuery);
        if (candidates.isEmpty()) {
            return emptyPage(safePage, safeSize);
        }
        Map<Long, Note> noteMap = loadNotes(candidates);
        SearchTuningService.SearchTuningSettings tuningSettings = searchTuningService.settingsValue();
        List<HybridScoredHit> scoredHits = scoreCandidates(
                candidates,
                noteMap,
                keyword,
                tag,
                keywordHits,
                semanticResult.semanticAvailable(),
                semanticResult.fallbackReason(),
                tuningSettings
        );
        scoredHits.sort(Comparator.comparingDouble(HybridScoredHit::hybridScore)
                .reversed()
                .thenComparing(hit -> hit.note().isPinned(), Comparator.reverseOrder())
                .thenComparing(hit -> hit.note().isFavorite(), Comparator.reverseOrder())
                .thenComparing(hit -> hit.note().getUpdatedAt(), Comparator.nullsLast(Comparator.reverseOrder()))
                .thenComparing(hit -> hit.note().getId(), Comparator.nullsLast(Comparator.reverseOrder())));
        List<HybridScoredHit> pageHits = pageHits(scoredHits, safePage, safeSize);
        List<HybridSearchResultResponse> items = pageHits.stream()
                .map(hit -> toResponse(hit, keywordHits, semanticResult))
                .toList();
        return new PageResponse<>(
                items,
                safePage,
                safeSize,
                scoredHits.size(),
                totalPages(scoredHits.size(), safeSize),
                safePage == 0,
                (safePage + 1L) * safeSize >= scoredHits.size()
        );
    }

    /**
     * 查询混合搜索候选结果，供知识库问答复用。
     *
     * @param keyword 搜索关键词或自然语言问题
     * @param tag 标签筛选
     * @param category 分类筛选
     * @param language 语言筛选
     * @param status 发布状态筛选
     * @param updatedFrom 更新时间开始日期
     * @param updatedTo 更新时间结束日期
     * @param limit 最大数量
     * @return 混合搜索结果
     */
    @Transactional(readOnly = true)
    public List<HybridSearchResultResponse> topResults(
            String keyword,
            String tag,
            String category,
            String language,
            NoteStatus status,
            LocalDate updatedFrom,
            LocalDate updatedTo,
            int limit
    ) {
        PageResponse<HybridSearchResultResponse> page = search(
                keyword,
                "all",
                tag,
                category,
                language,
                status,
                updatedFrom,
                updatedTo,
                0,
                Math.max(1, Math.min(limit, MAX_SIZE))
        );
        return page.items();
    }

    /**
     * 加载语义候选，配置缺失时降级为全文候选。
     *
     * @param keyword 关键词或自然语言问题
     * @param tag 标签筛选
     * @param category 分类筛选
     * @param language 语言筛选
     * @param status 发布状态筛选
     * @param updatedFrom 更新时间开始日期
     * @param updatedTo 更新时间结束日期
     * @param hasTextQuery 是否有文本查询
     * @return 语义候选结果
     */
    private SemanticCandidateResult loadSemanticCandidates(
            String keyword,
            String tag,
            String category,
            String language,
            NoteStatus status,
            LocalDate updatedFrom,
            LocalDate updatedTo,
            boolean hasTextQuery
    ) {
        if (!hasTextQuery) {
            return SemanticCandidateResult.empty(false, "未输入语义问题，按筛选和全文候选排序");
        }
        try {
            VectorIndexService.SemanticSearchHits semanticHits = vectorIndexService.searchSemanticHits(
                    keyword,
                    tag,
                    category,
                    language,
                    status,
                    updatedFrom,
                    updatedTo,
                    MAX_CANDIDATES
            );
            return new SemanticCandidateResult(semanticHits.hits(), true, "");
        } catch (BusinessException ex) {
            return SemanticCandidateResult.empty(false, "语义向量不可用，已降级为全文排序：" + ex.getMessage());
        }
    }

    /**
     * 合并关键词候选与语义候选。
     *
     * @param keywordHits 关键词候选
     * @param semanticHits 语义候选
     * @param hasTextQuery 是否有文本查询
     * @return 混合候选列表
     */
    private List<HybridCandidate> mergeCandidates(
            SearchService.KeywordSearchHits keywordHits,
            List<VectorIndexService.SemanticHit> semanticHits,
            boolean hasTextQuery
    ) {
        Map<Long, HybridCandidateBuilder> candidateMap = new LinkedHashMap<>();
        for (SearchService.KeywordSearchHit hit : keywordHits.hits()) {
            candidateMap.computeIfAbsent(hit.noteId(), HybridCandidateBuilder::new)
                    .keywordScore(hit.score());
        }
        if (hasTextQuery) {
            for (VectorIndexService.SemanticHit hit : semanticHits) {
                candidateMap.computeIfAbsent(hit.noteId(), HybridCandidateBuilder::new)
                        .semanticScore(hit.score())
                        .semanticPreview(hit.preview());
            }
        }
        return candidateMap.values().stream()
                .map(HybridCandidateBuilder::build)
                .toList();
    }

    /**
     * 批量加载笔记实体。
     *
     * @param candidates 候选列表
     * @return 笔记映射
     */
    private Map<Long, Note> loadNotes(List<HybridCandidate> candidates) {
        List<Long> noteIds = candidates.stream().map(HybridCandidate::noteId).toList();
        Map<Long, Note> noteMap = new HashMap<>();
        for (Note note : noteRepository.findByIdInAndDeletedFalseAndArchivedFalse(noteIds)) {
            noteMap.put(note.getId(), note);
        }
        return noteMap;
    }

    /**
     * 计算混合排序分数。
     *
     * @param candidates 候选列表
     * @param noteMap 笔记映射
     * @param keyword 关键词
     * @param tag 标签筛选
     * @param keywordHits 关键词候选结果
     * @param semanticAvailable 语义搜索是否可用
     * @param fallbackReason 降级原因
     * @param tuningSettings 搜索调优设置
     * @return 已评分命中
     */
    private List<HybridScoredHit> scoreCandidates(
            List<HybridCandidate> candidates,
            Map<Long, Note> noteMap,
            String keyword,
            String tag,
            SearchService.KeywordSearchHits keywordHits,
            boolean semanticAvailable,
            String fallbackReason,
            SearchTuningService.SearchTuningSettings tuningSettings
    ) {
        List<HybridScoredHit> scoredHits = new ArrayList<>();
        for (HybridCandidate candidate : candidates) {
            Note note = noteMap.get(candidate.noteId());
            if (note == null) {
                continue;
            }
            double keywordScore = normalizeKeywordScore(candidate.keywordScore(), keywordHits.maxScore());
            double semanticSimilarity = vectorIndexService.normalizeSimilarity(candidate.semanticScore());
            ScoreWeights weights = resolveWeights(candidate.hasKeywordHit(), candidate.hasSemanticHit(), semanticAvailable, tuningSettings);
            double titleBoost = titleHit(keyword, note) ? tuningSettings.titleHitBoost() : 0.0D;
            double tagBoost = tagHit(keyword, tag, note) ? tuningSettings.tagHitBoost() : 0.0D;
            double pinnedBoost = note.isPinned() ? tuningSettings.pinnedBoost() : 0.0D;
            double favoriteBoost = note.isFavorite() ? tuningSettings.favoriteBoost() : 0.0D;
            double recentBoost = recentBoost(note.getUpdatedAt(), tuningSettings);
            double hybridScore = roundScore(
                    keywordScore * weights.keywordWeight()
                            + semanticSimilarity * weights.semanticWeight()
                            + titleBoost
                            + tagBoost
                            + pinnedBoost
                            + favoriteBoost
                            + recentBoost
            );
            scoredHits.add(new HybridScoredHit(
                    note,
                    candidate,
                    roundScore(keywordScore),
                    roundScore(semanticSimilarity),
                    hybridScore,
                    explainRank(
                            keywordScore,
                            semanticSimilarity,
                            titleBoost,
                            tagBoost,
                            pinnedBoost,
                            favoriteBoost,
                            recentBoost,
                            semanticAvailable,
                            fallbackReason,
                            weights
                    )
            ));
        }
        return scoredHits;
    }

    /**
     * 转换为响应对象。
     *
     * @param hit 已评分命中
     * @param keywordHits 关键词候选结果
     * @param semanticResult 语义候选结果
     * @return 混合搜索结果响应
     */
    private HybridSearchResultResponse toResponse(
            HybridScoredHit hit,
            SearchService.KeywordSearchHits keywordHits,
            SemanticCandidateResult semanticResult
    ) {
        HighlightPayload highlightPayload = highlightPayload(hit, keywordHits, semanticResult);
        return HybridSearchResultResponse.from(
                hit.note(),
                highlightPayload.highlight(),
                highlightPayload.hitFields(),
                hit.keywordScore(),
                hit.semanticSimilarity(),
                hit.hybridScore(),
                hit.rankExplanation()
        );
    }

    /**
     * 构建高亮与命中字段。
     *
     * @param hit 已评分命中
     * @param keywordHits 关键词候选结果
     * @param semanticResult 语义候选结果
     * @return 高亮信息
     */
    private HighlightPayload highlightPayload(
            HybridScoredHit hit,
            SearchService.KeywordSearchHits keywordHits,
            SemanticCandidateResult semanticResult
    ) {
        Set<String> hitFields = new LinkedHashSet<>();
        String highlight = "";
        if (hit.candidate().hasKeywordHit()) {
            SearchService.SearchHighlight searchHighlight = searchService.buildHighlight(
                    hit.note(),
                    keywordHits.textQuery(),
                    keywordHits.scope()
            );
            highlight = searchHighlight.fragment();
            hitFields.addAll(searchHighlight.hitFields());
        }
        if (hit.candidate().hasSemanticHit()) {
            hitFields.add("semantic");
            if (highlight.isBlank()) {
                highlight = vectorIndexService.semanticHighlight(hit.note(), hit.candidate().semanticPreview());
            }
        }
        if (highlight.isBlank() && !semanticResult.fallbackReason().isBlank()) {
            highlight = vectorIndexService.semanticHighlight(hit.note(), hit.candidate().semanticPreview());
        }
        return new HighlightPayload(highlight, List.copyOf(hitFields));
    }

    /**
     * 根据候选来源决定基础权重。
     *
     * @param hasKeywordHit 是否有全文命中
     * @param hasSemanticHit 是否有语义命中
     * @param semanticAvailable 语义搜索是否可用
     * @param tuningSettings 搜索调优设置
     * @return 分数权重
     */
    private ScoreWeights resolveWeights(
            boolean hasKeywordHit,
            boolean hasSemanticHit,
            boolean semanticAvailable,
            SearchTuningService.SearchTuningSettings tuningSettings
    ) {
        if (hasKeywordHit && hasSemanticHit && semanticAvailable) {
            return new ScoreWeights(tuningSettings.keywordWeight(), tuningSettings.semanticWeight());
        }
        if (hasSemanticHit && semanticAvailable) {
            return new ScoreWeights(0.0D, 1.0D);
        }
        return new ScoreWeights(1.0D, 0.0D);
    }

    /**
     * 生成排序解释。
     *
     * @param keywordScore 关键词标准化得分
     * @param semanticSimilarity 语义相似度
     * @param titleBoost 标题加权
     * @param tagBoost 标签加权
     * @param pinnedBoost 置顶加权
     * @param favoriteBoost 收藏加权
     * @param recentBoost 更新时间加权
     * @param semanticAvailable 语义搜索是否可用
     * @param fallbackReason 降级原因
     * @param weights 当前使用的基础权重
     * @return 排序解释
     */
    private String explainRank(
            double keywordScore,
            double semanticSimilarity,
            double titleBoost,
            double tagBoost,
            double pinnedBoost,
            double favoriteBoost,
            double recentBoost,
            boolean semanticAvailable,
            String fallbackReason,
            ScoreWeights weights
    ) {
        List<String> parts = new ArrayList<>();
        if (keywordScore > 0) {
            parts.add("全文得分 " + formatScore(keywordScore) + " × " + formatScore(weights.keywordWeight()));
        }
        if (semanticAvailable && semanticSimilarity > 0) {
            parts.add("语义相似度 " + formatScore(semanticSimilarity) + " × " + formatScore(weights.semanticWeight()));
        }
        if (titleBoost > 0) {
            parts.add("标题命中加权");
        }
        if (tagBoost > 0) {
            parts.add("标签命中加权");
        }
        if (pinnedBoost > 0) {
            parts.add("置顶加权");
        }
        if (favoriteBoost > 0) {
            parts.add("收藏加权");
        }
        if (recentBoost > 0) {
            parts.add("最近更新加权");
        }
        if (!fallbackReason.isBlank()) {
            parts.add(fallbackReason);
        }
        return parts.isEmpty() ? "按筛选条件命中" : String.join("，", parts);
    }

    /**
     * 判断标题是否命中关键词。
     *
     * @param keyword 关键词
     * @param note 笔记
     * @return 是否命中
     */
    private boolean titleHit(String keyword, Note note) {
        String normalizedKeyword = normalizeText(keyword);
        return !normalizedKeyword.isBlank() && normalizeText(note.getTitle()).contains(normalizedKeyword);
    }

    /**
     * 判断标签是否命中。
     *
     * @param keyword 关键词
     * @param tag 标签筛选
     * @param note 笔记
     * @return 是否命中
     */
    private boolean tagHit(String keyword, String tag, Note note) {
        String normalizedKeyword = normalizeText(keyword);
        String normalizedTag = normalizeText(tag);
        return note.getTags().stream()
                .map(item -> normalizeText(item.getName()))
                .anyMatch(name -> (!normalizedTag.isBlank() && name.equals(normalizedTag))
                        || (!normalizedKeyword.isBlank() && name.contains(normalizedKeyword)));
    }

    /**
     * 计算最近更新加权。
     *
     * @param updatedAt 更新时间
     * @param tuningSettings 搜索调优设置
     * @return 最近更新加权
     */
    private double recentBoost(LocalDateTime updatedAt, SearchTuningService.SearchTuningSettings tuningSettings) {
        if (updatedAt == null) {
            return 0.0D;
        }
        long days = Duration.between(updatedAt, LocalDateTime.now()).toDays();
        if (days <= 7) {
            return tuningSettings.recentSevenDaysBoost();
        }
        if (days <= 30) {
            return tuningSettings.recentThirtyDaysBoost();
        }
        return 0.0D;
    }

    /**
     * 标准化关键词原始分。
     *
     * @param score 原始分
     * @param maxScore 最高分
     * @return 标准化分数
     */
    private double normalizeKeywordScore(float score, float maxScore) {
        if (score <= 0 || maxScore <= 0) {
            return 0.0D;
        }
        return Math.max(0.0D, Math.min(1.0D, score / maxScore));
    }

    /**
     * 提取当前页命中。
     *
     * @param hits 全部命中
     * @param page 页码
     * @param size 每页数量
     * @return 当前页命中
     */
    private List<HybridScoredHit> pageHits(List<HybridScoredHit> hits, int page, int size) {
        int fromIndex = Math.min(page * size, hits.size());
        int toIndex = Math.min(fromIndex + size, hits.size());
        return hits.subList(fromIndex, toIndex);
    }

    /**
     * 构建空分页。
     *
     * @param page 页码
     * @param size 每页数量
     * @return 空分页
     */
    private PageResponse<HybridSearchResultResponse> emptyPage(int page, int size) {
        return new PageResponse<>(List.of(), page, size, 0, 0, page == 0, true);
    }

    /**
     * 计算总页数。
     *
     * @param totalElements 总记录数
     * @param size 每页数量
     * @return 总页数
     */
    private int totalPages(long totalElements, int size) {
        return totalElements == 0 ? 0 : (int) Math.ceil((double) totalElements / size);
    }

    /**
     * 标准化每页数量。
     *
     * @param size 原始每页数量
     * @return 每页数量
     */
    private int normalizeSize(int size) {
        if (size <= 0) {
            return DEFAULT_SIZE;
        }
        return Math.min(size, MAX_SIZE);
    }

    /**
     * 标准化文本。
     *
     * @param value 原始文本
     * @return 标准化文本
     */
    private String normalizeText(String value) {
        return safeText(value).toLowerCase(Locale.ROOT);
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
     * 分数四舍五入。
     *
     * @param score 原始分数
     * @return 处理后的分数
     */
    private double roundScore(double score) {
        return Math.round(score * 10_000.0D) / 10_000.0D;
    }

    /**
     * 格式化分数。
     *
     * @param score 分数
     * @return 分数字符串
     */
    private String formatScore(double score) {
        return String.format(Locale.ROOT, "%.3f", roundScore(score));
    }

    /**
     * 语义候选结果。
     *
     * @param hits 语义命中
     * @param semanticAvailable 语义搜索是否可用
     * @param fallbackReason 降级原因
     */
    private record SemanticCandidateResult(
            List<VectorIndexService.SemanticHit> hits,
            boolean semanticAvailable,
            String fallbackReason
    ) {

        /**
         * 创建空语义候选结果。
         *
         * @param semanticAvailable 语义搜索是否可用
         * @param fallbackReason 降级原因
         * @return 空结果
         */
        private static SemanticCandidateResult empty(boolean semanticAvailable, String fallbackReason) {
            return new SemanticCandidateResult(List.of(), semanticAvailable, fallbackReason);
        }
    }

    /**
     * 混合候选构造器。
     */
    private static final class HybridCandidateBuilder {

        private final Long noteId;
        private float keywordScore;
        private float semanticScore;
        private String semanticPreview = "";

        private HybridCandidateBuilder(Long noteId) {
            this.noteId = noteId;
        }

        /**
         * 设置关键词原始分。
         *
         * @param value 原始分
         * @return 当前构造器
         */
        private HybridCandidateBuilder keywordScore(float value) {
            this.keywordScore = value;
            return this;
        }

        /**
         * 设置语义原始分。
         *
         * @param value 原始分
         * @return 当前构造器
         */
        private HybridCandidateBuilder semanticScore(float value) {
            this.semanticScore = value;
            return this;
        }

        /**
         * 设置语义预览。
         *
         * @param value 预览文本
         * @return 当前构造器
         */
        private HybridCandidateBuilder semanticPreview(String value) {
            this.semanticPreview = value == null ? "" : value;
            return this;
        }

        /**
         * 创建混合候选。
         *
         * @return 混合候选
         */
        private HybridCandidate build() {
            return new HybridCandidate(noteId, keywordScore, semanticScore, semanticPreview);
        }
    }

    /**
     * 混合候选。
     *
     * @param noteId 笔记ID
     * @param keywordScore 关键词原始分
     * @param semanticScore 语义原始分
     * @param semanticPreview 语义预览
     */
    private record HybridCandidate(Long noteId, float keywordScore, float semanticScore, String semanticPreview) {

        /**
         * 是否有全文命中。
         *
         * @return 是否有全文命中
         */
        private boolean hasKeywordHit() {
            return keywordScore > 0;
        }

        /**
         * 是否有语义命中。
         *
         * @return 是否有语义命中
         */
        private boolean hasSemanticHit() {
            return semanticScore > 0;
        }
    }

    /**
     * 已评分混合命中。
     *
     * @param note 笔记
     * @param candidate 候选
     * @param keywordScore 关键词标准化得分
     * @param semanticSimilarity 语义相似度
     * @param hybridScore 混合分数
     * @param rankExplanation 排序解释
     */
    private record HybridScoredHit(
            Note note,
            HybridCandidate candidate,
            double keywordScore,
            double semanticSimilarity,
            double hybridScore,
            String rankExplanation
    ) {
    }

    /**
     * 分数权重。
     *
     * @param keywordWeight 全文权重
     * @param semanticWeight 语义权重
     */
    private record ScoreWeights(double keywordWeight, double semanticWeight) {
    }

    /**
     * 高亮负载。
     *
     * @param highlight 高亮片段
     * @param hitFields 命中字段
     */
    private record HighlightPayload(String highlight, List<String> hitFields) {
    }
}
