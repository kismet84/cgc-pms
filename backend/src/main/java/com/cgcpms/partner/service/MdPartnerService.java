package com.cgcpms.partner.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.cgcpms.auth.context.UserContext;
import com.cgcpms.common.exception.BusinessException;
import com.cgcpms.contract.entity.CtContract;
import com.cgcpms.contract.mapper.CtContractMapper;
import com.cgcpms.system.dict.service.SysDictDataService;
import com.cgcpms.partner.entity.MdPartner;
import com.cgcpms.partner.mapper.MdPartnerMapper;
import com.cgcpms.partner.vo.MdPartnerVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import com.cgcpms.common.util.DateTimeUtils;

@Slf4j
@Service
@RequiredArgsConstructor
public class MdPartnerService {

    private static final int CODE_GENERATION_MAX_RETRIES = 3;

    private final MdPartnerMapper mdPartnerMapper;
    private final CtContractMapper ctContractMapper;
    private final SysDictDataService sysDictDataService;

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

    /**
     * 创建合作伙伴。
     *
     * <p>编号策略：当 {@code partnerCode} 为 {@code null} 或空时自动生成 "PTN-yyyyMMdd-NNN" 前缀编号；
     * 当手动指定 {@code partnerCode} 时不自动添加前缀，调用方负责保证编码格式和唯一性。
     * 此设计允许外部系统导入时保留原始编码。
     */
    @Transactional(rollbackFor = Exception.class)
    public Long create(MdPartner partner) {
        partner.setPartnerType(sysDictDataService.requireEnabledValue(
                "partner_type", partner.getPartnerType(),
                "PARTNER_TYPE_INVALID", "合作方类型不合法"));
        normalizeDefaultLeadDays(partner, partner.getPartnerType());
        // Auto-generate partner code: PTN-yyyyMMdd-NNN
        boolean autoGenerateCode = !StringUtils.hasText(partner.getPartnerCode());
        String prefix = null;
        Long tenantId = UserContext.getCurrentTenantId();
        if (autoGenerateCode) {
            String today = LocalDate.now().format(DateTimeUtils.DATE_COMPACT);
            prefix = "PTN-" + today + "-";
        }

        if (StringUtils.hasText(partner.getPartnerCode()) &&
                mdPartnerMapper.selectCount(new LambdaQueryWrapper<MdPartner>()
                        .eq(MdPartner::getPartnerCode, partner.getPartnerCode())
                        .eq(MdPartner::getTenantId, UserContext.getCurrentTenantId())) > 0) {
            throw new BusinessException("PARTNER_CODE_EXISTS", "合作伙伴编码已存在");
        }
        if (partner.getStatus() == null) partner.setStatus("ENABLE");
        if (!autoGenerateCode) {
            mdPartnerMapper.insert(partner);
            log.info("Creating partner: {}", partner.getPartnerName());
            return partner.getId();
        }

        for (int attempt = 0; attempt < CODE_GENERATION_MAX_RETRIES; attempt++) {
            partner.setPartnerCode(nextPartnerCode(tenantId, prefix, attempt));
            try {
                mdPartnerMapper.insert(partner);
                log.info("Creating partner: {}", partner.getPartnerName());
                return partner.getId();
            } catch (DuplicateKeyException e) {
                log.warn("合作方编号冲突，重试生成 partnerCode={}", partner.getPartnerCode());
            }
        }
        throw new BusinessException("PARTNER_CODE_CONFLICT", "合作方编号生成冲突，请重试");
    }

    private String nextPartnerCode(Long tenantId, String prefix, int offset) {
        LambdaQueryWrapper<MdPartner> codeWrapper = new LambdaQueryWrapper<>();
        codeWrapper.eq(MdPartner::getTenantId, tenantId)
                .likeRight(MdPartner::getPartnerCode, prefix)
                .orderByDesc(MdPartner::getPartnerCode);
        Page<MdPartner> page = new Page<>(0, 1);
        Page<MdPartner> result = mdPartnerMapper.selectPage(page, codeWrapper);
        List<MdPartner> list = result.getRecords();
        int seq = 1 + offset;
        if (!list.isEmpty()) {
            MdPartner last = list.get(0);
            if (last.getPartnerCode() != null
                    && last.getPartnerCode().length() == prefix.length() + 3) {
                try {
                    seq = Integer.parseInt(last.getPartnerCode().substring(prefix.length())) + 1 + offset;
                } catch (NumberFormatException ex) {
                    log.warn("Failed to parse partner code sequence: {}", last.getPartnerCode(), ex);
                }
            }
        }
        return prefix + String.format("%03d", seq);
    }

    @Transactional(rollbackFor = Exception.class)
    public void update(MdPartner partner) {
        MdPartner existing = mdPartnerMapper.selectById(partner.getId());
        if (existing == null)
            throw new BusinessException("PARTNER_NOT_FOUND", "合作伙伴不存在");
        if (!existing.getTenantId().equals(UserContext.getCurrentTenantId())) {
            throw new BusinessException("PARTNER_NOT_FOUND", "合作方不存在");
        }
        String effectivePartnerType = StringUtils.hasText(partner.getPartnerType())
                ? partner.getPartnerType() : existing.getPartnerType();
        effectivePartnerType = sysDictDataService.requireEnabledValue(
                "partner_type", effectivePartnerType,
                "PARTNER_TYPE_INVALID", "合作方类型不合法");
        if (StringUtils.hasText(partner.getPartnerType())) {
            partner.setPartnerType(effectivePartnerType);
        }
        if (!partner.isDefaultLeadDaysSpecified()) {
            partner.preserveDefaultLeadDays(existing.getDefaultLeadDays());
        }
        normalizeDefaultLeadDays(partner, effectivePartnerType);
        mdPartnerMapper.updateById(partner);
    }

    private void normalizeDefaultLeadDays(MdPartner partner, String effectivePartnerType) {
        if (!"SUPPLIER".equals(effectivePartnerType)) {
            partner.preserveDefaultLeadDays(null);
            return;
        }
        if (partner.getDefaultLeadDays() == null) return;
        try {
            int days = partner.getDefaultLeadDays().intValueExact();
            if (days < 0 || days > 3650) throw new ArithmeticException("out of range");
            partner.preserveDefaultLeadDays(BigDecimal.valueOf(days));
        } catch (ArithmeticException error) {
            throw new BusinessException("INVALID_PARTNER_DEFAULT_LEAD_DAYS", "供应商默认提前期必须为0到3650之间的整数");
        }
    }

    @Transactional(rollbackFor = Exception.class)
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
        vo.setDefaultLeadDays(p.getDefaultLeadDays() == null ? null : p.getDefaultLeadDays().intValueExact());
        if (p.getCreatedBy() != null) vo.setCreatedBy(String.valueOf(p.getCreatedBy()));
        if (p.getCreatedAt() != null) vo.setCreatedAt(DateTimeUtils.DTF.format(p.getCreatedAt()));
        if (p.getUpdatedAt() != null) vo.setUpdatedAt(DateTimeUtils.DTF.format(p.getUpdatedAt()));
        return vo;
    }
}
