package com.cgcpms.workflow.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.cgcpms.common.exception.BusinessException;
import com.cgcpms.org.entity.OrgPosition;
import com.cgcpms.org.mapper.OrgPositionMapper;
import com.cgcpms.project.entity.PmProjectMember;
import com.cgcpms.project.mapper.PmProjectMemberMapper;
import com.cgcpms.system.entity.SysUser;
import com.cgcpms.system.entity.SysUserRole;
import com.cgcpms.system.mapper.SysUserMapper;
import com.cgcpms.system.mapper.SysUserRoleMapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Resolves approver IDs from template node approverConfig JSON.
 * <p>
 * Supported config types:
 * <ul>
 *   <li>USER         — {"type":"USER","userId":123}</li>
 *   <li>ROLE         — {"type":"ROLE","roleId":456}</li>
 *   <li>POSITION     — {"type":"POSITION","positionId":789}</li>
 *   <li>PROJECT_ROLE — {"type":"PROJECT_ROLE","roleCode":"PM"}</li>
 * </ul>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ApproverResolver {

    private final SysUserMapper sysUserMapper;
    private final SysUserRoleMapper sysUserRoleMapper;
    private final OrgPositionMapper orgPositionMapper;
    private final PmProjectMemberMapper pmProjectMemberMapper;
    private final ObjectMapper objectMapper;

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
            case "ROLE" -> resolveRole(config, tenantId);
            case "POSITION" -> resolvePosition(config, tenantId);
            case "PROJECT_ROLE" -> resolveProjectRole(config, tenantId, projectId);
            default -> throw new BusinessException("UNSUPPORTED_APPROVER_TYPE",
                    "不支持的审批人类型: " + type);
        };

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
        SysUser user = sysUserMapper.selectById(userId);
        if (user == null || !Objects.equals(user.getTenantId(), tenantId)) {
            throw new BusinessException("WORKFLOW_APPROVER_INVALID", "审批人不属于当前租户");
        }
        return Collections.singletonList(userId);
    }

    private List<Long> resolveRole(JsonNode config, Long tenantId) {
        if (!config.has("roleId")) {
            throw new BusinessException("INVALID_APPROVER_CONFIG", "ROLE类型配置缺少roleId");
        }
        long roleId = config.get("roleId").asLong();

        List<SysUserRole> userRoles = sysUserRoleMapper.selectList(
                new LambdaQueryWrapper<SysUserRole>()
                        .eq(SysUserRole::getRoleId, roleId));
        if (userRoles.isEmpty()) {
            return Collections.emptyList();
        }

        List<Long> userIds = userRoles.stream()
                .map(SysUserRole::getUserId).distinct().toList();

        List<SysUser> users = sysUserMapper.selectList(
                new LambdaQueryWrapper<SysUser>()
                        .in(SysUser::getId, userIds)
                        .eq(tenantId != null, SysUser::getTenantId, tenantId)
                        .eq(SysUser::getStatus, "ENABLE"));

        return users.stream().map(SysUser::getId).toList();
    }

    private List<Long> resolvePosition(JsonNode config, Long tenantId) {
        if (!config.has("positionId")) {
            throw new BusinessException("INVALID_APPROVER_CONFIG", "POSITION类型配置缺少positionId");
        }
        long positionId = config.get("positionId").asLong();

        OrgPosition position = orgPositionMapper.selectById(positionId);
        if (position == null) {
            return Collections.emptyList();
        }

        LambdaQueryWrapper<SysUser> wrapper = new LambdaQueryWrapper<SysUser>()
                .eq(SysUser::getStatus, "ENABLE");
        if (tenantId != null) {
            wrapper.eq(SysUser::getTenantId, tenantId);
        }
        // Match users in the same org unit as the position
        if (position.getDepartmentId() != null) {
            wrapper.eq(SysUser::getOrgId, position.getDepartmentId());
        } else if (position.getCompanyId() != null) {
            wrapper.eq(SysUser::getOrgId, position.getCompanyId());
        }

        List<SysUser> users = sysUserMapper.selectList(wrapper);
        return users.stream().map(SysUser::getId).toList();
    }

    private List<Long> resolveProjectRole(JsonNode config, Long tenantId, Long projectId) {
        if (!config.has("roleCode")) {
            throw new BusinessException("INVALID_APPROVER_CONFIG", "PROJECT_ROLE类型配置缺少roleCode");
        }
        if (projectId == null) {
            throw new BusinessException("NO_PROJECT", "PROJECT_ROLE类型需要关联项目");
        }
        String roleCode = config.get("roleCode").asText();

        List<PmProjectMember> members = pmProjectMemberMapper.selectList(
                new LambdaQueryWrapper<PmProjectMember>()
                        .eq(tenantId != null, PmProjectMember::getTenantId, tenantId)
                        .eq(PmProjectMember::getProjectId, projectId)
                        .eq(PmProjectMember::getRoleCode, roleCode)
                        .eq(PmProjectMember::getStatus, "ACTIVE"));

        return members.stream().map(PmProjectMember::getUserId).distinct().toList();
    }
}
