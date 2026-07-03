package com.cgcpms.material.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.cgcpms.auth.context.UserContext;
import com.cgcpms.common.exception.BusinessException;
import com.cgcpms.common.result.PageResult;
import com.cgcpms.material.entity.MdMaterial;
import com.cgcpms.material.mapper.MdMaterialMapper;
import com.cgcpms.material.vo.MdMaterialVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import com.cgcpms.common.util.DateTimeUtils;

@Service
@RequiredArgsConstructor
public class MdMaterialService {

    private final MdMaterialMapper mdMaterialMapper;

    public PageResult<MdMaterialVO> getPage(long pageNo, long pageSize, String materialCode, String materialName, Long categoryId, String status) {
        LambdaQueryWrapper<MdMaterial> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(MdMaterial::getTenantId, UserContext.getCurrentTenantId());
        if (StringUtils.hasText(materialCode)) wrapper.like(MdMaterial::getMaterialCode, materialCode);
        if (StringUtils.hasText(materialName)) wrapper.like(MdMaterial::getMaterialName, materialName);
        if (categoryId != null) wrapper.eq(MdMaterial::getCategoryId, categoryId);
        if (StringUtils.hasText(status)) wrapper.eq(MdMaterial::getStatus, status);
        wrapper.orderByDesc(MdMaterial::getCreatedAt);

        Page<MdMaterial> page = mdMaterialMapper.selectPage(new Page<>(pageNo, pageSize), wrapper);
        IPage<MdMaterialVO> voPage = page.convert(this::toVO);
        return PageResult.of(voPage);
    }

    public MdMaterialVO getById(Long id) {
        MdMaterial material = mdMaterialMapper.selectById(id);
        if (material == null) throw new BusinessException("MATERIAL_NOT_FOUND", "材料不存在");
        if (!material.getTenantId().equals(UserContext.getCurrentTenantId())) {
            throw new BusinessException("MATERIAL_NOT_FOUND", "材料不存在");
        }
        return toVO(material);
    }

    @Transactional(rollbackFor = Exception.class)
    public Long create(MdMaterial material) {
        material.setTenantId(UserContext.getCurrentTenantId());
        mdMaterialMapper.insert(material);
        return material.getId();
    }

    @Transactional(rollbackFor = Exception.class)
    public void update(MdMaterial material) {
        MdMaterial existing = mdMaterialMapper.selectById(material.getId());
        if (existing == null)
            throw new BusinessException("MATERIAL_NOT_FOUND", "材料不存在");
        if (!existing.getTenantId().equals(UserContext.getCurrentTenantId())) {
            throw new BusinessException("MATERIAL_NOT_FOUND", "材料不存在");
        }
        mdMaterialMapper.updateById(material);
    }

    @Transactional(rollbackFor = Exception.class)
    public void updateStatus(Long id, String status) {
        MdMaterial existing = mdMaterialMapper.selectById(id);
        if (existing == null)
            throw new BusinessException("MATERIAL_NOT_FOUND", "材料不存在");
        if (!existing.getTenantId().equals(UserContext.getCurrentTenantId())) {
            throw new BusinessException("MATERIAL_NOT_FOUND", "材料不存在");
        }
        existing.setStatus(status);
        mdMaterialMapper.updateById(existing);
    }

    private MdMaterialVO toVO(MdMaterial m) {
        MdMaterialVO vo = new MdMaterialVO();
        vo.setId(m.getId() != null ? m.getId().toString() : null);
        vo.setTenantId(m.getTenantId() != null ? m.getTenantId().toString() : null);
        vo.setMaterialCode(m.getMaterialCode());
        vo.setMaterialName(m.getMaterialName());
        vo.setCategoryId(m.getCategoryId() != null ? m.getCategoryId().toString() : null);
        vo.setSpecification(m.getSpecification());
        vo.setUnit(m.getUnit());
        vo.setBrand(m.getBrand());
        vo.setDefaultTaxRate(m.getDefaultTaxRate() != null ? m.getDefaultTaxRate().toPlainString() : null);
        vo.setStatus(m.getStatus());
        vo.setCreatedBy(m.getCreatedBy() != null ? m.getCreatedBy().toString() : null);
        vo.setCreatedAt(m.getCreatedAt() != null ? DateTimeUtils.DTF.format(m.getCreatedAt()) : null);
        vo.setUpdatedAt(m.getUpdatedAt() != null ? DateTimeUtils.DTF.format(m.getUpdatedAt()) : null);
        vo.setRemark(m.getRemark());
        return vo;
    }
}
