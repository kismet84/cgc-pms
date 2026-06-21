package com.cgcpms.common.migration;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

class H2SoftDeleteUniqueMigrationTest {

    @Test
    @DisplayName("rebuildUniqueConstraints can replace anonymous mat_stock unique constraint")
    void matStockAnonymousUniqueConstraintIsRebuilt() throws Exception {
        try (Connection conn = DriverManager.getConnection(
                "jdbc:h2:mem:mat_stock_migration_test;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH",
                "sa", "");
             Statement st = conn.createStatement()) {
            st.execute("CREATE TABLE mat_stock (" +
                    "id BIGINT NOT NULL, " +
                    "tenant_id BIGINT NOT NULL DEFAULT 0, " +
                    "warehouse_id BIGINT NOT NULL, " +
                    "material_id BIGINT NOT NULL, " +
                    "available_qty DECIMAL(18,4) NOT NULL DEFAULT 0, " +
                    "version INT NOT NULL DEFAULT 0, " +
                    "created_by BIGINT NULL, created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP, " +
                    "updated_by BIGINT NULL, updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP, " +
                    "deleted_flag SMALLINT NOT NULL DEFAULT 0, remark TEXT NULL, " +
                    "PRIMARY KEY (id), UNIQUE (warehouse_id, material_id))");
            st.execute("INSERT INTO mat_stock (id, tenant_id, warehouse_id, material_id, available_qty, version) VALUES (1, 1, 10, 20, 1.0000, 0)");
            st.execute("UPDATE mat_stock SET deleted_flag=1 WHERE id=1");

            H2SoftDeleteUniqueMigration.rebuildUniqueConstraints(conn, "mat_stock",
                    "ALTER TABLE mat_stock ADD UNIQUE KEY uk_ms_warehouse_material (warehouse_id, material_id, deleted_flag)");

            assertDoesNotThrow(() -> st.execute("INSERT INTO mat_stock (id, tenant_id, warehouse_id, material_id, available_qty, version) VALUES (2, 1, 10, 20, 2.0000, 0)"));
        }
    }

    @Test
    @DisplayName("rebuildUniqueConstraints can replace anonymous pay_invoice unique constraint")
    void payInvoiceAnonymousUniqueConstraintIsRebuilt() throws Exception {
        try (Connection conn = DriverManager.getConnection(
                "jdbc:h2:mem:pay_invoice_migration_test;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH",
                "sa", "");
             Statement st = conn.createStatement()) {
            st.execute("CREATE TABLE pay_invoice (" +
                    "id BIGINT NOT NULL, " +
                    "tenant_id BIGINT NOT NULL DEFAULT 0, " +
                    "invoice_no VARCHAR(100) NOT NULL, " +
                    "deleted_flag TINYINT NOT NULL DEFAULT 0, " +
                    "created_by BIGINT NULL, created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP, " +
                    "updated_by BIGINT NULL, updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP, " +
                    "remark TEXT NULL, " +
                    "PRIMARY KEY (id), UNIQUE (tenant_id, invoice_no))");
            st.execute("INSERT INTO pay_invoice (id, tenant_id, invoice_no, deleted_flag) VALUES (1, 1, 'INV-1', 0)");
            st.execute("UPDATE pay_invoice SET deleted_flag=1 WHERE id=1");

            H2SoftDeleteUniqueMigration.rebuildUniqueConstraints(conn, "pay_invoice",
                    "ALTER TABLE pay_invoice ADD UNIQUE KEY uk_pi_tenant_invoice_no_del (tenant_id, invoice_no, deleted_flag)");

            assertDoesNotThrow(() -> st.execute("INSERT INTO pay_invoice (id, tenant_id, invoice_no, deleted_flag) VALUES (2, 1, 'INV-1', 0)"));
        }
    }
}
