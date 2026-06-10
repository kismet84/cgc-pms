package com.cgcpms.system.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.cgcpms.common.exception.BusinessException;
import com.cgcpms.system.entity.*;
import com.cgcpms.system.mapper.*;
import com.cgcpms.system.vo.SysUserVO;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class SysUserService {

    private static final DateTimeFormatter DTF = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final SysUserMapper sysUserMapper;
    private final SysUserRoleMapper sysUserRoleMapper;
    private final SysRoleMapper sysRoleMapper;
    private final PasswordEncoder passwordEncoder;

    public IPage<SysUserVO> getPage(long pageNo, long pageSize, String username, String realName, String status) {
        LambdaQueryWrapper<SysUser> wrapper = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(username)) {
            wrapper.like(SysUser::getUsername, username);
        }
        if (StringUtils.hasText(realName)) {
            wrapper.like(SysUser::getRealName, realName);
        }
        if (StringUtils.hasText(status)) {
            wrapper.eq(SysUser::getStatus, status);
        }
        wrapper.orderByDesc(SysUser::getCreatedAt);

        Page<SysUser> page = sysUserMapper.selectPage(new Page<>(pageNo, pageSize), wrapper);

        return page.convert(user -> {
            SysUserVO vo = new SysUserVO();
            vo.setId(user.getId());
            vo.setUsername(user.getUsername());
            vo.setRealName(user.getRealName());
            vo.setPhone(user.getPhone());
            vo.setEmail(user.getEmail());
            vo.setAvatar(user.getAvatar());
            vo.setStatus(user.getStatus());
            vo.setIsAdmin(user.getIsAdmin());
            vo.setRoleNames(getRoleNames(user.getId()));
            if (user.getCreatedAt() != null) vo.setCreatedAt(DTF.format(user.getCreatedAt()));
            if (user.getUpdatedAt() != null) vo.setUpdatedAt(DTF.format(user.getUpdatedAt()));
            return vo;
        });
    }

    public SysUserVO getById(Long id) {
        SysUser user = sysUserMapper.selectById(id);
        if (user == null) throw new BusinessException("USER_NOT_FOUND", "用户不存在");
        SysUserVO vo = new SysUserVO();
        vo.setId(user.getId());
        vo.setUsername(user.getUsername());
        vo.setRealName(user.getRealName());
        vo.setPhone(user.getPhone());
        vo.setEmail(user.getEmail());
        vo.setAvatar(user.getAvatar());
        vo.setStatus(user.getStatus());
        vo.setIsAdmin(user.getIsAdmin());
        vo.setRoleNames(getRoleNames(user.getId()));
        if (user.getCreatedAt() != null) vo.setCreatedAt(DTF.format(user.getCreatedAt()));
        if (user.getUpdatedAt() != null) vo.setUpdatedAt(DTF.format(user.getUpdatedAt()));
        return vo;
    }

    @Transactional
    public Long create(SysUser user) {
        if (sysUserMapper.selectCount(new LambdaQueryWrapper<SysUser>()
                .eq(SysUser::getUsername, user.getUsername())) > 0) {
            throw new BusinessException("USERNAME_EXISTS", "用户名已存在");
        }
        user.setPassword(passwordEncoder.encode(user.getPassword()));
        if (user.getStatus() == null) user.setStatus("ENABLE");
        sysUserMapper.insert(user);
        return user.getId();
    }

    @Transactional
    public void update(SysUser user) {
        SysUser existing = sysUserMapper.selectById(user.getId());
        if (existing == null) throw new BusinessException("USER_NOT_FOUND", "用户不存在");
        if (user.getPassword() == null || user.getPassword().isBlank()) {
            user.setPassword(null); // don't update password
        } else {
            user.setPassword(passwordEncoder.encode(user.getPassword()));
        }
        sysUserMapper.updateById(user);
    }

    @Transactional
    public void updateStatus(Long id, String status) {
        SysUser user = sysUserMapper.selectById(id);
        if (user == null) throw new BusinessException("USER_NOT_FOUND", "用户不存在");
        user.setStatus(status);
        sysUserMapper.updateById(user);
    }

    @Transactional
    public void delete(Long id) {
        sysUserMapper.deleteById(id);
    }

    @Transactional
    public void assignRoles(Long userId, List<Long> roleIds) {
        sysUserRoleMapper.delete(new LambdaQueryWrapper<SysUserRole>()
                .eq(SysUserRole::getUserId, userId));
        if (roleIds != null) {
            for (Long roleId : roleIds) {
                SysUserRole ur = new SysUserRole();
                ur.setUserId(userId);
                ur.setRoleId(roleId);
                sysUserRoleMapper.insert(ur);
            }
        }
    }

    private List<String> getRoleNames(Long userId) {
        List<SysUserRole> userRoles = sysUserRoleMapper.selectList(
                new LambdaQueryWrapper<SysUserRole>().eq(SysUserRole::getUserId, userId));
        if (userRoles.isEmpty()) return Collections.emptyList();
        List<Long> roleIds = userRoles.stream().map(SysUserRole::getRoleId).toList();
        return sysRoleMapper.selectBatchIds(roleIds).stream()
                .map(SysRole::getRoleName)
                .collect(Collectors.toList());
    }
}
