package com.knowledgebase.controller;

import com.knowledgebase.dto.ApiResponse;
import com.knowledgebase.dto.CategoryRequest;
import com.knowledgebase.dto.CategoryResponse;
import com.knowledgebase.service.CategoryService;
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
 * 分类接口控制器。
 */
@RestController
@RequestMapping("/api/categories")
public class CategoryController {

    private final CategoryService categoryService;

    /**
     * 创建分类控制器。
     *
     * @param categoryService 分类服务
     */
    public CategoryController(CategoryService categoryService) {
        this.categoryService = categoryService;
    }

    /**
     * 查询分类树。
     *
     * @return 分类树
     */
    @GetMapping
    public ApiResponse<List<CategoryResponse>> findTree() {
        return ApiResponse.success(categoryService.findTree());
    }

    /**
     * 创建分类。
     *
     * @param request 分类请求
     * @return 分类响应
     */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<CategoryResponse> create(@Valid @RequestBody CategoryRequest request) {
        return ApiResponse.success("分类创建成功", categoryService.create(request));
    }

    /**
     * 更新分类。
     *
     * @param id 分类ID
     * @param request 分类请求
     * @return 分类响应
     */
    @PutMapping("/{id}")
    public ApiResponse<CategoryResponse> update(
            @PathVariable Long id,
            @Valid @RequestBody CategoryRequest request
    ) {
        return ApiResponse.success("分类更新成功", categoryService.update(id, request));
    }

    /**
     * 删除分类。
     *
     * @param id 分类ID
     * @return 空响应
     */
    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        categoryService.delete(id);
        return ApiResponse.success("分类删除成功", null);
    }
}
