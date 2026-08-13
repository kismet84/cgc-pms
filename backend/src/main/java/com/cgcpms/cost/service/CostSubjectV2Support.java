package com.cgcpms.cost.service;

import com.cgcpms.auth.context.UserContext;
import com.cgcpms.common.exception.BusinessException;
import com.cgcpms.project.auth.ProjectAccessChecker;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Objects;

abstract class CostSubjectV2Support {

    protected final JdbcTemplate jdbc;
    protected final ProjectAccessChecker projectAccessChecker;

    CostSubjectV2Support(JdbcTemplate jdbc, ProjectAccessChecker projectAccessChecker) {
        this.jdbc = jdbc;
        this.projectAccessChecker = projectAccessChecker;
    }

    protected void requireApprovedWorkflow(Long approvalInstanceId, String businessType, Long businessId) {
        if (approvalInstanceId == null) throw new BusinessException("APPROVAL_REQUIRED", "必须绑定审批实例");
        List<Map<String, Object>> instances = jdbc.queryForList("""
                SELECT business_type,business_id,instance_status FROM wf_instance
                WHERE tenant_id=? AND id=? AND deleted_flag=0
                """, tenantId(), approvalInstanceId);
        if (instances.size() != 1 || !"APPROVED".equals(instances.get(0).get("instance_status"))) {
            throw new BusinessException("APPROVAL_NOT_APPROVED", "审批实例不存在或未通过");
        }
        if (businessType != null && !businessType.equals(instances.get(0).get("business_type"))) {
            throw new BusinessException("APPROVAL_BUSINESS_MISMATCH", "审批实例业务类型不匹配");
        }
        if (businessId != null && !Objects.equals(longValue(instances.get(0).get("business_id")), businessId)) {
            throw new BusinessException("APPROVAL_BUSINESS_MISMATCH", "审批实例业务单据不匹配");
        }
    }

    protected void requireWorkflowAmount(Long instanceId, String businessType, Long businessId, BigDecimal expected) {
        List<Map<String, Object>> instances = jdbc.queryForList("""
                SELECT amount FROM wf_instance
                WHERE tenant_id=? AND id=? AND business_type=? AND business_id=?
                  AND instance_status='RUNNING' AND deleted_flag=0
                """, tenantId(), instanceId, businessType, businessId);
        if (instances.size() != 1 || money(instances.getFirst().get("amount")).compareTo(expected) != 0) {
            throw new BusinessException("WORKFLOW_AMOUNT_MISMATCH", "审批金额与业务快照不一致");
        }
    }

    protected void requireMappingVersion(Long id, String status) {
        Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM cost_subject_mapping_version WHERE tenant_id=? AND id=? AND status=?",
                Integer.class, tenantId(), id, status);
        if (count == null || count != 1) throw new BusinessException("COST_SUBJECT_MAPPING_VERSION_INVALID", "成本科目映射版本不存在或状态不符");
    }

    protected void requireSubject(Long id, boolean leaf) {
        if (id == null) throw new BusinessException("COST_SUBJECT_REQUIRED", "成本科目不能为空");
        Integer count = jdbc.queryForObject("""
                SELECT COUNT(*) FROM cost_subject s WHERE s.tenant_id=? AND s.id=? AND s.deleted_flag=0
                  AND (?=0 OR (s.status='ENABLE' AND s.account_category='COST' AND NOT EXISTS (
                    SELECT 1 FROM cost_subject c WHERE c.tenant_id=s.tenant_id AND c.parent_id=s.id AND c.deleted_flag=0)))
                """, Integer.class, tenantId(), id, leaf ? 1 : 0);
        if (count == null || count != 1) throw new BusinessException("COST_SUBJECT_NOT_LEAF", leaf ? "成本归集必须使用启用的成本域末级科目" : "成本科目不存在");
    }

    protected void requireScope(Long projectId, Long subjectId) {
        Integer scoped = jdbc.queryForObject("SELECT COUNT(*) FROM project_cost_subject_scope WHERE tenant_id=? AND project_id=?",
                Integer.class, tenantId(), projectId);
        if (scoped != null && scoped > 0) {
            Integer allowed = jdbc.queryForObject("""
                    SELECT COUNT(*) FROM project_cost_subject_scope WHERE tenant_id=? AND project_id=? AND cost_subject_id=?
                      AND enabled=1 AND effective_from<=CURRENT_DATE AND (effective_to IS NULL OR effective_to>=CURRENT_DATE)
                    """, Integer.class, tenantId(), projectId, subjectId);
            if (allowed == null || allowed != 1) throw new BusinessException("COST_SUBJECT_NOT_IN_PROJECT_SCOPE", "成本科目不在项目适用范围内");
        }
    }

    protected void requireProject(Long projectId) {
        Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM pm_project WHERE tenant_id=? AND id=? AND deleted_flag=0",
                Integer.class, tenantId(), projectId);
        if (count == null || count != 1) throw new BusinessException("PROJECT_NOT_FOUND", "项目不存在");
        projectAccessChecker.checkAccess(projectId, "访问成本科目项目数据");
    }


    protected String placeholders(List<Long> values) {
        return String.join(",", values.stream().map(value -> "?").toList());
    }

    protected Map<String, Object> one(String sql, Long id) {
        List<Map<String, Object>> rows = jdbc.queryForList(sql, tenantId(), id);
        if (rows.size() != 1) throw new BusinessException("BUSINESS_SOURCE_NOT_FOUND", "业务来源不存在或不可用");
        return rows.get(0);
    }

    protected Long tenantId() {
        Long value = UserContext.getCurrentTenantId();
        if (value == null) throw new BusinessException("TENANT_CONTEXT_REQUIRED", "租户上下文缺失");
        return value;
    }

    protected Long userId() {
        Long value = UserContext.getCurrentUserId();
        if (value == null) throw new BusinessException("USER_CONTEXT_REQUIRED", "用户上下文缺失");
        return value;
    }

    protected static String textOrDefault(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
    }

    protected static void requireText(String value, String message) {
        if (value == null || value.isBlank()) throw new BusinessException("VALIDATION_ERROR", message);
    }

    protected static BigDecimal money(Object value) {
        if (value == null) return BigDecimal.ZERO;
        return value instanceof BigDecimal decimal ? decimal : new BigDecimal(String.valueOf(value));
    }

    protected static Long longValue(Object value) {
        return value == null ? null : ((Number) value).longValue();
    }

    protected static int intValue(Object value) {
        return value == null ? 0 : ((Number) value).intValue();
    }
}
