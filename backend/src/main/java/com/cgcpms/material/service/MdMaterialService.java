package com.cgcpms.material.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.cgcpms.auth.context.UserContext;
import com.cgcpms.common.exception.BusinessException;
import com.cgcpms.common.result.PageResult;
import com.cgcpms.material.entity.MdMaterial;
import com.cgcpms.material.entity.MdMaterialCategory;
import com.cgcpms.material.mapper.MdMaterialMapper;
import com.cgcpms.material.mapper.MdMaterialCategoryMapper;
import com.cgcpms.material.vo.MdMaterialVO;
import com.cgcpms.material.vo.MdMaterialPurchasePriceRow;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import com.cgcpms.common.util.DateTimeUtils;

import java.math.BigDecimal;
import java.util.Set;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class MdMaterialService {

    private static final Set<String> STATUSES = Set.of("ENABLE", "DISABLE");
    private final MdMaterialMapper mdMaterialMapper;
    private final MdMaterialCategoryMapper categoryMapper;

    public PageResult<MdMaterialVO> getPage(long pageNo, long pageSize, String materialCode, String materialName, Long categoryId, String status) {
        LambdaQueryWrapper<MdMaterial> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(MdMaterial::getTenantId, UserContext.getCurrentTenantId());
        if (StringUtils.hasText(materialCode)) wrapper.like(MdMaterial::getMaterialCode, materialCode);
        if (StringUtils.hasText(materialName)) wrapper.like(MdMaterial::getMaterialName, materialName);
        if (categoryId != null) wrapper.eq(MdMaterial::getCategoryId, categoryId);
        if (StringUtils.hasText(status)) wrapper.eq(MdMaterial::getStatus, status);
        wrapper.orderByDesc(MdMaterial::getCreatedAt);

        Page<MdMaterial> page = mdMaterialMapper.selectPage(new Page<>(pageNo, pageSize), wrapper);
        List<MdMaterial> materials = List.copyOf(page.getRecords());
        IPage<MdMaterialVO> voPage = page.convert(this::toVO);
        attachPurchasePrices(voPage.getRecords(), materials);
        return PageResult.of(voPage);
    }

    public MdMaterialVO getById(Long id) {
        MdMaterial material = mdMaterialMapper.selectById(id);
        if (material == null) throw new BusinessException("MATERIAL_NOT_FOUND", "材料不存在");
        if (!material.getTenantId().equals(UserContext.getCurrentTenantId())) {
            throw new BusinessException("MATERIAL_NOT_FOUND", "材料不存在");
        }
        MdMaterialVO vo = toVO(material);
        attachPurchasePrices(List.of(vo), List.of(material));
        return vo;
    }

    @Transactional(rollbackFor = Exception.class)
    public Long create(MdMaterial material) {
        material.setTenantId(UserContext.getCurrentTenantId());
        normalizeAndValidate(material, true);
        material.setCategoryId(resolveCategory(material.getCategoryId(), material.getTenantId()));
        try {
            mdMaterialMapper.insert(material);
        } catch (DuplicateKeyException error) {
            throw new BusinessException("MATERIAL_CODE_EXISTS", "材料编码已存在");
        }
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
        if (StringUtils.hasText(material.getMaterialCode())
                && !existing.getMaterialCode().equals(material.getMaterialCode().trim())) {
            throw new BusinessException("MATERIAL_CODE_IMMUTABLE", "材料编码创建后不可修改");
        }
        material.setMaterialCode(existing.getMaterialCode());
        if (!StringUtils.hasText(material.getMaterialName())) material.setMaterialName(existing.getMaterialName());
        if (!StringUtils.hasText(material.getStatus())) material.setStatus(existing.getStatus());
        material.setTenantId(existing.getTenantId());
        normalizeAndValidate(material, false);
        material.setCategoryId(resolveCategory(
                material.getCategoryId() == null ? existing.getCategoryId() : material.getCategoryId(),
                existing.getTenantId()));
        try {
            mdMaterialMapper.updateById(material);
        } catch (DuplicateKeyException error) {
            throw new BusinessException("MATERIAL_CODE_EXISTS", "材料编码已存在");
        }
    }

    private Long resolveCategory(Long categoryId, Long tenantId) {
        if (categoryId == null) {
            MdMaterialCategory fallback = categoryMapper.selectOne(new LambdaQueryWrapper<MdMaterialCategory>()
                    .eq(MdMaterialCategory::getTenantId, tenantId)
                    .eq(MdMaterialCategory::getCategoryCode, "UNCATEGORIZED")
                    .eq(MdMaterialCategory::getStatus, "ENABLE"));
            if (fallback == null) {
                throw new BusinessException("MATERIAL_CATEGORY_DEFAULT_MISSING", "租户默认材料分类不存在");
            }
            return fallback.getId();
        }
        MdMaterialCategory category = categoryMapper.selectById(categoryId);
        if (category == null || !tenantId.equals(category.getTenantId()) || !"ENABLE".equals(category.getStatus())) {
            throw new BusinessException("MATERIAL_CATEGORY_INVALID", "材料分类不存在、已停用或不属于当前租户");
        }
        return categoryId;
    }

    @Transactional(rollbackFor = Exception.class)
    public void updateStatus(Long id, String status) {
        validateStatus(status);
        MdMaterial existing = mdMaterialMapper.selectById(id);
        if (existing == null)
            throw new BusinessException("MATERIAL_NOT_FOUND", "材料不存在");
        if (!existing.getTenantId().equals(UserContext.getCurrentTenantId())) {
            throw new BusinessException("MATERIAL_NOT_FOUND", "材料不存在");
        }
        int updated = mdMaterialMapper.update(null, new LambdaUpdateWrapper<MdMaterial>()
                .eq(MdMaterial::getId, id)
                .eq(MdMaterial::getTenantId, existing.getTenantId())
                .set(MdMaterial::getStatus, status));
        if (updated != 1) throw new BusinessException("MATERIAL_NOT_FOUND", "材料不存在");
    }

    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        Long tenantId = UserContext.getCurrentTenantId();
        MdMaterial existing = mdMaterialMapper.selectByIdForUpdate(id, tenantId);
        if (existing == null) {
            throw new BusinessException("MATERIAL_NOT_FOUND", "材料不存在");
        }
        if (mdMaterialMapper.hasActiveReferences(id, tenantId) != 0) {
            throw new BusinessException("MATERIAL_REFERENCED", "材料已被业务引用，无法删除，请改为停用");
        }
        if (mdMaterialMapper.deleteById(id) != 1) {
            throw new BusinessException("MATERIAL_NOT_FOUND", "材料不存在");
        }
    }

    private void normalizeAndValidate(MdMaterial material, boolean creating) {
        material.setMaterialCode(required(material.getMaterialCode(), 64,
                "MATERIAL_CODE_REQUIRED", "材料编码不能为空"));
        material.setMaterialName(required(material.getMaterialName(), 200,
                "MATERIAL_NAME_REQUIRED", "材料名称不能为空"));
        material.setSpecification(optional(material.getSpecification(), 200, "材料规格"));
        material.setUnit(optional(material.getUnit(), 20, "计量单位"));
        material.setBrand(optional(material.getBrand(), 100, "材料品牌"));
        material.setRemark(optional(material.getRemark(), 500, "材料备注"));
        if (creating && !StringUtils.hasText(material.getStatus())) material.setStatus("ENABLE");
        validateStatus(material.getStatus());
        BigDecimal rate = material.getDefaultTaxRate();
        if (rate != null && (rate.scale() > 2
                || rate.compareTo(BigDecimal.ZERO) < 0
                || rate.compareTo(new BigDecimal("100")) > 0)) {
            throw new BusinessException("MATERIAL_TAX_RATE_INVALID", "默认税率必须为0到100且最多2位小数");
        }
    }

    private void validateStatus(String status) {
        if (status == null || !STATUSES.contains(status)) {
            throw new BusinessException("MATERIAL_STATUS_INVALID", "材料状态只允许ENABLE或DISABLE");
        }
    }

    private String required(String value, int maxLength, String code, String message) {
        if (!StringUtils.hasText(value)) throw new BusinessException(code, message);
        String normalized = value.trim();
        if (normalized.length() > maxLength) {
            throw new BusinessException(code, message + "且长度不得超过" + maxLength);
        }
        return normalized;
    }

    private String optional(String value, int maxLength, String label) {
        if (!StringUtils.hasText(value)) return null;
        String normalized = value.trim();
        if (normalized.length() > maxLength) {
            throw new BusinessException("MATERIAL_FIELD_TOO_LONG", label + "长度不得超过" + maxLength);
        }
        return normalized;
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
        vo.setTaxInclusiveInfoPrice(m.getTaxInclusiveInfoPrice() != null
                ? m.getTaxInclusiveInfoPrice().toPlainString() : null);
        vo.setInfoPricePeriod(m.getInfoPricePeriod());
        vo.setInfoPriceSource(m.getInfoPriceSource());
        vo.setInfoPriceVerificationStatus(m.getInfoPriceVerificationStatus());
        vo.setInfoPriceExternalRowKey(m.getInfoPriceExternalRowKey());
        vo.setInfoPriceReviewRequired(m.getInfoPriceReviewRequired());
        vo.setStatus(m.getStatus());
        vo.setCreatedBy(m.getCreatedBy() != null ? m.getCreatedBy().toString() : null);
        vo.setCreatedAt(m.getCreatedAt() != null ? DateTimeUtils.DTF.format(m.getCreatedAt()) : null);
        vo.setUpdatedAt(m.getUpdatedAt() != null ? DateTimeUtils.DTF.format(m.getUpdatedAt()) : null);
        vo.setRemark(m.getRemark());
        return vo;
    }

    private void attachPurchasePrices(List<MdMaterialVO> values, List<MdMaterial> materials) {
        if (materials.isEmpty()) return;
        List<Long> ids = materials.stream().map(MdMaterial::getId).toList();
        Map<Long, MdMaterialPurchasePriceRow> prices = mdMaterialMapper
                .selectLatestApprovedPurchasePrices(UserContext.getCurrentTenantId(), ids).stream()
                .collect(Collectors.toMap(MdMaterialPurchasePriceRow::getMaterialId, Function.identity()));
        for (int index = 0; index < materials.size(); index++) {
            MdMaterialPurchasePriceRow price = prices.get(materials.get(index).getId());
            if (price == null) continue;
            MdMaterialVO vo = values.get(index);
            vo.setPurchasePrice(price.getPurchasePrice().toPlainString());
            vo.setPurchasePriceReceiptItemId(price.getReceiptItemId().toString());
            vo.setPurchasePriceDate(price.getReceiptDate() != null ? price.getReceiptDate().toString() : null);
        }
    }
}
