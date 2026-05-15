package com.knowledgebase.service;

import com.knowledgebase.dto.ExportZipRequest;
import com.knowledgebase.dto.MarkdownImportItemResponse;
import com.knowledgebase.dto.MarkdownImportResponse;
import com.knowledgebase.dto.NoteRequest;
import com.knowledgebase.entity.Category;
import com.knowledgebase.entity.Note;
import com.knowledgebase.entity.NoteStatus;
import com.knowledgebase.entity.NoteType;
import com.knowledgebase.entity.Tag;
import com.knowledgebase.exception.BusinessException;
import com.knowledgebase.exception.ResourceNotFoundException;
import com.knowledgebase.repository.CategoryRepository;
import com.knowledgebase.repository.NoteRepository;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.yaml.snakeyaml.Yaml;

/**
 * Markdown 导入导出业务服务。
 */
@Service
public class ImportExportService {

    private static final DateTimeFormatter ZIP_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");
    private static final int MAX_TITLE_LENGTH = 160;
    private static final int MAX_TAG_LENGTH = 60;

    private final NoteService noteService;
    private final NoteRepository noteRepository;
    private final CategoryRepository categoryRepository;
    private final Yaml yaml = new Yaml();

    /**
     * 创建 Markdown 导入导出服务。
     *
     * @param noteService 笔记服务
     * @param noteRepository 笔记仓库
     * @param categoryRepository 分类仓库
     */
    public ImportExportService(
            NoteService noteService,
            NoteRepository noteRepository,
            CategoryRepository categoryRepository
    ) {
        this.noteService = noteService;
        this.noteRepository = noteRepository;
        this.categoryRepository = categoryRepository;
    }

    /**
     * 导入 Markdown 文件或 ZIP 压缩包。
     *
     * @param files 上传文件列表
     * @return 导入结果
     */
    @Transactional
    public MarkdownImportResponse importMarkdown(MultipartFile[] files) {
        if (files == null || files.length == 0) {
            throw new BusinessException("请至少上传一个 Markdown 文件或 ZIP 压缩包");
        }
        List<MarkdownImportItemResponse> items = new ArrayList<>();
        for (MultipartFile file : files) {
            if (file == null || file.isEmpty()) {
                items.add(failed("未知文件", "文件为空"));
                continue;
            }
            String fileName = safeOriginalFileName(file.getOriginalFilename());
            if (fileName.toLowerCase(Locale.ROOT).endsWith(".zip")) {
                items.addAll(importZip(fileName, file));
            } else {
                items.add(importSingleMarkdown(fileName, readMultipartBytes(file)));
            }
        }
        int importedCount = (int) items.stream().filter(MarkdownImportItemResponse::success).count();
        return new MarkdownImportResponse(importedCount, items.size() - importedCount, items);
    }

    /**
     * 导出单篇笔记为 Markdown 文件。
     *
     * @param noteId 笔记ID
     * @return 导出的文件
     */
    @Transactional(readOnly = true)
    public ExportedFile exportMarkdown(Long noteId) {
        Note note = findExistingNote(noteId);
        return buildMarkdownFile(note);
    }

    /**
     * 批量导出 Markdown 文件为 ZIP。
     *
     * @param request 导出请求
     * @return ZIP 文件
     */
    @Transactional(readOnly = true)
    public ExportedFile exportZip(ExportZipRequest request) {
        List<Long> noteIds = request.noteIds().stream().distinct().toList();
        if (noteIds.isEmpty()) {
            throw new BusinessException("导出笔记列表不能为空");
        }
        try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
             ZipOutputStream zipOutputStream = new ZipOutputStream(outputStream, StandardCharsets.UTF_8)) {
            Map<String, Integer> usedNames = new LinkedHashMap<>();
            for (Long noteId : noteIds) {
                ExportedFile file = buildMarkdownFile(findExistingNote(noteId));
                String entryName = uniqueEntryName(file.fileName(), usedNames);
                zipOutputStream.putNextEntry(new ZipEntry(entryName));
                zipOutputStream.write(file.content());
                zipOutputStream.closeEntry();
            }
            zipOutputStream.finish();
            String zipName = "knowledge-base-" + ZIP_TIME_FORMATTER.format(LocalDateTime.now()) + ".zip";
            return new ExportedFile(zipName, "application/zip", outputStream.toByteArray());
        } catch (IOException ex) {
            throw new BusinessException("导出 ZIP 失败：" + ex.getMessage());
        }
    }

    /**
     * 导入 ZIP 压缩包中的 Markdown 文件。
     *
     * @param zipFileName ZIP 文件名
     * @param file ZIP 文件
     * @return 导入结果列表
     */
    private List<MarkdownImportItemResponse> importZip(String zipFileName, MultipartFile file) {
        List<MarkdownImportItemResponse> items = new ArrayList<>();
        try (ZipInputStream zipInputStream = new ZipInputStream(file.getInputStream(), StandardCharsets.UTF_8)) {
            ZipEntry entry = zipInputStream.getNextEntry();
            while (entry != null) {
                if (!entry.isDirectory() && entry.getName().toLowerCase(Locale.ROOT).endsWith(".md")) {
                    items.add(importSingleMarkdown(entry.getName(), zipInputStream.readAllBytes()));
                }
                zipInputStream.closeEntry();
                entry = zipInputStream.getNextEntry();
            }
        } catch (IOException ex) {
            items.add(failed(zipFileName, "读取 ZIP 失败：" + ex.getMessage()));
        }
        if (items.isEmpty()) {
            items.add(failed(zipFileName, "ZIP 中未找到 Markdown 文件"));
        }
        return items;
    }

    /**
     * 导入单个 Markdown 文件。
     *
     * @param fileName 文件名
     * @param bytes 文件内容
     * @return 导入结果
     */
    private MarkdownImportItemResponse importSingleMarkdown(String fileName, byte[] bytes) {
        if (!fileName.toLowerCase(Locale.ROOT).endsWith(".md")) {
            return failed(fileName, "仅支持 .md 文件或 .zip 压缩包");
        }
        try {
            MarkdownDocument document = parseMarkdown(fileName, new String(bytes, StandardCharsets.UTF_8));
            Long categoryId = resolveCategoryId(document.categoryName());
            NoteRequest request = new NoteRequest(
                    document.title(),
                    document.content(),
                    null,
                    NoteType.MARKDOWN,
                    NoteStatus.PUBLISHED,
                    document.language(),
                    categoryId,
                    document.tags(),
                    false,
                    false
            );
            var note = noteService.create(request);
            return new MarkdownImportItemResponse(fileName, note.id(), note.title(), true, "导入成功");
        } catch (RuntimeException ex) {
            return failed(fileName, ex.getMessage());
        }
    }

    /**
     * 解析 Markdown 文档和 YAML front matter。
     *
     * @param fileName 文件名
     * @param rawContent 原始内容
     * @return Markdown 文档
     */
    private MarkdownDocument parseMarkdown(String fileName, String rawContent) {
        FrontMatterSplit split = splitFrontMatter(rawContent);
        String content = split.content().strip();
        if (content.isBlank()) {
            throw new BusinessException("Markdown 正文不能为空");
        }
        Map<String, Object> metadata = parseMetadata(split.frontMatter());
        String title = normalizeTitle(readString(metadata, "title").orElseGet(() -> inferTitle(fileName, content)));
        String language = readString(metadata, "language").map(String::trim).filter(value -> !value.isBlank()).orElse(null);
        String categoryName = readString(metadata, "category").map(String::trim).filter(value -> !value.isBlank()).orElse(null);
        Set<String> tags = readTags(metadata.get("tags"));
        return new MarkdownDocument(title, content, tags, categoryName, language);
    }

    /**
     * 分离 YAML front matter 和正文。
     *
     * @param rawContent 原始内容
     * @return 分离结果
     */
    private FrontMatterSplit splitFrontMatter(String rawContent) {
        String normalizedContent = rawContent == null ? "" : rawContent.replace("\r\n", "\n");
        if (!normalizedContent.startsWith("---\n")) {
            return new FrontMatterSplit("", normalizedContent);
        }
        int endIndex = normalizedContent.indexOf("\n---\n", 4);
        if (endIndex < 0) {
            return new FrontMatterSplit("", normalizedContent);
        }
        String frontMatter = normalizedContent.substring(4, endIndex);
        String content = normalizedContent.substring(endIndex + "\n---\n".length());
        return new FrontMatterSplit(frontMatter, content);
    }

    /**
     * 解析 YAML 元数据。
     *
     * @param frontMatter YAML 文本
     * @return 元数据映射
     */
    private Map<String, Object> parseMetadata(String frontMatter) {
        if (frontMatter == null || frontMatter.isBlank()) {
            return Map.of();
        }
        Object parsedValue = yaml.load(frontMatter);
        if (parsedValue instanceof Map<?, ?> map) {
            Map<String, Object> metadata = new LinkedHashMap<>();
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                if (entry.getKey() != null) {
                    metadata.put(entry.getKey().toString(), entry.getValue());
                }
            }
            return metadata;
        }
        return Map.of();
    }

    /**
     * 读取字符串元数据。
     *
     * @param metadata 元数据
     * @param key 键
     * @return 字符串值
     */
    private Optional<String> readString(Map<String, Object> metadata, String key) {
        Object value = metadata.get(key);
        return value == null ? Optional.empty() : Optional.of(value.toString());
    }

    /**
     * 读取标签元数据，兼容 YAML 数组和逗号分隔字符串。
     *
     * @param value 标签元数据
     * @return 标签集合
     */
    private Set<String> readTags(Object value) {
        Set<String> tags = new LinkedHashSet<>();
        if (value instanceof Iterable<?> iterable) {
            for (Object item : iterable) {
                addNormalizedTag(tags, item);
            }
            return tags;
        }
        if (value != null) {
            for (String item : value.toString().split(",")) {
                addNormalizedTag(tags, item);
            }
        }
        return tags;
    }

    /**
     * 添加标准化标签。
     *
     * @param tags 标签集合
     * @param rawValue 原始值
     */
    private void addNormalizedTag(Set<String> tags, Object rawValue) {
        if (rawValue == null) {
            return;
        }
        String tag = rawValue.toString().trim();
        if (!tag.isBlank()) {
            tags.add(tag.length() > MAX_TAG_LENGTH ? tag.substring(0, MAX_TAG_LENGTH) : tag);
        }
    }

    /**
     * 从文件名或一级标题推断标题。
     *
     * @param fileName 文件名
     * @param content 正文
     * @return 标题
     */
    private String inferTitle(String fileName, String content) {
        for (String line : content.split("\\R")) {
            String trimmedLine = line.trim();
            if (trimmedLine.startsWith("# ")) {
                return trimmedLine.substring(2).trim();
            }
        }
        return stripMarkdownExtension(fileName);
    }

    /**
     * 标准化标题。
     *
     * @param title 原始标题
     * @return 标准标题
     */
    private String normalizeTitle(String title) {
        String normalizedTitle = title == null ? "" : title.trim();
        if (normalizedTitle.isBlank()) {
            normalizedTitle = "未命名笔记";
        }
        return normalizedTitle.length() > MAX_TITLE_LENGTH
                ? normalizedTitle.substring(0, MAX_TITLE_LENGTH)
                : normalizedTitle;
    }

    /**
     * 解析或创建分类。
     *
     * @param categoryName 分类名称
     * @return 分类ID
     */
    private Long resolveCategoryId(String categoryName) {
        if (categoryName == null || categoryName.isBlank()) {
            return null;
        }
        String normalizedName = categoryName.trim();
        Category category = categoryRepository.findByName(normalizedName)
                .orElseGet(() -> categoryRepository.save(new Category(normalizedName, null)));
        return category.getId();
    }

    /**
     * 查询未删除笔记。
     *
     * @param noteId 笔记ID
     * @return 笔记实体
     */
    private Note findExistingNote(Long noteId) {
        Note note = noteRepository.findById(noteId)
                .orElseThrow(() -> new ResourceNotFoundException("笔记不存在：" + noteId));
        if (note.isDeleted()) {
            throw new ResourceNotFoundException("笔记已删除，不能导出：" + noteId);
        }
        return note;
    }

    /**
     * 构建 Markdown 导出文件。
     *
     * @param note 笔记实体
     * @return 导出文件
     */
    private ExportedFile buildMarkdownFile(Note note) {
        String markdown = buildFrontMatter(note) + "\n" + note.getContent().strip() + "\n";
        return new ExportedFile(safeMarkdownFileName(note), "text/markdown; charset=UTF-8",
                markdown.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * 构建导出 front matter。
     *
     * @param note 笔记实体
     * @return front matter 文本
     */
    private String buildFrontMatter(Note note) {
        StringBuilder builder = new StringBuilder("---\n");
        appendYamlValue(builder, "title", note.getTitle());
        appendYamlValue(builder, "type", note.getType().name());
        appendYamlValue(builder, "language", note.getLanguage());
        if (note.getCategory() != null) {
            appendYamlValue(builder, "category", note.getCategory().getName());
        }
        if (!note.getTags().isEmpty()) {
            builder.append("tags:\n");
            note.getTags().stream()
                    .map(Tag::getName)
                    .sorted()
                    .forEach(tagName -> builder.append("  - ").append(quoteYaml(tagName)).append('\n'));
        }
        builder.append("favorite: ").append(note.isFavorite()).append('\n');
        builder.append("pinned: ").append(note.isPinned()).append('\n');
        builder.append("---\n");
        return builder.toString();
    }

    /**
     * 追加 YAML 字符串值。
     *
     * @param builder 字符串构造器
     * @param key 键
     * @param value 值
     */
    private void appendYamlValue(StringBuilder builder, String key, String value) {
        if (value != null && !value.isBlank()) {
            builder.append(key).append(": ").append(quoteYaml(value)).append('\n');
        }
    }

    /**
     * YAML 字符串转义。
     *
     * @param value 原始值
     * @return 转义后的值
     */
    private String quoteYaml(String value) {
        return "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }

    /**
     * 构建安全 Markdown 文件名。
     *
     * @param note 笔记实体
     * @return 文件名
     */
    private String safeMarkdownFileName(Note note) {
        String baseName = sanitizeFileName(note.getTitle());
        if (baseName.isBlank()) {
            baseName = "note-" + note.getId();
        }
        return baseName + "-" + note.getId() + ".md";
    }

    /**
     * 生成 ZIP 内唯一文件名。
     *
     * @param fileName 原始文件名
     * @param usedNames 已用文件名计数
     * @return 唯一文件名
     */
    private String uniqueEntryName(String fileName, Map<String, Integer> usedNames) {
        int count = usedNames.getOrDefault(fileName, 0);
        usedNames.put(fileName, count + 1);
        if (count == 0) {
            return fileName;
        }
        return fileName.replaceFirst("\\.md$", "-" + count + ".md");
    }

    /**
     * 读取上传文件字节。
     *
     * @param file 上传文件
     * @return 文件字节
     */
    private byte[] readMultipartBytes(MultipartFile file) {
        try {
            return file.getBytes();
        } catch (IOException ex) {
            throw new BusinessException("读取上传文件失败：" + ex.getMessage());
        }
    }

    /**
     * 获取安全原始文件名。
     *
     * @param originalFileName 原始文件名
     * @return 安全文件名
     */
    private String safeOriginalFileName(String originalFileName) {
        if (originalFileName == null || originalFileName.isBlank()) {
            return "unknown.md";
        }
        return originalFileName.replace("\\", "/").replaceAll("^.*/", "");
    }

    /**
     * 移除 Markdown 文件扩展名。
     *
     * @param fileName 文件名
     * @return 基础名
     */
    private String stripMarkdownExtension(String fileName) {
        return safeOriginalFileName(fileName).replaceFirst("(?i)\\.md$", "");
    }

    /**
     * 清理文件名特殊字符。
     *
     * @param value 原始文件名
     * @return 安全文件名
     */
    private String sanitizeFileName(String value) {
        return value == null ? "" : value.trim().replaceAll("[\\\\/:*?\"<>|\\s]+", "-").replaceAll("^-+|-+$", "");
    }

    /**
     * 创建失败结果。
     *
     * @param fileName 文件名
     * @param message 失败消息
     * @return 导入失败结果
     */
    private MarkdownImportItemResponse failed(String fileName, String message) {
        return new MarkdownImportItemResponse(fileName, null, null, false, message);
    }

    /**
     * 导出的文件内容。
     *
     * @param fileName 文件名
     * @param contentType 内容类型
     * @param content 文件字节
     */
    public record ExportedFile(String fileName, String contentType, byte[] content) {
    }

    /**
     * Markdown 文档解析结果。
     *
     * @param title 标题
     * @param content 正文
     * @param tags 标签
     * @param categoryName 分类名称
     * @param language 代码语言
     */
    private record MarkdownDocument(String title, String content, Set<String> tags, String categoryName, String language) {
    }

    /**
     * front matter 分离结果。
     *
     * @param frontMatter YAML 元数据
     * @param content 正文
     */
    private record FrontMatterSplit(String frontMatter, String content) {
    }
}
