package com.knowledgebase.service;

import com.knowledgebase.dto.KnowledgeOrganizeCandidateResponse;
import com.knowledgebase.dto.NoteListResponse;
import com.knowledgebase.dto.OrganizeApplyItemRequest;
import com.knowledgebase.dto.OrganizeApplyRequest;
import com.knowledgebase.dto.OrganizeApplyResponse;
import com.knowledgebase.dto.PageResponse;
import com.knowledgebase.entity.Note;
import com.knowledgebase.entity.NoteStatus;
import com.knowledgebase.repository.NoteRepository;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 知识整理候选与元数据建议服务。
 */
@Service
public class KnowledgeOrganizeService {

    private static final int DEFAULT_SIZE = 20;
    private static final int MAX_SIZE = 100;

    private final NoteRepository noteRepository;
    private final NoteService noteService;

    /**
     * 创建知识整理服务。
     *
     * @param noteRepository 笔记仓库
     * @param noteService 笔记服务
     */
    public KnowledgeOrganizeService(NoteRepository noteRepository, NoteService noteService) {
        this.noteRepository = noteRepository;
        this.noteService = noteService;
    }

    /**
     * 查询待整理候选笔记。
     *
     * @param page 页码
     * @param size 每页数量
     * @return 候选分页
     */
    @Transactional(readOnly = true)
    public PageResponse<KnowledgeOrganizeCandidateResponse> candidates(int page, int size) {
        int safePage = Math.max(page, 0);
        int safeSize = normalizeSize(size);
        List<KnowledgeOrganizeCandidateResponse> candidates = noteRepository.findByDeletedFalseAndArchivedFalse()
                .stream()
                .map(this::toCandidate)
                .filter(candidate -> !candidate.reasons().isEmpty())
                .toList();
        int fromIndex = Math.min(safePage * safeSize, candidates.size());
        int toIndex = Math.min(fromIndex + safeSize, candidates.size());
        List<KnowledgeOrganizeCandidateResponse> pageItems = candidates.subList(fromIndex, toIndex);
        int totalPages = candidates.isEmpty() ? 0 : (int) Math.ceil((double) candidates.size() / safeSize);
        return new PageResponse<>(
                pageItems,
                safePage,
                safeSize,
                candidates.size(),
                totalPages,
                safePage == 0,
                (safePage + 1L) * safeSize >= candidates.size()
        );
    }

    /**
     * 应用人工确认后的整理结果。
     *
     * @param request 整理应用请求
     * @return 应用结果
     */
    @Transactional
    public OrganizeApplyResponse apply(OrganizeApplyRequest request) {
        List<NoteListResponse> updatedNotes = new ArrayList<>();
        for (OrganizeApplyItemRequest item : request.items()) {
            updatedNotes.add(noteService.applyMetadata(
                    item.noteId(),
                    item.summary(),
                    item.tags(),
                    item.categoryId()
            ));
        }
        return new OrganizeApplyResponse(
                updatedNotes.size(),
                updatedNotes,
                "已应用 " + updatedNotes.size() + " 条整理结果"
        );
    }

    /**
     * 转换为整理候选。
     *
     * @param note 笔记
     * @return 整理候选
     */
    private KnowledgeOrganizeCandidateResponse toCandidate(Note note) {
        List<String> reasons = reasons(note);
        return new KnowledgeOrganizeCandidateResponse(
                NoteListResponse.from(note),
                reasons,
                suggestedTags(note),
                suggestedCategory(note),
                suggestedSummary(note)
        );
    }

    /**
     * 生成待整理原因。
     *
     * @param note 笔记
     * @return 原因列表
     */
    private List<String> reasons(Note note) {
        List<String> reasons = new ArrayList<>();
        if (note.getStatus() == NoteStatus.DRAFT) {
            reasons.add("草稿笔记待确认发布状态");
        }
        if (note.getCategory() == null) {
            reasons.add("未设置分类");
        }
        if (note.getTags().isEmpty()) {
            reasons.add("未设置标签");
        }
        if (safeText(note.getSummary()).isBlank()) {
            reasons.add("缺少摘要");
        }
        if (looksLikeImportedLink(note)) {
            reasons.add("链接导入资料建议二次整理");
        }
        if (note.getUpdatedAt() != null && Duration.between(note.getUpdatedAt(), LocalDateTime.now()).toDays() >= 180) {
            reasons.add("超过 180 天未更新，建议复查有效性");
        }
        return reasons;
    }

    /**
     * 生成建议标签。
     *
     * @param note 笔记
     * @return 标签建议
     */
    private List<String> suggestedTags(Note note) {
        Set<String> tags = new LinkedHashSet<>();
        String text = normalizedSearchText(note);
        if (looksLikeImportedLink(note)) {
            tags.add("链接导入");
            tags.add("待整理");
        }
        if (text.contains("spring") || text.contains("controller") || text.contains("service")) {
            tags.add("Spring");
        }
        if (text.contains("sql") || text.contains("数据库")) {
            tags.add("数据库");
        }
        if (text.contains("todo") || text.contains("待办")) {
            tags.add("待办");
        }
        if (note.getType() != null && "CODE".equals(note.getType().name())) {
            tags.add("代码片段");
        }
        if (tags.isEmpty()) {
            tags.add("待整理");
        }
        return List.copyOf(tags);
    }

    /**
     * 生成建议分类。
     *
     * @param note 笔记
     * @return 分类建议
     */
    private String suggestedCategory(Note note) {
        if (note.getCategory() != null) {
            return note.getCategory().getName();
        }
        String text = normalizedSearchText(note);
        if (looksLikeImportedLink(note)) {
            return "链接资料";
        }
        if (text.contains("代码") || text.contains("接口") || text.contains("class ") || text.contains("function ")) {
            return "代码片段";
        }
        return "默认分类";
    }

    /**
     * 生成建议摘要。
     *
     * @param note 笔记
     * @return 摘要建议
     */
    private String suggestedSummary(Note note) {
        String summary = safeText(note.getSummary());
        if (!summary.isBlank()) {
            return summary;
        }
        String text = safeText(note.getContentText()).replaceAll("\\s+", " ");
        if (text.isBlank()) {
            return "暂无正文摘要，建议补充核心结论。";
        }
        return text.length() <= 120 ? text : text.substring(0, 120) + "...";
    }

    /**
     * 判断是否像链接导入资料。
     *
     * @param note 笔记
     * @return 是否链接导入
     */
    private boolean looksLikeImportedLink(Note note) {
        String text = normalizedSearchText(note);
        return text.contains("来源链接") || text.contains("网页正文摘录") || text.contains("http://") || text.contains("https://");
    }

    /**
     * 构建检索文本。
     *
     * @param note 笔记
     * @return 检索文本
     */
    private String normalizedSearchText(Note note) {
        return (safeText(note.getTitle()) + " " + safeText(note.getContentText()) + " " + safeText(note.getLanguage()))
                .toLowerCase(Locale.ROOT);
    }

    /**
     * 规范每页数量。
     *
     * @param size 原始数量
     * @return 安全数量
     */
    private int normalizeSize(int size) {
        if (size <= 0) {
            return DEFAULT_SIZE;
        }
        return Math.min(size, MAX_SIZE);
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
}
