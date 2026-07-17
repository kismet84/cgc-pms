package com.cgcpms.system.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.cgcpms.auth.context.UserContext;
import com.cgcpms.common.exception.BusinessException;
import com.cgcpms.system.entity.SysMenu;
import com.cgcpms.system.entity.SysRole;
import com.cgcpms.system.entity.SysRoleMenu;
import com.cgcpms.system.entity.SysUserRole;
import com.cgcpms.system.mapper.SysMenuMapper;
import com.cgcpms.system.mapper.SysRoleMapper;
import com.cgcpms.system.mapper.SysUserRoleMapper;
import com.cgcpms.system.mapper.SysRoleMenuMapper;
import com.cgcpms.system.vo.SysRoleVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.cgcpms.common.util.DateTimeUtils;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class SysRoleService {

    private static final Set<String> RESERVED_ROLE_CODES = Set.of("ADMIN", "SUPER_ADMIN");
    private static final Set<String> ALLOWED_STATUSES = Set.of("ENABLE", "DISABLE");
    private static final Set<String> ALLOWED_DATA_SCOPES =
            Set.of("ALL", "DEPT", "DEPT_AND_CHILD", "SELF", "CUSTOM");

    private final SysRoleMapper sysRoleMapper;
    private final SysRoleMenuMapper sysRoleMenuMapper;
    private final SysMenuMapper sysMenuMapper;
    private final SysUserRoleMapper sysUserRoleMapper;
    private final SysRoleMenuAuditService roleMenuAuditService;

    public List<SysRoleVO> getList() {
        return sysRoleMapper.selectList(
                new LambdaQueryWrapper<SysRole>()
                        .eq(SysRole::getTenantId, UserContext.getCurrentTenantId()))
                .stream().map(this::toVO).collect(Collectors.toList());
    }

    public SysRoleVO getById(Long id) {
        SysRole role = sysRoleMapper.selectById(id);
        if (role == null || !role.getTenantId().equals(UserContext.getCurrentTenantId()))
            throw new BusinessException("ROLE_NOT_FOUND", "角色不存在");
        return toVO(role);
    }

    @Transactional(rollbackFor = Exception.class)
    public Long create(SysRole role) {
        Long tenantId = UserContext.getCurrentTenantId();
        normalizeAndValidateCreate(role);
        if (sysRoleMapper.selectCount(new LambdaQueryWrapper<SysRole>()
                .eq(SysRole::getRoleCode, role.getRoleCode())
                .eq(SysRole::getTenantId, tenantId)) > 0) {
            throw new BusinessException("ROLE_CODE_EXISTS", "角色编码已存在");
        }
        role.setId(null);
        role.setTenantId(tenantId);
        role.setRoleType("CUSTOM");
        role.setRoleLevel(2);
        sysRoleMapper.insert(role);
        log.info("Creating role: {}", role.getRoleCode());
        return role.getId();
    }

    private void normalizeAndValidateCreate(SysRole role) {
        String roleCode = role.getRoleCode() == null ? "" : role.getRoleCode().trim();
        String roleName = role.getRoleName() == null ? "" : role.getRoleName().trim();
        if (roleCode.isEmpty() || roleName.isEmpty()) {
            throw new BusinessException("ROLE_CREATE_INVALID_FIELD", "角色编码和角色名称不能为空");
        }
        if (roleCode.length() > 50 || roleName.length() > 100) {
            throw new BusinessException("ROLE_CREATE_INVALID_FIELD", "角色编码或角色名称长度超限");
        }
        if (RESERVED_ROLE_CODES.contains(roleCode.toUpperCase())
                || (role.getRoleType() != null && !"CUSTOM".equalsIgnoreCase(role.getRoleType().trim()))
                || (role.getRoleLevel() != null && !Integer.valueOf(2).equals(role.getRoleLevel()))) {
            throw new BusinessException("ROLE_CREATE_PRIVILEGE_ESCALATION", "不允许创建系统或高等级角色");
        }

        String status = role.getStatus() == null || role.getStatus().isBlank()
                ? "ENABLE" : role.getStatus().trim().toUpperCase();
        String dataScope = role.getDataScope() == null || role.getDataScope().isBlank()
                ? "SELF" : role.getDataScope().trim().toUpperCase();
        if (!ALLOWED_STATUSES.contains(status) || !ALLOWED_DATA_SCOPES.contains(dataScope)) {
            throw new BusinessException("ROLE_CREATE_INVALID_FIELD", "角色状态或数据范围不合法");
        }

        role.setRoleCode(roleCode);
        role.setRoleName(roleName);
        role.setStatus(status);
        role.setDataScope(dataScope);
    }

    @Transactional(rollbackFor = Exception.class)
    public void update(SysRole role) {
        SysRole existing = sysRoleMapper.selectById(role.getId());
        if (existing == null || !existing.getTenantId().equals(UserContext.getCurrentTenantId()))
            throw new BusinessException("ROLE_NOT_FOUND", "角色不存在");

        String existingRoleCode = existing.getRoleCode() == null
                ? "" : existing.getRoleCode().trim().toUpperCase();
        if (RESERVED_ROLE_CODES.contains(existingRoleCode)
                || "SYSTEM".equalsIgnoreCase(existing.getRoleType())
                || (existing.getRoleLevel() != null && existing.getRoleLevel() < 2)) {
            throw new BusinessException("ROLE_UPDATE_PROTECTED", "系统或高等级角色不允许修改");
        }

        String requestRoleCode = role.getRoleCode() == null ? "" : role.getRoleCode().trim();
        if (!requestRoleCode.equals(existing.getRoleCode())) {
            throw new BusinessException("ROLE_UPDATE_IMMUTABLE_FIELD", "角色编码不允许修改");
        }

        String roleName = role.getRoleName() == null ? "" : role.getRoleName().trim();
        String status = role.getStatus() == null
                ? existing.getStatus() : role.getStatus().trim().toUpperCase();
        String dataScope = role.getDataScope() == null
                ? existing.getDataScope() : role.getDataScope().trim().toUpperCase();
        if (roleName.isEmpty() || roleName.length() > 100
                || !ALLOWED_STATUSES.contains(status)
                || !ALLOWED_DATA_SCOPES.contains(dataScope)) {
            throw new BusinessException("ROLE_UPDATE_INVALID_FIELD", "角色名称、状态或数据范围不合法");
        }

        existing.setRoleName(roleName);
        existing.setStatus(status);
        existing.setDataScope(dataScope);
        if (sysRoleMapper.updateById(existing) != 1) {
            throw new BusinessException("ROLE_UPDATE_FAILED", "角色修改失败，请重试");
        }
    }

    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        SysRole existing = sysRoleMapper.selectById(id);
        if (existing == null || !existing.getTenantId().equals(UserContext.getCurrentTenantId()))
            throw new BusinessException("ROLE_NOT_FOUND", "角色不存在");

        String roleCode = existing.getRoleCode() == null ? "" : existing.getRoleCode().trim().toUpperCase();
        if (RESERVED_ROLE_CODES.contains(roleCode)
                || "SYSTEM".equalsIgnoreCase(existing.getRoleType())
                || (existing.getRoleLevel() != null && existing.getRoleLevel() < 2)) {
            throw new BusinessException("ROLE_DELETE_PROTECTED", "系统或高等级角色不允许删除");
        }

        long userBindingCount = sysUserRoleMapper.selectCount(
                new LambdaQueryWrapper<SysUserRole>()
                        .eq(SysUserRole::getTenantId, existing.getTenantId())
                        .eq(SysUserRole::getRoleId, id));
        if (userBindingCount > 0) {
            throw new BusinessException("ROLE_IN_USE", "角色仍绑定用户，无法删除");
        }

        sysRoleMenuMapper.delete(new LambdaQueryWrapper<SysRoleMenu>()
                .eq(SysRoleMenu::getTenantId, existing.getTenantId())
                .eq(SysRoleMenu::getRoleId, id));
        if (sysRoleMapper.deleteById(id) != 1) {
            throw new BusinessException("ROLE_DELETE_FAILED", "角色删除失败，请重试");
        }
    }

    @Transactional(rollbackFor = Exception.class)
    public void assignMenus(Long roleId, List<Long> menuIds) {
        Long tenantId = UserContext.getCurrentTenantId();
        Long operatorId = UserContext.getCurrentUserId();
        AtomicReference<List<Long>> beforeMenuIds = new AtomicReference<>(List.of());
        List<Long> afterMenuIds = normalizeMenuIds(menuIds);
        try {
            doAssignMenus(roleId, tenantId, operatorId, beforeMenuIds, afterMenuIds);
        } catch (BusinessException ex) {
            recordFailureAudit(tenantId, operatorId, roleId, beforeMenuIds.get(), afterMenuIds, ex);
            throw ex;
        }
    }

    private void doAssignMenus(Long roleId, Long tenantId, Long operatorId,
                               AtomicReference<List<Long>> beforeMenuIdsRef, List<Long> afterMenuIds) {
        SysRole role = sysRoleMapper.selectById(roleId);
        if (role == null || !role.getTenantId().equals(tenantId))
            throw new BusinessException("ROLE_NOT_FOUND", "角色不存在");

        List<Long> beforeMenuIds = currentMenuIds(roleId);
        beforeMenuIdsRef.set(beforeMenuIds);
        requireEditableRole(role, operatorId);
        Map<Long, SysMenu> afterMenus = loadMenus(afterMenuIds, tenantId);
        rejectHighRiskDiff(beforeMenuIds, afterMenuIds, afterMenus);

        sysRoleMenuMapper.delete(new LambdaQueryWrapper<SysRoleMenu>()
                .eq(SysRoleMenu::getTenantId, tenantId)
                .eq(SysRoleMenu::getRoleId, roleId));
        for (Long menuId : afterMenuIds) {
            SysRoleMenu rm = new SysRoleMenu();
            rm.setTenantId(tenantId);
            rm.setRoleId(roleId);
            rm.setMenuId(menuId);
            sysRoleMenuMapper.insert(rm);
        }
        roleMenuAuditService.record(tenantId, operatorId, roleId, beforeMenuIds, afterMenuIds, true, null);
    }

    private List<Long> normalizeMenuIds(List<Long> menuIds) {
        if (menuIds == null) return List.of();
        return menuIds.stream()
                .filter(id -> id != null)
                .distinct()
                .sorted()
                .toList();
    }

    private List<Long> currentMenuIds(Long roleId) {
        return sysRoleMenuMapper.selectList(
                        new LambdaQueryWrapper<SysRoleMenu>()
                                .eq(SysRoleMenu::getTenantId, UserContext.getCurrentTenantId())
                                .eq(SysRoleMenu::getRoleId, roleId))
                .stream()
                .map(SysRoleMenu::getMenuId)
                .distinct()
                .sorted()
                .toList();
    }

    private void requireEditableRole(SysRole role, Long operatorId) {
        if ("SUPER_ADMIN".equals(role.getRoleCode()) || Integer.valueOf(0).equals(role.getRoleLevel())) {
            throw new BusinessException("ROLE_MENU_SUPER_ADMIN_PROTECTED", "超级管理员角色不允许编辑授权");
        }
        long selfRoleCount = sysUserRoleMapper.selectCount(
                new LambdaQueryWrapper<com.cgcpms.system.entity.SysUserRole>()
                        .eq(com.cgcpms.system.entity.SysUserRole::getUserId, operatorId)
                        .eq(com.cgcpms.system.entity.SysUserRole::getTenantId, role.getTenantId())
                        .eq(com.cgcpms.system.entity.SysUserRole::getRoleId, role.getId()));
        if (selfRoleCount > 0) {
            throw new BusinessException("ROLE_MENU_SELF_EDIT_FORBIDDEN", "不允许编辑当前用户持有的角色授权");
        }
    }

    private Map<Long, SysMenu> loadMenus(List<Long> menuIds, Long tenantId) {
        if (menuIds.isEmpty()) return Map.of();
        List<SysMenu> menus = sysMenuMapper.selectByIds(menuIds);
        Map<Long, SysMenu> menuMap = menus.stream()
                .filter(menu -> tenantId.equals(menu.getTenantId()))
                .collect(Collectors.toMap(SysMenu::getId, menu -> menu));
        if (menuMap.size() != menuIds.size()) {
            throw new BusinessException("MENU_NOT_FOUND", "菜单不存在");
        }
        return menuMap;
    }

    private void rejectHighRiskDiff(List<Long> beforeMenuIds, List<Long> afterMenuIds, Map<Long, SysMenu> afterMenus) {
        Set<Long> changedMenuIds = new HashSet<>(beforeMenuIds);
        for (Long menuId : afterMenuIds) {
            if (!changedMenuIds.add(menuId)) {
                changedMenuIds.remove(menuId);
            }
        }
        if (changedMenuIds.isEmpty()) return;

        Map<Long, SysMenu> beforeMenus = loadMenus(beforeMenuIds.stream()
                .filter(changedMenuIds::contains)
                .toList(), UserContext.getCurrentTenantId());
        for (Long menuId : changedMenuIds) {
            SysMenu menu = afterMenus.getOrDefault(menuId, beforeMenus.get(menuId));
            if (menu != null && isHighRiskSystemPermission(menu.getPerms())) {
                throw new BusinessException("ROLE_MENU_HIGH_RISK_FORBIDDEN", "高危系统权限不允许通过本入口变更");
            }
        }
    }

    private boolean isHighRiskSystemPermission(String perms) {
        return perms != null && (perms.startsWith("system:user:")
                || perms.startsWith("system:role:")
                || perms.startsWith("system:menu:"));
    }

    private void recordFailureAudit(Long tenantId, Long operatorId, Long roleId,
                                    List<Long> beforeMenuIds, List<Long> afterMenuIds,
                                    BusinessException ex) {
        try {
            roleMenuAuditService.record(tenantId, operatorId, roleId, beforeMenuIds, afterMenuIds,
                    false, ex.getCode());
        } catch (Exception auditEx) {
            log.warn("Failed to write role-menu failure audit: roleId={}, code={}", roleId, ex.getCode());
        }
    }

    private SysRoleVO toVO(SysRole role) {
        SysRoleVO vo = new SysRoleVO();
        vo.setId(role.getId());
        vo.setRoleCode(role.getRoleCode());
        vo.setRoleName(role.getRoleName());
        vo.setRoleType(role.getRoleType());
        vo.setStatus(role.getStatus());
        vo.setDataScope(role.getDataScope());
        List<SysRoleMenu> roleMenus = sysRoleMenuMapper.selectList(
                new LambdaQueryWrapper<SysRoleMenu>()
                        .eq(SysRoleMenu::getTenantId, role.getTenantId())
                        .eq(SysRoleMenu::getRoleId, role.getId()));
        vo.setMenuIds(roleMenus.stream().map(SysRoleMenu::getMenuId).collect(Collectors.toList()));
        if (role.getCreatedAt() != null) vo.setCreatedAt(DateTimeUtils.DTF.format(role.getCreatedAt()));
        return vo;
    }
}
