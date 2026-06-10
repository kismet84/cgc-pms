package com.cgcpms.partner.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.cgcpms.common.exception.BusinessException;
import com.cgcpms.partner.entity.MdPartner;
import com.cgcpms.partner.mapper.MdPartnerMapper;
import com.cgcpms.partner.vo.MdPartnerVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.format.DateTimeFormatter;

@Service
@RequiredArgsConstructor
public class MdPartnerService {

    private static final DateTimeFormatter DTF = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final MdPartnerMapper mdPartnerMapper;

    public IPage<MdPartnerVO> getPage(long pageNo, long pageSize, String partnerCode, String partnerName, String partnerType, String status) {
        LambdaQueryWrapper<MdPartner> wrapper = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(partnerCode)) wrapper.like(MdPartner::getPartnerCode, partnerCode);
        if (StringUtils.hasText(partnerName)) wrapper.like(MdPartner::getPartnerName, partnerName);
        if (StringUtils.hasText(partnerType)) wrapper.eq(MdPartner::getPartnerType, partnerType);
        if (StringUtils.hasText(status)) wrapper.eq(MdPartner::getStatus, status);
        wrapper.orderByDesc(MdPartner::getCreatedAt);

        Page<MdPartner> page = mdPartnerMapper.selectPage(new Page<>(pageNo, pageSize), wrapper);
        return page.convert(this::toVO);
    }

    public MdPartnerVO getById(Long id) {
        MdPartner partner = mdPartnerMapper.selectById(id);
        if (partner == null) throw new BusinessException("PARTNER_NOT_FOUND", "合作伙伴不存在");
        return toVO(partner);
    }

    @Transactional
    public Long create(MdPartner partner) {
        if (StringUtils.hasText(partner.getPartnerCode()) &&
                mdPartnerMapper.selectCount(new LambdaQueryWrapper<MdPartner>()
                        .eq(MdPartner::getPartnerCode, partner.getPartnerCode())) > 0) {
            throw new BusinessException("PARTNER_CODE_EXISTS", "合作伙伴编码已存在");
        }
        if (partner.getStatus() == null) partner.setStatus("ENABLE");
        mdPartnerMapper.insert(partner);
        return partner.getId();
    }

    @Transactional
    public void update(MdPartner partner) {
        if (mdPartnerMapper.selectById(partner.getId()) == null)
            throw new BusinessException("PARTNER_NOT_FOUND", "合作伙伴不存在");
        mdPartnerMapper.updateById(partner);
    }

    @Transactional
    public void delete(Long id) {
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
        if (p.getCreatedAt() != null) vo.setCreatedAt(DTF.format(p.getCreatedAt()));
        if (p.getUpdatedAt() != null) vo.setUpdatedAt(DTF.format(p.getUpdatedAt()));
        return vo;
    }
}
