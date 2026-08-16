package com.cgcpms.common.service;

import com.cgcpms.auth.context.UserContext;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Resolves internal source ids to tenant-scoped business codes for display.
 */
@Service
@RequiredArgsConstructor
public class BusinessReferenceService {

    private static final Map<String, Reference> REFERENCES = Map.ofEntries(
            Map.entry("BID_COST", new Reference("bid_cost", "bid_code")),
            Map.entry("CT_CONTRACT", new Reference("ct_contract", "contract_code")),
            Map.entry("CT_CHANGE", new Reference("ct_contract_change", "change_code")),
            Map.entry("VAR_ORDER", new Reference("var_order", "var_code")),
            Map.entry("SUB_MEASURE", new Reference("sub_measure", "measure_code")),
            Map.entry("MAT_RECEIPT", new Reference("mat_receipt", "receipt_code")),
            Map.entry("MAT_REQUISITION", new Reference("mat_requisition", "requisition_code")),
            Map.entry("STOCK_TRANSFER", new Reference("mat_stock_transfer", "idempotency_key")),
            Map.entry("MATERIAL_RETURN", new Reference("mat_material_return", "return_code")),
            Map.entry("MATERIAL_RETURN_REVERSAL", new Reference("mat_material_return", "return_code")),
            Map.entry("COST_RECALCULATION_NEGATIVE", new Reference("cost_recalculation_batch", "batch_code")),
            Map.entry("COST_RECALCULATION_POSITIVE", new Reference("cost_recalculation_batch", "batch_code")),
            Map.entry("COST_RECALCULATION_REVERSAL", new Reference("cost_recalculation_batch", "batch_code")),
            Map.entry("EXPENSE_APPLICATION", new Reference("expense_application", "expense_code")),
            Map.entry("QS_ISSUE", new Reference("qs_issue", "issue_code"))
    );

    private final JdbcTemplate jdbcTemplate;

    public Map<Long, String> resolve(String sourceType, Collection<Long> sourceIds) {
        Reference reference = REFERENCES.get(sourceType);
        var ids = sourceIds.stream().filter(Objects::nonNull).distinct().toList();
        if (reference == null || ids.isEmpty()) return Map.of();

        String placeholders = String.join(",", java.util.Collections.nCopies(ids.size(), "?"));
        String sql = "SELECT id, " + reference.codeColumn()
                + " FROM " + reference.tableName()
                + " WHERE tenant_id = ? AND id IN (" + placeholders + ")";
        Object[] args = new Object[ids.size() + 1];
        args[0] = UserContext.getCurrentTenantId();
        for (int index = 0; index < ids.size(); index++) args[index + 1] = ids.get(index);

        Map<Long, String> result = new LinkedHashMap<>();
        jdbcTemplate.query(sql, rs -> {
            String code = rs.getString(2);
            if (code != null && !code.isBlank()) result.put(rs.getLong(1), code);
        }, args);
        return result;
    }

    private record Reference(String tableName, String codeColumn) {
    }
}
