package com.cgcpms.settlement.service;

import com.cgcpms.settlement.entity.StlSettlement;
import com.cgcpms.settlement.entity.StlSettlementItem;
import com.cgcpms.settlement.vo.StlSettlementItemVO;
import com.cgcpms.settlement.vo.StlSettlementVO;
import com.cgcpms.common.util.DateTimeUtils;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

/**
 * 结算 VO 组装器 — 纯数据转换，无数据库访问。
 */
@Component
public class StlSettlementAssembler {

    // ---- BigDecimal helpers ----

    static BigDecimal nullToZero(BigDecimal value) {
        return value != null ? value : BigDecimal.ZERO;
    }

    // ---- VO mapping ----

    StlSettlementVO toVO(StlSettlement m, NameMaps maps) {
        StlSettlementVO vo = new StlSettlementVO();
        vo.setId(m.getId() != null ? m.getId().toString() : null);
        vo.setTenantId(m.getTenantId() != null ? m.getTenantId().toString() : null);
        vo.setProjectId(m.getProjectId() != null ? m.getProjectId().toString() : null);
        vo.setContractId(m.getContractId() != null ? m.getContractId().toString() : null);
        vo.setPartnerId(m.getPartnerId() != null ? m.getPartnerId().toString() : null);
        vo.setSettlementCode(m.getSettlementCode());
        vo.setSettlementType(m.getSettlementType());
        vo.setContractAmount(m.getContractAmount() != null ? m.getContractAmount().toPlainString() : null);
        vo.setChangeAmount(m.getChangeAmount() != null ? m.getChangeAmount().toPlainString() : null);
        vo.setMeasuredAmount(m.getMeasuredAmount() != null ? m.getMeasuredAmount().toPlainString() : null);
        vo.setDeductionAmount(m.getDeductionAmount() != null ? m.getDeductionAmount().toPlainString() : null);
        vo.setPaidAmount(m.getPaidAmount() != null ? m.getPaidAmount().toPlainString() : null);
        vo.setFinalAmount(m.getFinalAmount() != null ? m.getFinalAmount().toPlainString() : null);
        vo.setAmountFormulaVersion(m.getAmountFormulaVersion());
        vo.setApprovalStatus(m.getApprovalStatus());
        // Compatibility response field: approval_status is the sole workflow state authority.
        vo.setStatus(m.getApprovalStatus());
        vo.setUnpaidAmount(m.getUnpaidAmount() != null ? m.getUnpaidAmount().toPlainString() : null);
        vo.setWarrantyAmount(m.getWarrantyAmount() != null ? m.getWarrantyAmount().toPlainString() : null);
        vo.setSettlementStatus(m.getSettlementStatus());
        vo.setFinalizedAt(m.getFinalizedAt() != null ? m.getFinalizedAt().format(DateTimeUtils.DTF) : null);
        vo.setProjectName(maps.projectNames().get(m.getProjectId()));
        vo.setContractName(maps.contractNames().get(m.getContractId()));
        vo.setPartnerName(maps.partnerNames().get(m.getPartnerId()));
        vo.setCreatedBy(m.getCreatedBy() != null ? m.getCreatedBy().toString() : null);
        vo.setCreatedAt(m.getCreatedAt() != null ? m.getCreatedAt().format(DateTimeUtils.DTF) : null);
        vo.setUpdatedAt(m.getUpdatedAt() != null ? m.getUpdatedAt().format(DateTimeUtils.DTF) : null);
        vo.setRemark(m.getRemark());
        return vo;
    }

    StlSettlementItemVO toItemVO(StlSettlementItem item) {
        StlSettlementItemVO vo = new StlSettlementItemVO();
        vo.setId(item.getId() != null ? item.getId().toString() : null);
        vo.setTenantId(item.getTenantId() != null ? item.getTenantId().toString() : null);
        vo.setSettlementId(item.getSettlementId() != null ? item.getSettlementId().toString() : null);
        vo.setItemName(item.getItemName());
        vo.setUnit(item.getUnit());
        vo.setQuantity(item.getQuantity() != null ? item.getQuantity().toPlainString() : null);
        vo.setUnitPrice(item.getUnitPrice() != null ? item.getUnitPrice().toPlainString() : null);
        vo.setAmount(item.getAmount() != null ? item.getAmount().toPlainString() : null);
        vo.setCostSubjectId(item.getCostSubjectId() != null ? item.getCostSubjectId().toString() : null);
        vo.setSourceType(item.getSourceType());
        vo.setSourceId(item.getSourceId() != null ? item.getSourceId().toString() : null);
        vo.setCreatedBy(item.getCreatedBy() != null ? item.getCreatedBy().toString() : null);
        vo.setCreatedAt(item.getCreatedAt() != null ? item.getCreatedAt().format(DateTimeUtils.DTF) : null);
        vo.setUpdatedAt(item.getUpdatedAt() != null ? item.getUpdatedAt().format(DateTimeUtils.DTF) : null);
        vo.setRemark(item.getRemark());
        return vo;
    }

    // ---- Name holder ----

    record NameMaps(java.util.Map<Long, String> projectNames,
                    java.util.Map<Long, String> contractNames,
                    java.util.Map<Long, String> partnerNames) {
    }
}
