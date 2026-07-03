package com.cgcpms.system.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.cgcpms.auth.context.UserContext;
import com.cgcpms.common.exception.BusinessException;
import com.cgcpms.system.entity.SysMenu;
import com.cgcpms.system.entity.SysRole;
import com.cgcpms.system.entity.SysRoleMenu;
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
        if (sysRoleMapper.selectCount(new LambdaQueryWrapper<SysRole>()
                .eq(SysRole::getRoleCode, role.getRoleCode())
                .eq(SysRole::getTenantId, tenantId)) > 0) {
            throw new BusinessException("ROLE_CODE_EXISTS", "角色编码已存在");
        }
        if (role.getStatus() == null) role.setStatus("ENABLE");
        role.setTenantId(tenantId);
        sysRoleMapper.insert(role);
        log.info("Creating role: {}", role.getRoleCode());
        return role.getId();
    }

    @Transactional(rollbackFor = Exception.class)
    public void update(SysRole role) {
        SysRole existing = sysRoleMapper.selectById(role.getId());
        if (existing == null || !existing.getTenantId().equals(UserContext.getCurrentTenantId()))
            throw new BusinessException("ROLE_NOT_FOUND", "角色不存在");
        sysRoleMapper.updateById(role);
    }

    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        SysRole existing = sysRoleMapper.selectById(id);
        if (existing == null || !existing.getTenantId().equals(UserContext.getCurrentTenantId()))
            throw new BusinessException("ROLE_NOT_FOUND", "角色不存在");

        // Clean up role-menu and user-role associations to prevent orphan records
        sysRoleMenuMapper.delete(new LambdaQueryWrapper<SysRoleMenu>()
                .eq(SysRoleMenu::getRoleId, id));
        // Note: sys_user_role cleanup is handled by SysUserRoleMapper (if it exists)
        sysRoleMapper.deleteById(id);
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
                .eq(SysRoleMenu::getRoleId, roleId));
        for (Long menuId : afterMenuIds) {
            SysRoleMenu rm = new SysRoleMenu();
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
                        new LambdaQueryWrapper<SysRoleMenu>().eq(SysRoleMenu::getRoleId, roleId))
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
                        .eq(com.cgcpms.system.entity.SysUserRole::getRoleId, role.getId()));
        if (selfRoleCount > 0) {
            throw new BusinessException("ROLE_MENU_SELF_EDIT_FORBIDDEN", "不允许编辑当前用户持有的角色授权");
        }
    }

    private Map<Long, SysMenu> loadMenus(List<Long> menuIds, Long tenantId) {
        if (menuIds.isEmpty()) return Map.of();
        List<SysMenu> menus = sysMenuMapper.selectBatchIds(menuIds);
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
                new LambdaQueryWrapper<SysRoleMenu>().eq(SysRoleMenu::getRoleId, role.getId()));
        vo.setMenuIds(roleMenus.stream().map(SysRoleMenu::getMenuId).collect(Collectors.toList()));
        if (role.getCreatedAt() != null) vo.setCreatedAt(DateTimeUtils.DTF.format(role.getCreatedAt()));
        return vo;
    }
}
