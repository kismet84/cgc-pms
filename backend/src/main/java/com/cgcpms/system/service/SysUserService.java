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
import java.util.Set;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class SysUserService {

    private static final Set<String> ALLOWED_STATUSES = Set.of("ENABLE", "DISABLE");
    private static final Set<String> ADMIN_ROLE_CODES = Set.of("ADMIN", "SUPER_ADMIN");

    private final SysUserMapper sysUserMapper;
    private final SysUserRoleMapper sysUserRoleMapper;
    private final SysRoleMapper sysRoleMapper;
    private final PasswordEncoder passwordEncoder;

    public IPage<SysUserVO> getPage(long pageNo, long pageSize, String username, String realName, String status) {
        return getPage(pageNo, pageSize, username, realName, status, null);
    }

    public IPage<SysUserVO> getPage(long pageNo, long pageSize, String username, String realName,
                                    String status, Long roleId) {
        LambdaQueryWrapper<SysUser> wrapper = new LambdaQueryWrapper<>();
        Long tenantId = UserContext.getCurrentTenantId();
        if (tenantId == null) {
            throw new BusinessException("TENANT_CONTEXT_REQUIRED", "缺少租户上下文");
        }
        wrapper.eq(SysUser::getTenantId, tenantId);
        if (roleId != null) {
            wrapper.exists("""
                    SELECT 1
                    FROM sys_user_role ur
                    WHERE ur.tenant_id = {0}
                      AND ur.user_id = sys_user.id
                      AND ur.role_id = {1}
                    """, tenantId, roleId);
        }
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
        user.setStatus(normalizeStatus(user.getStatus()));
        user.setTenantId(UserContext.getCurrentTenantId());
        user.setIsAdmin(null); // role assignment is authoritative
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
        // Request body never controls tenant, login identity, privilege, status, password or audit fields.
        existing.setRealName(user.getRealName());
        existing.setPhone(user.getPhone());
        existing.setEmail(user.getEmail());
        existing.setAvatar(user.getAvatar());
        existing.setOrgId(user.getOrgId());
        sysUserMapper.updateById(existing);
    }

    @Transactional(rollbackFor = Exception.class)
    public void updateStatus(Long id, String status) {
        SysUser user = sysUserMapper.selectById(id);
        if (user == null || !user.getTenantId().equals(UserContext.getCurrentTenantId()))
            throw new BusinessException("USER_NOT_FOUND", "用户不存在");
        status = normalizeStatus(status);
        if (id.equals(UserContext.getCurrentUserId()) && "DISABLE".equals(status)) {
            throw new BusinessException("SELF_DISABLE_FORBIDDEN", "不能停用当前登录用户");
        }
        if ("DISABLE".equals(status) && hasAdministratorRole(id, user.getTenantId())) {
            requireAdministratorContinuity(id, user.getTenantId());
        }
        user.setStatus(status);
        sysUserMapper.updateById(user);
    }

    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        SysUser user = sysUserMapper.selectById(id);
        if (user == null || !user.getTenantId().equals(UserContext.getCurrentTenantId()))
            throw new BusinessException("USER_NOT_FOUND", "用户不存在");

        if (id.equals(UserContext.getCurrentUserId())) {
            throw new BusinessException("SELF_DELETE_FORBIDDEN", "不能删除当前登录用户");
        }
        Long currentTenantId = UserContext.getCurrentTenantId();
        if (hasAdministratorRole(id, currentTenantId)) {
            requireAdministratorContinuity(id, currentTenantId);
        }

        sysUserRoleMapper.delete(new LambdaQueryWrapper<SysUserRole>()
                .eq(SysUserRole::getTenantId, currentTenantId)
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
            List<SysRole> roles = sysRoleMapper.selectByIds(roleIds);
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

        boolean currentlyAdmin = hasAdministratorRole(userId, user.getTenantId());
        boolean willRemainAdmin = roleIds != null && roleIds.stream().anyMatch(roleId -> isAdministratorRole(roleId, user.getTenantId()));
        if (currentlyAdmin && !willRemainAdmin) requireAdministratorContinuity(userId, user.getTenantId());

        sysUserRoleMapper.delete(new LambdaQueryWrapper<SysUserRole>()
                .eq(SysUserRole::getTenantId, user.getTenantId())
                .eq(SysUserRole::getUserId, userId));
        if (roleIds != null) {
            for (Long roleId : roleIds) {
                SysUserRole ur = new SysUserRole();
                ur.setTenantId(user.getTenantId());
                ur.setUserId(userId);
                ur.setRoleId(roleId);
                sysUserRoleMapper.insert(ur);
            }
        }
    }

    /**
     * 获取当前用户的最高角色等级（数字越小等级越高）。
     * SUPER_ADMIN=0, ADMIN=1, 普通角色=2。
     * 当无法确定角色（如 JWT 无 roleCodes）时拒绝角色授予。
     */
    private int getCurrentUserMaxRoleLevel() {
        List<String> currentRoles = UserContext.getCurrentRoles();
        if (currentRoles.isEmpty()) {
            // Never infer privilege from an incomplete JWT context.
            return Integer.MAX_VALUE;
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

    private String normalizeStatus(String status) {
        String normalized = status == null || status.isBlank() ? "ENABLE" : status.trim().toUpperCase();
        if (!ALLOWED_STATUSES.contains(normalized)) {
            throw new BusinessException("USER_STATUS_INVALID", "用户状态仅支持 ENABLE 或 DISABLE");
        }
        return normalized;
    }

    private boolean hasAdministratorRole(Long userId, Long tenantId) {
        return sysUserRoleMapper.selectList(new LambdaQueryWrapper<SysUserRole>()
                        .eq(SysUserRole::getTenantId, tenantId).eq(SysUserRole::getUserId, userId))
                .stream().anyMatch(binding -> isAdministratorRole(binding.getRoleId(), tenantId));
    }

    private boolean isAdministratorRole(Long roleId, Long tenantId) {
        SysRole role = sysRoleMapper.selectById(roleId);
        return role != null && tenantId.equals(role.getTenantId()) && ADMIN_ROLE_CODES.contains(role.getRoleCode());
    }

    private void requireAdministratorContinuity(Long excludedUserId, Long tenantId) {
        List<SysUser> users = sysUserMapper.selectList(new LambdaQueryWrapper<SysUser>()
                .eq(SysUser::getTenantId, tenantId).eq(SysUser::getStatus, "ENABLE")
                .ne(SysUser::getId, excludedUserId).last("FOR UPDATE")); // SQL-SAFETY: fixed-sql-fragment
        boolean replacement = users.stream().anyMatch(candidate -> hasAdministratorRole(candidate.getId(), tenantId));
        if (!replacement) throw new BusinessException("LAST_ADMIN", "不能移除最后一个管理员用户");
    }

    private Map<Long, List<String>> bulkLoadRoleNames(List<Long> userIds) {
        if (userIds == null || userIds.isEmpty()) return Collections.emptyMap();
        var userRoles = sysUserRoleMapper.selectList(
                new LambdaQueryWrapper<SysUserRole>()
                        .eq(SysUserRole::getTenantId, UserContext.getCurrentTenantId())
                        .in(SysUserRole::getUserId, userIds));
        if (userRoles.isEmpty()) return Collections.emptyMap();
        List<Long> roleIds = userRoles.stream().map(SysUserRole::getRoleId).distinct().toList();
        var roleMap = sysRoleMapper.selectByIds(roleIds).stream()
                .collect(Collectors.toMap(SysRole::getId, SysRole::getRoleName));
        return userRoles.stream().collect(Collectors.groupingBy(
                SysUserRole::getUserId,
                Collectors.mapping(ur -> roleMap.get(ur.getRoleId()), Collectors.toList())));
    }

    private Map<Long, List<Long>> bulkLoadRoleIds(List<Long> userIds) {
        if (userIds == null || userIds.isEmpty()) return Collections.emptyMap();
        var userRoles = sysUserRoleMapper.selectList(
                new LambdaQueryWrapper<SysUserRole>()
                        .eq(SysUserRole::getTenantId, UserContext.getCurrentTenantId())
                        .in(SysUserRole::getUserId, userIds));
        if (userRoles.isEmpty()) return Collections.emptyMap();
        return userRoles.stream().collect(Collectors.groupingBy(
                SysUserRole::getUserId,
                Collectors.mapping(SysUserRole::getRoleId, Collectors.toList())));
    }

    private List<String> getRoleNames(Long userId) {
        List<SysUserRole> userRoles = sysUserRoleMapper.selectList(
                new LambdaQueryWrapper<SysUserRole>()
                        .eq(SysUserRole::getTenantId, UserContext.getCurrentTenantId())
                        .eq(SysUserRole::getUserId, userId));
        if (userRoles.isEmpty()) return Collections.emptyList();
        List<Long> roleIds = userRoles.stream().map(SysUserRole::getRoleId).toList();
        return sysRoleMapper.selectByIds(roleIds).stream()
                .map(SysRole::getRoleName)
                .collect(Collectors.toList());
    }

    private List<Long> getRoleIds(Long userId) {
        List<SysUserRole> userRoles = sysUserRoleMapper.selectList(
                new LambdaQueryWrapper<SysUserRole>()
                        .eq(SysUserRole::getTenantId, UserContext.getCurrentTenantId())
                        .eq(SysUserRole::getUserId, userId));
        if (userRoles.isEmpty()) return Collections.emptyList();
        return userRoles.stream().map(SysUserRole::getRoleId).collect(Collectors.toList());
    }
}
