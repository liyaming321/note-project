package com.knowledgebase.repository;

import com.knowledgebase.entity.Category;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * 分类数据访问接口。
 */
public interface CategoryRepository extends JpaRepository<Category, Long> {

    /**
     * 按分类名称查询分类。
     *
     * @param name 分类名称
     * @return 分类对象
     */
    Optional<Category> findByName(String name);

    /**
     * 判断分类名称是否存在。
     *
     * @param name 分类名称
     * @return 是否存在
     */
    boolean existsByName(String name);

    /**
     * 判断除指定分类外是否存在同名分类。
     *
     * @param name 分类名称
     * @param id 分类ID
     * @return 是否存在
     */
    boolean existsByNameAndIdNot(String name, Long id);

    /**
     * 判断指定父分类下是否存在子分类。
     *
     * @param parentId 父分类ID
     * @return 是否存在
     */
    boolean existsByParentId(Long parentId);
}
