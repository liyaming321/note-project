package com.knowledgebase.config;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Map;
import javax.sql.DataSource;
import org.springframework.boot.autoconfigure.orm.jpa.HibernatePropertiesCustomizer;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Hibernate 建表前的笔记表兼容迁移。
 */
@Component
public class NoteSchemaMigrationCustomizer implements HibernatePropertiesCustomizer {

    private static final String NOTES_TABLE = "NOTES";
    private static final String TYPE_COLUMN = "TYPE";
    private static final String STATUS_COLUMN = "STATUS";
    private static final String ARCHIVED_COLUMN = "ARCHIVED";
    private static final String SUMMARY_COLUMN = "SUMMARY";

    private final DataSource dataSource;
    private final JdbcTemplate jdbcTemplate;
    private boolean migrated;

    /**
     * 创建笔记表兼容迁移器。
     *
     * @param dataSource 数据源
     * @param jdbcTemplate JDBC 操作模板
     */
    public NoteSchemaMigrationCustomizer(DataSource dataSource, JdbcTemplate jdbcTemplate) {
        this.dataSource = dataSource;
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * 在 Hibernate 读取表结构前补齐旧库缺失字段。
     *
     * @param hibernateProperties Hibernate 配置
     */
    @Override
    public synchronized void customize(Map<String, Object> hibernateProperties) {
        if (migrated) {
            return;
        }
        migrated = true;
        if (!tableExists(NOTES_TABLE)) {
            return;
        }
        migrateContentFormatColumn();
        if (!columnExists(NOTES_TABLE, STATUS_COLUMN)) {
            jdbcTemplate.execute("ALTER TABLE notes ADD COLUMN status VARCHAR(20) DEFAULT 'PUBLISHED'");
        }
        if (!columnExists(NOTES_TABLE, ARCHIVED_COLUMN)) {
            jdbcTemplate.execute("ALTER TABLE notes ADD COLUMN archived BOOLEAN DEFAULT FALSE");
        }
        if (!columnExists(NOTES_TABLE, SUMMARY_COLUMN)) {
            jdbcTemplate.execute("ALTER TABLE notes ADD COLUMN summary VARCHAR(500)");
        }
        jdbcTemplate.update("UPDATE notes SET status = 'PUBLISHED' WHERE status IS NULL");
        jdbcTemplate.update("UPDATE notes SET archived = FALSE WHERE archived IS NULL");
    }

    /**
     * 将旧 H2 枚举格式迁移为字符串列，避免新增内容格式时被旧枚举约束拦截。
     */
    private void migrateContentFormatColumn() {
        if (!columnExists(NOTES_TABLE, TYPE_COLUMN)) {
            return;
        }
        jdbcTemplate.execute("ALTER TABLE notes ALTER COLUMN type VARCHAR(20)");
    }

    /**
     * 判断表是否存在。
     *
     * @param tableName 表名
     * @return 是否存在
     */
    private boolean tableExists(String tableName) {
        try (Connection connection = dataSource.getConnection();
             ResultSet resultSet = metadata(connection).getTables(null, null, tableName, new String[]{"TABLE"})) {
            return resultSet.next();
        } catch (SQLException ex) {
            throw new IllegalStateException("检查笔记表是否存在失败", ex);
        }
    }

    /**
     * 判断字段是否存在。
     *
     * @param tableName 表名
     * @param columnName 字段名
     * @return 是否存在
     */
    private boolean columnExists(String tableName, String columnName) {
        try (Connection connection = dataSource.getConnection();
             ResultSet resultSet = metadata(connection).getColumns(null, null, tableName, columnName)) {
            return resultSet.next();
        } catch (SQLException ex) {
            throw new IllegalStateException("检查笔记字段是否存在失败", ex);
        }
    }

    /**
     * 获取数据库元信息。
     *
     * @param connection 数据库连接
     * @return 数据库元信息
     * @throws SQLException 元信息读取异常
     */
    private DatabaseMetaData metadata(Connection connection) throws SQLException {
        return connection.getMetaData();
    }
}
