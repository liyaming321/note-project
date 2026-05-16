package com.knowledgebase.service;

import com.knowledgebase.dto.HybridSearchResultResponse;
import com.knowledgebase.dto.KnowledgeQaCitationResponse;
import com.knowledgebase.dto.KnowledgeQaRequest;
import com.knowledgebase.dto.KnowledgeQaResponse;
import com.knowledgebase.entity.Note;
import com.knowledgebase.exception.BusinessException;
import com.knowledgebase.repository.NoteRepository;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.util.HtmlUtils;

/**
 * 基于笔记检索增强的知识库问答服务。
 */
@Service
public class KnowledgeQaService {

    private static final int MAX_CONTEXT_CHARS_PER_NOTE = 1_200;
    private static final double TEMPERATURE = 0.2D;

    private final HybridSearchService hybridSearchService;
    private final NoteRepository noteRepository;
    private final LlmChatService llmChatService;

    /**
     * 创建知识库问答服务。
     *
     * @param hybridSearchService 混合搜索服务
     * @param noteRepository 笔记仓库
     * @param llmChatService LLM 对话服务
     */
    public KnowledgeQaService(
            HybridSearchService hybridSearchService,
            NoteRepository noteRepository,
            LlmChatService llmChatService
    ) {
        this.hybridSearchService = hybridSearchService;
        this.noteRepository = noteRepository;
        this.llmChatService = llmChatService;
    }

    /**
     * 基于知识库笔记回答问题。
     *
     * @param request 问答请求
     * @return 问答响应
     */
    @Transactional(readOnly = true)
    public KnowledgeQaResponse ask(KnowledgeQaRequest request) {
        String question = safeText(request.question());
        if (question.isBlank()) {
            throw new BusinessException("问题不能为空");
        }
        List<HybridSearchResultResponse> searchResults = hybridSearchService.topResults(
                question,
                request.tag(),
                request.category(),
                request.language(),
                request.status(),
                request.updatedFrom(),
                request.updatedTo(),
                request.safeTopK()
        );
        if (searchResults.isEmpty()) {
            return new KnowledgeQaResponse("未检索到可引用的笔记，请调整问题或筛选条件后重试。", "", "", List.of());
        }
        Map<Long, Note> noteMap = loadNotes(searchResults);
        List<KnowledgeQaCitationResponse> citations = new ArrayList<>();
        List<String> contextBlocks = new ArrayList<>();
        for (int index = 0; index < searchResults.size(); index++) {
            HybridSearchResultResponse result = searchResults.get(index);
            Note note = noteMap.get(result.id());
            if (note == null) {
                continue;
            }
            String snippet = citationSnippet(result, note);
            citations.add(KnowledgeQaCitationResponse.from(note, snippet));
            contextBlocks.add("""
                    [%d] 标题：%s
                    链接：/notes/%d
                    摘要：%s
                    正文片段：%s
                    """.formatted(
                    contextBlocks.size() + 1,
                    safeText(note.getTitle()),
                    note.getId(),
                    safeText(note.getSummary()),
                    limitText(safeText(note.getContentText()), MAX_CONTEXT_CHARS_PER_NOTE)
            ));
        }
        if (citations.isEmpty()) {
            return new KnowledgeQaResponse("检索结果已失效，请重建索引后再试。", "", "", List.of());
        }
        LlmChatService.LlmChatResult chatResult = llmChatService.chat(
                request.provider(),
                "你是个人知识库问答助手。只能基于给定引用回答；无法从引用中得出时要明确说明。回答必须用中文，并在关键句后标注引用编号，如 [1]。",
                buildPrompt(question, contextBlocks),
                TEMPERATURE
        );
        return new KnowledgeQaResponse(
                chatResult.content(),
                chatResult.provider(),
                chatResult.model(),
                citations
        );
    }

    /**
     * 批量加载笔记并保持可按 ID 查找。
     *
     * @param searchResults 搜索结果
     * @return 笔记映射
     */
    private Map<Long, Note> loadNotes(List<HybridSearchResultResponse> searchResults) {
        List<Long> noteIds = searchResults.stream().map(HybridSearchResultResponse::id).toList();
        Map<Long, Note> noteMap = new LinkedHashMap<>();
        for (Note note : noteRepository.findByIdInAndDeletedFalseAndArchivedFalse(noteIds)) {
            noteMap.put(note.getId(), note);
        }
        return noteMap;
    }

    /**
     * 构建问答提示词。
     *
     * @param question 问题
     * @param contextBlocks 上下文
     * @return 提示词
     */
    private String buildPrompt(String question, List<String> contextBlocks) {
        return """
                请回答下面的问题。

                问题：%s

                可用引用：
                %s

                要求：
                1. 只使用上面的引用内容回答，不要编造。
                2. 每个关键结论后标注引用编号，例如 [1]。
                3. 如果引用不足以回答，请说明缺少哪些信息。
                4. 回答后附一行“参考来源：”并列出引用编号。
                """.formatted(question, String.join("\n", contextBlocks));
    }

    /**
     * 构建引用片段。
     *
     * @param result 搜索结果
     * @param note 笔记
     * @return 片段
     */
    private String citationSnippet(HybridSearchResultResponse result, Note note) {
        String highlight = HtmlUtils.htmlUnescape(safeText(result.highlight()).replaceAll("<[^>]+>", ""));
        if (!highlight.isBlank()) {
            return limitText(highlight, 220);
        }
        return limitText(safeText(note.getContentText()), 220);
    }

    /**
     * 截断文本。
     *
     * @param value 原始文本
     * @param maxLength 最大长度
     * @return 截断文本
     */
    private String limitText(String value, int maxLength) {
        String safeValue = safeText(value).replaceAll("\\s+", " ");
        return safeValue.length() <= maxLength ? safeValue : safeValue.substring(0, maxLength) + "...";
    }

    /**
     * 获取安全文本。
     *
     * @param value 原始文本
     * @return 安全文本
     */
    private String safeText(String value) {
        return value == null ? "" : value.trim();
    }
}
