package com.cgcpms.contract.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.cgcpms.auth.context.UserContext;
import com.cgcpms.common.exception.BusinessException;
import com.cgcpms.contract.constant.ContractStatusConstants;
import com.cgcpms.contract.entity.CtContract;
import com.cgcpms.contract.entity.CtContractItem;
import com.cgcpms.contract.mapper.CtContractItemMapper;
import com.cgcpms.contract.mapper.CtContractMapper;
import com.cgcpms.material.entity.MdMaterial;
import com.cgcpms.material.mapper.MdMaterialMapper;
import com.cgcpms.project.auth.ProjectAccessChecker;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

@Service
@RequiredArgsConstructor
public class CtContractItemService extends ServiceImpl<CtContractItemMapper, CtContractItem> {

    private static final BigDecimal HUNDRED = new BigDecimal("100");

    private final CtContractItemMapper mapper;
    private final CtContractMapper ctContractMapper;
    private final MdMaterialMapper mdMaterialMapper;
    private final ProjectAccessChecker projectAccessChecker;

    /**
     * Verify parent contract belongs to current tenant and is in DRAFT status (editable).
     */
    private CtContract requireDraftParentContract(Long contractId, String action) {
        CtContract contract = requireParentContract(contractId, action);
        if (!java.util.List.of(ContractStatusConstants.APPROVAL_DRAFT, ContractStatusConstants.APPROVAL_REJECTED)
                .contains(contract.getApprovalStatus()))
            throw new BusinessException("CONTRACT_NOT_EDITABLE", "只有草稿或驳回合同可以编辑");
        return contract;
    }

    /**
     * Verify parent contract belongs to current tenant.
     */
    private CtContract requireParentContract(Long contractId, String action) {
        CtContract contract = ctContractMapper.selectById(contractId);
        if (contract == null || !contract.getTenantId().equals(UserContext.getCurrentTenantId()))
            throw new BusinessException("CONTRACT_NOT_FOUND", "合同不存在");
        if (contract.getProjectId() != null) {
            projectAccessChecker.checkAccess(contract.getProjectId(), action);
        }
        return contract;
    }

    public List<CtContractItem> getByContractId(Long contractId) {
        requireParentContract(contractId, "查看合同清单");
        LambdaQueryWrapper<CtContractItem> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(CtContractItem::getContractId, contractId)
               .orderByAsc(CtContractItem::getSortOrder);
        return mapper.selectList(wrapper);
    }

    @Transactional(rollbackFor = Exception.class)
    public Long create(CtContractItem item) {
        CtContract contract = requireDraftParentContract(item.getContractId(), "编辑合同清单");
        requireEnabledMaterials(List.of(item));
        deriveFinancials(contract, List.of(item));
        item.setTenantId(UserContext.getCurrentTenantId());
        mapper.insert(item);
        return item.getId();
    }

    @Transactional(rollbackFor = Exception.class)
    public void batchSave(Long contractId, List<CtContractItem> items) {
        CtContract contract = requireDraftParentContract(contractId, "编辑合同清单");
        requireEnabledMaterials(items);
        deriveFinancials(contract, items);
        LambdaQueryWrapper<CtContractItem> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(CtContractItem::getContractId, contractId);
        mapper.delete(wrapper);
        if (items != null && !items.isEmpty()) {
            Long tenantId = UserContext.getCurrentTenantId();
            items.forEach(i -> {
                i.setId(null);            // 清空ID，让ASSIGN_ID自动生成新ID，避免与软删除记录主键冲突
                i.setContractId(contractId);
                i.setTenantId(tenantId);
            });
            saveBatch(items);
        }
    }

    @Transactional(rollbackFor = Exception.class)
    public void update(CtContractItem item) {
        CtContract contract = requireDraftParentContract(item.getContractId(), "编辑合同清单");
        CtContractItem existing = mapper.selectById(item.getId());
        if (existing == null || !existing.getContractId().equals(item.getContractId())) {
            throw new BusinessException("ITEM_NOT_FOUND", "合同清单项不存在");
        }
        requireEnabledMaterials(List.of(item));
        deriveFinancials(contract, List.of(item));
        item.setTenantId(UserContext.getCurrentTenantId());
        mapper.updateById(item);
    }

    @Transactional(rollbackFor = Exception.class)
    public void delete(Long contractId, Long id) {
        requireDraftParentContract(contractId, "编辑合同清单");
        CtContractItem existing = mapper.selectById(id);
        if (existing == null || !existing.getContractId().equals(contractId)) {
            throw new BusinessException("ITEM_NOT_FOUND", "合同清单项不存在");
        }
        mapper.deleteById(id);
    }

    private void requireEnabledMaterials(List<CtContractItem> items) {
        if (items == null) return;
        Long tenantId = UserContext.getCurrentTenantId();
        for (Long materialId : items.stream()
                .map(CtContractItem::getMaterialId)
                .filter(java.util.Objects::nonNull)
                .distinct()
                .sorted()
                .toList()) {
            MdMaterial material = mdMaterialMapper.selectByIdForUpdate(materialId, tenantId);
            if (material == null || !"ENABLE".equals(material.getStatus())) {
                throw new BusinessException("MATERIAL_INVALID", "合同清单材料不存在或已停用");
            }
        }
    }

    void deriveFinancials(CtContract contract, List<CtContractItem> items) {
        if (items == null || items.isEmpty()) {
            return;
        }
        BigDecimal taxRate = contract.getTaxRate();
        if (taxRate == null || taxRate.compareTo(BigDecimal.ZERO) < 0 || taxRate.compareTo(HUNDRED) > 0) {
            throw new BusinessException("CONTRACT_TAX_RATE_INVALID", "合同税率必须在0到100之间");
        }
        for (CtContractItem item : items) {
            if (item.getQuantity() == null) {
                throw new BusinessException("CONTRACT_ITEM_QUANTITY_REQUIRED", "合同清单数量不能为空");
            }
            if (item.getUnitPrice() == null) {
                throw new BusinessException("CONTRACT_ITEM_UNIT_PRICE_REQUIRED", "合同清单单价不能为空");
            }
            if (item.getQuantity().compareTo(BigDecimal.ZERO) < 0 || item.getUnitPrice().compareTo(BigDecimal.ZERO) < 0) {
                throw new BusinessException("CONTRACT_ITEM_PRICE_INVALID", "合同清单数量和单价不能为负数");
            }
            BigDecimal amount = item.getQuantity().multiply(item.getUnitPrice()).setScale(2, RoundingMode.HALF_UP);
            BigDecimal amountWithoutTax = taxRate.signum() == 0
                    ? amount
                    : amount.multiply(HUNDRED).divide(HUNDRED.add(taxRate), 2, RoundingMode.HALF_UP);
            item.setAmount(amount);
            item.setTaxRate(taxRate);
            item.setAmountWithoutTax(amountWithoutTax);
            item.setTaxAmount(amount.subtract(amountWithoutTax));
        }
    }
}
