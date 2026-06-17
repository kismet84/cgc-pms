package com.cgcpms.contract.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.cgcpms.common.exception.BusinessException;
import com.cgcpms.contract.entity.CtContractItem;
import com.cgcpms.contract.mapper.CtContractItemMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class CtContractItemService extends ServiceImpl<CtContractItemMapper, CtContractItem> {

    private final CtContractItemMapper mapper;

    public List<CtContractItem> getByContractId(Long contractId) {
        LambdaQueryWrapper<CtContractItem> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(CtContractItem::getContractId, contractId)
               .orderByAsc(CtContractItem::getSortOrder);
        return mapper.selectList(wrapper);
    }

    @Transactional
    public Long create(CtContractItem item) {
        mapper.insert(item);
        return item.getId();
    }

    @Transactional
    public void batchSave(Long contractId, List<CtContractItem> items) {
        LambdaQueryWrapper<CtContractItem> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(CtContractItem::getContractId, contractId);
        mapper.delete(wrapper);
        if (items != null && !items.isEmpty()) {
            items.forEach(i -> {
                i.setId(null);            // 清空ID，让ASSIGN_ID自动生成新ID，避免与软删除记录主键冲突
                i.setContractId(contractId);
            });
            saveBatch(items);
        }
    }

    @Transactional
    public void update(CtContractItem item) {
        CtContractItem existing = mapper.selectById(item.getId());
        if (existing == null || !existing.getContractId().equals(item.getContractId())) {
            throw new BusinessException("ITEM_NOT_FOUND", "合同清单项不存在");
        }
        mapper.updateById(item);
    }

    @Transactional
    public void delete(Long contractId, Long id) {
        CtContractItem existing = mapper.selectById(id);
        if (existing == null || !existing.getContractId().equals(contractId)) {
            throw new BusinessException("ITEM_NOT_FOUND", "合同清单项不存在");
        }
        mapper.deleteById(id);
    }
}
