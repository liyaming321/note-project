package com.knowledgebase.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Lob;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.LocalDateTime;
import org.hibernate.annotations.Comment;
import org.hibernate.annotations.CreationTimestamp;

/**
 * 笔记历史版本实体。
 */
@Entity
@Table(
        name = "note_histories",
        uniqueConstraints = @UniqueConstraint(name = "uk_note_histories_note_version", columnNames = {"note_id", "version"})
)
@Comment("笔记历史版本表")
public class NoteHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Comment("历史版本ID")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "note_id", nullable = false)
    @Comment("所属笔记")
    private Note note;

    @Column(nullable = false)
    @Comment("历史版本号")
    private Integer version;

    @Column(nullable = false, length = 160)
    @Comment("历史标题")
    private String title;

    @Lob
    @Column(nullable = false)
    @Comment("历史原始内容")
    private String content;

    @Lob
    @Column(name = "content_text", nullable = false)
    @Comment("历史纯文本内容")
    private String contentText;

    @Column(nullable = false, length = 20)
    @Comment("历史笔记类型")
    private String type;

    @Column(length = 40)
    @Comment("历史代码语言")
    private String language;

    @Column(name = "category_id")
    @Comment("历史分类ID")
    private Long categoryId;

    @Column(name = "category_name", length = 80)
    @Comment("历史分类名称")
    private String categoryName;

    @Lob
    @Column(name = "tag_names_json", nullable = false)
    @Comment("历史标签名称快照JSON")
    private String tagNamesJson;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    @Comment("创建时间")
    private LocalDateTime createdAt;

    /**
     * JPA 使用的无参构造器。
     */
    protected NoteHistory() {
    }

    /**
     * 创建笔记历史版本。
     *
     * @param note 所属笔记
     * @param version 版本号
     * @param title 标题
     * @param content 内容
     * @param contentText 纯文本内容
     * @param type 笔记类型
     * @param language 代码语言
     * @param categoryId 分类ID
     * @param categoryName 分类名称
     * @param tagNamesJson 标签名称JSON
     */
    public NoteHistory(
            Note note,
            Integer version,
            String title,
            String content,
            String contentText,
            String type,
            String language,
            Long categoryId,
            String categoryName,
            String tagNamesJson
    ) {
        this.note = note;
        this.version = version;
        this.title = title;
        this.content = content;
        this.contentText = contentText;
        this.type = type;
        this.language = language;
        this.categoryId = categoryId;
        this.categoryName = categoryName;
        this.tagNamesJson = tagNamesJson;
    }

    /**
     * 获取历史版本ID。
     *
     * @return 历史版本ID
     */
    public Long getId() {
        return id;
    }

    /**
     * 获取所属笔记。
     *
     * @return 所属笔记
     */
    public Note getNote() {
        return note;
    }

    /**
     * 获取版本号。
     *
     * @return 版本号
     */
    public Integer getVersion() {
        return version;
    }

    /**
     * 获取标题。
     *
     * @return 标题
     */
    public String getTitle() {
        return title;
    }

    /**
     * 获取内容。
     *
     * @return 内容
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
     * 获取历史类型。
     *
     * @return 类型
     */
    public String getType() {
        return type;
    }

    /**
     * 获取语言。
     *
     * @return 语言
     */
    public String getLanguage() {
        return language;
    }

    /**
     * 获取分类ID。
     *
     * @return 分类ID
     */
    public Long getCategoryId() {
        return categoryId;
    }

    /**
     * 获取分类名称。
     *
     * @return 分类名称
     */
    public String getCategoryName() {
        return categoryName;
    }

    /**
     * 获取标签名称JSON。
     *
     * @return 标签名称JSON
     */
    public String getTagNamesJson() {
        return tagNamesJson;
    }

    /**
     * 获取创建时间。
     *
     * @return 创建时间
     */
    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
}
