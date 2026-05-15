package com.knowledgebase.config;

import com.knowledgebase.service.IndexService;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/**
 * 应用启动时检查 Lucene 索引。
 */
@Component
public class SearchIndexInitializer implements ApplicationRunner {

    private final IndexService indexService;

    /**
     * 创建搜索索引初始化器。
     *
     * @param indexService 索引服务
     */
    public SearchIndexInitializer(IndexService indexService) {
        this.indexService = indexService;
    }

    /**
     * 启动时在索引不存在时全量重建。
     *
     * @param args 启动参数
     */
    @Override
    public void run(ApplicationArguments args) {
        if (!indexService.indexExists()) {
            indexService.rebuild();
        }
    }
}
