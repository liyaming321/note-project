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
 * 笔记用途类型实体。
 */
@Entity
@Table(name = "note_kinds")
@Comment("笔记用途类型表")
public class NoteKind {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Comment("笔记用途ID")
    private Long id;

    @Column(nullable = false, unique = true, length = 80)
    @Comment("笔记用途名称")
    private String name;

    @Column(name = "sort_order", nullable = false)
    @Comment("排序值")
    private Long sortOrder;

    @Column(nullable = false)
    @Comment("是否默认内置用途")
    private boolean builtIn;

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
    protected NoteKind() {
    }

    /**
     * 创建笔记用途。
     *
     * @param name 用途名称
     * @param sortOrder 排序值
     * @param builtIn 是否默认内置
     */
    public NoteKind(String name, Long sortOrder, boolean builtIn) {
        this.name = name;
        this.sortOrder = sortOrder;
        this.builtIn = builtIn;
    }

    /**
     * 更新笔记用途。
     *
     * @param name 用途名称
     * @param sortOrder 排序值
     */
    public void update(String name, Long sortOrder) {
        this.name = name;
        this.sortOrder = sortOrder;
    }

    /**
     * 获取用途ID。
     *
     * @return 用途ID
     */
    public Long getId() {
        return id;
    }

    /**
     * 获取用途名称。
     *
     * @return 用途名称
     */
    public String getName() {
        return name;
    }

    /**
     * 获取排序值。
     *
     * @return 排序值
     */
    public Long getSortOrder() {
        return sortOrder;
    }

    /**
     * 是否默认内置用途。
     *
     * @return 是否默认内置
     */
    public boolean isBuiltIn() {
        return builtIn;
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
