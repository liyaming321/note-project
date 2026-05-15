package com.knowledgebase.entity;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.Lob;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import java.util.LinkedHashSet;
import java.util.Set;
import org.hibernate.annotations.Comment;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

/**
 * 笔记实体。
 */
@Entity
@Table(name = "notes")
@Comment("笔记表")
public class Note {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Comment("笔记ID")
    private Long id;

    @Column(nullable = false, length = 160)
    @Comment("笔记标题")
    private String title;

    @Lob
    @Column(nullable = false)
    @Comment("Markdown或代码原始内容")
    private String content;

    @Lob
    @Column(name = "content_text", nullable = false)
    @Comment("用于检索和摘要展示的纯文本内容")
    private String contentText;

    @Column(length = 500)
    @Comment("AI或人工维护的笔记摘要")
    private String summary;

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    @Comment("笔记类型")
    private NoteType type;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Comment("笔记发布状态：草稿或已发布")
    private NoteStatus status = NoteStatus.PUBLISHED;

    @Column(length = 40)
    @Comment("代码语言")
    private String language;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "category_id")
    @Comment("所属分类")
    private Category category;

    @ManyToMany(cascade = {CascadeType.PERSIST, CascadeType.MERGE})
    @JoinTable(
            name = "note_tags",
            joinColumns = @JoinColumn(name = "note_id"),
            inverseJoinColumns = @JoinColumn(name = "tag_id")
    )
    @Comment("笔记标签集合")
    private Set<Tag> tags = new LinkedHashSet<>();

    @Column
    @Comment("是否置顶")
    private boolean pinned;

    @Column(nullable = false)
    @Comment("是否收藏")
    private boolean favorite;

    @Column(name = "sort_order")
    @Comment("自定义排序值")
    private Long sortOrder;

    @Column(nullable = false)
    @Comment("是否归档")
    private boolean archived;

    @Column(name = "is_deleted", nullable = false)
    @Comment("是否逻辑删除")
    private boolean deleted;

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
    protected Note() {
    }

    /**
     * 创建笔记。
     *
     * @param title 标题
     * @param content 原始内容
     * @param contentText 纯文本内容
     * @param type 类型
     * @param language 代码语言
     * @param category 所属分类
     * @param tags 标签集合
     */
    public Note(
            String title,
            String content,
            String contentText,
            NoteType type,
            String language,
            Category category,
            Set<Tag> tags
    ) {
        this.title = title;
        this.content = content;
        this.contentText = contentText;
        this.type = type;
        this.language = language;
        this.category = category;
        this.tags = new LinkedHashSet<>(tags);
        this.pinned = false;
        this.favorite = false;
        this.sortOrder = System.currentTimeMillis();
        this.status = NoteStatus.PUBLISHED;
        this.archived = false;
        this.deleted = false;
    }

    /**
     * 更新笔记主体内容。
     *
     * @param title 标题
     * @param content 原始内容
     * @param contentText 纯文本内容
     * @param type 类型
     * @param language 代码语言
     * @param category 所属分类
     * @param tags 标签集合
     */
    public void update(
            String title,
            String content,
            String contentText,
            String summary,
            NoteType type,
            String language,
            Category category,
            Set<Tag> tags
    ) {
        this.title = title;
        this.content = content;
        this.contentText = contentText;
        this.summary = normalizeSummary(summary);
        this.type = type;
        this.language = language;
        this.category = category;
        this.tags.clear();
        this.tags.addAll(tags);
    }

    /**
     * 更新收藏状态。
     *
     * @param favorite 是否收藏
     */
    public void changeFavorite(boolean favorite) {
        this.favorite = favorite;
    }

    /**
     * 更新置顶状态。
     *
     * @param pinned 是否置顶
     */
    public void changePinned(boolean pinned) {
        this.pinned = pinned;
    }

    /**
     * 更新发布状态。
     *
     * @param status 发布状态
     */
    public void changeStatus(NoteStatus status) {
        this.status = status == null ? NoteStatus.PUBLISHED : status;
    }

    /**
     * 更新归档状态。
     *
     * @param archived 是否归档
     */
    public void changeArchived(boolean archived) {
        this.archived = archived;
    }

    /**
     * 更新自定义排序值。
     *
     * @param sortOrder 自定义排序值
     */
    public void changeSortOrder(Long sortOrder) {
        this.sortOrder = sortOrder;
    }

    /**
     * 更新笔记摘要。
     *
     * @param summary 摘要
     */
    public void changeSummary(String summary) {
        this.summary = normalizeSummary(summary);
    }

    /**
     * 标记笔记为已删除。
     */
    public void markDeleted() {
        this.deleted = true;
    }

    /**
     * 恢复已删除笔记。
     */
    public void restore() {
        this.deleted = false;
    }

    /**
     * 获取笔记ID。
     *
     * @return 笔记ID
     */
    public Long getId() {
        return id;
    }

    /**
     * 获取笔记标题。
     *
     * @return 笔记标题
     */
    public String getTitle() {
        return title;
    }

    /**
     * 获取原始内容。
     *
     * @return 原始内容
     */
    public String getContent() {
        return content;
    }

    /**
     * 获取纯文本内容。
     *
     * @return 纯文本内容
     */
    public String getContentText() {
        return contentText;
    }

    /**
     * 获取笔记摘要。
     *
     * @return 笔记摘要
     */
    public String getSummary() {
        return summary;
    }

    /**
     * 获取笔记类型。
     *
     * @return 笔记类型
     */
    public NoteType getType() {
        return type;
    }

    /**
     * 获取发布状态。
     *
     * @return 发布状态
     */
    public NoteStatus getStatus() {
        return status == null ? NoteStatus.PUBLISHED : status;
    }

    /**
     * 获取代码语言。
     *
     * @return 代码语言
     */
    public String getLanguage() {
        return language;
    }

    /**
     * 获取分类。
     *
     * @return 分类
     */
    public Category getCategory() {
        return category;
    }

    /**
     * 获取标签集合。
     *
     * @return 标签集合
     */
    public Set<Tag> getTags() {
        return tags;
    }

    /**
     * 是否置顶。
     *
     * @return 是否置顶
     */
    public boolean isPinned() {
        return pinned;
    }

    /**
     * 是否收藏。
     *
     * @return 是否收藏
     */
    public boolean isFavorite() {
        return favorite;
    }

    /**
     * 获取自定义排序值。
     *
     * @return 自定义排序值
     */
    public Long getSortOrder() {
        return sortOrder;
    }

    /**
     * 是否归档。
     *
     * @return 是否归档
     */
    public boolean isArchived() {
        return archived;
    }

    /**
     * 是否删除。
     *
     * @return 是否删除
     */
    public boolean isDeleted() {
        return deleted;
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

    /**
     * 标准化摘要。
     *
     * @param summary 原始摘要
     * @return 标准摘要
     */
    private String normalizeSummary(String summary) {
        if (summary == null || summary.isBlank()) {
            return null;
        }
        String compactSummary = summary.replaceAll("\\s+", " ").trim();
        return compactSummary.length() <= 500 ? compactSummary : compactSummary.substring(0, 500);
    }
}
