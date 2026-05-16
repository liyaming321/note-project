package com.knowledgebase.service;

import com.knowledgebase.config.KnowledgeBaseProperties;
import com.knowledgebase.dto.AdminVectorIndexInfoResponse;
import com.knowledgebase.dto.AdminVectorReindexResponse;
import com.knowledgebase.dto.EmbeddingProviderResponse;
import com.knowledgebase.dto.PageResponse;
import com.knowledgebase.dto.SemanticSearchResultResponse;
import com.knowledgebase.entity.Category;
import com.knowledgebase.entity.Note;
import com.knowledgebase.entity.NoteStatus;
import com.knowledgebase.entity.Tag;
import com.knowledgebase.exception.BusinessException;
import com.knowledgebase.repository.NoteRepository;
import com.knowledgebase.util.VectorIndexFields;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.KnnFloatVectorField;
import org.apache.lucene.document.LongPoint;
import org.apache.lucene.document.StoredField;
import org.apache.lucene.document.StringField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.LeafReader;
import org.apache.lucene.index.LeafReaderContext;
import org.apache.lucene.index.SegmentInfos;
import org.apache.lucene.index.Term;
import org.apache.lucene.index.VectorSimilarityFunction;
import org.apache.lucene.search.BooleanClause;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.KnnFloatVectorQuery;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;
import org.apache.lucene.util.Bits;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.util.HtmlUtils;

/**
 * Lucene 向量索引维护服务。
 */
@Service
public class VectorIndexService {

    private static final String META_PROVIDER = "provider";
    private static final String META_MODEL = "model";
    private static final String META_DIMENSION = "dimension";
    private static final String META_POOLING = "pooling";
    private static final String META_NORMALIZE = "normalize";
    private static final String META_LAST_REBUILT_AT = "lastRebuiltAt";
    private static final int MAX_EMBEDDING_TEXT_LENGTH = 6000;
    private static final int MAX_PREVIEW_LENGTH = 300;
    private static final int DEFAULT_SIZE = 10;
    private static final int MAX_SIZE = 50;
    private static final int MAX_VECTOR_CANDIDATES = 10_000;
    private static final ZoneId SYSTEM_ZONE = ZoneId.systemDefault();

    private final NoteRepository noteRepository;
    private final EmbeddingProvider embeddingProvider;
    private final Path vectorIndexPath;

    /**
     * 创建向量索引服务。
     *
     * @param noteRepository 笔记仓库
     * @param embeddingProvider Embedding 供应商
     * @param properties 知识库配置
     */
    public VectorIndexService(
            NoteRepository noteRepository,
            EmbeddingProvider embeddingProvider,
            KnowledgeBaseProperties properties
    ) {
        this.noteRepository = noteRepository;
        this.embeddingProvider = embeddingProvider;
        this.vectorIndexPath = Paths.get(properties.getVectorIndexPath()).toAbsolutePath().normalize();
    }

    /**
     * 获取 Embedding 供应商配置状态。
     *
     * @return 供应商配置状态
     */
    public EmbeddingProviderResponse providerInfo() {
        return new EmbeddingProviderResponse(
                embeddingProvider.name(),
                embeddingProvider.model(),
                embeddingProvider.configured(),
                embeddingProvider.statusMessage()
        );
    }

    /**
     * 获取向量索引维护信息。
     *
     * @return 向量索引维护信息
     */
    public AdminVectorIndexInfoResponse info() {
        VectorIndexMetadata metadata = readMetadata();
        boolean available = metadata.available();
        boolean configured = embeddingProvider.configured();
        return new AdminVectorIndexInfoResponse(
                vectorIndexPath.toString(),
                metadata.provider().isBlank() ? embeddingProvider.name() : metadata.provider(),
                metadata.model().isBlank() ? embeddingProvider.model() : metadata.model(),
                metadata.dimension(),
                metadata.pooling().isBlank() ? embeddingProvider.pooling() : metadata.pooling(),
                metadata.normalize() == null ? embeddingProvider.normalize() : metadata.normalize(),
                metadata.indexedCount(),
                configured,
                available,
                metadata.lastRebuiltAt(),
                resolveStatusMessage(configured, available)
        );
    }

    /**
     * 更新或创建单篇笔记向量索引。未配置 Embedding 时静默跳过。
     *
     * @param note 笔记实体
     */
    public synchronized void upsertNote(Note note) {
        if (!embeddingProvider.configured() || note == null || note.getId() == null) {
            return;
        }
        if (note.isDeleted() || note.isArchived()) {
            deleteNote(note.getId());
            return;
        }
        float[] vector;
        try {
            vector = embeddingProvider.embed(buildEmbeddingText(note));
        } catch (BusinessException ex) {
            return;
        }
        VectorIndexMetadata metadata = readMetadata();
        if (metadata.dimension() != null && metadata.dimension() != vector.length) {
            deleteNote(note.getId());
            return;
        }
        try (Directory directory = openDirectory();
             IndexWriter writer = createWriter(directory, IndexWriterConfig.OpenMode.CREATE_OR_APPEND)) {
            writer.updateDocument(noteIdTerm(note.getId()), toDocument(note, vector));
            writer.setLiveCommitData(commitData(vector.length, metadata.indexedCount(), metadata.lastRebuiltAt()));
        } catch (IOException ex) {
            throw new BusinessException("更新向量索引失败：" + ex.getMessage());
        }
    }

    /**
     * 删除单篇笔记向量索引。
     *
     * @param noteId 笔记ID
     */
    public synchronized void deleteNote(Long noteId) {
        if (noteId == null || Files.notExists(vectorIndexPath)) {
            return;
        }
        try (Directory directory = openDirectory()) {
            if (!DirectoryReader.indexExists(directory)) {
                return;
            }
            VectorIndexMetadata metadata = readMetadata(directory);
            try (IndexWriter writer = createWriter(directory, IndexWriterConfig.OpenMode.CREATE_OR_APPEND)) {
                writer.deleteDocuments(noteIdTerm(noteId));
                writer.setLiveCommitData(commitData(metadata.dimension(), metadata.indexedCount(), metadata.lastRebuiltAt()));
            }
        } catch (IOException ex) {
            throw new BusinessException("删除向量索引失败：" + ex.getMessage());
        }
    }

    /**
     * 全量重建向量索引。
     *
     * @return 重建结果
     */
    @Transactional(readOnly = true)
    public synchronized AdminVectorReindexResponse rebuild() {
        if (!embeddingProvider.configured()) {
            throw new BusinessException(embeddingProvider.statusMessage());
        }
        List<Note> notes = noteRepository.findByDeletedFalseAndArchivedFalse();
        int dimension = 0;
        int indexedCount = 0;
        try (Directory directory = openDirectory();
             IndexWriter writer = createWriter(directory, IndexWriterConfig.OpenMode.CREATE)) {
            for (List<Note> batch : partitionNotes(notes, embeddingProvider.batchSize())) {
                List<String> embeddingTexts = batch.stream()
                        .map(this::buildEmbeddingText)
                        .toList();
                List<float[]> vectors = embeddingProvider.embedAll(embeddingTexts);
                for (int index = 0; index < batch.size(); index++) {
                    Note note = batch.get(index);
                    float[] vector = vectors.get(index);
                    if (dimension == 0) {
                        dimension = vector.length;
                    } else if (dimension != vector.length) {
                        throw new BusinessException("Embedding 模型返回的向量维度不一致，请检查模型配置");
                    }
                    writer.addDocument(toDocument(note, vector));
                    indexedCount++;
                }
            }
            writer.setLiveCommitData(commitData(dimension, indexedCount, LocalDateTime.now().toString()));
        } catch (IOException ex) {
            throw new BusinessException("重建向量索引失败：" + ex.getMessage());
        }
        return new AdminVectorReindexResponse(
                indexedCount,
                vectorIndexPath.toString(),
                embeddingProvider.name(),
                embeddingProvider.model(),
                dimension
        );
    }

    /**
     * 获取向量索引目录。
     *
     * @return 向量索引目录
     */
    public Path getVectorIndexPath() {
        return vectorIndexPath;
    }

    /**
     * 获取向量索引文档数量。
     *
     * @return 文档数量
     */
    public int indexedCount() {
        return readMetadata().indexedCount();
    }

    /**
     * 清理数据库中已无效的向量索引文档。
     *
     * @param activeNoteIds 当前有效笔记ID
     * @return 清理数量
     */
    public synchronized int cleanupInvalidVectors(Collection<Long> activeNoteIds) {
        if (Files.notExists(vectorIndexPath)) {
            return 0;
        }
        Set<String> activeIds = new HashSet<>();
        for (Long noteId : activeNoteIds) {
            if (noteId != null) {
                activeIds.add(String.valueOf(noteId));
            }
        }
        try (Directory directory = openDirectory()) {
            if (!DirectoryReader.indexExists(directory)) {
                return 0;
            }
            VectorIndexMetadata metadata = readMetadata(directory);
            List<String> indexedIds = collectIndexedNoteIds(directory);
            List<String> invalidIds = indexedIds.stream()
                    .filter(noteId -> !activeIds.contains(noteId))
                    .toList();
            if (invalidIds.isEmpty()) {
                return 0;
            }
            try (IndexWriter writer = createWriter(directory, IndexWriterConfig.OpenMode.CREATE_OR_APPEND)) {
                for (String invalidId : invalidIds) {
                    writer.deleteDocuments(new Term(VectorIndexFields.ID, invalidId));
                }
                writer.setLiveCommitData(commitData(
                        metadata.dimension(),
                        Math.max(metadata.indexedCount() - invalidIds.size(), 0),
                        metadata.lastRebuiltAt()
                ));
            }
            return invalidIds.size();
        } catch (IOException ex) {
            throw new BusinessException("清理向量索引失败：" + ex.getMessage());
        }
    }

    /**
     * 基于指定笔记内容查询相似向量候选。
     *
     * @param note 笔记
     * @param maxCandidates 最大候选数量
     * @return 语义候选
     */
    public SemanticSearchHits searchSimilarNoteHits(Note note, int maxCandidates) {
        if (note == null || note.getId() == null) {
            return SemanticSearchHits.empty();
        }
        if (!embeddingProvider.configured()) {
            throw new BusinessException(embeddingProvider.statusMessage());
        }
        VectorIndexMetadata metadata = readMetadata();
        if (!metadata.available() || metadata.indexedCount() == 0) {
            return SemanticSearchHits.empty();
        }
        float[] queryVector = embeddingProvider.embed(buildEmbeddingText(note));
        validateQueryDimension(metadata, queryVector);
        try (Directory directory = openDirectory();
             DirectoryReader reader = DirectoryReader.open(directory)) {
            IndexSearcher searcher = new IndexSearcher(reader);
            int candidateCount = Math.min(metadata.indexedCount(), normalizeCandidateLimit(maxCandidates));
            KnnFloatVectorQuery vectorQuery = new KnnFloatVectorQuery(VectorIndexFields.VECTOR, queryVector, candidateCount);
            TopDocs topDocs = searcher.search(vectorQuery, candidateCount);
            List<SemanticHit> hits = collectSemanticHits(searcher, topDocs.scoreDocs).stream()
                    .filter(hit -> !note.getId().equals(hit.noteId()))
                    .toList();
            return new SemanticSearchHits(hits, hits.size());
        } catch (IOException ex) {
            throw new BusinessException("查询相似向量失败：" + ex.getMessage());
        }
    }

    /**
     * 根据自然语言问题执行语义搜索。
     *
     * @param question 自然语言问题
     * @param tag 标签筛选
     * @param category 分类筛选，支持分类ID或分类名
     * @param language 语言筛选
     * @param status 发布状态筛选
     * @param updatedFrom 更新时间开始日期
     * @param updatedTo 更新时间结束日期
     * @param page 页码
     * @param size 每页数量
     * @return 语义搜索结果分页
     */
    @Transactional(readOnly = true)
    public PageResponse<SemanticSearchResultResponse> semanticSearch(
            String question,
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
        String safeQuestion = safeText(question);
        if (safeQuestion.isBlank()) {
            throw new BusinessException("语义搜索问题不能为空");
        }
        if (!embeddingProvider.configured()) {
            throw new BusinessException(embeddingProvider.statusMessage());
        }
        SemanticSearchHits searchHits = searchSemanticHits(
                safeQuestion,
                tag,
                category,
                language,
                status,
                updatedFrom,
                updatedTo,
                Math.min((safePage + 1) * safeSize, MAX_VECTOR_CANDIDATES)
        );
        List<SemanticHit> hits = pageSemanticHits(searchHits.hits(), safePage, safeSize);
        return toSemanticPageResponse(hits, safeQuestion, safePage, safeSize, searchHits.totalElements());
    }

    /**
     * 查询语义搜索候选命中，供混合搜索复用。
     *
     * @param question 自然语言问题
     * @param tag 标签筛选
     * @param category 分类筛选，支持分类ID或分类名
     * @param language 语言筛选
     * @param status 发布状态筛选
     * @param updatedFrom 更新时间开始日期
     * @param updatedTo 更新时间结束日期
     * @param maxCandidates 最大候选数量
     * @return 语义搜索候选命中
     */
    public SemanticSearchHits searchSemanticHits(
            String question,
            String tag,
            String category,
            String language,
            NoteStatus status,
            LocalDate updatedFrom,
            LocalDate updatedTo,
            int maxCandidates
    ) {
        String safeQuestion = safeText(question);
        if (safeQuestion.isBlank()) {
            return SemanticSearchHits.empty();
        }
        if (!embeddingProvider.configured()) {
            throw new BusinessException(embeddingProvider.statusMessage());
        }
        VectorIndexMetadata metadata = readMetadata();
        if (!metadata.available() || metadata.indexedCount() == 0) {
            return SemanticSearchHits.empty();
        }
        float[] queryVector = embeddingProvider.embed(safeQuestion);
        validateQueryDimension(metadata, queryVector);
        try (Directory directory = openDirectory();
             DirectoryReader reader = DirectoryReader.open(directory)) {
            IndexSearcher searcher = new IndexSearcher(reader);
            Query filterQuery = buildSemanticFilterQuery(tag, category, language, status, updatedFrom, updatedTo);
            int candidateCount = Math.min(metadata.indexedCount(), normalizeCandidateLimit(maxCandidates));
            KnnFloatVectorQuery vectorQuery = filterQuery == null
                    ? new KnnFloatVectorQuery(VectorIndexFields.VECTOR, queryVector, candidateCount)
                    : new KnnFloatVectorQuery(VectorIndexFields.VECTOR, queryVector, candidateCount, filterQuery);
            TopDocs topDocs = searcher.search(vectorQuery, candidateCount);
            return new SemanticSearchHits(
                    collectSemanticHits(searcher, topDocs.scoreDocs),
                    topDocs.totalHits.value
            );
        } catch (IOException ex) {
            throw new BusinessException("语义搜索失败：" + ex.getMessage());
        }
    }

    /**
     * 创建 Lucene 文档。
     *
     * @param note 笔记实体
     * @param vector 向量
     * @return Lucene 文档
     */
    private org.apache.lucene.document.Document toDocument(Note note, float[] vector) {
        org.apache.lucene.document.Document document = new org.apache.lucene.document.Document();
        document.add(new StringField(VectorIndexFields.ID, String.valueOf(note.getId()), Field.Store.YES));
        document.add(new StoredField(VectorIndexFields.TITLE, safeText(note.getTitle())));
        document.add(new StoredField(VectorIndexFields.CONTENT_PREVIEW, limitText(note.getContentText(), MAX_PREVIEW_LENGTH)));
        addCategoryFields(document, note.getCategory());
        addOptionalString(document, VectorIndexFields.LANGUAGE, normalizeExact(note.getLanguage()));
        document.add(new StringField(VectorIndexFields.STATUS, note.getStatus().name(), Field.Store.YES));
        for (Tag tag : note.getTags()) {
            addOptionalString(document, VectorIndexFields.TAGS, normalizeExact(tag.getName()));
        }
        document.add(new LongPoint(VectorIndexFields.UPDATED_TIME, updatedTime(note)));
        document.add(new KnnFloatVectorField(VectorIndexFields.VECTOR, vector, VectorSimilarityFunction.COSINE));
        return document;
    }

    /**
     * 添加分类筛选字段。
     *
     * @param document Lucene 文档
     * @param category 分类
     */
    private void addCategoryFields(org.apache.lucene.document.Document document, Category category) {
        if (category == null) {
            return;
        }
        document.add(new StringField(VectorIndexFields.CATEGORY_ID, String.valueOf(category.getId()), Field.Store.YES));
        document.add(new StringField(VectorIndexFields.CATEGORY_EXACT, normalizeExact(category.getName()), Field.Store.YES));
    }

    /**
     * 添加可选精确匹配字段。
     *
     * @param document Lucene 文档
     * @param fieldName 字段名
     * @param value 字段值
     */
    private void addOptionalString(org.apache.lucene.document.Document document, String fieldName, String value) {
        if (!safeText(value).isBlank()) {
            document.add(new StringField(fieldName, value, Field.Store.YES));
        }
    }

    /**
     * 构建 Embedding 输入文本。
     *
     * @param note 笔记实体
     * @return 输入文本
     */
    private String buildEmbeddingText(Note note) {
        List<String> parts = new ArrayList<>();
        parts.add("标题：" + safeText(note.getTitle()));
        if (!safeText(note.getSummary()).isBlank()) {
            parts.add("摘要：" + safeText(note.getSummary()));
        }
        Category category = note.getCategory();
        if (category != null) {
            parts.add("分类：" + safeText(category.getName()));
        }
        String tagNames = note.getTags().stream()
                .map(Tag::getName)
                .map(this::safeText)
                .filter(value -> !value.isBlank())
                .reduce((left, right) -> left + "、" + right)
                .orElse("");
        if (!tagNames.isBlank()) {
            parts.add("标签：" + tagNames);
        }
        parts.add("正文：" + safeText(note.getContentText()));
        return limitText(String.join("\n", parts), MAX_EMBEDDING_TEXT_LENGTH);
    }

    /**
     * 创建提交元信息。
     *
     * @param dimension 向量维度
     * @param indexedCount 已索引数量
     * @param lastRebuiltAt 最近重建时间
     * @return 提交元信息
     */
    private Iterable<Map.Entry<String, String>> commitData(Integer dimension, int indexedCount, String lastRebuiltAt) {
        return Map.of(
                META_PROVIDER, embeddingProvider.name(),
                META_MODEL, embeddingProvider.model(),
                META_DIMENSION, String.valueOf(dimension == null ? 0 : dimension),
                META_POOLING, embeddingProvider.pooling(),
                META_NORMALIZE, String.valueOf(embeddingProvider.normalize()),
                META_LAST_REBUILT_AT, safeText(lastRebuiltAt)
        ).entrySet();
    }

    /**
     * 构建语义搜索筛选条件。
     *
     * @param tag 标签
     * @param category 分类
     * @param language 语言
     * @param status 发布状态
     * @param updatedFrom 更新时间开始日期
     * @param updatedTo 更新时间结束日期
     * @return 筛选查询
     */
    private Query buildSemanticFilterQuery(
            String tag,
            String category,
            String language,
            NoteStatus status,
            LocalDate updatedFrom,
            LocalDate updatedTo
    ) {
        BooleanQuery.Builder builder = new BooleanQuery.Builder();
        addExactFilter(builder, VectorIndexFields.TAGS, tag);
        addCategoryFilter(builder, category);
        addExactFilter(builder, VectorIndexFields.LANGUAGE, language);
        if (status != null) {
            builder.add(new TermQuery(new Term(VectorIndexFields.STATUS, status.name())), BooleanClause.Occur.FILTER);
        }
        addUpdatedTimeFilter(builder, updatedFrom, updatedTo);
        BooleanQuery query = builder.build();
        return query.clauses().isEmpty() ? null : query;
    }

    /**
     * 添加精确筛选条件。
     *
     * @param builder 查询构造器
     * @param fieldName 字段名
     * @param value 字段值
     */
    private void addExactFilter(BooleanQuery.Builder builder, String fieldName, String value) {
        String normalizedValue = normalizeExact(value);
        if (!normalizedValue.isBlank()) {
            builder.add(new TermQuery(new Term(fieldName, normalizedValue)), BooleanClause.Occur.FILTER);
        }
    }

    /**
     * 添加分类筛选条件。
     *
     * @param builder 查询构造器
     * @param category 分类ID或分类名
     */
    private void addCategoryFilter(BooleanQuery.Builder builder, String category) {
        String normalizedValue = normalizeExact(category);
        if (normalizedValue.isBlank()) {
            return;
        }
        String fieldName = normalizedValue.chars().allMatch(Character::isDigit)
                ? VectorIndexFields.CATEGORY_ID
                : VectorIndexFields.CATEGORY_EXACT;
        builder.add(new TermQuery(new Term(fieldName, normalizedValue)), BooleanClause.Occur.FILTER);
    }

    /**
     * 添加更新时间范围筛选。
     *
     * @param builder 查询构造器
     * @param updatedFrom 更新时间开始日期
     * @param updatedTo 更新时间结束日期
     */
    private void addUpdatedTimeFilter(BooleanQuery.Builder builder, LocalDate updatedFrom, LocalDate updatedTo) {
        if (updatedFrom == null && updatedTo == null) {
            return;
        }
        long from = updatedFrom == null ? Long.MIN_VALUE : toEpochMillis(updatedFrom, LocalTime.MIN);
        long to = updatedTo == null ? Long.MAX_VALUE : toEpochMillis(updatedTo.plusDays(1), LocalTime.MIN) - 1;
        builder.add(LongPoint.newRangeQuery(VectorIndexFields.UPDATED_TIME, from, to), BooleanClause.Occur.FILTER);
    }

    /**
     * 收集语义候选命中。
     *
     * @param searcher 搜索器
     * @param scoreDocs 命中文档
     * @return 语义候选命中
     * @throws IOException 读取索引异常
     */
    private List<SemanticHit> collectSemanticHits(IndexSearcher searcher, ScoreDoc[] scoreDocs)
            throws IOException {
        List<SemanticHit> hits = new ArrayList<>();
        for (ScoreDoc scoreDoc : scoreDocs) {
            var document = searcher.storedFields().document(scoreDoc.doc);
            hits.add(new SemanticHit(
                    Long.valueOf(document.get(VectorIndexFields.ID)),
                    scoreDoc.score,
                    safeText(document.get(VectorIndexFields.CONTENT_PREVIEW))
            ));
        }
        return hits;
    }

    /**
     * 收集当前向量索引中的笔记ID。
     *
     * @param directory 索引目录
     * @return 笔记ID列表
     * @throws IOException 读取异常
     */
    private List<String> collectIndexedNoteIds(Directory directory) throws IOException {
        try (DirectoryReader reader = DirectoryReader.open(directory)) {
            List<String> noteIds = new ArrayList<>();
            for (LeafReaderContext context : reader.leaves()) {
                LeafReader leafReader = context.reader();
                Bits liveDocs = leafReader.getLiveDocs();
                for (int docId = 0; docId < leafReader.maxDoc(); docId++) {
                    if (liveDocs != null && !liveDocs.get(docId)) {
                        continue;
                    }
                    var document = leafReader.document(docId);
                    String noteId = safeText(document.get(VectorIndexFields.ID));
                    if (!noteId.isBlank()) {
                        noteIds.add(noteId);
                    }
                }
            }
            return noteIds;
        }
    }

    /**
     * 提取当前页语义命中。
     *
     * @param hits 全部候选命中
     * @param page 页码
     * @param size 每页数量
     * @return 当前页命中
     */
    private List<SemanticHit> pageSemanticHits(List<SemanticHit> hits, int page, int size) {
        int fromIndex = Math.min(page * size, hits.size());
        int toIndex = Math.min(fromIndex + size, hits.size());
        List<SemanticHit> pageHits = new ArrayList<>();
        for (int index = fromIndex; index < toIndex; index++) {
            pageHits.add(hits.get(index));
        }
        return pageHits;
    }

    /**
     * 转换语义命中为分页响应。
     *
     * @param hits 命中列表
     * @param question 搜索问题
     * @param page 页码
     * @param size 每页数量
     * @param totalElements 总命中数
     * @return 分页响应
     */
    private PageResponse<SemanticSearchResultResponse> toSemanticPageResponse(
            List<SemanticHit> hits,
            String question,
            int page,
            int size,
            long totalElements
    ) {
        if (hits.isEmpty()) {
            return emptySemanticPage(page, size);
        }
        List<Long> noteIds = hits.stream().map(SemanticHit::noteId).toList();
        Map<Long, Note> noteMap = new LinkedHashMap<>();
        for (Note note : noteRepository.findByIdInAndDeletedFalseAndArchivedFalse(noteIds)) {
            noteMap.put(note.getId(), note);
        }
        List<SemanticSearchResultResponse> items = new ArrayList<>();
        for (SemanticHit hit : hits) {
            Note note = noteMap.get(hit.noteId());
            if (note == null) {
                continue;
            }
            items.add(SemanticSearchResultResponse.from(
                    note,
                    semanticHighlight(note, hit.preview()),
                    normalizeSimilarity(hit.score()),
                    matchReason(question, note, hit.score())
            ));
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
     * 构造语义匹配摘要。
     *
     * @param note 笔记
     * @param preview 索引预览
     * @return 摘要
     */
    public String semanticHighlight(Note note, String preview) {
        String text = safeText(preview).isBlank() ? safeText(note.getContentText()) : safeText(preview);
        if (text.isBlank()) {
            text = safeText(note.getTitle());
        }
        return HtmlUtils.htmlEscape(limitText(text, 160));
    }

    /**
     * 构造语义匹配原因。
     *
     * @param question 搜索问题
     * @param note 笔记
     * @param score 原始分数
     * @return 匹配原因
     */
    public String matchReason(String question, Note note, float score) {
        String category = note.getCategory() == null ? "" : safeText(note.getCategory().getName());
        String context = category.isBlank() ? safeText(note.getTitle()) : safeText(note.getTitle()) + " / " + category;
        return "问题「" + limitText(question, 40) + "」与「" + limitText(context, 80)
                + "」的语义向量相近，相似度 " + String.format(Locale.ROOT, "%.3f", normalizeSimilarity(score));
    }

    /**
     * 校验查询向量维度。
     *
     * @param metadata 索引元信息
     * @param queryVector 查询向量
     */
    private void validateQueryDimension(VectorIndexMetadata metadata, float[] queryVector) {
        if (metadata.dimension() == null) {
            return;
        }
        if (metadata.dimension() != queryVector.length) {
            throw new BusinessException("当前 Embedding 模型维度与向量索引不一致，请重建向量索引");
        }
    }

    /**
     * 构建空语义搜索分页。
     *
     * @param page 页码
     * @param size 每页数量
     * @return 空分页
     */
    private PageResponse<SemanticSearchResultResponse> emptySemanticPage(int page, int size) {
        return new PageResponse<>(List.of(), page, size, 0, 0, page == 0, true);
    }

    /**
     * 读取向量索引元信息。
     *
     * @return 元信息
     */
    private VectorIndexMetadata readMetadata() {
        if (Files.notExists(vectorIndexPath)) {
            return VectorIndexMetadata.empty();
        }
        try (Directory directory = FSDirectory.open(vectorIndexPath)) {
            if (!DirectoryReader.indexExists(directory)) {
                return VectorIndexMetadata.empty();
            }
            return readMetadata(directory);
        } catch (IOException ex) {
            throw new BusinessException("读取向量索引状态失败：" + ex.getMessage());
        }
    }

    /**
     * 从 Lucene 目录读取元信息。
     *
     * @param directory Lucene 目录
     * @return 元信息
     * @throws IOException 读取异常
     */
    private VectorIndexMetadata readMetadata(Directory directory) throws IOException {
        SegmentInfos segmentInfos = SegmentInfos.readLatestCommit(directory);
        Map<String, String> userData = segmentInfos.getUserData();
        int indexedCount;
        try (DirectoryReader reader = DirectoryReader.open(directory)) {
            indexedCount = reader.numDocs();
        }
        return new VectorIndexMetadata(
                safeText(userData.get(META_PROVIDER)),
                safeText(userData.get(META_MODEL)),
                parseDimension(userData.get(META_DIMENSION)),
                safeText(userData.get(META_POOLING)),
                parseBoolean(userData.get(META_NORMALIZE)),
                indexedCount,
                safeText(userData.get(META_LAST_REBUILT_AT)),
                true
        );
    }

    /**
     * 打开 Lucene 向量索引目录。
     *
     * @return Lucene 目录
     * @throws IOException 文件异常
     */
    private Directory openDirectory() throws IOException {
        Files.createDirectories(vectorIndexPath);
        return FSDirectory.open(vectorIndexPath);
    }

    /**
     * 按批次切分笔记列表。
     *
     * @param notes 笔记列表
     * @param batchSize 批次大小
     * @return 批次列表
     */
    private List<List<Note>> partitionNotes(List<Note> notes, int batchSize) {
        int safeBatchSize = Math.max(batchSize, 1);
        List<List<Note>> partitions = new ArrayList<>();
        for (int start = 0; start < notes.size(); start += safeBatchSize) {
            int end = Math.min(start + safeBatchSize, notes.size());
            partitions.add(notes.subList(start, end));
        }
        return partitions;
    }

    /**
     * 创建索引写入器。
     *
     * @param directory Lucene 目录
     * @param openMode 打开模式
     * @return 索引写入器
     */
    private IndexWriter createWriter(Directory directory, IndexWriterConfig.OpenMode openMode) throws IOException {
        IndexWriterConfig config = new IndexWriterConfig();
        config.setOpenMode(openMode);
        return new IndexWriter(directory, config);
    }

    /**
     * 构建笔记 ID 查询条件。
     *
     * @param noteId 笔记ID
     * @return Term
     */
    private Term noteIdTerm(Long noteId) {
        return new Term(VectorIndexFields.ID, String.valueOf(noteId));
    }

    /**
     * 解析向量维度。
     *
     * @param value 原始值
     * @return 维度
     */
    private Integer parseDimension(String value) {
        try {
            int dimension = Integer.parseInt(safeText(value));
            return dimension > 0 ? dimension : null;
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    /**
     * 解析布尔值。
     *
     * @param value 原始值
     * @return 布尔值
     */
    private Boolean parseBoolean(String value) {
        String safeValue = safeText(value);
        return safeValue.isBlank() ? null : Boolean.parseBoolean(safeValue);
    }

    /**
     * 解析状态说明。
     *
     * @param configured 是否配置
     * @param available 索引是否可用
     * @return 状态说明
     */
    private String resolveStatusMessage(boolean configured, boolean available) {
        if (!configured) {
            return embeddingProvider.statusMessage();
        }
        if (!available) {
            return "向量索引尚未创建，请先重建向量索引";
        }
        return "向量索引可用";
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
     * 截断文本。
     *
     * @param value 原始文本
     * @param maxLength 最大长度
     * @return 截断文本
     */
    private String limitText(String value, int maxLength) {
        String safeValue = safeText(value);
        return safeValue.length() <= maxLength ? safeValue : safeValue.substring(0, maxLength);
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
     * 获取更新时间时间戳。
     *
     * @param note 笔记
     * @return 毫秒时间戳
     */
    private long updatedTime(Note note) {
        if (note.getUpdatedAt() == null) {
            return System.currentTimeMillis();
        }
        return note.getUpdatedAt().atZone(SYSTEM_ZONE).toInstant().toEpochMilli();
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
        return Math.min(maxCandidates, MAX_VECTOR_CANDIDATES);
    }

    /**
     * 标准化 Lucene 向量分数。
     *
     * @param score 原始分数
     * @return 相似度
     */
    public double normalizeSimilarity(float score) {
        return Math.max(0.0D, Math.min(1.0D, score));
    }

    /**
     * 语义搜索候选结果。
     *
     * @param hits 候选命中
     * @param totalElements 总命中数
     */
    public record SemanticSearchHits(List<SemanticHit> hits, long totalElements) {

        /**
         * 创建空候选结果。
         *
         * @return 空候选结果
         */
        private static SemanticSearchHits empty() {
            return new SemanticSearchHits(List.of(), 0);
        }
    }

    /**
     * 语义命中。
     *
     * @param noteId 笔记ID
     * @param score 相似度分数
     * @param preview 内容预览
     */
    public record SemanticHit(Long noteId, float score, String preview) {
    }

    /**
     * 向量索引元信息。
     *
     * @param provider 供应商
     * @param model 模型
     * @param dimension 维度
     * @param pooling 池化
     * @param normalize 是否归一化
     * @param indexedCount 已索引数量
     * @param lastRebuiltAt 最近重建时间
     * @param available 是否可用
     */
    private record VectorIndexMetadata(
            String provider,
            String model,
            Integer dimension,
            String pooling,
            Boolean normalize,
            int indexedCount,
            String lastRebuiltAt,
            boolean available
    ) {

        /**
         * 创建空元信息。
         *
         * @return 空元信息
         */
        private static VectorIndexMetadata empty() {
            return new VectorIndexMetadata("", "", null, "", null, 0, "", false);
        }
    }
}
