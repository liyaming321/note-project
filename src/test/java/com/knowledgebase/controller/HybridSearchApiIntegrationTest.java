package com.knowledgebase.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/**
 * 混合搜索 API 集成测试。
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class HybridSearchApiIntegrationTest {

    private static final Path TEST_DIRECTORY = Path.of(System.getProperty("java.io.tmpdir"), "people-wiki-hybrid-test");
    private static final Path EXECUTABLE_PATH = TEST_DIRECTORY.resolve("hybrid-embedding.sh");
    private static final Path MODEL_PATH = TEST_DIRECTORY.resolve("model.gguf");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    /**
     * 注册测试 Embedding 配置。
     *
     * @param registry 动态配置注册器
     */
    @DynamicPropertySource
    static void registerEmbeddingProperties(DynamicPropertyRegistry registry) {
        registry.add("knowledge-base.embedding.local-cli.executable-path", () -> prepareEmbeddingExecutable().toString());
        registry.add("knowledge-base.embedding.local-cli.model-path", () -> prepareModelFile().toString());
        registry.add("knowledge-base.embedding.local-cli.batch-size", () -> "2");
        registry.add("knowledge-base.embedding.local-cli.timeout-seconds", () -> "5");
    }

    /**
     * 验证统一搜索入口可按模式分发，并返回混合搜索排序解释。
     *
     * @throws Exception 请求执行异常
     */
    @Test
    void shouldSearchByUnifiedModesAndExplainHybridRanking() throws Exception {
        String suffix = String.valueOf(System.nanoTime());
        long categoryId = createCategory("混合搜索分类" + suffix);
        String javaTag = "hybrid-java-" + suffix;
        long boostedNoteId = createNote(
                "Java 接口混合排序",
                "Java 接口设计、Controller 协作和 Service 分层实践。",
                categoryId,
                List.of(javaTag),
                "java",
                true,
                true
        );
        createNote(
                "Java 接口普通笔记",
                "Java 接口设计的普通记录。",
                categoryId,
                List.of(javaTag),
                "java",
                false,
                false
        );
        createNote(
                "数据库备份恢复",
                "数据库备份、恢复和索引维护记录。",
                categoryId,
                List.of("hybrid-db-" + suffix),
                "sql",
                false,
                false
        );

        rebuildSearchIndexes();

        mockMvc.perform(get("/api/search")
                        .param("mode", "exact")
                        .param("q", "Java 接口")
                        .param("tag", javaTag))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(2))
                .andExpect(jsonPath("$.data.items[0].hitFields").isArray());

        mockMvc.perform(get("/api/search")
                        .param("mode", "semantic")
                        .param("q", "怎么设计 Java 接口")
                        .param("tag", javaTag))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(2))
                .andExpect(jsonPath("$.data.items[0].semanticSimilarity").value(Matchers.greaterThan(0.9)))
                .andExpect(jsonPath("$.data.items[0].matchReason").value(Matchers.containsString("语义向量相近")));

        mockMvc.perform(get("/api/search")
                        .param("mode", "hybrid")
                        .param("q", "Java 接口")
                        .param("tag", javaTag)
                        .param("scope", "all"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(2))
                .andExpect(jsonPath("$.data.items[0].id").value((int) boostedNoteId))
                .andExpect(jsonPath("$.data.items[0].keywordScore").value(Matchers.greaterThan(0.0)))
                .andExpect(jsonPath("$.data.items[0].semanticSimilarity").value(Matchers.greaterThan(0.9)))
                .andExpect(jsonPath("$.data.items[0].hybridScore").value(Matchers.greaterThan(0.0)))
                .andExpect(jsonPath("$.data.items[0].rankExplanation").value(Matchers.containsString("全文得分")))
                .andExpect(jsonPath("$.data.items[0].rankExplanation").value(Matchers.containsString("语义相似度")))
                .andExpect(jsonPath("$.data.items[0].rankExplanation").value(Matchers.containsString("置顶加权")))
                .andExpect(jsonPath("$.data.items[0].rankExplanation").value(Matchers.containsString("收藏加权")));
    }

    /**
     * 验证混合搜索在重建索引后不会返回删除、归档状态的脏结果。
     *
     * @throws Exception 请求执行异常
     */
    @Test
    void shouldKeepHybridSearchConsistentAfterArchiveDeleteRestoreAndReindex() throws Exception {
        String suffix = String.valueOf(System.nanoTime());
        long categoryId = createCategory("混合一致性分类" + suffix);
        String tag = "hybrid-consistency-" + suffix;
        long noteId = createNote(
                "Java 接口一致性验证",
                "Java 接口一致性验证内容，用于检查归档、删除、恢复后的索引同步。",
                categoryId,
                List.of(tag),
                "java",
                false,
                false
        );

        rebuildSearchIndexes();
        assertHybridTotal(tag, 1);

        mockMvc.perform(patch("/api/notes/{id}/archived", noteId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(Map.of("value", true))))
                .andExpect(status().isOk());
        assertHybridTotal(tag, 0);

        mockMvc.perform(patch("/api/notes/{id}/archived", noteId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(Map.of("value", false))))
                .andExpect(status().isOk());
        assertHybridTotal(tag, 1);

        mockMvc.perform(delete("/api/notes/{id}", noteId))
                .andExpect(status().isOk());
        assertHybridTotal(tag, 0);

        mockMvc.perform(post("/api/notes/{id}/restore", noteId))
                .andExpect(status().isOk());
        assertHybridTotal(tag, 1);

        rebuildSearchIndexes();
        assertHybridTotal(tag, 1);
    }

    /**
     * 准备模拟 Embedding 命令。
     *
     * @return 命令路径
     */
    private static Path prepareEmbeddingExecutable() {
        try {
            Files.createDirectories(TEST_DIRECTORY);
            Files.writeString(EXECUTABLE_PATH, """
                #!/usr/bin/env bash
                prompt=""
                separator=""
                previous=""
                for argument in "$@"; do
                  if [ "$previous" = "-p" ]; then
                    prompt="$argument"
                  fi
                  if [ "$previous" = "--embd-separator" ]; then
                    separator="$argument"
                  fi
                  previous="$argument"
                done
                if [ -z "$separator" ]; then
                  separator=$'\\n<|knowledge-base-embedding-separator|>\\n'
                fi
                normalized="${prompt//$separator/$'\\x1f'}"
                IFS=$'\\x1f' read -ra texts <<< "$normalized"
                printf '['
                first=1
                for text in "${texts[@]}"; do
                  if [ "$first" -eq 0 ]; then
                    printf ','
                  fi
                  first=0
                  if [[ "$text" == *"Java"* || "$text" == *"java"* || "$text" == *"接口"* ]]; then
                    printf '[1,0,0,0,0,0,0,0]'
                  elif [[ "$text" == *"数据库"* || "$text" == *"备份"* || "$text" == *"恢复"* ]]; then
                    printf '[0,1,0,0,0,0,0,0]'
                  else
                    printf '[0,0,1,0,0,0,0,0]'
                  fi
                done
                printf ']'
                """);
            Files.setPosixFilePermissions(
                    EXECUTABLE_PATH,
                    Set.of(
                            java.nio.file.attribute.PosixFilePermission.OWNER_READ,
                            java.nio.file.attribute.PosixFilePermission.OWNER_WRITE,
                            java.nio.file.attribute.PosixFilePermission.OWNER_EXECUTE
                    )
            );
            return EXECUTABLE_PATH;
        } catch (Exception ex) {
            throw new IllegalStateException("准备混合搜索测试脚本失败", ex);
        }
    }

    /**
     * 准备模拟模型文件。
     *
     * @return 模型路径
     */
    private static Path prepareModelFile() {
        try {
            Files.createDirectories(TEST_DIRECTORY);
            Files.writeString(MODEL_PATH, "fake-model");
            return MODEL_PATH;
        } catch (Exception ex) {
            throw new IllegalStateException("准备混合搜索测试模型失败", ex);
        }
    }

    /**
     * 重建全文索引和向量索引。
     *
     * @throws Exception 请求执行异常
     */
    private void rebuildSearchIndexes() throws Exception {
        mockMvc.perform(post("/api/admin/reindex"))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/admin/vector-index/rebuild"))
                .andExpect(status().isOk());
    }

    /**
     * 断言混合搜索命中数量。
     *
     * @param tag 标签筛选
     * @param expectedTotal 预期数量
     * @throws Exception 请求执行异常
     */
    private void assertHybridTotal(String tag, int expectedTotal) throws Exception {
        mockMvc.perform(get("/api/search")
                        .param("mode", "hybrid")
                        .param("q", "Java 接口")
                        .param("tag", tag))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(expectedTotal));
    }

    /**
     * 创建测试分类。
     *
     * @param name 分类名
     * @return 分类ID
     * @throws Exception 请求执行异常
     */
    private long createCategory(String name) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/categories")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(Map.of("name", name))))
                .andExpect(status().isCreated())
                .andReturn();
        return readDataId(result);
    }

    /**
     * 创建测试笔记。
     *
     * @param title 标题
     * @param content 内容
     * @param categoryId 分类ID
     * @param tags 标签
     * @param language 语言
     * @param pinned 是否置顶
     * @param favorite 是否收藏
     * @return 笔记ID
     * @throws Exception 请求执行异常
     */
    private long createNote(
            String title,
            String content,
            long categoryId,
            List<String> tags,
            String language,
            boolean pinned,
            boolean favorite
    ) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/notes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(Map.of(
                                "title", title,
                                "content", content,
                                "type", "MARKDOWN",
                                "categoryId", categoryId,
                                "tags", tags,
                                "language", language,
                                "status", "PUBLISHED",
                                "pinned", pinned,
                                "favorite", favorite
                        ))))
                .andExpect(status().isCreated())
                .andReturn();
        return readDataId(result);
    }

    /**
     * 读取响应 data.id。
     *
     * @param result MVC 响应
     * @return ID
     * @throws Exception JSON 解析异常
     */
    private long readDataId(MvcResult result) throws Exception {
        JsonNode node = objectMapper.readTree(result.getResponse().getContentAsString());
        return node.path("data").path("id").asLong();
    }

    /**
     * 转换为 JSON。
     *
     * @param value 原始对象
     * @return JSON
     * @throws Exception JSON 序列化异常
     */
    private String toJson(Object value) throws Exception {
        return objectMapper.writeValueAsString(value);
    }
}
