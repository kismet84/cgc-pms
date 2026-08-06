package com.cgcpms.document;

import org.h2.tools.RunScript;
import org.junit.jupiter.api.Test;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

import static org.junit.jupiter.api.Assertions.assertThrows;

class DocumentCanvasMigrationTest {
    @Test
    void v287AllowsDynamicUppercaseTypesAndRejectsMalformedValues() throws Exception {
        try (Connection connection = DriverManager.getConnection(
                "jdbc:h2:mem:document_canvas_v287;MODE=MySQL;DATABASE_TO_LOWER=TRUE", "sa", "")) {
            connection.createStatement().execute("""
                    CREATE TABLE biz_document_template_version (schema_version VARCHAR(30));
                    CREATE TABLE biz_document_template (business_type VARCHAR(80),
                      CONSTRAINT ck_document_template_business CHECK (business_type IN ('PAYMENT','SETTLEMENT')));
                    CREATE TABLE biz_document_default_binding (business_type VARCHAR(80),
                      CONSTRAINT ck_document_default_business CHECK (business_type IN ('PAYMENT','SETTLEMENT')));
                    CREATE TABLE biz_document_generation (business_type VARCHAR(80),
                      CONSTRAINT ck_document_generation_business CHECK (business_type IN ('PAYMENT','SETTLEMENT')));
                    """);
            try (var reader = new InputStreamReader(getClass().getResourceAsStream(
                    "/db/migration-h2/V287__add_document_template_design_schema.sql"), StandardCharsets.UTF_8)) {
                RunScript.execute(connection, reader);
            }
            for (String table : new String[]{"biz_document_template", "biz_document_default_binding",
                    "biz_document_generation"}) {
                connection.createStatement().execute("INSERT INTO " + table + " (business_type) VALUES ('SUB_MEASURE')");
                assertThrows(SQLException.class, () -> connection.createStatement()
                        .execute("INSERT INTO " + table + " (business_type) VALUES ('sub-measure')"));
            }
        }
    }
}
