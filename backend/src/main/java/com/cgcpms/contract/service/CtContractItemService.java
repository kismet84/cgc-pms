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
import com.cgcpms.project.auth.ProjectAccessChecker;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class CtContractItemService extends ServiceImpl<CtContractItemMapper, CtContractItem> {

    private final CtContractItemMapper mapper;
    private final CtContractMapper ctContractMapper;
    private final ProjectAccessChecker projectAccessChecker;

    /**
     * Verify parent contract belongs to current tenant and is in DRAFT status (editable).
     */
    private CtContract requireDraftParentContract(Long contractId, String action) {
        CtContract contract = requireParentContract(contractId, action);
        if (!ContractStatusConstants.APPROVAL_DRAFT.equals(contract.getApprovalStatus()))
            throw new BusinessException("CONTRACT_NOT_EDITABLE", "合同非草稿状态，不可编辑");
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
        requireDraftParentContract(item.getContractId(), "编辑合同清单");
        item.setTenantId(UserContext.getCurrentTenantId());
        mapper.insert(item);
        return item.getId();
    }

    @Transactional(rollbackFor = Exception.class)
    public void batchSave(Long contractId, List<CtContractItem> items) {
        requireDraftParentContract(contractId, "编辑合同清单");
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
        requireDraftParentContract(item.getContractId(), "编辑合同清单");
        CtContractItem existing = mapper.selectById(item.getId());
        if (existing == null || !existing.getContractId().equals(item.getContractId())) {
            throw new BusinessException("ITEM_NOT_FOUND", "合同清单项不存在");
        }
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
}
