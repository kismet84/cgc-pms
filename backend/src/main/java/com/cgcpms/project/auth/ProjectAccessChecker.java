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
        if (project == null) {
            throw new BusinessException("PROJECT_NOT_FOUND", "项目不存在");
        }

        // 租户隔离
        Long currentTenantId = UserContext.getCurrentTenantId();
        if (!currentTenantId.equals(project.getTenantId())) {
            throw new BusinessException("PROJECT_NOT_FOUND", "项目不存在");
        }

        // 管理员/超级管理员可访问所有项目
        List<String> roles = UserContext.getCurrentRoles();
        if (roles.contains("ADMIN") || roles.contains("SUPER_ADMIN")) {
            return;
        }

        Long currentUserId = UserContext.getCurrentUserId();
        if (currentUserId != null && currentUserId.equals(project.getProjectManagerId())) {
            return;
        }

        // 解析用户的数据范围
        String dataScope = resolveEffectiveDataScope();
        switch (dataScope) {
            case "ALL":
                return;
            case "SELF":
                if (currentUserId != null && currentUserId.equals(project.getCreatedBy())) {
                    return;
                }
                break;
            case "DEPT":
            case "CUSTOM":
                // DEPT/CUSTOM 数据范围尚未实现，拒绝访问（fail-close）
                log.warn("PROJECT_ACCESS_DENIED: 数据范围 {} 未实现，用户 {} 无法访问项目 {}",
                        dataScope, UserContext.getCurrentUserId(), projectId);
                break;
            default:
                log.warn("PROJECT_ACCESS_DENIED: 未知数据范围 {}，用户 {} 无法访问项目 {}",
                        dataScope, UserContext.getCurrentUserId(), projectId);
                break;
        }

        throw new BusinessException("PROJECT_ACCESS_DENIED",
                "无权" + (requiredPermission != null ? requiredPermission : "访问") + "该项目");
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
