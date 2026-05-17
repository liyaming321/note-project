package com.knowledgebase.controller;

import com.knowledgebase.KnowledgeBaseApplication;
import com.knowledgebase.config.KnowledgeBaseProperties;
import com.knowledgebase.dto.AdminBackupInfoResponse;
import com.knowledgebase.dto.AdminConfigurationChecklistResponse;
import com.knowledgebase.dto.AdminReindexResponse;
import com.knowledgebase.dto.AdminIndexHealthResponse;
import com.knowledgebase.dto.AdminVectorIndexInfoResponse;
import com.knowledgebase.dto.AdminVectorCleanupResponse;
import com.knowledgebase.dto.AdminVectorReindexResponse;
import com.knowledgebase.dto.AdminWorkspaceInfoResponse;
import com.knowledgebase.dto.ApiResponse;
import com.knowledgebase.dto.EmbeddingProviderResponse;
import com.knowledgebase.dto.KnowledgeOrganizeCandidateResponse;
import com.knowledgebase.dto.LlmProviderTestResponse;
import com.knowledgebase.dto.OrganizeApplyRequest;
import com.knowledgebase.dto.OrganizeApplyResponse;
import com.knowledgebase.dto.PageResponse;
import com.knowledgebase.dto.SearchFeedbackSummaryResponse;
import com.knowledgebase.dto.SearchTuningSettingsRequest;
import com.knowledgebase.dto.SearchTuningSettingsResponse;
import com.knowledgebase.service.BackupService;
import com.knowledgebase.service.ConfigurationCenterService;
import com.knowledgebase.service.IndexMaintenanceService;
import com.knowledgebase.service.IndexService;
import com.knowledgebase.service.KnowledgeOrganizeService;
import com.knowledgebase.service.LlmChatService;
import com.knowledgebase.service.SearchTuningService;
import com.knowledgebase.service.VectorIndexService;
import jakarta.validation.Valid;
import java.nio.file.Paths;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 管理接口控制器。
 */
@RestController
@RequestMapping("/api/admin")
public class AdminController {

    private final IndexService indexService;
    private final VectorIndexService vectorIndexService;
    private final IndexMaintenanceService indexMaintenanceService;
    private final BackupService backupService;
    private final KnowledgeBaseProperties properties;
    private final SearchTuningService searchTuningService;
    private final KnowledgeOrganizeService knowledgeOrganizeService;
    private final ConfigurationCenterService configurationCenterService;
    private final LlmChatService llmChatService;

    /**
     * 创建管理控制器。
     *
     * @param indexService 索引服务
     * @param vectorIndexService 向量索引服务
     * @param indexMaintenanceService 索引运维服务
     * @param backupService 备份服务
     * @param properties 知识库配置
     * @param searchTuningService 搜索调优服务
     * @param knowledgeOrganizeService 知识整理服务
     * @param configurationCenterService 配置中心服务
     * @param llmChatService LLM 对话服务
     */
    public AdminController(
            IndexService indexService,
            VectorIndexService vectorIndexService,
            IndexMaintenanceService indexMaintenanceService,
            BackupService backupService,
            KnowledgeBaseProperties properties,
            SearchTuningService searchTuningService,
            KnowledgeOrganizeService knowledgeOrganizeService,
            ConfigurationCenterService configurationCenterService,
            LlmChatService llmChatService
    ) {
        this.indexService = indexService;
        this.vectorIndexService = vectorIndexService;
        this.indexMaintenanceService = indexMaintenanceService;
        this.backupService = backupService;
        this.properties = properties;
        this.searchTuningService = searchTuningService;
        this.knowledgeOrganizeService = knowledgeOrganizeService;
        this.configurationCenterService = configurationCenterService;
        this.llmChatService = llmChatService;
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
     * 获取配置中心检查清单。
     *
     * @return 配置中心检查清单
     */
    @GetMapping("/configuration-checklist")
    public ApiResponse<AdminConfigurationChecklistResponse> configurationChecklist() {
        return ApiResponse.success(configurationCenterService.checklist());
    }

    /**
     * 测试 LLM 供应商连接。
     *
     * @param provider 供应商
     * @return 测试结果
     */
    @PostMapping("/llm-providers/{provider}/test")
    public ApiResponse<LlmProviderTestResponse> testLlmProvider(@PathVariable String provider) {
        return ApiResponse.success(llmChatService.testConnection(provider));
    }

    /**
     * 获取搜索调优设置。
     *
     * @return 搜索调优设置
     */
    @GetMapping("/search-tuning")
    public ApiResponse<SearchTuningSettingsResponse> searchTuning() {
        return ApiResponse.success(searchTuningService.currentSettings());
    }

    /**
     * 更新搜索调优设置。
     *
     * @param request 设置请求
     * @return 更新后的设置
     */
    @PutMapping("/search-tuning")
    public ApiResponse<SearchTuningSettingsResponse> updateSearchTuning(
            @Valid @RequestBody SearchTuningSettingsRequest request
    ) {
        return ApiResponse.success("搜索调优配置已保存", searchTuningService.updateSettings(request));
    }

    /**
     * 获取搜索反馈汇总。
     *
     * @return 搜索反馈汇总
     */
    @GetMapping("/search-feedback-summary")
    public ApiResponse<SearchFeedbackSummaryResponse> searchFeedbackSummary() {
        return ApiResponse.success(searchTuningService.feedbackSummary());
    }

    /**
     * 查询待整理候选笔记。
     *
     * @param page 页码
     * @param size 每页数量
     * @return 待整理候选分页
     */
    @GetMapping("/organize-candidates")
    public ApiResponse<PageResponse<KnowledgeOrganizeCandidateResponse>> organizeCandidates(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return ApiResponse.success(knowledgeOrganizeService.candidates(page, size));
    }

    /**
     * 应用人工确认后的整理结果。
     *
     * @param request 整理应用请求
     * @return 应用结果
     */
    @PostMapping("/organize-candidates/apply")
    public ApiResponse<OrganizeApplyResponse> applyOrganizeCandidates(
            @Valid @RequestBody OrganizeApplyRequest request
    ) {
        return ApiResponse.success("整理结果已应用", knowledgeOrganizeService.apply(request));
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
     * 检查全文和向量索引健康状态。
     *
     * @return 索引健康状态
     */
    @GetMapping("/index-health")
    public ApiResponse<AdminIndexHealthResponse> indexHealth() {
        return ApiResponse.success(indexMaintenanceService.health());
    }

    /**
     * 清理无效向量索引。
     *
     * @return 清理结果
     */
    @PostMapping("/vector-index/cleanup")
    public ApiResponse<AdminVectorCleanupResponse> cleanupVectorIndex() {
        return ApiResponse.success("无效向量清理成功", indexMaintenanceService.cleanupInvalidVectors());
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
     * 获取备份健康信息。
     *
     * @return 备份健康信息
     */
    @GetMapping("/backup-info")
    public ApiResponse<AdminBackupInfoResponse> backupInfo() {
        return ApiResponse.success(backupService.info());
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
