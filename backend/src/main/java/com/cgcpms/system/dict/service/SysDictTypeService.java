package com.cgcpms.system.dict.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.cgcpms.auth.context.UserContext;
import com.cgcpms.common.exception.BusinessException;
import com.cgcpms.common.util.DateTimeUtils;
import com.cgcpms.system.dict.entity.SysDictData;
import com.cgcpms.system.dict.entity.SysDictGroup;
import com.cgcpms.system.dict.entity.SysDictType;
import com.cgcpms.system.dict.mapper.SysDictDataMapper;
import com.cgcpms.system.dict.mapper.SysDictGroupMapper;
import com.cgcpms.system.dict.mapper.SysDictTypeMapper;
import com.cgcpms.system.dict.vo.SysDictDataVO;
import com.cgcpms.system.dict.vo.SysDictGroupTreeVO;
import com.cgcpms.system.dict.vo.SysDictTypeVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class SysDictTypeService {

    private static final String DEFAULT_BUSINESS_GROUP_CODE = "BUSINESS_GOVERNANCE";
    private static final Pattern DICT_CODE_PATTERN = Pattern.compile("^[a-z][a-z0-9_]{1,99}$");
    private static final Set<String> VALID_STATUSES = Set.of("ENABLE", "DISABLE");
    private static final Set<String> VALID_CLASSES = Set.of("BUSINESS", "SYSTEM", "STATE_MACHINE");

    private final SysDictTypeMapper typeMapper;
    private final SysDictDataMapper dataMapper;
    private final SysDictGroupMapper groupMapper;
    private final SysDictDataService dataService;

    public IPage<SysDictTypeVO> getPage(long pageNo, long pageSize, String dictCode,
                                        String dictName, String status) {
        return getPage(pageNo, pageSize, null, dictCode, dictName, status, null);
    }

    public IPage<SysDictTypeVO> getPage(long pageNo, long pageSize, Long groupId, String dictCode,
                                        String dictName, String status, String dictClass) {
        LambdaQueryWrapper<SysDictType> wrapper = ownedTypeQuery();
        if (groupId != null) wrapper.eq(SysDictType::getGroupId, groupId);
        if (StringUtils.hasText(dictCode)) wrapper.like(SysDictType::getDictCode, dictCode.trim());
        if (StringUtils.hasText(dictName)) wrapper.like(SysDictType::getDictName, dictName.trim());
        if (StringUtils.hasText(status)) wrapper.eq(SysDictType::getStatus, normalizeStatus(status));
        if (StringUtils.hasText(dictClass)) wrapper.eq(SysDictType::getDictClass, normalizeClass(dictClass));
        wrapper.orderByAsc(SysDictType::getGroupId).orderByAsc(SysDictType::getDictCode);
        Map<Long, SysDictGroup> groups = ownedGroupsById();
        Page<SysDictType> page = typeMapper.selectPage(new Page<>(pageNo, pageSize), wrapper);
        return page.convert(type -> toVO(type, groups.get(type.getGroupId())));
    }

    public SysDictTypeVO getById(Long id) {
        SysDictType type = requireOwnedType(id);
        return toVO(type, requireOwnedGroup(type.getGroupId()));
    }

    public List<SysDictGroupTreeVO> getTree(String keyword) {
        Long tenantId = UserContext.getCurrentTenantId();
        LambdaQueryWrapper<SysDictGroup> groupQuery = new LambdaQueryWrapper<SysDictGroup>()
                .eq(SysDictGroup::getTenantId, tenantId)
                .orderByAsc(SysDictGroup::getOrderNum)
                .orderByAsc(SysDictGroup::getGroupCode);
        List<SysDictGroup> groups = groupMapper.selectList(groupQuery);
        if (groups.isEmpty()) return List.of();

        List<SysDictType> types = typeMapper.selectList(ownedTypeQuery()
                .in(SysDictType::getGroupId, groups.stream().map(SysDictGroup::getId).toList())
                .orderByAsc(SysDictType::getDictCode));
        Map<Long, List<SysDictDataVO>> dataByType = types.isEmpty()
                ? Map.of()
                : dataMapper.selectList(new LambdaQueryWrapper<SysDictData>()
                                .eq(SysDictData::getTenantId, tenantId)
                                .in(SysDictData::getDictTypeId, types.stream().map(SysDictType::getId).toList())
                                .orderByAsc(SysDictData::getOrderNum)
                                .orderByAsc(SysDictData::getDictValue))
                        .stream().collect(Collectors.groupingBy(SysDictData::getDictTypeId,
                                LinkedHashMap::new, Collectors.mapping(dataService::toVO, Collectors.toList())));
        Map<Long, List<SysDictType>> typesByGroup = types.stream()
                .collect(Collectors.groupingBy(SysDictType::getGroupId, LinkedHashMap::new, Collectors.toList()));

        String normalizedKeyword = StringUtils.hasText(keyword)
                ? keyword.trim().toLowerCase(Locale.ROOT) : null;
        List<SysDictGroupTreeVO> tree = new ArrayList<>();
        for (SysDictGroup group : groups) {
            boolean groupMatches = matches(normalizedKeyword, group.getGroupCode(), group.getGroupName());
            List<SysDictGroupTreeVO.TypeNode> childTypes = typesByGroup.getOrDefault(group.getId(), List.of()).stream()
                    .filter(type -> groupMatches || typeMatches(type,
                            dataByType.getOrDefault(type.getId(), List.of()), normalizedKeyword))
                    .map(type -> toTreeNode(type, group, dataByType.getOrDefault(type.getId(), List.of())))
                    .toList();
            if (normalizedKeyword != null && !groupMatches && childTypes.isEmpty()) continue;
            SysDictGroupTreeVO node = new SysDictGroupTreeVO();
            node.setId(String.valueOf(group.getId()));
            node.setGroupCode(group.getGroupCode());
            node.setGroupName(group.getGroupName());
            node.setOrderNum(group.getOrderNum());
            node.setStatus(group.getStatus());
            node.setTypes(childTypes);
            tree.add(node);
        }
        return tree;
    }

    @Transactional(rollbackFor = Exception.class)
    public Long create(SysDictType type) {
        DictWriteAuthorizer.requireSystemAdmin();
        normalizeAndValidate(type, true);
        if (!"BUSINESS".equals(type.getDictClass())) {
            throw new BusinessException("DICT_CLASS_CREATE_FORBIDDEN", "管理端仅允许新建 BUSINESS 字典类型");
        }
        SysDictGroup group = requireOwnedGroup(type.getGroupId());
        if (!"ENABLE".equals(group.getStatus())) {
            throw new BusinessException("DICT_GROUP_DISABLED", "停用分组不能新增字典类型");
        }
        requireUniqueCode(type.getDictCode(), null);
        type.setId(null);
        type.setTenantId(group.getTenantId());
        typeMapper.insert(type);
        dataService.evictCache(type.getDictCode());
        return type.getId();
    }

    @Transactional(rollbackFor = Exception.class)
    public void update(SysDictType request) {
        DictWriteAuthorizer.requireSystemAdmin();
        SysDictType existing = requireOwnedType(request.getId());
        if (request.getGroupId() == null) request.setGroupId(existing.getGroupId());
        if (!StringUtils.hasText(request.getDictClass())) request.setDictClass(existing.getDictClass());
        normalizeAndValidate(request, false);
        SysDictGroup targetGroup = requireOwnedGroup(request.getGroupId());
        if (!"ENABLE".equals(targetGroup.getStatus())) {
            throw new BusinessException("DICT_GROUP_DISABLED", "停用分组不能修改字典类型");
        }
        if (!existing.getDictClass().equals(request.getDictClass())) {
            throw new BusinessException("DICT_CLASS_IMMUTABLE", "字典分类创建后不可修改");
        }
        if (isProtected(existing) && !existing.getDictCode().equals(request.getDictCode())) {
            throw new BusinessException("DICT_CODE_IMMUTABLE", "系统或状态机字典编码不可修改");
        }
        if (isProtected(existing) && !existing.getGroupId().equals(request.getGroupId())) {
            throw new BusinessException("DICT_GROUP_IMMUTABLE", "系统或状态机字典所属分组不可修改");
        }
        if (isProtected(existing) && "DISABLE".equals(request.getStatus())) {
            throw new BusinessException("DICT_TYPE_DISABLE_PROTECTED", "系统或状态机字典类型不能停用");
        }
        if (!existing.getDictCode().equals(request.getDictCode())) {
            requireUniqueCode(request.getDictCode(), existing.getId());
        }
        request.setTenantId(existing.getTenantId());
        typeMapper.updateById(request);
        dataService.evictCache(existing.getDictCode());
        dataService.evictCache(request.getDictCode());
    }

    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        DictWriteAuthorizer.requireSystemAdmin();
        SysDictType existing = requireOwnedType(id);
        SysDictGroup group = requireOwnedGroup(existing.getGroupId());
        if (!"ENABLE".equals(group.getStatus())) {
            throw new BusinessException("DICT_GROUP_DISABLED", "停用分组不能删除字典类型");
        }
        if (isProtected(existing)) {
            throw new BusinessException("DICT_TYPE_DELETE_PROTECTED", "系统或状态机字典类型不能删除");
        }
        if (dataMapper.selectCount(new LambdaQueryWrapper<SysDictData>()
                .eq(SysDictData::getTenantId, existing.getTenantId())
                .eq(SysDictData::getDictTypeId, id)) > 0) {
            throw new BusinessException("DICT_TYPE_HAS_DATA", "字典类型存在标签，不能删除");
        }
        typeMapper.deleteById(id);
        dataService.evictCache(existing.getDictCode());
    }

    static boolean isProtected(SysDictType type) {
        return type != null && ("SYSTEM".equals(type.getDictClass()) || "STATE_MACHINE".equals(type.getDictClass()));
    }

    private void normalizeAndValidate(SysDictType type, boolean create) {
        if (create && type.getGroupId() == null) {
            SysDictGroup defaultGroup = groupMapper.selectOne(new LambdaQueryWrapper<SysDictGroup>()
                    .eq(SysDictGroup::getTenantId, UserContext.getCurrentTenantId())
                    .eq(SysDictGroup::getGroupCode, DEFAULT_BUSINESS_GROUP_CODE));
            if (defaultGroup == null) {
                throw new BusinessException("DICT_GROUP_NOT_FOUND", "默认业务治理分组不存在");
            }
            type.setGroupId(defaultGroup.getId());
        }
        String code = type.getDictCode() == null ? "" : type.getDictCode().trim().toLowerCase(Locale.ROOT);
        if (!DICT_CODE_PATTERN.matcher(code).matches()) {
            throw new BusinessException("INVALID_DICT_CODE", "字典编码必须为小写蛇形命名，长度 2-100 位");
        }
        if (!StringUtils.hasText(type.getDictName())) {
            throw new BusinessException("INVALID_DICT_NAME", "字典名称不能为空");
        }
        type.setDictCode(code);
        type.setDictName(type.getDictName().trim());
        type.setDictClass(normalizeClass(type.getDictClass()));
        type.setStatus(normalizeStatus(type.getStatus()));
    }

    private String normalizeClass(String value) {
        String dictClass = StringUtils.hasText(value) ? value.trim().toUpperCase(Locale.ROOT) : "BUSINESS";
        if (!VALID_CLASSES.contains(dictClass)) {
            throw new BusinessException("INVALID_DICT_CLASS", "字典分类仅支持 BUSINESS、SYSTEM 或 STATE_MACHINE");
        }
        return dictClass;
    }

    private String normalizeStatus(String value) {
        String status = StringUtils.hasText(value) ? value.trim().toUpperCase(Locale.ROOT) : "ENABLE";
        if (!VALID_STATUSES.contains(status)) {
            throw new BusinessException("INVALID_DICT_STATUS", "字典状态仅支持 ENABLE 或 DISABLE");
        }
        return status;
    }

    private void requireUniqueCode(String code, Long excludedId) {
        LambdaQueryWrapper<SysDictType> wrapper = ownedTypeQuery().eq(SysDictType::getDictCode, code);
        if (excludedId != null) wrapper.ne(SysDictType::getId, excludedId);
        if (typeMapper.selectCount(wrapper) > 0) {
            throw new BusinessException("DICT_CODE_EXISTS", "字典编码已存在");
        }
    }

    private SysDictType requireOwnedType(Long id) {
        SysDictType type = id == null ? null : typeMapper.selectById(id);
        if (type == null || !type.getTenantId().equals(UserContext.getCurrentTenantId())) {
            throw new BusinessException("DICT_TYPE_NOT_FOUND", "字典类型不存在");
        }
        return type;
    }

    private SysDictGroup requireOwnedGroup(Long id) {
        SysDictGroup group = id == null ? null : groupMapper.selectById(id);
        if (group == null || !group.getTenantId().equals(UserContext.getCurrentTenantId())) {
            throw new BusinessException("DICT_GROUP_NOT_FOUND", "字典分组不存在");
        }
        return group;
    }

    private LambdaQueryWrapper<SysDictType> ownedTypeQuery() {
        return new LambdaQueryWrapper<SysDictType>()
                .eq(SysDictType::getTenantId, UserContext.getCurrentTenantId());
    }

    private Map<Long, SysDictGroup> ownedGroupsById() {
        return groupMapper.selectList(new LambdaQueryWrapper<SysDictGroup>()
                        .eq(SysDictGroup::getTenantId, UserContext.getCurrentTenantId()))
                .stream().collect(Collectors.toMap(SysDictGroup::getId, group -> group));
    }

    private SysDictTypeVO toVO(SysDictType type, SysDictGroup group) {
        SysDictTypeVO vo = new SysDictTypeVO();
        vo.setId(type.getId() == null ? null : String.valueOf(type.getId()));
        vo.setGroupId(type.getGroupId() == null ? null : String.valueOf(type.getGroupId()));
        if (group != null) {
            vo.setGroupCode(group.getGroupCode());
            vo.setGroupName(group.getGroupName());
        }
        vo.setDictCode(type.getDictCode());
        vo.setDictName(type.getDictName());
        vo.setDictClass(type.getDictClass());
        vo.setStatus(type.getStatus());
        if (type.getCreatedAt() != null) vo.setCreatedAt(DateTimeUtils.DTF.format(type.getCreatedAt()));
        if (type.getUpdatedAt() != null) vo.setUpdatedAt(DateTimeUtils.DTF.format(type.getUpdatedAt()));
        return vo;
    }

    private boolean typeMatches(SysDictType type, List<SysDictDataVO> data, String keyword) {
        if (keyword == null || matches(keyword, type.getDictCode(), type.getDictName(), type.getDictClass())) return true;
        return data.stream().anyMatch(item -> matches(keyword, item.getDictLabel(), item.getDictValue()));
    }

    private boolean matches(String keyword, String... values) {
        if (keyword == null) return true;
        for (String value : values) {
            if (value != null && value.toLowerCase(Locale.ROOT).contains(keyword)) return true;
        }
        return false;
    }

    private SysDictGroupTreeVO.TypeNode toTreeNode(SysDictType type, SysDictGroup group, List<SysDictDataVO> data) {
        SysDictTypeVO source = toVO(type, group);
        SysDictGroupTreeVO.TypeNode node = new SysDictGroupTreeVO.TypeNode();
        node.setId(source.getId());
        node.setGroupId(source.getGroupId());
        node.setGroupCode(source.getGroupCode());
        node.setGroupName(source.getGroupName());
        node.setDictCode(source.getDictCode());
        node.setDictName(source.getDictName());
        node.setDictClass(source.getDictClass());
        node.setStatus(source.getStatus());
        node.setData(data);
        return node;
    }
}
