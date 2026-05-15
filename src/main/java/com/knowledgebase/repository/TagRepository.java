package com.knowledgebase.repository;

import com.knowledgebase.entity.Tag;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * 标签数据访问接口。
 */
public interface TagRepository extends JpaRepository<Tag, Long> {

    /**
     * 按标签名称查询标签。
     *
     * @param name 标签名称
     * @return 标签对象
     */
    Optional<Tag> findByName(String name);

    /**
     * 批量查询标签。
     *
     * @param names 标签名称集合
     * @return 标签列表
     */
    List<Tag> findByNameIn(Collection<String> names);

    /**
     * 判断标签名称是否存在。
     *
     * @param name 标签名称
     * @return 是否存在
     */
    boolean existsByName(String name);
}
