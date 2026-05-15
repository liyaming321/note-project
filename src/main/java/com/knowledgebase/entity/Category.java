package com.knowledgebase.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import org.hibernate.annotations.Comment;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

/**
 * 笔记分类实体。
 */
@Entity
@Table(name = "categories")
@Comment("笔记分类表")
public class Category {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Comment("分类ID")
    private Long id;

    @Column(nullable = false, unique = true, length = 80)
    @Comment("分类名称")
    private String name;

    @Column(name = "parent_id")
    @Comment("父分类ID")
    private Long parentId;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    @Comment("创建时间")
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(nullable = false)
    @Comment("更新时间")
    private LocalDateTime updatedAt;

    /**
     * JPA 使用的无参构造器。
     */
    protected Category() {
    }

    /**
     * 创建分类。
     *
     * @param name 分类名称
     * @param parentId 父分类ID
     */
    public Category(String name, Long parentId) {
        this.name = name;
        this.parentId = parentId;
    }

    /**
     * 更新分类名称与父级。
     *
     * @param name 分类名称
     * @param parentId 父分类ID
     */
    public void update(String name, Long parentId) {
        this.name = name;
        this.parentId = parentId;
    }

    /**
     * 获取分类ID。
     *
     * @return 分类ID
     */
    public Long getId() {
        return id;
    }

    /**
     * 获取分类名称。
     *
     * @return 分类名称
     */
    public String getName() {
        return name;
    }

    /**
     * 获取父分类ID。
     *
     * @return 父分类ID
     */
    public Long getParentId() {
        return parentId;
    }

    /**
     * 获取创建时间。
     *
     * @return 创建时间
     */
    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    /**
     * 获取更新时间。
     *
     * @return 更新时间
     */
    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }
}
