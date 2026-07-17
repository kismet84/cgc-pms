package com.cgcpms.material.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.cgcpms.auth.context.UserContext;
import com.cgcpms.common.exception.BusinessException;
import com.cgcpms.material.entity.MdMaterialCategory;
import com.cgcpms.material.mapper.MdMaterialCategoryMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class MdMaterialCategoryService {
    private final MdMaterialCategoryMapper mapper;

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

    private void validateParent(MdMaterialCategory category, Long tenantId) {
        if (category.getParentId() != null) parent(category.getParentId(), tenantId);
    }

    private MdMaterialCategory parent(Long id, Long tenantId) {
        MdMaterialCategory value = mapper.selectById(id);
        if (value == null || !tenantId.equals(value.getTenantId())) throw new BusinessException("MATERIAL_CATEGORY_NOT_FOUND", "材料分类不存在");
        return value;
    }
}
