package com.cgcpms.config;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

@SpringBootTest
@ActiveProfiles("local")
class DecimalScaleMigrationTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void allBusinessDecimalsUseTwoPlacesAndH2DefaultsSurviveTypeChanges() {
        Integer nonTwoScaleColumns = jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM information_schema.columns
                WHERE table_schema = 'public'
                  AND data_type IN ('NUMERIC', 'DECIMAL')
                  AND numeric_scale <> 2
                """, Integer.class);
        assertEquals(0, nonTwoScaleColumns);

        Map<String, String> defaults = Map.ofEntries(
                Map.entry("cash_forecast.confidence", "1.00"),
                Map.entry("collection_forecast.confidence", "1.00"),
                Map.entry("cost_forecast.profit_margin", "0.00"),
                Map.entry("cost_summary.profit_margin", "0.00"),
                Map.entry("ct_contract_item.quantity", "0.00"),
                Map.entry("ct_contract_item.unit_price", "0.00"),
                Map.entry("mat_purchase_order_item.tax_rate", "0.00"),
                Map.entry("mat_purchase_order_item.received_quantity", "0.00"),
                Map.entry("mat_quality_disposition.resolved_quantity", "0.00"),
                Map.entry("mat_receipt_item.unqualified_quantity", "0.00"),
                Map.entry("mat_requisition.total_amount", "0.00"),
                Map.entry("mat_requisition_item.quantity", "0.00"),
                Map.entry("mat_requisition_item.unit_price", "0.00"),
                Map.entry("mat_requisition_item.amount", "0.00"),
                Map.entry("mat_stock.available_qty", "0.00"),
                Map.entry("mat_stock.average_unit_cost", "0.00"),
                Map.entry("mat_stock.safety_stock_qty", "10.00"),
                Map.entry("mat_stock_transfer.unit_cost", "0.00"),
                Map.entry("mat_stock_txn.available_after", "0.00"),
                Map.entry("mat_stock_txn.unit_cost", "0.00"),
                Map.entry("overhead_allocation_record.allocation_ratio", "0.00"),
                Map.entry("production_measurement_line.prior_approved_quantity", "0.00"),
                Map.entry("project_wbs_task.actual_quantity", "0.00"),
                Map.entry("project_wbs_task.actual_progress", "0.00"),
                Map.entry("site_daily_progress.completed_quantity", "0.00"),
                Map.entry("sp_supplier_quote.tax_rate", "0.00")
        );
        defaults.forEach((column, expected) -> {
            String[] parts = column.split("\\.", 2);
            String actual = jdbcTemplate.queryForObject("""
                    SELECT column_default FROM information_schema.columns
                    WHERE table_schema = 'public' AND table_name = ? AND column_name = ?
                    """, String.class, parts[0], parts[1]);
            assertEquals(0, new BigDecimal(expected).compareTo(new BigDecimal(actual)), column);
        });
    }
}
