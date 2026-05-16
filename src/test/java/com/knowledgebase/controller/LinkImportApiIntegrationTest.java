package com.knowledgebase.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
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

/**
 * 链接导入 API 集成测试。
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class LinkImportApiIntegrationTest {

    private static final TestHttpServer TEST_SERVER = TestHttpServer.start();

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
    static void registerLlmProperties(DynamicPropertyRegistry registry) {
        registry.add("knowledge-base.llm.deepseek.api-key", () -> "test-api-key");
        registry.add("knowledge-base.llm.deepseek.base-url", TEST_SERVER::baseUrl);
        registry.add("knowledge-base.llm.deepseek.model", () -> "test-chat-model");
    }

    /**
     * 关闭测试 HTTP 服务。
     */
    @AfterAll
    static void stopServer() {
        TEST_SERVER.stop();
    }

    /**
     * 验证链接导入会生成新建笔记预览，且不会直接写入数据库。
     *
     * @throws Exception 请求执行异常
     */
    @Test
    void shouldPreviewLinkImportWithoutPersistingNote() throws Exception {
        mockMvc.perform(post("/api/categories")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(Map.of("name", "链接资料"))))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/import/link")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(Map.of(
                                "url", TEST_SERVER.pageUrl(),
                                "provider", "deepseek"
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.sourceUrl").value(TEST_SERVER.pageUrl()))
                .andExpect(jsonPath("$.data.sourceTitle").value("原始网页标题"))
                .andExpect(jsonPath("$.data.provider").value("deepseek"))
                .andExpect(jsonPath("$.data.model").value("test-chat-model"))
                .andExpect(jsonPath("$.data.title").value("AI整理的链接笔记"))
                .andExpect(jsonPath("$.data.summary").value("这是由测试模型整理出的网页摘要"))
                .andExpect(jsonPath("$.data.tags", Matchers.hasItems("链接导入", "网页整理")))
                .andExpect(jsonPath("$.data.categoryName").value("链接资料"))
                .andExpect(jsonPath("$.data.categoryId").isNumber())
                .andExpect(jsonPath("$.data.content").value(Matchers.containsString("来源链接")))
                .andExpect(jsonPath("$.data.content").value(Matchers.containsString("网页正文摘录")))
                .andExpect(jsonPath("$.data.content").value(Matchers.containsString("网页正文第一段")));

        mockMvc.perform(get("/api/notes").param("tag", "链接导入"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(0));
    }

    /**
     * 验证非 HTTP 链接会被拒绝。
     *
     * @throws Exception 请求执行异常
     */
    @Test
    void shouldRejectUnsupportedLinkScheme() throws Exception {
        mockMvc.perform(post("/api/import/link")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(Map.of(
                                "url", "file:///tmp/local.md",
                                "provider", "deepseek"
                        ))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value(Matchers.containsString("http")));
    }

    /**
     * 验证批量链接导入会返回逐条成功与失败结果。
     *
     * @throws Exception 请求执行异常
     */
    @Test
    void shouldPreviewBatchLinkImportWithItemFailures() throws Exception {
        mockMvc.perform(post("/api/import/links")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(Map.of(
                                "urls", java.util.List.of(TEST_SERVER.pageUrl(), "file:///tmp/local.md"),
                                "provider", "deepseek"
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalCount").value(2))
                .andExpect(jsonPath("$.data.successCount").value(1))
                .andExpect(jsonPath("$.data.failedCount").value(1))
                .andExpect(jsonPath("$.data.items[0].success").value(true))
                .andExpect(jsonPath("$.data.items[0].preview.title").value("AI整理的链接笔记"))
                .andExpect(jsonPath("$.data.items[1].success").value(false))
                .andExpect(jsonPath("$.data.items[1].message").value(Matchers.containsString("http")));
    }

    /**
     * 验证关闭 LLM 后仍可生成纯网页抓取预览。
     *
     * @throws Exception 请求执行异常
     */
    @Test
    void shouldPreviewLinkImportWithoutLlm() throws Exception {
        mockMvc.perform(post("/api/import/link")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(Map.of(
                                "url", TEST_SERVER.pageUrl(),
                                "provider", "deepseek",
                                "useLlm", false
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.provider").value("crawler"))
                .andExpect(jsonPath("$.data.model").value("jsoup"))
                .andExpect(jsonPath("$.data.title").value("原始网页标题"))
                .andExpect(jsonPath("$.data.summary").value(Matchers.containsString("网页正文第一段")))
                .andExpect(jsonPath("$.data.tags", Matchers.hasItems("链接导入", "网页摘录")))
                .andExpect(jsonPath("$.data.content").value(Matchers.containsString("网页正文摘录")));
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
     * 测试 HTTP 服务。
     *
     * @param server HTTP 服务
     */
    private record TestHttpServer(HttpServer server) {

        /**
         * 启动测试 HTTP 服务。
         *
         * @return 测试 HTTP 服务
         */
        static TestHttpServer start() {
            try {
                HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
                TestHttpServer testServer = new TestHttpServer(server);
                server.createContext("/page", testServer::handlePage);
                server.createContext("/chat/completions", testServer::handleChatCompletions);
                server.start();
                return testServer;
            } catch (IOException ex) {
                throw new IllegalStateException("启动链接导入测试服务失败", ex);
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
         * 获取测试页面地址。
         *
         * @return 页面地址
         */
        String pageUrl() {
            return baseUrl() + "/page";
        }

        /**
         * 停止服务。
         */
        void stop() {
            server.stop(0);
        }

        /**
         * 返回测试网页。
         *
         * @param exchange HTTP 交换
         * @throws IOException 写入异常
         */
        void handlePage(HttpExchange exchange) throws IOException {
            String html = """
                    <!doctype html>
                    <html>
                      <head>
                        <title>原始网页标题</title>
                        <meta property="og:title" content="原始网页标题" />
                      </head>
                      <body>
                        <nav>导航内容不应进入正文</nav>
                        <article>
                          <h1>网页正文标题</h1>
                          <p>网页正文第一段，介绍链接导入功能。</p>
                          <p>网页正文第二段，说明会进入新建笔记预览。</p>
                        </article>
                      </body>
                    </html>
                    """;
            writeResponse(exchange, "text/html; charset=utf-8", html);
        }

        /**
         * 返回模拟 LLM 响应。
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
                            "content": "{\\"title\\":\\"AI整理的链接笔记\\",\\"summary\\":\\"这是由测试模型整理出的网页摘要\\",\\"tags\\":[\\"链接导入\\",\\"网页整理\\"],\\"categoryName\\":\\"链接资料\\"}"
                          }
                        }
                      ]
                    }
                    """;
            writeResponse(exchange, "application/json; charset=utf-8", response);
        }

        /**
         * 写入 HTTP 响应。
         *
         * @param exchange HTTP 交换
         * @param contentType 内容类型
         * @param body 响应体
         * @throws IOException 写入异常
         */
        private void writeResponse(HttpExchange exchange, String contentType, String body) throws IOException {
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", contentType);
            exchange.sendResponseHeaders(200, bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
        }
    }
}
