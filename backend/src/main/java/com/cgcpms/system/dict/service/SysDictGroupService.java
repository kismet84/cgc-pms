package com.cgcpms.system.dict.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.cgcpms.auth.context.UserContext;
import com.cgcpms.common.exception.BusinessException;
import com.cgcpms.common.util.DateTimeUtils;
import com.cgcpms.system.dict.entity.SysDictGroup;
import com.cgcpms.system.dict.entity.SysDictType;
import com.cgcpms.system.dict.mapper.SysDictGroupMapper;
import com.cgcpms.system.dict.mapper.SysDictTypeMapper;
import com.cgcpms.system.dict.vo.SysDictGroupVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
public class SysDictGroupService {

    private static final Pattern GROUP_CODE_PATTERN = Pattern.compile("^[A-Z][A-Z0-9_]{1,49}$");
    private static final Set<String> VALID_STATUSES = Set.of("ENABLE", "DISABLE");

    private final SysDictGroupMapper groupMapper;
    private final SysDictTypeMapper typeMapper;

    public IPage<SysDictGroupVO> getPage(long pageNo, long pageSize, String keyword, String status) {
        LambdaQueryWrapper<SysDictGroup> wrapper = new LambdaQueryWrapper<SysDictGroup>()
                .eq(SysDictGroup::getTenantId, UserContext.getCurrentTenantId());
        if (StringUtils.hasText(keyword)) {
            String value = keyword.trim();
            wrapper.and(query -> query.like(SysDictGroup::getGroupCode, value)
                    .or().like(SysDictGroup::getGroupName, value));
        }
        if (StringUtils.hasText(status)) {
            wrapper.eq(SysDictGroup::getStatus, normalizeStatus(status));
        }
        wrapper.orderByAsc(SysDictGroup::getOrderNum).orderByAsc(SysDictGroup::getGroupCode);
        Page<SysDictGroup> page = groupMapper.selectPage(new Page<>(pageNo, pageSize), wrapper);
        return page.convert(this::toVO);
    }

    public SysDictGroupVO getById(Long id) {
        return toVO(requireOwned(id));
    }

    @Transactional(rollbackFor = Exception.class)
    public Long create(SysDictGroup group) {
        DictWriteAuthorizer.requireSystemAdmin();
        normalizeAndValidate(group);
        Long tenantId = UserContext.getCurrentTenantId();
        if (groupMapper.selectCount(new LambdaQueryWrapper<SysDictGroup>()
                .eq(SysDictGroup::getTenantId, tenantId)
                .eq(SysDictGroup::getGroupCode, group.getGroupCode())) > 0) {
            throw new BusinessException("DICT_GROUP_CODE_EXISTS", "字典分组编码已存在");
        }
        group.setId(null);
        group.setTenantId(tenantId);
        groupMapper.insert(group);
        return group.getId();
    }

    @Transactional(rollbackFor = Exception.class)
    public void update(SysDictGroup request) {
        DictWriteAuthorizer.requireSystemAdmin();
        SysDictGroup existing = requireOwned(request.getId());
        String requestedCode = normalizeCode(request.getGroupCode());
        if (!existing.getGroupCode().equals(requestedCode)) {
            throw new BusinessException("DICT_GROUP_CODE_IMMUTABLE", "字典分组编码创建后不可修改");
        }
        request.setTenantId(existing.getTenantId());
        normalizeAndValidate(request);
        groupMapper.updateById(request);
    }

    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        DictWriteAuthorizer.requireSystemAdmin();
        SysDictGroup existing = requireOwned(id);
        if (typeMapper.selectCount(new LambdaQueryWrapper<SysDictType>()
                .eq(SysDictType::getTenantId, existing.getTenantId())
                .eq(SysDictType::getGroupId, id)) > 0) {
            throw new BusinessException("DICT_GROUP_HAS_TYPES", "字典分组下存在字典类型，不能删除");
        }
        groupMapper.deleteById(id);
    }

    SysDictGroup requireOwnedEntity(Long id) {
        return requireOwned(id);
    }

    private SysDictGroup requireOwned(Long id) {
        SysDictGroup group = id == null ? null : groupMapper.selectById(id);
        Long tenantId = UserContext.getCurrentTenantId();
        if (group == null || !group.getTenantId().equals(tenantId)) {
            throw new BusinessException("DICT_GROUP_NOT_FOUND", "字典分组不存在");
        }
        return group;
    }

    private void normalizeAndValidate(SysDictGroup group) {
        String code = normalizeCode(group.getGroupCode());
        if (!GROUP_CODE_PATTERN.matcher(code).matches()) {
            throw new BusinessException("INVALID_DICT_GROUP_CODE", "分组编码必须为大写蛇形命名，长度 2-50 位");
        }
        if (!StringUtils.hasText(group.getGroupName())) {
            throw new BusinessException("INVALID_DICT_GROUP_NAME", "分组名称不能为空");
        }
        group.setGroupCode(code);
        group.setGroupName(group.getGroupName().trim());
        group.setOrderNum(group.getOrderNum() == null ? 0 : group.getOrderNum());
        group.setStatus(normalizeStatus(group.getStatus()));
    }

    private String normalizeCode(String value) {
        return value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
    }

    private String normalizeStatus(String value) {
        String status = StringUtils.hasText(value) ? value.trim().toUpperCase(Locale.ROOT) : "ENABLE";
        if (!VALID_STATUSES.contains(status)) {
            throw new BusinessException("INVALID_DICT_STATUS", "字典状态仅支持 ENABLE 或 DISABLE");
        }
        return status;
    }

    private SysDictGroupVO toVO(SysDictGroup group) {
        SysDictGroupVO vo = new SysDictGroupVO();
        vo.setId(group.getId() == null ? null : String.valueOf(group.getId()));
        vo.setGroupCode(group.getGroupCode());
        vo.setGroupName(group.getGroupName());
        vo.setOrderNum(group.getOrderNum());
        vo.setStatus(group.getStatus());
        if (group.getCreatedAt() != null) vo.setCreatedAt(DateTimeUtils.DTF.format(group.getCreatedAt()));
        if (group.getUpdatedAt() != null) vo.setUpdatedAt(DateTimeUtils.DTF.format(group.getUpdatedAt()));
        return vo;
    }
}
