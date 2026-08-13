package com.cgcpms.contract.service;

import static com.cgcpms.common.util.BigDecimalUtils.nvl;

import com.cgcpms.common.util.DateTimeUtils;
import com.cgcpms.contract.entity.CtContract;
import com.cgcpms.contract.vo.ContractPerformanceReportVO;
import com.cgcpms.contract.vo.CtContractVO;
import com.cgcpms.partner.entity.MdPartner;
import com.cgcpms.partner.mapper.MdPartnerMapper;
import com.cgcpms.project.entity.PmProject;
import com.cgcpms.project.mapper.PmProjectMapper;

import java.math.BigDecimal;
import java.util.Map;

final class CtContractViewAssembler {

    private final PmProjectMapper pmProjectMapper;
    private final MdPartnerMapper mdPartnerMapper;

    CtContractViewAssembler(PmProjectMapper pmProjectMapper, MdPartnerMapper mdPartnerMapper) {
        this.pmProjectMapper = pmProjectMapper;
        this.mdPartnerMapper = mdPartnerMapper;
    }

    CtContractVO toVO(CtContract c) {
        // Single-record variant: fetch project/partner individually (for getById).
        CtContractVO vo = buildBaseVO(c);
        if (c.getProjectId() != null) {
            PmProject project = pmProjectMapper.selectById(c.getProjectId());
            if (project != null) vo.setProjectName(project.getProjectName());
        }
                if (c.getPartyAId() != null) {
            MdPartner partyA = mdPartnerMapper.selectById(c.getPartyAId());
            if (partyA != null) vo.setPartyAName(partyA.getPartnerName());
        }
        if (c.getPartyBId() != null) {
            MdPartner partyB = mdPartnerMapper.selectById(c.getPartyBId());
            if (partyB != null) vo.setPartyBName(partyB.getPartnerName());
        }
        return vo;
    }

    CtContractVO toVO(CtContract c, Map<Long, String> projectNames, Map<Long, String> partyNames) {
        // Batch-friendly variant: use pre-fetched maps to avoid N+1.
        CtContractVO vo = buildBaseVO(c);
        if (c.getProjectId() != null) {
            vo.setProjectName(projectNames.get(c.getProjectId()));
        }
                if (c.getPartyAId() != null) {
            vo.setPartyAName(partyNames.get(c.getPartyAId()));
        }
        if (c.getPartyBId() != null) {
            vo.setPartyBName(partyNames.get(c.getPartyBId()));
        }
        return vo;
    }

    private CtContractVO buildBaseVO(CtContract c) {
        CtContractVO vo = new CtContractVO();
        vo.setId(c.getId() != null ? c.getId().toString() : null);
        vo.setTenantId(c.getTenantId() != null ? c.getTenantId().toString() : null);
        vo.setOrgId(c.getOrgId() != null ? c.getOrgId().toString() : null);
        vo.setProjectId(c.getProjectId() != null ? c.getProjectId().toString() : null);
        vo.setVersion(c.getVersion());

        vo.setContractCode(c.getContractCode());
        vo.setContractName(c.getContractName());
        vo.setContractType(c.getContractType());
        vo.setPartyAId(c.getPartyAId() != null ? c.getPartyAId().toString() : null);
        vo.setPartyBId(c.getPartyBId() != null ? c.getPartyBId().toString() : null);
        vo.setContractAmount(c.getContractAmount() != null ? c.getContractAmount().toPlainString() : null);
        vo.setCurrentAmount(c.getCurrentAmount() != null ? c.getCurrentAmount().toPlainString() : null);
        vo.setTaxRate(c.getTaxRate() != null ? c.getTaxRate().toPlainString() : null);
        vo.setTaxAmount(c.getTaxAmount() != null ? c.getTaxAmount().toPlainString() : null);
        vo.setAmountWithoutTax(c.getAmountWithoutTax() != null ? c.getAmountWithoutTax().toPlainString() : null);
        vo.setSignedDate(c.getSignedDate() != null ? DateTimeUtils.DATE_FMT.format(c.getSignedDate()) : null);
        vo.setStartDate(c.getStartDate() != null ? DateTimeUtils.DATE_FMT.format(c.getStartDate()) : null);
        vo.setEndDate(c.getEndDate() != null ? DateTimeUtils.DATE_FMT.format(c.getEndDate()) : null);
        vo.setPaymentMethod(c.getPaymentMethod());
        vo.setSettlementMethod(c.getSettlementMethod());
        vo.setPaidAmount(c.getPaidAmount() != null ? c.getPaidAmount().toPlainString() : null);
        vo.setPayableAmount(c.getPayableAmount() != null ? c.getPayableAmount().toPlainString() : null);
        vo.setSettlementAmount(c.getSettlementAmount() != null ? c.getSettlementAmount().toPlainString() : null);
        vo.setContractStatus(c.getContractStatus());
        vo.setApprovalStatus(c.getApprovalStatus());
        vo.setCreatedBy(c.getCreatedBy() != null ? c.getCreatedBy().toString() : null);
        vo.setCreatedAt(c.getCreatedAt() != null ? DateTimeUtils.DTF.format(c.getCreatedAt()) : null);
        vo.setUpdatedAt(c.getUpdatedAt() != null ? DateTimeUtils.DTF.format(c.getUpdatedAt()) : null);
        vo.setRemark(c.getRemark());
        return vo;
    }

    ContractPerformanceReportVO.Row toPerformanceRow(CtContract contract,
                                                      Map<Long, BigDecimal> changeByContract,
                                                      Map<Long, BigDecimal> paidByContract) {
        ContractPerformanceReportVO.Row row = new ContractPerformanceReportVO.Row();
        BigDecimal contractAmount = nvl(contract.getContractAmount());
        BigDecimal changeAmount = changeByContract.getOrDefault(contract.getId(), BigDecimal.ZERO);
        BigDecimal paidAmount = paidByContract.getOrDefault(contract.getId(), BigDecimal.ZERO);
        BigDecimal currentAmount = contract.getCurrentAmount() != null && contract.getCurrentAmount().compareTo(BigDecimal.ZERO) != 0
                ? contract.getCurrentAmount()
                : contractAmount.add(changeAmount);

        row.setContractId(String.valueOf(contract.getId()));
        row.setContractCode(contract.getContractCode());
        row.setContractName(contract.getContractName());
        row.setContractStatus(contract.getContractStatus());
        row.setContractAmount(contractAmount.toPlainString());
        row.setChangeAmount(changeAmount.toPlainString());
        row.setPaidAmount(paidAmount.toPlainString());
        row.setPaymentProgress(formatRatio(paidAmount, currentAmount));
        return row;
    }

    static String formatRatio(BigDecimal numerator, BigDecimal denominator) {
        if (denominator == null || denominator.compareTo(BigDecimal.ZERO) <= 0) {
            return "0.0000";
        }
        return numerator.divide(denominator, 4, java.math.RoundingMode.HALF_UP).toPlainString();
    }
}
