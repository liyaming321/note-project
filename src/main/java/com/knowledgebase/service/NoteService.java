package com.knowledgebase.service;

import com.knowledgebase.dto.NoteDetailResponse;
import com.knowledgebase.dto.NoteBatchRequest;
import com.knowledgebase.dto.NoteHistoryDetailResponse;
import com.knowledgebase.dto.NoteHistorySummaryResponse;
import com.knowledgebase.dto.NoteListResponse;
import com.knowledgebase.dto.NoteReorderRequest;
import com.knowledgebase.dto.NoteRequest;
import com.knowledgebase.dto.PageResponse;
import com.knowledgebase.exception.BusinessException;
import com.knowledgebase.entity.Category;
import com.knowledgebase.entity.Note;
import com.knowledgebase.entity.NoteHistory;
import com.knowledgebase.entity.NoteStatus;
import com.knowledgebase.entity.NoteType;
import com.knowledgebase.entity.Tag;
import com.knowledgebase.exception.ResourceNotFoundException;
import com.knowledgebase.repository.CategoryRepository;
import com.knowledgebase.repository.NoteRepository;
import com.knowledgebase.repository.TagRepository;
import com.knowledgebase.util.MarkdownTextExtractor;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Predicate;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 笔记业务服务。
 */
@Service
public class NoteService {

    private static final Set<String> ALLOWED_SORT_FIELDS = Set.of("createdAt", "updatedAt", "title", "sortOrder");
    private static final long SORT_ORDER_STEP = 10L;

    private final NoteRepository noteRepository;
    private final CategoryRepository categoryRepository;
    private final TagRepository tagRepository;
    private final IndexService indexService;
    private final VectorIndexService vectorIndexService;
    private final NoteHistoryService noteHistoryService;

    /**
     * 创建笔记业务服务。
     *
     * @param noteRepository 笔记仓库
     * @param categoryRepository 分类仓库
     * @param tagRepository 标签仓库
     * @param indexService 索引服务
     * @param vectorIndexService 向量索引服务
     * @param noteHistoryService 历史版本服务
     */
    public NoteService(
            NoteRepository noteRepository,
            CategoryRepository categoryRepository,
            TagRepository tagRepository,
            IndexService indexService,
            VectorIndexService vectorIndexService,
            NoteHistoryService noteHistoryService
    ) {
        this.noteRepository = noteRepository;
        this.categoryRepository = categoryRepository;
        this.tagRepository = tagRepository;
        this.indexService = indexService;
        this.vectorIndexService = vectorIndexService;
        this.noteHistoryService = noteHistoryService;
    }

    /**
     * 创建笔记。
     *
     * @param request 笔记请求
     * @return 笔记详情
     */
    @Transactional
    public NoteDetailResponse create(NoteRequest request) {
        Category category = findCategory(request.categoryId());
        Set<Tag> tags = resolveTags(request.safeTags());
        Note note = new Note(
                request.title().trim(),
                request.content(),
                MarkdownTextExtractor.extract(request.content()),
                request.type(),
                normalizeLanguage(request.language()),
                category,
                tags
        );
        note.changeSummary(request.summary());
        applyStatus(note, request);
        Note savedNote = noteRepository.save(note);
        indexService.upsertNote(savedNote);
        syncVectorIndex(savedNote);
        return NoteDetailResponse.from(savedNote);
    }

    /**
     * 查询笔记详情。
     *
     * @param id 笔记ID
     * @return 笔记详情
     */
    @Transactional(readOnly = true)
    public NoteDetailResponse findById(Long id) {
        return NoteDetailResponse.from(findNote(id));
    }

    /**
     * 更新笔记。
     *
     * @param id 笔记ID
     * @param request 笔记请求
     * @return 笔记详情
     */
    @Transactional
    public NoteDetailResponse update(Long id, NoteRequest request) {
        Note note = findNote(id);
        noteHistoryService.saveSnapshot(note);
        Category category = findCategory(request.categoryId());
        Set<Tag> tags = resolveTags(request.safeTags());
        note.update(
                request.title().trim(),
                request.content(),
                MarkdownTextExtractor.extract(request.content()),
                request.summary(),
                request.type(),
                normalizeLanguage(request.language()),
                category,
                tags
        );
        applyStatus(note, request);
        indexService.upsertNote(note);
        syncVectorIndex(note);
        return NoteDetailResponse.from(note);
    }

    /**
     * 恢复指定历史版本。
     *
     * @param id 笔记ID
     * @param version 版本号
     * @return 笔记详情
     */
    @Transactional
    public NoteDetailResponse revertToVersion(Long id, int version) {
        Note note = findNote(id);
        NoteHistory history = noteHistoryService.findHistoryEntity(id, version);
        noteHistoryService.saveSnapshot(note);
        note.update(
                history.getTitle(),
                history.getContent(),
                history.getContentText(),
                null,
                NoteType.valueOf(history.getType()),
                normalizeLanguage(history.getLanguage()),
                findCategory(noteHistoryService.resolveCategoryId(history)),
                resolveTags(noteHistoryService.resolveTagNames(history))
        );
        note.restore();
        indexService.upsertNote(note);
        syncVectorIndex(note);
        return NoteDetailResponse.from(note);
    }

    /**
     * 逻辑删除笔记。
     *
     * @param id 笔记ID
     */
    @Transactional
    public void delete(Long id) {
        Note note = findNote(id);
        note.markDeleted();
        indexService.deleteNote(note.getId());
        removeVectorIndex(note.getId());
    }

    /**
     * 恢复已删除笔记。
     *
     * @param id 笔记ID
     * @return 笔记详情
     */
    @Transactional
    public NoteDetailResponse restore(Long id) {
        Note note = findNote(id);
        note.restore();
        indexService.upsertNote(note);
        syncVectorIndex(note);
        return NoteDetailResponse.from(note);
    }

    /**
     * 更新收藏状态。
     *
     * @param id 笔记ID
     * @param favorite 是否收藏
     * @return 笔记详情
     */
    @Transactional
    public NoteDetailResponse changeFavorite(Long id, boolean favorite) {
        Note note = findNote(id);
        note.changeFavorite(favorite);
        return NoteDetailResponse.from(note);
    }

    /**
     * 更新置顶状态。
     *
     * @param id 笔记ID
     * @param pinned 是否置顶
     * @return 笔记详情
     */
    @Transactional
    public NoteDetailResponse changePinned(Long id, boolean pinned) {
        Note note = findNote(id);
        note.changePinned(pinned);
        return NoteDetailResponse.from(note);
    }

    /**
     * 更新笔记发布状态。
     *
     * @param id 笔记ID
     * @param status 发布状态
     * @return 笔记详情
     */
    @Transactional
    public NoteDetailResponse changeStatus(Long id, NoteStatus status) {
        Note note = findNote(id);
        note.changeStatus(status);
        indexService.upsertNote(note);
        syncVectorIndex(note);
        return NoteDetailResponse.from(note);
    }

    /**
     * 更新归档状态。
     *
     * @param id 笔记ID
     * @param archived 是否归档
     * @return 笔记详情
     */
    @Transactional
    public NoteDetailResponse changeArchived(Long id, boolean archived) {
        Note note = findNote(id);
        note.changeArchived(archived);
        indexService.upsertNote(note);
        syncVectorIndex(note);
        return NoteDetailResponse.from(note);
    }

    /**
     * 永久删除笔记。
     *
     * @param id 笔记ID
     */
    @Transactional
    public void permanentlyDelete(Long id) {
        Note note = findNote(id);
        indexService.deleteNote(note.getId());
        removeVectorIndex(note.getId());
        noteRepository.delete(note);
    }

    /**
     * 批量恢复已删除笔记。
     *
     * @param request 批量请求
     * @return 恢复后的笔记列表
     */
    @Transactional
    public List<NoteListResponse> batchRestore(NoteBatchRequest request) {
        List<Note> notes = noteRepository.findByIdIn(request.noteIds());
        if (notes.size() != request.noteIds().stream().distinct().count()) {
            throw new ResourceNotFoundException("批量恢复中存在不存在的笔记");
        }
        List<NoteListResponse> restoredNotes = new ArrayList<>();
        for (Note note : notes) {
            note.restore();
            indexService.upsertNote(note);
            syncVectorIndex(note);
            restoredNotes.add(NoteListResponse.from(note));
        }
        return restoredNotes;
    }

    /**
     * 按拖拽后的顺序更新笔记自定义排序值。
     *
     * @param request 排序请求
     * @return 更新后的笔记列表
     */
    @Transactional
    public List<NoteListResponse> reorder(NoteReorderRequest request) {
        List<Long> orderedIds = request.noteIds()
                .stream()
                .distinct()
                .toList();
        List<Note> notes = noteRepository.findByIdInAndDeletedFalse(orderedIds);
        if (notes.size() != orderedIds.size()) {
            throw new BusinessException("排序列表中存在不存在或已删除的笔记");
        }
        Map<Long, Note> noteById = notes.stream()
                .collect(Collectors.toMap(Note::getId, Function.identity()));
        long baseSortOrder = notes.stream()
                .map(Note::getSortOrder)
                .filter(java.util.Objects::nonNull)
                .min(Long::compareTo)
                .orElse(0L);
        List<NoteListResponse> reorderedNotes = new ArrayList<>();
        for (int index = 0; index < orderedIds.size(); index++) {
            Note note = noteById.get(orderedIds.get(index));
            note.changeSortOrder(baseSortOrder + index * SORT_ORDER_STEP);
            reorderedNotes.add(NoteListResponse.from(note));
        }
        return reorderedNotes;
    }

    /**
     * 查询笔记历史版本列表。
     *
     * @param id 笔记ID
     * @return 历史版本列表
     */
    @Transactional(readOnly = true)
    public List<NoteHistorySummaryResponse> findHistory(Long id) {
        findNote(id);
        return noteHistoryService.listHistory(id);
    }

    /**
     * 查询笔记历史版本详情。
     *
     * @param id 笔记ID
     * @param version 版本号
     * @return 历史版本详情
     */
    @Transactional(readOnly = true)
    public NoteHistoryDetailResponse findHistoryDetail(Long id, int version) {
        findNote(id);
        return noteHistoryService.findHistoryDetail(id, version);
    }

    /**
     * 分页查询笔记列表。
     *
     * @param categoryId 分类ID
     * @param tag 标签名称
     * @param type 笔记类型
     * @param status 发布状态
     * @param favorite 是否收藏
     * @param pinned 是否置顶
     * @param archived 是否归档
     * @param includeDeleted 是否包含已删除
     * @param onlyDeleted 是否仅查询已删除
     * @param updatedFrom 更新时间开始日期
     * @param updatedTo 更新时间结束日期
     * @param page 页码
     * @param size 每页数量
     * @param sort 排序字段
     * @param direction 排序方向
     * @return 分页笔记列表
     */
    @Transactional(readOnly = true)
    public PageResponse<NoteListResponse> findPage(
            Long categoryId,
            String tag,
            NoteType type,
            NoteStatus status,
            Boolean favorite,
            Boolean pinned,
            Boolean archived,
            boolean includeDeleted,
            boolean onlyDeleted,
            LocalDate updatedFrom,
            LocalDate updatedTo,
            int page,
            int size,
            String sort,
            String direction
    ) {
        Pageable pageable = PageRequest.of(
                Math.max(page, 0),
                Math.min(Math.max(size, 1), 100),
                buildSort(sort, direction)
        );
        return PageResponse.from(noteRepository.findAll(
                buildSpecification(categoryId, tag, type, status, favorite, pinned, archived, includeDeleted,
                        onlyDeleted, updatedFrom, updatedTo),
                pageable
        ).map(NoteListResponse::from));
    }

    /**
     * 查询笔记实体。
     *
     * @param id 笔记ID
     * @return 笔记实体
     */
    private Note findNote(Long id) {
        return noteRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("笔记不存在：" + id));
    }

    /**
     * 查询分类实体。
     *
     * @param categoryId 分类ID
     * @return 分类实体
     */
    private Category findCategory(Long categoryId) {
        if (categoryId == null) {
            return null;
        }
        return categoryRepository.findById(categoryId)
                .orElseThrow(() -> new ResourceNotFoundException("分类不存在：" + categoryId));
    }

    /**
     * 解析并创建缺失标签。
     *
     * @param tagNames 标签名称集合
     * @return 标签实体集合
     */
    private Set<Tag> resolveTags(Set<String> tagNames) {
        List<String> normalizedNames = tagNames.stream()
                .map(name -> Optional.ofNullable(name).orElse("").trim())
                .filter(name -> !name.isBlank())
                .distinct()
                .sorted()
                .toList();
        if (normalizedNames.isEmpty()) {
            return new LinkedHashSet<>();
        }
        Map<String, Tag> existingTags = tagRepository.findByNameIn(normalizedNames)
                .stream()
                .collect(Collectors.toMap(Tag::getName, Function.identity()));
        List<Tag> resolvedTags = new ArrayList<>();
        for (String name : normalizedNames) {
            Tag tag = existingTags.get(name);
            if (tag == null) {
                tag = tagRepository.save(new Tag(name));
                existingTags.put(name, tag);
            }
            resolvedTags.add(tag);
        }
        resolvedTags.sort(Comparator.comparing(Tag::getName));
        return new LinkedHashSet<>(resolvedTags);
    }

    /**
     * 应用收藏和置顶状态。
     *
     * @param note 笔记实体
     * @param request 笔记请求
     */
    private void applyStatus(Note note, NoteRequest request) {
        if (request.favorite() != null) {
            note.changeFavorite(request.favorite());
        }
        if (request.pinned() != null) {
            note.changePinned(request.pinned());
        }
        if (request.status() != null) {
            note.changeStatus(request.status());
        }
    }

    /**
     * 标准化代码语言。
     *
     * @param language 原始语言
     * @return 标准语言
     */
    private String normalizeLanguage(String language) {
        if (language == null || language.isBlank()) {
            return null;
        }
        return language.trim().toLowerCase(Locale.ROOT);
    }

    /**
     * 构建列表查询条件。
     *
     * @param categoryId 分类ID
     * @param tag 标签名称
     * @param type 笔记类型
     * @param status 发布状态
     * @param favorite 是否收藏
     * @param pinned 是否置顶
     * @param archived 是否归档
     * @param includeDeleted 是否包含删除
     * @param onlyDeleted 是否仅删除
     * @param updatedFrom 更新时间开始日期
     * @param updatedTo 更新时间结束日期
     * @return 查询条件
     */
    private Specification<Note> buildSpecification(
            Long categoryId,
            String tag,
            NoteType type,
            NoteStatus status,
            Boolean favorite,
            Boolean pinned,
            Boolean archived,
            boolean includeDeleted,
            boolean onlyDeleted,
            LocalDate updatedFrom,
            LocalDate updatedTo
    ) {
        return (root, query, criteriaBuilder) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (onlyDeleted) {
                predicates.add(criteriaBuilder.isTrue(root.get("deleted")));
            } else if (!includeDeleted) {
                predicates.add(criteriaBuilder.isFalse(root.get("deleted")));
            }
            if (archived != null) {
                predicates.add(criteriaBuilder.equal(root.get("archived"), archived));
            } else if (!onlyDeleted) {
                predicates.add(criteriaBuilder.isFalse(root.get("archived")));
            }
            if (categoryId != null) {
                predicates.add(criteriaBuilder.equal(root.get("category").get("id"), categoryId));
            }
            if (type != null) {
                predicates.add(criteriaBuilder.equal(root.get("type"), type));
            }
            if (status != null) {
                predicates.add(criteriaBuilder.equal(root.get("status"), status));
            }
            if (favorite != null) {
                predicates.add(criteriaBuilder.equal(root.get("favorite"), favorite));
            }
            if (pinned != null) {
                predicates.add(criteriaBuilder.equal(root.get("pinned"), pinned));
            }
            if (updatedFrom != null) {
                predicates.add(criteriaBuilder.greaterThanOrEqualTo(root.get("updatedAt"),
                        LocalDateTime.of(updatedFrom, LocalTime.MIN)));
            }
            if (updatedTo != null) {
                predicates.add(criteriaBuilder.lessThan(root.get("updatedAt"),
                        LocalDateTime.of(updatedTo.plusDays(1), LocalTime.MIN)));
            }
            if (tag != null && !tag.isBlank()) {
                Join<Note, Tag> tagJoin = root.join("tags", JoinType.INNER);
                predicates.add(criteriaBuilder.equal(tagJoin.get("name"), tag.trim()));
                query.distinct(true);
            }
            return criteriaBuilder.and(predicates.toArray(Predicate[]::new));
        };
    }

    /**
     * 构建排序规则。
     *
     * @param sort 排序字段
     * @param direction 排序方向
     * @return 排序规则
     */
    private Sort buildSort(String sort, String direction) {
        String sortField = ALLOWED_SORT_FIELDS.contains(sort) ? sort : "updatedAt";
        if ("sortOrder".equals(sortField)) {
            return Sort.by(Sort.Order.desc("pinned"), Sort.Order.asc("sortOrder"));
        }
        Sort.Direction sortDirection = "asc".equalsIgnoreCase(direction) ? Sort.Direction.ASC : Sort.Direction.DESC;
        return Sort.by(Sort.Order.desc("pinned"), new Sort.Order(sortDirection, sortField));
    }

    /**
     * 尽力同步单篇笔记向量索引，避免增强索引影响主业务写入。
     *
     * @param note 笔记实体
     */
    private void syncVectorIndex(Note note) {
        try {
            vectorIndexService.upsertNote(note);
        } catch (BusinessException ignored) {
            // 向量索引是增强能力，配置或运行时异常不应阻断笔记主流程。
        }
    }

    /**
     * 尽力删除单篇笔记向量索引。
     *
     * @param noteId 笔记ID
     */
    private void removeVectorIndex(Long noteId) {
        try {
            vectorIndexService.deleteNote(noteId);
        } catch (BusinessException ignored) {
            // 向量索引是增强能力，删除失败不应阻断笔记主流程。
        }
    }
}
