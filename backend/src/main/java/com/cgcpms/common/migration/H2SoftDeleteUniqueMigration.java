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
 * H2 的 CREATE TABLE ... UNIQUE(cols) 会在 INFORMATION_SCHEMA.INDEXES 中创建一条
 * UNIQUE INDEX（同时也会在 CONSTRAINTS 中创建一行）。纯 SQL 迁移中的 CREATE ALIAS
 * 需要运行时 javac，且手动拼接 DROP CONSTRAINT/DROP INDEX 存在 ResultSet/Statement
 * 生命周期问题。Java 迁移可以拿到 JDBC Connection，彻底消除这些问题。
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
 *
 * <p>对于多表/多约束的复杂迁移（如 V75），可调用静态辅助方法 {@link #rebuildUniqueConstraints}
 * 在循环中复用基类逻辑。</p>
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
        java.sql.Connection conn = context.getConnection();

        // 1. 收集该表所有 UNIQUE 索引名 — H2 中 TABLE_SCHEMA 存储为小写 'public'
        List<String> indexNames = new ArrayList<>();
        try (Statement st = conn.createStatement();
             // SAFETY: tableName is always a hard-coded constant from subclass constructors, never user input
             ResultSet rs = st.executeQuery(
                 "SELECT INDEX_NAME FROM INFORMATION_SCHEMA.INDEXES " +
                 "WHERE TABLE_SCHEMA='public' AND UPPER(TABLE_NAME)='" + tableName.toUpperCase() + "' " +
                 "AND INDEX_TYPE_NAME = 'UNIQUE INDEX'")) {
            while (rs.next()) {
                indexNames.add(rs.getString(1));
            }
        }

        // 2. 用独立 Statement 删除每个 UNIQUE 索引（H2 的 DROP INDEX 同时会删除对应约束）
        try (Statement st = conn.createStatement()) {
            for (String name : indexNames) {
                try {
                    st.execute("DROP INDEX \"" + name + "\"");
                } catch (SQLException e) {
                    // 如果 DROP INDEX 失败，尝试 DROP CONSTRAINT
                    try {
                        st.execute("ALTER TABLE " + tableName + " DROP CONSTRAINT \"" + name + "\"");
                    } catch (SQLException ignored) {}
                }
            }
        }

        // 3. 添加包含 deleted_flag 的新约束
        try (Statement st = conn.createStatement()) {
            st.execute(addConstraintSql);
        }
    }

    /**
     * 辅助方法 — 供 V75 等复杂迁移复用。
     * 收集并删除指定表的所有 UNIQUE 索引，然后执行给定的重建 SQL。
     *
     * @param conn    JDBC 连接
     * @param tableName 表名（大小写不敏感，将转为大写匹配）
     * @param addSqls   重建约束/索引的 DDL 语句
     */
    protected static void rebuildUniqueConstraints(java.sql.Connection conn, String tableName, String... addSqls) throws SQLException {
        String upper = tableName.toUpperCase();
        List<String> names = new ArrayList<>();
        try (Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery(
                 "SELECT INDEX_NAME FROM INFORMATION_SCHEMA.INDEXES " +
                 "WHERE TABLE_SCHEMA='PUBLIC' AND UPPER(TABLE_NAME)='" + upper + "' " +
                 "AND INDEX_TYPE_NAME = 'UNIQUE INDEX'")) {
            while (rs.next()) names.add(rs.getString(1));
        }
        try (Statement st = conn.createStatement()) {
            for (String name : names) {
                try {
                    st.execute("DROP INDEX \"" + name + "\"");
                } catch (SQLException e) {
                    try {
                        st.execute("ALTER TABLE " + tableName + " DROP CONSTRAINT \"" + name + "\"");
                    } catch (SQLException ignored) {}
                }
            }
            for (String sql : addSqls) st.execute(sql);
        }
    }
}
