package com.knowledgebase.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;
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
 * 语义搜索 API 集成测试。
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SemanticSearchApiIntegrationTest {

    private static final Path TEST_DIRECTORY = Path.of(System.getProperty("java.io.tmpdir"), "people-wiki-semantic-test");
    private static final Path EXECUTABLE_PATH = TEST_DIRECTORY.resolve("semantic-embedding.sh");
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
            throw new IllegalStateException("准备语义搜索测试脚本失败", ex);
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
            throw new IllegalStateException("准备语义搜索测试模型失败", ex);
        }
    }

    /**
     * 验证语义搜索正常命中、筛选组合和分页边界。
     *
     * @throws Exception 请求执行异常
     */
    @Test
    void shouldSearchSemanticallyWithFiltersAndPagination() throws Exception {
        long categoryId = createCategory("语义搜索分类");
        long javaNoteId = createNote(
                "Java 接口设计",
                "这篇笔记记录 Java Service 接口设计和控制器协作。",
                categoryId,
                List.of("semantic-java"),
                "java",
                "PUBLISHED"
        );
        createNote(
                "数据库备份恢复",
                "这篇笔记记录数据库备份、恢复和索引维护。",
                categoryId,
                List.of("semantic-db"),
                "sql",
                "DRAFT"
        );

        mockMvc.perform(post("/api/admin/vector-index/rebuild"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.indexedCount").value(org.hamcrest.Matchers.greaterThanOrEqualTo(2)));

        mockMvc.perform(get("/api/search/semantic")
                        .param("q", "怎么设计 Java 接口")
                        .param("tag", "semantic-java")
                        .param("category", String.valueOf(categoryId))
                        .param("language", "java")
                        .param("status", "PUBLISHED")
                        .param("updatedFrom", LocalDate.now().toString())
                        .param("updatedTo", LocalDate.now().toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(1))
                .andExpect(jsonPath("$.data.items[0].id").value(javaNoteId))
                .andExpect(jsonPath("$.data.items[0].semanticSimilarity").value(org.hamcrest.Matchers.greaterThan(0.9)))
                .andExpect(jsonPath("$.data.items[0].matchReason").value(org.hamcrest.Matchers.containsString("语义向量相近")))
                .andExpect(jsonPath("$.data.items[0].hitFields[0]").value("semantic"));

        mockMvc.perform(get("/api/search/semantic")
                        .param("q", "怎么设计 Java 接口")
                        .param("tag", "semantic-db"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(1))
                .andExpect(jsonPath("$.data.items[0].title").value("数据库备份恢复"));

        mockMvc.perform(get("/api/search/semantic")
                        .param("q", "怎么设计 Java 接口")
                        .param("size", "1")
                        .param("page", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.size").value(1))
                .andExpect(jsonPath("$.data.page").value(1));
    }

    /**
     * 验证语义搜索空索引返回空分页。
     *
     * @throws Exception 请求执行异常
     */
    @Test
    void shouldReturnEmptyPageWhenVectorIndexIsEmpty() throws Exception {
        mockMvc.perform(get("/api/search/semantic")
                        .param("q", "还没有索引的语义问题"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(0))
                .andExpect(jsonPath("$.data.items").isArray());
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
     * @param status 发布状态
     * @return 笔记ID
     * @throws Exception 请求执行异常
     */
    private long createNote(
            String title,
            String content,
            long categoryId,
            List<String> tags,
            String language,
            String status
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
                                "status", status
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
