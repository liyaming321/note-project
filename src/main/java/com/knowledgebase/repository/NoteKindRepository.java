package com.knowledgebase.repository;

import com.knowledgebase.entity.NoteKind;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * 笔记用途数据访问接口。
 */
public interface NoteKindRepository extends JpaRepository<NoteKind, Long> {

    /**
     * 按排序值和名称查询全部类型。
     *
     * @return 类型列表
     */
    List<NoteKind> findAllByOrderBySortOrderAscNameAsc();

    /**
     * 判断类型名称是否存在。
     *
     * @param name 类型名称
     * @return 是否存在
     */
    boolean existsByName(String name);

    /**
     * 判断除指定 ID 外是否存在同名类型。
     *
     * @param name 类型名称
     * @param id 排除的类型ID
     * @return 是否存在
     */
    boolean existsByNameAndIdNot(String name, Long id);
}
