package com.cgcpms.accounting.service;

import com.cgcpms.accounting.entity.AccountingEntry;
import com.cgcpms.accounting.entity.AccountingEntryLine;
import com.cgcpms.auth.context.UserContext;
import com.cgcpms.common.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class AccountingDimensionValidator {

    private final JdbcTemplate jdbcTemplate;

    public void validate(AccountingEntry entry) {
        Long tenantId = UserContext.getCurrentTenantId();
        for (AccountingEntryLine line : entry.getLines()) {
            if (line.getAccountingSubjectId() == null) {
                throw new BusinessException("ACCOUNTING_SUBJECT_REQUIRED", "会计分录必须绑定正式总账科目");
            }
            Map<String, Object> rule = jdbcTemplate.query("""
                    SELECT subject.subject_code,rule_row.project_requirement,rule_row.contract_requirement,
                           rule_row.partner_requirement,rule_row.department_requirement,rule_row.employee_requirement,
                           rule_row.allowed_contract_types,rule_row.allowed_partner_types
                    FROM cost_subject subject
                    JOIN accounting_subject_dimension_rule rule_row
                      ON rule_row.tenant_id=subject.tenant_id AND rule_row.accounting_subject_id=subject.id
                    WHERE subject.tenant_id=? AND subject.id=? AND subject.ledger_flag=1
                      AND subject.status='ENABLE' AND subject.deleted_flag=0
                      AND NOT EXISTS (SELECT 1 FROM cost_subject child
                                      WHERE child.tenant_id=subject.tenant_id AND child.parent_id=subject.id
                                        AND child.deleted_flag=0)
                    """, rs -> rs.next() ? Map.of(
                            "code", rs.getString("subject_code"),
                            "project", rs.getString("project_requirement"),
                            "contract", rs.getString("contract_requirement"),
                            "partner", rs.getString("partner_requirement"),
                            "department", rs.getString("department_requirement"),
                            "employee", rs.getString("employee_requirement"),
                            "contractTypes", nullable(rs.getString("allowed_contract_types")),
                            "partnerTypes", nullable(rs.getString("allowed_partner_types"))) : null,
                    tenantId, line.getAccountingSubjectId());
            if (rule == null) {
                throw new BusinessException("ACCOUNTING_SUBJECT_UNAVAILABLE", "会计分录科目未启用、非末级或缺少辅助核算政策");
            }
            String code = rule.get("code").toString();
            requireDimension(code, "项目", rule.get("project"), entry.getProjectId());
            requireDimension(code, "合同", rule.get("contract"), entry.getContractId());
            requireDimension(code, "往来单位", rule.get("partner"), entry.getPartnerId());
            requireDimension(code, "部门", rule.get("department"), entry.getDepartmentId());
            requireDimension(code, "员工", rule.get("employee"), entry.getEmployeeId());
            validateContract(entry, string(rule.get("contractTypes")));
            validatePartner(entry, string(rule.get("partnerTypes")));
        }
    }

    private void validateContract(AccountingEntry entry, String allowedTypes) {
        if (entry.getContractId() == null) return;
        String contractType = jdbcTemplate.query("""
                SELECT contract_type FROM ct_contract
                WHERE tenant_id=? AND id=? AND deleted_flag=0
                  AND (? IS NULL OR project_id=?)
                """, rs -> rs.next() ? rs.getString(1) : null,
                UserContext.getCurrentTenantId(), entry.getContractId(), entry.getProjectId(), entry.getProjectId());
        if (contractType == null) {
            throw new BusinessException("ACCOUNTING_CONTRACT_INVALID", "辅助核算合同不存在、跨租户或不属于当前项目");
        }
        if (allowedTypes != null && !values(allowedTypes).contains(normalizeContractType(contractType))) {
            throw new BusinessException("ACCOUNTING_CONTRACT_TYPE_INVALID", "合同类型不适用于当前会计科目");
        }
    }

    private void validatePartner(AccountingEntry entry, String allowedTypes) {
        if (entry.getPartnerId() == null) return;
        String partnerType = jdbcTemplate.query("""
                SELECT partner_type FROM md_partner
                WHERE tenant_id=? AND id=? AND status='ENABLE' AND deleted_flag=0
                """, rs -> rs.next() ? rs.getString(1) : null,
                UserContext.getCurrentTenantId(), entry.getPartnerId());
        if (partnerType == null) {
            throw new BusinessException("ACCOUNTING_PARTNER_INVALID", "辅助核算往来单位不存在、跨租户或已停用");
        }
        if (allowedTypes != null && !isAllowedPartnerType(values(allowedTypes), partnerType)) {
            throw new BusinessException("ACCOUNTING_PARTNER_TYPE_INVALID", "往来单位类型不适用于当前会计科目");
        }
    }

    private static boolean isAllowedPartnerType(Set<String> allowedTypes, String partnerType) {
        if (allowedTypes.contains(partnerType)) return true;
        if ("PARTY_A".equals(partnerType)) {
            return allowedTypes.contains("CUSTOMER") || allowedTypes.contains("OWNER");
        }
        if ("PARTY_B".equals(partnerType)) {
            return allowedTypes.contains("SUPPLIER")
                    || allowedTypes.contains("SUBCONTRACTOR")
                    || allowedTypes.contains("LESSOR")
                    || allowedTypes.contains("CONTRACTOR");
        }
        return false;
    }

    private static void requireDimension(String code, String label, Object requirement, Long value) {
        if ("REQUIRED".equals(requirement) && value == null) {
            throw new BusinessException("ACCOUNTING_DIMENSION_REQUIRED", "会计科目" + code + "必须核算" + label);
        }
    }

    private static Set<String> values(String value) {
        return Arrays.stream(value.split(",")).map(String::trim).filter(item -> !item.isEmpty()).collect(Collectors.toSet());
    }

    private static String normalizeContractType(String value) {
        return "SUBCONTRACT".equals(value) ? "SUB" : value;
    }

    private static Object nullable(String value) {
        return value == null ? "" : value;
    }

    private static String string(Object value) {
        return value == null || value.toString().isBlank() ? null : value.toString();
    }
}
