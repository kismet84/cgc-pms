package com.cgcpms.document;

import com.cgcpms.auth.context.UserContext;
import com.cgcpms.common.exception.BusinessException;
import com.cgcpms.document.provider.DocumentDataSnapshot;
import com.cgcpms.document.provider.PurchaseOrderDocumentDataProvider;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PurchaseOrderDocumentDataProviderTest {
    private JdbcTemplate jdbc;
    private PurchaseOrderDocumentDataProvider provider;

    @BeforeEach
    void setUp() {
        jdbc = mock(JdbcTemplate.class);
        provider = new PurchaseOrderDocumentDataProvider(jdbc);
        UserContext.restore(new UserContext.Snapshot(9L, "buyer", 7L, List.of()));
    }

    @AfterEach void clear() { UserContext.clear(); }

    @Test
    void formalSnapshotUsesServerPriceAmountAndTraceableCommercialContext() {
        when(jdbc.queryForList(anyString(), org.mockito.ArgumentMatchers.any(Object[].class)))
                .thenAnswer(invocation -> {
                    String sql = invocation.getArgument(0);
                    if (sql.contains("FROM mat_purchase_order o")) return List.of(Map.ofEntries(
                            Map.entry("id", 301L), Map.entry("order_code", "PO-301"),
                            Map.entry("order_type", "PURCHASE"), Map.entry("order_date", LocalDate.of(2026, 8, 1)),
                            Map.entry("delivery_date", LocalDate.of(2026, 8, 10)), Map.entry("delivery_terms", "到场交付"),
                            Map.entry("total_amount", new BigDecimal("35000.00")), Map.entry("approval_status", "APPROVED"),
                            Map.entry("order_status", "PERFORMING"), Map.entry("pricing_mode", "ACTUAL"),
                            Map.entry("budget_revision", 2), Map.entry("project_code", "P-1"),
                            Map.entry("project_name", "一号项目"), Map.entry("request_code", "PR-1"),
                            Map.entry("contract_code", "CT-1"), Map.entry("contract_name", "采购合同"),
                            Map.entry("partner_code", "S-1"), Map.entry("partner_name", "供应商")));
                    if (sql.contains("FROM mat_purchase_order_item i")) return List.of(Map.ofEntries(
                            Map.entry("material_name", "钢筋"), Map.entry("specification", "HRB400"),
                            Map.entry("unit", "吨"), Map.entry("quantity", new BigDecimal("10")),
                            Map.entry("unit_price", new BigDecimal("3500")), Map.entry("tax_rate", new BigDecimal("0.13")),
                            Map.entry("amount", new BigDecimal("35000")), Map.entry("tax_amount", new BigDecimal("4026.55")),
                            Map.entry("amount_without_tax", new BigDecimal("30973.45")),
                            Map.entry("quantity_adjust_reason", "现场调整"), Map.entry("contract_item_id", 41L),
                            Map.entry("price_source", "RECENT_RECEIPT"),
                            Map.entry("price_source_receipt_item_id", 51L), Map.entry("price_source_receipt_code", "RC-9"),
                            Map.entry("budget_line_id", 61L), Map.entry("budget_subject_code", "5401.02.02"),
                            Map.entry("budget_subject_name", "材料采购费"), Map.entry("wbs_task_code", "WBS-1"),
                            Map.entry("wbs_task_name", "主体施工"), Map.entry("remark", "服务端快照")));
                    if (sql.contains("FROM wf_instance")) return List.of(Map.of(
                            "instance_status", "APPROVED", "initiator_id", 9L,
                            "started_at", LocalDateTime.of(2026, 8, 1, 9, 0),
                            "ended_at", LocalDateTime.of(2026, 8, 1, 12, 0)));
                    return List.of();
                });

        DocumentDataSnapshot snapshot = provider.load(301L);

        assertEquals("purchase-order.v1", snapshot.schemaVersion());
        assertEquals("ACTUAL", map(snapshot.values(), "purchaseOrder").get("pricingMode"));
        assertEquals("35000.00", map(snapshot.values(), "purchaseOrder").get("totalAmount"));
        Map<String, Object> item = rows(snapshot.values(), "items").get(0);
        assertEquals("3500.00", item.get("unitPrice"));
        assertEquals("RECENT_RECEIPT", map(item, "priceSource").get("type"));
        assertEquals("RC-9", map(item, "priceSource").get("receiptCode"));
        assertEquals("材料采购费", map(item, "budget").get("subjectName"));
        assertEquals("主体施工", map(item, "wbs").get("name"));
    }

    @Test
    void draftCanPreviewButCannotGenerateFormalDocument() {
        AtomicReference<String> approvalStatus = new AtomicReference<>("DRAFT");
        when(jdbc.queryForList(anyString(), org.mockito.ArgumentMatchers.any(Object[].class)))
                .thenAnswer(invocation -> {
                    String sql = invocation.getArgument(0);
                    if (sql.contains("FROM mat_purchase_order o")) return List.of(Map.ofEntries(
                            Map.entry("id", 302L), Map.entry("order_code", "PO-302"),
                            Map.entry("approval_status", approvalStatus.get()), Map.entry("order_status", "DRAFT"),
                            Map.entry("pricing_mode", "FIXED"), Map.entry("total_amount", BigDecimal.ZERO),
                            Map.entry("project_name", "一号项目"), Map.entry("contract_name", "采购合同"),
                            Map.entry("partner_name", "供应商")));
                    return List.of();
                });

        assertEquals("PO-302", map(provider.loadPreview(302L).values(), "purchaseOrder").get("orderCode"));
        approvalStatus.set("REJECTED");
        assertEquals("PO-302", map(provider.loadPreview(302L).values(), "purchaseOrder").get("orderCode"));
        BusinessException error = assertThrows(BusinessException.class, () -> provider.load(302L));
        assertEquals("DOCUMENT_PURCHASE_ORDER_NOT_APPROVED", error.getCode());
    }

    @SuppressWarnings("unchecked") private Map<String, Object> map(Map<String, Object> root, String key) {
        return (Map<String, Object>) root.get(key);
    }
    @SuppressWarnings("unchecked") private List<Map<String, Object>> rows(Map<String, Object> root, String key) {
        return (List<Map<String, Object>>) root.get(key);
    }
}
