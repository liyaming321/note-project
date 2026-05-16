package com.knowledgebase.service;

import com.knowledgebase.config.KnowledgeBaseProperties;
import com.knowledgebase.dto.SimilarNoteResponse;
import com.knowledgebase.entity.Note;
import com.knowledgebase.entity.Tag;
import com.knowledgebase.exception.BusinessException;
import com.knowledgebase.exception.ResourceNotFoundException;
import com.knowledgebase.repository.NoteRepository;
import com.knowledgebase.util.SearchIndexFields;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.queries.mlt.MoreLikeThis;
import org.apache.lucene.search.BooleanClause;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 相似笔记推荐服务。
 */
@Service
public class SimilarNoteService {

    private static final int DEFAULT_LIMIT = 6;
    private static final int MAX_LIMIT = 12;
    private static final int CANDIDATE_LIMIT = 24;

    private final NoteRepository noteRepository;
    private final VectorIndexService vectorIndexService;
    private final Analyzer analyzer;
    private final Path indexPath;

    /**
     * 创建相似笔记服务。
     *
     * @param noteRepository 笔记仓库
     * @param vectorIndexService 向量索引服务
     * @param analyzer 搜索分析器
     * @param properties 知识库配置
     */
    public SimilarNoteService(
            NoteRepository noteRepository,
            VectorIndexService vectorIndexService,
            Analyzer analyzer,
            KnowledgeBaseProperties properties
    ) {
        this.noteRepository = noteRepository;
        this.vectorIndexService = vectorIndexService;
        this.analyzer = analyzer;
        this.indexPath = Paths.get(properties.getIndexPath()).toAbsolutePath().normalize();
    }

    /**
     * 查询相似笔记。
     *
     * @param noteId 笔记ID
     * @param limit 最大数量
     * @return 相似笔记列表
     */
    @Transactional(readOnly = true)
    public List<SimilarNoteResponse> findSimilarNotes(Long noteId, int limit) {
        Note sourceNote = noteRepository.findById(noteId)
                .orElseThrow(() -> new ResourceNotFoundException("笔记不存在：" + noteId));
        int safeLimit = normalizeLimit(limit);
        Map<Long, SimilarCandidate> candidates = new LinkedHashMap<>();
        collectVectorCandidates(sourceNote, candidates);
        collectMoreLikeThisCandidates(sourceNote, candidates);
        collectMetadataCandidates(sourceNote, candidates);
        if (candidates.isEmpty()) {
            return List.of();
        }
        Map<Long, Note> noteMap = loadNotes(candidates.keySet());
        return candidates.values()
                .stream()
                .filter(candidate -> noteMap.containsKey(candidate.noteId()))
                .sorted(Comparator.comparingDouble(SimilarCandidate::score).reversed()
                        .thenComparing(candidate -> noteMap.get(candidate.noteId()).getUpdatedAt(),
                                Comparator.nullsLast(Comparator.reverseOrder()))
                        .thenComparing(SimilarCandidate::noteId, Comparator.reverseOrder()))
                .limit(safeLimit)
                .map(candidate -> SimilarNoteResponse.from(
                        noteMap.get(candidate.noteId()),
                        roundScore(candidate.score()),
                        candidate.reason(),
                        candidate.source()
                ))
                .toList();
    }

    /**
     * 收集向量相似候选。
     *
     * @param sourceNote 源笔记
     * @param candidates 候选集合
     */
    private void collectVectorCandidates(Note sourceNote, Map<Long, SimilarCandidate> candidates) {
        try {
            VectorIndexService.SemanticSearchHits hits = vectorIndexService.searchSimilarNoteHits(
                    sourceNote,
                    CANDIDATE_LIMIT
            );
            for (VectorIndexService.SemanticHit hit : hits.hits()) {
                double score = vectorIndexService.normalizeSimilarity(hit.score());
                mergeCandidate(candidates, new SimilarCandidate(
                        hit.noteId(),
                        score,
                        "语义向量相近，相似度 " + formatScore(score),
                        "vector"
                ));
            }
        } catch (BusinessException ignored) {
            // 向量推荐是增强能力，不可用时继续使用全文和元数据兜底。
        }
    }

    /**
     * 收集全文相似候选。
     *
     * @param sourceNote 源笔记
     * @param candidates 候选集合
     */
    private void collectMoreLikeThisCandidates(Note sourceNote, Map<Long, SimilarCandidate> candidates) {
        if (Files.notExists(indexPath)) {
            return;
        }
        try (Directory directory = FSDirectory.open(indexPath)) {
            if (!DirectoryReader.indexExists(directory)) {
                return;
            }
            try (DirectoryReader reader = DirectoryReader.open(directory)) {
                Integer luceneDocId = findLuceneDocId(reader, sourceNote.getId());
                if (luceneDocId == null) {
                    return;
                }
                MoreLikeThis moreLikeThis = new MoreLikeThis(reader);
                moreLikeThis.setAnalyzer(analyzer);
                moreLikeThis.setFieldNames(new String[]{SearchIndexFields.TITLE, SearchIndexFields.CONTENT_PLAIN});
                moreLikeThis.setMinTermFreq(1);
                moreLikeThis.setMinDocFreq(1);
                moreLikeThis.setMaxQueryTerms(16);
                Query likeQuery = moreLikeThis.like(luceneDocId);
                BooleanQuery.Builder builder = new BooleanQuery.Builder();
                builder.add(likeQuery, BooleanClause.Occur.MUST);
                builder.add(new TermQuery(new org.apache.lucene.index.Term(
                        SearchIndexFields.ID,
                        String.valueOf(sourceNote.getId())
                )), BooleanClause.Occur.MUST_NOT);
                IndexSearcher searcher = new IndexSearcher(reader);
                TopDocs topDocs = searcher.search(builder.build(), CANDIDATE_LIMIT);
                float maxScore = maxScore(topDocs.scoreDocs);
                for (ScoreDoc scoreDoc : topDocs.scoreDocs) {
                    var document = searcher.storedFields().document(scoreDoc.doc);
                    Long noteId = Long.valueOf(document.get(SearchIndexFields.ID));
                    double score = normalizeLuceneScore(scoreDoc.score, maxScore);
                    mergeCandidate(candidates, new SimilarCandidate(
                            noteId,
                            Math.max(score, 0.2D),
                            "全文内容相似",
                            "more-like-this"
                    ));
                }
            }
        } catch (IOException | NumberFormatException ignored) {
            // 全文相似失败时继续用标签和分类兜底。
        }
    }

    /**
     * 收集标签、分类和更新时间兜底候选。
     *
     * @param sourceNote 源笔记
     * @param candidates 候选集合
     */
    private void collectMetadataCandidates(Note sourceNote, Map<Long, SimilarCandidate> candidates) {
        List<Note> notes = noteRepository.findByDeletedFalseAndArchivedFalse();
        Set<String> sourceTags = normalizedTags(sourceNote);
        Long sourceCategoryId = sourceNote.getCategory() == null ? null : sourceNote.getCategory().getId();
        for (Note note : notes) {
            if (sourceNote.getId().equals(note.getId())) {
                continue;
            }
            int tagOverlap = tagOverlap(sourceTags, normalizedTags(note));
            boolean sameCategory = sourceCategoryId != null
                    && note.getCategory() != null
                    && sourceCategoryId.equals(note.getCategory().getId());
            if (tagOverlap == 0 && !sameCategory) {
                continue;
            }
            double score = Math.min(0.65D, tagOverlap * 0.18D + (sameCategory ? 0.18D : 0.0D));
            String reason = tagOverlap > 0
                    ? "共享 " + tagOverlap + " 个标签" + (sameCategory ? "，且同分类" : "")
                    : "同分类笔记";
            mergeCandidate(candidates, new SimilarCandidate(note.getId(), score, reason, "metadata"));
        }
    }

    /**
     * 查找 Lucene 文档ID。
     *
     * @param reader 读取器
     * @param noteId 笔记ID
     * @return 文档ID
     * @throws IOException 读取异常
     */
    private Integer findLuceneDocId(DirectoryReader reader, Long noteId) throws IOException {
        String id = String.valueOf(noteId);
        for (int docId = 0; docId < reader.maxDoc(); docId++) {
            var document = reader.storedFields().document(docId);
            if (id.equals(document.get(SearchIndexFields.ID))) {
                return docId;
            }
        }
        return null;
    }

    /**
     * 合并候选，保留更高分数。
     *
     * @param candidates 候选集合
     * @param candidate 新候选
     */
    private void mergeCandidate(Map<Long, SimilarCandidate> candidates, SimilarCandidate candidate) {
        SimilarCandidate existing = candidates.get(candidate.noteId());
        if (existing == null || candidate.score() > existing.score()) {
            candidates.put(candidate.noteId(), candidate);
        }
    }

    /**
     * 批量加载笔记。
     *
     * @param noteIds 笔记ID
     * @return 笔记映射
     */
    private Map<Long, Note> loadNotes(Set<Long> noteIds) {
        Map<Long, Note> noteMap = new LinkedHashMap<>();
        for (Note note : noteRepository.findByIdInAndDeletedFalseAndArchivedFalse(noteIds)) {
            noteMap.put(note.getId(), note);
        }
        return noteMap;
    }

    /**
     * 标准化标签集合。
     *
     * @param note 笔记
     * @return 标签集合
     */
    private Set<String> normalizedTags(Note note) {
        Set<String> tags = new LinkedHashSet<>();
        for (Tag tag : note.getTags()) {
            String tagName = tag.getName() == null ? "" : tag.getName().trim().toLowerCase(Locale.ROOT);
            if (!tagName.isBlank()) {
                tags.add(tagName);
            }
        }
        return tags;
    }

    /**
     * 计算标签重合数量。
     *
     * @param left 左侧标签
     * @param right 右侧标签
     * @return 重合数量
     */
    private int tagOverlap(Set<String> left, Set<String> right) {
        int overlap = 0;
        for (String tag : left) {
            if (right.contains(tag)) {
                overlap++;
            }
        }
        return overlap;
    }

    /**
     * 标准化 Lucene 分数。
     *
     * @param score 原始分
     * @param maxScore 最高分
     * @return 标准化分数
     */
    private double normalizeLuceneScore(float score, float maxScore) {
        if (score <= 0 || maxScore <= 0) {
            return 0.0D;
        }
        return Math.max(0.0D, Math.min(1.0D, score / maxScore));
    }

    /**
     * 计算最高分。
     *
     * @param scoreDocs 命中列表
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
     * 标准化数量。
     *
     * @param limit 原始数量
     * @return 数量
     */
    private int normalizeLimit(int limit) {
        if (limit <= 0) {
            return DEFAULT_LIMIT;
        }
        return Math.min(limit, MAX_LIMIT);
    }

    /**
     * 格式化分数。
     *
     * @param score 分数
     * @return 文本
     */
    private String formatScore(double score) {
        return String.format(Locale.ROOT, "%.3f", roundScore(score));
    }

    /**
     * 分数四舍五入。
     *
     * @param score 原始分数
     * @return 分数
     */
    private double roundScore(double score) {
        return Math.round(score * 10_000.0D) / 10_000.0D;
    }

    /**
     * 相似候选。
     *
     * @param noteId 笔记ID
     * @param score 分数
     * @param reason 原因
     * @param source 来源
     */
    private record SimilarCandidate(Long noteId, double score, String reason, String source) {
    }
}
