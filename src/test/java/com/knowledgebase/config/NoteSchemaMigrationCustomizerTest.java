package com.knowledgebase.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashMap;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

/**
 * 笔记表结构兼容迁移测试。
 */
class NoteSchemaMigrationCustomizerTest {

    /**
     * 验证旧 H2 枚举列会迁移为可保存普通文本格式的字符串列。
     */
    @Test
    void shouldMigrateLegacyNoteTypeEnumToVarchar() {
        DriverManagerDataSource dataSource = new DriverManagerDataSource();
        dataSource.setDriverClassName("org.h2.Driver");
        dataSource.setUrl("jdbc:h2:mem:legacy-note-type-" + UUID.randomUUID() + ";MODE=MYSQL;DB_CLOSE_DELAY=-1");
        dataSource.setUsername("sa");
        dataSource.setPassword("");

        JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);
        jdbcTemplate.execute("""
                CREATE TABLE notes (
                    id BIGINT PRIMARY KEY,
                    title VARCHAR(160) NOT NULL,
                    content CLOB NOT NULL,
                    content_text CLOB NOT NULL,
                    type ENUM('CODE', 'MARKDOWN'),
                    status VARCHAR(20),
                    archived BOOLEAN,
                    summary VARCHAR(500)
                )
                """);
        jdbcTemplate.update("""
                INSERT INTO notes (id, title, content, content_text, type)
                VALUES (1, '旧枚举笔记', 'content', 'content', 'MARKDOWN')
                """);

        NoteSchemaMigrationCustomizer customizer = new NoteSchemaMigrationCustomizer(dataSource, jdbcTemplate);
        customizer.customize(new HashMap<>());

        jdbcTemplate.update("UPDATE notes SET type = 'TEXT' WHERE id = 1");

        String migratedType = jdbcTemplate.queryForObject("SELECT type FROM notes WHERE id = 1", String.class);
        String dataType = jdbcTemplate.queryForObject("""
                SELECT DATA_TYPE
                FROM INFORMATION_SCHEMA.COLUMNS
                WHERE TABLE_NAME = 'NOTES' AND COLUMN_NAME = 'TYPE'
                """, String.class);

        assertThat(migratedType).isEqualTo("TEXT");
        assertThat(dataType).isEqualTo("CHARACTER VARYING");
    }
}
