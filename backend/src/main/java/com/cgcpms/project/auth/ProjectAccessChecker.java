package com.cgcpms.project.auth;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.cgcpms.auth.context.UserContext;
import com.cgcpms.common.exception.BusinessException;
import com.cgcpms.project.entity.PmProject;
import com.cgcpms.project.mapper.PmProjectMapper;
import com.cgcpms.system.entity.SysRole;
import com.cgcpms.system.mapper.SysRoleMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Objects;

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
        if (isAccessible(project, roles, currentUserId, dataScope)) return;

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
        return projects.stream()
                .filter(p -> Objects.equals(tenantId, p.getTenantId()))
                .filter(p -> isAccessible(p, roles, userId, dataScope))
                .toList();
    }

    /**
     * 返回当前用户在当前租户内可访问的项目 ID，供跨项目列表查询复用。
     */
    public List<Long> accessibleProjectIds() {
        Long tenantId = UserContext.getCurrentTenantId();
        if (tenantId == null) return List.of();
        List<PmProject> tenantProjects = projectMapper.selectList(
                new LambdaQueryWrapper<PmProject>().eq(PmProject::getTenantId, tenantId));
        return filterAccessible(tenantProjects).stream()
                .map(PmProject::getId)
                .toList();
    }

    private boolean isAccessible(PmProject project, List<String> roles, Long userId, String dataScope) {
        if (roles.contains("ADMIN") || roles.contains("SUPER_ADMIN")) return true;
        if (userId != null && userId.equals(project.getProjectManagerId())) return true;
        if ("ALL".equals(dataScope)) return true;
        return "SELF".equals(dataScope) && userId != null && userId.equals(project.getCreatedBy());
    }

    private String resolveEffectiveDataScope() {
        List<String> roleCodes = UserContext.getCurrentRoles();
        if (roleCodes.isEmpty()) return "SELF";

        Long tenantId = UserContext.getCurrentTenantId();
        if (tenantId == null) return "SELF";

        List<SysRole> roles = sysRoleMapper.selectList(
                new LambdaQueryWrapper<SysRole>()
                        .eq(SysRole::getTenantId, tenantId)
                        .in(SysRole::getRoleCode, roleCodes));
        if (roles.isEmpty()) return "SELF";

        if (roles.stream().anyMatch(r -> "SELF".equals(r.getDataScope()))) return "SELF";
        if (roles.stream().anyMatch(r -> "DEPT".equals(r.getDataScope()))) return "DEPT";
        if (roles.stream().anyMatch(r -> "CUSTOM".equals(r.getDataScope()))) return "CUSTOM";
        return "ALL";
    }
}
