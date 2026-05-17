package com.knowledgebase.controller;

import com.knowledgebase.dto.ApiResponse;
import com.knowledgebase.dto.TagRequest;
import com.knowledgebase.dto.TagResponse;
import com.knowledgebase.service.TagService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * 标签接口控制器。
 */
@RestController
@RequestMapping("/api/tags")
public class TagController {

    private final TagService tagService;

    /**
     * 创建标签控制器。
     *
     * @param tagService 标签服务
     */
    public TagController(TagService tagService) {
        this.tagService = tagService;
    }

    /**
     * 查询所有标签。
     *
     * @return 标签列表
     */
    @GetMapping
    public ApiResponse<List<TagResponse>> findAll() {
        return ApiResponse.success(tagService.findAll());
    }

    /**
     * 创建标签。
     *
     * @param request 标签请求
     * @return 标签响应
     */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<TagResponse> create(@Valid @RequestBody TagRequest request) {
        return ApiResponse.success("标签创建成功", tagService.create(request));
    }

    /**
     * 删除标签。
     *
     * @param id 标签ID
     * @return 空响应
     */
    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        tagService.delete(id);
        return ApiResponse.success("标签删除成功", null);
    }
}
