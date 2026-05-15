package com.knowledgebase.config;

import com.knowledgebase.service.BackupService;
import java.nio.file.Paths;
import org.springframework.boot.context.event.ApplicationEnvironmentPreparedEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.Environment;

/**
 * 启动早期备份恢复初始化器。
 */
public class BackupRestoreInitializer implements ApplicationListener<ApplicationEnvironmentPreparedEvent> {

    /**
     * 环境准备完成后执行启动期恢复。
     *
     * @param event 环境准备事件
     */
    @Override
    public void onApplicationEvent(ApplicationEnvironmentPreparedEvent event) {
        ConfigurableEnvironment environment = event.getEnvironment();
        String restoreBackupPath = environment.getProperty("knowledge-base.restore-backup-path");
        if (restoreBackupPath == null || restoreBackupPath.isBlank()) {
            return;
        }
        new BackupService(toProperties(environment)).restoreFromBackup(Paths.get(restoreBackupPath));
    }

    /**
     * 从环境变量构建备份恢复所需配置。
     *
     * @param environment Spring 环境
     * @return 知识库配置
     */
    private KnowledgeBaseProperties toProperties(Environment environment) {
        KnowledgeBaseProperties properties = new KnowledgeBaseProperties();
        properties.setDataPath(environment.getProperty("knowledge-base.data-path"));
        properties.setIndexPath(environment.getProperty("knowledge-base.index-path"));
        properties.setVectorIndexPath(environment.getProperty("knowledge-base.vector-index-path"));
        properties.setImagesPath(environment.getProperty("knowledge-base.images-path"));
        properties.setRestoreBackupPath(environment.getProperty("knowledge-base.restore-backup-path"));
        return properties;
    }
}
