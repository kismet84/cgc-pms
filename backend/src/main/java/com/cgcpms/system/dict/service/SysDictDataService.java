package com.cgcpms.system.dict.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.cgcpms.auth.context.UserContext;
import com.cgcpms.common.exception.BusinessException;
import com.cgcpms.system.dict.entity.SysDictData;
import com.cgcpms.system.dict.entity.SysDictType;
import com.cgcpms.system.dict.mapper.SysDictDataMapper;
import com.cgcpms.system.dict.mapper.SysDictTypeMapper;
import com.cgcpms.system.dict.vo.SysDictDataVO;
import com.google.common.cache.LoadingCache;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import com.cgcpms.common.util.DateTimeUtils;

import java.util.List;
import java.util.Locale;
import java.util.Set;

@Slf4j
@Service
public class SysDictDataService {

    private static final long SYSTEM_TENANT_ID = 0L;
    private static final Set<String> VALID_STATUSES = Set.of("ENABLE", "DISABLE");
    private static final Set<String> SYSTEM_GOVERNED_CODES = Set.of(
            "project_type", "project_status", "approval_status", "partner_type",
            "contract_type", "contract_status", "cost_type", "cost_source_type", "cost_status");

    private final SysDictDataMapper sysDictDataMapper;
    private final SysDictTypeMapper sysDictTypeMapper;

    public SysDictDataService(SysDictDataMapper sysDictDataMapper, SysDictTypeMapper sysDictTypeMapper) {
        this.sysDictDataMapper = sysDictDataMapper;
        this.sysDictTypeMapper = sysDictTypeMapper;
    }

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

    @Transactional(rollbackFor = Exception.class)
    public Long create(SysDictData entity) {
        SysDictType dictType = requireOwnedType(entity.getDictTypeId());
        normalizeAndValidate(entity);
        if (sysDictDataMapper.selectCount(new LambdaQueryWrapper<SysDictData>()
                .eq(SysDictData::getDictTypeId, entity.getDictTypeId())
                .eq(SysDictData::getDictValue, entity.getDictValue())
                .eq(SysDictData::getTenantId, UserContext.getCurrentTenantId())) > 0) {
            throw new BusinessException("DICT_VALUE_EXISTS", "该字典类型下键值已存在");
        }
        entity.setTenantId(dictType.getTenantId());
        sysDictDataMapper.insert(entity);
        log.info("Creating dict data: dictTypeId={}, dictValue={}", entity.getDictTypeId(), entity.getDictValue());
        
        // 清除相关字典缓存
        evictCacheByTypeId(entity.getDictTypeId());
        
        return entity.getId();
    }

    @Transactional(rollbackFor = Exception.class)
    public void update(SysDictData entity) {
        SysDictData existing = sysDictDataMapper.selectById(entity.getId());
        if (existing == null) throw new BusinessException("DICT_DATA_NOT_FOUND", "字典数据不存在");
        if (!existing.getTenantId().equals(UserContext.getCurrentTenantId())) {
            throw new BusinessException("DICT_DATA_NOT_FOUND", "字典数据不存在");
        }
        if (!existing.getDictTypeId().equals(entity.getDictTypeId())) {
            throw new BusinessException("DICT_TYPE_IMMUTABLE", "标签所属字典类型创建后不可修改");
        }
        if (!existing.getDictValue().equals(entity.getDictValue())) {
            throw new BusinessException("DICT_VALUE_IMMUTABLE", "标签键值创建后不可修改");
        }
        requireOwnedType(existing.getDictTypeId());
        normalizeAndValidate(entity);
        SysDictType dictType = sysDictTypeMapper.selectById(existing.getDictTypeId());
        if (existing.getTenantId() == SYSTEM_TENANT_ID && dictType != null
                && isSystemGoverned(dictType.getDictCode()) && "DISABLE".equals(entity.getStatus())) {
            throw new BusinessException("DICT_CORE_VALUE_DISABLE_FORBIDDEN", "核心业务字典值不能停用");
        }
        entity.setTenantId(existing.getTenantId());
        sysDictDataMapper.updateById(entity);
        
        // 清除相关字典缓存
        evictCacheByTypeId(existing.getDictTypeId());
    }

    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        SysDictData existing = sysDictDataMapper.selectById(id);
        if (existing == null) throw new BusinessException("DICT_DATA_NOT_FOUND", "字典数据不存在");
        if (!existing.getTenantId().equals(UserContext.getCurrentTenantId())) {
            throw new BusinessException("DICT_DATA_NOT_FOUND", "字典数据不存在");
        }
        SysDictType dictType = sysDictTypeMapper.selectById(existing.getDictTypeId());
        if (existing.getTenantId() == SYSTEM_TENANT_ID && dictType != null
                && isSystemGoverned(dictType.getDictCode())) {
            throw new BusinessException("DICT_CORE_VALUE_DELETE_FORBIDDEN", "核心业务字典值不能删除");
        }
        
        // 清除相关字典缓存
        evictCacheByTypeId(existing.getDictTypeId());
        
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
        if (entity.getCreatedAt() != null) vo.setCreatedAt(DateTimeUtils.DTF.format(entity.getCreatedAt()));
        if (entity.getUpdatedAt() != null) vo.setUpdatedAt(DateTimeUtils.DTF.format(entity.getUpdatedAt()));
        return vo;
    }
    public java.util.List<SysDictDataVO> getByDictCode(String dictCode) {
        return fetchByDictCode(dictCode, currentTenantIdOrSystem());
    }

    public String requireEnabledValue(String dictCode, String dictValue, String errorCode, String message) {
        String normalizedValue = dictValue == null ? "" : dictValue.trim().toUpperCase(Locale.ROOT);
        boolean exists = getByDictCodeCached(dictCode).stream()
                .anyMatch(item -> normalizedValue.equals(item.getDictValue()));
        if (!exists) throw new BusinessException(errorCode, message);
        return normalizedValue;
    }

    /**
     * 根据字典编码查询字典数据列表（带缓存）
     * @param dictCode 字典编码
     * @return 字典数据列表
     */
    public List<SysDictDataVO> getByDictCodeCached(String dictCode) {
        return fetchByDictCode(dictCode, currentTenantIdOrSystem());
    }

    /**
     * 清除指定字典编码的缓存
     * @param dictCode 字典编码
     */
    public void evictCache(String dictCode) {
        // Compatibility no-op: authoritative reads intentionally bypass process-local caches.
    }

    /**
     * 清除所有缓存
     */
    public void evictAllCache() {
        // Compatibility no-op: authoritative reads intentionally bypass process-local caches.
    }

    /**
     * 根据字典类型 ID 清除缓存
     * @param dictTypeId 字典类型 ID
     */
    private void evictCacheByTypeId(Long dictTypeId) {
        if (dictTypeId == null) return;
        
        // 查询字典类型获取 dictCode
        SysDictType dictType = sysDictTypeMapper.selectById(dictTypeId);
        if (dictType != null) {
            evictCache(dictType.getDictCode());
        }
    }

    /**
     * 实际查询逻辑（无缓存）
     */
    private List<SysDictDataVO> fetchByDictCode(String dictCode, Long tenantId) {
        // 1. 查字典类型
        SysDictType dictType = isSystemGoverned(dictCode)
                ? findEnabledType(dictCode, SYSTEM_TENANT_ID)
                : findEnabledType(dictCode, tenantId);
        if (dictType == null && tenantId != null && tenantId != SYSTEM_TENANT_ID) {
            dictType = findEnabledType(dictCode, SYSTEM_TENANT_ID);
        }
        if (dictType == null) {
            return List.of();
        }
        Long dictTenantId = dictType.getTenantId();
        List<SysDictData> dataList = sysDictDataMapper.selectEnabledByTypeAndTenant(
                dictType.getId(), dictTenantId);
        return dataList.stream().map(this::toVO).toList();
    }

    private SysDictType findEnabledType(String dictCode, Long tenantId) {
        return sysDictTypeMapper.selectEnabledByCodeAndTenant(dictCode, tenantId);
    }

    static boolean isSystemGoverned(String dictCode) {
        return dictCode != null && SYSTEM_GOVERNED_CODES.contains(dictCode.trim().toLowerCase(Locale.ROOT));
    }

    private Long currentTenantIdOrSystem() {
        Long tenantId = UserContext.getCurrentTenantId();
        return tenantId != null ? tenantId : SYSTEM_TENANT_ID;
    }

    private SysDictType requireOwnedType(Long dictTypeId) {
        SysDictType dictType = dictTypeId == null ? null : sysDictTypeMapper.selectById(dictTypeId);
        Long tenantId = UserContext.getCurrentTenantId();
        if (dictType == null || !dictType.getTenantId().equals(tenantId)) {
            throw new BusinessException("DICT_TYPE_NOT_FOUND", "字典类型不存在");
        }
        return dictType;
    }

    private void normalizeAndValidate(SysDictData entity) {
        if (!StringUtils.hasText(entity.getDictLabel())) {
            throw new BusinessException("INVALID_DICT_LABEL", "标签名称不能为空");
        }
        if (!StringUtils.hasText(entity.getDictValue())) {
            throw new BusinessException("INVALID_DICT_VALUE", "标签键值不能为空");
        }
        String status = StringUtils.hasText(entity.getStatus())
                ? entity.getStatus().trim().toUpperCase(Locale.ROOT)
                : "ENABLE";
        if (!VALID_STATUSES.contains(status)) {
            throw new BusinessException("INVALID_DICT_STATUS", "标签状态仅支持 ENABLE 或 DISABLE");
        }
        entity.setDictLabel(entity.getDictLabel().trim());
        entity.setDictValue(entity.getDictValue().trim().toUpperCase(Locale.ROOT));
        entity.setCssClass(trimToNull(entity.getCssClass()));
        entity.setListClass(trimToNull(entity.getListClass()));
        entity.setOrderNum(entity.getOrderNum() == null ? 0 : entity.getOrderNum());
        entity.setStatus(status);
    }

    private String trimToNull(String value) {
        if (!StringUtils.hasText(value)) return null;
        return value.trim();
    }
}
