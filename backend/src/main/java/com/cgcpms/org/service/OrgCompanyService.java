package com.cgcpms.org.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.cgcpms.auth.context.UserContext;
import com.cgcpms.common.exception.BusinessException;
import com.cgcpms.org.entity.OrgCompany;
import com.cgcpms.org.mapper.OrgCompanyMapper;
import com.cgcpms.org.vo.OrgCompanyVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import com.cgcpms.common.util.DateTimeUtils;

@Service
@RequiredArgsConstructor
public class OrgCompanyService {

    private final OrgCompanyMapper orgCompanyMapper;

    public IPage<OrgCompanyVO> getPage(long pageNo, long pageSize, String companyCode, String companyName, String status) {
        LambdaQueryWrapper<OrgCompany> wrapper = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(companyCode)) wrapper.like(OrgCompany::getCompanyCode, companyCode);
        if (StringUtils.hasText(companyName)) wrapper.like(OrgCompany::getCompanyName, companyName);
        if (StringUtils.hasText(status)) wrapper.eq(OrgCompany::getStatus, status);
        wrapper.eq(OrgCompany::getTenantId, UserContext.getCurrentTenantId());
        wrapper.orderByDesc(OrgCompany::getCreatedTime);

        Page<OrgCompany> page = orgCompanyMapper.selectPage(new Page<>(pageNo, pageSize), wrapper);
        return page.convert(this::toVO);
    }

    public OrgCompanyVO getById(Long id) {
        OrgCompany company = orgCompanyMapper.selectById(id);
        if (company == null) throw new BusinessException("ORG_COMPANY_NOT_FOUND", "公司不存在");
        if (!company.getTenantId().equals(UserContext.getCurrentTenantId())) {
            throw new BusinessException("ORG_COMPANY_NOT_FOUND", "公司不存在");
        }
        return toVO(company);
    }

    @Transactional
    public Long create(OrgCompany company) {
        if (StringUtils.hasText(company.getCompanyCode())) {
            Long count = orgCompanyMapper.selectCount(new LambdaQueryWrapper<OrgCompany>()
                    .eq(OrgCompany::getCompanyCode, company.getCompanyCode())
                    .eq(OrgCompany::getTenantId, UserContext.getCurrentTenantId()));
            if (count > 0) {
                throw new BusinessException("ORG_COMPANY_CODE_EXISTS", "公司编码已存在");
            }
        }
        if (company.getStatus() == null) company.setStatus("ENABLE");
        orgCompanyMapper.insert(company);
        return company.getId();
    }

    @Transactional
    public void update(OrgCompany company) {
        OrgCompany existing = orgCompanyMapper.selectById(company.getId());
        if (existing == null)
            throw new BusinessException("ORG_COMPANY_NOT_FOUND", "公司不存在");
        if (!existing.getTenantId().equals(UserContext.getCurrentTenantId())) {
            throw new BusinessException("ORG_COMPANY_NOT_FOUND", "公司不存在");
        }
        // 编码唯一性校验：更新后的 companyCode 不得与同租户下其他记录重复
        if (StringUtils.hasText(company.getCompanyCode())
                && !company.getCompanyCode().equals(existing.getCompanyCode())) {
            Long count = orgCompanyMapper.selectCount(new LambdaQueryWrapper<OrgCompany>()
                    .eq(OrgCompany::getCompanyCode, company.getCompanyCode())
                    .eq(OrgCompany::getTenantId, UserContext.getCurrentTenantId()));
            if (count > 0) {
                throw new BusinessException("ORG_COMPANY_CODE_EXISTS", "公司编码已存在");
            }
        }
        orgCompanyMapper.updateById(company);
    }

    @Transactional
    public void delete(Long id) {
        OrgCompany existing = orgCompanyMapper.selectById(id);
        if (existing == null)
            throw new BusinessException("ORG_COMPANY_NOT_FOUND", "公司不存在");
        if (!existing.getTenantId().equals(UserContext.getCurrentTenantId())) {
            throw new BusinessException("ORG_COMPANY_NOT_FOUND", "公司不存在");
        }
        orgCompanyMapper.deleteById(id);
    }

    private OrgCompanyVO toVO(OrgCompany c) {
        OrgCompanyVO vo = new OrgCompanyVO();
        vo.setId(c.getId() == null ? null : String.valueOf(c.getId()));
        vo.setCompanyCode(c.getCompanyCode());
        vo.setCompanyName(c.getCompanyName());
        vo.setStatus(c.getStatus());
        vo.setCreatedBy(c.getCreatedBy() != null ? String.valueOf(c.getCreatedBy()) : null);
        if (c.getCreatedTime() != null) vo.setCreatedAt(DateTimeUtils.DTF.format(c.getCreatedTime()));
        if (c.getUpdatedTime() != null) vo.setUpdatedAt(DateTimeUtils.DTF.format(c.getUpdatedTime()));
        vo.setRemark(c.getRemark());
        return vo;
    }
}
