package com.knowledgebase.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.knowledgebase.config.KnowledgeBaseProperties;
import com.knowledgebase.config.KnowledgeBaseProperties.LocalCliEmbeddingProperties;
import com.knowledgebase.exception.BusinessException;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;

/**
 * 基于 llama.cpp 命令行工具的本地 Embedding 供应商。
 */
@Service
public class LocalCliEmbeddingProvider implements EmbeddingProvider {

    private static final Pattern FLOAT_PATTERN = Pattern.compile("[-+]?(?:\\d+\\.\\d+|\\d+|\\.\\d+)(?:[eE][-+]?\\d+)?");
    private static final int MIN_VECTOR_DIMENSION = 8;

    private final KnowledgeBaseProperties properties;
    private final ObjectMapper objectMapper;

    /**
     * 创建本地命令行 Embedding 供应商。
     *
     * @param properties 知识库配置
     * @param objectMapper JSON 工具
     */
    public LocalCliEmbeddingProvider(KnowledgeBaseProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    /**
     * 获取供应商名称。
     *
     * @return 供应商名称
     */
    @Override
    public String name() {
        return "local-cli";
    }

    /**
     * 获取模型路径。
     *
     * @return 模型路径
     */
    @Override
    public String model() {
        return safeText(config().getModelPath());
    }

    /**
     * 获取池化方式。
     *
     * @return 池化方式
     */
    @Override
    public String pooling() {
        String pooling = safeText(config().getPooling());
        return pooling.isBlank() ? "mean" : pooling;
    }

    /**
     * 是否对向量做归一化。
     *
     * @return 是否归一化
     */
    @Override
    public boolean normalize() {
        return config().isNormalize();
    }

    /**
     * 判断本地命令行供应商是否已配置。
     *
     * @return 是否已配置
     */
    @Override
    public boolean configured() {
        return configurationProblem().isEmpty();
    }

    /**
     * 获取配置状态说明。
     *
     * @return 配置状态说明
     */
    @Override
    public String statusMessage() {
        return configurationProblem().orElse("local-cli Embedding 已配置");
    }

    /**
     * 生成文本向量。
     *
     * @param text 文本
     * @return 向量
     */
    @Override
    public float[] embed(String text) {
        return embedAll(List.of(text)).get(0);
    }

    /**
     * 批量生成文本向量。
     *
     * @param texts 文本列表
     * @return 向量列表
     */
    @Override
    public List<float[]> embedAll(List<String> texts) {
        validateConfigured();
        List<String> safeTexts = texts.stream()
                .map(this::safeText)
                .toList();
        if (safeTexts.isEmpty() || safeTexts.stream().anyMatch(String::isBlank)) {
            throw new BusinessException("Embedding 文本不能为空");
        }
        ProcessResult result = runEmbeddingCommand(safeTexts);
        List<float[]> vectors = parseVectors(result.output());
        if (vectors.size() != safeTexts.size()) {
            if (safeTexts.size() == 1) {
                throw new BusinessException("local-cli Embedding 返回向量数量与输入文本数量不一致");
            }
            vectors = safeTexts.stream()
                    .map(this::embedSingleText)
                    .toList();
        }
        if (!normalize()) {
            return vectors;
        }
        return vectors.stream().map(this::normalizeVector).toList();
    }

    /**
     * 生成单条文本向量。
     *
     * @param text 文本
     * @return 向量
     */
    private float[] embedSingleText(String text) {
        ProcessResult result = runEmbeddingCommand(List.of(text));
        List<float[]> vectors = parseVectors(result.output());
        if (vectors.size() != 1) {
            throw new BusinessException("local-cli Embedding 返回向量数量与输入文本数量不一致");
        }
        return vectors.get(0);
    }

    /**
     * 执行 llama-embedding 命令。
     *
     * @param texts 文本列表
     * @return 命令执行结果
     */
    private ProcessResult runEmbeddingCommand(List<String> texts) {
        LocalCliEmbeddingProperties config = config();
        String separator = batchSeparator();
        List<String> command = new ArrayList<>();
        command.add(config.getExecutablePath().trim());
        command.add("-m");
        command.add(config.getModelPath().trim());
        command.add("--pooling");
        command.add(pooling());
        command.add("--embd-output-format");
        command.add("array");
        command.add("--embd-separator");
        command.add(separator);
        command.add("--log-verbosity");
        command.add("1");
        command.add("-p");
        command.add(String.join(separator, texts));
        try {
            Process process = new ProcessBuilder(command).start();
            StreamReader stdoutReader = StreamReader.start(process.getInputStream());
            StreamReader stderrReader = StreamReader.start(process.getErrorStream());
            boolean completed = process.waitFor(timeoutSeconds(), TimeUnit.SECONDS);
            if (!completed) {
                process.destroyForcibly();
                throw new BusinessException("local-cli Embedding 执行超时，请检查模型大小或超时配置");
            }
            String stdout = stdoutReader.read();
            String stderr = stderrReader.read();
            if (process.exitValue() != 0) {
                throw new BusinessException("local-cli Embedding 执行失败：" + limitText(stderr.isBlank() ? stdout : stderr, 800));
            }
            return new ProcessResult(stdout, stderr);
        } catch (IOException ex) {
            throw new BusinessException("local-cli Embedding 启动失败：" + ex.getMessage());
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new BusinessException("local-cli Embedding 执行被中断");
        }
    }

    /**
     * 解析命令输出中的向量。
     *
     * @param output 命令输出
     * @return 向量
     */
    private List<float[]> parseVectors(String output) {
        String safeOutput = safeText(output);
        List<float[]> jsonVectors = parseJsonVectors(safeOutput);
        if (!jsonVectors.isEmpty()) {
            return jsonVectors;
        }
        float[] vector = parsePlainVector(safeOutput);
        if (vector.length < MIN_VECTOR_DIMENSION) {
            throw new BusinessException("local-cli Embedding 未返回有效向量，请检查 llama-embedding 输出格式");
        }
        return List.of(vector);
    }

    /**
     * 从 JSON 输出中解析向量列表。
     *
     * @param output 命令输出
     * @return 向量列表
     */
    private List<float[]> parseJsonVectors(String output) {
        if (!output.startsWith("{") && !output.startsWith("[")) {
            return List.of();
        }
        try {
            JsonNode rootNode = objectMapper.readTree(output);
            List<JsonNode> vectorNodes = new ArrayList<>();
            collectVectorNodes(rootNode, vectorNodes);
            return vectorNodes.stream().map(this::toVector).toList();
        } catch (IOException ex) {
            return List.of();
        }
    }

    /**
     * 收集 JSON 中的数字数组。
     *
     * @param node JSON 节点
     * @param vectorNodes 数字数组节点列表
     */
    private void collectVectorNodes(JsonNode node, List<JsonNode> vectorNodes) {
        if (node == null || node.isMissingNode()) {
            return;
        }
        if (isNumberArray(node)) {
            vectorNodes.add(node);
            return;
        }
        if (node.isArray() || node.isObject()) {
            for (JsonNode childNode : node) {
                collectVectorNodes(childNode, vectorNodes);
            }
        }
    }

    /**
     * 判断节点是否是数字数组。
     *
     * @param node JSON 节点
     * @return 是否是数字数组
     */
    private boolean isNumberArray(JsonNode node) {
        if (!node.isArray() || node.size() < MIN_VECTOR_DIMENSION) {
            return false;
        }
        for (JsonNode childNode : node) {
            if (!childNode.isNumber()) {
                return false;
            }
        }
        return true;
    }

    /**
     * 将 JSON 数组转换为向量。
     *
     * @param node JSON 数组
     * @return 向量
     */
    private float[] toVector(JsonNode node) {
        float[] vector = new float[node.size()];
        for (int index = 0; index < node.size(); index++) {
            vector[index] = (float) node.get(index).asDouble();
        }
        return vector;
    }

    /**
     * 从纯文本输出中提取浮点数。
     *
     * @param output 命令输出
     * @return 向量
     */
    private float[] parsePlainVector(String output) {
        Matcher matcher = FLOAT_PATTERN.matcher(output);
        List<Float> values = new ArrayList<>();
        while (matcher.find()) {
            values.add(Float.parseFloat(matcher.group()));
        }
        float[] vector = new float[values.size()];
        for (int index = 0; index < values.size(); index++) {
            vector[index] = values.get(index);
        }
        return vector;
    }

    /**
     * 对向量做 L2 归一化。
     *
     * @param vector 原始向量
     * @return 归一化向量
     */
    private float[] normalizeVector(float[] vector) {
        double sum = 0D;
        for (float value : vector) {
            sum += value * value;
        }
        if (sum <= 0D) {
            return vector;
        }
        double norm = Math.sqrt(sum);
        float[] normalizedVector = new float[vector.length];
        for (int index = 0; index < vector.length; index++) {
            normalizedVector[index] = (float) (vector[index] / norm);
        }
        return normalizedVector;
    }

    /**
     * 校验供应商配置。
     */
    private void validateConfigured() {
        configurationProblem().ifPresent(message -> {
            throw new BusinessException(message);
        });
    }

    /**
     * 获取配置问题。
     *
     * @return 配置问题
     */
    private Optional<String> configurationProblem() {
        LocalCliEmbeddingProperties config = config();
        String executablePath = safeText(config.getExecutablePath());
        String modelPath = safeText(config.getModelPath());
        if (executablePath.isBlank()) {
            return Optional.of("请配置 KNOWLEDGE_BASE_EMBEDDING_LOCAL_CLI_EXECUTABLE");
        }
        if (modelPath.isBlank()) {
            return Optional.of("请配置 KNOWLEDGE_BASE_EMBEDDING_LOCAL_CLI_MODEL");
        }
        if (Files.notExists(Path.of(executablePath))) {
            return Optional.of("llama-embedding 可执行文件不存在：" + executablePath);
        }
        if (Files.notExists(Path.of(modelPath))) {
            return Optional.of("GGUF Embedding 模型文件不存在：" + modelPath);
        }
        return Optional.empty();
    }

    /**
     * 获取本地命令行配置。
     *
     * @return 本地命令行配置
     */
    private LocalCliEmbeddingProperties config() {
        return properties.getEmbedding().getLocalCli();
    }

    /**
     * 获取命令超时时间。
     *
     * @return 超时时间
     */
    private long timeoutSeconds() {
        return Math.max(config().getTimeoutSeconds(), 1);
    }

    /**
     * 获取批处理数量。
     *
     * @return 批处理数量
     */
    @Override
    public int batchSize() {
        return Math.max(config().getBatchSize(), 1);
    }

    /**
     * 获取批处理分隔符。
     *
     * @return 批处理分隔符
     */
    String batchSeparator() {
        return "\n<|knowledge-base-embedding-separator|>\n";
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

    /**
     * 截断文本。
     *
     * @param value 原始文本
     * @param maxLength 最大长度
     * @return 截断文本
     */
    private String limitText(String value, int maxLength) {
        String safeValue = safeText(value);
        return safeValue.length() <= maxLength ? safeValue : safeValue.substring(0, maxLength);
    }

    /**
     * 子进程输出读取器。
     *
     * @param thread 读取线程
     * @param buffer 输出缓冲区
     */
    private record StreamReader(Thread thread, ByteArrayOutputStream buffer) {

        /**
         * 启动输出读取线程。
         *
         * @param inputStream 输入流
         * @return 输出读取器
         */
        private static StreamReader start(java.io.InputStream inputStream) {
            ByteArrayOutputStream buffer = new ByteArrayOutputStream();
            Thread thread = new Thread(() -> {
                try (inputStream) {
                    inputStream.transferTo(buffer);
                } catch (IOException ex) {
                    // 读取子进程输出失败时，由主流程根据退出码返回更明确的错误。
                }
            });
            thread.start();
            return new StreamReader(thread, buffer);
        }

        /**
         * 读取完整输出。
         *
         * @return 输出文本
         */
        private String read() {
            try {
                thread.join();
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                throw new BusinessException("local-cli Embedding 输出读取被中断");
            }
            return buffer.toString(StandardCharsets.UTF_8);
        }
    }

    /**
     * 命令执行结果。
     *
     * @param output 标准输出
     * @param errorOutput 错误输出
     */
    private record ProcessResult(String output, String errorOutput) {
    }
}
