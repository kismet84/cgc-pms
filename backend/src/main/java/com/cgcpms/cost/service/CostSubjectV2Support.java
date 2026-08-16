package com.cgcpms.cost.service;

import com.cgcpms.auth.context.UserContext;
import com.cgcpms.common.exception.BusinessException;
import com.cgcpms.project.auth.ProjectAccessChecker;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Objects;

abstract class CostSubjectV2Support {

    protected static final int APPROVAL_DETAIL_ROW_LIMIT = 1000;

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
        List<String> statuses = jdbc.query("""
                SELECT status FROM cost_subject_mapping_version
                WHERE tenant_id=? AND id=? FOR UPDATE
                """, (rs, rowNum) -> rs.getString(1), tenantId(), id);
        if (statuses.size() != 1 || !status.equals(statuses.getFirst())) {
            throw new BusinessException("COST_SUBJECT_MAPPING_VERSION_INVALID", "成本科目映射版本不存在或状态不符");
        }
    }

    protected void requireSubject(Long id, boolean leaf) {
        if (id == null) throw new BusinessException("COST_SUBJECT_REQUIRED", "成本科目不能为空");
        if (!leaf) {
            Integer count = jdbc.queryForObject("""
                    SELECT COUNT(*) FROM cost_subject
                    WHERE tenant_id=? AND id=? AND deleted_flag=0
                    """, Integer.class, tenantId(), id);
            if (count == null || count != 1) {
                throw new BusinessException("COST_SUBJECT_NOT_LEAF", "成本科目不存在");
            }
            return;
        }
        List<Map<String, Object>> subjects = jdbc.queryForList("""
                SELECT id,status,account_category FROM cost_subject
                WHERE tenant_id=? AND id=? AND deleted_flag=0 FOR UPDATE
                """, tenantId(), id);
        Integer children = subjects.size() == 1 ? jdbc.queryForObject("""
                SELECT COUNT(*) FROM cost_subject
                WHERE tenant_id=? AND parent_id=? AND deleted_flag=0
                """, Integer.class, tenantId(), id) : null;
        if (subjects.size() != 1
                || !"ENABLE".equals(String.valueOf(subjects.getFirst().get("status")))
                || !"COST".equals(String.valueOf(subjects.getFirst().get("account_category")))
                || children == null || children != 0) {
            throw new BusinessException("COST_SUBJECT_NOT_LEAF", "成本归集必须使用启用的成本域末级科目");
        }
    }

    protected void requireLeafSubjects(List<Long> ids) {
        if (ids == null) return;
        ids.stream().filter(Objects::nonNull).distinct().sorted()
                .forEach(id -> requireSubject(id, true));
    }

    protected void requireScope(Long projectId, Long subjectId) {
        requireScopeAt(projectId, subjectId, LocalDate.now());
    }

    protected void requireScopeAt(Long projectId, Long subjectId, LocalDate asOfDate) {
        Integer excluded = jdbc.queryForObject("""
                SELECT COUNT(*) FROM project_cost_subject_scope_history h
                WHERE h.tenant_id=? AND h.project_id=? AND h.cost_subject_id=? AND h.enabled=0
                  AND h.configuration_version=(
                    SELECT MAX(latest.configuration_version) FROM project_cost_subject_scope_history latest
                    WHERE latest.tenant_id=h.tenant_id AND latest.project_id=h.project_id
                      AND latest.cost_subject_id=h.cost_subject_id AND latest.effective_from<=?
                      AND (latest.effective_to IS NULL OR latest.effective_to>=?))
                """, Integer.class, tenantId(), projectId, subjectId, asOfDate, asOfDate);
        if (excluded != null && excluded > 0) {
            throw new BusinessException("COST_SUBJECT_NOT_IN_PROJECT_SCOPE", "成本科目已被当前项目排除");
        }
    }

    protected void requireProject(Long projectId) {
        Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM pm_project WHERE tenant_id=? AND id=? AND deleted_flag=0",
                Integer.class, tenantId(), projectId);
        if (count == null || count != 1) throw new BusinessException("PROJECT_NOT_FOUND", "项目不存在");
        projectAccessChecker.checkAccess(projectId, "访问成本科目项目数据");
    }

    protected void requireProjectOpenForNormalCostGovernance(Long projectId) {
        List<String> statuses = jdbc.query("""
                SELECT status FROM pm_project
                WHERE tenant_id=? AND id=? AND deleted_flag=0 FOR UPDATE
                """, (rs, rowNum) -> rs.getString(1), tenantId(), projectId);
        if (statuses.size() != 1) {
            throw new BusinessException("PROJECT_NOT_FOUND", "项目不存在");
        }
        projectAccessChecker.checkAccess(projectId, "访问成本科目项目数据");
        if ("CLOSED".equals(statuses.getFirst())) {
            throw new BusinessException("COST_GOVERNANCE_PROJECT_CLOSED",
                    "已关闭项目禁止普通成本配置、转入或分摊；请使用关闭后财务调整/冲销流程");
        }
    }

    protected void requireProjectsOpenForNormalCostGovernance(List<Long> projectIds) {
        if (projectIds == null) return;
        projectIds.stream().filter(Objects::nonNull).distinct().sorted()
                .forEach(this::requireProjectOpenForNormalCostGovernance);
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

    protected void requireCurrentUserCreated(Object createdBy, String businessName) {
        if (!Objects.equals(longValue(createdBy), userId())) {
            throw new BusinessException("COST_REQUEST_CREATOR_REQUIRED",
                    businessName + "只能由原申请人提交；审批人必须使用另一账号");
        }
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

    protected static LocalDate localDate(Object value) {
        if (value == null) return null;
        if (value instanceof LocalDate date) return date;
        if (value instanceof java.sql.Date date) return date.toLocalDate();
        return LocalDate.parse(String.valueOf(value));
    }
}
