package com.cgcpms.contract.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.cgcpms.common.exception.BusinessException;
import com.cgcpms.contract.entity.CtContract;
import com.cgcpms.contract.mapper.CtContractMapper;
import com.cgcpms.contract.vo.CtContractVO;
import com.cgcpms.partner.entity.MdPartner;
import com.cgcpms.partner.mapper.MdPartnerMapper;
import com.cgcpms.project.entity.PmProject;
import com.cgcpms.project.mapper.PmProjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.format.DateTimeFormatter;

@Service
@RequiredArgsConstructor
public class CtContractService {

    private static final DateTimeFormatter DTF = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    private final CtContractMapper ctContractMapper;
    private final PmProjectMapper pmProjectMapper;
    private final MdPartnerMapper mdPartnerMapper;

    public IPage<CtContractVO> getPage(long pageNo, long pageSize, String contractCode, String contractName,
                                       String contractType, String contractStatus, String approvalStatus,
                                       Long projectId, Long partnerId) {
        LambdaQueryWrapper<CtContract> wrapper = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(contractCode)) wrapper.like(CtContract::getContractCode, contractCode);
        if (StringUtils.hasText(contractName)) wrapper.like(CtContract::getContractName, contractName);
        if (StringUtils.hasText(contractType)) wrapper.eq(CtContract::getContractType, contractType);
        if (StringUtils.hasText(contractStatus)) wrapper.eq(CtContract::getContractStatus, contractStatus);
        if (StringUtils.hasText(approvalStatus)) wrapper.eq(CtContract::getApprovalStatus, approvalStatus);
        if (projectId != null) wrapper.eq(CtContract::getProjectId, projectId);
        if (partnerId != null) wrapper.eq(CtContract::getPartnerId, partnerId);
        wrapper.orderByDesc(CtContract::getCreatedAt);

        Page<CtContract> page = ctContractMapper.selectPage(new Page<>(pageNo, pageSize), wrapper);
        return page.convert(this::toVO);
    }

    public CtContractVO getById(Long id) {
        CtContract c = ctContractMapper.selectById(id);
        if (c == null) throw new BusinessException("CONTRACT_NOT_FOUND", "合同不存在");
        return toVO(c);
    }

    private CtContractVO toVO(CtContract c) {
        CtContractVO vo = new CtContractVO();
        vo.setId(c.getId() != null ? c.getId().toString() : null);
        vo.setTenantId(c.getTenantId() != null ? c.getTenantId().toString() : null);
        vo.setOrgId(c.getOrgId() != null ? c.getOrgId().toString() : null);
        vo.setProjectId(c.getProjectId() != null ? c.getProjectId().toString() : null);
        vo.setPartnerId(c.getPartnerId() != null ? c.getPartnerId().toString() : null);
        vo.setContractCode(c.getContractCode());
        vo.setContractName(c.getContractName());
        vo.setContractType(c.getContractType());
        vo.setPartyA(c.getPartyA());
        vo.setPartyB(c.getPartyB());
        vo.setContractAmount(c.getContractAmount() != null ? c.getContractAmount().toPlainString() : null);
        vo.setCurrentAmount(c.getCurrentAmount() != null ? c.getCurrentAmount().toPlainString() : null);
        vo.setTaxRate(c.getTaxRate() != null ? c.getTaxRate().toPlainString() : null);
        vo.setTaxAmount(c.getTaxAmount() != null ? c.getTaxAmount().toPlainString() : null);
        vo.setAmountWithoutTax(c.getAmountWithoutTax() != null ? c.getAmountWithoutTax().toPlainString() : null);
        vo.setSignedDate(c.getSignedDate() != null ? DATE_FMT.format(c.getSignedDate()) : null);
        vo.setStartDate(c.getStartDate() != null ? DATE_FMT.format(c.getStartDate()) : null);
        vo.setEndDate(c.getEndDate() != null ? DATE_FMT.format(c.getEndDate()) : null);
        vo.setPaymentMethod(c.getPaymentMethod());
        vo.setSettlementMethod(c.getSettlementMethod());
        vo.setWarrantyRate(c.getWarrantyRate() != null ? c.getWarrantyRate().toPlainString() : null);
        vo.setWarrantyAmount(c.getWarrantyAmount() != null ? c.getWarrantyAmount().toPlainString() : null);
        vo.setContractStatus(c.getContractStatus());
        vo.setApprovalStatus(c.getApprovalStatus());
        vo.setCreatedBy(c.getCreatedBy() != null ? c.getCreatedBy().toString() : null);
        vo.setCreatedAt(c.getCreatedAt() != null ? DTF.format(c.getCreatedAt()) : null);
        vo.setUpdatedAt(c.getUpdatedAt() != null ? DTF.format(c.getUpdatedAt()) : null);
        vo.setRemark(c.getRemark());

        if (c.getProjectId() != null) {
            PmProject project = pmProjectMapper.selectById(c.getProjectId());
            if (project != null) vo.setProjectName(project.getProjectName());
        }
        if (c.getPartnerId() != null) {
            MdPartner partner = mdPartnerMapper.selectById(c.getPartnerId());
            if (partner != null) vo.setPartnerName(partner.getPartnerName());
        }
        return vo;
    }
}
