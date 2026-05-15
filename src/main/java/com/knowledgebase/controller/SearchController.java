package com.knowledgebase.controller;

import com.knowledgebase.dto.ApiResponse;
import com.knowledgebase.dto.HybridSearchResultResponse;
import com.knowledgebase.dto.PageResponse;
import com.knowledgebase.dto.SearchResultResponse;
import com.knowledgebase.dto.SemanticSearchResultResponse;
import com.knowledgebase.entity.NoteStatus;
import com.knowledgebase.service.HybridSearchService;
import com.knowledgebase.service.SearchService;
import com.knowledgebase.service.VectorIndexService;
import java.time.LocalDate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 搜索接口控制器。
 */
@RestController
@RequestMapping("/api/search")
public class SearchController {

    private final SearchService searchService;
    private final VectorIndexService vectorIndexService;
    private final HybridSearchService hybridSearchService;

    /**
     * 创建搜索控制器。
     *
     * @param searchService 搜索服务
     * @param vectorIndexService 向量索引服务
     * @param hybridSearchService 混合搜索服务
     */
    public SearchController(
            SearchService searchService,
            VectorIndexService vectorIndexService,
            HybridSearchService hybridSearchService
    ) {
        this.searchService = searchService;
        this.vectorIndexService = vectorIndexService;
        this.hybridSearchService = hybridSearchService;
    }

    /**
     * 搜索笔记。
     *
     * @param keyword 关键词
     * @param scope 搜索范围
     * @param tag 标签筛选
     * @param category 分类筛选
     * @param language 语言筛选
     * @param status 发布状态筛选
     * @param updatedFrom 更新时间开始日期
     * @param updatedTo 更新时间结束日期
     * @param page 页码
     * @param size 每页数量
     * @return 搜索结果分页
     */
    @GetMapping
    public ApiResponse<PageResponse<SearchResultResponse>> search(
            @RequestParam(name = "q", required = false) String keyword,
            @RequestParam(defaultValue = "all") String scope,
            @RequestParam(required = false) String tag,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String language,
            @RequestParam(required = false) NoteStatus status,
            @RequestParam(required = false) LocalDate updatedFrom,
            @RequestParam(required = false) LocalDate updatedTo,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size
    ) {
        return ApiResponse.success(searchService.search(
                keyword,
                scope,
                tag,
                category,
                language,
                status,
                updatedFrom,
                updatedTo,
                page,
                size
        ));
    }

    /**
     * 语义搜索笔记。
     *
     * @param question 自然语言问题
     * @param tag 标签筛选
     * @param category 分类筛选
     * @param language 语言筛选
     * @param status 发布状态筛选
     * @param updatedFrom 更新时间开始日期
     * @param updatedTo 更新时间结束日期
     * @param page 页码
     * @param size 每页数量
     * @return 语义搜索结果分页
     */
    @GetMapping("/semantic")
    public ApiResponse<PageResponse<SemanticSearchResultResponse>> semanticSearch(
            @RequestParam(name = "q", required = false) String question,
            @RequestParam(required = false) String tag,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String language,
            @RequestParam(required = false) NoteStatus status,
            @RequestParam(required = false) LocalDate updatedFrom,
            @RequestParam(required = false) LocalDate updatedTo,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size
    ) {
        return ApiResponse.success(vectorIndexService.semanticSearch(
                question,
                tag,
                category,
                language,
                status,
                updatedFrom,
                updatedTo,
                page,
                size
        ));
    }

    /**
     * 混合搜索笔记。
     *
     * @param keyword 搜索关键词或自然语言问题
     * @param scope 搜索范围
     * @param tag 标签筛选
     * @param category 分类筛选
     * @param language 语言筛选
     * @param status 发布状态筛选
     * @param updatedFrom 更新时间开始日期
     * @param updatedTo 更新时间结束日期
     * @param page 页码
     * @param size 每页数量
     * @return 混合搜索结果分页
     */
    @GetMapping("/hybrid")
    public ApiResponse<PageResponse<HybridSearchResultResponse>> hybridSearch(
            @RequestParam(name = "q", required = false) String keyword,
            @RequestParam(defaultValue = "all") String scope,
            @RequestParam(required = false) String tag,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String language,
            @RequestParam(required = false) NoteStatus status,
            @RequestParam(required = false) LocalDate updatedFrom,
            @RequestParam(required = false) LocalDate updatedTo,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size
    ) {
        return ApiResponse.success(hybridSearchService.search(
                keyword,
                scope,
                tag,
                category,
                language,
                status,
                updatedFrom,
                updatedTo,
                page,
                size
        ));
    }
}
