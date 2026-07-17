package com.cgcpms.common.migration;

import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

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
     * @param conn     JDBC 连接
     * @param tableName 表名（大小写不敏感，将转为大写匹配）
     * @param addSqls  重建约束/索引的 DDL 语句
     */
    protected static void rebuildUniqueConstraints(java.sql.Connection conn, String tableName, String... addSqls) throws SQLException {
        validateIdentifier(tableName);
        String upper = tableName.toUpperCase(Locale.ROOT);
        List<String> names = new ArrayList<>();
        String quotedTable = quoteIdentifier(tableName);

        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT CONSTRAINT_NAME FROM INFORMATION_SCHEMA.TABLE_CONSTRAINTS " +
                        "WHERE UPPER(CONSTRAINT_SCHEMA)='PUBLIC' AND UPPER(TABLE_NAME)=? " +
                        "AND CONSTRAINT_TYPE = 'UNIQUE'")) { // SQL-SAFETY: migration-ddl
            ps.setString(1, upper);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String name = rs.getString(1);
                    if (name != null && !name.isBlank() && !names.contains(name)) {
                        names.add(name);
                    }
                }
            }
        }

        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT INDEX_NAME FROM INFORMATION_SCHEMA.INDEXES " +
                        "WHERE UPPER(TABLE_SCHEMA)='PUBLIC' AND UPPER(TABLE_NAME)=? " +
                        "AND INDEX_TYPE_NAME = 'UNIQUE INDEX'")) { // SQL-SAFETY: migration-ddl
            ps.setString(1, upper);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String name = rs.getString(1);
                    if (name != null && !name.isBlank() && !names.contains(name)) {
                        names.add(name);
                    }
                }
            }
        }

        for (String name : names) {
            validateIdentifier(name);
            String quotedName = quoteIdentifier(name);
            try (PreparedStatement ps = conn.prepareStatement(
                    "ALTER TABLE " + quotedTable + " DROP CONSTRAINT " + quotedName)) { // SQL-SAFETY: migration-ddl
                ps.execute();
            } catch (SQLException ignored) {
                try (PreparedStatement ps = conn.prepareStatement("DROP INDEX " + quotedName)) { // SQL-SAFETY: migration-ddl
                    ps.execute();
                } catch (SQLException ignoredAgain) {
                    // If both forms fail, continue — the add SQL below will surface any remaining issue.
                }
            }
        }

        for (String sql : addSqls) {
            if (sql == null || sql.isBlank()) {
                continue;
            }
            try (PreparedStatement ps = conn.prepareStatement(sql)) { // SQL-SAFETY: migration-ddl
                ps.execute();
            }
        }
    }

    /**
     * 删除一个已知名称的 H2 唯一约束或唯一索引，不影响同表的其他唯一约束。
     */
    protected static void dropNamedUniqueConstraint(java.sql.Connection conn,
                                                    String tableName,
                                                    String constraintName) throws SQLException {
        validateIdentifier(tableName);
        validateIdentifier(constraintName);
        String actualConstraint = null;
        try (PreparedStatement ps = conn.prepareStatement("""
                SELECT CONSTRAINT_NAME
                FROM INFORMATION_SCHEMA.TABLE_CONSTRAINTS
                WHERE UPPER(CONSTRAINT_SCHEMA) = 'PUBLIC'
                  AND UPPER(TABLE_NAME) = ?
                  AND UPPER(CONSTRAINT_NAME) = ?
                  AND CONSTRAINT_TYPE = 'UNIQUE'
                """)) { // SQL-SAFETY: migration-ddl
            ps.setString(1, tableName.toUpperCase(Locale.ROOT));
            ps.setString(2, constraintName.toUpperCase(Locale.ROOT));
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    actualConstraint = rs.getString(1);
                }
            }
        }
        if (actualConstraint != null) {
            validateIdentifier(actualConstraint);
            try (PreparedStatement ps = conn.prepareStatement(
                    "ALTER TABLE " + quoteIdentifier(tableName) +
                            " DROP CONSTRAINT " + quoteIdentifier(actualConstraint))) { // SQL-SAFETY: migration-ddl
                ps.execute();
                return;
            }
        }

        String actualIndex = null;
        try (PreparedStatement ps = conn.prepareStatement("""
                SELECT INDEX_NAME
                FROM INFORMATION_SCHEMA.INDEXES
                WHERE UPPER(TABLE_SCHEMA) = 'PUBLIC'
                  AND UPPER(TABLE_NAME) = ?
                  AND UPPER(INDEX_NAME) = ?
                """)) { // SQL-SAFETY: migration-ddl
            ps.setString(1, tableName.toUpperCase(Locale.ROOT));
            ps.setString(2, constraintName.toUpperCase(Locale.ROOT));
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    actualIndex = rs.getString(1);
                }
            }
        }
        if (actualIndex != null) {
            validateIdentifier(actualIndex);
            try (PreparedStatement ps = conn.prepareStatement(
                    "DROP INDEX " + quoteIdentifier(actualIndex))) { // SQL-SAFETY: migration-ddl
                ps.execute();
            }
        }
    }

    private static String quoteIdentifier(String identifier) {
        return "\"" + identifier.replace("\"", "\"\"") + "\"";
    }

    private static void validateIdentifier(String value) throws SQLException {
        if (value == null || value.isBlank()) {
            throw new SQLException("Invalid identifier");
        }
    }
}
