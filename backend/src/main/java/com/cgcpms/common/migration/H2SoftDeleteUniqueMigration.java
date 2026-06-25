package com.cgcpms.common.migration;

import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

/**
 * H2 软删除安全唯一约束 — Java 迁移基类。
 * <p>
 * H2 的 CREATE TABLE ... UNIQUE(cols) 会在 INFORMATION_SCHEMA.INDEXES / TABLE_CONSTRAINTS
 * 中产生匿名唯一约束。纯 SQL 迁移很难可靠删除这些约束；Java 迁移可以拿到 JDBC Connection，
 * 先收集旧约束，再删除并重建为包含 deleted_flag 的新约束。
 * </p>
 *
 * <p>子类只需提供表名和新约束定义：</p>
 * <pre>{@code
 * public class V51__fix_project_unique_with_deleted_flag extends H2SoftDeleteUniqueMigration {
 *     public V51__fix_project_unique_with_deleted_flag() {
 *         super("pm_project", "ALTER TABLE pm_project ADD UNIQUE KEY uk_pm_project_code (tenant_id, project_code, deleted_flag)");
 *     }
 * }
 * }</pre>
 */
public abstract class H2SoftDeleteUniqueMigration extends BaseJavaMigration {

    private final String tableName;
    private final String addConstraintSql;

    protected H2SoftDeleteUniqueMigration(String tableName, String addConstraintSql) {
        this.tableName = tableName;
        this.addConstraintSql = addConstraintSql;
    }

    @Override
    public void migrate(Context context) throws Exception {
        rebuildUniqueConstraints(context.getConnection(), tableName, addConstraintSql);
    }

    /**
     * 收集并删除指定表的所有 UNIQUE 约束/索引，然后执行给定的重建 SQL。
     *
     * @param conn    JDBC 连接
     * @param tableName 表名（大小写不敏感，将转为大写匹配）
     * @param addSqls   重建约束/索引的 DDL 语句
     */
    protected static void rebuildUniqueConstraints(java.sql.Connection conn, String tableName, String... addSqls) throws SQLException {
        String upper = tableName.toUpperCase();
        List<String> names = new ArrayList<>();

        try (Statement st = conn.createStatement(); // SQL-SAFETY: migration-ddl
             ResultSet rs = st.executeQuery(
                 "SELECT CONSTRAINT_NAME FROM INFORMATION_SCHEMA.TABLE_CONSTRAINTS " +
                 "WHERE UPPER(CONSTRAINT_SCHEMA)='PUBLIC' AND UPPER(TABLE_NAME)='" + upper + "' " +
                 "AND CONSTRAINT_TYPE = 'UNIQUE'")) {
            while (rs.next()) {
                String name = rs.getString(1);
                if (!names.contains(name)) {
                    names.add(name);
                }
            }
        }

        try (Statement st = conn.createStatement(); // SQL-SAFETY: migration-ddl
             ResultSet rs = st.executeQuery(
                 "SELECT INDEX_NAME FROM INFORMATION_SCHEMA.INDEXES " +
                 "WHERE UPPER(TABLE_SCHEMA)='PUBLIC' AND UPPER(TABLE_NAME)='" + upper + "' " +
                 "AND INDEX_TYPE_NAME = 'UNIQUE INDEX'")) {
            while (rs.next()) {
                String name = rs.getString(1);
                if (!names.contains(name)) {
                    names.add(name);
                }
            }
        }

        try (Statement st = conn.createStatement()) { // SQL-SAFETY: migration-ddl
            for (String name : names) {
                try {
                    st.execute("ALTER TABLE " + tableName + " DROP CONSTRAINT \"" + name + "\"");
                } catch (SQLException ignored) {
                    try {
                        st.execute("DROP INDEX \"" + name + "\"");
                    } catch (SQLException ignoredAgain) {
                        // If both forms fail, continue — the add SQL below will surface any remaining issue.
                    }
                }
            }
            for (String sql : addSqls) {
                st.execute(sql);
            }
        }
    }
}
