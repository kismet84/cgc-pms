package com.cgcpms.system.dict.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.cgcpms.auth.context.UserContext;
import com.cgcpms.common.exception.BusinessException;
import com.cgcpms.system.dict.entity.SysDictType;
import com.cgcpms.system.dict.mapper.SysDictTypeMapper;
import com.cgcpms.system.dict.vo.SysDictTypeVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.format.DateTimeFormatter;

@Service
@RequiredArgsConstructor
public class SysDictTypeService {

    private static final DateTimeFormatter DTF = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final SysDictTypeMapper sysDictTypeMapper;

    public IPage<SysDictTypeVO> getPage(long pageNo, long pageSize, String dictCode, String dictName, String status) {
        LambdaQueryWrapper<SysDictType> wrapper = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(dictCode)) wrapper.like(SysDictType::getDictCode, dictCode);
        if (StringUtils.hasText(dictName)) wrapper.like(SysDictType::getDictName, dictName);
        if (StringUtils.hasText(status)) wrapper.eq(SysDictType::getStatus, status);
        wrapper.eq(SysDictType::getTenantId, UserContext.getCurrentTenantId());
        wrapper.orderByDesc(SysDictType::getCreatedAt);

        Page<SysDictType> page = sysDictTypeMapper.selectPage(new Page<>(pageNo, pageSize), wrapper);
        return page.convert(this::toVO);
    }

    public SysDictTypeVO getById(Long id) {
        SysDictType entity = sysDictTypeMapper.selectById(id);
        if (entity == null) throw new BusinessException("DICT_TYPE_NOT_FOUND", "字典类型不存在");
        if (!entity.getTenantId().equals(UserContext.getCurrentTenantId())) {
            throw new BusinessException("DICT_TYPE_NOT_FOUND", "字典类型不存在");
        }
        return toVO(entity);
    }

    @Transactional
    public Long create(SysDictType entity) {
        if (sysDictTypeMapper.selectCount(new LambdaQueryWrapper<SysDictType>()
                .eq(SysDictType::getDictCode, entity.getDictCode())
                .eq(SysDictType::getTenantId, UserContext.getCurrentTenantId())) > 0) {
            throw new BusinessException("DICT_CODE_EXISTS", "字典编码已存在");
        }
        if (entity.getStatus() == null) entity.setStatus("ENABLE");
        entity.setTenantId(UserContext.getCurrentTenantId());
        sysDictTypeMapper.insert(entity);
        return entity.getId();
    }

    @Transactional
    public void update(SysDictType entity) {
        SysDictType existing = sysDictTypeMapper.selectById(entity.getId());
        if (existing == null) throw new BusinessException("DICT_TYPE_NOT_FOUND", "字典类型不存在");
        if (!existing.getTenantId().equals(UserContext.getCurrentTenantId())) {
            throw new BusinessException("DICT_TYPE_NOT_FOUND", "字典类型不存在");
        }
        sysDictTypeMapper.updateById(entity);
    }

    @Transactional
    public void delete(Long id) {
        SysDictType existing = sysDictTypeMapper.selectById(id);
        if (existing == null) throw new BusinessException("DICT_TYPE_NOT_FOUND", "字典类型不存在");
        if (!existing.getTenantId().equals(UserContext.getCurrentTenantId())) {
            throw new BusinessException("DICT_TYPE_NOT_FOUND", "字典类型不存在");
        }
        sysDictTypeMapper.deleteById(id);
    }

    private SysDictTypeVO toVO(SysDictType entity) {
        SysDictTypeVO vo = new SysDictTypeVO();
        vo.setId(entity.getId() == null ? null : String.valueOf(entity.getId()));
        vo.setDictCode(entity.getDictCode());
        vo.setDictName(entity.getDictName());
        vo.setStatus(entity.getStatus());
        if (entity.getCreatedAt() != null) vo.setCreatedAt(DTF.format(entity.getCreatedAt()));
        if (entity.getUpdatedAt() != null) vo.setUpdatedAt(DTF.format(entity.getUpdatedAt()));
        return vo;
    }
}
