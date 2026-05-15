package com.knowledgebase.service;

import com.knowledgebase.config.KnowledgeBaseProperties;
import com.knowledgebase.dto.PageResponse;
import com.knowledgebase.dto.SearchResultResponse;
import com.knowledgebase.entity.Note;
import com.knowledgebase.entity.NoteStatus;
import com.knowledgebase.entity.SearchScope;
import com.knowledgebase.exception.BusinessException;
import com.knowledgebase.repository.NoteRepository;
import com.knowledgebase.util.MarkdownTextExtractor;
import com.knowledgebase.util.SearchIndexFields;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.queryparser.classic.MultiFieldQueryParser;
import org.apache.lucene.queryparser.classic.ParseException;
import org.apache.lucene.queryparser.classic.QueryParser;
import org.apache.lucene.search.BooleanClause;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.MatchAllDocsQuery;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.search.highlight.Highlighter;
import org.apache.lucene.search.highlight.InvalidTokenOffsetsException;
import org.apache.lucene.search.highlight.QueryScorer;
import org.apache.lucene.search.highlight.SimpleHTMLEncoder;
import org.apache.lucene.search.highlight.SimpleHTMLFormatter;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.util.HtmlUtils;

/**
 * 笔记全文搜索服务。
 */
@Service
public class SearchService {

    private static final int DEFAULT_SIZE = 10;
    private static final int MAX_SIZE = 50;
    private static final int MAX_CANDIDATES = 10_000;
    private static final int MAX_HIGHLIGHT_CHARS = 8000;
    private static final ZoneId SYSTEM_ZONE = ZoneId.systemDefault();

    private final NoteRepository noteRepository;
    private final Analyzer analyzer;
    private final Path indexPath;

    /**
     * 创建全文搜索服务。
     *
     * @param noteRepository 笔记仓库
     * @param analyzer 搜索分析器
     * @param properties 知识库配置
     */
    public SearchService(NoteRepository noteRepository, Analyzer analyzer, KnowledgeBaseProperties properties) {
        this.noteRepository = noteRepository;
        this.analyzer = analyzer;
        this.indexPath = Paths.get(properties.getIndexPath()).toAbsolutePath().normalize();
    }

    /**
     * 搜索笔记。
     *
     * @param keyword 关键词
     * @param scopeValue 搜索范围
     * @param tag 标签筛选
     * @param category 分类筛选
     * @param language 语言筛选
     * @param status 发布状态筛选
     * @param updatedFrom 更新时间开始日期
     * @param updatedTo 更新时间结束日期
     * @param page 页码
     * @param size 每页数量
     * @return 搜索结果分页
     */
    @Transactional(readOnly = true)
    public PageResponse<SearchResultResponse> search(
            String keyword,
            String scopeValue,
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
        if (hasNoSearchCondition(keyword, tag, category, language, status, updatedFrom, updatedTo) || indexUnavailable()) {
            return emptyPage(safePage, safeSize);
        }
        KeywordSearchHits keywordSearchHits = searchKeywordHits(
                keyword,
                scopeValue,
                tag,
                category,
                language,
                status,
                updatedFrom,
                updatedTo,
                Math.min((safePage + 1) * safeSize, MAX_CANDIDATES)
        );
        List<KeywordSearchHit> hits = pageHits(keywordSearchHits.hits(), safePage, safeSize);
        return toPageResponse(
                hits,
                keywordSearchHits.textQuery(),
                keywordSearchHits.scope(),
                safePage,
                safeSize,
                keywordSearchHits.totalElements()
        );
    }

    /**
     * 查询关键词候选命中，供混合搜索复用。
     *
     * @param keyword 关键词
     * @param scopeValue 搜索范围
     * @param tag 标签筛选
     * @param category 分类筛选
     * @param language 语言筛选
     * @param status 发布状态筛选
     * @param updatedFrom 更新时间开始日期
     * @param updatedTo 更新时间结束日期
     * @param maxCandidates 最大候选数量
     * @return 关键词搜索候选命中
     */
    public KeywordSearchHits searchKeywordHits(
            String keyword,
            String scopeValue,
            String tag,
            String category,
            String language,
            NoteStatus status,
            LocalDate updatedFrom,
            LocalDate updatedTo,
            int maxCandidates
    ) {
        if (hasNoSearchCondition(keyword, tag, category, language, status, updatedFrom, updatedTo) || indexUnavailable()) {
            return KeywordSearchHits.empty(SearchScope.fromValue(scopeValue));
        }
        SearchScope scope = SearchScope.fromValue(scopeValue);
        try (Directory directory = FSDirectory.open(indexPath);
             DirectoryReader reader = DirectoryReader.open(directory)) {
            Query textQuery = buildTextQuery(keyword, scope);
            Query searchQuery = buildFilteredQuery(textQuery, tag, category, language, status, updatedFrom, updatedTo);
            IndexSearcher searcher = new IndexSearcher(reader);
            TopDocs topDocs = searcher.search(searchQuery, normalizeCandidateLimit(maxCandidates));
            return new KeywordSearchHits(
                    collectKeywordHits(searcher, topDocs.scoreDocs),
                    textQuery,
                    scope,
                    topDocs.totalHits.value,
                    maxScore(topDocs.scoreDocs)
            );
        } catch (IOException | ParseException ex) {
            throw new BusinessException("搜索笔记失败：" + ex.getMessage());
        }
    }

    /**
     * 构建文本搜索查询。
     *
     * @param keyword 关键词
     * @param scope 搜索范围
     * @return Lucene 查询
     * @throws ParseException 查询解析异常
     */
    private Query buildTextQuery(String keyword, SearchScope scope) throws ParseException {
        if (keyword == null || keyword.isBlank()) {
            return new MatchAllDocsQuery();
        }
        Map<String, Float> boosts = new HashMap<>();
        boosts.put(SearchIndexFields.TITLE, 2.0F);
        MultiFieldQueryParser parser = new MultiFieldQueryParser(scope.getFields(), analyzer, boosts);
        return parser.parse(QueryParser.escape(keyword.trim()));
    }

    /**
     * 构建带筛选条件的查询。
     *
     * @param textQuery 文本查询
     * @param tag 标签
     * @param category 分类
     * @param language 语言
     * @param status 发布状态
     * @param updatedFrom 更新时间开始日期
     * @param updatedTo 更新时间结束日期
     * @return Lucene 查询
     */
    private Query buildFilteredQuery(
            Query textQuery,
            String tag,
            String category,
            String language,
            NoteStatus status,
            LocalDate updatedFrom,
            LocalDate updatedTo
    ) {
        BooleanQuery.Builder builder = new BooleanQuery.Builder();
        builder.add(textQuery, BooleanClause.Occur.MUST);
        addExactFilter(builder, SearchIndexFields.TAGS, tag);
        addCategoryFilter(builder, category);
        addExactFilter(builder, SearchIndexFields.LANGUAGE, language);
        if (status != null) {
            builder.add(new TermQuery(new org.apache.lucene.index.Term(SearchIndexFields.STATUS, status.name())),
                    BooleanClause.Occur.FILTER);
        }
        addUpdatedTimeFilter(builder, updatedFrom, updatedTo);
        return builder.build();
    }

    /**
     * 添加更新时间范围筛选。
     *
     * @param builder 布尔查询构造器
     * @param updatedFrom 更新时间开始日期
     * @param updatedTo 更新时间结束日期
     */
    private void addUpdatedTimeFilter(BooleanQuery.Builder builder, LocalDate updatedFrom, LocalDate updatedTo) {
        if (updatedFrom == null && updatedTo == null) {
            return;
        }
        long from = updatedFrom == null ? Long.MIN_VALUE : toEpochMillis(updatedFrom, LocalTime.MIN);
        long to = updatedTo == null ? Long.MAX_VALUE : toEpochMillis(updatedTo.plusDays(1), LocalTime.MIN) - 1;
        builder.add(org.apache.lucene.document.LongPoint.newRangeQuery(SearchIndexFields.UPDATED_TIME, from, to),
                BooleanClause.Occur.FILTER);
    }

    /**
     * 添加精确筛选条件。
     *
     * @param builder 布尔查询构造器
     * @param fieldName 字段名
     * @param value 字段值
     */
    private void addExactFilter(BooleanQuery.Builder builder, String fieldName, String value) {
        String normalizedValue = normalizeExact(value);
        if (!normalizedValue.isBlank()) {
            builder.add(new TermQuery(new org.apache.lucene.index.Term(fieldName, normalizedValue)),
                    BooleanClause.Occur.FILTER);
        }
    }

    /**
     * 添加分类筛选条件，支持分类 ID 或分类名称。
     *
     * @param builder 布尔查询构造器
     * @param category 分类筛选值
     */
    private void addCategoryFilter(BooleanQuery.Builder builder, String category) {
        String normalizedValue = normalizeExact(category);
        if (normalizedValue.isBlank()) {
            return;
        }
        String fieldName = normalizedValue.chars().allMatch(Character::isDigit)
                ? SearchIndexFields.CATEGORY_ID
                : SearchIndexFields.CATEGORY_EXACT;
        builder.add(new TermQuery(new org.apache.lucene.index.Term(fieldName, normalizedValue)),
                BooleanClause.Occur.FILTER);
    }

    /**
     * 提取当前页命中结果。
     *
     * @param searcher 搜索器
     * @param scoreDocs 命中文档
     * @param page 页码
     * @param size 每页数量
     * @return 当前页命中结果
     * @throws IOException 读取索引异常
     */
    private List<KeywordSearchHit> collectKeywordHits(IndexSearcher searcher, ScoreDoc[] scoreDocs) throws IOException {
        List<KeywordSearchHit> hits = new ArrayList<>();
        for (ScoreDoc scoreDoc : scoreDocs) {
            var document = searcher.storedFields().document(scoreDoc.doc);
            Long noteId = Long.valueOf(document.get(SearchIndexFields.ID));
            hits.add(new KeywordSearchHit(noteId, scoreDoc.score));
        }
        return hits;
    }

    /**
     * 提取当前页命中结果。
     *
     * @param hits 全部候选命中
     * @param page 页码
     * @param size 每页数量
     * @return 当前页命中结果
     */
    private List<KeywordSearchHit> pageHits(List<KeywordSearchHit> hits, int page, int size) {
        int fromIndex = Math.min(page * size, hits.size());
        int toIndex = Math.min(fromIndex + size, hits.size());
        List<KeywordSearchHit> pageHits = new ArrayList<>();
        for (int index = fromIndex; index < toIndex; index++) {
            pageHits.add(hits.get(index));
        }
        return pageHits;
    }

    /**
     * 转换为分页响应。
     *
     * @param hits 当前页命中结果
     * @param textQuery 文本查询
     * @param scope 搜索范围
     * @param page 页码
     * @param size 每页数量
     * @param totalElements 总命中数量
     * @return 搜索分页响应
     */
    private PageResponse<SearchResultResponse> toPageResponse(
            List<KeywordSearchHit> hits,
            Query textQuery,
            SearchScope scope,
            int page,
            int size,
            long totalElements
    ) {
        if (hits.isEmpty()) {
            return new PageResponse<>(List.of(), page, size, totalElements, totalPages(totalElements, size), page == 0, true);
        }
        List<Long> noteIds = hits.stream().map(KeywordSearchHit::noteId).toList();
        Map<Long, Note> noteMap = new LinkedHashMap<>();
        for (Note note : noteRepository.findByIdInAndDeletedFalseAndArchivedFalse(noteIds)) {
            noteMap.put(note.getId(), note);
        }
        List<SearchResultResponse> items = new ArrayList<>();
        for (KeywordSearchHit hit : hits) {
            Note note = noteMap.get(hit.noteId());
            if (note == null) {
                continue;
            }
            SearchHighlight highlightResult = buildHighlight(note, textQuery, scope);
            items.add(SearchResultResponse.from(note, highlightResult.fragment(), highlightResult.hitFields()));
        }
        return new PageResponse<>(
                items,
                page,
                size,
                totalElements,
                totalPages(totalElements, size),
                page == 0,
                (page + 1L) * size >= totalElements
        );
    }

    /**
     * 构建高亮信息。
     *
     * @param note 笔记实体
     * @param textQuery 文本查询
     * @param scope 搜索范围
     * @return 高亮信息
     */
    public SearchHighlight buildHighlight(Note note, Query textQuery, SearchScope scope) {
        List<String> hitFields = new ArrayList<>();
        String firstFragment = "";
        for (String fieldName : scope.getFields()) {
            String fieldValue = fieldValue(note, fieldName);
            if (fieldValue.isBlank()) {
                continue;
            }
            Optional<String> fragment = bestFragment(textQuery, fieldName, fieldValue);
            if (fragment.isPresent()) {
                hitFields.add(toDisplayField(fieldName));
                if (firstFragment.isBlank()) {
                    firstFragment = fragment.get();
                }
            }
        }
        if (firstFragment.isBlank()) {
            firstFragment = fallbackSummary(note);
        }
        return new SearchHighlight(firstFragment, hitFields);
    }

    /**
     * 获取最佳高亮片段。
     *
     * @param textQuery 文本查询
     * @param fieldName 字段名
     * @param fieldValue 字段值
     * @return 高亮片段
     */
    private Optional<String> bestFragment(Query textQuery, String fieldName, String fieldValue) {
        try {
            Highlighter highlighter = new Highlighter(
                    new SimpleHTMLFormatter("<mark>", "</mark>"),
                    new SimpleHTMLEncoder(),
                    new QueryScorer(textQuery, fieldName)
            );
            highlighter.setMaxDocCharsToAnalyze(MAX_HIGHLIGHT_CHARS);
            String fragment = highlighter.getBestFragment(analyzer, fieldName, fieldValue);
            return fragment == null || fragment.isBlank() ? Optional.empty() : Optional.of(fragment);
        } catch (IOException | InvalidTokenOffsetsException ex) {
            return Optional.empty();
        }
    }

    /**
     * 根据字段名获取笔记内容。
     *
     * @param note 笔记实体
     * @param fieldName 字段名
     * @return 字段值
     */
    private String fieldValue(Note note, String fieldName) {
        return switch (fieldName) {
            case SearchIndexFields.TITLE -> safeText(note.getTitle());
            case SearchIndexFields.CONTENT_PLAIN -> safeText(note.getContentText());
            case SearchIndexFields.CONTENT_CODE -> note.getType().name().equals("CODE")
                    ? safeText(note.getContent())
                    : MarkdownTextExtractor.extractCodeBlocks(note.getContent());
            case SearchIndexFields.CATEGORY -> note.getCategory() == null ? "" : safeText(note.getCategory().getName());
            default -> "";
        };
    }

    /**
     * 转换命中字段展示名。
     *
     * @param fieldName 索引字段名
     * @return 展示字段名
     */
    private String toDisplayField(String fieldName) {
        return switch (fieldName) {
            case SearchIndexFields.TITLE -> "title";
            case SearchIndexFields.CONTENT_CODE -> "code";
            case SearchIndexFields.CATEGORY -> "category";
            default -> "content";
        };
    }

    /**
     * 生成兜底摘要。
     *
     * @param note 笔记实体
     * @return 摘要
     */
    private String fallbackSummary(Note note) {
        String text = safeText(note.getContentText());
        if (text.isBlank()) {
            text = safeText(note.getTitle());
        }
        String summary = text.length() <= 160 ? text : text.substring(0, 160) + "...";
        return HtmlUtils.htmlEscape(summary);
    }

    /**
     * 判断索引是否不可用。
     *
     * @return 索引是否不可用
     */
    private boolean indexUnavailable() {
        if (Files.notExists(indexPath)) {
            return true;
        }
        try (Directory directory = FSDirectory.open(indexPath)) {
            return !DirectoryReader.indexExists(directory);
        } catch (IOException ex) {
            throw new BusinessException("读取搜索索引失败：" + ex.getMessage());
        }
    }

    /**
     * 构建空分页。
     *
     * @param page 页码
     * @param size 每页数量
     * @return 空分页响应
     */
    private PageResponse<SearchResultResponse> emptyPage(int page, int size) {
        return new PageResponse<>(List.of(), page, size, 0, 0, page == 0, true);
    }

    /**
     * 判断是否没有任何搜索条件。
     *
     * @param keyword 关键词
     * @param tag 标签
     * @param category 分类
     * @param language 语言
     * @param status 发布状态
     * @param updatedFrom 更新时间开始日期
     * @param updatedTo 更新时间结束日期
     * @return 是否没有搜索条件
     */
    private boolean hasNoSearchCondition(
            String keyword,
            String tag,
            String category,
            String language,
            NoteStatus status,
            LocalDate updatedFrom,
            LocalDate updatedTo
    ) {
        return safeText(keyword).isBlank()
                && safeText(tag).isBlank()
                && safeText(category).isBlank()
                && safeText(language).isBlank()
                && status == null
                && updatedFrom == null
                && updatedTo == null;
    }

    /**
     * 将本地日期时间转换为毫秒时间戳。
     *
     * @param date 日期
     * @param time 时间
     * @return 毫秒时间戳
     */
    private long toEpochMillis(LocalDate date, LocalTime time) {
        return date.atTime(time).atZone(SYSTEM_ZONE).toInstant().toEpochMilli();
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
     * 标准化候选召回数量。
     *
     * @param maxCandidates 原始候选数量
     * @return 候选召回数量
     */
    private int normalizeCandidateLimit(int maxCandidates) {
        if (maxCandidates <= 0) {
            return DEFAULT_SIZE;
        }
        return Math.min(maxCandidates, MAX_CANDIDATES);
    }

    /**
     * 计算命中列表最高原始分。
     *
     * @param scoreDocs Lucene 命中
     * @return 最高分
     */
    private float maxScore(ScoreDoc[] scoreDocs) {
        float maxScore = 0.0F;
        for (ScoreDoc scoreDoc : scoreDocs) {
            maxScore = Math.max(maxScore, scoreDoc.score);
        }
        return maxScore;
    }

    /**
     * 标准化精确匹配字段。
     *
     * @param value 原始值
     * @return 标准化值
     */
    private String normalizeExact(String value) {
        return safeText(value).toLowerCase(Locale.ROOT);
    }

    /**
     * 获取安全文本。
     *
     * @param value 原始值
     * @return 安全文本
     */
    private String safeText(String value) {
        return value == null ? "" : value.trim();
    }

    /**
     * 关键词搜索候选结果。
     *
     * @param hits 候选命中
     * @param textQuery 文本查询
     * @param scope 搜索范围
     * @param totalElements 总命中数
     * @param maxScore 最高原始分
     */
    public record KeywordSearchHits(
            List<KeywordSearchHit> hits,
            Query textQuery,
            SearchScope scope,
            long totalElements,
            float maxScore
    ) {

        /**
         * 创建空候选结果。
         *
         * @param scope 搜索范围
         * @return 空候选结果
         */
        private static KeywordSearchHits empty(SearchScope scope) {
            return new KeywordSearchHits(List.of(), new MatchAllDocsQuery(), scope, 0, 0.0F);
        }
    }

    /**
     * 关键词搜索命中。
     *
     * @param noteId 笔记ID
     * @param score Lucene 原始得分
     */
    public record KeywordSearchHit(Long noteId, float score) {
    }

    /**
     * 高亮结果。
     *
     * @param fragment 高亮片段
     * @param hitFields 命中字段
     */
    public record SearchHighlight(String fragment, List<String> hitFields) {
    }
}
