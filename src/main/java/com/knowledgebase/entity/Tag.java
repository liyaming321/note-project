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
 * 笔记标签实体。
 */
@Entity
@Table(name = "tags")
@Comment("笔记标签表")
public class Tag {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Comment("标签ID")
    private Long id;

    @Column(nullable = false, unique = true, length = 60)
    @Comment("标签名称")
    private String name;

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
    protected Tag() {
    }

    /**
     * 创建标签。
     *
     * @param name 标签名称
     */
    public Tag(String name) {
        this.name = name;
    }

    /**
     * 更新标签名称。
     *
     * @param name 标签名称
     */
    public void rename(String name) {
        this.name = name;
    }

    /**
     * 获取标签ID。
     *
     * @return 标签ID
     */
    public Long getId() {
        return id;
    }

    /**
     * 获取标签名称。
     *
     * @return 标签名称
     */
    public String getName() {
        return name;
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
