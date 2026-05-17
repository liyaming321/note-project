package com.knowledgebase.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.knowledgebase.config.KnowledgeBaseProperties;
import com.knowledgebase.dto.NoteHistoryDetailResponse;
import com.knowledgebase.dto.NoteHistorySummaryResponse;
import com.knowledgebase.entity.Note;
import com.knowledgebase.entity.NoteHistory;
import com.knowledgebase.entity.Tag;
import com.knowledgebase.exception.BusinessException;
import com.knowledgebase.exception.ResourceNotFoundException;
import com.knowledgebase.repository.NoteHistoryRepository;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 笔记历史版本服务。
 */
@Service
public class NoteHistoryService {

    private final NoteHistoryRepository noteHistoryRepository;
    private final ObjectMapper objectMapper;
    private final int maxVersions;

    /**
     * 创建笔记历史版本服务。
     *
     * @param noteHistoryRepository 历史版本仓库
     * @param objectMapper JSON 工具
     * @param properties 知识库配置
     */
    public NoteHistoryService(
            NoteHistoryRepository noteHistoryRepository,
            ObjectMapper objectMapper,
            KnowledgeBaseProperties properties
    ) {
        this.noteHistoryRepository = noteHistoryRepository;
        this.objectMapper = objectMapper;
        this.maxVersions = Math.max(properties.getHistoryMaxVersions(), 1);
    }

    /**
     * 保存当前笔记快照为历史版本。
     *
     * @param note 笔记实体
     * @return 历史版本实体
     */
    @Transactional
    public NoteHistory saveSnapshot(Note note) {
        int nextVersion = noteHistoryRepository.findTopByNote_IdOrderByVersionDesc(note.getId())
                .map(history -> history.getVersion() + 1)
                .orElse(1);
        NoteHistory history = new NoteHistory(
                note,
                nextVersion,
                note.getTitle(),
                note.getContent(),
                note.getContentText(),
                note.getType().name(),
                note.getLanguage(),
                note.getNoteKind() == null ? null : note.getNoteKind().getId(),
                note.getNoteKind() == null ? null : note.getNoteKind().getName(),
                note.getCategory() == null ? null : note.getCategory().getId(),
                note.getCategory() == null ? null : note.getCategory().getName(),
                serializeTags(note.getTags())
        );
        NoteHistory savedHistory = noteHistoryRepository.save(history);
        trimOldVersions(note.getId());
        return savedHistory;
    }

    /**
     * 查询笔记的历史版本列表。
     *
     * @param noteId 笔记ID
     * @return 历史版本列表
     */
    @Transactional(readOnly = true)
    public List<NoteHistorySummaryResponse> listHistory(Long noteId) {
        return noteHistoryRepository.findByNote_IdOrderByVersionDesc(noteId).stream()
                .map(history -> new NoteHistorySummaryResponse(
                        noteId,
                        history.getVersion(),
                        history.getTitle(),
                        history.getCreatedAt()
                ))
                .toList();
    }

    /**
     * 查询指定版本的历史详情。
     *
     * @param noteId 笔记ID
     * @param version 版本号
     * @return 历史详情
     */
    @Transactional(readOnly = true)
    public NoteHistoryDetailResponse findHistoryDetail(Long noteId, int version) {
        NoteHistory history = findHistoryEntity(noteId, version);
        return toDetailResponse(noteId, history);
    }

    /**
     * 查询指定历史实体。
     *
     * @param noteId 笔记ID
     * @param version 版本号
     * @return 历史实体
     */
    @Transactional(readOnly = true)
    public NoteHistory findHistoryEntity(Long noteId, int version) {
        return noteHistoryRepository.findByNote_IdAndVersion(noteId, version)
                .orElseThrow(() -> new ResourceNotFoundException("笔记历史版本不存在：" + noteId + " / " + version));
    }

    /**
     * 获取历史版本中的标签名称集合。
     *
     * @param history 历史实体
     * @return 标签名称集合
     */
    public Set<String> resolveTagNames(NoteHistory history) {
        List<String> tagNames = deserializeTags(history.getTagNamesJson());
        return new LinkedHashSet<>(tagNames);
    }

    /**
     * 获取历史版本的分类ID。
     *
     * @param history 历史实体
     * @return 分类ID
     */
    public Long resolveCategoryId(NoteHistory history) {
        return history.getCategoryId();
    }

    /**
     * 将历史实体转换为详情响应。
     *
     * @param noteId 笔记ID
     * @param history 历史实体
     * @return 详情响应
     */
    private NoteHistoryDetailResponse toDetailResponse(Long noteId, NoteHistory history) {
        return new NoteHistoryDetailResponse(
                noteId,
                history.getVersion(),
                history.getTitle(),
                history.getContent(),
                history.getContentText(),
                history.getType(),
                history.getLanguage(),
                history.getNoteKindId(),
                history.getNoteKindName(),
                history.getCategoryId(),
                history.getCategoryName(),
                deserializeTags(history.getTagNamesJson()),
                history.getCreatedAt()
        );
    }

    /**
     * 清理超出保留数量的旧历史版本。
     *
     * @param noteId 笔记ID
     */
    private void trimOldVersions(Long noteId) {
        List<NoteHistory> histories = noteHistoryRepository.findByNote_IdOrderByVersionAsc(noteId);
        int overflow = histories.size() - maxVersions;
        if (overflow <= 0) {
            return;
        }
        List<NoteHistory> removableHistories = new ArrayList<>(histories.subList(0, overflow));
        noteHistoryRepository.deleteAll(removableHistories);
    }

    /**
     * 将标签名称集合序列化为 JSON。
     *
     * @param tags 标签集合
     * @return JSON 字符串
     */
    private String serializeTags(Set<Tag> tags) {
        List<String> tagNames = tags.stream()
                .map(Tag::getName)
                .sorted()
                .toList();
        try {
            return objectMapper.writeValueAsString(tagNames);
        } catch (JsonProcessingException ex) {
            throw new BusinessException("序列化历史标签失败：" + ex.getMessage());
        }
    }

    /**
     * 将标签名称 JSON 反序列化为列表。
     *
     * @param json 标签名称 JSON
     * @return 标签名称列表
     */
    private List<String> deserializeTags(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<List<String>>() {
            });
        } catch (JsonProcessingException ex) {
            throw new BusinessException("反序列化历史标签失败：" + ex.getMessage());
        }
    }
}
