package com.cgcpms.partner.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.cgcpms.auth.context.UserContext;
import com.cgcpms.common.exception.BusinessException;
import com.cgcpms.contract.entity.CtContract;
import com.cgcpms.contract.mapper.CtContractMapper;
import com.cgcpms.partner.entity.MdPartner;
import com.cgcpms.partner.mapper.MdPartnerMapper;
import com.cgcpms.partner.vo.MdPartnerVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.util.List;

import com.cgcpms.common.util.DateTimeUtils;

@Slf4j
@Service
@RequiredArgsConstructor
public class MdPartnerService {

    private final MdPartnerMapper mdPartnerMapper;
    private final CtContractMapper ctContractMapper;

    public IPage<MdPartnerVO> getPage(long pageNo, long pageSize, String partnerCode, String partnerName, String partnerType, String status) {
        LambdaQueryWrapper<MdPartner> wrapper = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(partnerCode)) wrapper.like(MdPartner::getPartnerCode, partnerCode);
        if (StringUtils.hasText(partnerName)) wrapper.like(MdPartner::getPartnerName, partnerName);
        if (StringUtils.hasText(partnerType)) wrapper.eq(MdPartner::getPartnerType, partnerType);
        if (StringUtils.hasText(status)) wrapper.eq(MdPartner::getStatus, status);
        wrapper.eq(MdPartner::getTenantId, UserContext.getCurrentTenantId());
        wrapper.orderByDesc(MdPartner::getCreatedAt);

        Page<MdPartner> page = mdPartnerMapper.selectPage(new Page<>(pageNo, pageSize), wrapper);
        return page.convert(this::toVO);
    }

    public MdPartnerVO getById(Long id) {
        MdPartner partner = mdPartnerMapper.selectById(id);
        if (partner == null) throw new BusinessException("PARTNER_NOT_FOUND", "合作伙伴不存在");
        if (!partner.getTenantId().equals(UserContext.getCurrentTenantId())) {
            throw new BusinessException("PARTNER_NOT_FOUND", "合作方不存在");
        }
        return toVO(partner);
    }

    @Transactional
    public Long create(MdPartner partner) {
        // Auto-generate partner code: PTN-yyyyMMdd-NNN
        if (!StringUtils.hasText(partner.getPartnerCode())) {
            String today = LocalDate.now().format(DateTimeUtils.DATE_COMPACT);
            String prefix = "PTN-" + today + "-";
            Long tenantId = UserContext.getCurrentTenantId();
            LambdaQueryWrapper<MdPartner> codeWrapper = new LambdaQueryWrapper<>();
            codeWrapper.eq(MdPartner::getTenantId, tenantId)
                    .likeRight(MdPartner::getPartnerCode, prefix)
                    .orderByDesc(MdPartner::getPartnerCode)
                    .last("LIMIT 1");
            List<MdPartner> list = mdPartnerMapper.selectList(codeWrapper);
            int seq = 1;
            if (!list.isEmpty()) {
                MdPartner last = list.get(0);
                if (last.getPartnerCode() != null
                        && last.getPartnerCode().length() == prefix.length() + 3) {
                    try {
                        seq = Integer.parseInt(last.getPartnerCode().substring(prefix.length())) + 1;
                    } catch (NumberFormatException ex) {
                        log.warn("Failed to parse partner code sequence: {}", last.getPartnerCode(), ex);
                    }
                }
            }
            partner.setPartnerCode(prefix + String.format("%03d", seq));
        }

        if (StringUtils.hasText(partner.getPartnerCode()) &&
                mdPartnerMapper.selectCount(new LambdaQueryWrapper<MdPartner>()
                        .eq(MdPartner::getPartnerCode, partner.getPartnerCode())
                        .eq(MdPartner::getTenantId, UserContext.getCurrentTenantId())) > 0) {
            throw new BusinessException("PARTNER_CODE_EXISTS", "合作伙伴编码已存在");
        }
        if (partner.getStatus() == null) partner.setStatus("ENABLE");
        mdPartnerMapper.insert(partner);
        log.info("Creating partner: {}", partner.getPartnerName());
        return partner.getId();
    }

    @Transactional
    public void update(MdPartner partner) {
        MdPartner existing = mdPartnerMapper.selectById(partner.getId());
        if (existing == null)
            throw new BusinessException("PARTNER_NOT_FOUND", "合作伙伴不存在");
        if (!existing.getTenantId().equals(UserContext.getCurrentTenantId())) {
            throw new BusinessException("PARTNER_NOT_FOUND", "合作方不存在");
        }
        mdPartnerMapper.updateById(partner);
    }

    @Transactional
    public void delete(Long id) {
        MdPartner existing = mdPartnerMapper.selectById(id);
        if (existing == null)
            throw new BusinessException("PARTNER_NOT_FOUND", "合作伙伴不存在");
        if (!existing.getTenantId().equals(UserContext.getCurrentTenantId())) {
            throw new BusinessException("PARTNER_NOT_FOUND", "合作方不存在");
        }
        long contractCount = ctContractMapper.selectCount(
                new LambdaQueryWrapper<CtContract>().and(w -> w.eq(CtContract::getPartyAId, id).or().eq(CtContract::getPartyBId, id)));
        if (contractCount > 0) {
            throw new BusinessException("PARTNER_HAS_CONTRACTS", "该合作方存在关联合同，无法删除");
        }
        mdPartnerMapper.deleteById(id);
    }

    private MdPartnerVO toVO(MdPartner p) {
        MdPartnerVO vo = new MdPartnerVO();
        vo.setId(p.getId() == null ? null : String.valueOf(p.getId()));
        vo.setPartnerCode(p.getPartnerCode());
        vo.setPartnerName(p.getPartnerName());
        vo.setPartnerType(p.getPartnerType());
        vo.setCreditCode(p.getCreditCode());
        vo.setLegalPerson(p.getLegalPerson());
        vo.setContactName(p.getContactName());
        vo.setContactPhone(p.getContactPhone());
        vo.setBankName(p.getBankName());
        vo.setBankAccount(p.getBankAccount());
        vo.setQualificationLevel(p.getQualificationLevel());
        vo.setBlacklistFlag(p.getBlacklistFlag());
        vo.setRiskLevel(p.getRiskLevel());
        vo.setStatus(p.getStatus());
        if (p.getCreatedAt() != null) vo.setCreatedAt(DateTimeUtils.DTF.format(p.getCreatedAt()));
        if (p.getUpdatedAt() != null) vo.setUpdatedAt(DateTimeUtils.DTF.format(p.getUpdatedAt()));
        return vo;
    }
}
