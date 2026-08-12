package com.cgcpms.workflow.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.cgcpms.auth.context.UserContext;
import com.cgcpms.common.exception.BusinessException;
import com.cgcpms.org.entity.OrgPosition;
import com.cgcpms.org.mapper.OrgPositionMapper;
import com.cgcpms.project.entity.PmProjectMember;
import com.cgcpms.project.mapper.PmProjectMemberMapper;
import com.cgcpms.system.entity.SysRole;
import com.cgcpms.system.entity.SysUser;
import com.cgcpms.system.entity.SysUserRole;
import com.cgcpms.system.mapper.SysRoleMapper;
import com.cgcpms.system.mapper.SysUserMapper;
import com.cgcpms.system.mapper.SysUserRoleMapper;
import com.cgcpms.system.role.SystemRoleContract;
import com.cgcpms.workflow.WorkflowSecurityPolicy;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Resolves approver IDs from template node approverConfig JSON.
 * <p>
 * Supported config types:
 * <ul>
 *   <li>USER         — {"type":"USER","userId":123}</li>
 *   <li>ROLE         — {"type":"ROLE","roleId":456} or {"type":"ROLE","roleCode":"FINANCE"}</li>
 *   <li>POSITION     — {"type":"POSITION","positionId":789}</li>
 *   <li>PROJECT_ROLE — {"type":"PROJECT_ROLE","roleCode":"PROJECT_MANAGER"}</li>
 * </ul>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ApproverResolver {

    private final SysUserMapper sysUserMapper;
    private final SysUserRoleMapper sysUserRoleMapper;
    private final SysRoleMapper sysRoleMapper;
    private final OrgPositionMapper orgPositionMapper;
    private final PmProjectMemberMapper pmProjectMemberMapper;
    private final ObjectMapper objectMapper;
    private final JdbcTemplate jdbcTemplate;

    /**
     * Resolve approver user IDs from the approverConfig JSON.
     *
     * @param approverConfig JSON string from template node
     * @param tenantId       tenant for user lookup
     * @param projectId      project required for PROJECT_ROLE type (may be null)
     * @return list of resolved user IDs, never null
     * @throws BusinessException NO_APPROVER if no users match
     */
    public List<Long> resolve(String approverConfig, Long tenantId, Long projectId) {
        return resolve(approverConfig, tenantId, projectId, WorkflowSecurityPolicy.legacy());
    }

    public List<Long> resolve(String approverConfig, Long tenantId, Long projectId,
                              WorkflowSecurityPolicy policy) {
        if (tenantId == null) {
            throw new BusinessException("TENANT_CONTEXT_REQUIRED", "缺少租户上下文");
        }
        UserContext.Snapshot previousContext = UserContext.capture();
        Long currentTenantId = previousContext.tenantId();
        if (currentTenantId != null && !Objects.equals(currentTenantId, tenantId)) {
            throw new BusinessException("TENANT_CONTEXT_MISMATCH", "审批解析租户与当前上下文不一致");
        }
        boolean scopedTenant = currentTenantId == null;
        if (scopedTenant) {
            UserContext.restore(new UserContext.Snapshot(previousContext.userId(), previousContext.username(),
                    tenantId, previousContext.roles()));
        }
        try {
            return resolveWithinTenant(approverConfig, tenantId, projectId, policy);
        } finally {
            if (scopedTenant) {
                UserContext.restore(previousContext);
            }
        }
    }

    private List<Long> resolveWithinTenant(String approverConfig, Long tenantId, Long projectId,
                                           WorkflowSecurityPolicy policy) {
        if (approverConfig == null || approverConfig.isBlank() || "{}".equals(approverConfig.trim())) {
            throw new BusinessException("NO_APPROVER", "审批节点未配置审批人");
        }

        JsonNode config;
        try {
            config = objectMapper.readTree(approverConfig);
        } catch (Exception e) {
            throw new BusinessException("INVALID_APPROVER_CONFIG",
                    "审批人配置JSON格式无效: " + e.getMessage());
        }

        if (!config.has("type")) {
            throw new BusinessException("INVALID_APPROVER_CONFIG", "审批人配置缺少type字段");
        }
        String type = config.get("type").asText();

        List<Long> userIds = switch (type.toUpperCase()) {
            case "USER" -> resolveUser(config, tenantId);
            case "ROLE" -> resolveRole(config, tenantId, projectId, policy);
            case "POSITION" -> resolvePosition(config, tenantId);
            case "PROJECT_ROLE" -> resolveProjectRole(config, tenantId, projectId);
            default -> throw new BusinessException("UNSUPPORTED_APPROVER_TYPE",
                    "不支持的审批人类型: " + type);
        };

        if (userIds.isEmpty() && policy.allowAdminFallback()) {
            userIds = resolveFinanceAdministrators(tenantId);
        }
        if (userIds.isEmpty()) {
            throw new BusinessException("NO_APPROVER",
                    "审批节点未找到可用的审批人 (type=" + type + ")");
        }
        return userIds;
    }

    // ── type resolvers ──

    private List<Long> resolveUser(JsonNode config, Long tenantId) {
        if (!config.has("userId")) {
            throw new BusinessException("INVALID_APPROVER_CONFIG", "USER类型配置缺少userId");
        }
        Long userId = config.get("userId").asLong();
        SysUser user = sysUserMapper.selectOne(new LambdaQueryWrapper<SysUser>()
                .eq(SysUser::getId, userId)
                .eq(tenantId != null, SysUser::getTenantId, tenantId)
                .eq(SysUser::getStatus, "ENABLE")
                .last("FOR UPDATE")); // SQL-SAFETY: fixed-sql-fragment
        if (user == null) {
            throw new BusinessException("WORKFLOW_APPROVER_INVALID", "审批人不属于当前租户");
        }
        return Collections.singletonList(userId);
    }

    private List<Long> resolveRole(JsonNode config, Long tenantId, Long projectId,
                                   WorkflowSecurityPolicy policy) {
        SysRole role;
        if (config.has("roleId")) {
            role = sysRoleMapper.selectOne(new LambdaQueryWrapper<SysRole>()
                    .eq(SysRole::getId, config.get("roleId").asLong())
                    .eq(tenantId != null, SysRole::getTenantId, tenantId)
                    .last("FOR UPDATE")); // SQL-SAFETY: fixed-sql-fragment
            if (role == null) return Collections.emptyList();
            String canonicalCode = SystemRoleContract.canonicalRoleCode(role.getRoleCode());
            if (!Objects.equals(canonicalCode, role.getRoleCode())) {
                role = findEnabledRole(tenantId, canonicalCode);
            }
            if (role == null || !"ENABLE".equals(role.getStatus())) return Collections.emptyList();
        } else if (config.has("roleCode")) {
            role = findEnabledRole(tenantId, config.get("roleCode").asText());
            if (role == null) return Collections.emptyList();
        } else {
            throw new BusinessException("INVALID_APPROVER_CONFIG", "ROLE类型配置缺少roleId或roleCode");
        }
        if (SystemRoleContract.EMPLOYEE.equals(role.getRoleCode())) return Collections.emptyList();
        List<Long> users = resolveRoleById(role.getId(), tenantId);
        if (policy.requireProjectMembership()
                && SystemRoleContract.PROJECT_SCOPED_ROLE_CODES.contains(role.getRoleCode())) {
            users = intersectProjectMembers(users, tenantId, projectId);
        }
        return users;
    }

    private List<Long> resolvePosition(JsonNode config, Long tenantId) {
        if (!config.has("positionId")) {
            throw new BusinessException("INVALID_APPROVER_CONFIG", "POSITION类型配置缺少positionId");
        }
        long positionId = config.get("positionId").asLong();

        OrgPosition position = orgPositionMapper.selectOne(new LambdaQueryWrapper<OrgPosition>()
                .eq(OrgPosition::getId, positionId)
                .eq(tenantId != null, OrgPosition::getTenantId, tenantId)
                .eq(OrgPosition::getStatus, "ENABLE")
                .last("FOR UPDATE")); // SQL-SAFETY: fixed-sql-fragment
        if (position == null) {
            return Collections.emptyList();
        }
        return jdbcTemplate.queryForList("""
                SELECT u.id FROM org_user_position up
                JOIN sys_user u ON u.id=up.user_id AND u.tenant_id=up.tenant_id
                WHERE up.tenant_id=? AND up.position_id=? AND up.status='ACTIVE'
                  AND u.status='ENABLE' AND u.deleted_flag=0
                  AND (up.effective_from IS NULL OR up.effective_from<=CURRENT_DATE)
                  AND (up.effective_to IS NULL OR up.effective_to>=CURRENT_DATE)
                ORDER BY up.primary_flag DESC,u.id FOR UPDATE
                """, Long.class, tenantId, positionId);
    }

    private List<Long> resolveProjectRole(JsonNode config, Long tenantId, Long projectId) {
        if (!config.has("roleCode")) {
            throw new BusinessException("INVALID_APPROVER_CONFIG", "PROJECT_ROLE类型配置缺少roleCode");
        }
        if (projectId == null) {
            throw new BusinessException("NO_PROJECT", "PROJECT_ROLE类型需要关联项目");
        }
        String expectedRoleCode = SystemRoleContract.canonicalRoleCode(config.get("roleCode").asText());
        if (!SystemRoleContract.PROJECT_SCOPED_ROLE_CODES.contains(expectedRoleCode)) {
            throw new BusinessException("PROJECT_ROLE_INVALID", "项目角色必须使用七类项目范围系统角色");
        }
        List<Long> memberIds = pmProjectMemberMapper.selectList(
                        new LambdaQueryWrapper<PmProjectMember>()
                                .eq(tenantId != null, PmProjectMember::getTenantId, tenantId)
                                .eq(PmProjectMember::getProjectId, projectId)
                                .eq(PmProjectMember::getStatus, "ACTIVE")
                                .last("FOR UPDATE")) // SQL-SAFETY: fixed-sql-fragment
                .stream()
                .filter(member -> Objects.equals(expectedRoleCode,
                        SystemRoleContract.canonicalRoleCode(member.getRoleCode())))
                .map(PmProjectMember::getUserId).filter(Objects::nonNull).distinct().toList();
        if (memberIds.isEmpty()) return memberIds;
        return sysUserMapper.selectList(new LambdaQueryWrapper<SysUser>()
                        .in(SysUser::getId, memberIds)
                        .eq(tenantId != null, SysUser::getTenantId, tenantId)
                        .eq(SysUser::getStatus, "ENABLE")
                        .last("FOR UPDATE")) // SQL-SAFETY: fixed-sql-fragment
                .stream().map(SysUser::getId).distinct().toList();
    }

    private List<Long> intersectProjectMembers(List<Long> roleUsers, Long tenantId, Long projectId) {
        if (projectId == null) {
            throw new BusinessException("NO_PROJECT", "项目级审批角色需要关联项目");
        }
        if (roleUsers.isEmpty()) return roleUsers;
        List<PmProjectMember> members = pmProjectMemberMapper.selectList(
                new LambdaQueryWrapper<PmProjectMember>()
                        .eq(tenantId != null, PmProjectMember::getTenantId, tenantId)
                        .eq(PmProjectMember::getProjectId, projectId)
                        .eq(PmProjectMember::getStatus, "ACTIVE")
                        .last("FOR UPDATE")); // SQL-SAFETY: fixed-sql-fragment
        Set<Long> memberIds = members.stream().map(PmProjectMember::getUserId).collect(java.util.stream.Collectors.toSet());
        return roleUsers.stream().filter(memberIds::contains).distinct().toList();
    }

    private List<Long> resolveTenantUsersByRoleCode(Long tenantId, String roleCode) {
        SysRole role = findEnabledRole(tenantId, roleCode);
        if (role == null) {
            return Collections.emptyList();
        }
        return resolveRoleById(role.getId(), tenantId);
    }

    private SysRole findEnabledRole(Long tenantId, String roleCode) {
        return sysRoleMapper.selectOne(new LambdaQueryWrapper<SysRole>()
                .eq(tenantId != null, SysRole::getTenantId, tenantId)
                .eq(SysRole::getRoleCode, SystemRoleContract.canonicalRoleCode(roleCode))
                .eq(SysRole::getStatus, "ENABLE")
                .last("LIMIT 1 FOR UPDATE")); // SQL-SAFETY: fixed-sql-fragment
    }

    private List<Long> resolveFinanceAdministrators(Long tenantId) {
        List<Long> finance = resolveTenantUsersByRoleCode(tenantId, SystemRoleContract.COMPANY_FINANCE);
        if (finance.isEmpty()) return finance;
        Set<Long> superAdmins = Set.copyOf(
                resolveTenantUsersByRoleCode(tenantId, SystemRoleContract.HIDDEN_SUPER_ADMIN));
        return finance.stream().filter(superAdmins::contains).distinct().toList();
    }

    private List<Long> resolveRoleById(Long roleId, Long tenantId) {
        List<SysUserRole> userRoles = sysUserRoleMapper.selectList(
                new LambdaQueryWrapper<SysUserRole>()
                        .eq(tenantId != null, SysUserRole::getTenantId, tenantId)
                        .eq(SysUserRole::getRoleId, roleId)
                        .last("FOR UPDATE")); // SQL-SAFETY: fixed-sql-fragment
        if (userRoles.isEmpty()) {
            return Collections.emptyList();
        }

        List<Long> userIds = userRoles.stream()
                .map(SysUserRole::getUserId).distinct().toList();

        List<SysUser> users = sysUserMapper.selectList(
                new LambdaQueryWrapper<SysUser>()
                        .in(SysUser::getId, userIds)
                        .eq(tenantId != null, SysUser::getTenantId, tenantId)
                        .eq(SysUser::getStatus, "ENABLE")
                        .last("FOR UPDATE")); // SQL-SAFETY: fixed-sql-fragment

        return users.stream().map(SysUser::getId).toList();
    }
}
