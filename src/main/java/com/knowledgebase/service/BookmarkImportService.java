package com.knowledgebase.service;

import com.knowledgebase.dto.BookmarkImportItemResponse;
import com.knowledgebase.dto.BookmarkImportResponse;
import com.knowledgebase.dto.NoteRequest;
import com.knowledgebase.entity.Category;
import com.knowledgebase.entity.NoteStatus;
import com.knowledgebase.entity.NoteType;
import com.knowledgebase.exception.BusinessException;
import com.knowledgebase.repository.CategoryRepository;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Queue;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.util.HtmlUtils;

/**
 * 浏览器书签导入服务。
 */
@Service
public class BookmarkImportService {

    private static final Pattern HEADING_PATTERN = Pattern.compile("(?is)<H3\\b[^>]*>(.*?)</H3>");
    private static final Pattern BOOKMARK_PATTERN = Pattern.compile("(?is)<A\\b([^>]*)>(.*?)</A>");
    private static final Pattern HREF_PATTERN = Pattern.compile("(?is)\\bHREF\\s*=\\s*([\"'])(.*?)\\1");
    private static final Pattern ADD_DATE_PATTERN = Pattern.compile("(?is)\\bADD_DATE\\s*=\\s*([\"']?)(\\d+)\\1");
    private static final int MAX_TITLE_LENGTH = 160;

    private final NoteService noteService;
    private final CategoryRepository categoryRepository;

    /**
     * 创建浏览器书签导入服务。
     *
     * @param noteService 笔记服务
     * @param categoryRepository 分类仓库
     */
    public BookmarkImportService(NoteService noteService, CategoryRepository categoryRepository) {
        this.noteService = noteService;
        this.categoryRepository = categoryRepository;
    }

    /**
     * 导入浏览器导出的书签 HTML。
     *
     * @param file 书签 HTML 文件
     * @return 导入结果
     */
    @Transactional
    public BookmarkImportResponse importBookmarks(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException("书签文件不能为空");
        }
        String content = readFileContent(file);
        List<BookmarkCandidate> candidates = parseBookmarks(content);
        List<BookmarkImportItemResponse> items = new ArrayList<>();
        for (BookmarkCandidate candidate : candidates) {
            items.add(importBookmark(candidate));
        }
        int importedCount = (int) items.stream().filter(BookmarkImportItemResponse::success).count();
        return new BookmarkImportResponse(importedCount, items.size() - importedCount, items);
    }

    /**
     * 解析 Netscape Bookmark HTML。
     *
     * @param content HTML 内容
     * @return 书签候选列表
     */
    private List<BookmarkCandidate> parseBookmarks(String content) {
        List<BookmarkCandidate> candidates = new ArrayList<>();
        Queue<String> pendingFolders = new ArrayDeque<>();
        String[] lines = content.split("\\R");
        String currentFolder = "浏览器书签";
        for (String line : lines) {
            Matcher headingMatcher = HEADING_PATTERN.matcher(line);
            if (headingMatcher.find()) {
                pendingFolders.add(cleanHtmlText(headingMatcher.group(1)));
                continue;
            }
            if (line.toUpperCase(Locale.ROOT).contains("<DL")) {
                String pendingFolder = pendingFolders.poll();
                if (pendingFolder != null && !pendingFolder.isBlank()) {
                    currentFolder = pendingFolder;
                }
                continue;
            }
            Matcher bookmarkMatcher = BOOKMARK_PATTERN.matcher(line);
            if (bookmarkMatcher.find()) {
                String attributes = bookmarkMatcher.group(1);
                String title = normalizeTitle(cleanHtmlText(bookmarkMatcher.group(2)));
                String url = findAttribute(attributes, HREF_PATTERN);
                if (!url.isBlank()) {
                    candidates.add(new BookmarkCandidate(title, url, currentFolder, findAttribute(attributes, ADD_DATE_PATTERN)));
                }
            }
        }
        return candidates;
    }

    /**
     * 导入单条书签。
     *
     * @param candidate 书签候选
     * @return 导入结果
     */
    private BookmarkImportItemResponse importBookmark(BookmarkCandidate candidate) {
        try {
            Long categoryId = resolveCategoryId(candidate.folderName());
            Set<String> tags = new LinkedHashSet<>();
            tags.add("bookmark");
            NoteRequest request = new NoteRequest(
                    candidate.title(),
                    buildBookmarkContent(candidate),
                    null,
                    NoteType.MARKDOWN,
                    NoteStatus.PUBLISHED,
                    null,
                    null,
                    categoryId,
                    tags,
                    false,
                    false
            );
            var note = noteService.create(request);
            return new BookmarkImportItemResponse(candidate.title(), candidate.url(), note.id(), true, "导入成功");
        } catch (RuntimeException ex) {
            return new BookmarkImportItemResponse(candidate.title(), candidate.url(), null, false, ex.getMessage());
        }
    }

    /**
     * 构建书签笔记正文。
     *
     * @param candidate 书签候选
     * @return Markdown 正文
     */
    private String buildBookmarkContent(BookmarkCandidate candidate) {
        StringBuilder builder = new StringBuilder();
        builder.append("# ").append(candidate.title()).append("\n\n");
        builder.append("- 链接：[").append(candidate.title()).append("](").append(candidate.url()).append(")\n");
        builder.append("- 来源：浏览器书签\n");
        if (!candidate.addDate().isBlank()) {
            builder.append("- 原始创建时间戳：").append(candidate.addDate()).append("\n");
        }
        return builder.toString();
    }

    /**
     * 解析或创建分类。
     *
     * @param categoryName 分类名称
     * @return 分类ID
     */
    private Long resolveCategoryId(String categoryName) {
        String normalizedName = categoryName == null || categoryName.isBlank() ? "浏览器书签" : categoryName.trim();
        Category category = categoryRepository.findByName(normalizedName)
                .orElseGet(() -> categoryRepository.save(new Category(normalizedName, null)));
        return category.getId();
    }

    /**
     * 查找 HTML 属性。
     *
     * @param attributes 属性文本
     * @param pattern 正则模式
     * @return 属性值
     */
    private String findAttribute(String attributes, Pattern pattern) {
        Matcher matcher = pattern.matcher(attributes);
        if (!matcher.find()) {
            return "";
        }
        return HtmlUtils.htmlUnescape(matcher.group(2)).trim();
    }

    /**
     * 清理 HTML 文本。
     *
     * @param value 原始文本
     * @return 清理后的文本
     */
    private String cleanHtmlText(String value) {
        return HtmlUtils.htmlUnescape(value.replaceAll("(?is)<[^>]+>", "")).trim();
    }

    /**
     * 标准化标题。
     *
     * @param title 原始标题
     * @return 标准标题
     */
    private String normalizeTitle(String title) {
        String normalizedTitle = title == null || title.isBlank() ? "未命名书签" : title.trim();
        return normalizedTitle.length() > MAX_TITLE_LENGTH
                ? normalizedTitle.substring(0, MAX_TITLE_LENGTH)
                : normalizedTitle;
    }

    /**
     * 读取上传文件内容。
     *
     * @param file 上传文件
     * @return 文本内容
     */
    private String readFileContent(MultipartFile file) {
        try {
            return new String(file.getBytes(), StandardCharsets.UTF_8);
        } catch (IOException ex) {
            throw new BusinessException("读取书签文件失败：" + ex.getMessage());
        }
    }

    /**
     * 浏览器书签候选。
     *
     * @param title 标题
     * @param url 链接
     * @param folderName 文件夹名称
     * @param addDate 原始创建时间戳
     */
    private record BookmarkCandidate(String title, String url, String folderName, String addDate) {
    }
}
