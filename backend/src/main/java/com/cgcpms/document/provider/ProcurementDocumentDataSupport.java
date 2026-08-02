package com.cgcpms.document.provider;

import com.cgcpms.auth.context.UserContext;
import com.cgcpms.common.exception.BusinessException;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.temporal.TemporalAccessor;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

abstract class ProcurementDocumentDataSupport {
    protected final JdbcTemplate jdbc;

    protected ProcurementDocumentDataSupport(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    protected Long tenantId() {
        Long tenantId = UserContext.getCurrentTenantId();
        if (tenantId == null) throw new BusinessException("TENANT_CONTEXT_MISSING", "缺少租户上下文");
        return tenantId;
    }

    protected Map<String, Object> one(String sql, Object... args) {
        List<Map<String, Object>> rows = jdbc.queryForList(sql, args);
        if (rows.isEmpty()) throw new BusinessException("DOCUMENT_BUSINESS_NOT_FOUND", "采购业务单据不存在");
        return rows.get(0);
    }

    protected List<Map<String, Object>> query(String sql, Object... args) {
        return jdbc.queryForList(sql, args);
    }

    protected Map<String, Object> row() { return new LinkedHashMap<>(); }

    protected void put(Map<String, Object> target, String key, Object value) {
        target.put(key, text(value));
    }

    protected void money(Map<String, Object> target, String key, Object value) {
        BigDecimal amount = decimalValue(value);
        target.put(key, amount == null ? "0.00" : amount.setScale(2, RoundingMode.HALF_UP).toPlainString());
    }

    protected void decimal(Map<String, Object> target, String key, Object value) {
        BigDecimal number = decimalValue(value);
        target.put(key, number == null ? "0" : number.stripTrailingZeros().toPlainString());
    }

    protected Object value(Map<String, Object> source, String key) {
        Object value = source.get(key);
        if (value == null) value = source.get(key.toUpperCase(Locale.ROOT));
        return value;
    }

    protected List<Map<String, Object>> attachments(String businessType, Long businessId, Long tenantId) {
        List<Map<String, Object>> result = new ArrayList<>();
        for (Map<String, Object> source : query("""
                SELECT original_name,document_type,file_size,created_at
                FROM sys_file
                WHERE tenant_id=? AND business_type=? AND business_id=? AND deleted_flag=0
                  AND document_type<>'GENERATED_DOCUMENT'
                ORDER BY created_at,id
                """, tenantId, businessType, businessId)) {
            Map<String, Object> row = row();
            put(row, "name", value(source, "original_name"));
            put(row, "type", value(source, "document_type"));
            put(row, "size", value(source, "file_size"));
            put(row, "createdAt", value(source, "created_at"));
            result.add(row);
        }
        return result;
    }

    protected List<Map<String, Object>> approvalRecords(String businessType, Long businessId, Long tenantId) {
        List<Map<String, Object>> result = new ArrayList<>();
        for (Map<String, Object> source : query("""
                SELECT node_code,node_name,action_type,action_name,operator_id,operator_name,comment,created_at
                FROM wf_record
                WHERE tenant_id=? AND business_type=? AND business_id=? AND record_status='EFFECTIVE'
                  AND deleted_flag=0
                ORDER BY round_no,created_at,id
                """, tenantId, businessType, businessId)) {
            Map<String, Object> row = row();
            put(row, "nodeCode", value(source, "node_code"));
            put(row, "node", value(source, "node_name"));
            put(row, "actionType", value(source, "action_type"));
            put(row, "action", value(source, "action_name"));
            put(row, "operatorId", value(source, "operator_id"));
            put(row, "operator", value(source, "operator_name"));
            put(row, "comment", value(source, "comment"));
            put(row, "time", value(source, "created_at"));
            result.add(row);
        }
        return result;
    }

    protected Map<String, Object> workflow(String businessType, Long businessId, Long tenantId) {
        List<Map<String, Object>> rows = query("""
                SELECT instance_status,initiator_id,started_at,ended_at
                FROM wf_instance
                WHERE tenant_id=? AND business_type=? AND business_id=? AND deleted_flag=0
                ORDER BY id DESC
                """, tenantId, businessType, businessId);
        Map<String, Object> result = row();
        Map<String, Object> source = rows.isEmpty() ? Map.of() : rows.get(0);
        put(result, "status", value(source, "instance_status"));
        put(result, "initiatorId", value(source, "initiator_id"));
        put(result, "startedAt", value(source, "started_at"));
        put(result, "endedAt", value(source, "ended_at"));
        return result;
    }

    protected String chineseMoney(Object value) {
        BigDecimal amount = decimalValue(value);
        if (amount == null) return "零元整";
        long fen = amount.abs().setScale(2, RoundingMode.HALF_UP).movePointRight(2).longValueExact();
        if (fen == 0) return "零元整";
        String[] digits = {"零", "壹", "贰", "叁", "肆", "伍", "陆", "柒", "捌", "玖"};
        String[] units = {"分", "角", "元", "拾", "佰", "仟", "万", "拾", "佰", "仟", "亿", "拾", "佰", "仟", "万"};
        StringBuilder result = new StringBuilder();
        boolean zero = false;
        for (int i = 0; fen > 0 && i < units.length; i++, fen /= 10) {
            int digit = (int) (fen % 10);
            if (digit == 0) {
                if ((i == 2 || i == 6 || i == 10 || i == 14) && result.length() > 0) result.insert(0, units[i]);
                zero = result.length() > 0;
            } else {
                if (zero) result.insert(0, digits[0]);
                result.insert(0, digits[digit] + units[i]);
                zero = false;
            }
        }
        if (result.indexOf("分") < 0) result.append("整");
        return (amount.signum() < 0 ? "负" : "") + result;
    }

    private BigDecimal decimalValue(Object value) {
        if (value == null || value.toString().isBlank()) return null;
        return value instanceof BigDecimal decimal ? decimal : new BigDecimal(value.toString());
    }

    private String text(Object value) {
        if (value == null) return "";
        return value instanceof TemporalAccessor ? value.toString() : String.valueOf(value);
    }
}
