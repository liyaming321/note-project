package com.knowledgebase.repository;

import com.knowledgebase.entity.NoteHistory;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * 笔记历史版本数据访问接口。
 */
public interface NoteHistoryRepository extends JpaRepository<NoteHistory, Long> {

    /**
     * 按笔记 ID 和版本号查询历史版本。
     *
     * @param noteId 笔记ID
     * @param version 版本号
     * @return 历史版本
     */
    Optional<NoteHistory> findByNote_IdAndVersion(Long noteId, Integer version);

    /**
     * 查询笔记的最新历史版本。
     *
     * @param noteId 笔记ID
     * @return 最新历史版本
     */
    Optional<NoteHistory> findTopByNote_IdOrderByVersionDesc(Long noteId);

    /**
     * 按版本倒序查询笔记的所有历史版本。
     *
     * @param noteId 笔记ID
     * @return 历史版本列表
     */
    List<NoteHistory> findByNote_IdOrderByVersionDesc(Long noteId);

    /**
     * 按版本正序查询笔记的所有历史版本。
     *
     * @param noteId 笔记ID
     * @return 历史版本列表
     */
    List<NoteHistory> findByNote_IdOrderByVersionAsc(Long noteId);
}
