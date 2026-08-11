package com.cgcpms.material.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.cgcpms.auth.context.UserContext;
import com.cgcpms.common.exception.BusinessException;
import com.cgcpms.material.entity.MdMaterialCategory;
import com.cgcpms.material.entity.MdMaterial;
import com.cgcpms.material.mapper.MdMaterialCategoryMapper;
import com.cgcpms.material.mapper.MdMaterialMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class MdMaterialCategoryService {
    private final MdMaterialCategoryMapper mapper;
    private final MdMaterialMapper materialMapper;

    public List<MdMaterialCategory> list() {
        return mapper.selectList(new LambdaQueryWrapper<MdMaterialCategory>()
                .eq(MdMaterialCategory::getTenantId, UserContext.getCurrentTenantId())
                .orderByAsc(MdMaterialCategory::getLevelNo, MdMaterialCategory::getOrderNum, MdMaterialCategory::getId));
    }

    @Transactional(rollbackFor = Exception.class)
    public Long create(MdMaterialCategory category) {
        Long tenantId = UserContext.getCurrentTenantId();
        category.setTenantId(tenantId);
        validateParent(category, tenantId);
        if (category.getLevelNo() == null) category.setLevelNo(category.getParentId() == null ? 1 : parent(category.getParentId(), tenantId).getLevelNo() + 1);
        if (category.getOrderNum() == null) category.setOrderNum(0);
        if (category.getStatus() == null || category.getStatus().isBlank()) category.setStatus("ENABLE");
        try { mapper.insert(category); }
        catch (DuplicateKeyException e) { throw new BusinessException("MATERIAL_CATEGORY_CODE_EXISTS", "材料分类编码已存在"); }
        return category.getId();
    }

    @Transactional(rollbackFor = Exception.class)
    public void update(Long id, MdMaterialCategory input) {
        Long tenantId = UserContext.getCurrentTenantId();
        MdMaterialCategory existing = parent(id, tenantId);
        if (id.equals(input.getParentId())) throw new BusinessException("MATERIAL_CATEGORY_PARENT_INVALID", "材料分类不能以自身作为上级");
        input.setId(id); input.setTenantId(tenantId); input.setCategoryCode(existing.getCategoryCode());
        validateParent(input, tenantId);
        try { mapper.updateById(input); }
        catch (DuplicateKeyException e) { throw new BusinessException("MATERIAL_CATEGORY_CODE_EXISTS", "材料分类编码已存在"); }
    }

    @Transactional(rollbackFor = Exception.class)
    public void updateStatus(Long id, String status) {
        Long tenantId = UserContext.getCurrentTenantId();
        MdMaterialCategory category = parent(id, tenantId);
        String normalized = status == null ? "" : status.trim().toUpperCase();
        if (!List.of("ENABLE", "DISABLE").contains(normalized)) {
            throw new BusinessException("MATERIAL_CATEGORY_STATUS_INVALID", "材料分类状态仅支持 ENABLE 或 DISABLE");
        }
        category.setStatus(normalized);
        mapper.updateById(category);
    }

    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        Long tenantId = UserContext.getCurrentTenantId();
        parent(id, tenantId);
        if (mapper.selectCount(new LambdaQueryWrapper<MdMaterialCategory>()
                .eq(MdMaterialCategory::getTenantId, tenantId)
                .eq(MdMaterialCategory::getParentId, id)) > 0) {
            throw new BusinessException("MATERIAL_CATEGORY_HAS_CHILDREN", "材料分类存在子分类，不能删除");
        }
        if (materialMapper.selectCount(new LambdaQueryWrapper<MdMaterial>()
                .eq(MdMaterial::getTenantId, tenantId)
                .eq(MdMaterial::getCategoryId, id)) > 0) {
            throw new BusinessException("MATERIAL_CATEGORY_IN_USE", "材料分类已被材料引用，不能删除");
        }
        mapper.deleteById(id);
    }

    private void validateParent(MdMaterialCategory category, Long tenantId) {
        if (category.getParentId() != null) parent(category.getParentId(), tenantId);
    }

    private MdMaterialCategory parent(Long id, Long tenantId) {
        MdMaterialCategory value = mapper.selectById(id);
        if (value == null || !tenantId.equals(value.getTenantId())) throw new BusinessException("MATERIAL_CATEGORY_NOT_FOUND", "材料分类不存在");
        return value;
    }
}
