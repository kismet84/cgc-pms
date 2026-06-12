package com.cgcpms.system.dict.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.cgcpms.auth.context.UserContext;
import com.cgcpms.common.exception.BusinessException;
import com.cgcpms.system.dict.entity.SysDictData;
import com.cgcpms.system.dict.mapper.SysDictDataMapper;
import com.cgcpms.system.dict.vo.SysDictDataVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.format.DateTimeFormatter;

@Service
@RequiredArgsConstructor
public class SysDictDataService {

    private static final DateTimeFormatter DTF = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final SysDictDataMapper sysDictDataMapper;

    public IPage<SysDictDataVO> getPage(long pageNo, long pageSize, Long dictTypeId, String dictLabel, String status) {
        LambdaQueryWrapper<SysDictData> wrapper = new LambdaQueryWrapper<>();
        if (dictTypeId != null) wrapper.eq(SysDictData::getDictTypeId, dictTypeId);
        if (StringUtils.hasText(dictLabel)) wrapper.like(SysDictData::getDictLabel, dictLabel);
        if (StringUtils.hasText(status)) wrapper.eq(SysDictData::getStatus, status);
        wrapper.eq(SysDictData::getTenantId, UserContext.getCurrentTenantId());
        wrapper.orderByAsc(SysDictData::getOrderNum);

        Page<SysDictData> page = sysDictDataMapper.selectPage(new Page<>(pageNo, pageSize), wrapper);
        return page.convert(this::toVO);
    }

    public SysDictDataVO getById(Long id) {
        SysDictData entity = sysDictDataMapper.selectById(id);
        if (entity == null) throw new BusinessException("DICT_DATA_NOT_FOUND", "字典数据不存在");
        if (!entity.getTenantId().equals(UserContext.getCurrentTenantId())) {
            throw new BusinessException("DICT_DATA_NOT_FOUND", "字典数据不存在");
        }
        return toVO(entity);
    }

    @Transactional
    public Long create(SysDictData entity) {
        if (sysDictDataMapper.selectCount(new LambdaQueryWrapper<SysDictData>()
                .eq(SysDictData::getDictTypeId, entity.getDictTypeId())
                .eq(SysDictData::getDictValue, entity.getDictValue())
                .eq(SysDictData::getTenantId, UserContext.getCurrentTenantId())) > 0) {
            throw new BusinessException("DICT_VALUE_EXISTS", "该字典类型下键值已存在");
        }
        if (entity.getStatus() == null) entity.setStatus("ENABLE");
        if (entity.getOrderNum() == null) entity.setOrderNum(0);
        entity.setTenantId(UserContext.getCurrentTenantId());
        sysDictDataMapper.insert(entity);
        return entity.getId();
    }

    @Transactional
    public void update(SysDictData entity) {
        SysDictData existing = sysDictDataMapper.selectById(entity.getId());
        if (existing == null) throw new BusinessException("DICT_DATA_NOT_FOUND", "字典数据不存在");
        if (!existing.getTenantId().equals(UserContext.getCurrentTenantId())) {
            throw new BusinessException("DICT_DATA_NOT_FOUND", "字典数据不存在");
        }
        sysDictDataMapper.updateById(entity);
    }

    @Transactional
    public void delete(Long id) {
        SysDictData existing = sysDictDataMapper.selectById(id);
        if (existing == null) throw new BusinessException("DICT_DATA_NOT_FOUND", "字典数据不存在");
        if (!existing.getTenantId().equals(UserContext.getCurrentTenantId())) {
            throw new BusinessException("DICT_DATA_NOT_FOUND", "字典数据不存在");
        }
        sysDictDataMapper.deleteById(id);
    }

    private SysDictDataVO toVO(SysDictData entity) {
        SysDictDataVO vo = new SysDictDataVO();
        vo.setId(entity.getId() == null ? null : String.valueOf(entity.getId()));
        vo.setDictTypeId(entity.getDictTypeId() == null ? null : String.valueOf(entity.getDictTypeId()));
        vo.setDictLabel(entity.getDictLabel());
        vo.setDictValue(entity.getDictValue());
        vo.setCssClass(entity.getCssClass());
        vo.setListClass(entity.getListClass());
        vo.setOrderNum(entity.getOrderNum());
        vo.setStatus(entity.getStatus());
        if (entity.getCreatedAt() != null) vo.setCreatedAt(DTF.format(entity.getCreatedAt()));
        if (entity.getUpdatedAt() != null) vo.setUpdatedAt(DTF.format(entity.getUpdatedAt()));
        return vo;
    }
}
