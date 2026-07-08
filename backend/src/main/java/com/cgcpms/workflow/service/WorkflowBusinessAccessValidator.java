package com.cgcpms.workflow.service;

import com.cgcpms.common.exception.BusinessException;
import com.cgcpms.contract.entity.CtContract;
import com.cgcpms.contract.entity.CtContractChange;
import com.cgcpms.contract.mapper.CtContractChangeMapper;
import com.cgcpms.contract.mapper.CtContractMapper;
import com.cgcpms.cost.entity.CostTarget;
import com.cgcpms.cost.mapper.CostTargetMapper;
import com.cgcpms.payment.entity.PayApplication;
import com.cgcpms.payment.mapper.PayApplicationMapper;
import com.cgcpms.project.auth.ProjectAccessChecker;
import com.cgcpms.purchase.entity.MatPurchaseOrder;
import com.cgcpms.purchase.entity.MatPurchaseRequest;
import com.cgcpms.purchase.mapper.MatPurchaseOrderMapper;
import com.cgcpms.purchase.mapper.MatPurchaseRequestMapper;
import com.cgcpms.receipt.entity.MatReceipt;
import com.cgcpms.receipt.mapper.MatReceiptMapper;
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
import org.springframework.stereotype.Component;

import java.util.Objects;

@Component
@RequiredArgsConstructor
public class WorkflowBusinessAccessValidator {

    private final ProjectAccessChecker projectAccessChecker;
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
    private final MatRequisitionMapper requisitionMapper;

    public ValidationResult validateSubmit(String businessType, Long businessId, Long tenantId,
                                           Long requestProjectId, Long requestContractId) {
        if (businessType == null || businessId == null) {
            throw new BusinessException("WORKFLOW_BUSINESS_INVALID", "审批业务对象不能为空");
        }
        switch (businessType) {
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
            case WorkflowBusinessTypes.MATERIAL_REQUISITION -> {
                MatRequisition entity = requisitionMapper.selectById(businessId);
                return validate(entity != null, tenantId, entity == null ? null : entity.getTenantId(),
                        entity == null ? null : entity.getProjectId(), requestProjectId,
                        entity == null ? null : entity.getContractId(), requestContractId,
                        entity == null ? null : entity.getApprovalStatus(), "REQUISITION_NOT_FOUND");
            }
            default -> throw new BusinessException("UNSUPPORTED_BUSINESS_TYPE", "不支持的业务类型: " + businessType);
        }
    }

    private ValidationResult validate(boolean exists, Long tenantId, Long realTenantId, Long realProjectId,
                                      Long requestProjectId, Long realContractId, Long requestContractId,
                                      String approvalStatus, String notFoundCode) {
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
        if (!"DRAFT".equals(approvalStatus) && !"APPROVING".equals(approvalStatus)) {
            throw new BusinessException("WORKFLOW_STATUS_NOT_SUBMITTABLE", "业务状态不允许提交审批");
        }
        return new ValidationResult(realProjectId, realContractId, approvalStatus);
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
