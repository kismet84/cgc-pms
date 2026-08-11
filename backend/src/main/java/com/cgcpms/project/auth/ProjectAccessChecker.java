package com.cgcpms.project.auth;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.cgcpms.auth.context.UserContext;
import com.cgcpms.common.exception.BusinessException;
import com.cgcpms.project.entity.PmProject;
import com.cgcpms.project.entity.PmProjectMember;
import com.cgcpms.project.mapper.PmProjectMapper;
import com.cgcpms.project.mapper.PmProjectMemberMapper;
import com.cgcpms.system.entity.SysRole;
import com.cgcpms.system.mapper.SysRoleMapper;
import com.cgcpms.system.role.SystemRoleContract;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 统一项目数据范围访问判定器。
 * <p>
 * 在列表、详情、总览、更新、成员等所有项目相关入口复用同一判定逻辑。
 * 未实现的数据范围策略（DEPT/CUSTOM）拒绝访问（fail-close），
 * 不退化到全租户范围。
 * </p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ProjectAccessChecker {

    private final PmProjectMapper projectMapper;
    private final PmProjectMemberMapper projectMemberMapper;
    private final SysRoleMapper sysRoleMapper;

    /**
     * 验证当前用户是否有权访问指定项目。
     *
     * @param projectId         项目 ID
     * @param requiredPermission 所需权限标识（仅用于错误消息）
     * @throws BusinessException 如果无权访问
     */
    public void checkAccess(Long projectId, String requiredPermission) {
        PmProject project = projectMapper.selectById(projectId);
        checkAccess(project, requiredPermission);
    }

    public void checkAccess(PmProject project, String requiredPermission) {
        if (project == null) {
            throw new BusinessException("PROJECT_NOT_FOUND", "项目不存在");
        }

        // 租户隔离
        Long currentTenantId = UserContext.getCurrentTenantId();
        if (!currentTenantId.equals(project.getTenantId())) {
            throw new BusinessException("PROJECT_NOT_FOUND", "项目不存在");
        }

        List<String> roles = UserContext.getCurrentRoles();
        Long currentUserId = UserContext.getCurrentUserId();
        String dataScope = resolveEffectiveDataScope();
        Set<Long> memberProjectIds = activeMemberProjectIds(currentTenantId, currentUserId);
        if (isAccessible(project, roles, currentUserId, dataScope, memberProjectIds)) return;

        log.warn("PROJECT_ACCESS_DENIED: 数据范围 {}，用户 {} 无法访问项目 {}",
                dataScope, currentUserId, project.getId());

        throw new BusinessException("PROJECT_ACCESS_DENIED",
                "无权" + (requiredPermission != null ? requiredPermission : "访问") + "该项目");
    }

    public List<PmProject> filterAccessible(List<PmProject> projects) {
        if (projects == null || projects.isEmpty()) return List.of();
        Long tenantId = UserContext.getCurrentTenantId();
        List<String> roles = UserContext.getCurrentRoles();
        Long userId = UserContext.getCurrentUserId();
        String dataScope = resolveEffectiveDataScope();
        Set<Long> memberProjectIds = activeMemberProjectIds(tenantId, userId);
        return projects.stream()
                .filter(p -> Objects.equals(tenantId, p.getTenantId()))
                .filter(p -> isAccessible(p, roles, userId, dataScope, memberProjectIds))
                .toList();
    }

    /**
     * 返回当前用户在当前租户内可访问的项目 ID，供跨项目列表查询复用。
     */
    public List<Long> accessibleProjectIds() {
        return accessibleProjects().stream()
                .map(PmProject::getId)
                .toList();
    }

    /**
     * 返回当前用户在当前租户内可访问的项目，供轻量上下文选项复用。
     */
    public List<PmProject> accessibleProjects() {
        Long tenantId = UserContext.getCurrentTenantId();
        if (tenantId == null) return List.of();
        List<PmProject> tenantProjects = projectMapper.selectList(
                new LambdaQueryWrapper<PmProject>().eq(PmProject::getTenantId, tenantId));
        return filterAccessible(tenantProjects);
    }

    /**
     * Builds an immutable SQL predicate for queries that join {@code pm_project p}.
     * Construction is database-free; role and membership validity are checked by
     * the predicate in the caller's paged query.
     */
    public ProjectSqlScope sqlScope() {
        Long tenantId = UserContext.getCurrentTenantId();
        Long userId = UserContext.getCurrentUserId();
        if (tenantId == null || userId == null) {
            return new ProjectSqlScope("(p.id IS NULL AND 1 = 0)", List.of());
        }

        List<String> roleCodes = UserContext.getCurrentRoles().stream()
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(role -> !role.isEmpty())
                .map(role -> role.toUpperCase(Locale.ROOT))
                .distinct()
                .sorted()
                .toList();
        List<Object> parameters = new ArrayList<>();
        parameters.add(tenantId);
        parameters.add(tenantId);
        parameters.add(userId);

        String memberGrant = """
                EXISTS (
                    SELECT 1
                    FROM pm_project_member pm
                    WHERE pm.tenant_id = ?
                      AND pm.project_id = p.id
                      AND pm.user_id = ?
                      AND pm.status = 'ACTIVE'
                      AND pm.deleted_flag = 0
                )
                """.strip();
        if (roleCodes.isEmpty()) {
            parameters.add(userId);
            return new ProjectSqlScope("""
                    (
                        p.tenant_id = ?
                        AND p.deleted_flag = 0
                        AND (
                            %s
                            OR p.created_by = ?
                        )
                    )
                    """.formatted(memberGrant).strip(), parameters);
        }

        String rolePlaceholders = String.join(", ", Collections.nCopies(roleCodes.size(), "?"));
        parameters.add(tenantId);
        parameters.add(userId);
        parameters.addAll(roleCodes);
        parameters.add(userId);
        return new ProjectSqlScope("""
                (
                    p.tenant_id = ?
                    AND p.deleted_flag = 0
                    AND (
                        %s
                        OR CASE (
                            SELECT CASE
                                WHEN COUNT(*) = 0 THEN 'SELF'
                                WHEN SUM(CASE WHEN UPPER(r.role_code) = 'SUPER_ADMIN' THEN 1 ELSE 0 END) > 0 THEN 'ALL'
                                WHEN SUM(CASE WHEN r.data_scope = 'ALL' THEN 1 ELSE 0 END) > 0 THEN 'ALL'
                                WHEN SUM(CASE WHEN r.data_scope = 'PROJECT_MEMBER' THEN 1 ELSE 0 END) > 0 THEN 'PROJECT_MEMBER'
                                WHEN SUM(CASE WHEN r.data_scope = 'DEPT' THEN 1 ELSE 0 END) > 0 THEN 'DEPT'
                                WHEN SUM(CASE WHEN r.data_scope = 'DEPT_AND_CHILD' THEN 1 ELSE 0 END) > 0 THEN 'DEPT_AND_CHILD'
                                WHEN SUM(CASE WHEN r.data_scope = 'CUSTOM' THEN 1 ELSE 0 END) > 0 THEN 'CUSTOM'
                                WHEN SUM(CASE WHEN r.data_scope = 'SELF' THEN 1 ELSE 0 END) > 0 THEN 'SELF'
                                ELSE 'NONE'
                            END
                            FROM sys_user_role ur
                            JOIN sys_role r
                              ON r.tenant_id = ur.tenant_id
                             AND r.id = ur.role_id
                            WHERE ur.tenant_id = ?
                              AND ur.user_id = ?
                              AND r.status = 'ENABLE'
                              AND r.deleted_flag = 0
                              AND UPPER(r.role_code) IN (%s)
                        )
                            WHEN 'ALL' THEN 1
                            WHEN 'SELF' THEN CASE WHEN p.created_by = ? THEN 1 ELSE 0 END
                            ELSE 0
                        END = 1
                    )
                )
                """.formatted(memberGrant, rolePlaceholders).strip(), parameters);
    }

    public record ProjectSqlScope(String predicate, List<Object> parameters) {
        public ProjectSqlScope {
            predicate = Objects.requireNonNull(predicate, "predicate");
            parameters = List.copyOf(Objects.requireNonNull(parameters, "parameters"));
        }
    }

    public void requireAllScope(String action) {
        if ("ALL".equals(resolveEffectiveDataScope())) return;
        throw new BusinessException("PROJECT_ALL_SCOPE_REQUIRED", "无权" + action + "全租户项目数据");
    }

    private boolean isAccessible(PmProject project, List<String> roles, Long userId, String dataScope,
                                 Set<Long> memberProjectIds) {
        if (roles.contains(SystemRoleContract.HIDDEN_SUPER_ADMIN)) return true;
        if ("ALL".equals(dataScope)) return true;
        // Active project membership is an explicit grant and forms a union with
        // system-role scope. This also keeps member access when no system role
        // grants a broader scope.
        if (memberProjectIds.contains(project.getId())) return true;
        if ("PROJECT_MEMBER".equals(dataScope)) return false;
        return "SELF".equals(dataScope) && userId != null && userId.equals(project.getCreatedBy());
    }

    private Set<Long> activeMemberProjectIds(Long tenantId, Long userId) {
        if (tenantId == null || userId == null) return Set.of();
        return projectMemberMapper.selectList(
                        new LambdaQueryWrapper<PmProjectMember>()
                                .eq(PmProjectMember::getTenantId, tenantId)
                                .eq(PmProjectMember::getUserId, userId)
                                .eq(PmProjectMember::getStatus, "ACTIVE"))
                .stream()
                .map(PmProjectMember::getProjectId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
    }

    private String resolveEffectiveDataScope() {
        List<String> roleCodes = UserContext.getCurrentRoles();
        if (roleCodes.isEmpty()) return "SELF";
        if (roleCodes.stream().anyMatch(
                code -> SystemRoleContract.HIDDEN_SUPER_ADMIN.equalsIgnoreCase(code))) {
            return "ALL";
        }

        Long tenantId = UserContext.getCurrentTenantId();
        if (tenantId == null) return "SELF";

        List<SysRole> roles = sysRoleMapper.selectList(
                new LambdaQueryWrapper<SysRole>()
                        .eq(SysRole::getTenantId, tenantId)
                        .in(SysRole::getRoleCode, roleCodes));
        if (roles.isEmpty()) return "SELF";

        if (roles.stream().anyMatch(r -> "ALL".equals(r.getDataScope()))) return "ALL";
        if (roles.stream().anyMatch(r -> "PROJECT_MEMBER".equals(r.getDataScope()))) return "PROJECT_MEMBER";
        if (roles.stream().anyMatch(r -> "DEPT".equals(r.getDataScope()))) return "DEPT";
        if (roles.stream().anyMatch(r -> "DEPT_AND_CHILD".equals(r.getDataScope()))) return "DEPT_AND_CHILD";
        if (roles.stream().anyMatch(r -> "CUSTOM".equals(r.getDataScope()))) return "CUSTOM";
        if (roles.stream().anyMatch(r -> "SELF".equals(r.getDataScope()))) return "SELF";
        return "NONE";
    }
}
