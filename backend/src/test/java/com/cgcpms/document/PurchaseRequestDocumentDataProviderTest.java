package com.cgcpms.document;

import com.cgcpms.auth.context.UserContext;
import com.cgcpms.document.provider.DocumentDataSnapshot;
import com.cgcpms.document.provider.PurchaseRequestDocumentDataProvider;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PurchaseRequestDocumentDataProviderTest {
    private JdbcTemplate jdbc;
    private PurchaseRequestDocumentDataProvider provider;

    @BeforeEach
    void setUp() {
        jdbc = mock(JdbcTemplate.class);
        provider = new PurchaseRequestDocumentDataProvider(jdbc);
        UserContext.restore(new UserContext.Snapshot(9L, "approver", 7L, List.of()));
    }

    @AfterEach void clear() { UserContext.clear(); }

    @Test
    void formalSnapshotContainsAuthoritativeHeaderItemsApprovalAndSignatures() {
        AtomicBoolean materialFallbackQuery = new AtomicBoolean();
        when(jdbc.queryForList(anyString(), any(Object[].class))).thenAnswer(invocation -> {
            String sql = invocation.getArgument(0);
            if (sql.contains("FROM mat_purchase_request r")) return List.of(Map.ofEntries(
                    Map.entry("id", 101L), Map.entry("request_code", "PR-101"),
                    Map.entry("purpose", "主体施工"), Map.entry("approval_status", "APPROVED"),
                    Map.entry("status", "CONVERTED"), Map.entry("created_by", 8L),
                    Map.entry("created_at", LocalDateTime.of(2026, 8, 1, 10, 0)),
                    Map.entry("technical_quality_brand_requirements", "国标"),
                    Map.entry("project_code", "P-1"), Map.entry("project_name", "一号项目"),
                    Map.entry("applicant_name", "申请人"), Map.entry("department_name", "工程部"),
                    Map.entry("plan_date", LocalDate.of(2026, 8, 8))));
            if (sql.contains("FROM mat_purchase_request_item")) {
                materialFallbackQuery.set(sql.contains("LEFT JOIN md_material")
                        && sql.contains("COALESCE(NULLIF(TRIM(i.material_name),''),m.material_name)"));
                return List.of(Map.ofEntries(
                    Map.entry("material_name", "钢筋"), Map.entry("specification", "HRB400"),
                    Map.entry("unit", "吨"), Map.entry("quantity", new BigDecimal("10.0000")),
                    Map.entry("approved_quantity", new BigDecimal("8.0000")),
                    Map.entry("use_location", "主体"), Map.entry("planned_date", LocalDate.of(2026, 8, 8)),
                    Map.entry("remark", "分批到货")));
            }
            if (sql.contains("FROM wf_record")) return List.of(
                    record("DEPARTMENT_MANAGER", "部门负责人"), record("PROJECT_MANAGER", "项目负责人"));
            if (sql.contains("FROM wf_instance")) return List.of(Map.of(
                    "instance_status", "APPROVED", "initiator_id", 8L,
                    "started_at", LocalDateTime.of(2026, 8, 1, 9, 0),
                    "ended_at", LocalDateTime.of(2026, 8, 1, 12, 0)));
            return List.of();
        });

        DocumentDataSnapshot snapshot = provider.load(101L);

        assertEquals("purchase-request.v2", snapshot.schemaVersion());
        assertEquals("PR-101", map(snapshot.values(), "purchaseRequest").get("requestCode"));
        assertEquals("工程部", map(snapshot.values(), "applicant").get("department"));
        assertEquals("8", rows(snapshot.values(), "items").get(0).get("approvedQuantity"));
        assertTrue(materialFallbackQuery.get());
        assertEquals("钢筋", rows(snapshot.values(), "items").get(0).get("materialName"));
        assertEquals("", map(snapshot.values(), "signatures").get("applicant"));
        assertEquals("部门负责人", map(snapshot.values(), "signatures").get("departmentManager"));
        assertEquals("项目负责人", map(snapshot.values(), "signatures").get("projectManager"));
        assertTrue(snapshot.values().containsKey("approvalRecords"));
    }

    private Map<String, Object> record(String nodeCode, String operator) {
        return Map.of("node_code", nodeCode, "node_name", nodeCode, "action_type", "APPROVE",
                "action_name", "同意", "operator_id", 1L, "operator_name", operator,
                "comment", "同意", "created_at", LocalDateTime.of(2026, 8, 1, 11, 0));
    }

    @SuppressWarnings("unchecked") private Map<String, Object> map(Map<String, Object> root, String key) {
        return (Map<String, Object>) root.get(key);
    }
    @SuppressWarnings("unchecked") private List<Map<String, Object>> rows(Map<String, Object> root, String key) {
        return (List<Map<String, Object>>) root.get(key);
    }
}
