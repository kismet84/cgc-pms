package com.cgcpms.inventory.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.cgcpms.auth.context.UserContext;
import com.cgcpms.common.exception.BusinessException;
import com.cgcpms.common.result.PageResult;
import com.cgcpms.inventory.entity.MatWarehouse;
import com.cgcpms.inventory.mapper.MatWarehouseMapper;
import com.cgcpms.inventory.vo.MatWarehouseVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.format.DateTimeFormatter;

@Service
@RequiredArgsConstructor
public class MatWarehouseService {

    private static final DateTimeFormatter DTF = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final MatWarehouseMapper matWarehouseMapper;

    public PageResult<MatWarehouseVO> getPage(long pageNo, long pageSize, Long projectId, String warehouseCode, String warehouseName, String status) {
        LambdaQueryWrapper<MatWarehouse> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(MatWarehouse::getTenantId, UserContext.getCurrentTenantId());
        if (projectId != null) wrapper.eq(MatWarehouse::getProjectId, projectId);
        if (StringUtils.hasText(warehouseCode)) wrapper.like(MatWarehouse::getWarehouseCode, warehouseCode);
        if (StringUtils.hasText(warehouseName)) wrapper.like(MatWarehouse::getWarehouseName, warehouseName);
        if (StringUtils.hasText(status)) wrapper.eq(MatWarehouse::getStatus, status);
        wrapper.orderByDesc(MatWarehouse::getCreatedTime);

        Page<MatWarehouse> page = matWarehouseMapper.selectPage(new Page<>(pageNo, pageSize), wrapper);
        IPage<MatWarehouseVO> voPage = page.convert(this::toVO);
        return PageResult.of(voPage);
    }

    public MatWarehouseVO getById(Long id) {
        MatWarehouse warehouse = matWarehouseMapper.selectById(id);
        if (warehouse == null) {
            throw new BusinessException("WAREHOUSE_NOT_FOUND", "仓库不存在");
        }
        if (!warehouse.getTenantId().equals(UserContext.getCurrentTenantId())) {
            throw new BusinessException("WAREHOUSE_NOT_FOUND", "仓库不存在");
        }
        return toVO(warehouse);
    }

    @Transactional
    public Long create(MatWarehouse warehouse) {
        warehouse.setTenantId(UserContext.getCurrentTenantId());
        matWarehouseMapper.insert(warehouse);
        return warehouse.getId();
    }

    @Transactional
    public void update(MatWarehouse warehouse) {
        MatWarehouse existing = matWarehouseMapper.selectById(warehouse.getId());
        if (existing == null) {
            throw new BusinessException("WAREHOUSE_NOT_FOUND", "仓库不存在");
        }
        if (!existing.getTenantId().equals(UserContext.getCurrentTenantId())) {
            throw new BusinessException("WAREHOUSE_NOT_FOUND", "仓库不存在");
        }
        matWarehouseMapper.updateById(warehouse);
    }

    @Transactional
    public void updateStatus(Long id, String status) {
        MatWarehouse existing = matWarehouseMapper.selectById(id);
        if (existing == null) {
            throw new BusinessException("WAREHOUSE_NOT_FOUND", "仓库不存在");
        }
        if (!existing.getTenantId().equals(UserContext.getCurrentTenantId())) {
            throw new BusinessException("WAREHOUSE_NOT_FOUND", "仓库不存在");
        }
        existing.setStatus(status);
        matWarehouseMapper.updateById(existing);
    }

    private MatWarehouseVO toVO(MatWarehouse w) {
        MatWarehouseVO vo = new MatWarehouseVO();
        vo.setId(w.getId() != null ? w.getId().toString() : null);
        vo.setTenantId(w.getTenantId() != null ? w.getTenantId().toString() : null);
        vo.setProjectId(w.getProjectId() != null ? w.getProjectId().toString() : null);
        vo.setWarehouseCode(w.getWarehouseCode());
        vo.setWarehouseName(w.getWarehouseName());
        vo.setStatus(w.getStatus());
        vo.setCreatedBy(w.getCreatedBy() != null ? w.getCreatedBy().toString() : null);
        vo.setCreatedAt(w.getCreatedTime() != null ? DTF.format(w.getCreatedTime()) : null);
        vo.setUpdatedAt(w.getUpdatedTime() != null ? DTF.format(w.getUpdatedTime()) : null);
        vo.setRemark(w.getRemark());
        return vo;
    }
}
