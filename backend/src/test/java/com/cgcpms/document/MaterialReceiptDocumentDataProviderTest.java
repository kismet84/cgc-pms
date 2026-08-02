package com.cgcpms.document;

import com.cgcpms.auth.context.UserContext;
import com.cgcpms.document.provider.DocumentDataSnapshot;
import com.cgcpms.document.provider.MaterialReceiptDocumentDataProvider;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class MaterialReceiptDocumentDataProviderTest {
    private JdbcTemplate jdbc;
    private MaterialReceiptDocumentDataProvider provider;

    @BeforeEach
    void setUp() {
        jdbc = mock(JdbcTemplate.class);
        provider = new MaterialReceiptDocumentDataProvider(jdbc);
        UserContext.restore(new UserContext.Snapshot(9L, "receiver", 7L, List.of()));
    }

    @AfterEach void clear() { UserContext.clear(); }

    @Test
    void previewSnapshotContainsOrderContractSupplierQuantitiesAndBlankSignatureLines() {
        when(jdbc.queryForList(anyString(), any(Object[].class))).thenAnswer(invocation -> {
            String sql = invocation.getArgument(0);
            if (sql.contains("FROM mat_receipt r")) return List.of(Map.ofEntries(
                    Map.entry("id", 201L), Map.entry("receipt_code", "RC-201"),
                    Map.entry("system_batch_no", "MB-20260801-ABC"), Map.entry("delivery_note_no", "DN-8"),
                    Map.entry("receipt_date", LocalDate.of(2026, 8, 1)), Map.entry("receipt_mode", "INVENTORY"),
                    Map.entry("total_amount", new BigDecimal("1234.50")), Map.entry("approval_status", "DRAFT"),
                    Map.entry("project_code", "P-1"), Map.entry("project_name", "一号项目"),
                    Map.entry("order_code", "PO-1"), Map.entry("contract_code", "CT-1"),
                    Map.entry("contract_name", "钢材合同"), Map.entry("partner_code", "S-1"),
                    Map.entry("partner_name", "供应商"), Map.entry("warehouse_code", "W-1"),
                    Map.entry("warehouse_name", "一号仓"), Map.entry("receiver_name", "验收人"),
                    Map.entry("project_manager_name", "项目负责人")));
            if (sql.contains("FROM mat_receipt_item i")) return List.of(Map.ofEntries(
                    Map.entry("material_name", "钢筋"), Map.entry("specification", "HRB400"),
                    Map.entry("unit", "吨"), Map.entry("order_quantity", new BigDecimal("10")),
                    Map.entry("received_quantity", new BigDecimal("4")), Map.entry("qualified_quantity", new BigDecimal("2")),
                    Map.entry("unit_price", new BigDecimal("617.25")), Map.entry("amount", new BigDecimal("1234.50")),
                    Map.entry("use_location", "主体"), Map.entry("remark", "抽检合格")));
            return List.of();
        });

        DocumentDataSnapshot snapshot = provider.loadPreview(201L);

        assertEquals("material-receipt.v1", snapshot.schemaVersion());
        assertEquals("PO-1", map(snapshot.values(), "order").get("code"));
        assertEquals("供应商", map(snapshot.values(), "supplier").get("name"));
        assertEquals("2", rows(snapshot.values(), "items").get(0).get("acceptedQuantity"));
        assertEquals("1234.50", map(snapshot.values(), "receipt").get("totalAmount"));
        assertEquals("", map(snapshot.values(), "signatures").get("supplierRepresentative"));
        assertEquals("验收人", map(snapshot.values(), "signatures").get("receiver"));
    }

    @SuppressWarnings("unchecked") private Map<String, Object> map(Map<String, Object> root, String key) {
        return (Map<String, Object>) root.get(key);
    }
    @SuppressWarnings("unchecked") private List<Map<String, Object>> rows(Map<String, Object> root, String key) {
        return (List<Map<String, Object>>) root.get(key);
    }
}
