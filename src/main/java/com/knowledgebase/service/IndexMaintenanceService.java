package com.knowledgebase.service;

import com.knowledgebase.dto.AdminIndexHealthResponse;
import com.knowledgebase.dto.AdminVectorCleanupResponse;
import com.knowledgebase.entity.Note;
import com.knowledgebase.repository.NoteRepository;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 搜索索引运维服务。
 */
@Service
public class IndexMaintenanceService {

    private final NoteRepository noteRepository;
    private final IndexService indexService;
    private final VectorIndexService vectorIndexService;

    /**
     * 创建索引运维服务。
     *
     * @param noteRepository 笔记仓库
     * @param indexService 全文索引服务
     * @param vectorIndexService 向量索引服务
     */
    public IndexMaintenanceService(
            NoteRepository noteRepository,
            IndexService indexService,
            VectorIndexService vectorIndexService
    ) {
        this.noteRepository = noteRepository;
        this.indexService = indexService;
        this.vectorIndexService = vectorIndexService;
    }

    /**
     * 检查全文和向量索引健康状态。
     *
     * @return 健康状态
     */
    @Transactional(readOnly = true)
    public AdminIndexHealthResponse health() {
        long activeCount = activeNotes().size();
        int searchIndexedCount = indexService.indexedCount();
        int vectorIndexedCount = vectorIndexService.indexedCount();
        boolean searchHealthy = activeCount == searchIndexedCount;
        boolean vectorHealthy = vectorIndexedCount == 0 || vectorIndexedCount == activeCount;
        String message = searchHealthy && vectorHealthy
                ? "索引数量与当前有效笔记一致"
                : "索引数量与当前有效笔记不一致，建议重建索引或清理无效向量";
        return new AdminIndexHealthResponse(
                activeCount,
                searchIndexedCount,
                vectorIndexedCount,
                searchHealthy,
                vectorHealthy,
                message
        );
    }

    /**
     * 清理无效向量。
     *
     * @return 清理结果
     */
    @Transactional(readOnly = true)
    public AdminVectorCleanupResponse cleanupInvalidVectors() {
        List<Long> activeNoteIds = activeNotes().stream().map(Note::getId).toList();
        int removedCount = vectorIndexService.cleanupInvalidVectors(activeNoteIds);
        int indexedCount = vectorIndexService.indexedCount();
        String message = removedCount == 0 ? "未发现无效向量" : "已清理无效向量";
        return new AdminVectorCleanupResponse(removedCount, indexedCount, message);
    }

    /**
     * 查询当前有效笔记。
     *
     * @return 有效笔记
     */
    private List<Note> activeNotes() {
        return noteRepository.findByDeletedFalseAndArchivedFalse();
    }
}
