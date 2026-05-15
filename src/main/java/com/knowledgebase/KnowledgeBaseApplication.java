package com.knowledgebase;

import com.knowledgebase.config.BackupRestoreInitializer;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 个人知识库应用启动入口。
 */
@SpringBootApplication
public class KnowledgeBaseApplication {

    /**
     * 启动 Spring Boot 应用。
     *
     * @param args 命令行参数
     */
    public static void main(String[] args) {
        SpringApplication application = new SpringApplication(KnowledgeBaseApplication.class);
        application.addListeners(new BackupRestoreInitializer());
        application.run(args);
    }
}
