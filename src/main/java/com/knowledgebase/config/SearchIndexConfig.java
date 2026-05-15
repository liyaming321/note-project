package com.knowledgebase.config;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.cn.smart.SmartChineseAnalyzer;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Lucene 搜索索引配置。
 */
@Configuration
@EnableConfigurationProperties(KnowledgeBaseProperties.class)
public class SearchIndexConfig {

    /**
     * 创建中文分词分析器。
     *
     * @return SmartCN 中文分词分析器
     */
    @Bean
    public Analyzer searchAnalyzer() {
        return new SmartChineseAnalyzer();
    }
}
