package com.knowledgebase.config;

import jakarta.annotation.PostConstruct;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * 笔记表轻量迁移初始化器。
 */
@Component
public class NoteMigrationInitializer {

    private final JdbcTemplate jdbcTemplate;

    /**
     * 创建笔记表轻量迁移初始化器。
     *
     * @param jdbcTemplate JDBC 操作模板
     */
    public NoteMigrationInitializer(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * 回填旧数据缺失的第六阶段字段默认值。
     */
    @PostConstruct
    public void migrateNoteStateColumns() {
        jdbcTemplate.update("UPDATE notes SET status = 'PUBLISHED' WHERE status IS NULL");
        jdbcTemplate.update("UPDATE notes SET archived = FALSE WHERE archived IS NULL");
    }
}
