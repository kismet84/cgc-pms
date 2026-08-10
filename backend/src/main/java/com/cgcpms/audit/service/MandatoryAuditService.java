package com.cgcpms.audit.service;

import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.cgcpms.auth.context.UserContext;
import com.cgcpms.common.exception.BusinessException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Collection;
import java.util.HexFormat;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

@Service
public class MandatoryAuditService {
    private static final String REVENUE_TABLE = "revenue_audit_event";
    private static final String FINANCE_TABLE = "finance_audit_event";
    private static final String REVENUE_DOMAIN = "REVENUE";
    private static final String FINANCE_DOMAIN = "FINANCE";

    private final JdbcTemplate jdbc;
    private final ObjectMapper objectMapper;
    private final ObjectWriter canonicalWriter;

    public MandatoryAuditService(JdbcTemplate jdbc, ObjectMapper objectMapper) {
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
        this.canonicalWriter = objectMapper.writer()
                .with(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS);
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void revenue(String eventType, String businessType, Long businessId, Long projectId,
                        String commandKey, Object payload) {
        write(REVENUE_TABLE, REVENUE_DOMAIN, eventType, businessType, businessId, projectId, commandKey, payload);
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void finance(String eventType, String businessType, Long businessId, Long projectId,
                        String commandKey, Object payload) {
        write(FINANCE_TABLE, FINANCE_DOMAIN, eventType, businessType, businessId, projectId, commandKey, payload);
    }

    public void verifyRevenue(String eventType, String businessType, Long businessId,
                              String commandKey, Object payload) {
        verify(REVENUE_TABLE, REVENUE_DOMAIN, eventType, businessType, businessId, commandKey, payload);
    }

    public void verifyFinance(String eventType, String businessType, Long businessId,
                              String commandKey, Object payload) {
        verify(FINANCE_TABLE, FINANCE_DOMAIN, eventType, businessType, businessId, commandKey, payload);
    }

    /** 月结检查：V290 起逐命令键反查缺事件，并复核事件与冻结摘要。 */
    public IntegrityReport inspectTenant() {
        Long tenantId = tenant();
        int missing = missingExpected(REVENUE_TABLE, REVENUE_DOMAIN, tenantId)
                + missingExpected(FINANCE_TABLE, FINANCE_DOMAIN, tenantId);
        int integrity = hashMismatches(REVENUE_TABLE, tenantId)
                + hashMismatches(FINANCE_TABLE, tenantId)
                + expectedHashMismatches(REVENUE_TABLE, REVENUE_DOMAIN, tenantId)
                + expectedHashMismatches(FINANCE_TABLE, FINANCE_DOMAIN, tenantId)
                + orphanEvents(REVENUE_TABLE, REVENUE_DOMAIN, tenantId)
                + orphanEvents(FINANCE_TABLE, FINANCE_DOMAIN, tenantId);
        return new IntegrityReport(missing, integrity);
    }

    private void write(String table, String domain, String eventType, String businessType, Long businessId,
                       Long projectId, String commandKey, Object payload) {
        requireIdentity(businessId, commandKey);
        String normalizedKey = commandKey.trim();
        String body = json(payload);
        String hash = sha256(body);
        try {
            jdbc.update("INSERT INTO " + table + "(id,tenant_id,event_type,business_type,business_id,command_key,project_id,operator_id,event_at,archive_bucket,payload_json,payload_hash) VALUES(?,?,?,?,?,?,?,?,CURRENT_TIMESTAMP,'HOT',?,?)",
                    IdWorker.getId(), tenant(), eventType, businessType, businessId,
                    normalizedKey, projectId, UserContext.getCurrentUserId(), body, hash);
        } catch (DuplicateKeyException duplicate) {
            verifyExisting(table, eventType, businessType, businessId, normalizedKey, body);
            verifyExpectation(domain, eventType, businessType, businessId, normalizedKey, hash);
            return;
        }
        try {
            int inserted = jdbc.update("""
                    INSERT INTO mandatory_audit_expectation
                        (id,tenant_id,audit_domain,event_type,business_type,business_id,command_key,project_id,expected_hash,expected_at)
                    VALUES(?,?,?,?,?,?,?,?,?,CURRENT_TIMESTAMP)
                    """, IdWorker.getId(), tenant(), domain, eventType, businessType, businessId,
                    normalizedKey, projectId, hash);
            if (inserted != 1) {
                throw new BusinessException("MANDATORY_AUDIT_EXPECTATION_WRITE_FAILED", "关键领域审计分母写入失败");
            }
        } catch (DuplicateKeyException duplicate) {
            throw new BusinessException("MANDATORY_AUDIT_EXPECTATION_CONFLICT", "关键领域审计分母已存在但事件缺失");
        }
    }

    private void verify(String table, String domain, String eventType, String businessType, Long businessId,
                        String commandKey, Object payload) {
        requireIdentity(businessId, commandKey);
        String normalizedKey = commandKey.trim();
        String body = json(payload);
        verifyExisting(table, eventType, businessType, businessId, normalizedKey, body);
        verifyExpectation(domain, eventType, businessType, businessId, normalizedKey, sha256(body));
    }

    private void verifyExisting(String table, String eventType, String businessType, Long businessId,
                                String commandKey, String expectedBody) {
        List<Map<String, Object>> rows = jdbc.queryForList("SELECT payload_json,payload_hash FROM " + table
                        + " WHERE tenant_id=? AND event_type=? AND business_type=? AND business_id=? AND command_key=?",
                tenant(), eventType, businessType, businessId, commandKey);
        if (rows.size() != 1) {
            throw new BusinessException("MANDATORY_AUDIT_EVENT_MISSING", "关键领域审计事件缺失");
        }
        String storedBody = Objects.toString(rows.getFirst().get("payload_json"), null);
        String storedHash = Objects.toString(rows.getFirst().get("payload_hash"), null);
        if (!validPayload(storedBody, storedHash)) {
            throw new BusinessException("MANDATORY_AUDIT_INTEGRITY_VIOLATION", "关键领域审计摘要校验失败");
        }
        try {
            JsonNode expected = objectMapper.readTree(expectedBody);
            JsonNode stored = objectMapper.readTree(storedBody);
            if (!sameJson(expected, stored)) {
                throw new BusinessException("MANDATORY_AUDIT_IDEMPOTENCY_CONFLICT", "关键领域审计命令键已被不同事实使用");
            }
        } catch (JsonProcessingException invalidStoredPayload) {
            throw new BusinessException("MANDATORY_AUDIT_INTEGRITY_VIOLATION", "关键领域审计载荷无法解析");
        }
    }

    private void verifyExpectation(String domain, String eventType, String businessType, Long businessId,
                                   String commandKey, String expectedHash) {
        List<String> hashes = jdbc.queryForList("""
                SELECT expected_hash FROM mandatory_audit_expectation
                 WHERE tenant_id=? AND audit_domain=? AND event_type=? AND business_type=?
                   AND business_id=? AND command_key=?
                """, String.class, tenant(), domain, eventType, businessType, businessId, commandKey);
        if (hashes.size() != 1) {
            throw new BusinessException("MANDATORY_AUDIT_EXPECTATION_MISSING", "关键领域审计分母缺失");
        }
        if (!expectedHash.equalsIgnoreCase(hashes.getFirst())) {
            throw new BusinessException("MANDATORY_AUDIT_INTEGRITY_VIOLATION", "关键领域审计分母摘要不一致");
        }
    }

    private void requireIdentity(Long businessId, String commandKey) {
        if (businessId == null) {
            throw new BusinessException("MANDATORY_AUDIT_BUSINESS_ID_REQUIRED", "关键领域审计缺少业务 ID");
        }
        if (commandKey == null || commandKey.isBlank()) {
            throw new BusinessException("MANDATORY_AUDIT_COMMAND_KEY_REQUIRED", "关键领域审计缺少命令键");
        }
    }

    private int missingExpected(String table, String domain, Long tenantId) {
        Number value = jdbc.queryForObject("""
                SELECT COUNT(*) FROM mandatory_audit_expectation x
                LEFT JOIN %s a ON a.tenant_id=x.tenant_id AND a.event_type=x.event_type
                    AND a.business_type=x.business_type AND a.business_id=x.business_id
                    AND a.command_key=x.command_key
                WHERE x.tenant_id=? AND x.audit_domain=? AND a.id IS NULL
                """.formatted(table), Number.class, tenantId, domain);
        return value == null ? 0 : value.intValue();
    }

    private int orphanEvents(String table, String domain, Long tenantId) {
        Number value = jdbc.queryForObject("""
                SELECT COUNT(*) FROM %s a
                 WHERE a.tenant_id=? AND a.command_key IS NOT NULL
                   AND NOT EXISTS(SELECT 1 FROM mandatory_audit_expectation x
                                   WHERE x.tenant_id=a.tenant_id AND x.audit_domain=?
                                     AND x.event_type=a.event_type AND x.business_type=a.business_type
                                     AND x.business_id=a.business_id AND x.command_key=a.command_key)
                """.formatted(table), Number.class, tenantId, domain);
        return value == null ? 0 : value.intValue();
    }

    private int expectedHashMismatches(String table, String domain, Long tenantId) {
        int mismatches = 0;
        for (Map<String, Object> row : jdbc.queryForList("""
                SELECT a.payload_json,a.payload_hash,x.expected_hash
                  FROM mandatory_audit_expectation x
                  JOIN %s a ON a.tenant_id=x.tenant_id AND a.event_type=x.event_type
                       AND a.business_type=x.business_type AND a.business_id=x.business_id
                       AND a.command_key=x.command_key
                 WHERE x.tenant_id=? AND x.audit_domain=?
                """.formatted(table), tenantId, domain)) {
            String body = Objects.toString(row.get("payload_json"), null);
            String hash = Objects.toString(row.get("payload_hash"), null);
            String expectedHash = Objects.toString(row.get("expected_hash"), null);
            if (validPayload(body, hash) && !hash.equalsIgnoreCase(expectedHash)) {
                mismatches++;
            }
        }
        return mismatches;
    }

    private int hashMismatches(String table, Long tenantId) {
        int mismatches = 0;
        for (Map<String, Object> row : jdbc.queryForList(
                "SELECT payload_json,payload_hash FROM " + table + " WHERE tenant_id=? AND command_key IS NOT NULL",
                tenantId)) {
            if (!validPayload(Objects.toString(row.get("payload_json"), null),
                    Objects.toString(row.get("payload_hash"), null))) {
                mismatches++;
            }
        }
        return mismatches;
    }

    private boolean validPayload(String body, String hash) {
        if (body == null || hash == null || !sha256(body).equalsIgnoreCase(hash)) return false;
        try {
            return objectMapper.readTree(body) != null;
        } catch (JsonProcessingException invalidJson) {
            return false;
        }
    }

    private String json(Object payload) {
        try {
            return canonicalWriter.writeValueAsString(canonicalValue(payload == null ? Map.of() : payload));
        } catch (JsonProcessingException e) {
            throw new BusinessException("MANDATORY_AUDIT_PAYLOAD_INVALID", "关键领域审计载荷无法序列化");
        }
    }

    private Object canonicalValue(Object value) {
        if (value instanceof BigDecimal decimal) {
            return decimal.signum() == 0 ? BigDecimal.ZERO : decimal.stripTrailingZeros();
        }
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> sorted = new TreeMap<>();
            map.forEach((key, item) -> sorted.put(String.valueOf(key), canonicalValue(item)));
            return sorted;
        }
        if (value instanceof Collection<?> collection) {
            return collection.stream().map(this::canonicalValue).toList();
        }
        return value;
    }

    private boolean sameJson(JsonNode left, JsonNode right) {
        if (left == null || right == null) return left == right;
        if (left.isNumber() && right.isNumber()) {
            return left.decimalValue().compareTo(right.decimalValue()) == 0;
        }
        if (left.isObject() && right.isObject()) {
            if (left.size() != right.size()) return false;
            Iterator<String> names = left.fieldNames();
            while (names.hasNext()) {
                String name = names.next();
                if (!right.has(name) || !sameJson(left.get(name), right.get(name))) return false;
            }
            return true;
        }
        if (left.isArray() && right.isArray()) {
            if (left.size() != right.size()) return false;
            for (int index = 0; index < left.size(); index++) {
                if (!sameJson(left.get(index), right.get(index))) return false;
            }
            return true;
        }
        return left.equals(right);
    }

    private String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception impossible) {
            throw new IllegalStateException("SHA-256 unavailable", impossible);
        }
    }

    private Long tenant() {
        Long tenantId = UserContext.getCurrentTenantId();
        if (tenantId == null) {
            throw new BusinessException("TENANT_CONTEXT_REQUIRED", "缺少租户上下文");
        }
        return tenantId;
    }

    public record IntegrityReport(int missingEvents, int hashMismatches) {
        public int issueCount() {
            return missingEvents + hashMismatches;
        }
    }
}
