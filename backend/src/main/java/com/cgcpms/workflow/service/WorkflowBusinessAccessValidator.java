package com.cgcpms.workflow.service;

import com.cgcpms.common.exception.BusinessException;
import com.cgcpms.contract.entity.CtContract;
import com.cgcpms.contract.entity.CtContractChange;
import com.cgcpms.contract.mapper.CtContractChangeMapper;
import com.cgcpms.contract.mapper.CtContractMapper;
import com.cgcpms.cost.entity.CostTarget;
import com.cgcpms.cost.mapper.CostTargetMapper;
import com.cgcpms.measurement.entity.ProductionMeasurement;
import com.cgcpms.measurement.mapper.ProductionMeasurementMapper;
import com.cgcpms.payment.entity.PayApplication;
import com.cgcpms.payment.mapper.PayApplicationMapper;
import com.cgcpms.project.auth.ProjectAccessChecker;
import com.cgcpms.project.entity.PmProject;
import com.cgcpms.project.mapper.PmProjectMapper;
import com.cgcpms.purchase.entity.MatPurchaseOrder;
import com.cgcpms.purchase.entity.MatPurchaseRequest;
import com.cgcpms.purchase.mapper.MatPurchaseOrderMapper;
import com.cgcpms.purchase.mapper.MatPurchaseRequestMapper;
import com.cgcpms.receipt.entity.MatReceipt;
import com.cgcpms.receipt.mapper.MatReceiptMapper;
import com.cgcpms.revenue.entity.ContractRevenue;
import com.cgcpms.revenue.mapper.ContractRevenueMapper;
import com.cgcpms.requisition.entity.MatRequisition;
import com.cgcpms.requisition.mapper.MatRequisitionMapper;
import com.cgcpms.settlement.entity.StlSettlement;
import com.cgcpms.settlement.mapper.StlSettlementMapper;
import com.cgcpms.subcontract.entity.SubMeasure;
import com.cgcpms.subcontract.mapper.SubMeasureMapper;
import com.cgcpms.variation.entity.VarOrder;
import com.cgcpms.variation.mapper.VarOrderMapper;
import com.cgcpms.workflow.WorkflowBusinessTypes;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

@Component
@RequiredArgsConstructor
public class WorkflowBusinessAccessValidator {

    private final ProjectAccessChecker projectAccessChecker;
    private final PmProjectMapper projectMapper;
    private final CtContractMapper contractMapper;
    private final MatPurchaseOrderMapper purchaseOrderMapper;
    private final MatPurchaseRequestMapper purchaseRequestMapper;
    private final MatReceiptMapper receiptMapper;
    private final SubMeasureMapper subMeasureMapper;
    private final PayApplicationMapper payApplicationMapper;
    private final VarOrderMapper varOrderMapper;
    private final CtContractChangeMapper contractChangeMapper;
    private final StlSettlementMapper settlementMapper;
    private final CostTargetMapper costTargetMapper;
    private final ContractRevenueMapper contractRevenueMapper;
    private final MatRequisitionMapper requisitionMapper;
    private final ProductionMeasurementMapper productionMeasurementMapper;
    private final JdbcTemplate jdbcTemplate;

    public ValidationResult validateSubmit(String businessType, Long businessId, Long tenantId,
                                           Long requestProjectId, Long requestContractId) {
        if (businessType == null || businessId == null) {
            throw new BusinessException("WORKFLOW_BUSINESS_INVALID", "审批业务对象不能为空");
        }
        switch (businessType) {
            case WorkflowBusinessTypes.PROJECT_APPROVAL -> {
                PmProject entity = projectMapper.selectById(businessId);
                return validate(entity != null, tenantId, entity == null ? null : entity.getTenantId(),
                        entity == null ? null : entity.getId(), requestProjectId,
                        null, requestContractId,
                        entity == null ? null : entity.getApprovalStatus(), "PROJECT_NOT_FOUND", "DRAFT", "REJECTED");
            }
            case WorkflowBusinessTypes.CONTRACT_APPROVAL -> {
                CtContract entity = contractMapper.selectById(businessId);
                return validate(entity != null, tenantId, entity == null ? null : entity.getTenantId(),
                        entity == null ? null : entity.getProjectId(), requestProjectId,
                        entity == null ? null : entity.getId(), requestContractId,
                        entity == null ? null : entity.getApprovalStatus(), "CONTRACT_NOT_FOUND");
            }
            case WorkflowBusinessTypes.PURCHASE_ORDER -> {
                MatPurchaseOrder entity = purchaseOrderMapper.selectById(businessId);
                return validate(entity != null, tenantId, entity == null ? null : entity.getTenantId(),
                        entity == null ? null : entity.getProjectId(), requestProjectId,
                        entity == null ? null : entity.getContractId(), requestContractId,
                        entity == null ? null : entity.getApprovalStatus(), "PURCHASE_ORDER_NOT_FOUND");
            }
            case WorkflowBusinessTypes.PURCHASE_REQUEST -> {
                MatPurchaseRequest entity = purchaseRequestMapper.selectById(businessId);
                return validate(entity != null, tenantId, entity == null ? null : entity.getTenantId(),
                        entity == null ? null : entity.getProjectId(), requestProjectId,
                        entity == null ? null : entity.getContractId(), requestContractId,
                        entity == null ? null : entity.getApprovalStatus(), "PURCHASE_REQUEST_NOT_FOUND");
            }
            case WorkflowBusinessTypes.MATERIAL_RECEIPT -> {
                MatReceipt entity = receiptMapper.selectById(businessId);
                return validate(entity != null, tenantId, entity == null ? null : entity.getTenantId(),
                        entity == null ? null : entity.getProjectId(), requestProjectId,
                        entity == null ? null : entity.getContractId(), requestContractId,
                        entity == null ? null : entity.getApprovalStatus(), "RECEIPT_NOT_FOUND");
            }
            case WorkflowBusinessTypes.SUB_MEASURE -> {
                SubMeasure entity = subMeasureMapper.selectById(businessId);
                return validate(entity != null, tenantId, entity == null ? null : entity.getTenantId(),
                        entity == null ? null : entity.getProjectId(), requestProjectId,
                        entity == null ? null : entity.getContractId(), requestContractId,
                        entity == null ? null : entity.getApprovalStatus(), "SUB_MEASURE_NOT_FOUND");
            }
            case WorkflowBusinessTypes.PAY_REQUEST -> {
                PayApplication entity = payApplicationMapper.selectById(businessId);
                return validate(entity != null, tenantId, entity == null ? null : entity.getTenantId(),
                        entity == null ? null : entity.getProjectId(), requestProjectId,
                        entity == null ? null : entity.getContractId(), requestContractId,
                        entity == null ? null : entity.getApprovalStatus(), "PAY_APP_NOT_FOUND");
            }
            case WorkflowBusinessTypes.VAR_ORDER -> {
                VarOrder entity = varOrderMapper.selectById(businessId);
                return validate(entity != null, tenantId, entity == null ? null : entity.getTenantId(),
                        entity == null ? null : entity.getProjectId(), requestProjectId,
                        entity == null ? null : entity.getContractId(), requestContractId,
                        entity == null ? null : entity.getApprovalStatus(), "VAR_ORDER_NOT_FOUND");
            }
            case WorkflowBusinessTypes.CT_CHANGE -> {
                CtContractChange entity = contractChangeMapper.selectById(businessId);
                return validate(entity != null, tenantId, entity == null ? null : entity.getTenantId(),
                        entity == null ? null : entity.getProjectId(), requestProjectId,
                        entity == null ? null : entity.getContractId(), requestContractId,
                        entity == null ? null : entity.getApprovalStatus(), "CT_CHANGE_NOT_FOUND");
            }
            case WorkflowBusinessTypes.SETTLEMENT -> {
                StlSettlement entity = settlementMapper.selectById(businessId);
                return validate(entity != null, tenantId, entity == null ? null : entity.getTenantId(),
                        entity == null ? null : entity.getProjectId(), requestProjectId,
                        entity == null ? null : entity.getContractId(), requestContractId,
                        entity == null ? null : entity.getApprovalStatus(), "SETTLEMENT_NOT_FOUND");
            }
            case WorkflowBusinessTypes.COST_TARGET -> {
                CostTarget entity = costTargetMapper.selectById(businessId);
                return validate(entity != null, tenantId, entity == null ? null : entity.getTenantId(),
                        entity == null ? null : entity.getProjectId(), requestProjectId,
                        null, requestContractId,
                        entity == null ? null : entity.getApprovalStatus(), "COST_TARGET_NOT_FOUND");
            }
            case WorkflowBusinessTypes.CONTRACT_REVENUE -> {
                ContractRevenue entity = contractRevenueMapper.selectById(businessId);
                return validate(entity != null, tenantId, entity == null ? null : entity.getTenantId(),
                        entity == null ? null : entity.getProjectId(), requestProjectId,
                        entity == null ? null : entity.getContractId(), requestContractId,
                        entity == null ? null : entity.getApprovalStatus(), "REVENUE_NOT_FOUND", "PENDING");
            }
            case WorkflowBusinessTypes.PRODUCTION_MEASUREMENT -> {
                ProductionMeasurement entity = productionMeasurementMapper.selectById(businessId);
                return validate(entity != null, tenantId, entity == null ? null : entity.getTenantId(),
                        entity == null ? null : entity.getProjectId(), requestProjectId,
                        entity == null ? null : entity.getContractId(), requestContractId,
                        entity == null ? null : entity.getApprovalStatus(), "PRODUCTION_MEASUREMENT_NOT_FOUND", "DRAFT", "REJECTED");
            }
            case WorkflowBusinessTypes.PROJECT_BUDGET -> {
                return validateJdbc("project_budget", "approval_status", businessId, tenantId, requestProjectId, requestContractId, "PROJECT_BUDGET_NOT_FOUND");
            }
            case WorkflowBusinessTypes.EXPENSE -> {
                return validateJdbc("expense_application", "approval_status", businessId, tenantId, requestProjectId, requestContractId, "EXPENSE_NOT_FOUND");
            }
            case WorkflowBusinessTypes.OWNER_SETTLEMENT -> {
                return validateJdbc("owner_settlement", "status", businessId, tenantId, requestProjectId, requestContractId, "OWNER_SETTLEMENT_NOT_FOUND");
            }
            case WorkflowBusinessTypes.MATERIAL_REQUISITION -> {
                MatRequisition entity = requisitionMapper.selectById(businessId);
                return validate(entity != null, tenantId, entity == null ? null : entity.getTenantId(),
                        entity == null ? null : entity.getProjectId(), requestProjectId,
                        entity == null ? null : entity.getContractId(), requestContractId,
                        entity == null ? null : entity.getApprovalStatus(), "REQUISITION_NOT_FOUND");
            }
            case WorkflowBusinessTypes.PROJECT_SCHEDULE -> {
                return validateJdbc("project_schedule_plan", "status", businessId, tenantId, requestProjectId, requestContractId, "PROJECT_SCHEDULE_NOT_FOUND");
            }
            case WorkflowBusinessTypes.PROJECT_PERIOD_PLAN -> {
                return validateJdbc("project_period_plan", "status", businessId, tenantId, requestProjectId, requestContractId, "PROJECT_PERIOD_NOT_FOUND");
            }
            case WorkflowBusinessTypes.PROJECT_CORRECTIVE_ACTION -> {
                return validateJdbc("project_corrective_action", "status", businessId, tenantId, requestProjectId, requestContractId, "PROJECT_CORRECTIVE_NOT_FOUND");
            }
            case WorkflowBusinessTypes.COST_CORRECTIVE_ACTION -> {
                return validateJdbc("cost_corrective_action", "status", businessId, tenantId, requestProjectId, requestContractId, "COST_CORRECTIVE_NOT_FOUND");
            }
            default -> throw new BusinessException("UNSUPPORTED_BUSINESS_TYPE", "不支持的业务类型: " + businessType);
        }
    }

    private ValidationResult validateJdbc(String table, String statusColumn, Long businessId, Long tenantId,
                                            Long requestProjectId, Long requestContractId, String notFoundCode) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT tenant_id,project_id," + (hasNoContractColumn(table) ? "NULL" : "contract_id")
                        + " contract_id," + statusColumn + " approval_status FROM " + table + " WHERE id=? AND deleted_flag=0",
                businessId);
        Map<String, Object> row = rows.isEmpty() ? null : rows.get(0);
        return validate(row != null, tenantId, row == null ? null : ((Number) row.get("tenant_id")).longValue(),
                row == null ? null : ((Number) row.get("project_id")).longValue(), requestProjectId,
                row == null || row.get("contract_id") == null ? null : ((Number) row.get("contract_id")).longValue(), requestContractId,
                row == null ? null : Objects.toString(row.get("approval_status"), null), notFoundCode, "DRAFT", "REJECTED");
    }

    private boolean hasNoContractColumn(String table) {
        return Set.of("project_budget", "project_schedule_plan", "project_period_plan", "project_corrective_action", "cost_corrective_action").contains(table);
    }

    private ValidationResult validate(boolean exists, Long tenantId, Long realTenantId, Long realProjectId,
                                      Long requestProjectId, Long realContractId, Long requestContractId,
                                      String approvalStatus, String notFoundCode) {
        return validate(exists, tenantId, realTenantId, realProjectId, requestProjectId,
                realContractId, requestContractId, approvalStatus, notFoundCode, "DRAFT", "APPROVING");
    }

    private ValidationResult validate(boolean exists, Long tenantId, Long realTenantId, Long realProjectId,
                                      Long requestProjectId, Long realContractId, Long requestContractId,
                                      String approvalStatus, String notFoundCode, String... allowedStatuses) {
        if (!exists || !Objects.equals(realTenantId, tenantId)) {
            throw new BusinessException(notFoundCode, "审批业务对象不存在");
        }
        if (realProjectId == null) {
            throw new BusinessException("WORKFLOW_PROJECT_MISSING", "审批业务对象缺少项目关系");
        }
        if (requestProjectId != null && !Objects.equals(realProjectId, requestProjectId)) {
            throw new BusinessException("WORKFLOW_PROJECT_MISMATCH", "审批请求项目与业务对象不一致");
        }
        if (requestContractId != null && realContractId != null && !Objects.equals(realContractId, requestContractId)) {
            throw new BusinessException("WORKFLOW_CONTRACT_MISMATCH", "审批请求合同与业务对象不一致");
        }
        projectAccessChecker.checkAccess(realProjectId, "提交审批");
        if (!isAllowedStatus(approvalStatus, allowedStatuses)) {
            throw new BusinessException("WORKFLOW_STATUS_NOT_SUBMITTABLE", "业务状态不允许提交审批");
        }
        return new ValidationResult(realProjectId, realContractId, approvalStatus);
    }

    private boolean isAllowedStatus(String approvalStatus, String... allowedStatuses) {
        for (String allowedStatus : allowedStatuses) {
            if (Objects.equals(allowedStatus, approvalStatus)) {
                return true;
            }
        }
        return false;
    }

    public static final class ValidationResult {
        private final Long projectId;
        private final Long contractId;
        private final String approvalStatus;

        private ValidationResult(Long projectId, Long contractId, String approvalStatus) {
            this.projectId = projectId;
            this.contractId = contractId;
            this.approvalStatus = approvalStatus;
        }

        public Long getProjectId() {
            return projectId;
        }

        public Long getContractId() {
            return contractId;
        }

        public String getApprovalStatus() {
            return approvalStatus;
        }
    }
}
