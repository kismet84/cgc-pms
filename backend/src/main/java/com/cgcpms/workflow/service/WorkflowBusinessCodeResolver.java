package com.cgcpms.workflow.service;

import com.cgcpms.workflow.entity.WfInstance;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Resolves workflow business primary keys to their persisted, user-facing codes.
 */
@Component
@RequiredArgsConstructor
public class WorkflowBusinessCodeResolver {

    private static final Map<String, CodeSource> SOURCES = Map.ofEntries(
            Map.entry("CONTRACT_APPROVAL", new CodeSource("ct_contract", "contract_code", true)),
            Map.entry("PROJECT_APPROVAL", new CodeSource("pm_project", "project_code", true)),
            Map.entry("PURCHASE_ORDER", new CodeSource("mat_purchase_order", "order_code", true)),
            Map.entry("MATERIAL_RECEIPT", new CodeSource("mat_receipt", "receipt_code", true)),
            Map.entry("SUB_MEASURE", new CodeSource("sub_measure", "measure_code", true)),
            Map.entry("PAY_REQUEST", new CodeSource("pay_application", "apply_code", true)),
            Map.entry("VAR_ORDER", new CodeSource("var_order", "var_code", true)),
            Map.entry("PURCHASE_REQUEST", new CodeSource("mat_purchase_request", "request_code", true)),
            Map.entry("CT_CHANGE", new CodeSource("ct_contract_change", "change_code", true)),
            Map.entry("SETTLEMENT", new CodeSource("stl_settlement", "settlement_code", true)),
            Map.entry("COST_TARGET", new CodeSource("cost_target", "version_no", true)),
            Map.entry("CONTRACT_REVENUE", new CodeSource("contract_revenue", "revenue_code", true)),
            Map.entry("MATERIAL_REQUISITION", new CodeSource("mat_requisition", "requisition_code", true)),
            Map.entry("TECH_ITEM", new CodeSource("tech_item", "item_code", true)),
            Map.entry("PROJECT_BUDGET", new CodeSource("project_budget", "version_no", true)),
            Map.entry("EXPENSE", new CodeSource("expense_application", "expense_code", true)),
            Map.entry("OWNER_SETTLEMENT", new CodeSource("owner_settlement", "settlement_code", true)),
            Map.entry("PRODUCTION_MEASUREMENT", new CodeSource("production_measurement", "measure_code", true)),
            Map.entry("PROJECT_SCHEDULE", new CodeSource("project_schedule_plan", "plan_code", true)),
            Map.entry("PROJECT_PERIOD_PLAN", new CodeSource("project_period_plan", "period_code", true)),
            Map.entry("PROJECT_CORRECTIVE_ACTION", new CodeSource("project_corrective_action", "action_code", true)),
            Map.entry("COST_CORRECTIVE_ACTION", new CodeSource("cost_corrective_action", "action_code", true)),
            Map.entry("TECHNICAL_SCHEME", new CodeSource("technical_scheme", "scheme_code", true)),
            Map.entry("PROJECT_FINAL_ACCEPTANCE", new CodeSource("closeout_final_acceptance", "acceptance_code", true)),
            Map.entry("COST_SUBJECT_MAPPING", new CodeSource("cost_subject_mapping_version", "version_code", false)),
            Map.entry("BID_COST_TARGET_TRANSFER", new CodeSource("bid_cost_target_transfer", "transfer_code", false)),
            Map.entry("BID_COST_TARGET_TRANSFER_REVERSAL", new CodeSource("bid_cost_target_transfer", "transfer_code", false))
    );

    private final JdbcTemplate jdbcTemplate;

    public Map<Long, String> resolveByInstanceId(Long tenantId, Collection<WfInstance> instances) {
        if (tenantId == null || instances == null || instances.isEmpty()) {
            return Map.of();
        }

        Map<CodeSource, List<WfInstance>> grouped = new LinkedHashMap<>();
        for (WfInstance instance : instances) {
            if (instance == null || instance.getId() == null || instance.getBusinessId() == null) continue;
            CodeSource source = SOURCES.get(instance.getBusinessType());
            if (source != null) {
                grouped.computeIfAbsent(source, ignored -> new ArrayList<>()).add(instance);
            }
        }

        Map<Long, String> result = new HashMap<>();
        grouped.forEach((source, sourceInstances) -> resolveSource(
                tenantId, source, sourceInstances, result));
        return result;
    }

    public Set<Long> findInstanceIds(Long tenantId, String keyword, String businessType,
                                     String instanceStatus) {
        if (tenantId == null || !StringUtils.hasText(keyword)) return Set.of();

        List<Map.Entry<String, CodeSource>> selectedSources = SOURCES.entrySet().stream()
                .filter(entry -> !StringUtils.hasText(businessType)
                        || entry.getKey().equals(businessType))
                .toList();
        if (selectedSources.isEmpty()) return Set.of();

        String sql = selectedSources.stream()
                .map(entry -> {
                    CodeSource source = entry.getValue();
                    return "SELECT wi.id FROM wf_instance wi JOIN " + source.tableName()
                            + " biz ON biz.id = wi.business_id"
                            + " AND biz.tenant_id = wi.tenant_id"
                            + " WHERE wi.tenant_id = ? AND wi.deleted_flag = 0"
                            + " AND wi.business_type = ? AND biz." + source.codeColumn() + " LIKE ?"
                            + (source.softDeleted() ? " AND biz.deleted_flag = 0" : "")
                            + (StringUtils.hasText(instanceStatus) ? " AND wi.instance_status = ?" : "");
                })
                .collect(Collectors.joining(" UNION ALL "));

        Set<Long> result = new java.util.HashSet<>();
        jdbcTemplate.query(sql, statement -> {
            int parameter = 1;
            for (Map.Entry<String, CodeSource> entry : selectedSources) {
                statement.setLong(parameter++, tenantId);
                statement.setString(parameter++, entry.getKey());
                statement.setString(parameter++, "%" + keyword.trim() + "%");
                if (StringUtils.hasText(instanceStatus)) {
                    statement.setString(parameter++, instanceStatus);
                }
            }
        }, resultSet -> {
            result.add(resultSet.getLong(1));
        });
        return result;
    }

    private void resolveSource(Long tenantId, CodeSource source, List<WfInstance> instances,
                               Map<Long, String> result) {
        List<Long> businessIds = instances.stream()
                .map(WfInstance::getBusinessId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        if (businessIds.isEmpty()) return;

        String placeholders = String.join(",", businessIds.stream().map(ignored -> "?").toList());
        String sql = "SELECT id, " + source.codeColumn() + " AS business_code FROM "
                + source.tableName() + " WHERE tenant_id = ? AND id IN (" + placeholders + ")"
                + (source.softDeleted() ? " AND deleted_flag = 0" : "");

        Map<Long, String> codesByBusinessId = new HashMap<>();
        jdbcTemplate.query(sql, statement -> {
            statement.setLong(1, tenantId);
            for (int index = 0; index < businessIds.size(); index++) {
                statement.setLong(index + 2, businessIds.get(index));
            }
        }, resultSet -> {
            String code = resultSet.getString("business_code");
            if (StringUtils.hasText(code)) {
                codesByBusinessId.put(resultSet.getLong("id"), code.trim());
            }
        });

        for (WfInstance instance : instances) {
            String code = codesByBusinessId.get(instance.getBusinessId());
            if (code != null) result.put(instance.getId(), code);
        }
    }

    private record CodeSource(String tableName, String codeColumn, boolean softDeleted) {
    }
}
