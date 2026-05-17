package com.knowledgebase.controller;

import com.knowledgebase.dto.ApiResponse;
import com.knowledgebase.dto.NoteKindRequest;
import com.knowledgebase.dto.NoteKindResponse;
import com.knowledgebase.service.NoteKindService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * 笔记用途接口控制器。
 */
@RestController
@RequestMapping("/api/note-kinds")
public class NoteKindController {

    private final NoteKindService noteKindService;

    /**
     * 创建笔记用途控制器。
     *
     * @param noteKindService 笔记用途服务
     */
    public NoteKindController(NoteKindService noteKindService) {
        this.noteKindService = noteKindService;
    }

    /**
     * 查询全部用途。
     *
     * @return 用途列表
     */
    @GetMapping
    public ApiResponse<List<NoteKindResponse>> findAll() {
        return ApiResponse.success(noteKindService.findAll());
    }

    /**
     * 创建用途。
     *
     * @param request 用途请求
     * @return 用途响应
     */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<NoteKindResponse> create(@Valid @RequestBody NoteKindRequest request) {
        return ApiResponse.success("用途创建成功", noteKindService.create(request));
    }

    /**
     * 更新用途。
     *
     * @param id 用途ID
     * @param request 用途请求
     * @return 用途响应
     */
    @PutMapping("/{id}")
    public ApiResponse<NoteKindResponse> update(
            @PathVariable Long id,
            @Valid @RequestBody NoteKindRequest request
    ) {
        return ApiResponse.success("用途更新成功", noteKindService.update(id, request));
    }

    /**
     * 删除用途。
     *
     * @param id 用途ID
     * @return 空响应
     */
    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        noteKindService.delete(id);
        return ApiResponse.success("用途删除成功", null);
    }
}
