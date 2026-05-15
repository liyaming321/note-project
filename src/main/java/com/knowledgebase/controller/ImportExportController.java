package com.knowledgebase.controller;

import com.knowledgebase.dto.ApiResponse;
import com.knowledgebase.dto.BookmarkImportResponse;
import com.knowledgebase.dto.ExportZipRequest;
import com.knowledgebase.dto.MarkdownImportResponse;
import com.knowledgebase.service.BookmarkImportService;
import com.knowledgebase.service.ImportExportService;
import jakarta.validation.Valid;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * Markdown 导入导出控制器。
 */
@RestController
public class ImportExportController {

    private final ImportExportService importExportService;
    private final BookmarkImportService bookmarkImportService;

    /**
     * 创建 Markdown 导入导出控制器。
     *
     * @param importExportService Markdown 导入导出服务
     * @param bookmarkImportService 浏览器书签导入服务
     */
    public ImportExportController(
            ImportExportService importExportService,
            BookmarkImportService bookmarkImportService
    ) {
        this.importExportService = importExportService;
        this.bookmarkImportService = bookmarkImportService;
    }

    /**
     * 上传并导入 Markdown 文件或 ZIP 压缩包。
     *
     * @param files 上传文件
     * @return 导入结果
     */
    @PostMapping(value = "/api/import/markdown", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<MarkdownImportResponse> importMarkdown(@RequestPart("files") MultipartFile[] files) {
        return ApiResponse.success("Markdown 导入完成", importExportService.importMarkdown(files));
    }

    /**
     * 上传并导入浏览器书签 HTML。
     *
     * @param file 书签 HTML 文件
     * @return 导入结果
     */
    @PostMapping(value = "/api/import/bookmarks", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<BookmarkImportResponse> importBookmarks(@RequestPart("file") MultipartFile file) {
        return ApiResponse.success("书签导入完成", bookmarkImportService.importBookmarks(file));
    }

    /**
     * 导出单篇笔记 Markdown。
     *
     * @param id 笔记ID
     * @return Markdown 文件下载响应
     */
    @GetMapping("/api/notes/{id}/export/markdown")
    public ResponseEntity<byte[]> exportMarkdown(@PathVariable Long id) {
        return toDownloadResponse(importExportService.exportMarkdown(id));
    }

    /**
     * 批量导出 Markdown ZIP。
     *
     * @param request 导出请求
     * @return ZIP 文件下载响应
     */
    @PostMapping("/api/export/zip")
    public ResponseEntity<byte[]> exportZip(@Valid @RequestBody ExportZipRequest request) {
        return toDownloadResponse(importExportService.exportZip(request));
    }

    /**
     * 构建下载响应。
     *
     * @param file 导出文件
     * @return 下载响应
     */
    private ResponseEntity<byte[]> toDownloadResponse(ImportExportService.ExportedFile file) {
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename(file.fileName(), java.nio.charset.StandardCharsets.UTF_8)
                        .build()
                        .toString())
                .contentType(MediaType.parseMediaType(file.contentType()))
                .body(file.content());
    }
}
