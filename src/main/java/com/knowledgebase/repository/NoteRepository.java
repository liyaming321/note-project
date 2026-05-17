package com.knowledgebase.repository;

import com.knowledgebase.entity.Note;
import com.knowledgebase.entity.NoteType;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

/**
 * 笔记数据访问接口。
 */
public interface NoteRepository extends JpaRepository<Note, Long>, JpaSpecificationExecutor<Note> {

    /**
     * 查询全部未删除笔记。
     *
     * @return 未删除笔记列表
     */
    @EntityGraph(attributePaths = {"category", "tags", "noteKind"})
    List<Note> findByDeletedFalse();

    /**
     * 查询全部未删除且未归档笔记。
     *
     * @return 未删除未归档笔记列表
     */
    @EntityGraph(attributePaths = {"category", "tags", "noteKind"})
    List<Note> findByDeletedFalseAndArchivedFalse();

    /**
     * 按 ID 批量查询未删除笔记。
     *
     * @param ids 笔记ID集合
     * @return 未删除笔记列表
     */
    @EntityGraph(attributePaths = {"category", "tags", "noteKind"})
    List<Note> findByIdInAndDeletedFalse(Collection<Long> ids);

    /**
     * 按 ID 批量查询未删除且未归档笔记。
     *
     * @param ids 笔记ID集合
     * @return 未删除未归档笔记列表
     */
    @EntityGraph(attributePaths = {"category", "tags", "noteKind"})
    List<Note> findByIdInAndDeletedFalseAndArchivedFalse(Collection<Long> ids);

    /**
     * 按 ID 批量查询笔记。
     *
     * @param ids 笔记ID集合
     * @return 笔记列表
     */
    @EntityGraph(attributePaths = {"category", "tags", "noteKind"})
    List<Note> findByIdIn(Collection<Long> ids);

    /**
     * 查询关联了指定标签的全部笔记。
     *
     * @param tagId 标签ID
     * @return 笔记列表
     */
    @EntityGraph(attributePaths = {"category", "tags", "noteKind"})
    List<Note> findDistinctByTags_Id(Long tagId);

    /**
     * 查询关联了指定用途的全部笔记。
     *
     * @param noteKindId 用途ID
     * @return 笔记列表
     */
    @EntityGraph(attributePaths = {"category", "tags", "noteKind"})
    List<Note> findDistinctByNoteKindId(Long noteKindId);

    /**
     * 判断指定分类下是否存在笔记。
     *
     * @param categoryId 分类ID
     * @return 是否存在
     */
    boolean existsByCategoryId(Long categoryId);

    /**
     * 判断指定类型下是否存在未删除笔记。
     *
     * @param type 内容格式
     * @param deleted 是否删除
     * @return 是否存在
     */
    boolean existsByTypeAndDeleted(NoteType type, boolean deleted);
}
