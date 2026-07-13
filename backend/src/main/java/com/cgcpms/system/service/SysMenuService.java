package com.cgcpms.system.service;

import com.cgcpms.common.exception.BusinessException;
import com.cgcpms.system.entity.SysMenu;
import com.cgcpms.system.entity.SysRoleMenu;
import com.cgcpms.system.mapper.SysMenuMapper;
import com.cgcpms.system.mapper.SysRoleMenuMapper;
import com.cgcpms.system.vo.MenuTreeVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class SysMenuService {

    private static final Set<String> MENU_TYPES = Set.of("DIR", "MENU", "BUTTON");

    private final SysMenuMapper sysMenuMapper;
    private final SysRoleMenuMapper sysRoleMenuMapper;

    public List<MenuTreeVO> getTree() {
        Long tenantId = com.cgcpms.auth.context.UserContext.getCurrentTenantId();
        List<SysMenu> allMenus = sysMenuMapper.selectList(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<SysMenu>()
                        .eq(SysMenu::getTenantId, tenantId));
        return buildTree(allMenus, 0L);
    }

    public List<SysMenu> getFlatList() {
        Long tenantId = com.cgcpms.auth.context.UserContext.getCurrentTenantId();
        return sysMenuMapper.selectList(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<SysMenu>()
                        .eq(SysMenu::getTenantId, tenantId)
                        .orderByAsc(SysMenu::getOrderNum));
    }

    public SysMenu getById(Long id) {
        Long tenantId = com.cgcpms.auth.context.UserContext.getCurrentTenantId();
        SysMenu menu = sysMenuMapper.selectById(id);
        if (menu == null || !menu.getTenantId().equals(tenantId)) throw new BusinessException("MENU_NOT_FOUND", "菜单不存在");
        return menu;
    }

    @Transactional(rollbackFor = Exception.class)
    public Long create(SysMenu menu) {
        Long tenantId = com.cgcpms.auth.context.UserContext.getCurrentTenantId();
        if (!MENU_TYPES.contains(menu.getMenuType())) {
            throw new BusinessException("MENU_TYPE_INVALID", "菜单类型不合法");
        }

        Long parentId = menu.getParentId() == null ? 0L : menu.getParentId();
        menu.setParentId(parentId);
        if (parentId != 0L) {
            SysMenu parent = sysMenuMapper.selectById(parentId);
            if (parent == null || !Objects.equals(parent.getTenantId(), tenantId)
                    || "BUTTON".equals(parent.getMenuType())) {
                throw new BusinessException("MENU_PARENT_INVALID", "父菜单不存在或不可作为父节点");
            }
        }

        // Force tenantId from authenticated context, ignore client-supplied value
        menu.setTenantId(tenantId);
        if (menu.getStatus() == null) menu.setStatus("ENABLE");
        if (menu.getVisible() == null) menu.setVisible(1);
        sysMenuMapper.insert(menu);
        log.info("Creating menu: {}", menu.getMenuName());
        return menu.getId();
    }

    @Transactional(rollbackFor = Exception.class)
    public void update(SysMenu menu) {
        Long tenantId = com.cgcpms.auth.context.UserContext.getCurrentTenantId();
        SysMenu existing = sysMenuMapper.selectById(menu.getId());
        if (existing == null || !existing.getTenantId().equals(tenantId))
            throw new BusinessException("MENU_NOT_FOUND", "菜单不存在");
        sysMenuMapper.updateById(menu);
    }

    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        Long tenantId = com.cgcpms.auth.context.UserContext.getCurrentTenantId();
        SysMenu existing = sysMenuMapper.selectById(id);
        if (existing == null || !existing.getTenantId().equals(tenantId))
            throw new BusinessException("MENU_NOT_FOUND", "菜单不存在");
        // Check for child menus
        long childCount = sysMenuMapper.selectCount(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<SysMenu>()
                        .eq(SysMenu::getParentId, id)
                        .eq(SysMenu::getTenantId, tenantId));
        if (childCount > 0) {
            throw new BusinessException("MENU_HAS_CHILDREN", "菜单存在子菜单，请先删除子菜单");
        }
        // Check for role references
        long roleRefCount = sysRoleMenuMapper.selectCount(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<SysRoleMenu>()
                        .eq(SysRoleMenu::getMenuId, id));
        if (roleRefCount > 0) {
            throw new BusinessException("MENU_REFERENCED_BY_ROLES", "菜单被角色引用，请先解除角色授权");
        }
        sysMenuMapper.deleteById(id);
    }

    private List<MenuTreeVO> buildTree(List<SysMenu> menus, Long parentId) {
        return menus.stream()
                .filter(m -> m.getParentId() != null && m.getParentId().equals(parentId))
                .sorted(Comparator.comparing(SysMenu::getOrderNum, Comparator.nullsLast(Comparator.naturalOrder())))
                .map(menu -> {
                    MenuTreeVO vo = new MenuTreeVO();
                    vo.setId(menu.getId());
                    vo.setParentId(menu.getParentId());
                    vo.setMenuName(menu.getMenuName());
                    vo.setMenuType(menu.getMenuType());
                    vo.setPath(menu.getPath());
                    vo.setComponent(menu.getComponent());
                    vo.setPerms(menu.getPerms());
                    vo.setIcon(menu.getIcon());
                    vo.setOrderNum(menu.getOrderNum());
                    vo.setChildren(buildTree(menus, menu.getId()));
                    return vo;
                })
                .collect(Collectors.toList());
    }
}
