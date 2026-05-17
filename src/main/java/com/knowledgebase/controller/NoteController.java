package com.knowledgebase.controller;

import com.knowledgebase.dto.ApiResponse;
import com.knowledgebase.dto.LlmProviderResponse;
import com.knowledgebase.dto.LlmSummaryRequest;
import com.knowledgebase.dto.LlmSummaryResponse;
import com.knowledgebase.dto.NoteBatchRequest;
import com.knowledgebase.dto.NoteDetailResponse;
import com.knowledgebase.dto.NoteHistoryDetailResponse;
import com.knowledgebase.dto.NoteHistorySummaryResponse;
import com.knowledgebase.dto.NoteListResponse;
import com.knowledgebase.dto.NotePublishStatusRequest;
import com.knowledgebase.dto.NoteReorderRequest;
import com.knowledgebase.dto.NoteRequest;
import com.knowledgebase.dto.NoteStatusRequest;
import com.knowledgebase.dto.PageResponse;
import com.knowledgebase.entity.NoteStatus;
import com.knowledgebase.entity.NoteType;
import com.knowledgebase.service.NoteService;
import com.knowledgebase.service.LlmSummaryService;
import jakarta.validation.Valid;
import java.time.LocalDate;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * 笔记接口控制器。
 */
@RestController
@RequestMapping("/api/notes")
public class NoteController {

    private final NoteService noteService;
    private final LlmSummaryService llmSummaryService;

    /**
     * 创建笔记控制器。
     *
     * @param noteService 笔记服务
     * @param llmSummaryService LLM 总结服务
     */
    public NoteController(NoteService noteService, LlmSummaryService llmSummaryService) {
        this.noteService = noteService;
        this.llmSummaryService = llmSummaryService;
    }

    /**
     * 获取 LLM 供应商配置状态。
     *
     * @return LLM 供应商配置状态
     */
    @GetMapping("/llm-providers")
    public ApiResponse<List<LlmProviderResponse>> llmProviders() {
        return ApiResponse.success(llmSummaryService.providers());
    }

    /**
     * 生成笔记 LLM 总结建议。
     *
     * @param request 总结请求
     * @return 总结建议
     */
    @PostMapping("/llm-summary")
    public ApiResponse<LlmSummaryResponse> summarize(@Valid @RequestBody LlmSummaryRequest request) {
        return ApiResponse.success("总结生成成功", llmSummaryService.summarize(request));
    }

    /**
     * 创建笔记。
     *
     * @param request 笔记请求
     * @return 笔记详情
     */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<NoteDetailResponse> create(@Valid @RequestBody NoteRequest request) {
        return ApiResponse.success("笔记创建成功", noteService.create(request));
    }

    /**
     * 获取笔记详情。
     *
     * @param id 笔记ID
     * @return 笔记详情
     */
    @GetMapping("/{id}")
    public ApiResponse<NoteDetailResponse> findById(@PathVariable Long id) {
        return ApiResponse.success(noteService.findById(id));
    }

    /**
     * 更新笔记。
     *
     * @param id 笔记ID
     * @param request 笔记请求
     * @return 笔记详情
     */
    @PutMapping("/{id}")
    public ApiResponse<NoteDetailResponse> update(@PathVariable Long id, @Valid @RequestBody NoteRequest request) {
        return ApiResponse.success("笔记更新成功", noteService.update(id, request));
    }

    /**
     * 逻辑删除笔记。
     *
     * @param id 笔记ID
     * @return 空响应
     */
    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        noteService.delete(id);
        return ApiResponse.success("笔记删除成功", null);
    }

    /**
     * 恢复已删除笔记。
     *
     * @param id 笔记ID
     * @return 笔记详情
     */
    @PostMapping("/{id}/restore")
    public ApiResponse<NoteDetailResponse> restore(@PathVariable Long id) {
        return ApiResponse.success("笔记恢复成功", noteService.restore(id));
    }

    /**
     * 获取笔记历史版本列表。
     *
     * @param id 笔记ID
     * @return 历史版本列表
     */
    @GetMapping("/{id}/history")
    public ApiResponse<List<NoteHistorySummaryResponse>> findHistory(@PathVariable Long id) {
        return ApiResponse.success(noteService.findHistory(id));
    }

    /**
     * 获取指定历史版本详情。
     *
     * @param id 笔记ID
     * @param version 版本号
     * @return 历史版本详情
     */
    @GetMapping("/{id}/history/{version}")
    public ApiResponse<NoteHistoryDetailResponse> findHistoryDetail(
            @PathVariable Long id,
            @PathVariable int version
    ) {
        return ApiResponse.success(noteService.findHistoryDetail(id, version));
    }

    /**
     * 恢复到指定历史版本。
     *
     * @param id 笔记ID
     * @param version 版本号
     * @return 恢复后的笔记详情
     */
    @PostMapping("/{id}/revert/{version}")
    public ApiResponse<NoteDetailResponse> revert(
            @PathVariable Long id,
            @PathVariable int version
    ) {
        return ApiResponse.success("历史版本恢复成功", noteService.revertToVersion(id, version));
    }

    /**
     * 更新收藏状态。
     *
     * @param id 笔记ID
     * @param request 状态请求
     * @return 笔记详情
     */
    @PatchMapping("/{id}/favorite")
    public ApiResponse<NoteDetailResponse> changeFavorite(
            @PathVariable Long id,
            @Valid @RequestBody NoteStatusRequest request
    ) {
        return ApiResponse.success("收藏状态更新成功", noteService.changeFavorite(id, request.value()));
    }

    /**
     * 更新置顶状态。
     *
     * @param id 笔记ID
     * @param request 状态请求
     * @return 笔记详情
     */
    @PatchMapping("/{id}/pinned")
    public ApiResponse<NoteDetailResponse> changePinned(
            @PathVariable Long id,
            @Valid @RequestBody NoteStatusRequest request
    ) {
        return ApiResponse.success("置顶状态更新成功", noteService.changePinned(id, request.value()));
    }

    /**
     * 更新发布状态。
     *
     * @param id 笔记ID
     * @param request 发布状态请求
     * @return 笔记详情
     */
    @PatchMapping("/{id}/status")
    public ApiResponse<NoteDetailResponse> changeStatus(
            @PathVariable Long id,
            @Valid @RequestBody NotePublishStatusRequest request
    ) {
        return ApiResponse.success("发布状态更新成功", noteService.changeStatus(id, request.status()));
    }

    /**
     * 更新归档状态。
     *
     * @param id 笔记ID
     * @param request 状态请求
     * @return 笔记详情
     */
    @PatchMapping("/{id}/archived")
    public ApiResponse<NoteDetailResponse> changeArchived(
            @PathVariable Long id,
            @Valid @RequestBody NoteStatusRequest request
    ) {
        return ApiResponse.success("归档状态更新成功", noteService.changeArchived(id, request.value()));
    }

    /**
     * 永久删除笔记。
     *
     * @param id 笔记ID
     * @return 空响应
     */
    @DeleteMapping("/{id}/permanent")
    public ApiResponse<Void> permanentlyDelete(@PathVariable Long id) {
        noteService.permanentlyDelete(id);
        return ApiResponse.success("笔记已永久删除", null);
    }

    /**
     * 批量恢复笔记。
     *
     * @param request 批量请求
     * @return 恢复后的笔记列表
     */
    @PostMapping("/batch/restore")
    public ApiResponse<List<NoteListResponse>> batchRestore(@Valid @RequestBody NoteBatchRequest request) {
        return ApiResponse.success("笔记批量恢复成功", noteService.batchRestore(request));
    }

    /**
     * 更新笔记自定义排序。
     *
     * @param request 排序请求
     * @return 更新后的笔记列表
     */
    @PostMapping("/reorder")
    public ApiResponse<List<NoteListResponse>> reorder(@Valid @RequestBody NoteReorderRequest request) {
        return ApiResponse.success("笔记排序更新成功", noteService.reorder(request));
    }

    /**
     * 分页查询笔记。
     *
     * @param categoryId 分类ID
     * @param tag 标签名称
     * @param type 内容格式
     * @param noteKindId 笔记用途类型ID
     * @param status 发布状态
     * @param favorite 是否收藏
     * @param pinned 是否置顶
     * @param archived 是否归档
     * @param includeDeleted 是否包含已删除
     * @param onlyDeleted 是否仅查询已删除
     * @param updatedFrom 更新时间开始日期
     * @param updatedTo 更新时间结束日期
     * @param page 页码
     * @param size 每页数量
     * @param sort 排序字段
     * @param direction 排序方向
     * @return 分页笔记列表
     */
    @GetMapping
    public ApiResponse<PageResponse<NoteListResponse>> findPage(
            @RequestParam(required = false) Long categoryId,
            @RequestParam(required = false) String tag,
            @RequestParam(required = false) NoteType type,
            @RequestParam(required = false) Long noteKindId,
            @RequestParam(required = false) NoteStatus status,
            @RequestParam(required = false) Boolean favorite,
            @RequestParam(required = false) Boolean pinned,
            @RequestParam(required = false) Boolean archived,
            @RequestParam(defaultValue = "false") boolean includeDeleted,
            @RequestParam(defaultValue = "false") boolean onlyDeleted,
            @RequestParam(required = false) LocalDate updatedFrom,
            @RequestParam(required = false) LocalDate updatedTo,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "updatedAt") String sort,
            @RequestParam(defaultValue = "desc") String direction
    ) {
        return ApiResponse.success(noteService.findPage(
                categoryId,
                tag,
                type,
                noteKindId,
                status,
                favorite,
                pinned,
                archived,
                includeDeleted,
                onlyDeleted,
                updatedFrom,
                updatedTo,
                page,
                size,
                sort,
                direction
        ));
    }
}
