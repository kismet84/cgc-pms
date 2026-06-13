package com.cgcpms.system.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.cgcpms.common.exception.BusinessException;
import com.cgcpms.system.entity.SysRole;
import com.cgcpms.system.entity.SysRoleMenu;
import com.cgcpms.system.mapper.SysRoleMapper;
import com.cgcpms.system.mapper.SysRoleMenuMapper;
import com.cgcpms.system.vo.SysRoleVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.cgcpms.common.util.DateTimeUtils;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class SysRoleService {

    private final SysRoleMapper sysRoleMapper;
    private final SysRoleMenuMapper sysRoleMenuMapper;

    public List<SysRoleVO> getList() {
        return sysRoleMapper.selectList(null).stream().map(this::toVO).collect(Collectors.toList());
    }

    public SysRoleVO getById(Long id) {
        SysRole role = sysRoleMapper.selectById(id);
        if (role == null) throw new BusinessException("ROLE_NOT_FOUND", "角色不存在");
        return toVO(role);
    }

    @Transactional
    public Long create(SysRole role) {
        if (sysRoleMapper.selectCount(new LambdaQueryWrapper<SysRole>()
                .eq(SysRole::getRoleCode, role.getRoleCode())) > 0) {
            throw new BusinessException("ROLE_CODE_EXISTS", "角色编码已存在");
        }
        if (role.getStatus() == null) role.setStatus("ENABLE");
        sysRoleMapper.insert(role);
        log.info("Creating role: {}", role.getRoleCode());
        return role.getId();
    }

    @Transactional
    public void update(SysRole role) {
        sysRoleMapper.updateById(role);
    }

    @Transactional
    public void delete(Long id) {
        sysRoleMapper.deleteById(id);
    }

    @Transactional
    public void assignMenus(Long roleId, List<Long> menuIds) {
        sysRoleMenuMapper.delete(new LambdaQueryWrapper<SysRoleMenu>()
                .eq(SysRoleMenu::getRoleId, roleId));
        if (menuIds != null) {
            for (Long menuId : menuIds) {
                SysRoleMenu rm = new SysRoleMenu();
                rm.setRoleId(roleId);
                rm.setMenuId(menuId);
                sysRoleMenuMapper.insert(rm);
            }
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
