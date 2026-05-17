package com.knowledgebase.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.forwardedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/**
 * 知识库 API 集成测试。
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class KnowledgeBaseApiIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    /**
     * 验证分类、标签和笔记的核心 CRUD 流程。
     *
     * @throws Exception 请求执行异常
     */
    @Test
    void shouldManageNotesCategoriesAndTags() throws Exception {
        MvcResult categoryResult = mockMvc.perform(post("/api/categories")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(Map.of("name", "测试分类"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.name").value("测试分类"))
                .andReturn();
        long categoryId = readDataId(categoryResult);

        mockMvc.perform(post("/api/tags")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(Map.of("name", "spring"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.name").value("spring"));

        MvcResult noteResult = mockMvc.perform(post("/api/notes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(Map.of(
                                "title", "第一篇笔记",
                                "content", "# 标题\n\n这是一段 Markdown 内容。",
                                "type", "MARKDOWN",
                                "categoryId", categoryId,
                                "tags", List.of("spring", "java"),
                                "favorite", true,
                                "pinned", true
                        ))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.title").value("第一篇笔记"))
                .andExpect(jsonPath("$.data.favorite").value(true))
                .andExpect(jsonPath("$.data.pinned").value(true))
                .andReturn();
        long noteId = readDataId(noteResult);

        mockMvc.perform(get("/api/notes/{id}", noteId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(noteId))
                .andExpect(jsonPath("$.data.contentText").value("标题 这是一段 Markdown 内容。"));

        mockMvc.perform(get("/api/notes")
                        .param("tag", "spring")
                        .param("categoryId", String.valueOf(categoryId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(1))
                .andExpect(jsonPath("$.data.items[0].title").value("第一篇笔记"));

        mockMvc.perform(put("/api/notes/{id}", noteId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(Map.of(
                                "title", "更新后的笔记",
                                "content", "更新内容",
                                "type", "MARKDOWN",
                                "categoryId", categoryId,
                                "tags", List.of("updated")
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.title").value("更新后的笔记"))
                .andExpect(jsonPath("$.data.tags[0].name").value("updated"));

        mockMvc.perform(patch("/api/notes/{id}/favorite", noteId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(Map.of("value", false))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.favorite").value(false));

        mockMvc.perform(delete("/api/notes/{id}", noteId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        mockMvc.perform(get("/api/notes").param("tag", "updated"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(0));

        mockMvc.perform(get("/api/notes")
                        .param("tag", "updated")
                        .param("includeDeleted", "true"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(1))
                .andExpect(jsonPath("$.data.items[0].deleted").value(true));

        mockMvc.perform(post("/api/notes/{id}/restore", noteId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.deleted").value(false));
    }

    /**
     * 验证普通文本内容格式会按原文保存纯文本内容。
     *
     * @throws Exception 请求执行异常
     */
    @Test
    void shouldSupportPlainTextContentFormat() throws Exception {
        String plainText = "第一行普通文本\n# 这一行不是 Markdown 标题\n* 星号也应该按原文保存";

        MvcResult noteResult = mockMvc.perform(post("/api/notes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(Map.of(
                                "title", "普通文本笔记",
                                "content", plainText,
                                "type", "TEXT",
                                "tags", List.of("plain-text-format")
                        ))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.type").value("TEXT"))
                .andExpect(jsonPath("$.data.contentText").value(plainText))
                .andReturn();
        long noteId = readDataId(noteResult);

        mockMvc.perform(get("/api/notes/{id}", noteId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content").value(plainText))
                .andExpect(jsonPath("$.data.contentText").value(plainText));

        mockMvc.perform(get("/api/notes")
                        .param("type", "TEXT")
                        .param("tag", "plain-text-format"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(1))
                .andExpect(jsonPath("$.data.items[0].type").value("TEXT"));
    }

    /**
     * 验证删除标签时会同步移除笔记关联。
     *
     * @throws Exception 请求执行异常
     */
    @Test
    void shouldDeleteTagAndDetachFromNotes() throws Exception {
        MvcResult tagResult = mockMvc.perform(post("/api/tags")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(Map.of("name", "delete-me"))))
                .andExpect(status().isCreated())
                .andReturn();
        long tagId = readDataId(tagResult);

        MvcResult noteResult = mockMvc.perform(post("/api/notes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(Map.of(
                                "title", "带待删标签的笔记",
                                "content", "删除标签后，这篇笔记应保留但不再带该标签。",
                                "type", "MARKDOWN",
                                "tags", List.of("delete-me", "keep-me")
                        ))))
                .andExpect(status().isCreated())
                .andReturn();
        long noteId = readDataId(noteResult);

        mockMvc.perform(delete("/api/tags/{id}", tagId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        mockMvc.perform(get("/api/tags"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[*].name").value(org.hamcrest.Matchers.not(org.hamcrest.Matchers.hasItem("delete-me"))));

        mockMvc.perform(get("/api/notes/{id}", noteId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.tags[*].name").value(org.hamcrest.Matchers.hasItem("keep-me")))
                .andExpect(jsonPath("$.data.tags[*].name").value(org.hamcrest.Matchers.not(org.hamcrest.Matchers.hasItem("delete-me"))));

        mockMvc.perform(get("/api/notes").param("tag", "delete-me"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(0));

        mockMvc.perform(get("/api/search")
                        .param("q", "删除标签")
                        .param("tag", "delete-me"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(0));
    }

    /**
     * 验证笔记用途类型可以管理、筛选、保存历史快照，并在删除后与笔记脱钩。
     *
     * @throws Exception 请求执行异常
     */
    @Test
    void shouldManageNoteKindsAndDetachFromNotes() throws Exception {
        MvcResult noteKindResult = mockMvc.perform(post("/api/note-kinds")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(Map.of("name", "读书测试", "sortOrder", 88))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.name").value("读书测试"))
                .andExpect(jsonPath("$.data.builtIn").value(false))
                .andReturn();
        long noteKindId = readDataId(noteKindResult);

        MvcResult noteResult = mockMvc.perform(post("/api/notes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(Map.of(
                                "title", "带类型的笔记",
                                "content", "这篇笔记用于验证用途类型管理。",
                                "type", "MARKDOWN",
                                "noteKindId", noteKindId,
                                "tags", List.of("note-kind")
                        ))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.noteKind.id").value((int) noteKindId))
                .andExpect(jsonPath("$.data.noteKind.name").value("读书测试"))
                .andReturn();
        long noteId = readDataId(noteResult);

        mockMvc.perform(get("/api/notes")
                        .param("noteKindId", String.valueOf(noteKindId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(1))
                .andExpect(jsonPath("$.data.items[0].id").value((int) noteId))
                .andExpect(jsonPath("$.data.items[0].noteKind.name").value("读书测试"));

        mockMvc.perform(put("/api/note-kinds/{id}", noteKindId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(Map.of("name", "读书复盘", "sortOrder", 89))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").value("读书复盘"))
                .andExpect(jsonPath("$.data.sortOrder").value(89));

        mockMvc.perform(put("/api/notes/{id}", noteId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(Map.of(
                                "title", "带类型的笔记更新",
                                "content", "更新后仍然保留用途类型，并保存历史快照。",
                                "type", "MARKDOWN",
                                "noteKindId", noteKindId,
                                "tags", List.of("note-kind", "updated")
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.noteKind.name").value("读书复盘"));

        mockMvc.perform(get("/api/notes/{id}/history/{version}", noteId, 1))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.noteKindId").value((int) noteKindId))
                .andExpect(jsonPath("$.data.noteKindName").value("读书复盘"));

        mockMvc.perform(delete("/api/note-kinds/{id}", noteKindId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        mockMvc.perform(get("/api/notes/{id}", noteId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.noteKind").doesNotExist())
                .andExpect(jsonPath("$.data.tags[*].name").value(org.hamcrest.Matchers.hasItem("updated")));

        mockMvc.perform(get("/api/notes")
                        .param("noteKindId", String.valueOf(noteKindId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(0));
    }

    /**
     * 验证参数校验失败时返回错误响应。
     *
     * @throws Exception 请求执行异常
     */
    @Test
    void shouldReturnValidationError() throws Exception {
        mockMvc.perform(post("/api/notes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(Map.of(
                                "title", "",
                                "content", "",
                                "type", "MARKDOWN"
                        ))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));
    }

    /**
     * 验证笔记摘要保存和 LLM 未配置时的错误提示。
     *
     * @throws Exception 请求执行异常
     */
    @Test
    void shouldSaveSummaryAndValidateLlmConfiguration() throws Exception {
        MvcResult noteResult = mockMvc.perform(post("/api/notes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(Map.of(
                                "title", "摘要笔记",
                                "content", "这是一段较长的笔记正文，用于验证摘要字段会优先展示。",
                                "summary", "LLM 生成的摘要内容",
                                "type", "MARKDOWN",
                                "tags", List.of("summary")
                        ))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.summary").value("LLM 生成的摘要内容"))
                .andReturn();
        long noteId = readDataId(noteResult);

        mockMvc.perform(get("/api/notes/{id}", noteId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.summary").value("LLM 生成的摘要内容"));

        mockMvc.perform(get("/api/notes").param("tag", "summary"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0].summary").value("LLM 生成的摘要内容"));

        mockMvc.perform(get("/api/notes/llm-providers"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].name").value("bailian"))
                .andExpect(jsonPath("$.data[0].configured").value(false))
                .andExpect(jsonPath("$.data[1].name").value("deepseek"))
                .andExpect(jsonPath("$.data[1].configured").value(false));

        mockMvc.perform(post("/api/notes/llm-summary")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(Map.of(
                                "provider", "deepseek",
                                "title", "摘要笔记",
                                "content", "需要调用 LLM 总结的正文内容",
                                "type", "MARKDOWN",
                                "categoryNames", List.of("默认分类")
                        ))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));
    }

    /**
     * 验证草稿发布、归档、回收站、永久删除和更新时间范围筛选。
     *
     * @throws Exception 请求执行异常
     */
    @Test
    void shouldManageStageSixNoteStatesAndFilters() throws Exception {
        MvcResult categoryResult = mockMvc.perform(post("/api/categories")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(Map.of("name", "第六阶段状态分类"))))
                .andExpect(status().isCreated())
                .andReturn();
        long categoryId = readDataId(categoryResult);

        MvcResult noteResult = mockMvc.perform(post("/api/notes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(Map.of(
                                "title", "第六阶段草稿",
                                "content", "第六阶段草稿内容",
                                "type", "MARKDOWN",
                                "status", "DRAFT",
                                "categoryId", categoryId,
                                "tags", List.of("stage-six-state")
                        ))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.status").value("DRAFT"))
                .andExpect(jsonPath("$.data.archived").value(false))
                .andReturn();
        long noteId = readDataId(noteResult);

        mockMvc.perform(get("/api/notes")
                        .param("tag", "stage-six-state")
                        .param("status", "DRAFT"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(1))
                .andExpect(jsonPath("$.data.items[0].status").value("DRAFT"));

        mockMvc.perform(get("/api/search")
                        .param("tag", "stage-six-state")
                        .param("status", "DRAFT"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(1));

        mockMvc.perform(patch("/api/notes/{id}/status", noteId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(Map.of("status", "PUBLISHED"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("PUBLISHED"));

        String today = LocalDate.now().toString();
        mockMvc.perform(get("/api/notes")
                        .param("tag", "stage-six-state")
                        .param("status", "PUBLISHED")
                        .param("updatedFrom", today)
                        .param("updatedTo", today))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(1));

        mockMvc.perform(get("/api/search")
                        .param("tag", "stage-six-state")
                        .param("status", "PUBLISHED")
                        .param("updatedFrom", today)
                        .param("updatedTo", today))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(1));

        mockMvc.perform(patch("/api/notes/{id}/archived", noteId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(Map.of("value", true))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.archived").value(true));

        mockMvc.perform(get("/api/notes").param("tag", "stage-six-state"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(0));

        mockMvc.perform(get("/api/notes")
                        .param("tag", "stage-six-state")
                        .param("archived", "true"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(1))
                .andExpect(jsonPath("$.data.items[0].archived").value(true));

        mockMvc.perform(get("/api/search").param("tag", "stage-six-state"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(0));

        mockMvc.perform(patch("/api/notes/{id}/archived", noteId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(Map.of("value", false))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.archived").value(false));

        mockMvc.perform(delete("/api/notes/{id}", noteId))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/notes")
                        .param("tag", "stage-six-state")
                        .param("onlyDeleted", "true"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(1))
                .andExpect(jsonPath("$.data.items[0].deleted").value(true));

        mockMvc.perform(post("/api/notes/batch/restore")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(Map.of("noteIds", List.of(noteId)))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].deleted").value(false));

        mockMvc.perform(delete("/api/notes/{id}", noteId))
                .andExpect(status().isOk());
        mockMvc.perform(delete("/api/notes/{id}/permanent", noteId))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/notes")
                        .param("tag", "stage-six-state")
                        .param("onlyDeleted", "true"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(0));
    }

    /**
     * 验证分类管理和工作区维护信息接口。
     *
     * @throws Exception 请求执行异常
     */
    @Test
    void shouldManageCategoriesAndExposeWorkspaceInfo() throws Exception {
        MvcResult parentResult = mockMvc.perform(post("/api/categories")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(Map.of("name", "第六阶段父分类"))))
                .andExpect(status().isCreated())
                .andReturn();
        long parentId = readDataId(parentResult);

        mockMvc.perform(put("/api/categories/{id}", parentId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(Map.of("name", "第六阶段父分类改名"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").value("第六阶段父分类改名"));

        MvcResult childResult = mockMvc.perform(post("/api/categories")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(Map.of("name", "第六阶段子分类", "parentId", parentId))))
                .andExpect(status().isCreated())
                .andReturn();
        long childId = readDataId(childResult);

        mockMvc.perform(put("/api/categories/{id}", parentId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(Map.of("name", "第六阶段父分类改名", "parentId", childId))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));

        mockMvc.perform(delete("/api/categories/{id}", parentId))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));

        mockMvc.perform(delete("/api/categories/{id}", childId))
                .andExpect(status().isOk());
        mockMvc.perform(delete("/api/categories/{id}", parentId))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/admin/workspace"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.dataPath").isNotEmpty())
                .andExpect(jsonPath("$.data.indexPath").isNotEmpty())
                .andExpect(jsonPath("$.data.vectorIndexPath").isNotEmpty())
                .andExpect(jsonPath("$.data.imagesPath").isNotEmpty())
                .andExpect(jsonPath("$.data.version").value("dev"));

        mockMvc.perform(get("/api/admin/embedding-provider"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").value("local-cli"))
                .andExpect(jsonPath("$.data.configured").value(false));

        mockMvc.perform(get("/api/admin/vector-index"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.vectorIndexPath").isNotEmpty())
                .andExpect(jsonPath("$.data.provider").value("local-cli"))
                .andExpect(jsonPath("$.data.configured").value(false))
                .andExpect(jsonPath("$.data.available").value(false));

        mockMvc.perform(post("/api/admin/vector-index/rebuild"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));

        mockMvc.perform(get("/api/search/semantic").param("q", "未配置时的语义搜索"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("KNOWLEDGE_BASE_EMBEDDING")));
    }

    /**
     * 验证 RAG 问答、相似笔记和索引运维接口。
     *
     * @throws Exception 请求执行异常
     */
    @Test
    void shouldSupportStageSevenDiscoveryAndMaintenanceApis() throws Exception {
        MvcResult categoryResult = mockMvc.perform(post("/api/categories")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(Map.of("name", "第七阶段发现分类"))))
                .andExpect(status().isCreated())
                .andReturn();
        long categoryId = readDataId(categoryResult);
        long sourceNoteId = createTestNote(
                "RAG 混合搜索说明",
                "RAG 会先使用混合搜索召回相关笔记，再调用 LLM 生成带引用的回答。",
                List.of("stage-seven", "rag")
        );
        long similarNoteId = createTestNote(
                "月亮火车备忘",
                "蓝莓、火车和月亮是完全不同主题的个人备忘。",
                List.of("stage-seven", "rag")
        );

        mockMvc.perform(put("/api/notes/{id}", sourceNoteId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(Map.of(
                                "title", "RAG 混合搜索说明",
                                "content", "RAG 会先使用混合搜索召回相关笔记，再调用 LLM 生成带引用的回答。",
                                "type", "MARKDOWN",
                                "categoryId", categoryId,
                                "tags", List.of("stage-seven", "rag")
                        ))))
                .andExpect(status().isOk());

        mockMvc.perform(put("/api/notes/{id}", similarNoteId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(Map.of(
                                "title", "月亮火车备忘",
                                "content", "蓝莓、火车和月亮是完全不同主题的个人备忘。",
                                "type", "MARKDOWN",
                                "categoryId", categoryId,
                                "tags", List.of("stage-seven", "rag")
                        ))))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/admin/reindex"))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/knowledge-qa")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(Map.of(
                                "question", "RAG 问答如何返回引用？",
                                "provider", "deepseek",
                                "topK", 3,
                                "tag", "stage-seven"
                        ))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("deepseek")))
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("API Key")));

        mockMvc.perform(get("/api/notes/{id}/similar", sourceNoteId)
                        .param("limit", "5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].id").value((int) similarNoteId))
                .andExpect(jsonPath("$.data[0].reason").value(org.hamcrest.Matchers.containsString("标签")))
                .andExpect(jsonPath("$.data[0].source").value("metadata"));

        mockMvc.perform(get("/api/admin/index-health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.databaseActiveCount").value(org.hamcrest.Matchers.greaterThanOrEqualTo(2)))
                .andExpect(jsonPath("$.data.searchIndexedCount").value(org.hamcrest.Matchers.greaterThanOrEqualTo(2)))
                .andExpect(jsonPath("$.data.vectorIndexedCount").value(0))
                .andExpect(jsonPath("$.data.searchHealthy").isBoolean())
                .andExpect(jsonPath("$.data.vectorHealthy").value(true));

        mockMvc.perform(post("/api/admin/vector-index/cleanup"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.removedCount").value(0))
                .andExpect(jsonPath("$.data.indexedCount").value(0))
                .andExpect(jsonPath("$.data.message").value("未发现无效向量"));
    }

    /**
     * 验证全文搜索、筛选、逻辑删除、恢复和手动重建索引。
     *
     * @throws Exception 请求执行异常
     */
    @Test
    void shouldSearchNotesAndKeepIndexSynchronized() throws Exception {
        MvcResult categoryResult = mockMvc.perform(post("/api/categories")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(Map.of("name", "搜索分类"))))
                .andExpect(status().isCreated())
                .andReturn();
        long categoryId = readDataId(categoryResult);

        MvcResult noteResult = mockMvc.perform(post("/api/notes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(Map.of(
                                "title", "Lucene 中文搜索",
                                "content", """
                                        # 搜索笔记

                                        这是一段关于全文检索和中文分词的内容。

                                        ```java
                                        public class SearchDemo {
                                            void query() {
                                                System.out.println("hello lucene");
                                            }
                                        }
                                        ```
                                        """,
                                "type", "MARKDOWN",
                                "categoryId", categoryId,
                                "tags", List.of("search", "lucene"),
                                "language", "java"
                        ))))
                .andExpect(status().isCreated())
                .andReturn();
        long noteId = readDataId(noteResult);

        mockMvc.perform(get("/api/search")
                        .param("q", "Lucene")
                        .param("scope", "title"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(1))
                .andExpect(jsonPath("$.data.items[0].id").value(noteId))
                .andExpect(jsonPath("$.data.items[0].hitFields[0]").value("title"));

        mockMvc.perform(get("/api/search")
                        .param("q", "System.out.println")
                        .param("scope", "code")
                        .param("tag", "lucene")
                        .param("category", String.valueOf(categoryId))
                        .param("language", "java"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(1))
                .andExpect(jsonPath("$.data.items[0].highlight").value(org.hamcrest.Matchers.containsString("<mark>")));

        mockMvc.perform(delete("/api/notes/{id}", noteId))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/search")
                        .param("q", "Lucene")
                        .param("scope", "all"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(0));

        mockMvc.perform(post("/api/notes/{id}/restore", noteId))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/admin/reindex"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.indexedCount").value(org.hamcrest.Matchers.greaterThanOrEqualTo(1)));

        mockMvc.perform(get("/api/search")
                        .param("q", "中文分词")
                        .param("scope", "all"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(1))
                .andExpect(jsonPath("$.data.items[0].id").value(noteId));
    }

    /**
     * 验证笔记更新自动保存历史版本，并可恢复到指定版本。
     *
     * @throws Exception 请求执行异常
     */
    @Test
    void shouldSaveHistoryAndRevertToSpecificVersion() throws Exception {
        long noteId = createTestNote("历史初稿", "初稿内容", List.of("history", "v1"));
        updateTestNote(noteId, "历史二稿", "二稿内容", List.of("history", "v2"));
        updateTestNote(noteId, "历史终稿", "终稿内容", List.of("history", "v3"));

        mockMvc.perform(get("/api/notes/{id}/history", noteId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", org.hamcrest.Matchers.hasSize(2)))
                .andExpect(jsonPath("$.data[0].version").value(2))
                .andExpect(jsonPath("$.data[0].title").value("历史二稿"))
                .andExpect(jsonPath("$.data[1].version").value(1));

        mockMvc.perform(get("/api/notes/{id}/history/{version}", noteId, 1))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.title").value("历史初稿"))
                .andExpect(jsonPath("$.data.content").value("初稿内容"))
                .andExpect(jsonPath("$.data.tags", org.hamcrest.Matchers.hasItem("v1")));

        mockMvc.perform(post("/api/notes/{id}/revert/{version}", noteId, 1))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.title").value("历史初稿"))
                .andExpect(jsonPath("$.data.content").value("初稿内容"))
                .andExpect(jsonPath("$.data.tags[*].name", org.hamcrest.Matchers.hasItem("v1")));

        mockMvc.perform(get("/api/notes/{id}/history", noteId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", org.hamcrest.Matchers.hasSize(3)))
                .andExpect(jsonPath("$.data[0].version").value(3))
                .andExpect(jsonPath("$.data[0].title").value("历史终稿"));

        mockMvc.perform(get("/api/search").param("tag", "v1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[*].id", org.hamcrest.Matchers.hasItem((int) noteId)));
    }

    /**
     * 验证历史版本会按配置裁剪最旧记录。
     *
     * @throws Exception 请求执行异常
     */
    @Test
    void shouldTrimHistoryByConfiguredMaxVersions() throws Exception {
        long noteId = createTestNote("保留初始", "保留初始内容", List.of("keep"));
        updateTestNote(noteId, "保留版本一", "保留内容一", List.of("keep"));
        updateTestNote(noteId, "保留版本二", "保留内容二", List.of("keep"));
        updateTestNote(noteId, "保留版本三", "保留内容三", List.of("keep"));
        updateTestNote(noteId, "保留版本四", "保留内容四", List.of("keep"));

        mockMvc.perform(get("/api/notes/{id}/history", noteId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", org.hamcrest.Matchers.hasSize(3)))
                .andExpect(jsonPath("$.data[0].version").value(4))
                .andExpect(jsonPath("$.data[2].version").value(2));

        mockMvc.perform(get("/api/notes/{id}/history/{version}", noteId, 1))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false));
    }

    /**
     * 验证 Markdown 文件和 ZIP 批量导入。
     *
     * @throws Exception 请求执行异常
     */
    @Test
    void shouldImportMarkdownFilesAndZip() throws Exception {
        MockMultipartFile markdownFile = new MockMultipartFile(
                "files",
                "front-matter.md",
                "text/markdown",
                """
                        ---
                        title: "导入标题"
                        tags:
                          - import
                          - markdown
                        category: "导入分类"
                        language: "java"
                        ---
                        # 正文标题

                        导入正文内容
                        """.getBytes(StandardCharsets.UTF_8)
        );

        mockMvc.perform(multipart("/api/import/markdown").file(markdownFile))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.importedCount").value(1))
                .andExpect(jsonPath("$.data.items[0].title").value("导入标题"))
                .andExpect(jsonPath("$.data.items[0].success").value(true));

        mockMvc.perform(get("/api/notes").param("tag", "import"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(1))
                .andExpect(jsonPath("$.data.items[0].category.name").value("导入分类"));

        MockMultipartFile zipFile = new MockMultipartFile(
                "files",
                "notes.zip",
                "application/zip",
                createMarkdownZip()
        );

        mockMvc.perform(multipart("/api/import/markdown").file(zipFile))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.importedCount").value(2))
                .andExpect(jsonPath("$.data.failedCount").value(0));
    }

    /**
     * 验证 Markdown 单篇导出和 ZIP 批量导出。
     *
     * @throws Exception 请求执行异常
     */
    @Test
    void shouldExportMarkdownAndZip() throws Exception {
        long firstNoteId = createTestNote("导出笔记一", "# 导出内容一", List.of("export"));
        long secondNoteId = createTestNote("导出笔记二", "# 导出内容二", List.of("export"));

        mockMvc.perform(get("/api/notes/{id}/export/markdown", firstNoteId))
                .andExpect(status().isOk())
                .andExpect(result -> {
                    String markdown = result.getResponse().getContentAsString(StandardCharsets.UTF_8);
                    org.assertj.core.api.Assertions.assertThat(markdown)
                            .contains("title: \"导出笔记一\"")
                            .contains("# 导出内容一");
                });

        MvcResult zipResult = mockMvc.perform(post("/api/export/zip")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(Map.of("noteIds", List.of(firstNoteId, secondNoteId)))))
                .andExpect(status().isOk())
                .andReturn();

        List<String> entryNames = readZipEntryNames(zipResult.getResponse().getContentAsByteArray());
        org.assertj.core.api.Assertions.assertThat(entryNames)
                .hasSize(2)
                .allMatch(name -> name.endsWith(".md"));
    }

    /**
     * 验证图片上传和读取。
     *
     * @throws Exception 请求执行异常
     */
    @Test
    void shouldUploadAndReadImage() throws Exception {
        byte[] imageBytes = "fake-png-content".getBytes(StandardCharsets.UTF_8);
        MockMultipartFile imageFile = new MockMultipartFile(
                "file",
                "paste.png",
                "image/png",
                imageBytes
        );

        MvcResult uploadResult = mockMvc.perform(multipart("/api/assets/images").file(imageFile))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.url", org.hamcrest.Matchers.startsWith("/api/assets/images/")))
                .andReturn();

        String imageUrl = objectMapper.readTree(uploadResult.getResponse().getContentAsString())
                .path("data")
                .path("url")
                .asText();
        mockMvc.perform(get(imageUrl))
                .andExpect(status().isOk())
                .andExpect(result -> org.assertj.core.api.Assertions.assertThat(result.getResponse().getContentAsByteArray())
                        .isEqualTo(imageBytes));
    }

    /**
     * 验证笔记拖拽排序接口。
     *
     * @throws Exception 请求执行异常
     */
    @Test
    void shouldReorderNotesByCustomSortOrder() throws Exception {
        long firstNoteId = createTestNote("排序笔记一", "排序内容一", List.of("sort"));
        long secondNoteId = createTestNote("排序笔记二", "排序内容二", List.of("sort"));
        long thirdNoteId = createTestNote("排序笔记三", "排序内容三", List.of("sort"));

        mockMvc.perform(post("/api/notes/reorder")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(Map.of("noteIds", List.of(thirdNoteId, firstNoteId, secondNoteId)))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].id").value(thirdNoteId))
                .andExpect(jsonPath("$.data[1].id").value(firstNoteId))
                .andExpect(jsonPath("$.data[2].id").value(secondNoteId));

        mockMvc.perform(get("/api/notes")
                        .param("tag", "sort")
                        .param("sort", "sortOrder")
                        .param("direction", "asc"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0].id").value(thirdNoteId))
                .andExpect(jsonPath("$.data.items[1].id").value(firstNoteId))
                .andExpect(jsonPath("$.data.items[2].id").value(secondNoteId));
    }

    /**
     * 验证打包后前端路由能转发，且不会拦截静态入口自身。
     *
     * @throws Exception 请求执行异常
     */
    @Test
    void shouldForwardSpaNoteRoutes() throws Exception {
        mockMvc.perform(get("/notes/1"))
                .andExpect(status().isOk())
                .andExpect(forwardedUrl("/index.html"));
    }

    /**
     * 验证完整数据备份 ZIP 会包含数据、索引和图片目录。
     *
     * @throws Exception 请求执行异常
     */
    @Test
    void shouldCreateFullBackupZip() throws Exception {
        createTestNote("备份笔记", "备份内容", List.of("backup"));
        mockMvc.perform(post("/api/admin/reindex"))
                .andExpect(status().isOk());
        mockMvc.perform(multipart("/api/assets/images").file(new MockMultipartFile(
                        "file",
                        "backup.png",
                        "image/png",
                        "backup-image".getBytes(StandardCharsets.UTF_8)
                )))
                .andExpect(status().isOk());

        MvcResult backupResult = mockMvc.perform(get("/api/admin/backup"))
                .andExpect(status().isOk())
                .andReturn();

        List<String> entryNames = readZipEntryNames(backupResult.getResponse().getContentAsByteArray());
        org.assertj.core.api.Assertions.assertThat(entryNames)
                .anyMatch(name -> name.startsWith("data/"))
                .anyMatch(name -> name.startsWith("index/"))
                .anyMatch(name -> name.startsWith("vector-index/"))
                .anyMatch(name -> name.startsWith("images/"));
    }

    /**
     * 验证浏览器书签 HTML 导入为笔记。
     *
     * @throws Exception 请求执行异常
     */
    @Test
    void shouldImportBrowserBookmarks() throws Exception {
        MockMultipartFile bookmarkFile = new MockMultipartFile(
                "file",
                "bookmarks.html",
                "text/html",
                """
                        <!DOCTYPE NETSCAPE-Bookmark-file-1>
                        <DL><p>
                            <DT><H3 ADD_DATE="1710000000">技术资料</H3>
                            <DL><p>
                                <DT><A HREF="https://spring.io/projects/spring-boot" ADD_DATE="1710000001">Spring Boot</A>
                                <DT><A HREF="https://vuejs.org/" ADD_DATE="1710000002">Vue.js</A>
                            </DL><p>
                        </DL><p>
                        """.getBytes(StandardCharsets.UTF_8)
        );

        mockMvc.perform(multipart("/api/import/bookmarks").file(bookmarkFile))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.importedCount").value(2))
                .andExpect(jsonPath("$.data.items[0].title").value("Spring Boot"))
                .andExpect(jsonPath("$.data.items[1].url").value("https://vuejs.org/"));

        mockMvc.perform(get("/api/notes").param("tag", "bookmark"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements", org.hamcrest.Matchers.greaterThanOrEqualTo(2)))
                .andExpect(jsonPath("$.data.items[*].category.name", org.hamcrest.Matchers.hasItem("技术资料")));
    }

    /**
     * 将对象序列化为 JSON。
     *
     * @param value 对象
     * @return JSON 字符串
     * @throws Exception 序列化异常
     */
    private String toJson(Object value) throws Exception {
        return objectMapper.writeValueAsString(value);
    }

    /**
     * 创建测试笔记。
     *
     * @param title 标题
     * @param content 内容
     * @param tags 标签
     * @return 笔记ID
     * @throws Exception 请求执行异常
     */
    private long createTestNote(String title, String content, List<String> tags) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/notes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(Map.of(
                                "title", title,
                                "content", content,
                                "type", "MARKDOWN",
                                "tags", tags
                        ))))
                .andExpect(status().isCreated())
                .andReturn();
        return readDataId(result);
    }

    /**
     * 更新测试笔记。
     *
     * @param noteId 笔记ID
     * @param title 标题
     * @param content 内容
     * @param tags 标签
     * @throws Exception 请求执行异常
     */
    private void updateTestNote(long noteId, String title, String content, List<String> tags) throws Exception {
        mockMvc.perform(put("/api/notes/{id}", noteId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(Map.of(
                                "title", title,
                                "content", content,
                                "type", "MARKDOWN",
                                "tags", tags
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.title").value(title));
    }

    /**
     * 读取统一响应中的 data.id。
     *
     * @param result MVC 响应
     * @return 资源ID
     * @throws Exception 解析异常
     */
    private long readDataId(MvcResult result) throws Exception {
        JsonNode rootNode = objectMapper.readTree(result.getResponse().getContentAsString());
        return rootNode.path("data").path("id").asLong();
    }

    /**
     * 创建包含两个 Markdown 文件的 ZIP。
     *
     * @return ZIP 字节
     * @throws Exception ZIP 写入异常
     */
    private byte[] createMarkdownZip() throws Exception {
        try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
             ZipOutputStream zipOutputStream = new ZipOutputStream(outputStream, StandardCharsets.UTF_8)) {
            zipOutputStream.putNextEntry(new ZipEntry("zip-note-a.md"));
            zipOutputStream.write("""
                    ---
                    title: "ZIP 导入 A"
                    tags: zip,a
                    ---
                    ZIP 正文 A
                    """.getBytes(StandardCharsets.UTF_8));
            zipOutputStream.closeEntry();
            zipOutputStream.putNextEntry(new ZipEntry("folder/zip-note-b.md"));
            zipOutputStream.write("""
                    ---
                    title: "ZIP 导入 B"
                    tags:
                      - zip
                      - b
                    ---
                    ZIP 正文 B
                    """.getBytes(StandardCharsets.UTF_8));
            zipOutputStream.closeEntry();
            zipOutputStream.finish();
            return outputStream.toByteArray();
        }
    }

    /**
     * 读取 ZIP 文件条目名。
     *
     * @param zipBytes ZIP 字节
     * @return 条目名称列表
     * @throws Exception ZIP 读取异常
     */
    private List<String> readZipEntryNames(byte[] zipBytes) throws Exception {
        java.util.ArrayList<String> names = new java.util.ArrayList<>();
        try (ZipInputStream zipInputStream = new ZipInputStream(new ByteArrayInputStream(zipBytes), StandardCharsets.UTF_8)) {
            ZipEntry entry = zipInputStream.getNextEntry();
            while (entry != null) {
                names.add(entry.getName());
                zipInputStream.closeEntry();
                entry = zipInputStream.getNextEntry();
            }
        }
        return names;
    }
}
