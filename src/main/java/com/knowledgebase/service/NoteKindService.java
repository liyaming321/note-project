package com.knowledgebase.service;

import com.knowledgebase.dto.NoteKindRequest;
import com.knowledgebase.dto.NoteKindResponse;
import com.knowledgebase.entity.Note;
import com.knowledgebase.entity.NoteKind;
import com.knowledgebase.exception.BusinessException;
import com.knowledgebase.exception.ResourceNotFoundException;
import com.knowledgebase.repository.NoteKindRepository;
import com.knowledgebase.repository.NoteRepository;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 笔记用途业务服务。
 */
@Service
public class NoteKindService {

    private static final long SORT_ORDER_STEP = 10L;

    private final NoteKindRepository noteKindRepository;
    private final NoteRepository noteRepository;

    /**
     * 创建笔记用途业务服务。
     *
     * @param noteKindRepository 笔记用途仓库
     * @param noteRepository 笔记仓库
     */
    public NoteKindService(NoteKindRepository noteKindRepository, NoteRepository noteRepository) {
        this.noteKindRepository = noteKindRepository;
        this.noteRepository = noteRepository;
    }

    /**
     * 查询全部用途。
     *
     * @return 用途列表
     */
    @Transactional(readOnly = true)
    public List<NoteKindResponse> findAll() {
        return noteKindRepository.findAllByOrderBySortOrderAscNameAsc()
                .stream()
                .map(NoteKindResponse::from)
                .toList();
    }

    /**
     * 创建用途。
     *
     * @param request 用途请求
     * @return 用途响应
     */
    @Transactional
    public NoteKindResponse create(NoteKindRequest request) {
        String normalizedName = normalizeName(request.name());
        if (noteKindRepository.existsByName(normalizedName)) {
            throw new BusinessException("用途已存在：" + normalizedName);
        }
        NoteKind noteKind = noteKindRepository.save(new NoteKind(
                normalizedName,
                resolveSortOrder(request.sortOrder()),
                false
        ));
        return NoteKindResponse.from(noteKind);
    }

    /**
     * 更新用途。
     *
     * @param id 用途ID
     * @param request 用途请求
     * @return 用途响应
     */
    @Transactional
    public NoteKindResponse update(Long id, NoteKindRequest request) {
        NoteKind noteKind = findNoteKind(id);
        String normalizedName = normalizeName(request.name());
        if (noteKindRepository.existsByNameAndIdNot(normalizedName, id)) {
            throw new BusinessException("用途已存在：" + normalizedName);
        }
        noteKind.update(normalizedName, resolveSortOrder(request.sortOrder()));
        return NoteKindResponse.from(noteKind);
    }

    /**
     * 删除用途，并从关联笔记中移除该用途。
     *
     * @param id 用途ID
     */
    @Transactional
    public void delete(Long id) {
        NoteKind noteKind = findNoteKind(id);
        List<Note> relatedNotes = noteRepository.findDistinctByNoteKindId(id);
        for (Note note : relatedNotes) {
            note.changeNoteKind(null);
        }
        noteKindRepository.delete(noteKind);
    }

    /**
     * 查询用途实体。
     *
     * @param id 用途ID
     * @return 用途实体
     */
    private NoteKind findNoteKind(Long id) {
        return noteKindRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("用途不存在：" + id));
    }

    /**
     * 标准化用途名称。
     *
     * @param name 原始名称
     * @return 标准名称
     */
    private String normalizeName(String name) {
        return name == null ? "" : name.trim();
    }

    /**
     * 解析排序值。
     *
     * @param sortOrder 请求排序值
     * @return 排序值
     */
    private Long resolveSortOrder(Long sortOrder) {
        if (sortOrder != null) {
            return sortOrder;
        }
        return noteKindRepository.findAllByOrderBySortOrderAscNameAsc()
                .stream()
                .map(NoteKind::getSortOrder)
                .max(Long::compareTo)
                .orElse(0L) + SORT_ORDER_STEP;
    }
}
