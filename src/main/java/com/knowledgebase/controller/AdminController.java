package com.knowledgebase.controller;

import com.knowledgebase.KnowledgeBaseApplication;
import com.knowledgebase.config.KnowledgeBaseProperties;
import com.knowledgebase.dto.AdminReindexResponse;
import com.knowledgebase.dto.AdminVectorIndexInfoResponse;
import com.knowledgebase.dto.AdminVectorReindexResponse;
import com.knowledgebase.dto.AdminWorkspaceInfoResponse;
import com.knowledgebase.dto.ApiResponse;
import com.knowledgebase.dto.EmbeddingProviderResponse;
import com.knowledgebase.service.BackupService;
import com.knowledgebase.service.IndexService;
import com.knowledgebase.service.VectorIndexService;
import java.nio.file.Paths;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 管理接口控制器。
 */
@RestController
@RequestMapping("/api/admin")
public class AdminController {

    private final IndexService indexService;
    private final VectorIndexService vectorIndexService;
    private final BackupService backupService;
    private final KnowledgeBaseProperties properties;

    /**
     * 创建管理控制器。
     *
     * @param indexService 索引服务
     * @param vectorIndexService 向量索引服务
     * @param backupService 备份服务
     * @param properties 知识库配置
     */
    public AdminController(
            IndexService indexService,
            VectorIndexService vectorIndexService,
            BackupService backupService,
            KnowledgeBaseProperties properties
    ) {
        this.indexService = indexService;
        this.vectorIndexService = vectorIndexService;
        this.backupService = backupService;
        this.properties = properties;
    }

    /**
     * 获取工作区维护信息。
     *
     * @return 工作区维护信息
     */
    @GetMapping("/workspace")
    public ApiResponse<AdminWorkspaceInfoResponse> workspaceInfo() {
        return ApiResponse.success(new AdminWorkspaceInfoResponse(
                normalizePath(properties.getDataPath()),
                normalizePath(properties.getIndexPath()),
                normalizePath(properties.getVectorIndexPath()),
                normalizePath(properties.getImagesPath()),
                properties.getHistoryMaxVersions(),
                applicationVersion()
        ));
    }

    /**
     * 获取 Embedding 供应商配置状态。
     *
     * @return Embedding 供应商配置状态
     */
    @GetMapping("/embedding-provider")
    public ApiResponse<EmbeddingProviderResponse> embeddingProvider() {
        return ApiResponse.success(vectorIndexService.providerInfo());
    }

    /**
     * 获取向量索引维护信息。
     *
     * @return 向量索引维护信息
     */
    @GetMapping("/vector-index")
    public ApiResponse<AdminVectorIndexInfoResponse> vectorIndexInfo() {
        return ApiResponse.success(vectorIndexService.info());
    }

    /**
     * 手动重建向量索引。
     *
     * @return 重建结果
     */
    @PostMapping("/vector-index/rebuild")
    public ApiResponse<AdminVectorReindexResponse> rebuildVectorIndex() {
        return ApiResponse.success("向量索引重建成功", vectorIndexService.rebuild());
    }

    /**
     * 手动重建全文索引。
     *
     * @return 重建结果
     */
    @PostMapping("/reindex")
    public ApiResponse<AdminReindexResponse> reindex() {
        int indexedCount = indexService.rebuild();
        return ApiResponse.success(
                "索引重建成功",
                new AdminReindexResponse(indexedCount, indexService.getIndexPath().toString())
        );
    }

    /**
     * 下载知识库完整备份。
     *
     * @return 备份 ZIP 下载响应
     */
    @GetMapping("/backup")
    public ResponseEntity<byte[]> backup() {
        BackupService.ExportedBackup backup = backupService.createBackup();
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename(backup.fileName(), java.nio.charset.StandardCharsets.UTF_8)
                        .build()
                        .toString())
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .body(backup.content());
    }

    /**
     * 标准化路径显示。
     *
     * @param path 原始路径
     * @return 绝对路径
     */
    private String normalizePath(String path) {
        return Paths.get(path).toAbsolutePath().normalize().toString();
    }

    /**
     * 获取应用版本。
     *
     * @return 应用版本
     */
    private String applicationVersion() {
        String version = KnowledgeBaseApplication.class.getPackage().getImplementationVersion();
        return version == null || version.isBlank() ? "dev" : version;
    }
}
