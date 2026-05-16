package com.knowledgebase.controller;

import com.knowledgebase.dto.ApiResponse;
import com.knowledgebase.dto.KnowledgeQaRequest;
import com.knowledgebase.dto.KnowledgeQaResponse;
import com.knowledgebase.service.KnowledgeQaService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 知识库问答接口控制器。
 */
@RestController
@RequestMapping("/api/knowledge-qa")
public class KnowledgeQaController {

    private final KnowledgeQaService knowledgeQaService;

    /**
     * 创建知识库问答控制器。
     *
     * @param knowledgeQaService 问答服务
     */
    public KnowledgeQaController(KnowledgeQaService knowledgeQaService) {
        this.knowledgeQaService = knowledgeQaService;
    }

    /**
     * 基于知识库问答。
     *
     * @param request 问答请求
     * @return 问答结果
     */
    @PostMapping
    public ApiResponse<KnowledgeQaResponse> ask(@Valid @RequestBody KnowledgeQaRequest request) {
        return ApiResponse.success("问答生成成功", knowledgeQaService.ask(request));
    }
}
