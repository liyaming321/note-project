package com.knowledgebase.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.AfterAll;
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
 * 第八阶段体验、稳定性与性能优化 API 集成测试。
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class StageEightApiIntegrationTest {

    private static final TestLlmServer TEST_SERVER = TestLlmServer.start();

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    /**
     * 注册测试 LLM 配置。
     *
     * @param registry 动态配置注册器
     */
    @DynamicPropertySource
    static void registerProperties(DynamicPropertyRegistry registry) {
        registry.add("knowledge-base.llm.deepseek.api-key", () -> "test-api-key");
        registry.add("knowledge-base.llm.deepseek.base-url", TEST_SERVER::baseUrl);
        registry.add("knowledge-base.llm.deepseek.model", () -> "stage-eight-chat-model");
    }

    /**
     * 停止测试 LLM 服务。
     */
    @AfterAll
    static void stopServer() {
        TEST_SERVER.stop();
    }

    /**
     * 验证搜索调优配置与搜索反馈记录。
     *
     * @throws Exception 请求执行异常
     */
    @Test
    void shouldTuneSearchWeightsAndRecordFeedback() throws Exception {
        long noteId = createNote("中文短词 API", "API 调试、接口搜索和代码片段质量回归。", List.of("stage-eight-search"));

        mockMvc.perform(put("/api/admin/search-tuning")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(Map.of(
                                "keywordWeight", 0.8,
                                "semanticWeight", 0.2,
                                "titleHitBoost", 0.12,
                                "tagHitBoost", 0.07,
                                "pinnedBoost", 0.05,
                                "favoriteBoost", 0.04,
                                "recentSevenDaysBoost", 0.02,
                                "recentThirtyDaysBoost", 0.01
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.keywordWeight").value(0.8))
                .andExpect(jsonPath("$.data.semanticWeight").value(0.2))
                .andExpect(jsonPath("$.data.configPath").isNotEmpty());

        mockMvc.perform(get("/api/search")
                        .param("mode", "hybrid")
                        .param("q", "API")
                        .param("tag", "stage-eight-search"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(1))
                .andExpect(jsonPath("$.data.items[0].rankExplanation").value(Matchers.containsString("KNOWLEDGE_BASE_EMBEDDING")));

        mockMvc.perform(post("/api/search/feedback")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(Map.of(
                                "noteId", noteId,
                                "keyword", "API",
                                "mode", "hybrid",
                                "useful", true,
                                "reason", "中文短词命中准确"
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalCount").value(Matchers.greaterThanOrEqualTo(1)))
                .andExpect(jsonPath("$.data.usefulCount").value(Matchers.greaterThanOrEqualTo(1)));

        mockMvc.perform(get("/api/admin/search-feedback-summary"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.recentItems[0].noteId").value((int) noteId))
                .andExpect(jsonPath("$.data.recentItems[0].useful").value(true));
    }

    /**
     * 验证知识库问答支持追问上下文、引用筛选、严格模式和连接测试。
     *
     * @throws Exception 请求执行异常
     */
    @Test
    void shouldAskWithContextCitationFilterStrictModeAndTestLlm() throws Exception {
        long selectedNoteId = createNote(
                "RAG 严格引用",
                "严格模式要求答案只能来自引用，引用不足时要说明缺少信息。",
                List.of("stage-eight-qa")
        );
        createNote("无关问答资料", "这是一段不会被引用筛选选中的资料。", List.of("stage-eight-qa"));

        mockMvc.perform(post("/api/knowledge-qa")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(Map.of(
                                "question", "严格模式怎么回答？",
                                "provider", "deepseek",
                                "topK", 3,
                                "tag", "stage-eight-qa",
                                "conversationContext", List.of("上一轮：用户问过 RAG。"),
                                "citationNoteIds", List.of(selectedNoteId),
                                "strictMode", true
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.answer").value(Matchers.containsString("测试回答")))
                .andExpect(jsonPath("$.data.provider").value("deepseek"))
                .andExpect(jsonPath("$.data.model").value("stage-eight-chat-model"))
                .andExpect(jsonPath("$.data.citations", Matchers.hasSize(1)))
                .andExpect(jsonPath("$.data.citations[0].noteId").value((int) selectedNoteId));

        mockMvc.perform(post("/api/admin/llm-providers/deepseek/test"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.success").value(true))
                .andExpect(jsonPath("$.data.model").value("stage-eight-chat-model"));
    }

    /**
     * 验证知识整理候选、配置检查和备份健康信息。
     *
     * @throws Exception 请求执行异常
     */
    @Test
    void shouldExposeOrganizeCandidatesConfigurationChecklistAndBackupInfo() throws Exception {
        long noteId = createNote(
                "待整理链接资料",
                "来源链接：https://example.com\n网页正文摘录：这是一段缺少摘要和标签的资料。",
                List.of()
        );

        mockMvc.perform(get("/api/admin/organize-candidates"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(Matchers.greaterThanOrEqualTo(1)))
                .andExpect(jsonPath("$.data.items[*].note.id").value(Matchers.hasItem((int) noteId)))
                .andExpect(jsonPath("$.data.items[*].suggestedTags").isArray());

        mockMvc.perform(get("/api/admin/configuration-checklist"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.offlineReady").isBoolean())
                .andExpect(jsonPath("$.data.llmReady").value(true))
                .andExpect(jsonPath("$.data.items").isArray());

        mockMvc.perform(get("/api/admin/backup-info"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.healthy").isBoolean())
                .andExpect(jsonPath("$.data.message").isNotEmpty());

        mockMvc.perform(get("/api/admin/backup"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/admin/backup-info"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.lastBackupFileName").value(Matchers.containsString("knowledge-base-backup")))
                .andExpect(jsonPath("$.data.lastBackupSize").value(Matchers.greaterThan(0)))
                .andExpect(jsonPath("$.data.lastBackupChecksum").isNotEmpty());
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
    private long createNote(String title, String content, List<String> tags) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/notes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(Map.of(
                                "title", title,
                                "content", content,
                                "type", "MARKDOWN",
                                "tags", tags,
                                "status", "PUBLISHED"
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
     * 转换 JSON。
     *
     * @param value 原始对象
     * @return JSON 字符串
     * @throws Exception 序列化异常
     */
    private String toJson(Object value) throws Exception {
        return objectMapper.writeValueAsString(value);
    }

    /**
     * 测试 LLM HTTP 服务。
     *
     * @param server HTTP 服务
     */
    private record TestLlmServer(HttpServer server) {

        /**
         * 启动测试 LLM 服务。
         *
         * @return 测试服务
         */
        static TestLlmServer start() {
            try {
                HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
                TestLlmServer testServer = new TestLlmServer(server);
                server.createContext("/chat/completions", testServer::handleChatCompletions);
                server.start();
                return testServer;
            } catch (IOException ex) {
                throw new IllegalStateException("启动第八阶段测试 LLM 服务失败", ex);
            }
        }

        /**
         * 获取基础地址。
         *
         * @return 基础地址
         */
        String baseUrl() {
            return "http://127.0.0.1:" + server.getAddress().getPort();
        }

        /**
         * 停止服务。
         */
        void stop() {
            server.stop(0);
        }

        /**
         * 返回模拟 Chat Completions 响应。
         *
         * @param exchange HTTP 交换
         * @throws IOException 写入异常
         */
        void handleChatCompletions(HttpExchange exchange) throws IOException {
            String response = """
                    {
                      "choices": [
                        {
                          "message": {
                            "content": "测试回答：严格模式需要基于引用回答。[1]\\n参考来源：[1]"
                          }
                        }
                      ]
                    }
                    """;
            byte[] bytes = response.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json; charset=utf-8");
            exchange.sendResponseHeaders(200, bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
        }
    }
}
