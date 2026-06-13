package com.cgcpms.system.service;

import com.cgcpms.common.exception.BusinessException;
import com.cgcpms.system.entity.SysMenu;
import com.cgcpms.system.mapper.SysMenuMapper;
import com.cgcpms.system.vo.MenuTreeVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class SysMenuService {

    private final SysMenuMapper sysMenuMapper;

    public List<MenuTreeVO> getTree() {
        List<SysMenu> allMenus = sysMenuMapper.selectList(null);
        return buildTree(allMenus, 0L);
    }

    public List<SysMenu> getFlatList() {
        return sysMenuMapper.selectList(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<SysMenu>()
                        .orderByAsc(SysMenu::getOrderNum));
    }

    public SysMenu getById(Long id) {
        SysMenu menu = sysMenuMapper.selectById(id);
        if (menu == null) throw new BusinessException("MENU_NOT_FOUND", "菜单不存在");
        return menu;
    }

    @Transactional
    public Long create(SysMenu menu) {
        if (menu.getStatus() == null) menu.setStatus("ENABLE");
        if (menu.getVisible() == null) menu.setVisible(1);
        sysMenuMapper.insert(menu);
        log.info("Creating menu: {}", menu.getMenuName());
        return menu.getId();
    }

    @Transactional
    public void update(SysMenu menu) {
        sysMenuMapper.updateById(menu);
    }

    @Transactional
    public void delete(Long id) {
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
