package com.knowledgebase.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 知识库本地运行配置。
 */
@ConfigurationProperties(prefix = "knowledge-base")
public class KnowledgeBaseProperties {

    private String dataPath;
    private String indexPath;
    private String vectorIndexPath;
    private String imagesPath;
    private String restoreBackupPath;
    private int historyMaxVersions = 50;
    private LlmProperties llm = new LlmProperties();
    private EmbeddingProperties embedding = new EmbeddingProperties();

    /**
     * 获取 H2 数据文件路径。
     *
     * @return H2 数据文件路径
     */
    public String getDataPath() {
        return dataPath;
    }

    /**
     * 设置 H2 数据文件路径。
     *
     * @param dataPath H2 数据文件路径
     */
    public void setDataPath(String dataPath) {
        this.dataPath = dataPath;
    }

    /**
     * 获取 Lucene 索引目录。
     *
     * @return 索引目录
     */
    public String getIndexPath() {
        return indexPath;
    }

    /**
     * 设置 Lucene 索引目录。
     *
     * @param indexPath 索引目录
     */
    public void setIndexPath(String indexPath) {
        this.indexPath = indexPath;
    }

    /**
     * 获取 Lucene 向量索引目录。
     *
     * @return 向量索引目录
     */
    public String getVectorIndexPath() {
        return vectorIndexPath;
    }

    /**
     * 设置 Lucene 向量索引目录。
     *
     * @param vectorIndexPath 向量索引目录
     */
    public void setVectorIndexPath(String vectorIndexPath) {
        this.vectorIndexPath = vectorIndexPath;
    }

    /**
     * 获取本地图片存储目录。
     *
     * @return 图片存储目录
     */
    public String getImagesPath() {
        return imagesPath;
    }

    /**
     * 设置本地图片存储目录。
     *
     * @param imagesPath 图片存储目录
     */
    public void setImagesPath(String imagesPath) {
        this.imagesPath = imagesPath;
    }

    /**
     * 获取启动期恢复备份文件路径。
     *
     * @return 备份文件路径
     */
    public String getRestoreBackupPath() {
        return restoreBackupPath;
    }

    /**
     * 设置启动期恢复备份文件路径。
     *
     * @param restoreBackupPath 备份文件路径
     */
    public void setRestoreBackupPath(String restoreBackupPath) {
        this.restoreBackupPath = restoreBackupPath;
    }

    /**
     * 获取历史版本最大保留数量。
     *
     * @return 历史版本最大保留数量
     */
    public int getHistoryMaxVersions() {
        return historyMaxVersions;
    }

    /**
     * 设置历史版本最大保留数量。
     *
     * @param historyMaxVersions 历史版本最大保留数量
     */
    public void setHistoryMaxVersions(int historyMaxVersions) {
        this.historyMaxVersions = historyMaxVersions;
    }

    /**
     * 获取 LLM 总结配置。
     *
     * @return LLM 总结配置
     */
    public LlmProperties getLlm() {
        return llm;
    }

    /**
     * 设置 LLM 总结配置。
     *
     * @param llm LLM 总结配置
     */
    public void setLlm(LlmProperties llm) {
        this.llm = llm == null ? new LlmProperties() : llm;
    }

    /**
     * 获取 Embedding 配置。
     *
     * @return Embedding 配置
     */
    public EmbeddingProperties getEmbedding() {
        return embedding;
    }

    /**
     * 设置 Embedding 配置。
     *
     * @param embedding Embedding 配置
     */
    public void setEmbedding(EmbeddingProperties embedding) {
        this.embedding = embedding == null ? new EmbeddingProperties() : embedding;
    }

    /**
     * Embedding 向量化配置。
     */
    public static class EmbeddingProperties {

        private String provider = "local-cli";
        private LocalCliEmbeddingProperties localCli = new LocalCliEmbeddingProperties();

        /**
         * 获取默认 Embedding 供应商。
         *
         * @return 默认供应商
         */
        public String getProvider() {
            return provider;
        }

        /**
         * 设置默认 Embedding 供应商。
         *
         * @param provider 默认供应商
         */
        public void setProvider(String provider) {
            this.provider = provider;
        }

        /**
         * 获取本地命令行 Embedding 配置。
         *
         * @return 本地命令行配置
         */
        public LocalCliEmbeddingProperties getLocalCli() {
            return localCli;
        }

        /**
         * 设置本地命令行 Embedding 配置。
         *
         * @param localCli 本地命令行配置
         */
        public void setLocalCli(LocalCliEmbeddingProperties localCli) {
            this.localCli = localCli == null ? new LocalCliEmbeddingProperties() : localCli;
        }
    }

    /**
     * llama.cpp 本地命令行 Embedding 配置。
     */
    public static class LocalCliEmbeddingProperties {

        private String executablePath;
        private String modelPath;
        private String pooling = "mean";
        private boolean normalize = true;
        private int timeoutSeconds = 120;
        private int batchSize = 1;

        /**
         * 获取 llama-embedding 可执行文件路径。
         *
         * @return 可执行文件路径
         */
        public String getExecutablePath() {
            return executablePath;
        }

        /**
         * 设置 llama-embedding 可执行文件路径。
         *
         * @param executablePath 可执行文件路径
         */
        public void setExecutablePath(String executablePath) {
            this.executablePath = executablePath;
        }

        /**
         * 获取 GGUF Embedding 模型路径。
         *
         * @return 模型路径
         */
        public String getModelPath() {
            return modelPath;
        }

        /**
         * 设置 GGUF Embedding 模型路径。
         *
         * @param modelPath 模型路径
         */
        public void setModelPath(String modelPath) {
            this.modelPath = modelPath;
        }

        /**
         * 获取池化方式。
         *
         * @return 池化方式
         */
        public String getPooling() {
            return pooling;
        }

        /**
         * 设置池化方式。
         *
         * @param pooling 池化方式
         */
        public void setPooling(String pooling) {
            this.pooling = pooling;
        }

        /**
         * 是否对向量做 L2 归一化。
         *
         * @return 是否归一化
         */
        public boolean isNormalize() {
            return normalize;
        }

        /**
         * 设置是否对向量做 L2 归一化。
         *
         * @param normalize 是否归一化
         */
        public void setNormalize(boolean normalize) {
            this.normalize = normalize;
        }

        /**
         * 获取命令超时时间。
         *
         * @return 超时时间，单位秒
         */
        public int getTimeoutSeconds() {
            return timeoutSeconds;
        }

        /**
         * 设置命令超时时间。
         *
         * @param timeoutSeconds 超时时间，单位秒
         */
        public void setTimeoutSeconds(int timeoutSeconds) {
            this.timeoutSeconds = timeoutSeconds;
        }

        /**
         * 获取批处理数量。
         *
         * @return 批处理数量
         */
        public int getBatchSize() {
            return batchSize;
        }

        /**
         * 设置批处理数量。
         *
         * @param batchSize 批处理数量
         */
        public void setBatchSize(int batchSize) {
            this.batchSize = batchSize;
        }
    }

    /**
     * LLM 总结配置。
     */
    public static class LlmProperties {

        private String defaultProvider = "bailian";
        private int timeoutSeconds = 60;
        private ProviderProperties bailian = new ProviderProperties(
                "https://dashscope.aliyuncs.com/compatible-mode/v1",
                "qwen-plus"
        );
        private ProviderProperties deepseek = new ProviderProperties(
                "https://api.deepseek.com",
                "deepseek-v4-flash"
        );

        /**
         * 获取默认供应商。
         *
         * @return 默认供应商
         */
        public String getDefaultProvider() {
            return defaultProvider;
        }

        /**
         * 设置默认供应商。
         *
         * @param defaultProvider 默认供应商
         */
        public void setDefaultProvider(String defaultProvider) {
            this.defaultProvider = defaultProvider;
        }

        /**
         * 获取请求超时时间。
         *
         * @return 请求超时时间，单位秒
         */
        public int getTimeoutSeconds() {
            return timeoutSeconds;
        }

        /**
         * 设置请求超时时间。
         *
         * @param timeoutSeconds 请求超时时间，单位秒
         */
        public void setTimeoutSeconds(int timeoutSeconds) {
            this.timeoutSeconds = timeoutSeconds;
        }

        /**
         * 获取阿里百炼配置。
         *
         * @return 阿里百炼配置
         */
        public ProviderProperties getBailian() {
            return bailian;
        }

        /**
         * 设置阿里百炼配置。
         *
         * @param bailian 阿里百炼配置
         */
        public void setBailian(ProviderProperties bailian) {
            this.bailian = bailian == null ? new ProviderProperties() : bailian;
        }

        /**
         * 获取 DeepSeek 配置。
         *
         * @return DeepSeek 配置
         */
        public ProviderProperties getDeepseek() {
            return deepseek;
        }

        /**
         * 设置 DeepSeek 配置。
         *
         * @param deepseek DeepSeek 配置
         */
        public void setDeepseek(ProviderProperties deepseek) {
            this.deepseek = deepseek == null ? new ProviderProperties() : deepseek;
        }
    }

    /**
     * OpenAI 兼容供应商配置。
     */
    public static class ProviderProperties {

        private String apiKey;
        private String baseUrl;
        private String model;

        /**
         * 创建空供应商配置。
         */
        public ProviderProperties() {
        }

        /**
         * 创建供应商配置。
         *
         * @param baseUrl API 基础地址
         * @param model 模型名称
         */
        public ProviderProperties(String baseUrl, String model) {
            this.baseUrl = baseUrl;
            this.model = model;
        }

        /**
         * 获取 API Key。
         *
         * @return API Key
         */
        public String getApiKey() {
            return apiKey;
        }

        /**
         * 设置 API Key。
         *
         * @param apiKey API Key
         */
        public void setApiKey(String apiKey) {
            this.apiKey = apiKey;
        }

        /**
         * 获取 API 基础地址。
         *
         * @return API 基础地址
         */
        public String getBaseUrl() {
            return baseUrl;
        }

        /**
         * 设置 API 基础地址。
         *
         * @param baseUrl API 基础地址
         */
        public void setBaseUrl(String baseUrl) {
            this.baseUrl = baseUrl;
        }

        /**
         * 获取模型名称。
         *
         * @return 模型名称
         */
        public String getModel() {
            return model;
        }

        /**
         * 设置模型名称。
         *
         * @param model 模型名称
         */
        public void setModel(String model) {
            this.model = model;
        }
    }
}
