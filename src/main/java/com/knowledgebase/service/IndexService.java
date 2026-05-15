package com.knowledgebase.service;

import com.knowledgebase.config.KnowledgeBaseProperties;
import com.knowledgebase.entity.Category;
import com.knowledgebase.entity.Note;
import com.knowledgebase.entity.NoteType;
import com.knowledgebase.entity.Tag;
import com.knowledgebase.exception.BusinessException;
import com.knowledgebase.repository.NoteRepository;
import com.knowledgebase.util.MarkdownTextExtractor;
import com.knowledgebase.util.SearchIndexFields;
import jakarta.annotation.PreDestroy;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.ZoneId;
import java.util.List;
import java.util.Locale;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.LongPoint;
import org.apache.lucene.document.NumericDocValuesField;
import org.apache.lucene.document.StoredField;
import org.apache.lucene.document.StringField;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.Term;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Lucene 索引维护服务。
 */
@Service
public class IndexService {

    private static final ZoneId SYSTEM_ZONE = ZoneId.systemDefault();

    private final NoteRepository noteRepository;
    private final Analyzer analyzer;
    private final Path indexPath;

    /**
     * 创建索引维护服务。
     *
     * @param noteRepository 笔记仓库
     * @param analyzer 搜索分析器
     * @param properties 知识库配置
     */
    public IndexService(NoteRepository noteRepository, Analyzer analyzer, KnowledgeBaseProperties properties) {
        this.noteRepository = noteRepository;
        this.analyzer = analyzer;
        this.indexPath = Paths.get(properties.getIndexPath()).toAbsolutePath().normalize();
    }

    /**
     * 更新或创建单篇笔记索引。
     *
     * @param note 笔记实体
     */
    public synchronized void upsertNote(Note note) {
        if (note.isDeleted() || note.isArchived()) {
            deleteNote(note.getId());
            return;
        }
        try (Directory directory = openDirectory();
             IndexWriter writer = createWriter(directory, IndexWriterConfig.OpenMode.CREATE_OR_APPEND)) {
            writer.updateDocument(noteIdTerm(note.getId()), toDocument(note));
        } catch (IOException ex) {
            throw new BusinessException("更新搜索索引失败：" + ex.getMessage());
        }
    }

    /**
     * 删除单篇笔记索引。
     *
     * @param noteId 笔记ID
     */
    public synchronized void deleteNote(Long noteId) {
        try (Directory directory = openDirectory();
             IndexWriter writer = createWriter(directory, IndexWriterConfig.OpenMode.CREATE_OR_APPEND)) {
            writer.deleteDocuments(noteIdTerm(noteId));
        } catch (IOException ex) {
            throw new BusinessException("删除搜索索引失败：" + ex.getMessage());
        }
    }

    /**
     * 全量重建索引。
     *
     * @return 重建后的索引笔记数量
     */
    @Transactional(readOnly = true)
    public synchronized int rebuild() {
        List<Note> notes = noteRepository.findByDeletedFalseAndArchivedFalse();
        try (Directory directory = openDirectory();
             IndexWriter writer = createWriter(directory, IndexWriterConfig.OpenMode.CREATE)) {
            for (Note note : notes) {
                writer.addDocument(toDocument(note));
            }
            return notes.size();
        } catch (IOException ex) {
            throw new BusinessException("重建搜索索引失败：" + ex.getMessage());
        }
    }

    /**
     * 判断索引是否已经存在。
     *
     * @return 索引是否存在
     */
    public synchronized boolean indexExists() {
        if (Files.notExists(indexPath)) {
            return false;
        }
        try (Directory directory = FSDirectory.open(indexPath)) {
            return DirectoryReader.indexExists(directory);
        } catch (IOException ex) {
            throw new BusinessException("检查搜索索引失败：" + ex.getMessage());
        }
    }

    /**
     * 获取索引目录。
     *
     * @return 索引目录
     */
    public Path getIndexPath() {
        return indexPath;
    }

    /**
     * 关闭搜索分析器。
     */
    @PreDestroy
    public void closeAnalyzer() {
        analyzer.close();
    }

    /**
     * 打开文件系统索引目录。
     *
     * @return Lucene 目录
     * @throws IOException 文件系统异常
     */
    private Directory openDirectory() throws IOException {
        Files.createDirectories(indexPath);
        return FSDirectory.open(indexPath);
    }

    /**
     * 创建索引写入器。
     *
     * @param directory 索引目录
     * @param openMode 打开模式
     * @return 索引写入器
     */
    private IndexWriter createWriter(Directory directory, IndexWriterConfig.OpenMode openMode) throws IOException {
        IndexWriterConfig config = new IndexWriterConfig(analyzer);
        config.setOpenMode(openMode);
        return new IndexWriter(directory, config);
    }

    /**
     * 将笔记转换为 Lucene 文档。
     *
     * @param note 笔记实体
     * @return Lucene 文档
     */
    private Document toDocument(Note note) {
        Document document = new Document();
        document.add(new StringField(SearchIndexFields.ID, String.valueOf(note.getId()), Field.Store.YES));
        document.add(new TextField(SearchIndexFields.TITLE, safeText(note.getTitle()), Field.Store.YES));
        document.add(new TextField(SearchIndexFields.CONTENT_PLAIN, safeText(note.getContentText()), Field.Store.YES));
        document.add(new TextField(SearchIndexFields.CONTENT_CODE, extractCodeContent(note), Field.Store.YES));
        document.add(new StringField(SearchIndexFields.TYPE, note.getType().name(), Field.Store.YES));
        document.add(new StringField(SearchIndexFields.STATUS, note.getStatus().name(), Field.Store.YES));
        addOptionalString(document, SearchIndexFields.LANGUAGE, normalizeExact(note.getLanguage()));
        addCategoryFields(document, note.getCategory());
        for (Tag tag : note.getTags()) {
            document.add(new StringField(SearchIndexFields.TAGS, normalizeExact(tag.getName()), Field.Store.YES));
        }
        document.add(new StoredField(SearchIndexFields.TAG_NAMES, joinedTags(note)));
        long createdTime = createdTime(note);
        document.add(new LongPoint(SearchIndexFields.CREATED_TIME, createdTime));
        document.add(new NumericDocValuesField(SearchIndexFields.CREATED_TIME_SORT, createdTime));
        long updatedTime = updatedTime(note);
        document.add(new LongPoint(SearchIndexFields.UPDATED_TIME, updatedTime));
        document.add(new NumericDocValuesField(SearchIndexFields.UPDATED_TIME_SORT, updatedTime));
        return document;
    }

    /**
     * 添加分类索引字段。
     *
     * @param document Lucene 文档
     * @param category 分类实体
     */
    private void addCategoryFields(Document document, Category category) {
        if (category == null) {
            return;
        }
        document.add(new TextField(SearchIndexFields.CATEGORY, safeText(category.getName()), Field.Store.YES));
        document.add(new StringField(SearchIndexFields.CATEGORY_EXACT, normalizeExact(category.getName()), Field.Store.YES));
        document.add(new StringField(SearchIndexFields.CATEGORY_ID, String.valueOf(category.getId()), Field.Store.YES));
    }

    /**
     * 添加可选精确匹配字段。
     *
     * @param document Lucene 文档
     * @param fieldName 字段名
     * @param value 字段值
     */
    private void addOptionalString(Document document, String fieldName, String value) {
        if (!value.isBlank()) {
            document.add(new StringField(fieldName, value, Field.Store.YES));
        }
    }

    /**
     * 提取代码索引内容。
     *
     * @param note 笔记实体
     * @return 代码内容
     */
    private String extractCodeContent(Note note) {
        if (note.getType() == NoteType.CODE) {
            return safeText(note.getContent());
        }
        return MarkdownTextExtractor.extractCodeBlocks(note.getContent());
    }

    /**
     * 拼接标签名称。
     *
     * @param note 笔记实体
     * @return 标签名称文本
     */
    private String joinedTags(Note note) {
        return note.getTags().stream()
                .map(Tag::getName)
                .map(this::safeText)
                .filter(value -> !value.isBlank())
                .sorted()
                .reduce((left, right) -> left + "," + right)
                .orElse("");
    }

    /**
     * 构建笔记 ID 查询条件。
     *
     * @param noteId 笔记ID
     * @return Lucene Term
     */
    private Term noteIdTerm(Long noteId) {
        return new Term(SearchIndexFields.ID, String.valueOf(noteId));
    }

    /**
     * 获取创建时间时间戳。
     *
     * @param note 笔记实体
     * @return 毫秒时间戳
     */
    private long createdTime(Note note) {
        if (note.getCreatedAt() == null) {
            return System.currentTimeMillis();
        }
        return note.getCreatedAt().atZone(SYSTEM_ZONE).toInstant().toEpochMilli();
    }

    /**
     * 获取更新时间时间戳。
     *
     * @param note 笔记实体
     * @return 毫秒时间戳
     */
    private long updatedTime(Note note) {
        if (note.getUpdatedAt() == null) {
            return System.currentTimeMillis();
        }
        return note.getUpdatedAt().atZone(SYSTEM_ZONE).toInstant().toEpochMilli();
    }

    /**
     * 标准化精确匹配字段。
     *
     * @param value 原始值
     * @return 标准化值
     */
    private String normalizeExact(String value) {
        return safeText(value).toLowerCase(Locale.ROOT);
    }

    /**
     * 获取安全文本。
     *
     * @param value 原始值
     * @return 安全文本
     */
    private String safeText(String value) {
        return value == null ? "" : value.trim();
    }
}
