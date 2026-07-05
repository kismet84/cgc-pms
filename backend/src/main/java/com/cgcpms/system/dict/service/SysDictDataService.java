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
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import com.cgcpms.common.util.DateTimeUtils;

import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
public class SysDictDataService {

    private static final long SYSTEM_TENANT_ID = 0L;

    private final SysDictDataMapper sysDictDataMapper;
    private final SysDictTypeMapper sysDictTypeMapper;

    /**
     * 字典数据缓存：key = "tenantId:dictCode", value = List<SysDictDataVO>
     * 缓存 TTL 10 分钟，最大 1000 条
     */
    private final LoadingCache<String, List<SysDictDataVO>> dictCache = CacheBuilder.newBuilder()
            .maximumSize(1000)
            .expireAfterWrite(10, TimeUnit.MINUTES)
            .build(new CacheLoader<>() {
                @Override
                public List<SysDictDataVO> load(String key) {
                    String[] parts = key.split(":", 2);
                    Long tenantId = Long.parseLong(parts[0]);
                    String dictCode = parts[1];
                    return fetchByDictCode(dictCode, tenantId);
                }
            });

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

    /**
     * 根据字典编码查询字典数据列表（带缓存）
     * @param dictCode 字典编码
     * @return 字典数据列表
     */
    public List<SysDictDataVO> getByDictCodeCached(String dictCode) {
        Long tenantId = currentTenantIdOrSystem();
        String cacheKey = tenantId + ":" + dictCode;
        try {
            return dictCache.get(cacheKey);
        } catch (ExecutionException e) {
            log.error("Failed to load dict data for code: {}, tenantId: {}", dictCode, tenantId, e);
            return List.of();
        }
    }

    /**
     * 清除指定字典编码的缓存
     * @param dictCode 字典编码
     */
    public void evictCache(String dictCode) {
        Long tenantId = currentTenantIdOrSystem();
        String cacheKey = tenantId + ":" + dictCode;
        dictCache.invalidate(cacheKey);
        log.debug("Evicted dict cache for code: {}, tenantId: {}", dictCode, tenantId);
    }

    /**
     * 清除所有缓存
     */
    public void evictAllCache() {
        dictCache.invalidateAll();
        log.debug("Evicted all dict cache");
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
        SysDictType dictType = sysDictTypeMapper.selectOne(
                new LambdaQueryWrapper<SysDictType>()
                        .eq(SysDictType::getDictCode, dictCode)
                        .eq(SysDictType::getTenantId, tenantId)
        );
        if (dictType == null) {
            return List.of();
        }
        // 2. 查字典数据
        List<SysDictData> dataList = sysDictDataMapper.selectList(
                new LambdaQueryWrapper<SysDictData>()
                        .eq(SysDictData::getDictTypeId, dictType.getId())
                        .eq(SysDictData::getTenantId, tenantId)
                        .eq(SysDictData::getStatus, "ENABLE")
                        .orderByAsc(SysDictData::getOrderNum)
        );
        return dataList.stream().map(this::toVO).toList();
    }

    private Long currentTenantIdOrSystem() {
        Long tenantId = UserContext.getCurrentTenantId();
        return tenantId != null ? tenantId : SYSTEM_TENANT_ID;
    }
}
