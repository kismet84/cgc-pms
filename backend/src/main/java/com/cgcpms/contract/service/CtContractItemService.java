package com.cgcpms.contract.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
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
            saveBatch(items);
        }
    }

    @Transactional
    public void update(CtContractItem item) {
        mapper.updateById(item);
    }

    @Transactional
    public void delete(Long id) {
        mapper.deleteById(id);
    }
}
