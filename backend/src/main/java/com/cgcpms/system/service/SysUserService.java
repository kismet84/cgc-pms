package com.cgcpms.system.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.cgcpms.auth.context.UserContext;
import com.cgcpms.common.exception.BusinessException;
import com.cgcpms.system.entity.*;
import com.cgcpms.system.mapper.*;
import com.cgcpms.system.vo.SysUserVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import com.cgcpms.common.util.DateTimeUtils;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class SysUserService {

    private final SysUserMapper sysUserMapper;
    private final SysUserRoleMapper sysUserRoleMapper;
    private final SysRoleMapper sysRoleMapper;
    private final PasswordEncoder passwordEncoder;

    public IPage<SysUserVO> getPage(long pageNo, long pageSize, String username, String realName, String status) {
        LambdaQueryWrapper<SysUser> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(SysUser::getTenantId, UserContext.getCurrentTenantId());
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

        List<Long> userIds = page.getRecords().stream().map(SysUser::getId).toList();
        Map<Long, List<String>> roleNamesMap = bulkLoadRoleNames(userIds);
        Map<Long, List<Long>> roleIdsMap = bulkLoadRoleIds(userIds);

        return page.convert(user -> {
            SysUserVO vo = new SysUserVO();
            vo.setId(user.getId());
            vo.setUsername(user.getUsername());
            vo.setRealName(user.getRealName());
            vo.setPhone(user.getPhone());
            vo.setEmail(user.getEmail());
            vo.setAvatar(user.getAvatar());
            vo.setOrgId(user.getOrgId());
            vo.setStatus(user.getStatus());
            vo.setIsAdmin(user.getIsAdmin());
            vo.setRoleNames(roleNamesMap.getOrDefault(user.getId(), Collections.emptyList()));
            vo.setRoleIds(roleIdsMap.getOrDefault(user.getId(), Collections.emptyList()));
            if (user.getCreatedAt() != null) vo.setCreatedAt(DateTimeUtils.DTF.format(user.getCreatedAt()));
            if (user.getUpdatedAt() != null) vo.setUpdatedAt(DateTimeUtils.DTF.format(user.getUpdatedAt()));
            return vo;
        });
    }

    public SysUserVO getById(Long id) {
        SysUser user = sysUserMapper.selectById(id);
        if (user == null || !user.getTenantId().equals(UserContext.getCurrentTenantId()))
            throw new BusinessException("USER_NOT_FOUND", "用户不存在");
        SysUserVO vo = new SysUserVO();
        vo.setId(user.getId());
        vo.setUsername(user.getUsername());
        vo.setRealName(user.getRealName());
        vo.setPhone(user.getPhone());
        vo.setEmail(user.getEmail());
        vo.setAvatar(user.getAvatar());
        vo.setOrgId(user.getOrgId());
        vo.setStatus(user.getStatus());
        vo.setIsAdmin(user.getIsAdmin());
        vo.setRoleNames(getRoleNames(user.getId()));
        vo.setRoleIds(getRoleIds(user.getId()));
        if (user.getCreatedAt() != null) vo.setCreatedAt(DateTimeUtils.DTF.format(user.getCreatedAt()));
        if (user.getUpdatedAt() != null) vo.setUpdatedAt(DateTimeUtils.DTF.format(user.getUpdatedAt()));
        return vo;
    }

    @Transactional(rollbackFor = Exception.class)
    public Long create(SysUser user) {
        if (sysUserMapper.selectCount(new LambdaQueryWrapper<SysUser>()
                .eq(SysUser::getUsername, user.getUsername())) > 0) {
            throw new BusinessException("USERNAME_EXISTS", "用户名已存在");
        }
        user.setPassword(passwordEncoder.encode(user.getPassword()));
        if (user.getStatus() == null) user.setStatus("ENABLE");
        user.setTenantId(UserContext.getCurrentTenantId());
        sysUserMapper.insert(user);
        log.info("Creating user: {}", user.getUsername());
        if (user.getRoleIds() != null && !user.getRoleIds().isEmpty()) {
            assignRoles(user.getId(), user.getRoleIds());
        }
        return user.getId();
    }

    @Transactional(rollbackFor = Exception.class)
    public void update(SysUser user) {
        SysUser existing = sysUserMapper.selectById(user.getId());
        if (existing == null || !existing.getTenantId().equals(UserContext.getCurrentTenantId()))
            throw new BusinessException("USER_NOT_FOUND", "用户不存在");
        if (user.getPassword() == null || user.getPassword().isBlank()) {
            user.setPassword(null); // don't update password
        } else {
            user.setPassword(passwordEncoder.encode(user.getPassword()));
        }
        sysUserMapper.updateById(user);
    }

    @Transactional(rollbackFor = Exception.class)
    public void updateStatus(Long id, String status) {
        SysUser user = sysUserMapper.selectById(id);
        if (user == null || !user.getTenantId().equals(UserContext.getCurrentTenantId()))
            throw new BusinessException("USER_NOT_FOUND", "用户不存在");
        user.setStatus(status);
        sysUserMapper.updateById(user);
    }

    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        SysUser user = sysUserMapper.selectById(id);
        if (user == null || !user.getTenantId().equals(UserContext.getCurrentTenantId()))
            throw new BusinessException("USER_NOT_FOUND", "用户不存在");

        // 检查是否是 ADMIN 用户，如果是则检查是否还有其他 ADMIN（不能删除最后一个管理员）
        Long currentTenantId = UserContext.getCurrentTenantId();
        List<SysUserRole> userRoles = sysUserRoleMapper.selectList(
                new LambdaQueryWrapper<SysUserRole>().eq(SysUserRole::getUserId, id));
        boolean isAdmin = userRoles.stream().anyMatch(ur -> {
            SysRole role = sysRoleMapper.selectById(ur.getRoleId());
            return role != null && "ADMIN".equals(role.getRoleCode());
        });
        if (isAdmin) {
            // 统计当前租户下还有多少 ADMIN 用户
            List<SysUserRole> allUserRoles = sysUserRoleMapper.selectList(
                    new LambdaQueryWrapper<SysUserRole>().in(SysUserRole::getUserId,
                            sysUserMapper.selectList(new LambdaQueryWrapper<SysUser>()
                                    .eq(SysUser::getTenantId, currentTenantId)
                                    .ne(SysUser::getId, id))
                                    .stream().map(SysUser::getId).toList()));
            long adminCount = allUserRoles.stream().filter(ur -> {
                SysRole role = sysRoleMapper.selectById(ur.getRoleId());
                return role != null && "ADMIN".equals(role.getRoleCode());
            }).count();
            if (adminCount == 0) {
                throw new BusinessException("LAST_ADMIN", "不能删除最后一个管理员用户");
            }
        }

        sysUserRoleMapper.delete(new LambdaQueryWrapper<SysUserRole>()
                .eq(SysUserRole::getUserId, id));
        sysUserMapper.deleteById(id);
    }

    @Transactional(rollbackFor = Exception.class)
    public void assignRoles(Long userId, List<Long> roleIds) {
        // Tenant isolation: verify user belongs to current tenant
        SysUser user = sysUserMapper.selectById(userId);
        if (user == null || !user.getTenantId().equals(UserContext.getCurrentTenantId()))
            throw new BusinessException("USER_NOT_FOUND", "用户不存在");

        // 禁止自我提权
        Long currentUserId = UserContext.getCurrentUserId();
        if (currentUserId != null && currentUserId.equals(userId)) {
            throw new BusinessException("SELF_ROLE_ASSIGN_FORBIDDEN", "不能给自己分配角色");
        }

        // 获取当前操作者的最高角色等级
        int operatorMaxLevel = getCurrentUserMaxRoleLevel();

        // Verify all roles belong to current tenant and validate role level
        if (roleIds != null && !roleIds.isEmpty()) {
            List<SysRole> roles = sysRoleMapper.selectBatchIds(roleIds);
            if (roles.size() != roleIds.size())
                throw new BusinessException("ROLE_NOT_FOUND", "部分角色不存在");
            Long currentTenantId = UserContext.getCurrentTenantId();
            for (SysRole role : roles) {
                if (!currentTenantId.equals(role.getTenantId()))
                    throw new BusinessException("ROLE_NOT_FOUND", "角色不存在");
                // 检查角色等级：只能授予 <= 自己等级的角色（数字越小等级越高）
                int targetLevel = role.getRoleLevel() != null ? role.getRoleLevel() : 2;
                if (targetLevel < operatorMaxLevel) {
                    throw new BusinessException("ROLE_LEVEL_DENIED",
                            "无权授予角色: " + role.getRoleName() + "（等级不足）");
                }
            }
        }

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

    /**
     * 获取当前用户的最高角色等级（数字越小等级越高）。
     * SUPER_ADMIN=0, ADMIN=1, 普通角色=2。
     * 当无法确定角色（如 JWT 无 roleCodes）时返回 0（最高权限），
     * 此时依赖控制器层的 @PreAuthorize 确保调用方已认证。
     */
    private int getCurrentUserMaxRoleLevel() {
        List<String> currentRoles = UserContext.getCurrentRoles();
        if (currentRoles.isEmpty()) {
            // 无法确定角色等级时返回最高权限（依赖 @PreAuthorize 门禁）
            return 0;
        }

        Long tenantId = UserContext.getCurrentTenantId();
        if (tenantId == null) return Integer.MAX_VALUE;

        List<SysRole> roles = sysRoleMapper.selectList(
                new LambdaQueryWrapper<SysRole>()
                        .eq(SysRole::getTenantId, tenantId)
                        .in(SysRole::getRoleCode, currentRoles));
        return roles.stream()
                .mapToInt(r -> r.getRoleLevel() != null ? r.getRoleLevel() : 2)
                .min()
                .orElse(Integer.MAX_VALUE);
    }

    private Map<Long, List<String>> bulkLoadRoleNames(List<Long> userIds) {
        if (userIds == null || userIds.isEmpty()) return Collections.emptyMap();
        var userRoles = sysUserRoleMapper.selectList(
                new LambdaQueryWrapper<SysUserRole>().in(SysUserRole::getUserId, userIds));
        if (userRoles.isEmpty()) return Collections.emptyMap();
        List<Long> roleIds = userRoles.stream().map(SysUserRole::getRoleId).distinct().toList();
        var roleMap = sysRoleMapper.selectBatchIds(roleIds).stream()
                .collect(Collectors.toMap(SysRole::getId, SysRole::getRoleName));
        return userRoles.stream().collect(Collectors.groupingBy(
                SysUserRole::getUserId,
                Collectors.mapping(ur -> roleMap.get(ur.getRoleId()), Collectors.toList())));
    }

    private Map<Long, List<Long>> bulkLoadRoleIds(List<Long> userIds) {
        if (userIds == null || userIds.isEmpty()) return Collections.emptyMap();
        var userRoles = sysUserRoleMapper.selectList(
                new LambdaQueryWrapper<SysUserRole>().in(SysUserRole::getUserId, userIds));
        if (userRoles.isEmpty()) return Collections.emptyMap();
        return userRoles.stream().collect(Collectors.groupingBy(
                SysUserRole::getUserId,
                Collectors.mapping(SysUserRole::getRoleId, Collectors.toList())));
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

    private List<Long> getRoleIds(Long userId) {
        List<SysUserRole> userRoles = sysUserRoleMapper.selectList(
                new LambdaQueryWrapper<SysUserRole>().eq(SysUserRole::getUserId, userId));
        if (userRoles.isEmpty()) return Collections.emptyList();
        return userRoles.stream().map(SysUserRole::getRoleId).collect(Collectors.toList());
    }
}
