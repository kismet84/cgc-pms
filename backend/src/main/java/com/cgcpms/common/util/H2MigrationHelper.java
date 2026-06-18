package com.cgcpms.common.util;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

/**
 * H2 迁移助手 — 提供预编译的存储过程，避免 H2 CREATE ALIAS 运行时依赖 javac。
 * <p>
 * 仅用于 H2 迁移脚本（db/migration-h2），通过 {@code CREATE ALIAS ... FOR "..."} 引用。
 * </p>
 */
public class H2MigrationHelper {

    /**
     * 删除指定表的所有 UNIQUE 约束/索引（包括 auto-generated 和显式命名的）。
     * 在 H2 中，CREATE TABLE ... UNIQUE(cols) 会同时创建一个 CONSTRAINT 和一个同名 INDEX。
     * DROP CONSTRAINT 会删除约束但可能留下索引；同时尝试 DROP INDEX 确保清理干净。
     */
    public static String dropUniqueConstraints(Connection conn, String tableName) throws Exception {
        // 先收集名称（关闭 ResultSet），再执行 DDL（独立 Statement）
        List<String> names = new ArrayList<>();
        String upper = tableName.toUpperCase(java.util.Locale.ROOT);

        // 只从 INDEXES 视图收集（它同时包含 CONSTRAINT 创建的索引和纯 UNIQUE INDEX）
        try (Statement st = conn.createStatement()) {
            ResultSet rs = st.executeQuery(
                "SELECT INDEX_NAME FROM INFORMATION_SCHEMA.INDEXES " +
                "WHERE TABLE_SCHEMA='PUBLIC' AND UPPER(TABLE_NAME)='" + upper + "' " +
                "AND (INDEX_TYPE_NAME LIKE '%UNIQUE%' OR INDEX_TYPE_NAME LIKE '%unique%')");
            while (rs.next()) {
                names.add(rs.getString(1));
            }
            // 确保 Connection 级别的 ResultSet 在此处已关闭
        }

        // 用单独的 Statement 执行 DROP
        try (Statement st = conn.createStatement()) {
            for (String name : names) {
                // 先尝试作为索引删除
                try {
                    st.execute("DROP INDEX \"" + name + "\"");
                    continue;
                } catch (SQLException ignored) {}
                // 再尝试作为约束删除
                try {
                    st.execute("ALTER TABLE " + tableName + " DROP CONSTRAINT \"" + name + "\"");
                } catch (SQLException ignored) {}
            }
        }
        return null;
    }

    public static String dropUniqueConstraintsAndIndex(Connection conn, String tableName, String indexName)
            throws Exception {
        dropUniqueConstraints(conn, tableName);
        try (Statement st = conn.createStatement()) {
            st.execute("DROP INDEX IF EXISTS " + indexName);
        }
        return null;
    }
}
