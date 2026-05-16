package com.knowledgebase.controller;

import com.knowledgebase.dto.ApiResponse;
import com.knowledgebase.dto.SimilarNoteResponse;
import com.knowledgebase.service.SimilarNoteService;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 笔记发现接口控制器。
 */
@RestController
@RequestMapping("/api/notes")
public class NoteDiscoveryController {

    private final SimilarNoteService similarNoteService;

    /**
     * 创建笔记发现控制器。
     *
     * @param similarNoteService 相似笔记服务
     */
    public NoteDiscoveryController(SimilarNoteService similarNoteService) {
        this.similarNoteService = similarNoteService;
    }

    /**
     * 查询相似笔记。
     *
     * @param id 笔记ID
     * @param limit 最大数量
     * @return 相似笔记列表
     */
    @GetMapping("/{id}/similar")
    public ApiResponse<List<SimilarNoteResponse>> similar(
            @PathVariable Long id,
            @RequestParam(defaultValue = "6") int limit
    ) {
        return ApiResponse.success(similarNoteService.findSimilarNotes(id, limit));
    }
}
