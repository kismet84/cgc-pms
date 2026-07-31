package com.cgcpms.common.util;

import com.cgcpms.auth.context.UserContext;
import com.cgcpms.common.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.List;

@Component
@RequiredArgsConstructor
public class BusinessCodeGenerator {
    private static final int MAX_SEQUENCE = 999;

    private final JdbcTemplate jdbc;

    public String next(Rule rule, Long scopeId, int offset) {
        if (rule.scopeColumn != null && scopeId == null) {
            throw new IllegalArgumentException("业务编码作用域不能为空: " + rule.name());
        }
        String fullPrefix = rule.prefix + "-" + LocalDate.now().format(DateTimeUtils.DATE_COMPACT) + "-";
        String sql = "SELECT " + rule.codeColumn + " FROM " + rule.tableName
                + " WHERE tenant_id=? AND " + rule.codeColumn + " LIKE ?"
                + (rule.scopeColumn == null ? "" : " AND " + rule.scopeColumn + "=?")
                + " ORDER BY " + rule.codeColumn + " DESC";
        Object[] args = rule.scopeColumn == null
                ? new Object[]{UserContext.getCurrentTenantId(), fullPrefix + "%"}
                : new Object[]{UserContext.getCurrentTenantId(), fullPrefix + "%", scopeId};
        List<String> rows = jdbc.queryForList(sql, String.class, args);
        int maxSequence = 0;
        for (String code : rows) {
            try {
                String suffix = code.substring(fullPrefix.length());
                if (suffix.length() == 3) {
                    maxSequence = Math.max(maxSequence, Integer.parseInt(suffix));
                }
            } catch (RuntimeException ignored) {
                // 非标准历史编号不阻止生成新编号。
            }
        }
        int sequence = maxSequence + 1 + offset;
        if (sequence > MAX_SEQUENCE) {
            throw new BusinessException("BUSINESS_CODE_SEQUENCE_EXHAUSTED", "当日业务编号已超过999条，请联系管理员");
        }
        return fullPrefix + String.format("%03d", sequence);
    }

    public enum Rule {
        WAREHOUSE("mat_warehouse", "warehouse_code", "WH", "project_id"),
        SOURCING("sp_sourcing_event", "sourcing_code", "SRC", null),
        SUPPLIER_RETURN("sp_supplier_return", "return_code", "SRT", null),
        SCHEDULE("project_schedule_plan", "plan_code", "SCH", "project_id"),
        SCHEDULE_PERIOD("project_period_plan", "period_code", "SPD", "project_id"),
        SCHEDULE_CORRECTIVE("project_corrective_action", "action_code", "SCA", "project_id"),
        QUALITY_PLAN("qs_inspection_plan", "plan_code", "QPL", "project_id"),
        QUALITY_INSPECTION("qs_inspection_record", "inspection_code", "QIN", "project_id"),
        QUALITY_CONSEQUENCE("qs_consequence", "consequence_code", "QCO", "project_id"),
        TECH_SCHEME("technical_scheme", "scheme_code", "TSC", null),
        TECH_REVIEW("tech_drawing_review", "review_code", "TRV", "project_id"),
        TECH_RFI("tech_rfi", "rfi_code", "RFI", null),
        TECH_DISCLOSURE("tech_disclosure", "disclosure_code", "TDS", "project_id"),
        TECH_ARCHIVE("tech_acceptance_archive", "archive_code", "TAR", "project_id"),
        PROJECT_CLOSEOUT("project_closeout", "closeout_code", "PC", null),
        SECTION_ACCEPTANCE("closeout_section_acceptance", "acceptance_code", "SA", null),
        FINAL_ACCEPTANCE("closeout_final_acceptance", "acceptance_code", "FA", null),
        WARRANTY("closeout_warranty", "warranty_code", "WAR", null),
        DEFECT("closeout_defect", "defect_code", "DEF", null),
        ARCHIVE_TRANSFER("closeout_archive_transfer", "transfer_code", "ATR", null),
        COST_FORECAST("cost_forecast", "forecast_code", "CFT", "project_id"),
        COST_CORRECTIVE("cost_corrective_action", "action_code", "CCA", "project_id");

        private final String tableName;
        private final String codeColumn;
        private final String prefix;
        private final String scopeColumn;

        Rule(String tableName, String codeColumn, String prefix, String scopeColumn) {
            this.tableName = tableName;
            this.codeColumn = codeColumn;
            this.prefix = prefix;
            this.scopeColumn = scopeColumn;
        }
    }
}
