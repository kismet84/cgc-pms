package com.cgcpms.system.dict.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.cgcpms.auth.context.UserContext;
import com.cgcpms.common.exception.BusinessException;
import com.cgcpms.system.dict.entity.SysDictType;
import com.cgcpms.system.dict.entity.SysDictData;
import com.cgcpms.system.dict.mapper.SysDictDataMapper;
import com.cgcpms.system.dict.mapper.SysDictTypeMapper;
import com.cgcpms.system.dict.vo.SysDictTypeVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import com.cgcpms.common.util.DateTimeUtils;

import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

@Slf4j
@Service
@RequiredArgsConstructor
public class SysDictTypeService {

    private static final Pattern DICT_CODE_PATTERN = Pattern.compile("^[a-z][a-z0-9_]{1,99}$");
    private static final Set<String> VALID_STATUSES = Set.of("ENABLE", "DISABLE");

    private final SysDictTypeMapper sysDictTypeMapper;
    private final SysDictDataMapper sysDictDataMapper;
    private final SysDictDataService sysDictDataService;

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

    @Transactional(rollbackFor = Exception.class)
    public Long create(SysDictType entity) {
        normalizeAndValidate(entity);
        rejectTenantCoreOverride(entity.getDictCode());
        if (sysDictTypeMapper.selectCount(new LambdaQueryWrapper<SysDictType>()
                .eq(SysDictType::getDictCode, entity.getDictCode())
                .eq(SysDictType::getTenantId, UserContext.getCurrentTenantId())) > 0) {
            throw new BusinessException("DICT_CODE_EXISTS", "字典编码已存在");
        }
        entity.setTenantId(UserContext.getCurrentTenantId());
        sysDictTypeMapper.insert(entity);
        sysDictDataService.evictCache(entity.getDictCode());
        log.info("Creating dict type: {}", entity.getDictCode());
        return entity.getId();
    }

    @Transactional(rollbackFor = Exception.class)
    public void update(SysDictType entity) {
        SysDictType existing = sysDictTypeMapper.selectById(entity.getId());
        if (existing == null) throw new BusinessException("DICT_TYPE_NOT_FOUND", "字典类型不存在");
        if (!existing.getTenantId().equals(UserContext.getCurrentTenantId())) {
            throw new BusinessException("DICT_TYPE_NOT_FOUND", "字典类型不存在");
        }
        if (!existing.getDictCode().equals(entity.getDictCode())) {
            throw new BusinessException("DICT_CODE_IMMUTABLE", "字典编码创建后不可修改");
        }
        rejectTenantCoreOverride(existing.getDictCode());
        normalizeAndValidate(entity);
        entity.setTenantId(existing.getTenantId());
        sysDictTypeMapper.updateById(entity);
        sysDictDataService.evictCache(existing.getDictCode());
    }

    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        SysDictType existing = sysDictTypeMapper.selectById(id);
        if (existing == null) throw new BusinessException("DICT_TYPE_NOT_FOUND", "字典类型不存在");
        if (!existing.getTenantId().equals(UserContext.getCurrentTenantId())) {
            throw new BusinessException("DICT_TYPE_NOT_FOUND", "字典类型不存在");
        }
        if (sysDictDataMapper.selectCount(new LambdaQueryWrapper<SysDictData>()
                .eq(SysDictData::getDictTypeId, id)
                .eq(SysDictData::getTenantId, existing.getTenantId())) > 0) {
            throw new BusinessException("DICT_TYPE_HAS_DATA", "字典类型存在标签，不能删除");
        }
        sysDictTypeMapper.deleteById(id);
        sysDictDataService.evictCache(existing.getDictCode());
    }

    private void normalizeAndValidate(SysDictType entity) {
        String dictCode = entity.getDictCode() == null
                ? ""
                : entity.getDictCode().trim().toLowerCase(Locale.ROOT);
        if (!DICT_CODE_PATTERN.matcher(dictCode).matches()) {
            throw new BusinessException("INVALID_DICT_CODE", "字典编码必须为小写蛇形命名，长度 2-100 位");
        }
        if (!StringUtils.hasText(entity.getDictName())) {
            throw new BusinessException("INVALID_DICT_NAME", "字典名称不能为空");
        }
        String status = StringUtils.hasText(entity.getStatus())
                ? entity.getStatus().trim().toUpperCase(Locale.ROOT)
                : "ENABLE";
        if (!VALID_STATUSES.contains(status)) {
            throw new BusinessException("INVALID_DICT_STATUS", "字典状态仅支持 ENABLE 或 DISABLE");
        }
        entity.setDictCode(dictCode);
        entity.setDictName(entity.getDictName().trim());
        entity.setStatus(status);
    }

    private void rejectTenantCoreOverride(String dictCode) {
        Long tenantId = UserContext.getCurrentTenantId();
        if (tenantId != null && tenantId != 0L && SysDictDataService.isSystemGoverned(dictCode)) {
            throw new BusinessException("DICT_CORE_TYPE_TENANT_OVERRIDE_FORBIDDEN", "核心业务字典由系统统一治理，租户不能覆盖");
        }
    }

    private SysDictTypeVO toVO(SysDictType entity) {
        SysDictTypeVO vo = new SysDictTypeVO();
        vo.setId(entity.getId() == null ? null : String.valueOf(entity.getId()));
        vo.setDictCode(entity.getDictCode());
        vo.setDictName(entity.getDictName());
        vo.setStatus(entity.getStatus());
        if (entity.getCreatedAt() != null) vo.setCreatedAt(DateTimeUtils.DTF.format(entity.getCreatedAt()));
        if (entity.getUpdatedAt() != null) vo.setUpdatedAt(DateTimeUtils.DTF.format(entity.getUpdatedAt()));
        return vo;
    }
}
