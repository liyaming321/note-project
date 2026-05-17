package com.knowledgebase.service;

import com.knowledgebase.dto.TagRequest;
import com.knowledgebase.dto.TagResponse;
import com.knowledgebase.entity.Note;
import com.knowledgebase.entity.Tag;
import com.knowledgebase.exception.BusinessException;
import com.knowledgebase.exception.ResourceNotFoundException;
import com.knowledgebase.repository.NoteRepository;
import com.knowledgebase.repository.TagRepository;
import java.util.Comparator;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 标签业务服务。
 */
@Service
public class TagService {

    private final TagRepository tagRepository;
    private final NoteRepository noteRepository;
    private final IndexService indexService;
    private final VectorIndexService vectorIndexService;

    /**
     * 创建标签业务服务。
     *
     * @param tagRepository 标签仓库
     * @param noteRepository 笔记仓库
     * @param indexService 全文索引服务
     * @param vectorIndexService 向量索引服务
     */
    public TagService(
            TagRepository tagRepository,
            NoteRepository noteRepository,
            IndexService indexService,
            VectorIndexService vectorIndexService
    ) {
        this.tagRepository = tagRepository;
        this.noteRepository = noteRepository;
        this.indexService = indexService;
        this.vectorIndexService = vectorIndexService;
    }

    /**
     * 查询所有标签。
     *
     * @return 标签列表
     */
    @Transactional(readOnly = true)
    public List<TagResponse> findAll() {
        return tagRepository.findAll()
                .stream()
                .sorted(Comparator.comparing(Tag::getName))
                .map(TagResponse::from)
                .toList();
    }

    /**
     * 创建标签。
     *
     * @param request 标签请求
     * @return 标签响应
     */
    @Transactional
    public TagResponse create(TagRequest request) {
        String normalizedName = normalizeName(request.name());
        if (tagRepository.existsByName(normalizedName)) {
            throw new BusinessException("标签已存在：" + normalizedName);
        }
        Tag tag = tagRepository.save(new Tag(normalizedName));
        return TagResponse.from(tag);
    }

    /**
     * 删除标签，并从所有关联笔记中移除该标签。
     *
     * @param id 标签ID
     */
    @Transactional
    public void delete(Long id) {
        Tag tag = tagRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("标签不存在：" + id));
        List<Note> relatedNotes = noteRepository.findDistinctByTags_Id(id);
        for (Note note : relatedNotes) {
            note.getTags().removeIf(currentTag -> id.equals(currentTag.getId()));
            indexService.upsertNote(note);
            syncVectorIndex(note);
        }
        tagRepository.delete(tag);
    }

    /**
     * 标准化标签名称。
     *
     * @param name 原始名称
     * @return 标准名称
     */
    private String normalizeName(String name) {
        return name == null ? "" : name.trim();
    }

    /**
     * 尽力同步单篇笔记向量索引，避免标签管理影响主流程。
     *
     * @param note 笔记实体
     */
    private void syncVectorIndex(Note note) {
        try {
            vectorIndexService.upsertNote(note);
        } catch (BusinessException ignored) {
            // 向量索引是增强能力，标签删除不应因为本地模型配置问题失败。
        }
    }
}
