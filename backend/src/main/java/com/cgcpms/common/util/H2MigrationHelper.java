package com.cgcpms.common.util;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.PreparedStatement;
import java.util.ArrayList;
import java.util.Locale;
import java.util.List;
import java.util.regex.Pattern;

/**
 * H2 迁移助手 — 提供预编译的存储过程，避免 H2 CREATE ALIAS 运行时依赖 javac。
 * <p>
 * 仅用于 H2 迁移脚本（db/migration-h2），通过 {@code CREATE ALIAS ... FOR "..."} 引用。
 * </p>
 */
public class H2MigrationHelper {

    private static final String PUBLIC_SCHEMA = "PUBLIC";
    private static final Pattern IDENTIFIER_PATTERN = Pattern.compile("[A-Za-z_][A-Za-z0-9_]*");

    /**
     * 删除指定表的所有 UNIQUE 约束/索引（包括 auto-generated 和显式命名的）。
     * 在 H2 中，CREATE TABLE ... UNIQUE(cols) 会同时创建一个 CONSTRAINT 和一个同名 INDEX。
     * DROP CONSTRAINT 会删除约束但可能留下索引；同时尝试 DROP INDEX 确保清理干净。
     */
    public static String dropUniqueConstraints(Connection conn, String tableName) throws Exception {
        String safeTable = normalizeIdentifier(tableName, "tableName");
        String upperTable = safeTable.toUpperCase(Locale.ROOT);

        // 先收集名称（使用参数化查询并关闭 ResultSet），再逐条执行 DDL
        List<String> names = new ArrayList<>();

        // 只从 INDEXES 视图收集（它同时包含 CONSTRAINT 创建的索引和纯 UNIQUE INDEX）
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT INDEX_NAME FROM INFORMATION_SCHEMA.INDEXES " +
                        "WHERE TABLE_SCHEMA = ? AND UPPER(TABLE_NAME)=? " +
                        "AND (INDEX_TYPE_NAME LIKE '%UNIQUE%' OR INDEX_TYPE_NAME LIKE '%unique%')")) { // SQL-SAFETY: migration-ddl
            ps.setString(1, PUBLIC_SCHEMA);
            ps.setString(2, upperTable);
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
                "SELECT CONSTRAINT_NAME FROM INFORMATION_SCHEMA.TABLE_CONSTRAINTS " +
                        "WHERE UPPER(CONSTRAINT_SCHEMA)=? AND UPPER(TABLE_NAME)=? " +
                        "AND CONSTRAINT_TYPE = 'UNIQUE'")) { // SQL-SAFETY: migration-ddl
            ps.setString(1, PUBLIC_SCHEMA);
            ps.setString(2, upperTable);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String name = rs.getString(1);
                    if (name != null && !name.isBlank() && !names.contains(name)) {
                        names.add(name);
                    }
                }
            }
        }

        String safeTableRef = quoteIdentifier(safeTable);
        for (String name : names) {
            String safeConstraintOrIndex = normalizeIdentifier(name, "constraintName");
            String quotedName = quoteIdentifier(safeConstraintOrIndex);

            // 先尝试作为索引删除
            try (PreparedStatement ps = conn.prepareStatement("DROP INDEX " + quotedName)) { // SQL-SAFETY: migration-ddl
                ps.execute();
                continue;
            } catch (SQLException ignored) {
                // 可能是 CONSTRAINT 名称，继续尝试约束删除
            }
            // 再尝试作为约束删除
            try (PreparedStatement ps = conn.prepareStatement(
                    "ALTER TABLE " + safeTableRef + " DROP CONSTRAINT " + quotedName)) { // SQL-SAFETY: migration-ddl
                ps.execute();
            }
        }
        return null;
    }

    public static String dropUniqueConstraintsAndIndex(Connection conn, String tableName, String indexName)
            throws Exception {
        dropUniqueConstraints(conn, tableName);

        String safeIndex = normalizeIdentifier(indexName, "indexName");
        try (PreparedStatement ps = conn.prepareStatement("DROP INDEX IF EXISTS " + quoteIdentifier(safeIndex))) { // SQL-SAFETY: migration-ddl
            ps.execute();
        }
        return null;
    }

    private static String normalizeIdentifier(String value, String label) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Invalid " + label + ": empty");
        }
        String trimmed = value.trim();
        if (!IDENTIFIER_PATTERN.matcher(trimmed).matches()) {
            throw new IllegalArgumentException("Invalid " + label + ": " + trimmed);
        }
        return trimmed;
    }

    private static String quoteIdentifier(String value) {
        return "\"" + value.replace("\"", "\"\"") + "\"";
    }
}
